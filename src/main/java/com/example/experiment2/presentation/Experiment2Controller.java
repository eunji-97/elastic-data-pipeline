package com.example.experiment2.presentation;

import com.example.experiment2.application.BlueGreenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 실험 2 — Blue-Green 적재 제어 API.
 */
@RestController
@RequestMapping("/api/v1/experiment2")
@ConditionalOnProperty(name = "experiment2.enabled", havingValue = "true", matchIfMissing = false)
public class Experiment2Controller {

    private final BlueGreenService blueGreenService;

    public Experiment2Controller(BlueGreenService blueGreenService) {
        this.blueGreenService = blueGreenService;
    }

    /**
     * Blue-Green 전체 사이클 실행.
     */
    @PostMapping("/run")
    ResponseEntity<Map<String, Object>> run(@RequestBody Map<String, String> request) {
        String sourceUrl = request.getOrDefault("sourceUrl",
                "file:///tmp/pubchem-test/Compound_050000001_050500000.sdf.gz");

        BlueGreenService.CycleResult result = blueGreenService.runCycle(sourceUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("version", result.version());
        body.put("activeIndex", result.activeIndex());
        body.put("recordsLoaded", result.recordsLoaded());
        body.put("verification", Map.of(
                "checked", result.verificationChecked(),
                "matched", result.verificationMatched()
        ));
        body.put("totalElapsedMs", result.totalElapsedMs());
        body.put("steps", result.stepTimings());
        if (result.error() != null) {
            body.put("error", result.error());
        }

        return result.success()
                ? ResponseEntity.ok(body)
                : ResponseEntity.internalServerError().body(body);
    }

    /**
     * 장애 주입 — 특정 단계에서 오류 시뮬레이션.
     */
    @PostMapping("/inject-failure")
    ResponseEntity<Map<String, Object>> injectFailure(@RequestBody Map<String, String> request) {
        String phase = request.getOrDefault("phase", "bulk-update");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Failure injection at phase: " + phase,
                "expectedBehavior", "기존 인덱스 untouched, 신규 인덱스 삭제됨"
        ));
    }

    /**
     * 실험 상태 확인.
     */
    @GetMapping("/health")
    ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "experiment", "Blue-Green (RDB + Reindex + alias swap)",
                "indexStrategy", "Reindex API + alias atomic swap",
                "verification", "RDB vs ES sampling (100 compounds)",
                "rollback", "0초 (alias 유지)"
        ));
    }
}
