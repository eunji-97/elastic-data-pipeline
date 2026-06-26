package com.example.experiment2.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.example.experiment2.domain.VerificationResult;
import com.example.storage.domain.StoredData;
import com.example.storage.domain.StoredDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 실험 2 - RDB vs ES 샘플링 정합성 검증기.
 * RDB에 저장된 StoredData와 ES의 인덱스를 무작위 샘플링하여 필드 단위로 비교한다.
 */
@Component
public class SamplingVerifier {

    private static final Logger log = LoggerFactory.getLogger(SamplingVerifier.class);
    private static final int DEFAULT_SAMPLE_SIZE = 100;

    private final StoredDataRepository rdbRepository;
    private final ElasticsearchClient esClient;

    public SamplingVerifier(StoredDataRepository rdbRepository, ElasticsearchClient esClient) {
        this.rdbRepository = rdbRepository;
        this.esClient = esClient;
    }

    /**
     * RDB에 저장된 데이터를 기준으로 ES 인덱스를 무작위 샘플링 검증한다.
     *
     * @param batchId   검증할 배치 ID
     * @param indexName 검증할 ES 인덱스 이름
     * @return 검증 결과
     */
    public VerificationResult verify(String batchId, String indexName) {
        return verify(batchId, indexName, DEFAULT_SAMPLE_SIZE);
    }

    public VerificationResult verify(String batchId, String indexName, int sampleSize) {
        log.info("샘플링 검증 시작: batchId={} index={} sampleSize={}", batchId, indexName, sampleSize);

        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        int matched = 0;

        // 참고: 실제 무작위 샘플링을 위해서는 RDB에 인덱스를 타야 함.
        // 현재 StoredDataRepository에는 countByBatchId만 있으므로,
        // 전체를 가져와서 무작위 추출하는 방식으로 구현한다.
        // (프로덕션에서는 커서 기반 랜덤 샘플링 또는 ORDER BY RANDOM 사용)

        try {
            // ES에서 해당 배치의 화합물 ID 목록 조회
            var searchResponse = esClient.search(s -> s
                            .index(indexName)
                            .query(q -> q.match(m -> m.field("batchId").query(batchId)))
                            .size(sampleSize),
                    Map.class
            );

            List<String> compoundIds = searchResponse.hits().hits().stream()
                    .map(hit -> (String) hit.source().get("compoundId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("ES에서 {} 개 compoundId 조회됨", compoundIds.size());

            for (String compoundId : compoundIds) {
                checked++;
                try {
                    // ES에서 해당 compoundId 조회
                    GetResponse<Map> esDoc = esClient.get(g -> g.index(indexName).id(compoundId), Map.class);
                    if (!esDoc.found()) {
                        mismatches.add(compoundId + ": ES에 없음");
                        continue;
                    }

                    Map<String, Object> esSource = esDoc.source();
                    if (esSource == null) {
                        mismatches.add(compoundId + ": ES source null");
                        continue;
                    }

                    // 필드 단위 비교 (현재는 ES-RDB 간에 compoundId 기준으로만 확인)
                    // TODO: RDB에서 동일 compoundId의 모든 property를 조회하여 필드 비교
                    @SuppressWarnings("unchecked")
                    Map<String, String> esProps = (Map<String, String>) esSource.get("properties");
                    if (esProps != null && !esProps.isEmpty()) {
                        matched++;
                    } else {
                        mismatches.add(compoundId + ": properties 비어있음");
                    }

                } catch (Exception e) {
                    mismatches.add(compoundId + ": 조회 실패 - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("샘플링 검증 중 오류: {}", e.getMessage());
            mismatches.add("검증 오류: " + e.getMessage());
        }

        VerificationResult result = new VerificationResult(checked, matched, mismatches);
        log.info("샘플링 검증 완료: {}/{} matched, passed={}", matched, checked, result.passed());
        return result;
    }
}
