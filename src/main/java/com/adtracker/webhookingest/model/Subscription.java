package com.adtracker.webhookingest.model;

import com.adtracker.webhookingest.model.generator.UUIDv7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a YouTube channel subscription.
 *
 * Manages the lifecycle of PubSubHubbub subscriptions to YouTube channels,
 * including lease management and renewal tracking. Each subscription represents
 * an active or pending webhook subscription for a specific YouTube channel.
 *
 * Subscription lifecycle:
 * 1. PENDING: Initial subscription request sent to hub
 * 2. ACTIVE: Hub confirmed subscription, receiving notifications
 * 3. EXPIRED: Lease expired, needs renewal
 * 4. FAILED: Subscription or renewal failed, needs attention
 *
 * Lease management:
 * - leaseExpiresAt: When current lease expires
 * - nextRenewalAt: When to attempt renewal (before expiration)
 * - renewalAttempts: Counter for retry tracking
 * - lastRenewalError: Error message from last failed renewal
 *
 * Design principles:
 * - One subscription per channel (unique constraint on channel_id)
 * - Time-ordered primary key (UUIDv7) for efficient indexing
 * - Automatic timestamp management with @CreationTimestamp and @UpdateTimestamp
 * - Status enum for type-safe status management
 */
@Entity
@Table(
    name = "subscriptions",
    schema = "webhook_ingestion",
    indexes = {
        @Index(name = "idx_subscriptions_channel_id", columnList = "channel_id"),
        @Index(name = "idx_subscriptions_status", columnList = "subscription_status"),
        @Index(name = "idx_subscriptions_next_renewal", columnList = "next_renewal_at"),
        @Index(name = "idx_subscriptions_lease_expires", columnList = "lease_expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Subscription {

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
     * YouTube channel ID being subscribed to.
     * Format: UC... (24 characters)
     * Unique constraint ensures only one subscription per channel.
     */
    @Column(name = "channel_id", nullable = false, unique = true, length = 255)
    private String channelId;

    /**
     * PubSubHubbub topic URL for this channel.
     * Format: https://www.youtube.com/xml/feeds/videos.xml?channel_id={channelId}
     */
    @Column(name = "topic_url", nullable = false, length = 500)
    private String topicUrl;

    /**
     * Callback URL for receiving webhook notifications.
     * The hub will POST notifications to this URL.
     */
    @Column(name = "callback_url", nullable = false, length = 500)
    private String callbackUrl;

    /**
     * Current status of the subscription.
     * Values: PENDING, ACTIVE, EXPIRED, FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 50)
    private SubscriptionStatus subscriptionStatus;

    /**
     * Lease duration in seconds as confirmed by the hub.
     * Typical values: 432000 (5 days) to 864000 (10 days)
     */
    @Column(name = "lease_seconds", nullable = false)
    private Integer leaseSeconds;

    /**
     * Timestamp when the current lease expires.
     * After this time, the subscription is no longer active.
     */
    @Column(name = "lease_expires_at", nullable = false)
    private OffsetDateTime leaseExpiresAt;

    /**
     * Timestamp of the last successful renewal.
     * Null if subscription has never been renewed.
     */
    @Column(name = "last_renewed_at")
    private OffsetDateTime lastRenewedAt;

    /**
     * Timestamp when next renewal should be attempted.
     * Typically set to lease_expires_at - renewal_buffer (e.g., 24 hours before expiration)
     */
    @Column(name = "next_renewal_at", nullable = false)
    private OffsetDateTime nextRenewalAt;

    /**
     * Counter tracking consecutive renewal attempts.
     * Reset to 0 on successful renewal.
     * Used for exponential backoff and alerting.
     */
    @Column(name = "renewal_attempts", nullable = false)
    @Builder.Default
    private Integer renewalAttempts = 0;

    /**
     * Error message from the most recent failed renewal attempt.
     * Null if last renewal was successful.
     * Useful for debugging and alerting.
     */
    @Column(name = "last_renewal_error", columnDefinition = "TEXT")
    private String lastRenewalError;

    /**
     * Timestamp when the record was created in the database.
     * Automatically set by Hibernate on insert.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Timestamp when the record was last updated.
     * Automatically updated by Hibernate on any modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Pre-update lifecycle callback to ensure updated_at is refreshed.
     * Hibernate @UpdateTimestamp should handle this, but this provides a safety net.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Checks if the subscription is currently active.
     *
     * @return true if status is ACTIVE and lease has not expired
     */
    public boolean isActive() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE &&
               leaseExpiresAt.isAfter(OffsetDateTime.now());
    }

    /**
     * Checks if the subscription lease is about to expire.
     *
     * @param bufferHours hours before expiration to consider "soon"
     * @return true if lease expires within the buffer period
     */
    public boolean isExpiringSoon(long bufferHours) {
        OffsetDateTime threshold = OffsetDateTime.now().plusHours(bufferHours);
        return leaseExpiresAt.isBefore(threshold);
    }

    /**
     * Checks if renewal is due based on nextRenewalAt timestamp.
     *
     * @return true if current time is past nextRenewalAt
     */
    public boolean isRenewalDue() {
        return OffsetDateTime.now().isAfter(nextRenewalAt);
    }

    /**
     * Marks a successful renewal with updated lease information.
     *
     * @param newLeaseSeconds new lease duration from hub
     * @param renewalBufferHours hours before expiration to schedule next renewal
     */
    public void markRenewalSuccess(int newLeaseSeconds, long renewalBufferHours) {
        this.leaseSeconds = newLeaseSeconds;
        this.lastRenewedAt = OffsetDateTime.now();
        this.leaseExpiresAt = OffsetDateTime.now().plusSeconds(newLeaseSeconds);
        this.nextRenewalAt = leaseExpiresAt.minusHours(renewalBufferHours);
        this.renewalAttempts = 0;
        this.lastRenewalError = null;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
    }

    /**
     * Marks a failed renewal attempt with error information.
     *
     * @param errorMessage description of the failure
     */
    public void markRenewalFailure(String errorMessage) {
        this.renewalAttempts++;
        this.lastRenewalError = errorMessage;
        // Status update should be handled by calling code based on retry policy
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subscription that = (Subscription) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, channelId);
    }
}
