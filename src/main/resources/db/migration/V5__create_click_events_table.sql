-- V5__create_click_events_table.sql
-- LinkForge — Click events analytics table

CREATE TABLE click_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_url_id    UUID NOT NULL REFERENCES short_urls(id) ON DELETE CASCADE,
    ip_hash         VARCHAR(64),
    user_agent      VARCHAR(512),
    referer         VARCHAR(2048),
    country         VARCHAR(100),
    country_code    VARCHAR(2),
    city            VARCHAR(100),
    region          VARCHAR(100),
    latitude        DECIMAL(9,6),
    longitude       DECIMAL(9,6),
    device          VARCHAR(20),
    browser         VARCHAR(100),
    browser_version VARCHAR(50),
    os              VARCHAR(100),
    os_version      VARCHAR(50),
    language        VARCHAR(10),
    timezone        VARCHAR(100),
    is_bot          BOOLEAN NOT NULL DEFAULT FALSE,
    bot_confidence  DECIMAL(5,4) NOT NULL DEFAULT 0,
    is_unique       BOOLEAN NOT NULL DEFAULT TRUE,
    source          VARCHAR(10) NOT NULL DEFAULT 'WEB',
    timestamp       TIMESTAMP NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (timestamp);

-- Create partitions for current and next 2 years
CREATE TABLE click_events_2025 PARTITION OF click_events
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

CREATE TABLE click_events_2026 PARTITION OF click_events
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE click_events_2027 PARTITION OF click_events
    FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');

CREATE TABLE click_events_default PARTITION OF click_events DEFAULT;

CREATE INDEX idx_click_event_url_id ON click_events(short_url_id);
CREATE INDEX idx_click_event_timestamp ON click_events(timestamp);
CREATE INDEX idx_click_event_country ON click_events(country);
CREATE INDEX idx_click_event_device ON click_events(device);
CREATE INDEX idx_click_event_is_bot ON click_events(is_bot);
CREATE INDEX idx_click_event_url_timestamp ON click_events(short_url_id, timestamp);
