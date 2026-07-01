package com.example.common;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 클라이언트 제공자.
 * elasticsearch.enabled=true 일 때만 빈 등록.
 */
@Component
public class ElasticsearchClientProvider {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClientProvider.class);

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.username:#{null}}")
    private String username;

    @Value("${elasticsearch.password:#{null}}")
    private String password;

    @Bean
    @ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
    ElasticsearchClient elasticsearchClient() {
        log.info("Creating Elasticsearch client for {}:{}", host, port);

        HttpHost httpHost = new HttpHost(host, port, "http");

        // 대용량 bulk/reindex 작업을 위한 넉넉한 타임아웃 설정
        RestClientBuilder builder = RestClient.builder(httpHost)
                .setRequestConfigCallback(rcb -> rcb
                        .setConnectTimeout(10_000)         // 연결 타임아웃: 10초
                        .setSocketTimeout(600_000)         // 소켓 타임아웃: 10분 (bulk/reindex 대응)
                        .setConnectionRequestTimeout(30_000) // 커넥션 요청 타임아웃: 30초
                );

        if (username != null && password != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        RestClient restClient = builder.build();

        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(transport);

        // 헬스 체크
        try {
            boolean alive = client.ping().value();
            log.info("Elasticsearch ping: {}", alive ? "OK" : "FAILED");
        } catch (Exception e) {
            log.warn("Elasticsearch not reachable: {}", e.getMessage());
        }

        return client;
    }
}
