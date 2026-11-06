-- A signed transfer may require manual reconciliation without issuing tickets.
ALTER TABLE payment_transaction
    ADD COLUMN payment_link_id VARCHAR(64) COLLATE utf8mb4_nopad_bin,
    ADD COLUMN handling_status VARCHAR(24) COLLATE utf8mb4_nopad_bin NOT NULL DEFAULT 'PENDING'
        CHECK (handling_status IN ('PENDING', 'APPLIED', 'RECONCILIATION_REQUIRED')),
    ADD COLUMN handling_reason VARCHAR(64) COLLATE utf8mb4_nopad_bin;
