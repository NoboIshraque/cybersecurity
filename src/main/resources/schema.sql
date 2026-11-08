-- Mirage embedded database schema (H2). Loaded by the Console on startup.
-- This is the runtime copy of db/schema.sql; keep the two in sync.

-- sensor: one row per Sensor (hostname = its id), upserted on every heartbeat.
CREATE TABLE IF NOT EXISTS sensor (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostname       VARCHAR(255) NOT NULL,
    ip             VARCHAR(45)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    last_heartbeat TIMESTAMP
);

-- attacker_profile: one row per source, updated whenever it raises an alert.
CREATE TABLE IF NOT EXISTS attacker_profile (
    src_ip      VARCHAR(45) PRIMARY KEY,
    first_seen  TIMESTAMP,
    last_seen   TIMESTAMP,
    event_count INT DEFAULT 0,
    techniques  VARCHAR(512),
    severity    VARCHAR(16),
    state       VARCHAR(32)
);

-- attack_event: every event a Sensor reported, with the decoy type and the reporting Sensor.
CREATE TABLE IF NOT EXISTS attack_event (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id  BIGINT REFERENCES sensor(id),
    src_ip     VARCHAR(45),
    ts         TIMESTAMP,
    action     VARCHAR(32),
    payload    CLOB,
    decoy_type VARCHAR(32)
);

-- alert: the hash-chained, ECDSA-signed ledger. key_id names the signing key (rows without one
-- predate the saved key and are not checked); alert_ts is the exact timestamp that was hashed.
CREATE TABLE IF NOT EXISTS alert (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_ip           VARCHAR(45),
    ts               TIMESTAMP,
    classification   VARCHAR(255),
    attack_technique VARCHAR(32),
    severity         VARCHAR(16),
    prev_hash        CHAR(64),
    hash             CHAR(64),
    signature        VARCHAR(512),
    key_id           VARCHAR(32),
    alert_ts         VARCHAR(40)
);

-- honeytoken: the planted bait credentials, and when one was first used.
CREATE TABLE IF NOT EXISTS honeytoken (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_value      VARCHAR(255) NOT NULL,
    planted_location VARCHAR(512),
    used             BOOLEAN DEFAULT FALSE,
    used_ts          TIMESTAMP
);

-- block_rule: every response command sent (scope = BLOCK, TARPIT or FEED_FAKE); an UNBLOCK
-- closes the source's active rows.
CREATE TABLE IF NOT EXISTS block_rule (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_ip     VARCHAR(45) NOT NULL,
    created_ts TIMESTAMP,
    scope      VARCHAR(32),
    active     BOOLEAN DEFAULT TRUE
);

-- Upgrades for databases created before these columns existed (no-ops on a new one).
ALTER TABLE attack_event ADD COLUMN IF NOT EXISTS decoy_type VARCHAR(32);
ALTER TABLE alert ADD COLUMN IF NOT EXISTS key_id VARCHAR(32);
ALTER TABLE alert ADD COLUMN IF NOT EXISTS alert_ts VARCHAR(40);
