package com.example.experiment2.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 실험 2 - 샘플링 정합성 검증 결과.
 */
public class VerificationResult {

    private final int totalChecked;
    private final int matched;
    private final List<String> mismatches;
    private final boolean passed;

    public VerificationResult(int totalChecked, int matched, List<String> mismatches) {
        this.totalChecked = totalChecked;
        this.matched = matched;
        this.mismatches = mismatches != null ? mismatches : new ArrayList<>();
        this.passed = (totalChecked > 0) && (matched == totalChecked);
    }

    public boolean passed() { return passed; }
    public int totalChecked() { return totalChecked; }
    public int matched() { return matched; }
    public List<String> mismatches() { return mismatches; }
    public double matchRate() {
        return totalChecked > 0 ? (double) matched / totalChecked * 100.0 : 0;
    }
}
