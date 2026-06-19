package com.example.sdf.domain;

import java.util.stream.Stream;

/**
 * SDF 데이터 접근 Repository 인터페이스.
 * 도메인 계층에서 정의 — 구현은 infra에.
 */
public interface SdfRepository {

    /**
     * 원본 URL에서 SDF 아카이브를 다운로드하고 메타데이터를 반환한다.
     */
    SdfMetadata download(String sourceUrl);

    /**
     * 다운로드된 아카이브를 해제하고, SDF 파일들의 경로를 반환한다.
     */
    Stream<SdfMetadata> extract(SdfMetadata archive);

    /**
     * 파싱된 SdfRecord들을 청크 단위(Stream)로 제공한다.
     */
    Stream<SdfRecord> parse(SdfMetadata sdfFile);
}
