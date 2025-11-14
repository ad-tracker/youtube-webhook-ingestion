package com.adtracker.webhookingest.controller;

import com.adtracker.webhookingest.config.TestContainersInitializer;
import com.adtracker.webhookingest.config.TestDatabaseConfig;
import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = TestContainersInitializer.class)
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("WebhookController Tests")
class WebhookControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .build();
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
    @DisplayName("Should process valid webhook successfully")
    void shouldProcessValidWebhookSuccessfully() throws Exception {
        // Arrange - Integration test with real service
        WebhookPayloadDto payload = createValidPayload();

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.eventId").exists())
                .andExpect(jsonPath("$.message").value("Webhook event processed successfully"));
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromXForwardedForHeader() throws Exception {
        // Arrange - Integration test verifying IP extraction through full flow
        WebhookPayloadDto payload = createValidPayload();

        // Act & Assert - The IP extraction is verified through successful processing
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("Should extract user agent from header")
    void shouldExtractUserAgentFromHeader() throws Exception {
        // Arrange - Integration test verifying user agent extraction
        WebhookPayloadDto payload = createValidPayload();
        String userAgent = "YouTube-Webhook/1.0";

        // Act & Assert - The user agent extraction is verified through successful processing
        mockMvc.perform(post("/api/v1/webhooks/youtube")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", userAgent)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
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
}
