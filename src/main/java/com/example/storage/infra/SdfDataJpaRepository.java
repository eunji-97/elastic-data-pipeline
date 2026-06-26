package com.example.storage.infra;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for SDF data.
 */
interface SdfDataJpaRepository extends JpaRepository<StoredDataJpaEntity, Long> {

    long countByBatchId(String batchId);

    java.util.List<StoredDataJpaEntity> findByBatchId(String batchId);

    java.util.List<StoredDataJpaEntity> findByBatchId(String batchId, org.springframework.data.domain.Pageable pageable);
}
