package com.adtracker.webhookingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main entry point for the YouTube Webhook Ingestion Service.
 *
 * This service receives webhook notifications from YouTube, validates them,
 * and publishes them to RabbitMQ for downstream processing.
 *
 * Key Features:
 * - Virtual threads enabled for improved scalability
 * - Spring Security for webhook validation
 * - PostgreSQL for persistence
 * - RabbitMQ for event streaming
 * - Comprehensive observability with Actuator and Prometheus
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WebhookIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookIngestionApplication.class, args);
    }
}
