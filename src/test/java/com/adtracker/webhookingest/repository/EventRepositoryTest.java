package com.adtracker.webhookingest.repository;

import com.adtracker.webhookingest.model.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.adtracker.webhookingest.config.TestDatabaseConfig;
import com.adtracker.webhookingest.config.TestContainersInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for EventRepository.
 *
 * Uses Testcontainers to spin up a real PostgreSQL instance for testing.
 * This ensures tests run against the actual database with real constraints,
 * indexes, and SQL behavior.
 *
 * Test coverage:
 * - UUIDv7 generation and auto-increment behavior
 * - Event deduplication via event_hash unique constraint
 * - Time-based queries and filtering
 * - Channel and video-specific queries
 * - Index performance validation (implicit through query execution)
 */
@SpringBootTest(properties = {
    "spring.rabbitmq.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@ContextConfiguration(initializers = TestContainersInitializer.class)
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("EventRepository Integration Tests")
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    private String uniqueChannelId;
    private String uniqueVideoId;

    @BeforeEach
    void setUp() {
        // Use UUID to ensure unique IDs for each test method to prevent constraint violations
        uniqueChannelId = "UCTest" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        uniqueVideoId = "testVideo" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }

    @AfterEach
    void tearDown() {
        // Clean up database after each test to ensure test isolation
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should generate UUIDv7 primary key on save")
    void shouldGenerateUUIDv7PrimaryKey() {
        // Arrange
        Event testEvent = createTestEvent();

        // Act
        Event saved = eventRepository.save(testEvent);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should auto-populate createdAt timestamp")
    void shouldAutoPopulateCreatedAt() {
        // Arrange
        Event testEvent = createTestEvent();
        OffsetDateTime beforeSave = OffsetDateTime.now();

        // Act
        Event saved = eventRepository.save(testEvent);

        // Assert
        OffsetDateTime afterSave = OffsetDateTime.now();
        assertThat(saved.getCreatedAt())
            .isNotNull()
            .isAfterOrEqualTo(beforeSave)
            .isBeforeOrEqualTo(afterSave);
    }

    @Test
    @DisplayName("Should prevent duplicate events with same event_hash")
    void shouldPreventDuplicateEventHash() {
        // Arrange
        Event testEvent = createTestEvent();
        eventRepository.save(testEvent);

        Event duplicate = Event.builder()
            .eventType("video.published")
            .channelId(uniqueChannelId)
            .videoId(uniqueVideoId)
            .rawXml(testEvent.getRawXml())
            .eventHash(testEvent.getEventHash()) // Same hash
            .receivedAt(OffsetDateTime.now())
            .build();

        // Act & Assert
        assertThatThrownBy(() -> eventRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_event_hash");
    }

    @Test
    @DisplayName("Should find event by event hash")
    void shouldFindEventByEventHash() {
        // Arrange
        Event testEvent = createTestEvent();
        Event saved = eventRepository.save(testEvent);

        // Act
        Optional<Event> found = eventRepository.findByEventHash(saved.getEventHash());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getEventHash()).isEqualTo(saved.getEventHash());
    }

    @Test
    @DisplayName("Should check event existence by hash")
    void shouldCheckEventExistenceByHash() {
        // Arrange
        Event testEvent = createTestEvent();
        Event saved = eventRepository.save(testEvent);

        // Act
        boolean exists = eventRepository.existsByEventHash(saved.getEventHash());
        boolean notExists = eventRepository.existsByEventHash("nonexistent-hash");

        // Assert
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should find events by channel ID")
    void shouldFindEventsByChannelId() {
        // Arrange
        String channelId = generateUniqueChannelId();
        Event event1 = createEvent(channelId, generateUniqueVideoId(), "content1");
        Event event2 = createEvent(channelId, generateUniqueVideoId(), "content2");
        Event event3 = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content3");

        eventRepository.saveAll(List.of(event1, event2, event3));

        // Act
        List<Event> channel1Events = eventRepository.findByChannelId(channelId);

        // Assert
        assertThat(channel1Events).hasSize(2);
        assertThat(channel1Events)
            .extracting(Event::getChannelId)
            .containsOnly(channelId);
        // Verify ordered by createdAt DESC
        assertThat(channel1Events.get(0).getCreatedAt())
            .isAfterOrEqualTo(channel1Events.get(1).getCreatedAt());
    }

    @Test
    @DisplayName("Should find events by video ID")
    void shouldFindEventsByVideoId() {
        // Arrange
        String videoId = generateUniqueVideoId();
        Event event1 = createEvent(generateUniqueChannelId(), videoId, "content1");
        Event event2 = createEvent(generateUniqueChannelId(), videoId, "content2"); // Same video, different channel
        Event event3 = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content3");

        eventRepository.saveAll(List.of(event1, event2, event3));

        // Act
        List<Event> video1Events = eventRepository.findByVideoId(videoId);

        // Assert
        assertThat(video1Events).hasSize(2);
        assertThat(video1Events)
            .extracting(Event::getVideoId)
            .containsOnly(videoId);
    }

    @Test
    @DisplayName("Should find most recent event by channel ID")
    void shouldFindMostRecentEventByChannelId() throws InterruptedException {
        // Arrange
        String channelId = generateUniqueChannelId();
        Event older = createEvent(channelId, generateUniqueVideoId(), "content1");
        eventRepository.save(older);

        Thread.sleep(10); // Ensure different timestamps

        String newerVideoId = generateUniqueVideoId();
        Event newer = createEvent(channelId, newerVideoId, "content2");
        eventRepository.save(newer);

        // Act
        Optional<Event> mostRecent = eventRepository.findMostRecentByChannelId(channelId);

        // Assert
        assertThat(mostRecent).isPresent();
        assertThat(mostRecent.get().getVideoId()).isEqualTo(newerVideoId);
    }

    @Test
    @DisplayName("Should count events by channel ID")
    void shouldCountEventsByChannelId() {
        // Arrange
        String channelId1 = generateUniqueChannelId();
        String channelId2 = generateUniqueChannelId();
        Event event1 = createEvent(channelId1, generateUniqueVideoId(), "content1");
        Event event2 = createEvent(channelId1, generateUniqueVideoId(), "content2");
        Event event3 = createEvent(channelId2, generateUniqueVideoId(), "content3");

        eventRepository.saveAll(List.of(event1, event2, event3));

        // Act
        long channel1Count = eventRepository.countByChannelId(channelId1);
        long channel2Count = eventRepository.countByChannelId(channelId2);

        // Assert
        assertThat(channel1Count).isEqualTo(2);
        assertThat(channel2Count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find events created before cutoff date")
    void shouldFindEventsCreatedBeforeCutoffDate() {
        // Arrange
        OffsetDateTime cutoff = OffsetDateTime.now().plusHours(1);

        Event event1 = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content1");
        Event event2 = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content2");
        eventRepository.saveAll(List.of(event1, event2));

        // Act
        List<Event> oldEvents = eventRepository.findByCreatedAtBefore(cutoff);

        // Assert
        assertThat(oldEvents).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should count events created in time range")
    void shouldCountEventsCreatedInTimeRange() {
        // Arrange
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now().plusHours(1);

        Event event1 = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content1");
        Event event2 = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content2");
        eventRepository.saveAll(List.of(event1, event2));

        // Act
        long count = eventRepository.countByCreatedAtBetween(start, end);

        // Assert
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should find events by type and time range")
    void shouldFindEventsByTypeAndTimeRange() {
        // Arrange
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now().plusHours(1);

        Event published = createEventWithType(generateUniqueChannelId(), generateUniqueVideoId(), "content1", "video.published");
        Event updated = createEventWithType(generateUniqueChannelId(), generateUniqueVideoId(), "content2", "video.updated");
        eventRepository.saveAll(List.of(published, updated));

        // Act
        List<Event> publishedEvents = eventRepository.findByEventTypeAndCreatedAtBetween(
            "video.published", start, end
        );

        // Assert
        assertThat(publishedEvents).hasSizeGreaterThanOrEqualTo(1);
        assertThat(publishedEvents)
            .allMatch(e -> e.getEventType().equals("video.published"));
    }

    @Test
    @DisplayName("Should find recent events with limit")
    void shouldFindRecentEventsWithLimit() {
        // Arrange
        for (int i = 0; i < 10; i++) {
            Event event = createEvent(generateUniqueChannelId(), generateUniqueVideoId(), "content" + i);
            eventRepository.save(event);
        }

        // Act
        List<Event> recentEvents = eventRepository.findRecentEvents(5);

        // Assert
        assertThat(recentEvents).hasSizeGreaterThanOrEqualTo(5);
        // Verify ordered by createdAt DESC
        for (int i = 0; i < Math.min(recentEvents.size() - 1, 4); i++) {
            assertThat(recentEvents.get(i).getCreatedAt())
                .isAfterOrEqualTo(recentEvents.get(i + 1).getCreatedAt());
        }
    }

    @Test
    @DisplayName("Should handle empty results gracefully")
    void shouldHandleEmptyResultsGracefully() {
        // Act
        Optional<Event> notFound = eventRepository.findByEventHash("nonexistent");
        List<Event> noEvents = eventRepository.findByChannelId("nonexistent");
        long zeroCount = eventRepository.countByChannelId("nonexistent");

        // Assert
        assertThat(notFound).isEmpty();
        assertThat(noEvents).isEmpty();
        assertThat(zeroCount).isZero();
    }

    @Test
    @DisplayName("Should preserve all event fields on save")
    void shouldPreserveAllEventFieldsOnSave() {
        // Arrange
        String channelId = generateUniqueChannelId();
        String videoId = generateUniqueVideoId();
        String rawXml = String.format("<xml>test content for %s</xml>", videoId);
        Event event = Event.builder()
            .eventType("video.published")
            .channelId(channelId)
            .videoId(videoId)
            .rawXml(rawXml)
            .eventHash(Event.computeEventHash(rawXml))
            .receivedAt(OffsetDateTime.now().minusMinutes(5))
            .build();

        // Act
        Event saved = eventRepository.save(event);
        Event retrieved = eventRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(retrieved.getEventType()).isEqualTo("video.published");
        assertThat(retrieved.getChannelId()).isEqualTo(channelId);
        assertThat(retrieved.getVideoId()).isEqualTo(videoId);
        assertThat(retrieved.getRawXml()).isEqualTo(rawXml);
        assertThat(retrieved.getEventHash()).isEqualTo(Event.computeEventHash(rawXml));
        assertThat(retrieved.getReceivedAt()).isNotNull();
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    // Helper methods

    private Event createTestEvent() {
        return createEvent(uniqueChannelId, uniqueVideoId, "testContent");
    }

    private Event createEvent(String channelId, String videoId, String content) {
        return createEventWithType(channelId, videoId, content, "video.published");
    }

    private Event createEventWithType(String channelId, String videoId, String content, String eventType) {
        String rawXml = String.format("<xml>%s-%s-%s</xml>", channelId, videoId, content);
        return Event.builder()
            .eventType(eventType)
            .channelId(channelId)
            .videoId(videoId)
            .rawXml(rawXml)
            .eventHash(Event.computeEventHash(rawXml))
            .receivedAt(OffsetDateTime.now())
            .build();
    }

    private String generateUniqueChannelId() {
        return "UCTest" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }

    private String generateUniqueVideoId() {
        return "testVideo" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }
}
