package com.example.storage.infra;

import org.springframework.data.jpa.repository.JpaRepository;

interface SdfDataJpaRepository extends JpaRepository<StoredDataJpaEntity, Long> {

    long countByBatchId(String batchId);
}
