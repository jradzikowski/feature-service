-- V1: feature-service initial schema.
-- Portable Postgres / H2 (MODE=PostgreSQL): no UUID-generating functions, ids assigned by the app.

CREATE TABLE applications (
    id             UUID         NOT NULL,
    slug           VARCHAR(64)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    config_version BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_applications_slug UNIQUE (slug)
);
COMMENT ON TABLE applications IS 'Applications consuming feature flags (audit is the first one)';
COMMENT ON COLUMN applications.config_version IS 'Monotonic change counter; source of the Evaluation API ETag';

CREATE TABLE flags (
    id             UUID         NOT NULL,
    application_id UUID         NOT NULL,
    flag_key       VARCHAR(128) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    value_type     VARCHAR(16)  NOT NULL,
    default_value  TEXT         NOT NULL,
    flag_kind      VARCHAR(16)  NOT NULL,
    locked         BOOLEAN      NOT NULL DEFAULT FALSE,
    archived       BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at     DATE,
    owner          VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_flags_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT uq_flags_app_key UNIQUE (application_id, flag_key)
);
COMMENT ON COLUMN flags.default_value IS 'Global value as JSON matching value_type; editing it changes every workgroup without an override';
COMMENT ON COLUMN flags.locked IS 'Kill switch: TRUE => per-workgroup overrides are ignored, everyone gets default_value';
COMMENT ON COLUMN flags.flag_kind IS 'RELEASE|EXPERIMENT|OPS|PERMISSION - lifecycle categories (Fowler)';

CREATE INDEX idx_flags_application ON flags (application_id);

CREATE TABLE flag_overrides (
    id           UUID         NOT NULL,
    flag_id      UUID         NOT NULL,
    workgroup_id UUID         NOT NULL,
    value        TEXT         NOT NULL,
    note         VARCHAR(512),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_overrides_flag FOREIGN KEY (flag_id) REFERENCES flags (id) ON DELETE CASCADE,
    CONSTRAINT uq_overrides_flag_wg UNIQUE (flag_id, workgroup_id)
);
COMMENT ON COLUMN flag_overrides.workgroup_id IS 'Workgroup id from the consuming application; existence is not verified here';

CREATE INDEX idx_overrides_flag ON flag_overrides (flag_id);

CREATE TABLE flag_audit_log (
    id             UUID         NOT NULL,
    application_id UUID         NOT NULL,
    flag_key       VARCHAR(128) NOT NULL,
    operation      VARCHAR(32)  NOT NULL,
    workgroup_id   UUID,
    old_value      TEXT,
    new_value      TEXT,
    actor_username VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flag_audit_log IS 'Append-only change journal; flag_key intentionally has no FK so the log survives flag deletion';

CREATE INDEX idx_audit_app_key_time ON flag_audit_log (application_id, flag_key, created_at DESC);

CREATE TABLE api_tokens (
    id             UUID         NOT NULL,
    application_id UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    token_prefix   VARCHAR(24)  NOT NULL,
    revoked_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tokens_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT uq_tokens_hash UNIQUE (token_hash)
);
COMMENT ON COLUMN api_tokens.token_hash IS 'SHA-256 hex of the token; the plaintext is never stored';

CREATE TABLE admin_users (
    id            UUID         NOT NULL,
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_admin_users_username UNIQUE (username)
);
