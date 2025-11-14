package com.adtracker.webhookingest.model;

import com.adtracker.webhookingest.model.generator.UUIDv7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a YouTube webhook event.
 *
 * This is an insert-only, immutable table that stores raw webhook notifications
 * from YouTube's PubSubHubbub system. Events are deduplicated using a SHA-256
 * hash of the raw XML content.
 *
 * Design principles:
 * - Insert-only: No updates or deletes (audit trail/event sourcing)
 * - Immutable: All fields are final after creation
 * - Deduplicated: Unique constraint on event_hash prevents duplicates
 * - Time-ordered: UUIDv7 primary key provides chronological ordering
 *
 * Use cases:
 * - Audit trail of all webhook notifications received
 * - Event replay for debugging or data recovery
 * - Analytics on webhook delivery patterns
 * - Deduplication of retried webhook deliveries
 */
@Entity
@Table(
    name = "events",
    schema = "webhook_ingestion",
    indexes = {
        @Index(name = "idx_events_channel_id", columnList = "channel_id"),
        @Index(name = "idx_events_video_id", columnList = "video_id"),
        @Index(name = "idx_events_received_at", columnList = "received_at"),
        @Index(name = "idx_events_created_at", columnList = "created_at")
    }
)
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Event {

    /**
     * Primary key using UUIDv7 for time-ordered unique identification.
     * UUIDv7 embeds a timestamp for better index locality and query performance.
     */
    @Id
    @GeneratedValue(generator = "uuid-v7")
    @GenericGenerator(name = "uuid-v7", type = UUIDv7Generator.class)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Type of webhook event (e.g., "video.published", "video.updated").
     * Extracted from the XML payload for quick filtering.
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * YouTube channel ID that published the content.
     * Format: UC... (24 characters)
     */
    @Column(name = "channel_id", nullable = false, length = 255)
    private String channelId;

    /**
     * YouTube video ID associated with this event.
     * Format: 11-character alphanumeric string
     */
    @Column(name = "video_id", nullable = false, length = 255)
    private String videoId;

    /**
     * Complete raw XML payload received from YouTube PubSubHubbub.
     * Preserved for audit trail and potential reprocessing.
     */
    @Column(name = "raw_xml", nullable = false, columnDefinition = "TEXT")
    private String rawXml;

    /**
     * SHA-256 hash of the raw_xml content for deduplication.
     * Unique constraint prevents duplicate event storage.
     */
    @Column(name = "event_hash", nullable = false, length = 64, unique = true)
    private String eventHash;

    /**
     * Timestamp when the webhook was received by the service.
     * Set at application level before persistence.
     */
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    /**
     * Timestamp when the record was created in the database.
     * Automatically set by Hibernate on insert.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Computes SHA-256 hash of the raw XML for deduplication.
     * Should be called before persisting the entity.
     *
     * @param xml raw XML content
     * @return hex-encoded SHA-256 hash
     */
    public static String computeEventHash(String xml) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id) &&
               Objects.equals(eventHash, event.eventHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, eventHash);
    }
}
