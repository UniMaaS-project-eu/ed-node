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

-- ==========================================
-- Grant specific permissions
-- ==========================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO identityhub_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO identityhub_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO identityhub_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO identityhub_user;