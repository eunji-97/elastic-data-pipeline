package com.example.storage.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 벌크 적재 도메인 서비스.
 * 순수 도메인 로직만 — 기술 의존 없음.
 */
public class BulkLoadService {

    private static final Logger log = LoggerFactory.getLogger(BulkLoadService.class);
    private static final int BATCH_SIZE = 1_000;

    private final StoredDataRepository repository;

    public BulkLoadService(StoredDataRepository repository) {
        this.repository = repository;
    }

    /**
     * 여러 건의 StoredData를 BATCH_SIZE 단위로 나누어 저장한다.
     */
    public int loadAll(List<StoredData> dataList) {
        int totalInserted = 0;

        for (int i = 0; i < dataList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, dataList.size());
            List<StoredData> batch = dataList.subList(i, end);
            int inserted = repository.saveAll(batch);
            totalInserted += inserted;

            log.debug("Batch {}: {}/{} rows inserted", i / BATCH_SIZE + 1,
                    inserted, batch.size());
        }

        return totalInserted;
    }
}
