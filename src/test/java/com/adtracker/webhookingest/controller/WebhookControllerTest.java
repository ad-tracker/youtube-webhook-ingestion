package com.adtracker.webhookingest.controller;

import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.adtracker.webhookingest.dto.WebhookResponseDto;
import com.adtracker.webhookingest.exception.WebhookProcessingException;
import com.adtracker.webhookingest.exception.WebhookValidationException;
import com.adtracker.webhookingest.service.WebhookIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
@DisplayName("WebhookController Tests")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookIngestionService webhookIngestionService;

    @Test
    @DisplayName("Should accept valid webhook payload")
    void shouldAcceptValidWebhookPayload() throws Exception {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookResponseDto response = createSuccessResponse();

        when(webhookIngestionService.processWebhook(any(), anyString(), anyString()))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(response.getEventId().toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 for invalid payload")
    void shouldReturn400ForInvalidPayload() throws Exception {
        // Arrange
        WebhookPayloadDto invalidPayload = new WebhookPayloadDto();
        // Missing required fields

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    @DisplayName("Should handle validation exception")
    void shouldHandleValidationException() throws Exception {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();

        when(webhookIngestionService.processWebhook(any(), anyString(), anyString()))
                .thenThrow(new WebhookValidationException("Invalid video ID"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Invalid video ID"));
    }

    @Test
    @DisplayName("Should handle processing exception")
    void shouldHandleProcessingException() throws Exception {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();

        when(webhookIngestionService.processWebhook(any(), anyString(), anyString()))
                .thenThrow(new WebhookProcessingException("Failed to process webhook"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Processing Error"))
                .andExpect(jsonPath("$.message").value("Failed to process webhook event"));
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromXForwardedForHeader() throws Exception {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookResponseDto response = createSuccessResponse();

        when(webhookIngestionService.processWebhook(any(), eq("10.0.0.1"), anyString()))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Should extract user agent from header")
    void shouldExtractUserAgentFromHeader() throws Exception {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        WebhookResponseDto response = createSuccessResponse();
        String userAgent = "YouTube-Webhook/1.0";

        when(webhookIngestionService.processWebhook(any(), anyString(), eq(userAgent)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", userAgent)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Health endpoint should return OK")
    void healthEndpointShouldReturnOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/webhooks/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Webhook receiver is healthy"));
    }

    @Test
    @DisplayName("Should validate required fields")
    void shouldValidateRequiredFields() throws Exception {
        // Arrange
        String incompletePayload = "{}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompletePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("Should validate video ID size constraint")
    void shouldValidateVideoIdSizeConstraint() throws Exception {
        // Arrange
        WebhookPayloadDto payload = createValidPayload();
        payload.setVideoId("a".repeat(51)); // Exceeds max size

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
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

    private WebhookResponseDto createSuccessResponse() {
        return WebhookResponseDto.builder()
                .eventId(UUID.randomUUID())
                .status("ACCEPTED")
                .message("Webhook event processed successfully")
                .receivedAt(Instant.now())
                .build();
    }
}
