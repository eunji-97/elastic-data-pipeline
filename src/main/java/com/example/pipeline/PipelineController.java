package com.example.pipeline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pipelines")
class PipelineController {

    private final RunPipelineService pipelineService;

    PipelineController(RunPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/run")
    ResponseEntity<?> run(@Valid @RequestBody RunRequest request) {
        PipelineResult result = pipelineService.run(request.sourceUrl());

        return switch (result.status()) {
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));
            case PARTIAL -> ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "warning", "Some records were skipped"
            ));
            case FAILED -> ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", result.errorMessage()
            ));
        };
    }

    record RunRequest(@NotBlank String sourceUrl) {}
}
