package com.example.experiment1;

import com.example.experiment1.application.DirectEsLoadService;
import com.example.experiment1.domain.EsCompoundDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실험 1 - DirectEsLoadService 단위 테스트.
 */
class DirectEsLoadServiceTest {

    @Test
    void loadResultShouldTrackSuccess() {
        DirectEsLoadService.LoadResult result = new DirectEsLoadService.LoadResult(100, 0, 5000, 1, 0);
        assertTrue(result.success());
        assertEquals(100, result.indexed());
        assertEquals(0, result.skipped());
        assertEquals(5000, result.elapsedMs());
        assertEquals(1, result.fileCount());
        assertEquals(0, result.failedFileCount());
        assertNull(result.error());
    }

    @Test
    void loadResultShouldTrackFailure() {
        DirectEsLoadService.LoadResult result = new DirectEsLoadService.LoadResult(0, 0, 3000, 0, 1, "ES connection failed");
        assertFalse(result.success());
        assertEquals("ES connection failed", result.error());
    }

    @Test
    void compoundDocumentShouldHoldProperties() {
        var props = java.util.Map.of("PUBCHEM_MOLECULAR_FORMULA", "C9H8O4", "PUBCHEM_MOLECULAR_WEIGHT", "180.16");
        EsCompoundDocument doc = new EsCompoundDocument("C001", props, "file:///test.sdf.gz", "batch-1");

        assertEquals("C001", doc.getCompoundId());
        assertEquals(2, doc.getProperties().size());
        assertEquals("C9H8O4", doc.getProperties().get("PUBCHEM_MOLECULAR_FORMULA"));
    }
}
