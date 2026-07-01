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

        try {
            // Step 1: RDB에서 batchId 기준으로 compoundId 목록을 무작위 추출
            // findAll 대신 페이징을 활용하여 전체 compoundId 수집 후 샘플링
            long totalRows = rdbRepository.countByBatchId(batchId);
            if (totalRows == 0) {
                log.warn("RDB에 batchId={} 데이터가 없습니다", batchId);
                return new VerificationResult(0, 0, List.of("RDB에 데이터 없음"));
            }

            // RDB에서 compoundId 목록 수집 (최대 5000개까지 스캔하여 샘플링 후보 확보)
            Set<String> compoundIdCandidates = new LinkedHashSet<>();
            int scanLimit = Math.min((int) totalRows, 5000);
            int pageSize = 1000;
            for (int offset = 0; offset < scanLimit && compoundIdCandidates.size() < sampleSize * 5; offset += pageSize) {
                List<StoredData> rows = rdbRepository.findByBatchIdPaged(batchId, offset, pageSize);
                for (StoredData row : rows) {
                    compoundIdCandidates.add(row.compoundId());
                }
            }

            if (compoundIdCandidates.isEmpty()) {
                log.warn("RDB에서 compoundId를 찾을 수 없습니다");
                return new VerificationResult(0, 0, List.of("RDB에서 compoundId를 찾을 수 없음"));
            }

            // 무작위 샘플링
            List<String> candidateList = new ArrayList<>(compoundIdCandidates);
            Collections.shuffle(candidateList);
            List<String> sampleCompoundIds = candidateList.subList(0, Math.min(sampleSize, candidateList.size()));

            log.info("RDB에서 {}개 compoundId 샘플링 완료 (후보: {}개)", sampleCompoundIds.size(), candidateList.size());

            // Step 2: 각 compoundId에 대해 RDB vs ES 비교
            for (String compoundId : sampleCompoundIds) {
                checked++;
                try {
                    // RDB에서 해당 compoundId의 모든 프로퍼티 조회
                    List<StoredData> rdbRows = rdbRepository.findByBatchIdAndCompoundId(batchId, compoundId);
                    if (rdbRows.isEmpty()) {
                        mismatches.add(compoundId + ": RDB에 없음");
                        continue;
                    }

                    // RDB 프로퍼티를 Map으로 변환
                    Map<String, String> rdbProps = new LinkedHashMap<>();
                    for (StoredData row : rdbRows) {
                        rdbProps.put(row.propertyName(), row.propertyValue());
                    }

                    // ES에서 해당 compoundId 조회
                    GetResponse<Map> esDoc = esClient.get(g -> g.index(indexName).id(compoundId), Map.class);
                    if (!esDoc.found()) {
                        mismatches.add(compoundId + ": ES에 없음 (RDB에는 " + rdbProps.size() + "개 프로퍼티 존재)");
                        continue;
                    }

                    Map<String, Object> esSource = esDoc.source();
                    if (esSource == null) {
                        mismatches.add(compoundId + ": ES source null");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, String> esProps = (Map<String, String>) esSource.get("properties");
                    if (esProps == null || esProps.isEmpty()) {
                        mismatches.add(compoundId + ": ES properties 비어있음 (RDB: " + rdbProps.size() + "개)");
                        continue;
                    }

                    // RDB vs ES 프로퍼티 단위 비교
                    boolean compoundMatched = true;
                    for (Map.Entry<String, String> rdbEntry : rdbProps.entrySet()) {
                        String propName = rdbEntry.getKey();
                        String rdbValue = rdbEntry.getValue();
                        String esValue = esProps.get(propName);

                        if (esValue == null) {
                            mismatches.add(compoundId + ": ES에 '" + propName + "' 프로퍼티 누락");
                            compoundMatched = false;
                        } else if (!rdbValue.equals(esValue)) {
                            mismatches.add(compoundId + ": '" + propName + "' 불일치 — RDB='" + rdbValue + "' ES='" + esValue + "'");
                            compoundMatched = false;
                        }
                    }

                    // ES에만 있고 RDB에는 없는 프로퍼티 검사
                    for (String esPropName : esProps.keySet()) {
                        if (!rdbProps.containsKey(esPropName)) {
                            mismatches.add(compoundId + ": RDB에 '" + esPropName + "' 프로퍼티 누락");
                            compoundMatched = false;
                        }
                    }

                    if (compoundMatched) {
                        matched++;
                    }

                } catch (Exception e) {
                    mismatches.add(compoundId + ": 조회 실패 - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("샘플링 검증 중 오류: {}", e.getMessage(), e);
            mismatches.add("검증 오류: " + e.getMessage());
        }

        VerificationResult result = new VerificationResult(checked, matched, mismatches);
        log.info("샘플링 검증 완료: {}/{} matched ({}%), passed={}",
                matched, checked, String.format("%.1f", result.matchRate()), result.passed());
        return result;
    }
}
