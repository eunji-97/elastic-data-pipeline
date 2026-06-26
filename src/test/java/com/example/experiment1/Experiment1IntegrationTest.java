package com.example.experiment1;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실험 1 통합 테스트 — 프로파일 기동 검증.
 */
@SpringBootTest
@ActiveProfiles("experiment1")
class Experiment1IntegrationTest {

    @Test
    void contextLoads() {
        assertTrue(true, "Experiment 1 context should load");
    }
}
