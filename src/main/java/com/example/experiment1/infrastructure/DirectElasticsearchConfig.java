package com.example.experiment1.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 실험 1 - Direct ES 설정.
 * 앱 기동 시 compounds_v1 인덱스를 자동 생성한다.
 */
@Configuration
@ConditionalOnProperty(name = "experiment1.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(ElasticsearchClient.class)
public class DirectElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(DirectElasticsearchConfig.class);
    private static final String INDEX_NAME = "compounds_v1";

    private final ElasticsearchClient client;

    public DirectElasticsearchConfig(ElasticsearchClient client) {
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ensureIndex() {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (!exists) {
                client.indices().create(CreateIndexRequest.of(c -> c.index(INDEX_NAME)));
                log.info("Index {} created", INDEX_NAME);
            } else {
                log.info("Index {} already exists", INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure index {}: {}", INDEX_NAME, e.getMessage());
        }
    }
}
