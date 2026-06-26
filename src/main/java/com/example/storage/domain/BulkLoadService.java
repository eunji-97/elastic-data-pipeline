package com.example.storage.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 대량 데이터를 배치 단위로 나누어 저장하는 서비스.
 */
public class BulkLoadService {

    private static final Logger log = LoggerFactory.getLogger(BulkLoadService.class);
    private static final int BATCH_SIZE = 1000;

    private final StoredDataRepository repository;

    public BulkLoadService(StoredDataRepository repository) {
        this.repository = repository;
    }

    /**
     * 리스트를 BATCH_SIZE 단위로 나누어 저장하고 총 삽입 건수를 반환한다.
     */
    public int loadAll(List<StoredData> list) {
        int totalInserted = 0;

        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, list.size());
            List<StoredData> batch = list.subList(i, end);

            int inserted = repository.saveAll(batch);
            totalInserted += inserted;

            log.debug("Batch {}: {}/{} rows inserted",
                    (i / BATCH_SIZE + 1), inserted, batch.size());
        }

        return totalInserted;
    }
}
