package com.adtracker.webhookingest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for incoming webhook payload from YouTube.
 *
 * This DTO represents the expected structure of webhook notifications
 * and includes validation constraints to ensure data integrity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayloadDto {

    @NotBlank(message = "Video ID is required")
    @Size(max = 50, message = "Video ID must not exceed 50 characters")
    private String videoId;

    @NotBlank(message = "Channel ID is required")
    @Size(max = 50, message = "Channel ID must not exceed 50 characters")
    private String channelId;

    @NotBlank(message = "Event type is required")
    @Size(max = 50, message = "Event type must not exceed 50 characters")
    private String eventType;

    @NotBlank(message = "Payload content is required")
    private String content;

    private String signature;

    private Long timestamp;
}
