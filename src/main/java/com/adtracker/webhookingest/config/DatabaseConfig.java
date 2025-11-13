package com.adtracker.webhookingest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Database configuration for JPA and transaction management.
 *
 * Enables JPA repositories and declarative transaction management
 * for the webhook ingestion service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.adtracker.webhookingest.repository")
@EnableTransactionManagement
@Slf4j
public class DatabaseConfig {

    public DatabaseConfig() {
        log.info("Database configuration initialized");
    }
}
