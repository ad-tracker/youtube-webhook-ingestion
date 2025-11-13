package com.adtracker.webhookingest.service;

import com.adtracker.webhookingest.model.WebhookEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for publishing webhook events to RabbitMQ.
 *
 * This service handles the message publishing logic to decouple
 * webhook ingestion from downstream processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePublishingService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${webhook.ingestion.queue.exchange:youtube.webhooks}")
    private String exchange;

    @Value("${webhook.ingestion.queue.routing-key:webhook.received}")
    private String routingKey;

    /**
     * Publish a webhook event to RabbitMQ.
     *
     * @param event the webhook event to publish
     */
    public void publishWebhookEvent(WebhookEvent event) {
        log.debug("Publishing webhook event {} to exchange: {}, routing key: {}",
                event.getId(), exchange, routingKey);

        try {
            String message = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.info("Successfully published webhook event {} to RabbitMQ", event.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook event {}: {}", event.getId(), e.getMessage());
            throw new RuntimeException("Failed to serialize webhook event for publishing", e);
        } catch (Exception e) {
            log.error("Failed to publish webhook event {} to RabbitMQ: {}", event.getId(), e.getMessage());
            throw new RuntimeException("Failed to publish webhook event to RabbitMQ", e);
        }
    }
}
