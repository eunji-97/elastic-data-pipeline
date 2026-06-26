package com.example.storage.domain;

import java.util.Objects;

/**
 * SDF 파싱 결과를 저장하는 도메인 엔티티 (EAV 모델).
 * 각 Property는 개별 row로 저장된다.
 */
public class StoredData {

    private Long id;
    private String compoundId;
    private String propertyName;
    private String propertyValue;
    private String sourceUrl;
    private String batchId;

    private StoredData() {
    }

    /**
     * 도메인 엔티티를 생성한다. 모든 필드는 non-null이어야 한다.
     */
    public static StoredData create(String compoundId, String propertyName,
                                     String propertyValue, String sourceUrl, String batchId) {
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
