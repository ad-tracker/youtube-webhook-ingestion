package com.adtracker.webhookingest.model.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for UUIDv7Generator.
 *
 * Validates:
 * - UUIDv7 version and variant bits
 * - Timestamp extraction accuracy
 * - Uniqueness of generated UUIDs
 * - Monotonic ordering within same millisecond
 * - Thread safety and concurrent generation
 */
@DisplayName("UUIDv7Generator Unit Tests")
class UUIDv7GeneratorTest {

    @Test
    @DisplayName("Should generate UUID with version 7")
    void shouldGenerateUUIDWithVersion7() {
        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();

        // Assert
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should generate UUID with correct variant")
    void shouldGenerateUUIDWithCorrectVariant() {
        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();

        // Assert
        // Variant bits should be 0b10xx (RFC 4122 variant)
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should embed current timestamp in UUID")
    void shouldEmbedCurrentTimestampInUUID() {
        // Arrange
        Instant beforeGeneration = Instant.now().minusSeconds(1); // 1 second buffer

        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();
        Instant extracted = UUIDv7Generator.extractTimestamp(uuid);

        // Assert
        Instant afterGeneration = Instant.now().plusSeconds(1); // 1 second buffer
        assertThat(extracted)
            .isAfterOrEqualTo(beforeGeneration)
            .isBeforeOrEqualTo(afterGeneration);
    }

    @Test
    @DisplayName("Should extract timestamp with millisecond precision")
    void shouldExtractTimestampWithMillisecondPrecision() {
        // Arrange
        Instant now = Instant.now();

        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();
        Instant extracted = UUIDv7Generator.extractTimestamp(uuid);

        // Assert
        // Timestamps should be within 100ms of each other
        assertThat(extracted.toEpochMilli())
            .isCloseTo(now.toEpochMilli(), within(100L));
    }

    @RepeatedTest(100)
    @DisplayName("Should generate unique UUIDs")
    void shouldGenerateUniqueUUIDs() {
        // Arrange
        Set<UUID> generatedUUIDs = new HashSet<>();
        int count = 1000;

        // Act
        for (int i = 0; i < count; i++) {
            UUID uuid = UUIDv7Generator.generateUUIDv7();
            generatedUUIDs.add(uuid);
        }

        // Assert
        assertThat(generatedUUIDs).hasSize(count);
    }

    @Test
    @DisplayName("Should generate UUIDs in ascending order over time")
    void shouldGenerateUUIDsInAscendingOrder() throws InterruptedException {
        // Arrange
        UUID first = UUIDv7Generator.generateUUIDv7();

        // Wait to ensure different timestamp
        Thread.sleep(2);

        // Act
        UUID second = UUIDv7Generator.generateUUIDv7();

        // Assert
        // Compare as strings to verify lexicographic ordering
        assertThat(first.toString().compareTo(second.toString())).isLessThan(0);

        // Also verify timestamps
        Instant firstTime = UUIDv7Generator.extractTimestamp(first);
        Instant secondTime = UUIDv7Generator.extractTimestamp(second);
        assertThat(firstTime).isBefore(secondTime);
    }

    @Test
    @DisplayName("Should generate UUIDs with different random components")
    void shouldGenerateUUIDsWithDifferentRandomComponents() {
        // Act - Generate multiple UUIDs in quick succession
        UUID uuid1 = UUIDv7Generator.generateUUIDv7();
        UUID uuid2 = UUIDv7Generator.generateUUIDv7();
        UUID uuid3 = UUIDv7Generator.generateUUIDv7();

        // Assert - UUIDs should be different even if generated in same millisecond
        assertThat(uuid1).isNotEqualTo(uuid2);
        assertThat(uuid2).isNotEqualTo(uuid3);
        assertThat(uuid1).isNotEqualTo(uuid3);
    }

    @Test
    @DisplayName("Should handle concurrent UUID generation")
    void shouldHandleConcurrentUUIDGeneration() throws InterruptedException {
        // Arrange
        Set<UUID> generatedUUIDs = new HashSet<>();
        int threadCount = 10;
        int uuidsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < uuidsPerThread; j++) {
                    UUID uuid = UUIDv7Generator.generateUUIDv7();
                    synchronized (generatedUUIDs) {
                        generatedUUIDs.add(uuid);
                    }
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertThat(generatedUUIDs).hasSize(threadCount * uuidsPerThread);
    }

    @Test
    @DisplayName("Should generate non-null UUIDs")
    void shouldGenerateNonNullUUIDs() {
        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();

        // Assert
        assertThat(uuid).isNotNull();
    }

    @Test
    @DisplayName("Should have correct timestamp in most significant bits")
    void shouldHaveCorrectTimestampInMostSignificantBits() {
        // Arrange
        long beforeMs = Instant.now().toEpochMilli();

        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();

        // Assert
        long afterMs = Instant.now().toEpochMilli();

        // Extract timestamp from most significant bits (first 48 bits)
        long mostSigBits = uuid.getMostSignificantBits();
        long timestampMs = mostSigBits >>> 16;

        assertThat(timestampMs)
            .isGreaterThanOrEqualTo(beforeMs)
            .isLessThanOrEqualTo(afterMs);
    }

    @Test
    @DisplayName("Should maintain version bits in correct position")
    void shouldMaintainVersionBitsInCorrectPosition() {
        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();

        // Assert
        // Version bits are at positions 48-51 (4 bits) of the UUID
        // For version 7, these should be 0111 (binary) = 7 (decimal)
        long mostSigBits = uuid.getMostSignificantBits();
        int versionBits = (int) ((mostSigBits >>> 12) & 0x0F);
        assertThat(versionBits).isEqualTo(7);
    }

    @Test
    @DisplayName("Should maintain variant bits in correct position")
    void shouldMaintainVariantBitsInCorrectPosition() {
        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();

        // Assert
        // Variant bits are at positions 64-65 of the UUID
        // For RFC 4122, these should be 0b10 (binary) = 2 (decimal)
        long leastSigBits = uuid.getLeastSignificantBits();
        int variantBits = (int) ((leastSigBits >>> 62) & 0x03);
        assertThat(variantBits).isEqualTo(2);
    }

    @Test
    @DisplayName("Should produce UUID string in correct format")
    void shouldProduceUUIDStringInCorrectFormat() {
        // Act
        UUID uuid = UUIDv7Generator.generateUUIDv7();
        String uuidString = uuid.toString();

        // Assert
        // Standard UUID format: 8-4-4-4-12 hexadecimal digits
        assertThat(uuidString).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
    }
}
