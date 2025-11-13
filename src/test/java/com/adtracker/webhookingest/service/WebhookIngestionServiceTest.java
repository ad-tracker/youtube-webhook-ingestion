package com.adtracker.webhookingest.service;

import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.adtracker.webhookingest.dto.WebhookResponseDto;
import com.adtracker.webhookingest.exception.WebhookProcessingException;
import com.adtracker.webhookingest.model.WebhookEvent;
import com.adtracker.webhookingest.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookIngestionService Tests")
class WebhookIngestionServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookValidationService validationService;

    @Mock
    private MessagePublishingService messagePublishingService;

    @InjectMocks
    private WebhookIngestionService webhookIngestionService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookIngestionService = new WebhookIngestionService(
                webhookEventRepository,
                validationService,
                messagePublishingService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should process webhook successfully")
    void shouldProcessWebhookSuccessfully() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookEvent savedEvent = createWebhookEvent();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        doNothing().when(validationService).validatePayload(any());
        doNothing().when(messagePublishingService).publishWebhookEvent(any());

        // Act
        WebhookResponseDto response = webhookIngestionService.processWebhook(
                payload, "127.0.0.1", "Test-Agent/1.0"
        );

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getEventId()).isEqualTo(savedEvent.getId());
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getMessage()).contains("processed successfully");

        verify(validationService).validatePayload(payload);
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
        verify(messagePublishingService).publishWebhookEvent(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Should validate payload before processing")
    void shouldValidatePayloadBeforeProcessing() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookEvent savedEvent = createWebhookEvent();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        // Act
        webhookIngestionService.processWebhook(payload, "127.0.0.1", "Test-Agent/1.0");

        // Assert
        verify(validationService).validatePayload(payload);
    }

    @Test
    @DisplayName("Should persist webhook event with correct fields")
    void shouldPersistWebhookEventWithCorrectFields() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        String sourceIp = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        WebhookEvent savedEvent = createWebhookEvent();
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);

        // Act
        webhookIngestionService.processWebhook(payload, sourceIp, userAgent);

        // Assert
        verify(webhookEventRepository, atLeastOnce()).save(eventCaptor.capture());
        WebhookEvent capturedEvent = eventCaptor.getAllValues().get(0);

        assertThat(capturedEvent.getVideoId()).isEqualTo(payload.getVideoId());
        assertThat(capturedEvent.getChannelId()).isEqualTo(payload.getChannelId());
        assertThat(capturedEvent.getSourceIp()).isEqualTo(sourceIp);
        assertThat(capturedEvent.getUserAgent()).isEqualTo(userAgent);
        assertThat(capturedEvent.getProcessed()).isFalse();
        assertThat(capturedEvent.getProcessingStatus()).isEqualTo(WebhookEvent.ProcessingStatus.PENDING);
    }

    @Test
    @DisplayName("Should publish event to message queue")
    void shouldPublishEventToMessageQueue() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookEvent savedEvent = createWebhookEvent();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        // Act
        webhookIngestionService.processWebhook(payload, "127.0.0.1", "Test-Agent/1.0");

        // Assert
        verify(messagePublishingService).publishWebhookEvent(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Should mark event as processed after successful publishing")
    void shouldMarkEventAsProcessedAfterSuccessfulPublishing() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookEvent savedEvent = createWebhookEvent();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);

        // Act
        webhookIngestionService.processWebhook(payload, "127.0.0.1", "Test-Agent/1.0");

        // Assert
        verify(webhookEventRepository, times(2)).save(eventCaptor.capture());
        WebhookEvent updatedEvent = eventCaptor.getAllValues().get(1);

        assertThat(updatedEvent.getProcessed()).isTrue();
        assertThat(updatedEvent.getProcessingStatus()).isEqualTo(WebhookEvent.ProcessingStatus.COMPLETED);
        assertThat(updatedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle publishing failure gracefully")
    void shouldHandlePublishingFailureGracefully() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookEvent savedEvent = createWebhookEvent();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(messagePublishingService).publishWebhookEvent(any());

        // Act & Assert
        assertThatThrownBy(() -> webhookIngestionService.processWebhook(
                payload, "127.0.0.1", "Test-Agent/1.0"
        ))
                .isInstanceOf(WebhookProcessingException.class)
                .hasMessageContaining("Failed to publish webhook event");

        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Should mark event as failed when publishing fails")
    void shouldMarkEventAsFailedWhenPublishingFails() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookEvent savedEvent = createWebhookEvent();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        doThrow(new RuntimeException("Publishing failed"))
                .when(messagePublishingService).publishWebhookEvent(any());

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);

        // Act
        try {
            webhookIngestionService.processWebhook(payload, "127.0.0.1", "Test-Agent/1.0");
        } catch (WebhookProcessingException e) {
            // Expected
        }

        // Assert
        verify(webhookEventRepository, times(2)).save(eventCaptor.capture());
        WebhookEvent failedEvent = eventCaptor.getAllValues().get(1);

        assertThat(failedEvent.getProcessingStatus()).isEqualTo(WebhookEvent.ProcessingStatus.FAILED);
        assertThat(failedEvent.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("Should map event type correctly")
    void shouldMapEventTypeCorrectly() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setEventType("VIDEO_PUBLISHED");

        WebhookEvent savedEvent = createWebhookEvent();
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);

        // Act
        webhookIngestionService.processWebhook(payload, "127.0.0.1", "Test-Agent/1.0");

        // Assert
        verify(webhookEventRepository, atLeastOnce()).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).getEventType())
                .isEqualTo(WebhookEvent.EventType.VIDEO_PUBLISHED);
    }

    @Test
    @DisplayName("Should handle unknown event types gracefully")
    void shouldHandleUnknownEventTypesGracefully() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setEventType("UNKNOWN_EVENT_TYPE");

        WebhookEvent savedEvent = createWebhookEvent();
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(savedEvent);

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);

        // Act
        webhookIngestionService.processWebhook(payload, "127.0.0.1", "Test-Agent/1.0");

        // Assert
        verify(webhookEventRepository, atLeastOnce()).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).getEventType())
                .isEqualTo(WebhookEvent.EventType.UNKNOWN);
    }

    private WebhookPayloadDto createValidPayload() {
        return WebhookPayloadDto.builder()
                .videoId("dQw4w9WgXcQ")
                .channelId("UCuAXFkgsw1L7xaCfnd5JJOw")
                .eventType("VIDEO_PUBLISHED")
                .content("{\"title\":\"Test Video\"}")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private WebhookEvent createWebhookEvent() {
        return WebhookEvent.builder()
                .id(UUID.randomUUID())
                .videoId("dQw4w9WgXcQ")
                .channelId("UCuAXFkgsw1L7xaCfnd5JJOw")
                .eventType(WebhookEvent.EventType.VIDEO_PUBLISHED)
                .payload("{\"title\":\"Test Video\"}")
                .sourceIp("127.0.0.1")
                .userAgent("Test-Agent/1.0")
                .processed(false)
                .processingStatus(WebhookEvent.ProcessingStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
