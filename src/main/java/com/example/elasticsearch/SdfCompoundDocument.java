package com.example.elasticsearch;

import java.util.Map;

/**
 * SDF 화합물 ES 도큐먼트 매핑.
 * EAV로 저장된 SDF 데이터를 검색 가능한 형태로 변환한다.
 */
public class SdfCompoundDocument {

    private String compoundId;
    private Map<String, String> properties;  // propertyName → propertyValue
    private String sourceUrl;
    private String batchId;

    public SdfCompoundDocument() {
    }

    public SdfCompoundDocument(String compoundId, Map<String, String> properties,
                                String sourceUrl, String batchId) {
        this.compoundId = compoundId;
        this.properties = properties;
        this.sourceUrl = sourceUrl;
        this.batchId = batchId;
    }

    public String getCompoundId() { return compoundId; }
    public void setCompoundId(String compoundId) { this.compoundId = compoundId; }
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
}
