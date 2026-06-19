package com.example.sdf.domain;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 다운로드된 SDF 아카이브의 메타데이터.
 * Value Object.
 */
public final class SdfMetadata {

    private final String sourceUrl;
    private final Path archivePath;
    private final long fileSizeBytes;
    private final Instant downloadedAt;

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
