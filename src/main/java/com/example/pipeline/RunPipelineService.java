package com.example.pipeline;

import com.example.sdf.domain.SdfMetadata;
import com.example.sdf.domain.SdfRecord;
import com.example.sdf.domain.SdfRepository;
import com.example.storage.domain.BulkLoadService;
import com.example.storage.domain.StoredData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SDF 파이프라인 실행 서비스.
 * download → extract → parse → load 4단계를 순차 실행하고 결과를 집계한다.
 */
@Service
public class RunPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RunPipelineService.class);

    private final SdfRepository sdfRepository;
    private final BulkLoadService bulkLoadService;

    public RunPipelineService(SdfRepository sdfRepository, BulkLoadService bulkLoadService) {
        this.sdfRepository = sdfRepository;
        this.bulkLoadService = bulkLoadService;
    }

    /**
     * PubChem CURRENT-Full에서 전체 SDF.gz 파일을 다운로드하여 파이프라인을 실행한다.
     * application.yml의 pubchem.ftp.full-path 설정을 사용하며, 전체 파일을 제한 없이 처리한다.
     *
     * @return 파이프라인 실행 결과
     */
    public PipelineResult runFull() {
        log.info("PubChem Full 다운로드 시작");
        PipelineResult result = PipelineResult.empty();
        List<String> fileUrls = sdfRepository.discoverFullSdfUrls();
        if (fileUrls.isEmpty()) {
            return result.failed("Full SDF 파일을 찾을 수 없습니다").done();
        }
        log.info("Pipeline started | batchId={} | files={}", result.batchId(), fileUrls.size());
        return runFileList(result, fileUrls);
    }

    /**
     * PubChem Monthly에서 가장 최근 월의 SDF.gz 파일을 다운로드하여 파이프라인을 실행한다.
     * application.yml의 pubchem.ftp.monthly-base-path 하위에서
     * 가장 최근 YYYY-MM-DD/SDF/ 디렉토리를 자동으로 찾아 처리한다.
     *
     * @return 파이프라인 실행 결과
     */
    public PipelineResult runMonthly() {
        log.info("PubChem Monthly 다운로드 시작");
        PipelineResult result = PipelineResult.empty();
        List<String> fileUrls = sdfRepository.discoverMonthlySdfUrls();
        if (fileUrls.isEmpty()) {
            return result.failed("Monthly SDF 파일을 찾을 수 없습니다").done();
        }
        log.info("Pipeline started | batchId={} | files={}", result.batchId(), fileUrls.size());
        return runFileList(result, fileUrls);
    }

    /**
     * 주어진 URL에서 SDF 데이터를 가져와 파싱 후 DB에 저장하는 전체 파이프라인을 실행한다.
     *
     * 단일 파일 URL(.sdf/.sdf.gz)이면 해당 파일만 처리하고,
     * 디렉토리 URL이면 내부의 모든 .sdf.gz 파일을 발견하여 일괄 처리한다.
     *
     * @param sourceUrl SDF 파일 URL 또는 디렉토리 리스팅 URL
     * @param maxFiles  최대 처리 파일 수 (0 = 제한 없음)
     * @return 파이프라인 실행 결과 (각 단계별 메트릭 포함)
     */
    public PipelineResult run(String sourceUrl, int maxFiles) {
        PipelineResult result = PipelineResult.empty();
        log.info("Pipeline started | batchId={} | source={} | maxFiles={}",
                result.batchId(), sourceUrl, maxFiles > 0 ? maxFiles : "전체");

        // URL 유형 감지: .sdf 또는 .sdf.gz로 끝나면 단일 파일, 아니면 디렉토리
        boolean isSingleFile = sourceUrl.endsWith(".sdf") || sourceUrl.endsWith(".sdf.gz");

        if (isSingleFile) {
            return runSingleFile(sourceUrl, result);
        }
        return runDirectory(sourceUrl, maxFiles, result);
    }

    /** 단일 SDF 파일 처리 (기존 로직). */
    private PipelineResult runSingleFile(String sourceUrl, PipelineResult result) {
        try {
            // Step 1: Download
            Instant t1 = Instant.now();
            SdfMetadata downloaded = sdfRepository.download(sourceUrl);
            result.withDownload(downloaded.fileSizeBytes(), Duration.between(t1, Instant.now()));
            log.info("Step 1/4 Download OK | {} bytes", downloaded.fileSizeBytes());

            // Step 2: Extract
            t1 = Instant.now();
            List<SdfMetadata> extractedFiles = sdfRepository.extract(downloaded).toList();
            result.withExtraction(extractedFiles.size(), Duration.between(t1, Instant.now()));
            log.info("Step 2/4 Extract OK | {} files", extractedFiles.size());

            // Step 3 + 4: Parse and Load
            processExtractedFiles(extractedFiles, sourceUrl, result);

        } catch (Exception e) {
            log.error("Pipeline failed | batchId={}", result.batchId(), e);
            result.failed(e.getMessage());
        }

        return result.done();
    }

    /** 디렉토리 모드: 내부의 모든 .sdf.gz 파일을 발견하여 일괄 처리. */
    private PipelineResult runDirectory(String directoryUrl, int maxFiles, PipelineResult result) {
        List<String> fileUrls;
        try {
            fileUrls = sdfRepository.discoverSdfUrls(directoryUrl);
        } catch (Exception e) {
            log.error("파일 목록 조회 실패: {}", directoryUrl, e);
            return result.failed("디렉토리 목록 조회 실패: " + e.getMessage()).done();
        }

        if (fileUrls.isEmpty()) {
            return result.failed("디렉토리에서 .sdf.gz 파일을 찾을 수 없습니다: " + directoryUrl).done();
        }

        // maxFiles 제한 적용
        int totalDiscovered = fileUrls.size();
        if (maxFiles > 0 && fileUrls.size() > maxFiles) {
            fileUrls = fileUrls.subList(0, maxFiles);
            log.info("전체 {}개 중 {}개만 처리 (maxFiles={})", totalDiscovered, maxFiles, maxFiles);
        }

        return runFileList(result, fileUrls);
    }

    /**
     * 미리 발견된 SDF 파일 URL 리스트를 순차적으로 다운로드·파싱·적재한다.
     * runFull(), runMonthly(), runDirectory()에서 공통으로 사용한다.
     */
    private PipelineResult runFileList(PipelineResult result, List<String> fileUrls) {
        Instant downloadStart = Instant.now();
        log.info("파일 처리 시작: {}개 .sdf.gz", fileUrls.size());

        int totalRows = 0, insertedCount = 0, totalBatches = 0;
        int successCount = 0;

        for (int i = 0; i < fileUrls.size(); i++) {
            String fileUrl = fileUrls.get(i);
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            Instant fileStart = Instant.now();

            try {
                log.info("[{}/{}] 처리 시작: {}", i + 1, fileUrls.size(), fileName);

                // Download
                SdfMetadata downloaded = sdfRepository.download(fileUrl);

                // Extract (pass-through)
                List<SdfMetadata> extracted = sdfRepository.extract(downloaded).toList();

                // Parse + Load
                int[] fileCounters = processExtractedFiles(extracted, fileUrl, result);
                int fileRows = fileCounters[0];
                int fileInserted = fileCounters[1];
                int fileBatches = fileCounters[2];
                int fileSkipped = fileCounters[3];

                long elapsedMs = Duration.between(fileStart, Instant.now()).toMillis();

                result.addFileResult(new PipelineResult.FileResult(
                        fileName, downloaded.fileSizeBytes(),
                        fileRows, fileSkipped, elapsedMs, null));

                totalRows += fileRows;
                insertedCount += fileInserted;
                totalBatches += fileBatches;
                successCount++;

                log.info("[{}/{}] 완료: {} | {} records | {}ms",
                        i + 1, fileUrls.size(), fileName, fileRows, elapsedMs);

            } catch (Exception e) {
                long elapsedMs = Duration.between(fileStart, Instant.now()).toMillis();
                log.error("[{}/{}] 실패: {} - {}", i + 1, fileUrls.size(), fileName, e.getMessage());
                result.addFileResult(new PipelineResult.FileResult(
                        fileName, 0, 0, 0, elapsedMs, e.getMessage()));
                // 개별 파일 실패 시 계속 진행
            }
        }

        // 집계 메트릭 설정
        Duration totalDownloadElapsed = Duration.between(downloadStart, Instant.now());
        result.withDownload(fileUrls.size(), result.totalDownloadedBytes(), totalDownloadElapsed)
              .withParsing(totalRows, 0, null)
              .withLoading(insertedCount, totalBatches, null);

        // 상태 결정
        if (successCount == 0) {
            result.failed("모든 파일 처리 실패 (" + fileUrls.size() + "개 중 0개 성공)");
        } else if (result.failedFileCount() > 0) {
            result.partial();
            log.warn("Pipeline PARTIAL | batchId={} | {}/{} files succeeded",
                    result.batchId(), successCount, fileUrls.size());
        }

        log.info("Pipeline OK | batchId={} | files={} | parsed={} | loaded={}",
                result.batchId(), successCount, totalRows, insertedCount);
        return result.done();
    }

    /**
     * 추출된 SDF 파일들을 파싱하여 DB에 저장한다.
     *
     * @return [totalRows, insertedCount, totalBatches, skippedRows]
     */
    private int[] processExtractedFiles(List<SdfMetadata> extractedFiles,
                                         String sourceUrl,
                                         PipelineResult result) {
        int[] counters = {0, 0, 0, 0}; // totalRows, insertedCount, totalBatches, skippedRows
        final int CHUNK_SIZE = 50000;
        List<SdfRecord> chunk = new ArrayList<>();

        for (SdfMetadata fileMeta : extractedFiles) {
            int skipped = sdfRepository.parseAndConsume(fileMeta, record -> {
                chunk.add(record);
                counters[0]++;
                if (chunk.size() >= CHUNK_SIZE) {
                    counters[1] += flushChunk(chunk, sourceUrl, result.batchId());
                    counters[2]++;
                    chunk.clear();
                }
            });
            counters[3] += skipped;
        }
        // 잔여 청크
        if (!chunk.isEmpty()) {
            counters[1] += flushChunk(chunk, sourceUrl, result.batchId());
            counters[2]++;
        }

        return counters;
    }

    /** SdfRecord 청크를 StoredData로 변환 후 DB에 저장 */
    private int flushChunk(List<SdfRecord> chunk, String sourceUrl, String batchId) {
        List<StoredData> storedDataList = new ArrayList<>();
        for (SdfRecord record : chunk) {
            for (SdfRecord.Property prop : record.properties()) {
                storedDataList.add(StoredData.create(
                        record.compoundId(), prop.name(), prop.value(),
                        sourceUrl, batchId));
            }
        }
        return bulkLoadService.loadAll(storedDataList);
    }
}
