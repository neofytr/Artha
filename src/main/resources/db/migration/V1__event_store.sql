-- Event store: append-only, optimistic concurrency via unique (aggregate_id, sequence_number)
CREATE TABLE domain_events (
    event_id           UUID PRIMARY KEY,
    aggregate_id       UUID NOT NULL,
    aggregate_type     VARCHAR(128) NOT NULL,
    sequence_number    BIGINT NOT NULL,
    event_type         VARCHAR(128) NOT NULL,
    payload            TEXT NOT NULL,
    occurred_at        TIMESTAMPTZ NOT NULL,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_aggregate_sequence UNIQUE (aggregate_id, sequence_number)
);

CREATE INDEX idx_events_aggregate ON domain_events(aggregate_id, sequence_number);
CREATE INDEX idx_events_type_occurred ON domain_events(event_type, occurred_at);

-- Aggregate snapshots: optional fast-start bypass for long event streams
CREATE TABLE aggregate_snapshots (
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(128) NOT NULL,
    version         BIGINT NOT NULL,
    data            BYTEA NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, version)
);

CREATE INDEX idx_snapshots_latest ON aggregate_snapshots(aggregate_id, version DESC);

-- Transactional outbox: events written here in the same transaction as the event store,
-- then relayed to Kafka by a separate polling process. Gives at-least-once delivery
-- without distributed transactions.
CREATE TABLE event_outbox (
    outbox_id        BIGSERIAL PRIMARY KEY,
    event_id         UUID NOT NULL,
    aggregate_id     UUID NOT NULL,
    aggregate_type   VARCHAR(128) NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    payload          TEXT NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,
    topic            VARCHAR(256) NOT NULL,
    partition_key    VARCHAR(128) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    attempts         INT NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON event_outbox(status, created_at) WHERE status = 'PENDING';

-- Idempotency keys: client-supplied retry deduplication
CREATE TABLE idempotency_keys (
    key_value     VARCHAR(255) PRIMARY KEY,
    state         VARCHAR(16) NOT NULL, -- IN_PROGRESS | COMPLETED
    response      BYTEA,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);

-- Saga instance durability
CREATE TABLE saga_instances (
    saga_id              UUID PRIMARY KEY,
    definition_name      VARCHAR(128) NOT NULL,
    state                VARCHAR(32) NOT NULL,
    completed_step_index INT NOT NULL DEFAULT -1,
    context              TEXT NOT NULL,
    last_error           TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_open ON saga_instances(state, updated_at)
    WHERE state IN ('STARTED', 'RUNNING', 'COMPENSATING', 'STUCK');

-- Read model: account projection (eventually consistent)
CREATE TABLE account_read_model (
    account_id     UUID PRIMARY KEY,
    owner_id       VARCHAR(128) NOT NULL,
    currency       CHAR(3) NOT NULL,
    balance        NUMERIC(32, 8) NOT NULL,
    available_balance NUMERIC(32, 8) NOT NULL,
    reserved_total NUMERIC(32, 8) NOT NULL DEFAULT 0,
    status         VARCHAR(16) NOT NULL,
    version        BIGINT NOT NULL,   -- matches aggregate version at projection time
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_owner ON account_read_model(owner_id);

-- Read model: transaction history projection
CREATE TABLE transaction_history (
    event_id       UUID PRIMARY KEY,
    account_id     UUID NOT NULL,
    kind           VARCHAR(32) NOT NULL, -- DEPOSIT | WITHDRAWAL | RESERVATION_COMMIT | ...
    amount         NUMERIC(32, 8) NOT NULL,
    currency       CHAR(3) NOT NULL,
    reference      VARCHAR(256),
    occurred_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_txn_account_time ON transaction_history(account_id, occurred_at DESC);
