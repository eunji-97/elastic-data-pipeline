package com.example.experiment2.domain;

/**
 * 실험 2 - ES Alias 관리 Value Object.
 * Blue-Green swap을 위해 alias와 인덱스 버전 관계를 표현한다.
 */
public class IndexAlias {

    private final String aliasName;
    private final String activeIndex;   // 현재 서비스 중인 인덱스 (Green)
    private final String nextIndex;     // 새로 생성할 인덱스 (Blue)
    private final int version;

    public IndexAlias(String aliasName, int currentVersion) {
        this.aliasName = aliasName;
        this.version = currentVersion + 1;
        this.activeIndex = aliasName + "_v" + currentVersion;
        this.nextIndex = aliasName + "_v" + this.version;
    }

    public String aliasName() { return aliasName; }
    public String activeIndex() { return activeIndex; }
    public String nextIndex() { return nextIndex; }
    public int version() { return version; }
}
