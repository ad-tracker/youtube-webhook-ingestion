package com.adtracker.webhookingest.repository;

import com.adtracker.webhookingest.model.Subscription;
import com.adtracker.webhookingest.model.SubscriptionStatus;
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
 * Integration tests for SubscriptionRepository.
 *
 * Uses Testcontainers to spin up a real PostgreSQL instance for testing.
 * This ensures tests run against the actual database with real constraints,
 * indexes, and SQL behavior.
 *
 * Test coverage:
 * - UUIDv7 generation and auto-increment behavior
 * - Unique constraint on channel_id
 * - Status-based filtering
 * - Renewal scheduling and tracking
 * - Lease expiration monitoring
 * - Timestamp auto-population (createdAt, updatedAt)
 */
@SpringBootTest(properties = {
    "spring.rabbitmq.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@Import(TestDatabaseConfig.class)
@Testcontainers
@Transactional
@ActiveProfiles("test")
@DisplayName("SubscriptionRepository Integration Tests")
class SubscriptionRepositoryTest {

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
    private SubscriptionRepository subscriptionRepository;

    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        testSubscription = Subscription.builder()
            .channelId("UCTestChannel123")
            .topicUrl("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCTestChannel123")
            .callbackUrl("https://example.com/webhook/callback")
            .subscriptionStatus(SubscriptionStatus.ACTIVE)
            .leaseSeconds(432000) // 5 days
            .leaseExpiresAt(now.plusSeconds(432000))
            .nextRenewalAt(now.plusSeconds(432000).minusHours(24))
            .renewalAttempts(0)
            .build();
    }

    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should generate UUIDv7 primary key on save")
    void shouldGenerateUUIDv7PrimaryKey() {
        // Arrange - testSubscription has no ID set

        // Act
        Subscription saved = subscriptionRepository.save(testSubscription);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should auto-populate createdAt and updatedAt timestamps")
    void shouldAutoPopulateTimestamps() {
        // Arrange
        OffsetDateTime beforeSave = OffsetDateTime.now();

        // Act
        Subscription saved = subscriptionRepository.save(testSubscription);

        // Assert
        OffsetDateTime afterSave = OffsetDateTime.now();
        assertThat(saved.getCreatedAt())
            .isNotNull()
            .isAfterOrEqualTo(beforeSave)
            .isBeforeOrEqualTo(afterSave);
        assertThat(saved.getUpdatedAt())
            .isNotNull()
            .isAfterOrEqualTo(beforeSave)
            .isBeforeOrEqualTo(afterSave);
    }

    @Test
    @DisplayName("Should update updatedAt timestamp on modification")
    void shouldUpdateUpdatedAtOnModification() throws InterruptedException {
        // Arrange
        Subscription saved = subscriptionRepository.save(testSubscription);
        OffsetDateTime originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(100); // Ensure timestamp difference

        // Act
        saved.setRenewalAttempts(5);
        Subscription updated = subscriptionRepository.saveAndFlush(saved);

        // Assert
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt()); // createdAt unchanged
    }

    @Test
    @DisplayName("Should prevent duplicate channel_id subscriptions")
    void shouldPreventDuplicateChannelId() {
        // Arrange
        subscriptionRepository.save(testSubscription);

        Subscription duplicate = Subscription.builder()
            .channelId("UCTestChannel123") // Same channel ID
            .topicUrl("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCTestChannel123")
            .callbackUrl("https://example.com/webhook/callback2")
            .subscriptionStatus(SubscriptionStatus.PENDING)
            .leaseSeconds(432000)
            .leaseExpiresAt(OffsetDateTime.now().plusSeconds(432000))
            .nextRenewalAt(OffsetDateTime.now().plusSeconds(432000).minusHours(24))
            .renewalAttempts(0)
            .build();

        // Act & Assert
        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_subscriptions_channel_id");
    }

    @Test
    @DisplayName("Should find subscription by channel ID")
    void shouldFindSubscriptionByChannelId() {
        // Arrange
        Subscription saved = subscriptionRepository.save(testSubscription);

        // Act
        Optional<Subscription> found = subscriptionRepository.findByChannelId("UCTestChannel123");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getChannelId()).isEqualTo("UCTestChannel123");
    }

    @Test
    @DisplayName("Should check subscription existence by channel ID")
    void shouldCheckSubscriptionExistenceByChannelId() {
        // Arrange
        subscriptionRepository.save(testSubscription);

        // Act
        boolean exists = subscriptionRepository.existsByChannelId("UCTestChannel123");
        boolean notExists = subscriptionRepository.existsByChannelId("UCNonExistent");

        // Assert
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should find subscriptions by status")
    void shouldFindSubscriptionsByStatus() {
        // Arrange
        Subscription active1 = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        Subscription active2 = createSubscription("UCChannel2", SubscriptionStatus.ACTIVE);
        Subscription pending = createSubscription("UCChannel3", SubscriptionStatus.PENDING);
        Subscription expired = createSubscription("UCChannel4", SubscriptionStatus.EXPIRED);

        subscriptionRepository.saveAll(List.of(active1, active2, pending, expired));

        // Act
        List<Subscription> activeSubscriptions = subscriptionRepository.findBySubscriptionStatus(SubscriptionStatus.ACTIVE);
        List<Subscription> pendingSubscriptions = subscriptionRepository.findBySubscriptionStatus(SubscriptionStatus.PENDING);

        // Assert
        assertThat(activeSubscriptions).hasSize(2);
        assertThat(pendingSubscriptions).hasSize(1);
        assertThat(activeSubscriptions)
            .extracting(Subscription::getSubscriptionStatus)
            .containsOnly(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should find subscriptions due for renewal")
    void shouldFindSubscriptionsDueForRenewal() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();

        Subscription dueNow = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        dueNow.setNextRenewalAt(now.minusHours(1)); // Due 1 hour ago

        Subscription dueSoon = createSubscription("UCChannel2", SubscriptionStatus.ACTIVE);
        dueSoon.setNextRenewalAt(now.plusHours(1)); // Due in 1 hour

        subscriptionRepository.saveAll(List.of(dueNow, dueSoon));

        // Act
        List<Subscription> dueSubscriptions = subscriptionRepository.findByNextRenewalAtBefore(now);

        // Assert
        assertThat(dueSubscriptions).hasSize(1);
        assertThat(dueSubscriptions.get(0).getChannelId()).isEqualTo("UCChannel1");
    }

    @Test
    @DisplayName("Should find active subscriptions due for renewal")
    void shouldFindActiveSubscriptionsDueForRenewal() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();

        Subscription activeDue = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        activeDue.setNextRenewalAt(now.minusHours(1));

        Subscription expiredDue = createSubscription("UCChannel2", SubscriptionStatus.EXPIRED);
        expiredDue.setNextRenewalAt(now.minusHours(1));

        subscriptionRepository.saveAll(List.of(activeDue, expiredDue));

        // Act
        List<Subscription> activeRenewals = subscriptionRepository.findActiveSubscriptionsDueForRenewal(now);

        // Assert
        assertThat(activeRenewals).hasSize(1);
        assertThat(activeRenewals.get(0).getChannelId()).isEqualTo("UCChannel1");
        assertThat(activeRenewals.get(0).getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should find subscriptions with expired leases")
    void shouldFindSubscriptionsWithExpiredLeases() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();

        Subscription expired = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        expired.setLeaseExpiresAt(now.minusHours(1));

        Subscription active = createSubscription("UCChannel2", SubscriptionStatus.ACTIVE);
        active.setLeaseExpiresAt(now.plusDays(1));

        subscriptionRepository.saveAll(List.of(expired, active));

        // Act
        List<Subscription> expiredLeases = subscriptionRepository.findByLeaseExpiresAtBefore(now);

        // Assert
        assertThat(expiredLeases).hasSize(1);
        assertThat(expiredLeases.get(0).getChannelId()).isEqualTo("UCChannel1");
    }

    @Test
    @DisplayName("Should find subscriptions with excessive renewal attempts")
    void shouldFindSubscriptionsWithExcessiveRenewalAttempts() {
        // Arrange
        Subscription lowAttempts = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        lowAttempts.setRenewalAttempts(2);

        Subscription highAttempts = createSubscription("UCChannel2", SubscriptionStatus.FAILED);
        highAttempts.setRenewalAttempts(5);

        subscriptionRepository.saveAll(List.of(lowAttempts, highAttempts));

        // Act
        List<Subscription> excessive = subscriptionRepository.findByRenewalAttemptsGreaterThanEqual(3);

        // Assert
        assertThat(excessive).hasSize(1);
        assertThat(excessive.get(0).getChannelId()).isEqualTo("UCChannel2");
        assertThat(excessive.get(0).getRenewalAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should find subscriptions by status and expiring soon")
    void shouldFindByStatusAndExpiringSoon() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime soonThreshold = now.plusHours(24);

        Subscription activeExpiringSoon = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        activeExpiringSoon.setLeaseExpiresAt(now.plusHours(12));

        Subscription activeNotExpiring = createSubscription("UCChannel2", SubscriptionStatus.ACTIVE);
        activeNotExpiring.setLeaseExpiresAt(now.plusDays(5));

        subscriptionRepository.saveAll(List.of(activeExpiringSoon, activeNotExpiring));

        // Act
        List<Subscription> expiringSoon = subscriptionRepository.findByStatusAndLeaseExpiresAtBefore(
            SubscriptionStatus.ACTIVE, soonThreshold
        );

        // Assert
        assertThat(expiringSoon).hasSize(1);
        assertThat(expiringSoon.get(0).getChannelId()).isEqualTo("UCChannel1");
    }

    @Test
    @DisplayName("Should count subscriptions by status")
    void shouldCountSubscriptionsByStatus() {
        // Arrange
        Subscription active1 = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        Subscription active2 = createSubscription("UCChannel2", SubscriptionStatus.ACTIVE);
        Subscription pending = createSubscription("UCChannel3", SubscriptionStatus.PENDING);

        subscriptionRepository.saveAll(List.of(active1, active2, pending));

        // Act
        long activeCount = subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE);
        long pendingCount = subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.PENDING);
        long expiredCount = subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.EXPIRED);

        // Assert
        assertThat(activeCount).isEqualTo(2);
        assertThat(pendingCount).isEqualTo(1);
        assertThat(expiredCount).isZero();
    }

    @Test
    @DisplayName("Should find recently created subscriptions")
    void shouldFindRecentlyCreatedSubscriptions() {
        // Arrange
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);

        Subscription recent = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(recent);

        // Act
        List<Subscription> recentSubscriptions = subscriptionRepository.findRecentlyCreated(since);

        // Assert
        assertThat(recentSubscriptions).hasSize(1);
        assertThat(recentSubscriptions.get(0).getChannelId()).isEqualTo("UCChannel1");
    }

    @Test
    @DisplayName("Should find subscriptions not renewed since timestamp")
    void shouldFindSubscriptionsNotRenewedSince() {
        // Arrange
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(7);

        Subscription neverRenewed = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        neverRenewed.setLastRenewedAt(null);

        Subscription recentlyRenewed = createSubscription("UCChannel2", SubscriptionStatus.ACTIVE);
        recentlyRenewed.setLastRenewedAt(OffsetDateTime.now().minusDays(1));

        Subscription oldRenewal = createSubscription("UCChannel3", SubscriptionStatus.ACTIVE);
        oldRenewal.setLastRenewedAt(OffsetDateTime.now().minusDays(10));

        subscriptionRepository.saveAll(List.of(neverRenewed, recentlyRenewed, oldRenewal));

        // Act
        List<Subscription> staleSubscriptions = subscriptionRepository.findNotRenewedSince(threshold);

        // Assert
        assertThat(staleSubscriptions).hasSize(2);
        assertThat(staleSubscriptions)
            .extracting(Subscription::getChannelId)
            .containsExactlyInAnyOrder("UCChannel1", "UCChannel3");
    }

    @Test
    @DisplayName("Should test default convenience methods")
    void shouldTestDefaultConvenienceMethods() {
        // Arrange
        Subscription active = createSubscription("UCChannel1", SubscriptionStatus.ACTIVE);
        Subscription pending = createSubscription("UCChannel2", SubscriptionStatus.PENDING);
        Subscription expired = createSubscription("UCChannel3", SubscriptionStatus.EXPIRED);
        Subscription failed = createSubscription("UCChannel4", SubscriptionStatus.FAILED);

        subscriptionRepository.saveAll(List.of(active, pending, expired, failed));

        // Act
        List<Subscription> allActive = subscriptionRepository.findAllActive();
        List<Subscription> allPending = subscriptionRepository.findAllPending();
        List<Subscription> allExpired = subscriptionRepository.findAllExpired();
        List<Subscription> allFailed = subscriptionRepository.findAllFailed();

        // Assert
        assertThat(allActive).hasSize(1);
        assertThat(allPending).hasSize(1);
        assertThat(allExpired).hasSize(1);
        assertThat(allFailed).hasSize(1);
    }

    @Test
    @DisplayName("Should test subscription entity helper methods")
    void shouldTestSubscriptionHelperMethods() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();

        Subscription subscription = Subscription.builder()
            .channelId("UCTestChannel")
            .topicUrl("https://youtube.com/feed")
            .callbackUrl("https://example.com/callback")
            .subscriptionStatus(SubscriptionStatus.ACTIVE)
            .leaseSeconds(432000)
            .leaseExpiresAt(now.plusDays(1))
            .nextRenewalAt(now.minusHours(1))
            .renewalAttempts(0)
            .build();

        // Act & Assert - isActive
        assertThat(subscription.isActive()).isTrue();

        // Act & Assert - isExpiringSoon
        assertThat(subscription.isExpiringSoon(48)).isTrue(); // Within 48 hours
        assertThat(subscription.isExpiringSoon(12)).isFalse(); // Not within 12 hours

        // Act & Assert - isRenewalDue
        assertThat(subscription.isRenewalDue()).isTrue();

        // Act & Assert - markRenewalSuccess
        subscription.markRenewalSuccess(432000, 24);
        assertThat(subscription.getRenewalAttempts()).isZero();
        assertThat(subscription.getLastRenewalError()).isNull();
        assertThat(subscription.getLastRenewedAt()).isNotNull();

        // Act & Assert - markRenewalFailure
        subscription.markRenewalFailure("Connection timeout");
        assertThat(subscription.getRenewalAttempts()).isEqualTo(1);
        assertThat(subscription.getLastRenewalError()).isEqualTo("Connection timeout");
    }

    @Test
    @DisplayName("Should preserve all subscription fields on save")
    void shouldPreserveAllSubscriptionFieldsOnSave() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();
        Subscription subscription = Subscription.builder()
            .channelId("UCChannel123")
            .topicUrl("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCChannel123")
            .callbackUrl("https://example.com/webhook")
            .subscriptionStatus(SubscriptionStatus.ACTIVE)
            .leaseSeconds(432000)
            .leaseExpiresAt(now.plusSeconds(432000))
            .lastRenewedAt(now.minusDays(1))
            .nextRenewalAt(now.plusSeconds(432000).minusHours(24))
            .renewalAttempts(0)
            .lastRenewalError(null)
            .build();

        // Act
        Subscription saved = subscriptionRepository.save(subscription);
        Subscription retrieved = subscriptionRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(retrieved.getChannelId()).isEqualTo("UCChannel123");
        assertThat(retrieved.getTopicUrl()).contains("UCChannel123");
        assertThat(retrieved.getCallbackUrl()).isEqualTo("https://example.com/webhook");
        assertThat(retrieved.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(retrieved.getLeaseSeconds()).isEqualTo(432000);
        assertThat(retrieved.getLeaseExpiresAt()).isNotNull();
        assertThat(retrieved.getNextRenewalAt()).isNotNull();
        assertThat(retrieved.getRenewalAttempts()).isZero();
    }

    // Helper methods

    private Subscription createSubscription(String channelId, SubscriptionStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return Subscription.builder()
            .channelId(channelId)
            .topicUrl("https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
            .callbackUrl("https://example.com/webhook/callback")
            .subscriptionStatus(status)
            .leaseSeconds(432000)
            .leaseExpiresAt(now.plusSeconds(432000))
            .nextRenewalAt(now.plusSeconds(432000).minusHours(24))
            .renewalAttempts(0)
            .build();
    }
}
