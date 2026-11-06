-- One checked piece has the same definition for included and purchased baggage.
CREATE TABLE checked_baggage_policy (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version INT UNSIGNED NOT NULL UNIQUE,
    piece_max_kg INT UNSIGNED NOT NULL CHECK (piece_max_kg > 0),
    max_linear_dimensions_cm INT UNSIGNED NOT NULL CHECK (max_linear_dimensions_cm > 0),
    max_extra_pieces INT UNSIGNED NOT NULL CHECK (max_extra_pieces <= 2),
    extra_piece_price_excl_vat BIGINT UNSIGNED NOT NULL CHECK (extra_piece_price_excl_vat > 0),
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    effective_from_utc DATETIME(6) NOT NULL,
    effective_to_utc DATETIME(6),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT ck_checked_baggage_policy_period
        CHECK (effective_to_utc IS NULL OR effective_to_utc > effective_from_utc)
) ENGINE=InnoDB;

INSERT INTO checked_baggage_policy
    (version, piece_max_kg, max_linear_dimensions_cm, max_extra_pieces,
     extra_piece_price_excl_vat, currency, effective_from_utc)
VALUES (1, 23, 158, 2, 325000, 'VND', UTC_TIMESTAMP(6));

ALTER TABLE fare_benefit_policy
    ADD COLUMN checked_baggage_policy_id BIGINT UNSIGNED NULL AFTER checked_pieces;

UPDATE fare_benefit_policy
SET checked_baggage_policy_id = (SELECT id FROM checked_baggage_policy WHERE version = 1);

ALTER TABLE fare_benefit_policy
    MODIFY COLUMN checked_baggage_policy_id BIGINT UNSIGNED NOT NULL,
    ADD CONSTRAINT fk_fare_benefit_checked_baggage_policy
        FOREIGN KEY (checked_baggage_policy_id) REFERENCES checked_baggage_policy(id),
    DROP COLUMN checked_piece_max_kg;

-- Keep historical weights and money intact. Unknown historical dimensions/policy
-- remain NULL; only new snapshots record the current common piece definition.
ALTER TABLE booking_passenger_benefit
    ADD COLUMN checked_baggage_policy_id BIGINT UNSIGNED NULL AFTER checked_pieces,
    ADD COLUMN checked_piece_max_linear_cm INT UNSIGNED NULL AFTER checked_piece_max_kg,
    ADD COLUMN extra_checked_pieces INT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN extra_unit_price_excl_vat BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN extra_baggage_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_booking_benefit_checked_baggage_policy
        FOREIGN KEY (checked_baggage_policy_id) REFERENCES checked_baggage_policy(id),
    ADD CONSTRAINT ck_booking_benefit_checked_dimensions
        CHECK (checked_piece_max_linear_cm IS NULL OR checked_piece_max_linear_cm > 0),
    ADD CONSTRAINT ck_booking_benefit_extra_quantity CHECK (extra_checked_pieces <= 2),
    ADD CONSTRAINT ck_booking_benefit_extra_amount
        CHECK (extra_baggage_amount = extra_checked_pieces * extra_unit_price_excl_vat),
    ADD CONSTRAINT ck_booking_benefit_extra_policy
        CHECK (extra_checked_pieces = 0 OR
            (checked_baggage_policy_id IS NOT NULL AND checked_piece_max_linear_cm IS NOT NULL
             AND checked_piece_max_kg = 23 AND extra_unit_price_excl_vat > 0 AND seat_type IS NOT NULL));

ALTER TABLE booking
    ADD COLUMN extra_baggage_amount BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER fare_subtotal;

-- V1's total CHECK was unnamed. Discover its MariaDB-assigned name instead of
-- assuming a generated CONSTRAINT_n number, which can differ across servers.
SET @booking_total_constraint = (
    SELECT constraint_name FROM information_schema.check_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'booking'
      AND REPLACE(REPLACE(REPLACE(check_clause, '`', ''), ' ', ''), CHAR(10), '')
          = 'total_amount=fare_subtotal+vat_amount'
);
SET @drop_booking_total = CONCAT('ALTER TABLE booking DROP CONSTRAINT `',
    REPLACE(@booking_total_constraint, '`', '``'), '`');
PREPARE drop_booking_total FROM @drop_booking_total;
EXECUTE drop_booking_total;
DEALLOCATE PREPARE drop_booking_total;

-- VAT is computed once by the backend on (fare_subtotal + extra_baggage_amount).
-- Do not recalculate tax or prices on existing orders during migration.
ALTER TABLE booking ADD CONSTRAINT ck_booking_total_amount
    CHECK (total_amount = fare_subtotal + extra_baggage_amount + vat_amount);
