package com.example.storage.domain;

import java.util.Objects;

/**
 * 저장소에 적재될 데이터 Entity (Aggregate Root).
 * JPA에 의존하지 않는 순수 도메인 객체.
 */
public class StoredData {

    private Long id;
    private String compoundId;
    private String propertyName;
    private String propertyValue;
    private String sourceUrl;
    private String batchId;

    private StoredData() {
        // factory 전용
    }

    public static StoredData create(String compoundId, String propertyName,
                                     String propertyValue, String sourceUrl,
                                     String batchId) {
        StoredData data = new StoredData();
        data.compoundId = Objects.requireNonNull(compoundId);
        data.propertyName = Objects.requireNonNull(propertyName);
        data.propertyValue = Objects.requireNonNull(propertyValue);
        data.sourceUrl = Objects.requireNonNull(sourceUrl);
        data.batchId = Objects.requireNonNull(batchId);
        return data;
    }

    public Long id() { return id; }
    public String compoundId() { return compoundId; }
    public String propertyName() { return propertyName; }
    public String propertyValue() { return propertyValue; }
    public String sourceUrl() { return sourceUrl; }
    public String batchId() { return batchId; }
}
