package com.adtracker.webhookingest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned after successful webhook processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponseDto {

    private UUID eventId;
    private String status;
    private String message;
    private Instant receivedAt;
}
