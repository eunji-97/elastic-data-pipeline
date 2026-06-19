package com.example.storage.infra;

import com.example.storage.domain.StoredData;
import jakarta.persistence.*;

/**
 * StoredData의 JPA Entity 구현체.
 * 도메인 Entity를 JPA 애너테이션으로 매핑.
 */
@Entity
@Table(name = "sdf_data", indexes = {
        @Index(name = "idx_compound_id", columnList = "compoundId"),
        @Index(name = "idx_batch_id", columnList = "batchId"),
        @Index(name = "idx_source_url", columnList = "sourceUrl")
})
class StoredDataJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String compoundId;

    @Column(nullable = false, length = 500)
    private String propertyName;

    @Column(columnDefinition = "TEXT")
    private String propertyValue;

    @Column(nullable = false, length = 1024)
    private String sourceUrl;

    @Column(nullable = false, length = 64)
    private String batchId;

    protected StoredDataJpaEntity() {
        // JPA
    }

    /** 도메인 Entity → JPA Entity 변환 */
    static StoredDataJpaEntity from(StoredData domain) {
        StoredDataJpaEntity entity = new StoredDataJpaEntity();
        entity.compoundId = domain.compoundId();
        entity.propertyName = domain.propertyName();
        entity.propertyValue = domain.propertyValue();
        entity.sourceUrl = domain.sourceUrl();
        entity.batchId = domain.batchId();
        return entity;
    }

    /** JPA Entity → 도메인 Entity 변환 */
    StoredData toDomain() {
        StoredData data = StoredData.create(compoundId, propertyName, propertyValue,
                sourceUrl, batchId);
        return data;
    }
}
