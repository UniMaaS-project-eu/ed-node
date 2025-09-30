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

-- ############################### Assets ###############################

CREATE TABLE IF NOT EXISTS edc_asset
(
    asset_id           VARCHAR NOT NULL,
    created_at         BIGINT  NOT NULL,
    properties         JSON    DEFAULT '{}',
    private_properties JSON    DEFAULT '{}',
    data_address       JSON    DEFAULT '{}',
    PRIMARY KEY (asset_id)
);

COMMENT ON COLUMN edc_asset.properties IS 'Asset properties serialized as JSON';
COMMENT ON COLUMN edc_asset.private_properties IS 'Asset private properties serialized as JSON';
COMMENT ON COLUMN edc_asset.data_address IS 'Asset DataAddress serialized as JSON';

--INDICES OPCIONALES POSIBLE MEJORA DE RENDIMIENTO:
CREATE UNIQUE INDEX IF NOT EXISTS edc_asset_id_uindex ON edc_asset (asset_id);

/*
CREATE TABLE IF NOT EXISTS edc_asset_property (
    asset_id VARCHAR NOT NULL,
    property_name VARCHAR NOT NULL,
    property_value VARCHAR,
    property_type VARCHAR NOT NULL,
    PRIMARY KEY (asset_id, property_name),
    FOREIGN KEY (asset_id) REFERENCES edc_asset (asset_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS edc_asset_dataaddress (
    asset_id VARCHAR NOT NULL,
    properties JSON,
    PRIMARY KEY (asset_id),
    FOREIGN KEY (asset_id) REFERENCES edc_asset (asset_id) ON DELETE CASCADE
);

--CREATE INDEX IF NOT EXISTS idx_asset_property_asset_id ON edc_asset_property(asset_id);

*/



-- ############################### Policy Definitions ###############################

CREATE TABLE IF NOT EXISTS edc_policydefinitions
(
    policy_id             VARCHAR NOT NULL,
    created_at            BIGINT  NOT NULL,
    permissions           JSON,
    prohibitions          JSON,
    duties                JSON,
    profiles              JSON,
    extensible_properties JSON,
    inherits_from         VARCHAR,
    assigner              VARCHAR,
    assignee              VARCHAR,
    target                VARCHAR,
    policy_type           VARCHAR NOT NULL,
    private_properties    JSON,
    PRIMARY KEY (policy_id)
);

COMMENT ON COLUMN edc_policydefinitions.permissions IS 'Java List<Permission> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.prohibitions IS 'Java List<Prohibition> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.duties IS 'Java List<Duty> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.profiles IS 'Java List<String> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.extensible_properties IS 'Java Map<String, Object> serialized as JSON';
COMMENT ON COLUMN edc_policydefinitions.policy_type IS 'Java PolicyType serialized as JSON';

CREATE UNIQUE INDEX IF NOT EXISTS edc_policydefinitions_id_uindex ON edc_policydefinitions (policy_id);

-- ############################### Contract Definitions ###############################

CREATE TABLE IF NOT EXISTS edc_contract_definitions
(
    created_at             BIGINT  NOT NULL,
    contract_definition_id VARCHAR NOT NULL,
    access_policy_id       VARCHAR NOT NULL,
    contract_policy_id     VARCHAR NOT NULL,
    assets_selector        JSON    NOT NULL,
    private_properties     JSON,
    PRIMARY KEY (contract_definition_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS edc_contract_id_uindex ON edc_contract_definitions (contract_definition_id);
CREATE INDEX IF NOT EXISTS idx_contract_def_access_policy ON edc_contract_definitions(access_policy_id);
CREATE INDEX IF NOT EXISTS idx_contract_def_contract_policy ON edc_contract_definitions(contract_policy_id);

-- ############################### Lease ###############################

CREATE TABLE IF NOT EXISTS edc_lease
(
    leased_by      VARCHAR               NOT NULL,
    leased_at      BIGINT,
    lease_duration INTEGER DEFAULT 60000 NOT NULL,
    lease_id       VARCHAR               NOT NULL
        CONSTRAINT lease_pk
            PRIMARY KEY
);

COMMENT ON COLUMN edc_lease.leased_at IS 'posix timestamp of lease';

COMMENT ON COLUMN edc_lease.lease_duration IS 'duration of lease in milliseconds';

CREATE UNIQUE INDEX IF NOT EXISTS lease_lease_id_uindex ON edc_lease (lease_id);



-- ############################### Contract Agreement ###############################

CREATE TABLE IF NOT EXISTS edc_contract_agreement
(
    agr_id            VARCHAR NOT NULL
        CONSTRAINT contract_agreement_pk
            PRIMARY KEY,
    provider_agent_id VARCHAR,
    consumer_agent_id VARCHAR,
    signing_date      BIGINT,
    start_date        BIGINT,
    end_date          INTEGER,
    asset_id          VARCHAR NOT NULL,
    policy            JSON
);

CREATE UNIQUE INDEX IF NOT EXISTS contract_agreement_id_uindex ON edc_contract_agreement (agr_id);

-- ############################### Contract Negotiations ###############################

CREATE TABLE IF NOT EXISTS edc_contract_negotiation
(
    id                   VARCHAR           NOT NULL
        CONSTRAINT contract_negotiation_pk
            PRIMARY KEY,
    created_at           BIGINT            NOT NULL,
    updated_at           BIGINT            NOT NULL,
    correlation_id       VARCHAR,
    counterparty_id      VARCHAR           NOT NULL,
    counterparty_address VARCHAR           NOT NULL,
    protocol             VARCHAR           NOT NULL,
    type                 VARCHAR           NOT NULL,
    state                INTEGER DEFAULT 0 NOT NULL,
    state_count          INTEGER DEFAULT 0,
    state_timestamp      BIGINT,
    error_detail         VARCHAR,
    agreement_id         VARCHAR
        CONSTRAINT contract_negotiation_contract_agreement_id_fk
            REFERENCES edc_contract_agreement,
    contract_offers      JSON,
    callback_addresses   JSON,
    trace_context        JSON,
    pending              BOOLEAN DEFAULT FALSE,
    protocol_messages    JSON,
    lease_id             VARCHAR
        CONSTRAINT contract_negotiation_lease_lease_id_fk
            REFERENCES edc_lease
            ON DELETE SET NULL
);

COMMENT ON COLUMN edc_contract_negotiation.agreement_id IS 'ContractAgreement serialized as JSON';

COMMENT ON COLUMN edc_contract_negotiation.contract_offers IS 'List<ContractOffer> serialized as JSON';

COMMENT ON COLUMN edc_contract_negotiation.trace_context IS 'Map<String,String> serialized as JSON';


CREATE INDEX IF NOT EXISTS contract_negotiation_correlationid_index ON edc_contract_negotiation (correlation_id);

CREATE UNIQUE INDEX IF NOT EXISTS contract_negotiation_id_uindex ON edc_contract_negotiation (id);

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS contract_negotiation_state ON edc_contract_negotiation (state,state_timestamp);

CREATE INDEX IF NOT EXISTS contract_negotiation_id ON edc_contract_negotiation(id);
CREATE INDEX IF NOT EXISTS contract_negotiation_counterparty ON edc_contract_negotiation(counterparty_id);


-- ############################### Transfer Process ###############################

CREATE TABLE IF NOT EXISTS edc_transfer_process
(
    transferprocess_id       VARCHAR           NOT NULL
        CONSTRAINT transfer_process_pk
            PRIMARY KEY,
    type                       VARCHAR           NOT NULL,
    state                      INTEGER           NOT NULL,
    state_count                INTEGER DEFAULT 0 NOT NULL,
    state_time_stamp           BIGINT,
    created_at                 BIGINT            NOT NULL,
    updated_at                 BIGINT            NOT NULL,
    trace_context              JSON,
    error_detail               VARCHAR,
    resource_manifest          JSON,
    provisioned_resource_set   JSON,
    content_data_address       JSON,
    deprovisioned_resources    JSON,
    private_properties         JSON,
    callback_addresses         JSON,
    pending                    BOOLEAN  DEFAULT FALSE,
    transfer_type              VARCHAR,
    protocol_messages          JSON,
    data_plane_id              VARCHAR,
    correlation_id             VARCHAR,
    counter_party_address      VARCHAR,
    protocol                   VARCHAR,
    asset_id                   VARCHAR,
    contract_id                VARCHAR,
    data_destination           JSON,
    lease_id                   VARCHAR
            CONSTRAINT transfer_process_lease_lease_id_fk
                REFERENCES edc_lease
                ON DELETE SET NULL
);

COMMENT ON COLUMN edc_transfer_process.trace_context IS 'Java Map serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.resource_manifest IS 'java ResourceManifest serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.provisioned_resource_set IS 'ProvisionedResourceSet serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.content_data_address IS 'DataAddress serialized as JSON';

COMMENT ON COLUMN edc_transfer_process.deprovisioned_resources IS 'List of deprovisioned resources, serialized as JSON';


CREATE UNIQUE INDEX IF NOT EXISTS transfer_process_id_uindex ON edc_transfer_process (transferprocess_id);

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS transfer_process_state ON edc_transfer_process (state,state_time_stamp);


-- ############################### Transfer Process (REQUIRED BY ISSERSERVICE) ###############################

CREATE TABLE IF NOT EXISTS edc_issuance_process
(
    id                          VARCHAR           NOT NULL PRIMARY KEY,
    state                       INTEGER           NOT NULL,
    state_count                 INTEGER DEFAULT 0 NOT NULL,
    state_time_stamp            BIGINT,
    created_at                  BIGINT            NOT NULL,
    updated_at                  BIGINT            NOT NULL,
    trace_context               JSON,
    error_detail                VARCHAR,
    pending                     BOOLEAN  DEFAULT FALSE,
    lease_id                    VARCHAR CONSTRAINT issuance_process_lease_lease_id_fk REFERENCES edc_lease ON DELETE SET NULL,
    holder_id                   VARCHAR           NOT NULL,
    participant_context_id      VARCHAR           NOT NULL,
    holder_pid                  VARCHAR           NOT NULL,
    claims                      JSON              NOT NULL,
    credential_definitions      JSONB             NOT NULL,
    credential_formats          JSONB             NOT NULL
);

-- This will help to identify states that need to be transitioned without a table scan when the entries grow
CREATE INDEX IF NOT EXISTS issuance_process_state ON edc_issuance_process (state,state_time_stamp);

-- ==========================================
-- Grant specific permissions
-- ==========================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO edc_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO edc_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO edc_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO edc_user;
