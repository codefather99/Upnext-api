-- =====================================================================
-- V1__upnext_core.sql            OWNER: Deji (Data and rules)
-- Rules protected HERE:
--   vote PRIMARY KEY (request_id, user_id)  -> one vote per user per request, even for two taps at once
--   request.version + conditional UPDATE     -> two admins moving one card: one wins, one sees the true column
-- user_id / author_id carry no FK to app_user (Henry's table, V100) so the two migration streams stay independent.
-- =====================================================================

CREATE TABLE request (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    title         varchar(160) NOT NULL CHECK (char_length(title) >= 3),
    description   text         NOT NULL CHECK (char_length(description) BETWEEN 10 AND 4000),
    tags          text[]       NOT NULL DEFAULT '{}' CHECK (cardinality(tags) <= 5),
    status        varchar(20)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','PLANNED','IN_PROGRESS','COMPLETED')),
    author_id     uuid         NOT NULL,
    author_name   varchar(80)  NOT NULL,                 -- snapshot at submit time
    duplicate_of  uuid         REFERENCES request(id),   -- reserved for the post-Monday merge rule (ADR-U3)
    version       integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

-- Board, roadmap and browse all read "visible requests by status, newest first".
CREATE INDEX ix_request_status_created ON request (status, created_at DESC) WHERE duplicate_of IS NULL;
CREATE INDEX ix_request_tags ON request USING gin (tags);

-- RULE: one vote per user per request.
CREATE TABLE vote (
    request_id uuid        NOT NULL REFERENCES request(id),
    user_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, user_id)
);
CREATE INDEX ix_vote_user ON vote (user_id);           -- "which requests have I voted for"

-- Append-only: who moved what, and when.
CREATE TABLE request_status_history (
    id              bigserial    PRIMARY KEY,
    request_id      uuid         NOT NULL REFERENCES request(id),
    from_status     varchar(20),
    to_status       varchar(20)  NOT NULL,
    changed_by_id   uuid         NOT NULL,
    changed_by_name varchar(80)  NOT NULL,
    request_id_hdr  varchar(64),
    changed_at      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_history_request ON request_status_history (request_id, changed_at);
