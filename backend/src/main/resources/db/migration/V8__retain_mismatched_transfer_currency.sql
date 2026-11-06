-- Retain the provider's currency for reconciliation, even when it is not VND.
-- Booking, payment and payment_attempt remain VND-only.
ALTER TABLE payment_transaction
    MODIFY COLUMN currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND'
        CHECK (currency REGEXP '^[A-Z]{3}$');
