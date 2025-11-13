package com.adtracker.webhookingest.repository;

import com.adtracker.webhookingest.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing WebhookEvent entities.
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /**
     * Find all unprocessed webhook events.
     */
    List<WebhookEvent> findByProcessedFalse();

    /**
     * Find webhook events by processing status.
     */
    List<WebhookEvent> findByProcessingStatus(WebhookEvent.ProcessingStatus status);

    /**
     * Find webhook events created after a specific time.
     */
    List<WebhookEvent> findByCreatedAtAfter(Instant timestamp);

    /**
     * Find webhook events by video ID.
     */
    List<WebhookEvent> findByVideoIdOrderByCreatedAtDesc(String videoId);

    /**
     * Find webhook events by channel ID.
     */
    List<WebhookEvent> findByChannelIdOrderByCreatedAtDesc(String channelId);

    /**
     * Count unprocessed events.
     */
    @Query("SELECT COUNT(w) FROM WebhookEvent w WHERE w.processed = false")
    long countUnprocessedEvents();

    /**
     * Find events that need retry (failed status and retry count below max).
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.processingStatus = :status AND w.retryCount < :maxRetries")
    List<WebhookEvent> findEventsForRetry(
            @Param("status") WebhookEvent.ProcessingStatus status,
            @Param("maxRetries") Integer maxRetries
    );
}
