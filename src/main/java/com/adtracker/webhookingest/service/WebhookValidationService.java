package com.adtracker.webhookingest.service;

import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.adtracker.webhookingest.exception.WebhookValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for validating incoming webhook payloads.
 *
 * This service performs validation checks on webhook payloads to ensure
 * they meet the required criteria before processing.
 */
@Service
@Slf4j
public class WebhookValidationService {

    @Value("${webhook.ingestion.validation.enabled:true}")
    private boolean validationEnabled;

    @Value("${webhook.ingestion.max-payload-size:1048576}")
    private int maxPayloadSize;

    /**
     * Validate a webhook payload.
     *
     * @param payload the payload to validate
     * @throws WebhookValidationException if validation fails
     */
    public void validatePayload(WebhookPayloadDto payload) {
        if (!validationEnabled) {
            log.debug("Webhook validation is disabled");
            return;
        }

        validateNotNull(payload);

        log.debug("Validating webhook payload for video: {}", payload.getVideoId());

        validateVideoId(payload.getVideoId());
        validateChannelId(payload.getChannelId());
        validateEventType(payload.getEventType());
        validatePayloadSize(payload.getContent());
        validateTimestamp(payload.getTimestamp());

        log.debug("Webhook payload validation passed");
    }

    private void validateNotNull(WebhookPayloadDto payload) {
        if (payload == null) {
            throw new WebhookValidationException("Webhook payload cannot be null");
        }
    }

    private void validateVideoId(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            throw new WebhookValidationException("Video ID cannot be null or empty");
        }
        if (videoId.length() > 50) {
            throw new WebhookValidationException("Video ID exceeds maximum length of 50 characters");
        }
        // YouTube video IDs are typically 11 characters
        if (!videoId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new WebhookValidationException("Video ID contains invalid characters");
        }
    }

    private void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new WebhookValidationException("Channel ID cannot be null or empty");
        }
        if (channelId.length() > 50) {
            throw new WebhookValidationException("Channel ID exceeds maximum length of 50 characters");
        }
        // YouTube channel IDs typically start with UC
        if (!channelId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new WebhookValidationException("Channel ID contains invalid characters");
        }
    }

    private void validateEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new WebhookValidationException("Event type cannot be null or empty");
        }
        if (eventType.length() > 50) {
            throw new WebhookValidationException("Event type exceeds maximum length of 50 characters");
        }
    }

    private void validatePayloadSize(String content) {
        if (content == null) {
            throw new WebhookValidationException("Payload content cannot be null");
        }
        int payloadBytes = content.getBytes().length;
        if (payloadBytes > maxPayloadSize) {
            throw new WebhookValidationException(
                    String.format("Payload size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            payloadBytes, maxPayloadSize)
            );
        }
    }

    private void validateTimestamp(Long timestamp) {
        if (timestamp != null && timestamp <= 0) {
            throw new WebhookValidationException("Invalid timestamp value");
        }
    }
}
