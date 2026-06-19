package com.example.storage.domain;

import java.util.List;

/**
 * 데이터 저장 Repository 인터페이스.
 * 도메인 계층에서 정의 — 구현은 infra에.
 */
public interface StoredDataRepository {

    /**
     * StoredData 리스트를 벌크 저장한다.
     * @return 실제 저장된 건수
     */
    int saveAll(List<StoredData> batch);

    /**
     * 특정 batchId로 저장된 데이터 건수를 반환한다.
     */
    long countByBatchId(String batchId);
}
