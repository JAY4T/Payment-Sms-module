-- V6: optimistic locking, fee-bearing audit column, and indexes for the reconciliation sweeps.
--
-- Flyway is the only schema authority for this service (spring.jpa.hibernate.ddl-auto=validate),
-- so every column and index the entities rely on has to exist here.

-- Optimistic locking. Two writers genuinely race on these rows:
--   transactions - the Intasend webhook (processCollectionCallback) and the scheduled
--                  clearing-status sweep (updatePendingClearingStatuses) can both move the same
--                  transaction out of PENDING;
--   wallets      - syncWallet, the reconciliation sweeps and the transfer paths all
--                  read-modify-write balance/available_balance.
-- Without a version the later writer silently overwrites the earlier one.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Which Intasend tarrif (BUSINESS-PAYS / CUSTOMER-PAYS) the checkout was created with, so a
-- later reconciliation can tell who was meant to absorb the gateway fee without guessing from
-- amounts. Nullable: rows created before this column existed have no recorded intent.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS fee_bearing VARCHAR(30);

-- The three reconciliation sweeps in TransactionDaoImpl all filter on a status and then on the
-- presence of a provider id, and the transactions table previously had no indexes beyond its
-- primary key and the unique transaction_ref. Each index below is matched to one predicate, with
-- created_at last so the sweep's ORDER BY created_at ASC is served by the same index instead of a
-- separate sort:
--
--   getPendingCollectionTransactions         status IN (...) AND invoice_id IS NOT NULL
--   getCollectionTransactionsPendingClearingStatus
--                                            status = 'COMPLETED' AND invoice_id IS NOT NULL
--                                            AND (clearing_status IS NULL OR <> 'AVAILABLE')
--   getPendingSendMoneyBatches               has_batch = true AND status IN (...)
--                                            AND intasend_tracking_id IS NOT NULL
CREATE INDEX IF NOT EXISTS idx_transactions_status_invoice_id
    ON transactions(status, invoice_id, created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_status_clearing_status
    ON transactions(status, clearing_status, created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_status_has_batch_tracking
    ON transactions(status, has_batch, intasend_tracking_id, created_at);

-- Supporting indexes for the admin transaction listing (filters on wallet, and orders by
-- created_at descending with optional created_at/updated_at ranges).
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_id
    ON transactions(wallet_id);

CREATE INDEX IF NOT EXISTS idx_transactions_created_at
    ON transactions(created_at);

CREATE INDEX IF NOT EXISTS idx_transactions_updated_at
    ON transactions(updated_at);