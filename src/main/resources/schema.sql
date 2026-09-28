-- DuelCore database schema (MySQL 8+)

CREATE TABLE IF NOT EXISTS dc_players (
    uuid            CHAR(36)      NOT NULL PRIMARY KEY,
    username        VARCHAR(16)   NOT NULL,
    currency        BIGINT        NOT NULL DEFAULT 0,
    overall_points  DOUBLE        NOT NULL DEFAULT 0,
    country_code    VARCHAR(2)    DEFAULT NULL,
    client_brand    VARCHAR(32)   DEFAULT NULL,
    last_seen       BIGINT        NOT NULL DEFAULT 0,
    INDEX idx_username (username)
);

-- Per-kit tier progress / points, drives the leaderboard filters
-- (Top 10 Overall / Top 10 <kit>) shown in-game.
CREATE TABLE IF NOT EXISTS dc_kit_stats (
    uuid        CHAR(36)     NOT NULL,
    kit_id      VARCHAR(32)  NOT NULL,
    tier        VARCHAR(8)   NOT NULL DEFAULT 'NONE',
    points      DOUBLE       NOT NULL DEFAULT 0,
    wins        INT          NOT NULL DEFAULT 0,
    losses      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, kit_id),
    INDEX idx_kit_points (kit_id, points)
);

-- Personal kit loadouts, saved as serialized item data (base64) per slot
CREATE TABLE IF NOT EXISTS dc_kit_loadouts (
    uuid        CHAR(36)     NOT NULL,
    kit_id      VARCHAR(32)  NOT NULL,
    slot        INT          NOT NULL,
    item_data   TEXT         NOT NULL,
    PRIMARY KEY (uuid, kit_id, slot)
);

-- Unlocked armor trim materials/patterns per player, tied to tier rank-ups
CREATE TABLE IF NOT EXISTS dc_trim_unlocks (
    uuid        CHAR(36)     NOT NULL,
    trim_id     VARCHAR(32)  NOT NULL,
    unlocked_at BIGINT       NOT NULL,
    PRIMARY KEY (uuid, trim_id)
);

-- Parties: one row per active party, members stored as a JSON array of UUIDs
CREATE TABLE IF NOT EXISTS dc_parties (
    party_id    CHAR(36)     NOT NULL PRIMARY KEY,
    leader_uuid CHAR(36)     NOT NULL,
    password    VARCHAR(64)  DEFAULT NULL,
    members     TEXT         NOT NULL,
    server_id   VARCHAR(64)  NOT NULL,
    created_at  BIGINT       NOT NULL
);

-- Discord account links
CREATE TABLE IF NOT EXISTS dc_discord_links (
    uuid        CHAR(36)     NOT NULL PRIMARY KEY,
    discord_id  VARCHAR(32)  NOT NULL UNIQUE,
    linked_at   BIGINT       NOT NULL
);

-- Pending /verify codes, single-use, expire after config'd minutes
CREATE TABLE IF NOT EXISTS dc_verify_codes (
    code        VARCHAR(8)   NOT NULL PRIMARY KEY,
    uuid        CHAR(36)     NOT NULL,
    created_at  BIGINT       NOT NULL,
    expires_at  BIGINT       NOT NULL
);
