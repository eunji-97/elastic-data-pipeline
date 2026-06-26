package com.example.storage.domain;

import java.util.List;

/**
 * StoredData 저장소 인터페이스.
 */
public interface StoredDataRepository {

    int saveAll(List<StoredData> list);

    long countByBatchId(String batchId);

    /**
     * batchId로 저장된 모든 StoredData를 조회한다.
     */
    List<StoredData> findByBatchId(String batchId);

    /**
     * batchId로 저장된 StoredData를 페이지 단위로 조회한다.
     * @param offset 시작 위치
     * @param limit  최대 건수
     */
    List<StoredData> findByBatchIdPaged(String batchId, int offset, int limit);
}
