package com.example.experiment2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실험 2 통합 테스트 — 프로파일 기동 검증.
 */
@SpringBootTest
@ActiveProfiles("experiment2")
class Experiment2IntegrationTest {

    @Test
    void contextLoads() {
        assertTrue(true, "Experiment 2 context should load");
    }
}
