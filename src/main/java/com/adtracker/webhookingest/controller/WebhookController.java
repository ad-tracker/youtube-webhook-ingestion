package com.adtracker.webhookingest.controller;

import com.adtracker.webhookingest.dto.WebhookPayloadDto;
import com.adtracker.webhookingest.dto.WebhookResponseDto;
import com.adtracker.webhookingest.service.WebhookIngestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for receiving webhook notifications from YouTube.
 *
 * This controller exposes endpoints for webhook ingestion and provides
 * appropriate HTTP responses based on processing outcomes.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookIngestionService webhookIngestionService;

    /**
     * Endpoint for receiving YouTube webhook notifications.
     *
     * @param payload the webhook payload
     * @param request the HTTP servlet request
     * @return response with processing details
     */
    @PostMapping("/youtube")
    public ResponseEntity<WebhookResponseDto> receiveYoutubeWebhook(
            @Valid @RequestBody WebhookPayloadDto payload,
            HttpServletRequest request) {

        String sourceIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        log.info("Received YouTube webhook from IP: {} for video: {}", sourceIp, payload.getVideoId());

        WebhookResponseDto response = webhookIngestionService.processWebhook(payload, sourceIp, userAgent);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Health check endpoint for webhook receiver.
     *
     * @return OK status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Webhook receiver is healthy");
    }

    /**
     * Extract the client IP address from the request.
     * Handles proxy headers like X-Forwarded-For.
     *
     * @param request the HTTP servlet request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
