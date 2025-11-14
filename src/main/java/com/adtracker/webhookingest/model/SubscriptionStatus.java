package com.adtracker.webhookingest.model;

/**
 * Enumeration of possible subscription statuses for YouTube channel subscriptions.
 *
 * Represents the lifecycle states of a PubSubHubbub subscription:
 * - PENDING: Subscription request submitted, awaiting confirmation
 * - ACTIVE: Subscription confirmed and currently active
 * - EXPIRED: Subscription lease has expired
 * - FAILED: Subscription or renewal attempt failed
 *
 * Status transitions:
 * - PENDING → ACTIVE: Hub confirms subscription
 * - PENDING → FAILED: Hub rejects subscription
 * - ACTIVE → EXPIRED: Lease expires without renewal
 * - ACTIVE → FAILED: Renewal attempts exhausted
 * - EXPIRED → ACTIVE: Successful renewal after expiration
 * - FAILED → ACTIVE: Successful re-subscription
 */
public enum SubscriptionStatus {

    /**
     * Subscription request has been submitted to the hub but not yet confirmed.
     * Awaiting callback verification from PubSubHubbub hub.
     */
    PENDING("pending"),

    /**
     * Subscription is confirmed and actively receiving webhook notifications.
     * Lease is valid and within expiration period.
     */
    ACTIVE("active"),

    /**
     * Subscription lease has expired.
     * No longer receiving notifications until renewed.
     */
    EXPIRED("expired"),

    /**
     * Subscription or renewal attempt has failed.
     * Requires manual intervention or retry with backoff.
     */
    FAILED("failed");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of the status.
     * Matches the database column value.
     *
     * @return lowercase status value
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a string value to SubscriptionStatus enum.
     *
     * @param value string representation of status
     * @return corresponding SubscriptionStatus
     * @throws IllegalArgumentException if value doesn't match any status
     */
    public static SubscriptionStatus fromValue(String value) {
        for (SubscriptionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid subscription status: " + value);
    }

    /**
     * Checks if the subscription is in an active state.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Checks if the subscription needs renewal or re-subscription.
     *
     * @return true if status is EXPIRED or FAILED
     */
    public boolean needsRenewal() {
        return this == EXPIRED || this == FAILED;
    }

    /**
     * Checks if the subscription is awaiting confirmation.
     *
     * @return true if status is PENDING
     */
    public boolean isPending() {
        return this == PENDING;
    }

    @Override
    public String toString() {
        return value;
    }
}
