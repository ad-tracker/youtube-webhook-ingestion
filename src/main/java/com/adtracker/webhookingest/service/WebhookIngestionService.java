package com.adtracker.webhookingest.service;

import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.adtracker.webhookingest.dto.WebhookResponseDto;
import com.adtracker.webhookingest.exception.WebhookProcessingException;
import com.adtracker.webhookingest.exception.WebhookValidationException;
import com.adtracker.webhookingest.model.WebhookEvent;
import com.adtracker.webhookingest.repository.WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for processing incoming webhook events from YouTube.
 *
 * This service handles the core business logic of receiving, validating,
 * persisting, and publishing webhook events to the message queue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIngestionService {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookValidationService validationService;
    private final MessagePublishingService messagePublishingService;
    private final ObjectMapper objectMapper;

    /**
     * Process an incoming webhook payload.
     *
     * @param payload the webhook payload to process
     * @param sourceIp the source IP address of the request
     * @param userAgent the user agent of the request
     * @return response DTO with processing details
     * @throws WebhookValidationException if validation fails
     * @throws WebhookProcessingException if processing fails
     */
    @Transactional
    public WebhookResponseDto processWebhook(WebhookPayloadDto payload, String sourceIp, String userAgent) {
        log.info("Processing webhook for video: {} from channel: {}", payload.getVideoId(), payload.getChannelId());

        // Validate the webhook payload
        validationService.validatePayload(payload);

        // Convert payload to JSON string for storage
        String payloadJson = convertToJson(payload);

        // Create and persist webhook event
        WebhookEvent event = createWebhookEvent(payload, payloadJson, sourceIp, userAgent);
        WebhookEvent savedEvent = webhookEventRepository.save(event);

        log.debug("Webhook event persisted with ID: {}", savedEvent.getId());

        // Publish event to message queue
        try {
            messagePublishingService.publishWebhookEvent(savedEvent);
            savedEvent.setProcessed(true);
            savedEvent.setProcessingStatus(WebhookEvent.ProcessingStatus.COMPLETED);
            savedEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(savedEvent);
            log.info("Webhook event {} published successfully", savedEvent.getId());
        } catch (Exception e) {
            log.error("Failed to publish webhook event {}: {}", savedEvent.getId(), e.getMessage(), e);
            savedEvent.setProcessingStatus(WebhookEvent.ProcessingStatus.FAILED);
            savedEvent.setErrorMessage(e.getMessage());
            webhookEventRepository.save(savedEvent);
            throw new WebhookProcessingException("Failed to publish webhook event", e);
        }

        return WebhookResponseDto.builder()
                .eventId(savedEvent.getId())
                .status("ACCEPTED")
                .message("Webhook event processed successfully")
                .receivedAt(savedEvent.getCreatedAt())
                .build();
    }

    private WebhookEvent createWebhookEvent(WebhookPayloadDto payload, String payloadJson,
                                            String sourceIp, String userAgent) {
        return WebhookEvent.builder()
                .videoId(payload.getVideoId())
                .channelId(payload.getChannelId())
                .eventType(mapEventType(payload.getEventType()))
                .payload(payloadJson)
                .sourceIp(sourceIp)
                .userAgent(userAgent)
                .processed(false)
                .processingStatus(WebhookEvent.ProcessingStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private WebhookEvent.EventType mapEventType(String eventType) {
        try {
            return WebhookEvent.EventType.valueOf(eventType.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown event type: {}, defaulting to UNKNOWN", eventType);
            return WebhookEvent.EventType.UNKNOWN;
        }
    }

    private String convertToJson(WebhookPayloadDto payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload to JSON: {}", e.getMessage());
            throw new WebhookProcessingException("Failed to serialize webhook payload", e);
        }
    }
}
