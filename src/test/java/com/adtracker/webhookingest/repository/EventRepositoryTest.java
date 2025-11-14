package com.adtracker.webhookingest.repository;

import com.adtracker.webhookingest.model.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.adtracker.webhookingest.config.TestDatabaseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
@Import(TestDatabaseConfig.class)
@Testcontainers
@Transactional
@ActiveProfiles("test")
@DisplayName("EventRepository Integration Tests")
class EventRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        String rawXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>yt:video:testVideoId123</id>
                <yt:videoId>testVideoId123</yt:videoId>
                <yt:channelId>UCTestChannel123</yt:channelId>
              </entry>
            </feed>
            """;

        testEvent = Event.builder()
            .eventType("video.published")
            .channelId("UCTestChannel123")
            .videoId("testVideoId123")
            .rawXml(rawXml)
            .eventHash(Event.computeEventHash(rawXml))
            .receivedAt(OffsetDateTime.now())
            .build();
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should generate UUIDv7 primary key on save")
    void shouldGenerateUUIDv7PrimaryKey() {
        // Arrange - testEvent has no ID set

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
        eventRepository.save(testEvent);

        Event duplicate = Event.builder()
            .eventType("video.published")
            .channelId("UCTestChannel123")
            .videoId("testVideoId123")
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
        Event event1 = createEvent("UCChannel1", "video1", "content1");
        Event event2 = createEvent("UCChannel1", "video2", "content2");
        Event event3 = createEvent("UCChannel2", "video3", "content3");

        eventRepository.saveAll(List.of(event1, event2, event3));

        // Act
        List<Event> channel1Events = eventRepository.findByChannelId("UCChannel1");

        // Assert
        assertThat(channel1Events).hasSize(2);
        assertThat(channel1Events)
            .extracting(Event::getChannelId)
            .containsOnly("UCChannel1");
        // Verify ordered by createdAt DESC
        assertThat(channel1Events.get(0).getCreatedAt())
            .isAfterOrEqualTo(channel1Events.get(1).getCreatedAt());
    }

    @Test
    @DisplayName("Should find events by video ID")
    void shouldFindEventsByVideoId() {
        // Arrange
        Event event1 = createEvent("UCChannel1", "video1", "content1");
        Event event2 = createEvent("UCChannel2", "video1", "content2"); // Same video, different channel
        Event event3 = createEvent("UCChannel1", "video2", "content3");

        eventRepository.saveAll(List.of(event1, event2, event3));

        // Act
        List<Event> video1Events = eventRepository.findByVideoId("video1");

        // Assert
        assertThat(video1Events).hasSize(2);
        assertThat(video1Events)
            .extracting(Event::getVideoId)
            .containsOnly("video1");
    }

    @Test
    @DisplayName("Should find most recent event by channel ID")
    void shouldFindMostRecentEventByChannelId() throws InterruptedException {
        // Arrange
        Event older = createEvent("UCChannel1", "video1", "content1");
        eventRepository.save(older);

        Thread.sleep(10); // Ensure different timestamps

        Event newer = createEvent("UCChannel1", "video2", "content2");
        eventRepository.save(newer);

        // Act
        Optional<Event> mostRecent = eventRepository.findMostRecentByChannelId("UCChannel1");

        // Assert
        assertThat(mostRecent).isPresent();
        assertThat(mostRecent.get().getVideoId()).isEqualTo("video2");
    }

    @Test
    @DisplayName("Should count events by channel ID")
    void shouldCountEventsByChannelId() {
        // Arrange
        Event event1 = createEvent("UCChannel1", "video1", "content1");
        Event event2 = createEvent("UCChannel1", "video2", "content2");
        Event event3 = createEvent("UCChannel2", "video3", "content3");

        eventRepository.saveAll(List.of(event1, event2, event3));

        // Act
        long channel1Count = eventRepository.countByChannelId("UCChannel1");
        long channel2Count = eventRepository.countByChannelId("UCChannel2");

        // Assert
        assertThat(channel1Count).isEqualTo(2);
        assertThat(channel2Count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find events created before cutoff date")
    void shouldFindEventsCreatedBeforeCutoffDate() {
        // Arrange
        OffsetDateTime cutoff = OffsetDateTime.now().plusHours(1);

        Event event1 = createEvent("UCChannel1", "video1", "content1");
        Event event2 = createEvent("UCChannel2", "video2", "content2");
        eventRepository.saveAll(List.of(event1, event2));

        // Act
        List<Event> oldEvents = eventRepository.findByCreatedAtBefore(cutoff);

        // Assert
        assertThat(oldEvents).hasSize(2);
    }

    @Test
    @DisplayName("Should count events created in time range")
    void shouldCountEventsCreatedInTimeRange() {
        // Arrange
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now().plusHours(1);

        Event event1 = createEvent("UCChannel1", "video1", "content1");
        Event event2 = createEvent("UCChannel2", "video2", "content2");
        eventRepository.saveAll(List.of(event1, event2));

        // Act
        long count = eventRepository.countByCreatedAtBetween(start, end);

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Should find events by type and time range")
    void shouldFindEventsByTypeAndTimeRange() {
        // Arrange
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now().plusHours(1);

        Event published = createEventWithType("UCChannel1", "video1", "content1", "video.published");
        Event updated = createEventWithType("UCChannel2", "video2", "content2", "video.updated");
        eventRepository.saveAll(List.of(published, updated));

        // Act
        List<Event> publishedEvents = eventRepository.findByEventTypeAndCreatedAtBetween(
            "video.published", start, end
        );

        // Assert
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).getEventType()).isEqualTo("video.published");
    }

    @Test
    @DisplayName("Should find recent events with limit")
    void shouldFindRecentEventsWithLimit() {
        // Arrange
        for (int i = 0; i < 10; i++) {
            Event event = createEvent("UCChannel" + i, "video" + i, "content" + i);
            eventRepository.save(event);
        }

        // Act
        List<Event> recentEvents = eventRepository.findRecentEvents(5);

        // Assert
        assertThat(recentEvents).hasSize(5);
        // Verify ordered by createdAt DESC
        for (int i = 0; i < recentEvents.size() - 1; i++) {
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
        String rawXml = "<xml>test content</xml>";
        Event event = Event.builder()
            .eventType("video.published")
            .channelId("UCChannel123")
            .videoId("video123")
            .rawXml(rawXml)
            .eventHash(Event.computeEventHash(rawXml))
            .receivedAt(OffsetDateTime.now().minusMinutes(5))
            .build();

        // Act
        Event saved = eventRepository.save(event);
        Event retrieved = eventRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(retrieved.getEventType()).isEqualTo("video.published");
        assertThat(retrieved.getChannelId()).isEqualTo("UCChannel123");
        assertThat(retrieved.getVideoId()).isEqualTo("video123");
        assertThat(retrieved.getRawXml()).isEqualTo(rawXml);
        assertThat(retrieved.getEventHash()).isEqualTo(Event.computeEventHash(rawXml));
        assertThat(retrieved.getReceivedAt()).isNotNull();
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    // Helper methods

    private Event createEvent(String channelId, String videoId, String content) {
        return createEventWithType(channelId, videoId, content, "video.published");
    }

    private Event createEventWithType(String channelId, String videoId, String content, String eventType) {
        String rawXml = String.format("<xml>%s</xml>", content);
        return Event.builder()
            .eventType(eventType)
            .channelId(channelId)
            .videoId(videoId)
            .rawXml(rawXml)
            .eventHash(Event.computeEventHash(rawXml))
            .receivedAt(OffsetDateTime.now())
            .build();
    }
}
