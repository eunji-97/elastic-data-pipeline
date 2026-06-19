package com.example.shared.config;

import com.example.storage.domain.BulkLoadService;
import com.example.storage.domain.StoredDataRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAsync
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.storage.infra")
@EntityScan(basePackages = "com.example.storage.infra")
class PipelineConfig {

    /**
     * 도메인 서비스를 Bean으로 등록.
     * DDD 원칙상 도메인 계층에는 Spring 애너테이션을 두지 않는다.
     */
    @Bean
    BulkLoadService bulkLoadService(StoredDataRepository repository) {
        return new BulkLoadService(repository);
    }
}
