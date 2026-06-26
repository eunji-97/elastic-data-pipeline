package com.example.experiment1.presentation;

import com.example.experiment1.application.DirectEsLoadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 실험 1 — Direct ES 적재 제어 API.
 */
@RestController
@RequestMapping("/api/v1/experiment1")
@ConditionalOnProperty(name = "experiment1.enabled", havingValue = "true", matchIfMissing = false)
public class Experiment1Controller {

    private final DirectEsLoadService loadService;

    public Experiment1Controller(DirectEsLoadService loadService) {
        this.loadService = loadService;
    }

    /**
     * SDF 파일을 ES에 직접 적재한다.
     */
    @PostMapping("/load")
    ResponseEntity<Map<String, Object>> load(@RequestBody Map<String, String> request) {
        String sourceUrl = request.getOrDefault("sourceUrl",
                "file:///tmp/pubchem-test/Compound_050000001_050500000.sdf.gz");

        DirectEsLoadService.LoadResult result = loadService.load(sourceUrl);

        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "indexed", result.indexed(),
                "skipped", result.skipped(),
                "elapsedMs", result.elapsedMs(),
                "fileCount", result.fileCount(),
                "failedFileCount", result.failedFileCount(),
                "error", result.error() != null ? result.error() : ""
        ));
    }

    /**
     * 실험 상태 확인.
     */
    @GetMapping("/health")
    ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "experiment", "Direct ES (baseline)",
                "indexStrategy", "single index direct update",
                "verification", "none"
        ));
    }
}
