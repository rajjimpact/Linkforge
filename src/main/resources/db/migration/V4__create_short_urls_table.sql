-- V4__create_short_urls_table.sql
-- LinkForge — Short URLs core table

CREATE TABLE short_urls (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code              VARCHAR(50) NOT NULL UNIQUE,
    original_url            VARCHAR(2048) NOT NULL,
    title                   VARCHAR(255),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash           VARCHAR(255),
    is_private              BOOLEAN NOT NULL DEFAULT FALSE,
    is_one_time             BOOLEAN NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    click_count             BIGINT NOT NULL DEFAULT 0,
    unique_click_count      BIGINT NOT NULL DEFAULT 0,
    expires_at              TIMESTAMP,
    scheduled_start         TIMESTAMP,
    scheduled_end           TIMESTAMP,
    has_qr_code             BOOLEAN NOT NULL DEFAULT FALSE,
    qr_code_path            VARCHAR(500),
    is_safe                 BOOLEAN NOT NULL DEFAULT TRUE,
    safe_browsing_checked_at TIMESTAMP,
    last_health_check_at    TIMESTAMP,
    health_status           VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_short_url_short_code ON short_urls(short_code);
CREATE INDEX idx_short_url_user_id ON short_urls(user_id);
CREATE INDEX idx_short_url_expires_at ON short_urls(expires_at);
CREATE INDEX idx_short_url_is_active ON short_urls(is_active);
CREATE INDEX idx_short_url_created_at ON short_urls(created_at);
CREATE INDEX idx_short_url_is_safe ON short_urls(is_safe);

CREATE TRIGGER update_short_urls_updated_at
    BEFORE UPDATE ON short_urls
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
