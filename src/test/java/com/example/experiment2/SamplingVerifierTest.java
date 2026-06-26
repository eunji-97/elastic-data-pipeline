package com.example.experiment2;

import com.example.experiment2.application.SamplingVerifier;
import com.example.experiment2.domain.VerificationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실험 2 - Verifier 도메인 로직 테스트 (ES 없이).
 */
class SamplingVerifierTest {

    @Test
    void verificationResultShouldCalculateMatchRate() {
        VerificationResult passed = new VerificationResult(50, 50, java.util.List.of());
        assertTrue(passed.passed());
        assertEquals(100.0, passed.matchRate());

        VerificationResult failed = new VerificationResult(50, 48, java.util.List.of("err1", "err2"));
        assertFalse(failed.passed());
        assertEquals(96.0, failed.matchRate());
    }
}
