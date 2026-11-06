-- V5 already versions booking reviews. This migration adds real PayOS payment
-- records while retaining QR_SIMULATION for historical bookings.
-- V1 declared column-level CHECKs, which MariaDB replaces when the columns
-- are modified. They cannot be removed with DROP CONSTRAINT.
ALTER TABLE payment
    MODIFY COLUMN method VARCHAR(24) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (method IN ('QR_SIMULATION', 'PAYOS')),
    MODIFY COLUMN status VARCHAR(32) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'EXPIRED', 'RECONCILIATION_REQUIRED')),
    ADD UNIQUE KEY uq_payment_id_booking (id, booking_id);

-- A booking can have several historical holds but one PayOS link per hold.
-- The composite FKs prevent an attempt from joining another booking's hold.
ALTER TABLE seat_hold ADD UNIQUE KEY uq_seat_hold_id_booking (id, booking_id);

CREATE TABLE payment_attempt (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    seat_hold_id BIGINT UNSIGNED NOT NULL,
    order_code BIGINT UNSIGNED NOT NULL,
    payment_link_id VARCHAR(64) COLLATE utf8mb4_nopad_bin,
    status VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (status IN ('CREATING', 'PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED')),
    amount BIGINT UNSIGNED NOT NULL CHECK (amount > 0),
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    expires_at_utc DATETIME(6) NOT NULL,
    checkout_url TEXT,
    qr_code TEXT,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_payment_attempt_hold (seat_hold_id),
    UNIQUE KEY uq_payment_attempt_order (order_code),
    UNIQUE KEY uq_payment_attempt_link (payment_link_id),
    KEY ix_payment_attempt_payment (payment_id, created_at_utc),
    KEY ix_payment_attempt_expiry (status, expires_at_utc),
    CONSTRAINT fk_payment_attempt_payment_booking FOREIGN KEY (payment_id, booking_id)
        REFERENCES payment (id, booking_id),
    CONSTRAINT fk_payment_attempt_hold_booking FOREIGN KEY (seat_hold_id, booking_id)
        REFERENCES seat_hold (id, booking_id)
) ENGINE=InnoDB;

-- A verified PayOS transfer is recorded even if the booking cannot be ticketed.
-- One payment link can have more than one transfer; reference deduplicates webhooks.
CREATE TABLE payment_transaction (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payment_attempt_id BIGINT UNSIGNED NOT NULL,
    provider_reference VARCHAR(128) COLLATE utf8mb4_nopad_bin NOT NULL,
    amount BIGINT UNSIGNED NOT NULL CHECK (amount > 0),
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    provider_transaction_at VARCHAR(32) COLLATE utf8mb4_nopad_bin,
    received_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_payment_transaction_reference (provider_reference),
    KEY ix_payment_transaction_attempt (payment_attempt_id, received_at_utc),
    CONSTRAINT fk_payment_transaction_attempt FOREIGN KEY (payment_attempt_id)
        REFERENCES payment_attempt (id)
) ENGINE=InnoDB;
