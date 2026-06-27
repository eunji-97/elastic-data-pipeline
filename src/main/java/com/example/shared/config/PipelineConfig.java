package com.example.shared.config;

import com.example.pipeline.config.PubChemFtpProperties;
import com.example.storage.domain.BulkLoadService;
import com.example.storage.domain.StoredDataRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 파이프라인 공통 설정.
 */
@Configuration
@EnableAsync
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.storage.infra")
@EntityScan(basePackages = "com.example.storage.infra")
@EnableConfigurationProperties(PubChemFtpProperties.class)
class PipelineConfig {

    @Bean
    BulkLoadService bulkLoadService(StoredDataRepository repository) {
        return new BulkLoadService(repository);
    }
}
