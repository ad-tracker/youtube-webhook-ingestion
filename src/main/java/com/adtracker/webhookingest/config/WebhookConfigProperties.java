package com.adtracker.webhookingest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Type-safe configuration properties for webhook ingestion.
 *
 * This class provides a type-safe way to access webhook-related
 * configuration values from application.yml.
 */
@ConfigurationProperties(prefix = "webhook.ingestion")
@Data
@Validated
public class WebhookConfigProperties {

    /**
     * Maximum payload size in bytes.
     */
    @Min(value = 1024, message = "Max payload size must be at least 1KB")
    private int maxPayloadSize = 1048576; // 1MB default

    /**
     * Validation settings.
     */
    private ValidationConfig validation = new ValidationConfig();

    /**
     * Queue settings.
     */
    private QueueConfig queue = new QueueConfig();

    @Data
    public static class ValidationConfig {
        /**
         * Enable or disable payload validation.
         */
        private boolean enabled = true;

        /**
         * Enable strict validation mode.
         */
        private boolean strictMode = false;
    }

    @Data
    public static class QueueConfig {
        /**
         * Queue name for webhook events.
         */
        @NotBlank(message = "Queue name cannot be blank")
        private String name = "youtube.webhooks.raw";

        /**
         * Exchange name for webhook events.
         */
        @NotBlank(message = "Exchange name cannot be blank")
        private String exchange = "youtube.webhooks";

        /**
         * Routing key for webhook events.
         */
        @NotBlank(message = "Routing key cannot be blank")
        private String routingKey = "webhook.received";
    }
}
