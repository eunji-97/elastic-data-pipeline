package com.example.sdf.domain;

import java.util.List;
import java.util.Objects;

/**
 * 파싱된 SDF 레코드 한 건.
 * 불변 Value Object — ID 없음.
 */
public final class SdfRecord {

    private final String compoundId;
    private final List<Property> properties;

    public SdfRecord(String compoundId, List<Property> properties) {
        this.compoundId = Objects.requireNonNull(compoundId);
        this.properties = List.copyOf(properties); // 방어 복사
    }

    public String compoundId() {
        return compoundId;
    }

    public List<Property> properties() {
        return properties;
    }

    public String propertyValue(String name) {
        return properties.stream()
                .filter(p -> p.name().equals(name))
                .map(Property::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * SDF 속성 한 쌍.
     */
    public record Property(String name, String value) {
        public Property {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SdfRecord that)) return false;
        return compoundId.equals(that.compoundId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(compoundId);
    }

    @Override
    public String toString() {
        return "SdfRecord[compoundId=" + compoundId + ", properties=" + properties.size() + "]";
    }
}
