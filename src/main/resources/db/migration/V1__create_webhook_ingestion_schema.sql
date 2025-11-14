-- =====================================================================
-- Flyway Migration V1: Create webhook_ingestion schema
-- =====================================================================
-- Description: Initial schema creation for YouTube webhook ingestion service
-- Author: Database Team
-- Date: 2025-11-13
-- JIRA: ADTRACKER-4
-- =====================================================================

-- Create dedicated schema for webhook ingestion
CREATE SCHEMA IF NOT EXISTS webhook_ingestion;

-- Enable uuid-ossp extension for random UUID generation support
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================================
-- UUIDv7 Generation Function
-- =====================================================================
-- UUIDv7 embeds a timestamp in the first 48 bits, providing time-based
-- ordering which improves database performance for indexed UUID primary keys
-- =====================================================================

CREATE OR REPLACE FUNCTION webhook_ingestion.uuid_generate_v7()
RETURNS UUID AS $$
DECLARE
  unix_ts_ms BIGINT;
  uuid_bytes BYTEA;
BEGIN
  -- Get current timestamp in milliseconds since epoch
  unix_ts_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;

  -- Construct UUIDv7:
  -- - First 48 bits (12 hex chars): timestamp in milliseconds
  -- - Remaining 80 bits (20 hex chars): random data
  uuid_bytes := E'\\x' ||
    LPAD(TO_HEX(unix_ts_ms), 12, '0') ||
    ENCODE(gen_random_bytes(10), 'hex');

  RETURN uuid_bytes::UUID;
END;
$$ LANGUAGE plpgsql VOLATILE;

-- =====================================================================
-- Events Table
-- =====================================================================
-- Purpose: Store raw XML webhook notifications from YouTube PubSubHubbub
-- Design: Insert-only table for audit trail and event sourcing
-- =====================================================================

CREATE TABLE webhook_ingestion.events (
    id UUID PRIMARY KEY DEFAULT webhook_ingestion.uuid_generate_v7(),
    event_type VARCHAR(50) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    video_id VARCHAR(255) NOT NULL,
    raw_xml TEXT NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure event deduplication via content hash
    CONSTRAINT uq_event_hash UNIQUE (event_hash)
);

-- Performance indexes for common query patterns
CREATE INDEX idx_events_channel_id ON webhook_ingestion.events(channel_id);
CREATE INDEX idx_events_video_id ON webhook_ingestion.events(video_id);
CREATE INDEX idx_events_received_at ON webhook_ingestion.events(received_at);
CREATE INDEX idx_events_created_at ON webhook_ingestion.events(created_at);

-- Add table comment for documentation
COMMENT ON TABLE webhook_ingestion.events IS 'Stores raw YouTube webhook notifications for audit trail and event sourcing. Insert-only table.';
COMMENT ON COLUMN webhook_ingestion.events.id IS 'UUIDv7 primary key with embedded timestamp for optimal indexing';
COMMENT ON COLUMN webhook_ingestion.events.event_hash IS 'SHA-256 hash of raw_xml for deduplication';
COMMENT ON COLUMN webhook_ingestion.events.received_at IS 'Timestamp when webhook was received by the service';
COMMENT ON COLUMN webhook_ingestion.events.created_at IS 'Timestamp when record was created in database';

-- =====================================================================
-- Subscriptions Table
-- =====================================================================
-- Purpose: Track active YouTube channel subscriptions and lease management
-- Design: Supports PubSubHubbub subscription lifecycle and renewal tracking
-- =====================================================================

CREATE TABLE webhook_ingestion.subscriptions (
    id UUID PRIMARY KEY DEFAULT webhook_ingestion.uuid_generate_v7(),
    channel_id VARCHAR(255) NOT NULL,
    topic_url VARCHAR(500) NOT NULL,
    callback_url VARCHAR(500) NOT NULL,
    subscription_status VARCHAR(50) NOT NULL,
    lease_seconds INTEGER NOT NULL,
    lease_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_renewed_at TIMESTAMP WITH TIME ZONE,
    next_renewal_at TIMESTAMP WITH TIME ZONE NOT NULL,
    renewal_attempts INTEGER NOT NULL DEFAULT 0,
    last_renewal_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure only one subscription per channel
    CONSTRAINT uq_subscriptions_channel_id UNIQUE (channel_id),

    -- Validate subscription status values
    CONSTRAINT chk_subscription_status CHECK (subscription_status IN ('active', 'pending', 'expired', 'failed'))
);

-- Performance indexes for subscription management queries
CREATE INDEX idx_subscriptions_channel_id ON webhook_ingestion.subscriptions(channel_id);
CREATE INDEX idx_subscriptions_status ON webhook_ingestion.subscriptions(subscription_status);
CREATE INDEX idx_subscriptions_next_renewal ON webhook_ingestion.subscriptions(next_renewal_at);
CREATE INDEX idx_subscriptions_lease_expires ON webhook_ingestion.subscriptions(lease_expires_at);

-- Add table comments for documentation
COMMENT ON TABLE webhook_ingestion.subscriptions IS 'Tracks YouTube channel subscriptions and manages PubSubHubbub lease renewals';
COMMENT ON COLUMN webhook_ingestion.subscriptions.id IS 'UUIDv7 primary key with embedded timestamp for optimal indexing';
COMMENT ON COLUMN webhook_ingestion.subscriptions.subscription_status IS 'Current status: active, pending, expired, or failed';
COMMENT ON COLUMN webhook_ingestion.subscriptions.lease_seconds IS 'Lease duration in seconds as confirmed by hub';
COMMENT ON COLUMN webhook_ingestion.subscriptions.lease_expires_at IS 'When the current lease expires';
COMMENT ON COLUMN webhook_ingestion.subscriptions.next_renewal_at IS 'When to attempt next renewal (before expiration)';
COMMENT ON COLUMN webhook_ingestion.subscriptions.renewal_attempts IS 'Counter for tracking renewal retry attempts';

-- =====================================================================
-- End of Migration V1
-- =====================================================================
