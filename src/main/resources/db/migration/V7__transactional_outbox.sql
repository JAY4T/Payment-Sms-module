-- V7: durable transactional outbox for transaction lifecycle events (Sprint 1, F11).
--
-- Every event that tells LigiOpen money has moved (transaction.completed / .failed / .cleared,
-- payout.completed / .failed) used to be a fire-and-forget RabbitMQ publish wrapped in a
-- try/catch. A broker outage, an undeclared exchange, or a crash between committing the status
-- change and publishing it silently dropped the event, and nothing on this side retried or even
-- recorded the loss - while on the other side that event was the only trigger that moved a
-- seller's, team's or club's money out of the clearing wallet.
--
-- Events are now written here, in the same transaction as the state change, and a relay publishes
-- them afterwards with retries. Delayed is acceptable; lost is not.
CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGSERIAL PRIMARY KEY,

    -- Stable idempotency key for consumers, unchanged across retries of the same logical event.
    -- UNIQUE so a duplicate insert (two callbacks racing on one transaction status change) fails
    -- loudly in the producing transaction instead of publishing the event twice.
    event_id        VARCHAR(120) NOT NULL UNIQUE,

    exchange_name   VARCHAR(120) NOT NULL,
    routing_key     VARCHAR(120) NOT NULL,

    -- The serialised event body, exactly as it will be published.
    payload         TEXT NOT NULL,

    status          VARCHAR(20) NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,

    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT now(),
    published_at    TIMESTAMP
);

-- The relay's only query: unpublished rows whose next attempt is due, oldest first. Partial so it
-- stays proportional to the (tiny) backlog rather than to the whole event history.
CREATE INDEX IF NOT EXISTS idx_outbox_events_due
    ON outbox_events (next_attempt_at, created_at)
    WHERE status <> 'PUBLISHED';

-- Backs the "oldest unpublished event" age check that raises the backlog alert.
CREATE INDEX IF NOT EXISTS idx_outbox_events_created_at
    ON outbox_events (created_at)
    WHERE status <> 'PUBLISHED';
