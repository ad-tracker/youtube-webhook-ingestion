package com.adtracker.webhookingest.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Initializer that starts Testcontainers and configures Spring properties before the application context loads.
 */
public class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

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

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                "spring.datasource.username=" + postgresContainer.getUsername(),
                "spring.datasource.password=" + postgresContainer.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.rabbitmq.host=" + rabbitMQContainer.getHost(),
                "spring.rabbitmq.port=" + rabbitMQContainer.getAmqpPort(),
                "spring.rabbitmq.username=" + rabbitMQContainer.getAdminUsername(),
                "spring.rabbitmq.password=" + rabbitMQContainer.getAdminPassword()
        );
    }
}
