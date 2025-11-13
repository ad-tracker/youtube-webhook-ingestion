package com.adtracker.webhookingest.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a received webhook event from YouTube.
 *
 * This entity stores all incoming webhook notifications for audit purposes
 * and potential replay scenarios.
 */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "video_id", nullable = false, length = 50)
    private String videoId;

    @Column(name = "channel_id", nullable = false, length = 50)
    private String channelId;

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processing_status", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum EventType {
        VIDEO_PUBLISHED,
        VIDEO_UPDATED,
        VIDEO_DELETED,
        CHANNEL_UPDATED,
        UNKNOWN
    }

    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        RETRYING
    }
}
