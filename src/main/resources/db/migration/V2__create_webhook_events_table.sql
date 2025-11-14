-- Migration for webhook_events table (legacy/existing webhook storage)
-- This table is separate from the webhook_ingestion.events table

CREATE TABLE IF NOT EXISTS webhook_ingestion.webhook_events (
    id UUID PRIMARY KEY,
    video_id VARCHAR(50) NOT NULL,
    channel_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    source_ip VARCHAR(45),
    user_agent VARCHAR(500),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common query patterns
CREATE INDEX idx_webhook_events_video_id ON webhook_ingestion.webhook_events(video_id);
CREATE INDEX idx_webhook_events_channel_id ON webhook_ingestion.webhook_events(channel_id);
CREATE INDEX idx_webhook_events_processed ON webhook_ingestion.webhook_events(processed);
CREATE INDEX idx_webhook_events_processing_status ON webhook_ingestion.webhook_events(processing_status);
CREATE INDEX idx_webhook_events_created_at ON webhook_ingestion.webhook_events(created_at);
