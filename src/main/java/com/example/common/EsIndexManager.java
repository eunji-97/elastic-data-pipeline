package com.example.common;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/**
 * ES 인덱스 관리 유틸리티.
 * 인덱스 생성/삭제/alias swap 기능을 제공한다.
 */
@Component
@ConditionalOnBean(ElasticsearchClient.class)
public class EsIndexManager {

    private static final Logger log = LoggerFactory.getLogger(EsIndexManager.class);

    private final ElasticsearchClient client;

    public EsIndexManager(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * 인덱스가 존재하는지 확인한다.
     */
    public boolean indexExists(String indexName) {
        try {
            return client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        } catch (Exception e) {
            log.warn("Failed to check index existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 매핑이 포함된 인덱스를 생성한다.
     */
    public void createIndex(String indexName, String mappingJson) {
        try {
            if (indexExists(indexName)) {
                log.info("Index {} already exists", indexName);
                return;
            }

            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .withJson(new StringReader(mappingJson))
            ));
            log.info("Index {} created", indexName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create index: " + indexName, e);
        }
    }

    /**
     * 인덱스를 삭제한다.
     */
    public void deleteIndex(String indexName) {
        try {
            if (!indexExists(indexName)) {
                return;
            }
            client.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            log.info("Index {} deleted", indexName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete index: " + indexName, e);
        }
    }

    /**
     * Alias를 원자적으로 전환한다 (Blue-Green swap).
     */
    public void aliasSwap(String aliasName, String oldIndex, String newIndex) {
        try {
            client.indices().updateAliases(UpdateAliasesRequest.of(u -> u
                    .actions(
                            Action.of(a -> a.remove(r -> r.index(oldIndex).alias(aliasName))),
                            Action.of(a -> a.add(ad -> ad.index(newIndex).alias(aliasName)))
                    )
            ));
            log.info("Alias {} swapped: {} -> {}", aliasName, oldIndex, newIndex);
        } catch (Exception e) {
            throw new RuntimeException("Alias swap failed: " + aliasName, e);
        }
    }

    /**
     * 인덱스 상태를 확인한다.
     */
    public HealthStatus indexHealth(String indexName) {
        try {
            return client.cat().indices().valueBody().stream()
                    .filter(i -> i.index() != null && i.index().equals(indexName))
                    .findFirst()
                    .map(i -> {
                        try {
                            return HealthStatus.valueOf(i.health().toUpperCase());
                        } catch (Exception ex) {
                            return HealthStatus.Red;
                        }
                    })
                    .orElse(HealthStatus.Red);
        } catch (Exception e) {
            return HealthStatus.Red;
        }
    }
}
