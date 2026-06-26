package com.example.experiment1.domain;

import java.util.Map;

/**
 * 실험 1 - ES 화합물 도큐먼트.
 * SDF 데이터를 ES에 직접 인덱싱하기 위한 매핑.
 */
public class EsCompoundDocument {

    private String compoundId;
    private Map<String, String> properties;
    private String sourceUrl;
    private String batchId;

    public EsCompoundDocument() {
    }

    public EsCompoundDocument(String compoundId, Map<String, String> properties,
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
