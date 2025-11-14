package com.adtracker.webhookingest.model.generator;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Custom Hibernate identifier generator for UUIDv7.
 *
 * UUIDv7 embeds a timestamp in the first 48 bits, providing time-ordered UUIDs
 * that improve database index performance compared to random UUIDs (v4).
 *
 * Format (128 bits):
 * - 48 bits: Unix timestamp in milliseconds
 * - 12 bits: Random data (version and variant bits)
 * - 62 bits: Random data
 *
 * This implementation follows the UUIDv7 draft specification and ensures
 * monotonically increasing IDs when generated in sequence, which benefits:
 * - B-tree index locality
 * - Reduced index fragmentation
 * - Better query performance on time-based ranges
 *
 * Thread-safe implementation using SecureRandom for cryptographically
 * strong random number generation.
 */
public class UUIDv7Generator implements IdentifierGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a new UUIDv7 identifier.
     *
     * @param session Hibernate session (not used but required by interface)
     * @param object Entity object (not used but required by interface)
     * @return UUIDv7 as a Serializable UUID
     */
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        return generateUUIDv7();
    }

    /**
     * Generates a UUIDv7 with embedded timestamp.
     *
     * Structure:
     * - Bytes 0-5: Timestamp (48 bits, big-endian)
     * - Bytes 6-7: Version (4 bits = 0x7) + Random (12 bits)
     * - Bytes 8-15: Variant (2 bits = 0b10) + Random (62 bits)
     *
     * @return UUIDv7 instance
     */
    public static UUID generateUUIDv7() {
        // Get current timestamp in milliseconds
        long timestamp = Instant.now().toEpochMilli();

        // Generate random bytes for the remainder
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        // Build UUID bytes array (16 bytes total)
        byte[] uuidBytes = new byte[16];

        // Bytes 0-5: Timestamp (48 bits, big-endian)
        uuidBytes[0] = (byte) (timestamp >>> 40);
        uuidBytes[1] = (byte) (timestamp >>> 32);
        uuidBytes[2] = (byte) (timestamp >>> 24);
        uuidBytes[3] = (byte) (timestamp >>> 16);
        uuidBytes[4] = (byte) (timestamp >>> 8);
        uuidBytes[5] = (byte) timestamp;

        // Bytes 6-7: Version (0x7) in upper 4 bits + random in lower 12 bits
        uuidBytes[6] = (byte) ((0x70) | (randomBytes[0] & 0x0F));
        uuidBytes[7] = randomBytes[1];

        // Bytes 8-9: Variant (0b10) in upper 2 bits + random in lower 14 bits
        uuidBytes[8] = (byte) ((0x80) | (randomBytes[2] & 0x3F));
        uuidBytes[9] = randomBytes[3];

        // Bytes 10-15: Random (48 bits)
        System.arraycopy(randomBytes, 4, uuidBytes, 10, 6);

        // Convert bytes to UUID
        return fromBytes(uuidBytes);
    }

    /**
     * Constructs a UUID from a byte array.
     *
     * @param bytes 16-byte array representing the UUID
     * @return UUID instance
     */
    private static UUID fromBytes(byte[] bytes) {
        long mostSigBits = 0;
        long leastSigBits = 0;

        // Most significant 64 bits (bytes 0-7)
        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (bytes[i] & 0xff);
        }

        // Least significant 64 bits (bytes 8-15)
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (bytes[i] & 0xff);
        }

        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * Extracts the timestamp from a UUIDv7.
     *
     * @param uuid UUIDv7 instance
     * @return Instant representing the embedded timestamp
     */
    public static Instant extractTimestamp(UUID uuid) {
        long mostSigBits = uuid.getMostSignificantBits();
        long timestamp = mostSigBits >>> 16;
        return Instant.ofEpochMilli(timestamp);
    }
}
