-- ==========================================
-- Vault database and user setup
-- ==========================================

CREATE DATABASE vault;
CREATE USER vault_user WITH PASSWORD 'vault_password';

GRANT ALL PRIVILEGES ON DATABASE vault TO vault_user;
GRANT CONNECT ON DATABASE vault TO vault_user;

\connect vault

GRANT ALL PRIVILEGES ON SCHEMA public TO vault_user;
GRANT USAGE ON SCHEMA public TO vault_user;

CREATE TABLE vault_kv_store (
  parent_path TEXT COLLATE "C" NOT NULL,
  path        TEXT COLLATE "C",
  key         TEXT COLLATE "C",
  value       BYTEA,
  CONSTRAINT pkey PRIMARY KEY (path, key)
);

CREATE INDEX parent_path_idx ON vault_kv_store (parent_path);

CREATE TABLE vault_ha_locks (
  ha_key        TEXT COLLATE "C" NOT NULL,
  ha_identity   TEXT COLLATE "C" NOT NULL,
  ha_value      TEXT COLLATE "C",
  valid_until   TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ha_key PRIMARY KEY (ha_key)
);

-- ==========================================
-- Grant specific permissions
-- ==========================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO vault_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO vault_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO vault_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO vault_user;
