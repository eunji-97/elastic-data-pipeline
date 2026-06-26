package com.example.experiment2;

import com.example.experiment2.domain.IndexAlias;
import com.example.experiment2.domain.VerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실험 2 - Blue-Green 도메인 단위 테스트.
 */
class BlueGreenServiceTest {

    @Test
    void indexAliasShouldIncrementVersion() {
        IndexAlias alias = new IndexAlias("compounds", 0);
        assertEquals("compounds", alias.aliasName());
        assertEquals("compounds_v0", alias.activeIndex());
        assertEquals("compounds_v1", alias.nextIndex());
        assertEquals(1, alias.version());
    }

    @Test
    void indexAliasShouldTrackMultipleVersions() {
        IndexAlias v1 = new IndexAlias("compounds", 0);
        IndexAlias v2 = new IndexAlias("compounds", v1.version());
        assertEquals("compounds_v1", v2.activeIndex());
        assertEquals("compounds_v2", v2.nextIndex());
        assertEquals(2, v2.version());
    }

    @Test
    void verificationShouldPassWhenAllMatched() {
        VerificationResult result = new VerificationResult(100, 100, List.of());
        assertTrue(result.passed());
        assertEquals(100.0, result.matchRate());
    }

    @Test
    void verificationShouldFailWhenMismatch() {
        VerificationResult result = new VerificationResult(100, 95,
                List.of("C001: properties 비어있음", "C002: ES에 없음"));
        assertFalse(result.passed());
        assertEquals(95.0, result.matchRate());
        assertEquals(2, result.mismatches().size());
    }

    @Test
    void verificationShouldHandleZeroSamples() {
        VerificationResult result = new VerificationResult(0, 0, List.of());
        assertFalse(result.passed()); // 0 total → passed=false (safe default)
    }
}
