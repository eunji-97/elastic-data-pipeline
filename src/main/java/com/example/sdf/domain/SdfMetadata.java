package com.example.sdf.domain;

import java.nio.file.Path;
import java.time.Instant;

/**
 * SDF 파일의 메타데이터. 다운로드/추출된 파일의 출처와 위치를 추적한다.
 */
public final class SdfMetadata {

    private final String sourceUrl;
    private final Path archivePath;
    private final long fileSizeBytes;
    private final Instant downloadedAt;

    /**
     * @param sourceUrl     원본 다운로드 URL
     * @param archivePath   로컬 파일 시스템 상의 경로
     * @param fileSizeBytes 파일 크기 (byte)
     */
    public SdfMetadata(String sourceUrl, Path archivePath, long fileSizeBytes) {
        this.sourceUrl = sourceUrl;
        this.archivePath = archivePath;
        this.fileSizeBytes = fileSizeBytes;
        this.downloadedAt = Instant.now();
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public Path archivePath() {
        return archivePath;
    }

    public long fileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant downloadedAt() {
        return downloadedAt;
    }
}
