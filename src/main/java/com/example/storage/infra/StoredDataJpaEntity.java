package com.example.storage.infra;

import com.example.storage.domain.StoredData;
import jakarta.persistence.*;

/**
 * StoredData의 JPA Entity. EAV 모델을 RDB 테이블로 매핑한다.
 */
@Entity
@Table(name = "sdf_data", indexes = {
        @Index(name = "idx_compound_id", columnList = "compoundId"),
        @Index(name = "idx_source_url", columnList = "sourceUrl"),
        @Index(name = "idx_batch_id", columnList = "batchId")
})
class StoredDataJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String compoundId;

    @Column(nullable = false, length = 200)
    private String propertyName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String propertyValue;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Column(nullable = false, length = 20)
    private String batchId;

    protected StoredDataJpaEntity() {
    }

    /**
     * 도메인 엔티티 → JPA Entity 변환
     */
    static StoredDataJpaEntity from(StoredData domain) {
        StoredDataJpaEntity entity = new StoredDataJpaEntity();
        entity.compoundId = domain.compoundId();
        entity.propertyName = domain.propertyName();
        entity.propertyValue = domain.propertyValue();
        entity.sourceUrl = domain.sourceUrl();
        entity.batchId = domain.batchId();
        return entity;
    }

    /**
     * JPA Entity → 도메인 엔티티 변환
     */
    StoredData toDomain() {
        return StoredData.create(compoundId, propertyName, propertyValue, sourceUrl, batchId);
    }
}
