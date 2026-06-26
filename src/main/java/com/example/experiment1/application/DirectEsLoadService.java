package com.example.experiment1.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.example.experiment1.domain.EsCompoundDocument;
import com.example.sdf.domain.SdfMetadata;
import com.example.sdf.domain.SdfRecord;
import com.example.sdf.domain.SdfRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실험 1 - Direct ES 적재 서비스 (베이스라인).
 * SDF 데이터를 ES에 직접 벌크 인덱싱한다. 정합성 검증 없음.
 */
@Service
@ConditionalOnProperty(name = "experiment1.enabled", havingValue = "true", matchIfMissing = false)
public class DirectEsLoadService {

    private static final Logger log = LoggerFactory.getLogger(DirectEsLoadService.class);
    private static final String INDEX_NAME = "compounds_v1";

    private final SdfRepository sdfRepository;
    private final ElasticsearchClient esClient;

    public DirectEsLoadService(SdfRepository sdfRepository, ElasticsearchClient esClient) {
        this.sdfRepository = sdfRepository;
        this.esClient = esClient;
    }

    /**
     * SDF 데이터를 다운로드 → 파싱 → ES 직접 적재.
     *
     * 단일 파일 URL(.sdf/.sdf.gz)이면 해당 파일만 처리하고,
     * 디렉토리 URL이면 내부의 모든 .sdf.gz 파일을 발견하여 일괄 처리한다.
     *
     * @param sourceUrl SDF 파일 URL 또는 디렉토리 리스팅 URL
     * @return 적재 결과 (건수, 소요 시간)
     */
    public LoadResult load(String sourceUrl) {
        boolean isSingleFile = sourceUrl.endsWith(".sdf") || sourceUrl.endsWith(".sdf.gz");
        if (isSingleFile) {
            return loadSingleFile(sourceUrl);
        }
        return loadDirectory(sourceUrl);
    }

    /** 단일 SDF 파일 처리 (기존 로직). */
    private LoadResult loadSingleFile(String sourceUrl) {
        Instant start = Instant.now();
        log.info("=== Experiment 1: Direct ES Load (단일 파일) | source={} ===", sourceUrl);

        try {
            // Step 1: Download
            SdfMetadata downloaded = sdfRepository.download(sourceUrl);
            log.info("[1/3] Download 완료: {} bytes", downloaded.fileSizeBytes());

            // Step 2: Extract + Parse
            List<SdfRecord> allRecords = new ArrayList<>();
            List<SdfMetadata> extracted = sdfRepository.extract(downloaded).toList();
            for (SdfMetadata file : extracted) {
                List<SdfRecord> records = sdfRepository.parse(file).toList();
                allRecords.addAll(records);
            }
            log.info("[2/3] Parse 완료: {} compounds", allRecords.size());

            // Step 3: Direct ES bulk indexing
            int indexed = bulkIndexToEs(allRecords, sourceUrl);
            log.info("[3/3] ES 적재 완료: {} documents", indexed);

            Duration elapsed = Duration.between(start, Instant.now());
            log.info("=== Experiment 1 완료 | {} compounds | {}ms ===", indexed, elapsed.toMillis());

            return new LoadResult(indexed, allRecords.size() - indexed, elapsed.toMillis(), 1, 0);

        } catch (Exception e) {
            log.error("Experiment 1 실패", e);
            Duration elapsed = Duration.between(start, Instant.now());
            return new LoadResult(0, 0, elapsed.toMillis(), 0, 1, e.getMessage());
        }
    }

    /** 디렉토리 모드: 모든 .sdf.gz 파일을 발견하여 ES에 적재. */
    private LoadResult loadDirectory(String directoryUrl) {
        Instant start = Instant.now();
        log.info("=== Experiment 1: Direct ES Load (디렉토리 모드) | source={} ===", directoryUrl);

        List<String> fileUrls;
        try {
            fileUrls = sdfRepository.discoverSdfUrls(directoryUrl);
        } catch (Exception e) {
            log.error("파일 목록 조회 실패: {}", directoryUrl, e);
            Duration elapsed = Duration.between(start, Instant.now());
            return new LoadResult(0, 0, elapsed.toMillis(), 0, 0, e.getMessage());
        }

        if (fileUrls.isEmpty()) {
            Duration elapsed = Duration.between(start, Instant.now());
            return new LoadResult(0, 0, elapsed.toMillis(), 0, 0,
                    "디렉토리에서 .sdf.gz 파일을 찾을 수 없습니다: " + directoryUrl);
        }

        log.info("디렉토리 모드: {}개 .sdf.gz 파일 발견", fileUrls.size());

        int totalIndexed = 0, totalParsed = 0, successCount = 0;

        for (int i = 0; i < fileUrls.size(); i++) {
            String fileUrl = fileUrls.get(i);
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);

            try {
                log.info("[{}/{}] 처리 시작: {}", i + 1, fileUrls.size(), fileName);

                // Download
                SdfMetadata downloaded = sdfRepository.download(fileUrl);

                // Parse and ES bulk (스트리밍 방식)
                int[] fileResult = new int[2]; // [parsed, indexed]
                String batchId = Long.toHexString(System.nanoTime());
                List<EsCompoundDocument> batch = new ArrayList<>();

                sdfRepository.parseAndConsume(downloaded, record -> {
                    Map<String, String> props = new HashMap<>();
                    for (SdfRecord.Property prop : record.properties()) {
                        props.put(prop.name(), prop.value());
                    }
                    batch.add(new EsCompoundDocument(record.compoundId(), props, fileUrl, batchId));
                    fileResult[0]++;

                    if (batch.size() >= 1000) {
                        fileResult[1] += sendBulk(batch);
                        batch.clear();
                    }
                });
                // 잔여 배치
                if (!batch.isEmpty()) {
                    fileResult[1] += sendBulk(batch);
                }

                totalParsed += fileResult[0];
                totalIndexed += fileResult[1];
                successCount++;
                log.info("[{}/{}] 완료: {} | parsed={} indexed={}", i + 1, fileUrls.size(),
                        fileName, fileResult[0], fileResult[1]);

            } catch (Exception e) {
                log.error("[{}/{}] 실패: {} - {}", i + 1, fileUrls.size(), fileName, e.getMessage());
                // 개별 파일 실패 시 계속 진행
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        int failedCount = fileUrls.size() - successCount;
        log.info("=== Experiment 1 완료 | files={} | parsed={} | indexed={} | {}ms ===",
                successCount, totalParsed, totalIndexed, elapsed.toMillis());

        if (successCount == 0) {
            return new LoadResult(0, 0, elapsed.toMillis(), 0, fileUrls.size(),
                    "모든 파일 처리 실패 (" + fileUrls.size() + "개 중 0개 성공)");
        }
        return new LoadResult(totalIndexed, totalParsed - totalIndexed, elapsed.toMillis(),
                fileUrls.size(), failedCount);
    }

    /**
     * SdfRecord 리스트 → ESCompoundDocument 변환 → ES _bulk 인덱싱.
     */
    private int bulkIndexToEs(List<SdfRecord> records, String sourceUrl) {
        String batchId = Long.toHexString(System.nanoTime());
        int totalIndexed = 0;

        List<EsCompoundDocument> batch = new ArrayList<>();
        for (SdfRecord record : records) {
            Map<String, String> props = new HashMap<>();
            for (SdfRecord.Property prop : record.properties()) {
                props.put(prop.name(), prop.value());
            }
            batch.add(new EsCompoundDocument(record.compoundId(), props, sourceUrl, batchId));

            // 1000건 단위로 벌크 전송
            if (batch.size() >= 1000) {
                totalIndexed += sendBulk(batch);
                batch.clear();
            }
        }
        // 잔여 배치
        if (!batch.isEmpty()) {
            totalIndexed += sendBulk(batch);
        }

        return totalIndexed;
    }

    private int sendBulk(List<EsCompoundDocument> batch) {
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(INDEX_NAME);
            for (EsCompoundDocument doc : batch) {
                bulkBuilder.operations(op -> op
                        .index(idx -> idx.id(doc.getCompoundId()).document(doc)));
            }
            BulkResponse response = esClient.bulk(bulkBuilder.build());
            long errors = response.items().stream()
                    .filter(item -> item.error() != null).count();
            return batch.size() - (int) errors;
        } catch (Exception e) {
            log.error("Bulk indexing failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 적재 결과.
     */
    public record LoadResult(int indexed, int skipped, long elapsedMs,
                              int fileCount, int failedFileCount, String error) {
        public LoadResult(int indexed, int skipped, long elapsedMs, int fileCount, int failedFileCount) {
            this(indexed, skipped, elapsedMs, fileCount, failedFileCount, null);
        }
        public boolean success() { return error == null; }
    }
}
