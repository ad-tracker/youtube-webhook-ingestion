package com.adtracker.webhookingest.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration that provides Testcontainers for integration tests.
 * Provides both PostgreSQL and RabbitMQ containers for realistic testing.
 */
@TestConfiguration
public class TestRabbitMQConfig {

    private static final PostgreSQLContainer<?> postgresContainer;
    private static final RabbitMQContainer rabbitMQContainer;

    static {
        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("webhook_ingestion_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        postgresContainer.start();

        rabbitMQContainer = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                .withReuse(true);
        rabbitMQContainer.start();
    }

    @Bean
    public PostgreSQLContainer<?> postgresContainer() {
        return postgresContainer;
    }

    @Bean
    public RabbitMQContainer rabbitMQContainer() {
        return rabbitMQContainer;
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // RabbitMQ properties
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }
}
