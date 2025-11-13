package com.adtracker.webhookingest.service;

import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.adtracker.webhookingest.exception.WebhookValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("WebhookValidationService Tests")
class WebhookValidationServiceTest {

    private WebhookValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new WebhookValidationService();
        ReflectionTestUtils.setField(validationService, "validationEnabled", true);
        ReflectionTestUtils.setField(validationService, "maxPayloadSize", 1048576);
    }

    @Test
    @DisplayName("Should validate valid payload successfully")
    void shouldValidateValidPayloadSuccessfully() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();

        // Act & Assert
        assertDoesNotThrow(() -> validationService.validatePayload(payload));
    }

    @Test
    @DisplayName("Should throw exception when payload is null")
    void shouldThrowExceptionWhenPayloadIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(null))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessage("Webhook payload cannot be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should throw exception when video ID is null or empty")
    void shouldThrowExceptionWhenVideoIdIsNullOrEmpty(String videoId) {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setVideoId(videoId);

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Video ID");
    }

    @Test
    @DisplayName("Should throw exception when video ID exceeds max length")
    void shouldThrowExceptionWhenVideoIdExceedsMaxLength() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setVideoId("a".repeat(51));

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Video ID exceeds maximum length");
    }

    @ParameterizedTest
    @ValueSource(strings = {"video@123", "video#456", "video 789"})
    @DisplayName("Should throw exception when video ID contains invalid characters")
    void shouldThrowExceptionWhenVideoIdContainsInvalidCharacters(String videoId) {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setVideoId(videoId);

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Video ID contains invalid characters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should throw exception when channel ID is null or empty")
    void shouldThrowExceptionWhenChannelIdIsNullOrEmpty(String channelId) {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setChannelId(channelId);

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Channel ID");
    }

    @Test
    @DisplayName("Should throw exception when channel ID exceeds max length")
    void shouldThrowExceptionWhenChannelIdExceedsMaxLength() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setChannelId("UC" + "a".repeat(50));

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Channel ID exceeds maximum length");
    }

    @Test
    @DisplayName("Should throw exception when payload content is null")
    void shouldThrowExceptionWhenPayloadContentIsNull() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setContent(null);

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Payload content cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when payload size exceeds maximum")
    void shouldThrowExceptionWhenPayloadSizeExceedsMaximum() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setContent("x".repeat(1048577)); // Exceeds 1MB

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Payload size")
                .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    @DisplayName("Should throw exception when timestamp is invalid")
    void shouldThrowExceptionWhenTimestampIsInvalid() {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setTimestamp(-1L);

        // Act & Assert
        assertThatThrownBy(() -> validationService.validatePayload(payload))
                .isInstanceOf(WebhookValidationException.class)
                .hasMessageContaining("Invalid timestamp");
    }

    @Test
    @DisplayName("Should skip validation when disabled")
    void shouldSkipValidationWhenDisabled() {
        // Arrange
        ReflectionTestUtils.setField(validationService, "validationEnabled", false);
        WebhookPayloadDto invalidPayload = new WebhookPayloadDto();

        // Act & Assert
        assertDoesNotThrow(() -> validationService.validatePayload(invalidPayload));
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
}
