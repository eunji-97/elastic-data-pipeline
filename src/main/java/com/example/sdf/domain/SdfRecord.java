package com.example.sdf.domain;

import java.util.List;
import java.util.Objects;

/**
 * SDF(Structure Data File)에서 파싱된 단일 화합물 레코드.
 * compoundId로 식별되며 여러 Property를 가진다.
 */
public final class SdfRecord {

    private final String compoundId;
    private final List<Property> properties;

    public SdfRecord(String compoundId, List<Property> properties) {
        this.compoundId = Objects.requireNonNull(compoundId);
        this.properties = List.copyOf(properties);
    }

    public String compoundId() {
        return compoundId;
    }

    public List<Property> properties() {
        return properties;
    }

    /**
     * 프로퍼티 이름으로 값을 조회한다. 없으면 null 반환.
     */
    public String propertyValue(String name) {
        return properties.stream()
                .filter(p -> p.name().equals(name))
                .map(Property::value)
                .findFirst()
                .orElse(null);
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
        return "SdfRecord[compoundId=%s, properties=%d]".formatted(compoundId, properties.size());
    }

    /**
     * SDF 프로퍼티 (name-value 쌍).
     */
    public record Property(String name, String value) {
        public Property {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }
}
