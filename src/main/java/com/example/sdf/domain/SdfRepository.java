package com.example.sdf.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * SDF 데이터 접근 인터페이스.
 * 다운로드 → 추출(압축 해제) → 파싱의 3단계 파이프라인을 정의한다.
 */
public interface SdfRepository {

    /**
     * 디렉토리 URL에서 .sdf.gz 파일 URL 목록을 발견한다.
     * HTTP 디렉토리 리스팅(HTML 페이지) 또는 file:// 로컬 디렉토리에서
     * .sdf.gz 확장자를 가진 파일들의 절대 URL 목록을 반환한다.
     *
     * @param directoryUrl 파일 목록을 조회할 디렉토리 URL
     * @return 발견된 .sdf.gz 파일 URL 리스트 (없으면 빈 리스트)
     */
    List<String> discoverSdfUrls(String directoryUrl);

    /**
     * URL에서 SDF 파일을 다운로드한다.
     *
     * @param sourceUrl 다운로드할 SDF 파일 URL (.sdf 또는 .sdf.gz)
     * @return 다운로드된 파일의 메타데이터
     */
    SdfMetadata download(String sourceUrl);

    /**
     * 다운로드된 아카이브에서 개별 SDF 파일을 추출한다.
     * .gz 압축 파일이면 압축 해제, plain .sdf면 그대로 통과.
     *
     * @param metadata 다운로드된 파일의 메타데이터
     * @return 추출된 SDF 파일들의 메타데이터 스트림
     */
    Stream<SdfMetadata> extract(SdfMetadata metadata);

    /**
     * SDF 파일을 파싱하여 SdfRecord 스트림을 반환한다.
     *
     * @param metadata 파싱할 SDF 파일의 메타데이터
     * @return 파싱된 SdfRecord 스트림
     */
    Stream<SdfRecord> parse(SdfMetadata metadata);

    /**
     * SDF 파일을 파싱하며 레코드마다 consumer를 호출한다. (메모리 절약 — 전체를 리스트에 담지 않음)
     */
    void parseAndConsume(SdfMetadata metadata, java.util.function.Consumer<SdfRecord> consumer);
}
