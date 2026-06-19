package com.example.storage.infra;

import com.example.storage.domain.StoredData;
import com.example.storage.domain.StoredDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * StoredDataRepository의 JPA 구현체.
 * 도메인과 JPA 사이의 변환을 담당.
 */
@Repository
class StoredDataRepositoryImpl implements StoredDataRepository {

    private static final Logger log = LoggerFactory.getLogger(StoredDataRepositoryImpl.class);

    private final SdfDataJpaRepository jpaRepository;

    StoredDataRepositoryImpl(SdfDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int saveAll(List<StoredData> batch) {
        List<StoredDataJpaEntity> entities = batch.stream()
                .map(StoredDataJpaEntity::from)
                .toList();

        List<StoredDataJpaEntity> saved = jpaRepository.saveAll(entities);
        return saved.size();
    }

    @Override
    public long countByBatchId(String batchId) {
        return jpaRepository.countByBatchId(batchId);
    }
}
