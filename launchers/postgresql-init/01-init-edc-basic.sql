-- ==========================================
-- EDC database and user setup
-- ==========================================

-- Created by default (docker-compose)
-- CREATE DATABASE edc;
-- CREATE USER edc_user WITH PASSWORD 'edc_password';

GRANT ALL PRIVILEGES ON DATABASE edc TO edc_user;
GRANT CONNECT ON DATABASE edc TO edc_user;

\connect edc

GRANT ALL PRIVILEGES ON SCHEMA public TO edc_user;
GRANT USAGE ON SCHEMA public TO edc_user;

-- ==========================================
-- Grant specific permissions
-- ==========================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO edc_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO edc_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO edc_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO edc_user;