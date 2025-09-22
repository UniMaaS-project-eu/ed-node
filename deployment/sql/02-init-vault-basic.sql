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

-- ==========================================
-- Grant specific permissions
-- ==========================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO vault_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO vault_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO vault_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO vault_user;