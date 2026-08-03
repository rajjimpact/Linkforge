-- V3__create_api_keys_table.sql
-- LinkForge — API Keys for developer access

CREATE TABLE api_keys (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                  VARCHAR(100) NOT NULL,
    key_hash              VARCHAR(64) NOT NULL UNIQUE,
    key_prefix            VARCHAR(12) NOT NULL,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    rate_limit_per_minute INTEGER,
    last_used_at          TIMESTAMP,
    total_requests        BIGINT NOT NULL DEFAULT 0,
    expires_at            TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_key_hash ON api_keys(key_hash);
CREATE INDEX idx_api_key_user_id ON api_keys(user_id);
CREATE INDEX idx_api_key_enabled ON api_keys(enabled);
