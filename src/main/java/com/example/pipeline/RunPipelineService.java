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
import java.util.List;
import java.util.stream.Stream;

/**
 * 파이프라인 실행 Use Case (애플리케이션 서비스).
 * SDF → Storage 오케스트레이션을 담당한다.
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
     * SDF URL로부터 전체 파이프라인을 실행한다.
     *
     * @param sourceUrl SDF 아카이브 URL
     * @return 파이프라인 실행 결과
     */
    public PipelineResult run(String sourceUrl) {
        PipelineResult result = PipelineResult.empty();
        log.info("Pipeline started | batchId={} | source={}", result.batchId(), sourceUrl);

        try {
            // 1. Download
            Instant start = Instant.now();
            SdfMetadata archive = sdfRepository.download(sourceUrl);
            result.withDownload(archive.fileSizeBytes(), Duration.between(start, Instant.now()));
            log.info("Step 1/4 Download OK | {} bytes", archive.fileSizeBytes());

            // 2. Extract
            start = Instant.now();
            List<SdfMetadata> sdfFiles = sdfRepository.extract(archive).toList();
            result.withExtraction(sdfFiles.size(), Duration.between(start, Instant.now()));
            log.info("Step 2/4 Extract OK | {} files", sdfFiles.size());

            // 3. Parse + 4. Load (streaming: chunk 단위로)
            int totalParsed = 0;
            int totalLoaded = 0;
            int batches = 0;

            for (SdfMetadata sdfFile : sdfFiles) {
                // 청크 단위 파싱 (한 번에 전부 메모리에 올리지 않음)
                List<SdfRecord> chunk = sdfRepository.parse(sdfFile).toList();
                totalParsed += chunk.size();

                // SdfRecord → StoredData 변환 (도메인 매핑)
                List<StoredData> dataBatch = chunk.stream()
                        .<StoredData>mapMulti((record, consumer) -> {
                            for (var prop : record.properties()) {
                                consumer.accept(StoredData.create(
                                        record.compoundId(),
                                        prop.name(),
                                        prop.value(),
                                        sourceUrl,
                                        result.batchId()));
                            }
                        })
                        .toList();

                int loaded = bulkLoadService.loadAll(dataBatch);
                totalLoaded += loaded;
                batches++;
            }

            result.withParsing(totalParsed, 0, null) // TODO: skipped count
                    .withLoading(totalLoaded, batches, null);

            log.info("Pipeline OK | batchId={} | parsed={} | loaded={}",
                    result.batchId(), totalParsed, totalLoaded);

        } catch (Exception e) {
            log.error("Pipeline failed | batchId={}", result.batchId(), e);
            result.failed(e.getMessage());
        }

        return result.done();
    }
}
