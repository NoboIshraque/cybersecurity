-- Mirage embedded database schema (H2). Loaded by the Console on startup.
-- This is the runtime copy of db/schema.sql; keep the two in sync.

CREATE TABLE IF NOT EXISTS sensor (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostname       VARCHAR(255) NOT NULL,
    ip             VARCHAR(45)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    last_heartbeat TIMESTAMP
);

CREATE TABLE IF NOT EXISTS decoy (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id BIGINT REFERENCES sensor(id),
    type      VARCHAR(32) NOT NULL,
    port      INT NOT NULL,
    status    VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS attacker_profile (
    src_ip      VARCHAR(45) PRIMARY KEY,
    first_seen  TIMESTAMP,
    last_seen   TIMESTAMP,
    event_count INT DEFAULT 0,
    techniques  VARCHAR(512),
    severity    VARCHAR(16),
    state       VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS attack_event (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id BIGINT REFERENCES sensor(id),
    decoy_id  BIGINT REFERENCES decoy(id),
    src_ip    VARCHAR(45),
    ts        TIMESTAMP,
    action    VARCHAR(32),
    payload   CLOB
);

CREATE TABLE IF NOT EXISTS alert (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_ip           VARCHAR(45),
    ts               TIMESTAMP,
    classification   VARCHAR(255),
    attack_technique VARCHAR(32),
    severity         VARCHAR(16),
    prev_hash        CHAR(64),
    hash             CHAR(64)
);

CREATE TABLE IF NOT EXISTS honeytoken (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_value      VARCHAR(255) NOT NULL,
    planted_location VARCHAR(512),
    used             BOOLEAN DEFAULT FALSE,
    used_ts          TIMESTAMP
);

CREATE TABLE IF NOT EXISTS block_rule (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_ip     VARCHAR(45) NOT NULL,
    created_ts TIMESTAMP,
    scope      VARCHAR(32),
    active     BOOLEAN DEFAULT TRUE
);
