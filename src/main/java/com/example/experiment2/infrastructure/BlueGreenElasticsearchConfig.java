package com.example.experiment2.infrastructure;

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
 * 실험 2 - Blue-Green ES 설정.
 * 앱 기동 시 초기 인덱스(compounds_v0)와 alias를 자동 생성한다.
 */
@Configuration
@ConditionalOnProperty(name = "experiment2.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(ElasticsearchClient.class)
public class BlueGreenElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(BlueGreenElasticsearchConfig.class);
    private static final String ALIAS = "compounds";
    private static final String INITIAL_INDEX = "compounds_v0";

    private final ElasticsearchClient client;

    public BlueGreenElasticsearchConfig(ElasticsearchClient client) {
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ensureInitialIndex() {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INITIAL_INDEX))).value();
            if (!exists) {
                client.indices().create(CreateIndexRequest.of(c -> c.index(INITIAL_INDEX)));
                log.info("초기 인덱스 생성: {}", INITIAL_INDEX);

                // alias가 없으면 최초 연결
                boolean aliasExists = client.indices().exists(ExistsRequest.of(e -> e.index(ALIAS))).value();
                if (!aliasExists) {
                    client.indices().updateAliases(ua -> ua
                            .actions(co.elastic.clients.elasticsearch.indices.update_aliases.Action.of(a -> a
                                    .add(ad -> ad.index(INITIAL_INDEX).alias(ALIAS))))
                    );
                    log.info("Alias '{}' → '{}' 연결", ALIAS, INITIAL_INDEX);
                }
            } else {
                log.info("초기 인덱스 {} 이미 존재", INITIAL_INDEX);
            }
        } catch (Exception e) {
            log.warn("초기 인덱스 보장 실패: {}", e.getMessage());
        }
    }
}
