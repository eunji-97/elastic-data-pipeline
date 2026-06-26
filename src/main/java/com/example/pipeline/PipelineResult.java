package com.example.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 파이프라인 실행 결과. 빌더 패턴으로 각 단계의 메트릭을 누적한다.
 */
public class PipelineResult {

    public enum Status {
        SUCCESS, PARTIAL, FAILED
    }

    private Status status;
    private String batchId;
    private Instant startedAt;
    private Instant finishedAt;
    private Download download;
    private Extraction extraction;
    private Parsing parsing;
    private Loading loading;
    private String errorMessage;

    // -- 다중 파일 처리용 메트릭 --
    private int totalDownloadedFiles;
    private long totalDownloadedBytes;
    private int failedFileCount;
    private List<FileResult> perFileResults;

    public PipelineResult() {
    }

    /**
     * 새 파이프라인 결과를 초기화한다.
     */
    public static PipelineResult empty() {
        PipelineResult result = new PipelineResult();
        result.status = Status.SUCCESS;
        result.startedAt = Instant.now();
        result.batchId = Long.toHexString(System.nanoTime());
        result.perFileResults = new ArrayList<>();
        return result;
    }

    /** 단일 파일용 Download 메트릭 설정 (기존 하위 호환). */
    public PipelineResult withDownload(long fileSizeBytes, Duration elapsed) {
        this.download = new Download("", fileSizeBytes, elapsed);
        return this;
    }

    /** 다중 파일 집계용 Download 메트릭 설정. */
    public PipelineResult withDownload(int fileCount, long totalBytes, Duration totalElapsed) {
        this.totalDownloadedFiles = fileCount;
        this.totalDownloadedBytes = totalBytes;
        this.download = new Download("", totalBytes, totalElapsed);
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

    /** 파일별 처리 결과를 누적한다. */
    public PipelineResult addFileResult(FileResult fileResult) {
        this.perFileResults.add(fileResult);
        this.totalDownloadedFiles++;
        this.totalDownloadedBytes += fileResult.fileSizeBytes();
        if (fileResult.error() != null) {
            this.failedFileCount++;
        }
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

    public Duration totalElapsed() {
        if (startedAt != null && finishedAt != null) {
            return Duration.between(startedAt, finishedAt);
        }
        return null;
    }

    // -- accessors --

    public Status status() { return status; }
    public String batchId() { return batchId; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public Download download() { return download; }
    public Extraction extraction() { return extraction; }
    public Parsing parsing() { return parsing; }
    public Loading loading() { return loading; }
    public String errorMessage() { return errorMessage; }
    public int totalDownloadedFiles() { return totalDownloadedFiles; }
    public long totalDownloadedBytes() { return totalDownloadedBytes; }
    public int failedFileCount() { return failedFileCount; }
    public List<FileResult> perFileResults() { return perFileResults; }

    // -- inner records --

    /** 단일 파일 다운로드 메트릭. */
    public record Download(String sourceUrl, long fileSizeBytes, Duration elapsed) {}
    /** Extraction 단계 메트릭. */
    public record Extraction(int fileCount, Duration elapsed) {}
    /** Parsing 단계 메트릭. */
    public record Parsing(int totalRows, int skippedRows, Duration elapsed) {}
    /** Loading 단계 메트릭. */
    public record Loading(long insertedCount, int totalBatches, Duration elapsed) {}

    /**
     * 개별 파일 처리 결과.
     *
     * @param fileName        파일명
     * @param fileSizeBytes   파일 크기 (byte)
     * @param recordsParsed   파싱된 레코드 수
     * @param recordsSkipped  건너뛴 레코드 수
     * @param elapsedMs       파일 처리 소요 시간 (ms)
     * @param error           오류 메시지 (성공 시 null)
     */
    public record FileResult(
            String fileName,
            long fileSizeBytes,
            int recordsParsed,
            int recordsSkipped,
            long elapsedMs,
            String error
    ) {}
}
