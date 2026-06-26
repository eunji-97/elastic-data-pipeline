package com.example.pipeline;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파이프라인 실행을 위한 REST 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/pipelines")
class PipelineController {

    private final RunPipelineService pipelineService;

    PipelineController(RunPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/run")
    ResponseEntity<?> run(@RequestBody RunRequest request) {
        PipelineResult result = pipelineService.run(request.sourceUrl());
        Map<String, Object> body = toResponseMap(result);

        return switch (result.status()) {
            case SUCCESS -> ResponseEntity.ok(body);
            case PARTIAL -> {
                body.put("warning", "Some records were skipped");
                yield ResponseEntity.ok(body);
            }
            case FAILED -> ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", result.errorMessage()
            ));
        };
    }

    private Map<String, Object> toResponseMap(PipelineResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("status", r.status().name());
        body.put("batchId", r.batchId());
        body.put("startedAt", r.startedAt() != null ? r.startedAt().toString() : null);
        body.put("finishedAt", r.finishedAt() != null ? r.finishedAt().toString() : null);
        body.put("totalElapsedMs", r.totalElapsed() != null ? r.totalElapsed().toMillis() : null);

        if (r.download() != null) {
            Map<String, Object> downloadMap = new LinkedHashMap<>();
            downloadMap.put("fileSizeBytes", r.download().fileSizeBytes());
            if (r.totalDownloadedFiles() > 0) {
                downloadMap.put("fileCount", r.totalDownloadedFiles());
                downloadMap.put("totalDownloadedBytes", r.totalDownloadedBytes());
                downloadMap.put("failedFileCount", r.failedFileCount());
            } else {
                downloadMap.put("fileCount", 1);
            }
            body.put("download", downloadMap);
        }
        if (r.extraction() != null) {
            body.put("extraction", Map.of(
                    "fileCount", r.extraction().fileCount()
            ));
        }
        if (r.parsing() != null) {
            body.put("parsing", Map.of(
                    "totalRows", r.parsing().totalRows(),
                    "skippedRows", r.parsing().skippedRows()
            ));
        }
        if (r.loading() != null) {
            body.put("loading", Map.of(
                    "insertedCount", r.loading().insertedCount(),
                    "totalBatches", r.loading().totalBatches()
            ));
        }
        if (r.errorMessage() != null) {
            body.put("errorMessage", r.errorMessage());
        }

        // 파일별 상세 결과 (디렉토리 모드)
        if (r.perFileResults() != null && !r.perFileResults().isEmpty()) {
            List<Map<String, Object>> fileResults = r.perFileResults().stream()
                    .map(fr -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("fileName", fr.fileName());
                        m.put("fileSizeBytes", fr.fileSizeBytes());
                        m.put("recordsParsed", fr.recordsParsed());
                        m.put("recordsSkipped", fr.recordsSkipped());
                        m.put("elapsedMs", fr.elapsedMs());
                        if (fr.error() != null) m.put("error", fr.error());
                        return m;
                    }).toList();
            body.put("perFileResults", fileResults);
        }
        return body;
    }

    record RunRequest(String sourceUrl) {}
}
