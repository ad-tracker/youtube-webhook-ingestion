-- Initialize database schema for webhook ingestion service

-- Create schema
CREATE SCHEMA IF NOT EXISTS webhook_ingestion;

-- Set search path
SET search_path TO webhook_ingestion;

-- Create webhook_events table
CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id VARCHAR(50) NOT NULL,
    channel_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    source_ip VARCHAR(45),
    user_agent VARCHAR(500),
    processed BOOLEAN NOT NULL DEFAULT false,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_webhook_events_video_id ON webhook_events(video_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_channel_id ON webhook_events(channel_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_processed ON webhook_events(processed);
CREATE INDEX IF NOT EXISTS idx_webhook_events_processing_status ON webhook_events(processing_status);
CREATE INDEX IF NOT EXISTS idx_webhook_events_created_at ON webhook_events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_events_event_type ON webhook_events(event_type);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at
CREATE TRIGGER update_webhook_events_updated_at
    BEFORE UPDATE ON webhook_events
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (adjust as needed for production)
GRANT ALL PRIVILEGES ON SCHEMA webhook_ingestion TO postgres;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA webhook_ingestion TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA webhook_ingestion TO postgres;

-- Success message
SELECT 'Database schema initialized successfully' AS status;
