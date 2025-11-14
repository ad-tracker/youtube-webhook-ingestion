package com.adtracker.webhookingest.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Test configuration for database migrations.
 *
 * Ensures Flyway migrations run in tests to create the schema
 * before Hibernate validation occurs.
 *
 * Note: The EntityManagerFactory is configured to depend on Flyway
 * via Spring Boot's autoconfiguration.
 */
@TestConfiguration
public class TestDatabaseConfig {

    /**
     * Configures and executes Flyway migrations for test database.
     * Migrations run immediately when the bean is created.
     *
     * @param dataSource The test datasource from Testcontainers
     * @return Configured Flyway instance
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .schemas("webhook_ingestion")
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .cleanDisabled(false)
            .load();

        // Run migrations immediately
        flyway.migrate();

        return flyway;
    }
}
