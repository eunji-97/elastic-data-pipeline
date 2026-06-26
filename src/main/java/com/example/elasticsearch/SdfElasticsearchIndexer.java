package com.example.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * SDF 화합물 데이터를 ES에 벌크 인덱싱한다.
 */
@Component
@ConditionalOnBean(ElasticsearchClient.class)
public class SdfElasticsearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(SdfElasticsearchIndexer.class);
    private static final String INDEX_NAME = "sdf_compounds";

    private final ElasticsearchClient client;

    public SdfElasticsearchIndexer(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * SDF 화합물 도큐먼트 리스트를 벌크 인덱싱한다.
     */
    public int bulkIndex(List<SdfCompoundDocument> documents) {
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
                    .index(INDEX_NAME);

            for (SdfCompoundDocument doc : documents) {
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .id(doc.getCompoundId())
                                .document(doc)
                        ));
            }

            var response = client.bulk(bulkBuilder.build());
            int indexed = documents.size() - (response.errors() ? countErrors(response.items()) : 0);

            log.info("Bulk indexed: {}/{} documents", indexed, documents.size());
            return indexed;
        } catch (IOException e) {
            throw new RuntimeException("ES bulk index failed", e);
        }
    }

    private int countErrors(List<BulkResponseItem> items) {
        return (int) items.stream()
                .filter(item -> item.error() != null)
                .count();
    }
}
