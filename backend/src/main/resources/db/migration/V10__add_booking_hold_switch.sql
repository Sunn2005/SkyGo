-- Keep all existing bookings/holds/attempts and their original timestamps.
ALTER TABLE seat_hold
    MODIFY COLUMN status VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (status IN ('ACTIVE','CONSUMED','EXPIRED','RELEASED'));

ALTER TABLE booking
    MODIFY COLUMN booking_status VARCHAR(24) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (booking_status IN ('PENDING_PAYMENT','PAID','EXPIRED','AWAITING_REHOLD','CANCELLED')),
    ADD COLUMN cancelled_at_utc DATETIME(6),
    ADD COLUMN cancellation_reason VARCHAR(64) COLLATE utf8mb4_nopad_bin;

-- NULL retains the previous rule: booking closes at departure.
ALTER TABLE flight
    ADD COLUMN booking_closes_at_utc DATETIME(6),
    ADD CONSTRAINT ck_flight_booking_closes CHECK
        (booking_closes_at_utc IS NULL OR booking_closes_at_utc <= departure_at_utc);

ALTER TABLE payment_attempt
    ADD UNIQUE KEY uq_attempt_booking_hold (id,booking_id,seat_hold_id);

CREATE TABLE booking_hold_switch (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED NOT NULL,
    source_hold_id BIGINT UNSIGNED NOT NULL,
    expected_hold_version BIGINT NOT NULL CHECK (expected_hold_version >= 1),
    payment_attempt_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) COLLATE utf8mb4_nopad_bin NOT NULL,
    request_hash BINARY(32) NOT NULL,
    requested_selection JSON NOT NULL,
    target_hold_id BIGINT UNSIGNED,
    status VARCHAR(32) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (status IN ('VERIFYING_PAYMENT','QR_CLOSED','COMPLETED','FAILED','RECONCILIATION_REQUIRED')),
    error_code VARCHAR(64) COLLATE utf8mb4_nopad_bin,
    active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN status IN ('VERIFYING_PAYMENT','QR_CLOSED') THEN user_id ELSE NULL END) PERSISTENT,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    completed_at_utc DATETIME(6),
    UNIQUE KEY uq_hold_switch_request (user_id,idempotency_key),
    UNIQUE KEY uq_hold_switch_active_user (active_user_id),
    KEY ix_hold_switch_recovery (status,updated_at_utc,id),
    CONSTRAINT fk_switch_booking_owner FOREIGN KEY (booking_id,user_id) REFERENCES booking(id,user_id),
    CONSTRAINT fk_switch_source_owner FOREIGN KEY (source_hold_id,user_id) REFERENCES seat_hold(id,user_id),
    CONSTRAINT fk_switch_source_booking FOREIGN KEY (source_hold_id,booking_id) REFERENCES seat_hold(id,booking_id),
    CONSTRAINT fk_switch_attempt FOREIGN KEY (payment_attempt_id,booking_id,source_hold_id)
        REFERENCES payment_attempt(id,booking_id,seat_hold_id),
    CONSTRAINT fk_switch_target_owner FOREIGN KEY (target_hold_id,user_id) REFERENCES seat_hold(id,user_id),
    CONSTRAINT ck_switch_target CHECK ((status='COMPLETED') = (target_hold_id IS NOT NULL))
) ENGINE=InnoDB;
