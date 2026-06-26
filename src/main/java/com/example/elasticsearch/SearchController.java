package com.example.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * ES 검색 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnBean(ElasticsearchClient.class)
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private static final String INDEX = "sdf_compounds";

    private final ElasticsearchClient client;

    public SearchController(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * 키워드 검색.
     */
    @GetMapping("/compounds")
    ResponseEntity<?> search(@RequestParam("q") String query) {
        try {
            SearchResponse<SdfCompoundDocument> response = client.search(s -> s
                            .index(INDEX)
                            .query(q -> q
                                    .multiMatch(mm -> mm
                                            .query(query)
                                            .fields("compoundId", "properties.*")
                                    )
                            ),
                    SdfCompoundDocument.class
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "totalHits", response.hits().total() != null ? response.hits().total().value() : 0,
                    "results", response.hits().hits()
            ));
        } catch (IOException e) {
            log.error("Search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Search failed: " + e.getMessage()
            ));
        }
    }

    /**
     * ID로 화합물 조회.
     */
    @GetMapping("/compounds/{compoundId}")
    ResponseEntity<?> getById(@PathVariable String compoundId) {
        try {
            var response = client.get(g -> g.index(INDEX).id(compoundId), SdfCompoundDocument.class);
            if (response.found()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", response.source()
                ));
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 인덱스 통계.
     */
    @GetMapping("/stats")
    ResponseEntity<?> stats() {
        try {
            var countResponse = client.count(c -> c.index(INDEX));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "indexName", INDEX,
                    "documentCount", countResponse.count(),
                    "health", "see /actuator/health"
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
