package com.example.storage.infra;

import com.example.storage.domain.StoredData;
import com.example.storage.domain.StoredDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * StoredDataRepository 구현체. JPA 기반 저장.
 */
@Repository
class StoredDataRepositoryImpl implements StoredDataRepository {

    private static final Logger log = LoggerFactory.getLogger(StoredDataRepositoryImpl.class);

    private final SdfDataJpaRepository jpaRepository;

    StoredDataRepositoryImpl(SdfDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int saveAll(List<StoredData> list) {
        List<StoredDataJpaEntity> entities = list.stream()
                .map(StoredDataJpaEntity::from)
                .toList();
        List<StoredDataJpaEntity> saved = jpaRepository.saveAll(entities);
        return saved.size();
    }

    @Override
    public long countByBatchId(String batchId) {
        return jpaRepository.countByBatchId(batchId);
    }

    @Override
    public List<StoredData> findByBatchId(String batchId) {
        return jpaRepository.findByBatchId(batchId).stream()
                .map(StoredDataJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<StoredData> findByBatchIdPaged(String batchId, int offset, int limit) {
        return jpaRepository.findByBatchId(batchId, PageRequest.of(offset / limit, limit)).stream()
                .map(StoredDataJpaEntity::toDomain)
                .toList();
    }
}
