package com.example.experiment2.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.example.experiment2.domain.IndexAlias;
import com.example.experiment2.domain.VerificationResult;
import com.example.pipeline.PipelineResult;
import com.example.pipeline.RunPipelineService;
import com.example.storage.domain.StoredData;
import com.example.storage.domain.StoredDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 실험 2 - Blue-Green ES 적재 서비스.
 *
 * SDF 데이터 → RDB 저장 → 신규 ES 인덱스 생성 → Reindex → 업데이트 → 샘플링 검증 → alias swap.
 * 실패 시 기존 인덱스를 유지하고 신규 인덱스만 삭제한다.
 */
@Service
@ConditionalOnProperty(name = "experiment2.enabled", havingValue = "true", matchIfMissing = false)
public class BlueGreenService {

    private static final Logger log = LoggerFactory.getLogger(BlueGreenService.class);
    private static final String ALIAS = "compounds";

    private final RunPipelineService pipelineService;
    private final SamplingVerifier verifier;
    private final StoredDataRepository rdbRepository;
    private final ElasticsearchClient esClient;

    // 초기 버전: ES에서 현재 alias가 가리키는 인덱스로부터 결정
    private int currentVersion;

    public BlueGreenService(RunPipelineService pipelineService,
                            SamplingVerifier verifier,
                            StoredDataRepository rdbRepository,
                            ElasticsearchClient esClient) {
        this.pipelineService = pipelineService;
        this.verifier = verifier;
        this.rdbRepository = rdbRepository;
        this.esClient = esClient;
        this.currentVersion = detectCurrentVersion();
        log.info("BlueGreenService 초기화: currentVersion={}", currentVersion);
    }

    /**
     * Blue-Green 전체 사이클 실행.
     *
     * @param sourceUrl SDF 파일 URL
     * @return 사이클 결과
     */
    public CycleResult runCycle(String sourceUrl) {
        Instant cycleStart = Instant.now();
        IndexAlias alias = new IndexAlias(ALIAS, currentVersion);
        Map<String, Long> stepTimings = new LinkedHashMap<>();
        String error = null;

        log.info("=== Experiment 2: Blue-Green Cycle 시작 ===");
        log.info("Active: {} → Next: {}", alias.activeIndex(), alias.nextIndex());

        try {
            // Step 1: RDB 저장 (기존 파이프라인 활용)
            Instant t1 = Instant.now();
            PipelineResult pipeline = pipelineService.run(sourceUrl, 0);
            stepTimings.put("1-rdb-load-ms", Duration.between(t1, Instant.now()).toMillis());

            if (pipeline.status() == PipelineResult.Status.FAILED) {
                throw new RuntimeException("RDB 적재 실패: " + pipeline.errorMessage());
            }
            log.info("[1/6] RDB 저장 완료: {} rows", pipeline.loading() != null ? pipeline.loading().insertedCount() : 0);

            // Step 2: 신규 인덱스 생성 (Blue)
            t1 = Instant.now();
            createNextIndex(alias);
            stepTimings.put("2-create-index-ms", Duration.between(t1, Instant.now()).toMillis());
            log.info("[2/6] 신규 인덱스 생성: {}", alias.nextIndex());

            // Step 3: Reindex (기존 → 신규). 최초 실행이면 skip.
            t1 = Instant.now();
            boolean hasExistingData = indexExists(alias.activeIndex()) && hasDocs(alias.activeIndex());
            if (hasExistingData) {
                reindexFromActive(alias);
                stepTimings.put("3-reindex-ms", Duration.between(t1, Instant.now()).toMillis());
                log.info("[3/6] Reindex 완료: {} → {}", alias.activeIndex(), alias.nextIndex());
            } else {
                stepTimings.put("3-reindex-ms", 0L);
                log.info("[3/6] Reindex skip (최초 실행 — 기존 데이터 없음)");
            }

            // Step 4: 신규 데이터 _bulk 업데이트 (RDB → ES)
            t1 = Instant.now();
            int updated = loadRdbToEs(alias.nextIndex(), pipeline.batchId());
            stepTimings.put("4-bulk-update-ms", Duration.between(t1, Instant.now()).toMillis());
            log.info("[4/6] _bulk 업데이트: {} documents", updated);

            // Step 5: 샘플링 검증
            t1 = Instant.now();
            VerificationResult verification = verifier.verify(pipeline.batchId(), alias.nextIndex());
            stepTimings.put("5-verification-ms", Duration.between(t1, Instant.now()).toMillis());

            if (!verification.passed()) {
                log.warn("[5/6] 검증 실패! mismatches={}", verification.mismatches().size());
                log.warn("  → 신규 인덱스 {} 삭제, {} 유지", alias.nextIndex(), alias.activeIndex());
                deleteIndex(alias.nextIndex());
                throw new RuntimeException("샘플링 검증 실패: " + verification.matched()
                        + "/" + verification.totalChecked() + " matched");
            }
            log.info("[5/6] 샘플링 검증 통과: {}/{}", verification.matched(), verification.totalChecked());

            // Step 6: Alias swap
            t1 = Instant.now();
            aliasSwap(alias);
            stepTimings.put("6-alias-swap-ms", Duration.between(t1, Instant.now()).toMillis());
            currentVersion = alias.version();
            log.info("[6/6] Alias swap 완료: {} → {}", alias.activeIndex(), alias.nextIndex());

            Duration totalElapsed = Duration.between(cycleStart, Instant.now());
            log.info("=== Experiment 2 완료 | {}ms | version {} ===", totalElapsed.toMillis(), currentVersion);

            return new CycleResult(true, currentVersion, alias.nextIndex(),
                    pipeline.loading() != null ? pipeline.loading().insertedCount() : 0,
                    verification.totalChecked(),
                    verification.matched(),
                    totalElapsed.toMillis(),
                    stepTimings
            );

        } catch (Exception e) {
            log.error("Blue-Green cycle 실패: {}", e.getMessage());
            // 안전장치: 신규 인덱스 정리
            try { deleteIndex(alias.nextIndex()); } catch (Exception ignored) {}
            Duration totalElapsed = Duration.between(cycleStart, Instant.now());
            return new CycleResult(false, currentVersion, alias.activeIndex(),
                    0, 0, 0, totalElapsed.toMillis(), stepTimings, e.getMessage());
        }
    }

    /**
     * ES에서 alias가 가리키는 현재 인덱스를 조회하여 버전을 결정한다.
     * 최초 실행(alias 없음)이면 0을 반환.
     */
    private int detectCurrentVersion() {
        try {
            var aliasResponse = esClient.indices().getAlias(
                    GetAliasRequest.of(g -> g.index(ALIAS + "_v*"))
            );
            // "compounds_v3" → version 3 추출
            return aliasResponse.result().keySet().stream()
                    .filter(idx -> idx.startsWith(ALIAS + "_v"))
                    .mapToInt(idx -> {
                        try {
                            return Integer.parseInt(idx.substring(idx.lastIndexOf('v') + 1));
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(0);
        } catch (ElasticsearchException e) {
            // alias가 아예 없는 최초 실행
            log.info("기존 인덱스 없음 — 최초 실행 모드");
            return 0;
        } catch (Exception e) {
            log.warn("버전 감지 실패, 기본값 0 사용: {}", e.getMessage());
            return 0;
        }
    }

    // -- Blue-Green 내부 단계 --

    private void createNextIndex(IndexAlias alias) throws Exception {
        boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(alias.nextIndex()))).value();
        if (!exists) {
            esClient.indices().create(CreateIndexRequest.of(c -> c.index(alias.nextIndex())));
        }
    }

    private boolean indexExists(String indexName) {
        try {
            return esClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasDocs(String indexName) {
        try {
            long count = esClient.count(c -> c.index(indexName)).count();
            return count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void reindexFromActive(IndexAlias alias) throws Exception {
        // 기존 활성 인덱스에서 새 인덱스로 데이터 복제
        esClient.reindex(r -> r
                .source(src -> src.index(alias.activeIndex()))
                .dest(dst -> dst.index(alias.nextIndex()))
        );
    }

    /**
     * RDB에서 batchId로 StoredData를 페이지 단위로 조회 → compoundId 기준 그룹핑 → ES _bulk 적재.
     * 대용량 데이터도 OOM 없이 처리하기 위해 페이지네이션 적용.
     */
    private int loadRdbToEs(String indexName, String batchId) {
        log.info("[4/6] RDB→ES 적재 시작: batchId={}", batchId);

        long totalRows = rdbRepository.countByBatchId(batchId);
        log.info("  총 RDB rows: {}", totalRows);

        int totalIndexed = 0;
        int pageSize = 50000; // 5만 row씩 읽어서 처리
        int offset = 0;
        Map<String, Map<String, String>> buffer = new LinkedHashMap<>(); // compoundId → properties

        while (offset < totalRows) {
            // 페이지 단위로 RDB에서 읽기
            List<StoredData> rows = rdbRepository.findByBatchIdPaged(batchId, offset, pageSize);
            log.info("  페이지 읽기: offset={} rows={}", offset, rows.size());

            // compoundId 기준 그룹핑
            for (StoredData row : rows) {
                Map<String, String> props = buffer.computeIfAbsent(
                        row.compoundId(), k -> new LinkedHashMap<>());
                props.put(row.propertyName(), row.propertyValue());
            }

            // 버퍼가 5000 도큐먼트 이상 쌓이면 ES로 전송
            while (buffer.size() >= 5000) {
                totalIndexed += flushBuffer(indexName, buffer, batchId, 5000);
            }

            offset += pageSize;
        }

        // 잔여 버퍼 flush
        while (!buffer.isEmpty()) {
            totalIndexed += flushBuffer(indexName, buffer, batchId, buffer.size());
        }

        log.info("  RDB→ES 적재 완료: {} documents indexed", totalIndexed);
        return totalIndexed;
    }

    /**
     * 버퍼에서 count개 도큐먼트를 ES로 _bulk 전송하고 버퍼에서 제거한다.
     */
    private int flushBuffer(String indexName, Map<String, Map<String, String>> buffer,
                             String batchId, int count) {
        List<Map.Entry<String, Map<String, String>>> batch = new ArrayList<>();
        var iter = buffer.entrySet().iterator();
        for (int i = 0; i < count && iter.hasNext(); i++) {
            batch.add(iter.next());
            iter.remove();
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(indexName);
            for (var entry : batch) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("compoundId", entry.getKey());
                doc.put("properties", entry.getValue());
                doc.put("batchId", batchId);
                bulkBuilder.operations(op -> op
                        .index(idx -> idx.id(entry.getKey()).document(doc)));
            }
            BulkResponse response = esClient.bulk(bulkBuilder.build());
            long errors = response.items().stream()
                    .filter(item -> item.error() != null).count();
            int indexed = batch.size() - (int) errors;
            log.info("  Flush: {}/{} docs indexed (buffer remaining: {})", indexed, batch.size(), buffer.size());
            return indexed;
        } catch (Exception e) {
            log.error("  Bulk indexing failed: {}", e.getMessage());
            return 0;
        }
    }

    private void aliasSwap(IndexAlias alias) throws Exception {
        // 원자적 alias 전환
        esClient.indices().updateAliases(ua -> ua
                .actions(
                        co.elastic.clients.elasticsearch.indices.update_aliases.Action.of(a -> a
                                .remove(r -> r.index(alias.activeIndex()).alias(alias.aliasName()))),
                        co.elastic.clients.elasticsearch.indices.update_aliases.Action.of(a -> a
                                .add(ad -> ad.index(alias.nextIndex()).alias(alias.aliasName())))
                )
        );
    }

    private void deleteIndex(String indexName) {
        try {
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            log.info("인덱스 삭제: {}", indexName);
        } catch (Exception e) {
            log.warn("인덱스 삭제 실패 (무시): {} - {}", indexName, e.getMessage());
        }
    }

    /**
     * Blue-Green 사이클 결과.
     */
    public record CycleResult(
            boolean success,
            int version,
            String activeIndex,
            long recordsLoaded,
            int verificationChecked,
            int verificationMatched,
            long totalElapsedMs,
            Map<String, Long> stepTimings,
            String error
    ) {
        public CycleResult(boolean success, int version, String activeIndex,
                           long recordsLoaded, int verificationChecked, int verificationMatched,
                           long totalElapsedMs, Map<String, Long> stepTimings) {
            this(success, version, activeIndex, recordsLoaded,
                    verificationChecked, verificationMatched, totalElapsedMs, stepTimings, null);
        }
    }
}
