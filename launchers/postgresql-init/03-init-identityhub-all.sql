-- ==========================================
-- IdentityHub database and user setup
-- ==========================================

CREATE DATABASE identityhub;
CREATE USER identityhub_user WITH PASSWORD 'identityhub_password';

GRANT ALL PRIVILEGES ON DATABASE identityhub TO identityhub_user;
GRANT CONNECT ON DATABASE identityhub TO identityhub_user;

\connect identityhub

GRANT ALL PRIVILEGES ON SCHEMA public TO identityhub_user;
GRANT USAGE ON SCHEMA public TO identityhub_user;


CREATE SCHEMA IF NOT EXISTS identityhub AUTHORIZATION identityhub_user;
SET search_path TO identityhub;

CREATE TABLE edc_participant (
    participant_id VARCHAR(255) NOT NULL PRIMARY KEY,
    did VARCHAR(255) NOT NULL UNIQUE,
    state INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE edc_verifiable_credential (
    credential_id VARCHAR(255) NOT NULL PRIMARY KEY,
    holder_id VARCHAR(255) NOT NULL,
    issuer_id VARCHAR(255) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    credential_type VARCHAR(255) NOT NULL,
    state INTEGER NOT NULL DEFAULT 0,
    raw_vc TEXT NOT NULL,
    issued_at BIGINT,
    expires_at BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE edc_verifiable_presentation (
    presentation_id VARCHAR(255) NOT NULL PRIMARY KEY,
    holder_id VARCHAR(255) NOT NULL,
    audience VARCHAR(255),
    state INTEGER NOT NULL DEFAULT 0,
    raw_vp TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE edc_did_resource (
    did VARCHAR(255) NOT NULL PRIMARY KEY,
    document TEXT NOT NULL,
    state INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE edc_key_pair_resource (
    key_id VARCHAR(255) NOT NULL PRIMARY KEY,
    participant_id VARCHAR(255) NOT NULL,
    key_type VARCHAR(50) NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    private_key_alias VARCHAR(255),
    public_key_pem TEXT,
    use_type VARCHAR(50) DEFAULT 'sig',
    active BOOLEAN DEFAULT true,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE edc_sts_token (
    token_id VARCHAR(255) NOT NULL PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    audience VARCHAR(255),
    expires_at BIGINT NOT NULL,
    scope VARCHAR(255),
    token_hash VARCHAR(255) NOT NULL,
    revoked BOOLEAN DEFAULT false,
    created_at BIGINT NOT NULL
);

CREATE TABLE edc_identity_hub_audit (
    audit_id VARCHAR(255) NOT NULL PRIMARY KEY,
    participant_id VARCHAR(255),
    operation VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    details TEXT,
    timestamp BIGINT NOT NULL
);

CREATE INDEX idx_vc_holder ON edc_verifiable_credential(holder_id);
CREATE INDEX idx_vc_issuer ON edc_verifiable_credential(issuer_id);
CREATE INDEX idx_vc_subject ON edc_verifiable_credential(subject_id);
CREATE INDEX idx_vc_type ON edc_verifiable_credential(credential_type);
CREATE INDEX idx_vc_state ON edc_verifiable_credential(state);
CREATE INDEX idx_vc_expires ON edc_verifiable_credential(expires_at);

CREATE INDEX idx_vp_holder ON edc_verifiable_presentation(holder_id);
CREATE INDEX idx_vp_audience ON edc_verifiable_presentation(audience);
CREATE INDEX idx_vp_state ON edc_verifiable_presentation(state);

CREATE INDEX idx_keypair_participant ON edc_key_pair_resource(participant_id);
CREATE INDEX idx_keypair_active ON edc_key_pair_resource(active);
CREATE INDEX idx_keypair_type ON edc_key_pair_resource(key_type);

CREATE INDEX idx_sts_client ON edc_sts_token(client_id);
CREATE INDEX idx_sts_subject ON edc_sts_token(subject);
CREATE INDEX idx_sts_expires ON edc_sts_token(expires_at);
CREATE INDEX idx_sts_revoked ON edc_sts_token(revoked);

CREATE INDEX idx_audit_participant ON edc_identity_hub_audit(participant_id);
CREATE INDEX idx_audit_operation ON edc_identity_hub_audit(operation);
CREATE INDEX idx_audit_timestamp ON edc_identity_hub_audit(timestamp);

-- ==========================================
-- Grant specific permissions
-- ==========================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO identityhub_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO identityhub_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO identityhub_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO identityhub_user;