package com.example.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;

/**
 * 파이프라인 실행 결과 DTO.
 */
@Getter
@Accessors(fluent = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineResult {

    private Status status;
    private String batchId;
    private Instant startedAt;
    private Instant finishedAt;

    private Download download;
    private Extraction extraction;
    private Parsing parsing;
    private Loading loading;

    private String errorMessage;

    // ── nested result objects ──

    public record Download(String sourceUrl, long fileSizeBytes, Duration elapsed) {}
    public record Extraction(int fileCount, Duration elapsed) {}
    public record Parsing(int totalRows, int skippedRows, Duration elapsed) {}
    public record Loading(long insertedCount, int totalBatches, Duration elapsed) {}

    public enum Status { SUCCESS, PARTIAL, FAILED }

    // ── factory ──

    public static PipelineResult empty() {
        PipelineResult r = new PipelineResult();
        r.status = Status.SUCCESS;
        r.startedAt = Instant.now();
        r.batchId = Long.toHexString(System.nanoTime());
        return r;
    }

    public PipelineResult withDownload(long fileSizeBytes, Duration elapsed) {
        this.download = new Download(null, fileSizeBytes, elapsed);
        return this;
    }

    public PipelineResult withExtraction(int fileCount, Duration elapsed) {
        this.extraction = new Extraction(fileCount, elapsed);
        return this;
    }

    public PipelineResult withParsing(int totalRows, int skippedRows, Duration elapsed) {
        this.parsing = new Parsing(totalRows, skippedRows, elapsed);
        return this;
    }

    public PipelineResult withLoading(long insertedCount, int totalBatches, Duration elapsed) {
        this.loading = new Loading(insertedCount, totalBatches, elapsed);
        return this;
    }

    public PipelineResult failed(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message;
        return this;
    }

    public PipelineResult partial() {
        this.status = Status.PARTIAL;
        return this;
    }

    public PipelineResult done() {
        this.finishedAt = Instant.now();
        return this;
    }

    /** computed — not a field */
    public Duration totalElapsed() {
        return startedAt != null && finishedAt != null
                ? Duration.between(startedAt, finishedAt)
                : null;
    }
}
