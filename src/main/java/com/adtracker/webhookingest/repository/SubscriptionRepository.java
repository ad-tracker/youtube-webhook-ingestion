package com.adtracker.webhookingest.repository;

import com.adtracker.webhookingest.model.Subscription;
import com.adtracker.webhookingest.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Subscription entities.
 *
 * Provides data access operations for YouTube channel subscriptions with support for:
 * - Subscription lookup by channel ID
 * - Status-based filtering for lifecycle management
 * - Renewal scheduling and tracking
 * - Lease expiration monitoring
 *
 * Design notes:
 * - One subscription per channel (enforced by unique constraint)
 * - All queries leverage database indexes for performance
 * - Uses OffsetDateTime for timezone-aware temporal queries
 * - Supports renewal scheduling and failure tracking
 *
 * Performance considerations:
 * - findByChannelId: Uses unique index, O(log n) lookup
 * - findBySubscriptionStatus: Uses idx_subscriptions_status
 * - findByNextRenewalAtBefore: Uses idx_subscriptions_next_renewal
 * - findByLeaseExpiresAtBefore: Uses idx_subscriptions_lease_expires
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Finds a subscription by YouTube channel ID.
     *
     * Uses unique index for optimal performance.
     * Returns empty Optional if no subscription exists for the channel.
     *
     * @param channelId YouTube channel ID
     * @return Optional containing the subscription if found
     */
    Optional<Subscription> findByChannelId(String channelId);

    /**
     * Checks if a subscription exists for a given channel ID.
     *
     * More efficient than findByChannelId when you only need existence checking.
     *
     * @param channelId YouTube channel ID
     * @return true if subscription exists, false otherwise
     */
    boolean existsByChannelId(String channelId);

    /**
     * Finds all subscriptions with a specific status.
     *
     * Performance: Uses idx_subscriptions_status index.
     * Useful for lifecycle management and monitoring.
     *
     * @param status subscription status to filter by
     * @return list of subscriptions with the given status
     */
    List<Subscription> findBySubscriptionStatus(SubscriptionStatus status);

    /**
     * Finds subscriptions that need renewal.
     *
     * Returns subscriptions where next_renewal_at is before the specified timestamp.
     * Performance: Uses idx_subscriptions_next_renewal index.
     *
     * Typical usage: Find subscriptions due for renewal now or in the past.
     *
     * @param timestamp cutoff time for renewal scheduling
     * @return list of subscriptions due for renewal
     */
    List<Subscription> findByNextRenewalAtBefore(OffsetDateTime timestamp);

    /**
     * Finds subscriptions with expired or soon-to-expire leases.
     *
     * Performance: Uses idx_subscriptions_lease_expires index.
     * Useful for monitoring and alerting on expired subscriptions.
     *
     * @param timestamp cutoff time for lease expiration
     * @return list of subscriptions with leases expiring before the timestamp
     */
    List<Subscription> findByLeaseExpiresAtBefore(OffsetDateTime timestamp);

    /**
     * Finds active subscriptions that are due for renewal.
     *
     * Combines status and renewal time filtering for precise control.
     * Performance: Uses composite filtering on indexed columns.
     *
     * @param timestamp cutoff time for renewal scheduling
     * @return list of active subscriptions due for renewal
     */
    @Query("SELECT s FROM Subscription s WHERE s.subscriptionStatus = 'ACTIVE' AND s.nextRenewalAt < :timestamp ORDER BY s.nextRenewalAt ASC")
    List<Subscription> findActiveSubscriptionsDueForRenewal(@Param("timestamp") OffsetDateTime timestamp);

    /**
     * Finds subscriptions with failed renewal attempts exceeding a threshold.
     *
     * Used for alerting and manual intervention triggering.
     *
     * @param minAttempts minimum number of renewal attempts
     * @return list of subscriptions with excessive renewal failures
     */
    @Query("SELECT s FROM Subscription s WHERE s.renewalAttempts >= :minAttempts ORDER BY s.renewalAttempts DESC")
    List<Subscription> findByRenewalAttemptsGreaterThanEqual(@Param("minAttempts") Integer minAttempts);

    /**
     * Finds subscriptions that are both active and expiring soon.
     *
     * Useful for proactive monitoring and alerting on subscriptions
     * that may expire if renewal fails.
     *
     * @param timestamp cutoff time for "expiring soon" (e.g., now + 24 hours)
     * @param status subscription status to filter (typically ACTIVE)
     * @return list of active subscriptions expiring soon
     */
    @Query("SELECT s FROM Subscription s WHERE s.subscriptionStatus = :status AND s.leaseExpiresAt < :timestamp ORDER BY s.leaseExpiresAt ASC")
    List<Subscription> findByStatusAndLeaseExpiresAtBefore(
        @Param("status") SubscriptionStatus status,
        @Param("timestamp") OffsetDateTime timestamp
    );

    /**
     * Counts subscriptions by status.
     *
     * Useful for metrics and dashboard displays.
     *
     * @param status subscription status to count
     * @return count of subscriptions with the given status
     */
    long countBySubscriptionStatus(SubscriptionStatus status);

    /**
     * Finds all active subscriptions.
     *
     * Convenience method for retrieving all currently active subscriptions.
     * Performance: Uses idx_subscriptions_status index.
     *
     * @return list of all active subscriptions
     */
    default List<Subscription> findAllActive() {
        return findBySubscriptionStatus(SubscriptionStatus.ACTIVE);
    }

    /**
     * Finds all pending subscriptions.
     *
     * Useful for monitoring subscription confirmation delays.
     *
     * @return list of all pending subscriptions
     */
    default List<Subscription> findAllPending() {
        return findBySubscriptionStatus(SubscriptionStatus.PENDING);
    }

    /**
     * Finds all expired subscriptions.
     *
     * Useful for cleanup and renewal operations.
     *
     * @return list of all expired subscriptions
     */
    default List<Subscription> findAllExpired() {
        return findBySubscriptionStatus(SubscriptionStatus.EXPIRED);
    }

    /**
     * Finds all failed subscriptions.
     *
     * Useful for alerting and manual intervention.
     *
     * @return list of all failed subscriptions
     */
    default List<Subscription> findAllFailed() {
        return findBySubscriptionStatus(SubscriptionStatus.FAILED);
    }

    /**
     * Finds subscriptions due for renewal right now.
     *
     * Convenience method for scheduled renewal tasks.
     *
     * @return list of subscriptions due for renewal
     */
    default List<Subscription> findDueForRenewal() {
        return findByNextRenewalAtBefore(OffsetDateTime.now());
    }

    /**
     * Finds recently created subscriptions.
     *
     * Useful for monitoring subscription activity and debugging.
     *
     * @param since timestamp to search from
     * @return list of subscriptions created after the timestamp
     */
    @Query("SELECT s FROM Subscription s WHERE s.createdAt > :since ORDER BY s.createdAt DESC")
    List<Subscription> findRecentlyCreated(@Param("since") OffsetDateTime since);

    /**
     * Finds subscriptions that haven't been renewed in a specified period.
     *
     * Useful for identifying stale subscriptions or monitoring renewal health.
     *
     * @param since timestamp threshold for last renewal
     * @return list of subscriptions not renewed since the timestamp
     */
    @Query("SELECT s FROM Subscription s WHERE s.lastRenewedAt IS NULL OR s.lastRenewedAt < :since ORDER BY s.lastRenewedAt ASC NULLS FIRST")
    List<Subscription> findNotRenewedSince(@Param("since") OffsetDateTime since);
}
