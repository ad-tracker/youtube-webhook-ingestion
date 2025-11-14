package com.adtracker.webhookingest.repository;

import com.adtracker.webhookingest.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Event entities.
 *
 * Provides data access operations for webhook events with support for:
 * - Deduplication checking via event hash
 * - Time-based queries for archival and analytics
 * - Channel and video-specific event retrieval
 *
 * Design notes:
 * - Events table is insert-only (no update/delete operations)
 * - All queries leverage database indexes for performance
 * - Uses OffsetDateTime for timezone-aware temporal queries
 *
 * Performance considerations:
 * - existsByEventHash: Uses unique index, very fast O(log n)
 * - findByCreatedAtBefore: Uses idx_events_created_at
 * - countByCreatedAtBetween: Range query on indexed column
 * - findByChannelId: Uses idx_events_channel_id
 * - findByVideoId: Uses idx_events_video_id
 */
@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Checks if an event with the given hash already exists.
     * Used for deduplication before inserting new events.
     *
     * This is more efficient than findByEventHash when you only need
     * existence checking, as it can return early without loading the entity.
     *
     * @param eventHash SHA-256 hash of the raw XML
     * @return true if event exists, false otherwise
     */
    boolean existsByEventHash(String eventHash);

    /**
     * Finds an event by its unique event hash.
     *
     * Useful for retrieving the existing event when a duplicate is detected.
     *
     * @param eventHash SHA-256 hash of the raw XML
     * @return Optional containing the event if found
     */
    Optional<Event> findByEventHash(String eventHash);

    /**
     * Finds all events created before a specific timestamp.
     * Typically used for archival or data retention policies.
     *
     * Performance: Uses idx_events_created_at index.
     * Consider pagination for large result sets.
     *
     * @param cutoffDate timestamp before which to find events
     * @return list of events created before the cutoff
     */
    List<Event> findByCreatedAtBefore(OffsetDateTime cutoffDate);

    /**
     * Counts events created within a specific time range.
     * Useful for analytics and monitoring webhook delivery rates.
     *
     * Performance: Index-only scan on idx_events_created_at when possible.
     *
     * @param start beginning of time range (inclusive)
     * @param end end of time range (inclusive)
     * @return count of events in the range
     */
    long countByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);

    /**
     * Finds all events for a specific YouTube channel.
     *
     * Performance: Uses idx_events_channel_id index.
     * Consider pagination for channels with many events.
     *
     * @param channelId YouTube channel ID
     * @return list of events for the channel, ordered by creation time
     */
    @Query("SELECT e FROM Event e WHERE e.channelId = :channelId ORDER BY e.createdAt DESC")
    List<Event> findByChannelId(@Param("channelId") String channelId);

    /**
     * Finds all events for a specific YouTube video.
     *
     * Multiple events may exist for the same video (e.g., published, updated).
     * Performance: Uses idx_events_video_id index.
     *
     * @param videoId YouTube video ID
     * @return list of events for the video, ordered by creation time
     */
    @Query("SELECT e FROM Event e WHERE e.videoId = :videoId ORDER BY e.createdAt DESC")
    List<Event> findByVideoId(@Param("videoId") String videoId);

    /**
     * Finds the most recent event for a specific channel.
     *
     * Useful for determining the latest activity from a channel.
     *
     * @param channelId YouTube channel ID
     * @return Optional containing the most recent event if any exist
     */
    @Query("SELECT e FROM Event e WHERE e.channelId = :channelId ORDER BY e.createdAt DESC LIMIT 1")
    Optional<Event> findMostRecentByChannelId(@Param("channelId") String channelId);

    /**
     * Counts total events received for a specific channel.
     *
     * Useful for channel activity metrics and monitoring.
     *
     * @param channelId YouTube channel ID
     * @return total count of events for the channel
     */
    long countByChannelId(String channelId);

    /**
     * Finds events by event type within a time range.
     *
     * Useful for analytics on specific event types (e.g., video publications).
     *
     * @param eventType type of event (e.g., "video.published")
     * @param start beginning of time range (inclusive)
     * @param end end of time range (inclusive)
     * @return list of matching events ordered by creation time
     */
    @Query("SELECT e FROM Event e WHERE e.eventType = :eventType AND e.createdAt BETWEEN :start AND :end ORDER BY e.createdAt DESC")
    List<Event> findByEventTypeAndCreatedAtBetween(
        @Param("eventType") String eventType,
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end
    );

    /**
     * Finds recent events across all channels.
     *
     * Used for dashboard views or monitoring recent activity.
     * Limited to prevent excessive data retrieval.
     *
     * @param limit maximum number of events to return
     * @return list of most recent events
     */
    @Query("SELECT e FROM Event e ORDER BY e.createdAt DESC LIMIT :limit")
    List<Event> findRecentEvents(@Param("limit") int limit);
}
