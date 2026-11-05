-- SkyGo initial schema. Database connection must use utf8mb4; all timestamps are UTC.
SET NAMES utf8mb4;

CREATE TABLE app_user (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    phone_normalized VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    email VARCHAR(254),
    password_hash VARCHAR(255) COLLATE utf8mb4_nopad_bin NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6))
) ENGINE=InnoDB;

CREATE TABLE auth_session (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash BINARY(32) NOT NULL UNIQUE,
    expires_at_utc DATETIME(6) NOT NULL,
    revoked_at_utc DATETIME(6),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_session_owner (id, user_id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB;

CREATE TABLE login_guard (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    phone_normalized VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    failure_count INT UNSIGNED NOT NULL DEFAULT 0,
    failure_at_1_utc DATETIME(6),
    failure_at_2_utc DATETIME(6),
    failure_at_3_utc DATETIME(6),
    failure_at_4_utc DATETIME(6),
    failure_at_5_utc DATETIME(6),
    locked_until_utc DATETIME(6),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CHECK (failure_count =
           (failure_at_1_utc IS NOT NULL) + (failure_at_2_utc IS NOT NULL) +
           (failure_at_3_utc IS NOT NULL) + (failure_at_4_utc IS NOT NULL) +
           (failure_at_5_utc IS NOT NULL)),
    CHECK (failure_at_2_utc IS NULL OR (failure_at_1_utc IS NOT NULL AND failure_at_1_utc <= failure_at_2_utc)),
    CHECK (failure_at_3_utc IS NULL OR (failure_at_2_utc IS NOT NULL AND failure_at_2_utc <= failure_at_3_utc)),
    CHECK (failure_at_4_utc IS NULL OR (failure_at_3_utc IS NOT NULL AND failure_at_3_utc <= failure_at_4_utc)),
    CHECK (failure_at_5_utc IS NULL OR (failure_at_4_utc IS NOT NULL AND failure_at_4_utc <= failure_at_5_utc)),
    CHECK ((failure_count = 5) = (locked_until_utc IS NOT NULL)),
    CHECK (locked_until_utc IS NULL OR locked_until_utc = failure_at_5_utc + INTERVAL 15 MINUTE)
) ENGINE=InnoDB;

CREATE TABLE airport (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code CHAR(3) COLLATE utf8mb4_bin NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    city VARCHAR(120) NOT NULL,
    country_code CHAR(2) COLLATE utf8mb4_bin NOT NULL,
    country_name VARCHAR(120) NOT NULL,
    region_code VARCHAR(32) COLLATE utf8mb4_nopad_bin NOT NULL,
    region_name VARCHAR(120) NOT NULL,
    region_sort_order INT UNSIGNED NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    KEY ix_airport_region (region_code, is_active, code),
    KEY ix_airport_country (country_code)
) ENGINE=InnoDB;

CREATE TABLE air_route (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    origin_airport_id BIGINT UNSIGNED NOT NULL,
    destination_airport_id BIGINT UNSIGNED NOT NULL,
    air_distance_km INT UNSIGNED NOT NULL CHECK (air_distance_km > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0,1)),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_route_airports (origin_airport_id, destination_airport_id),
    FOREIGN KEY (origin_airport_id) REFERENCES airport(id),
    FOREIGN KEY (destination_airport_id) REFERENCES airport(id),
    CHECK (origin_airport_id <> destination_airport_id)
) ENGINE=InnoDB;

CREATE TABLE pricing_policy (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version INT UNSIGNED NOT NULL UNIQUE,
    economy_fixed_vnd BIGINT UNSIGNED NOT NULL,
    economy_per_km_vnd BIGINT UNSIGNED NOT NULL,
    business_multiplier DECIMAL(7,6) NOT NULL CHECK (business_multiplier > 0),
    vat_rate DECIMAL(7,6) NOT NULL CHECK (vat_rate BETWEEN 0 AND 1),
    effective_from_utc DATETIME(6) NOT NULL,
    effective_to_utc DATETIME(6),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CHECK (effective_to_utc IS NULL OR effective_to_utc > effective_from_utc)
) ENGINE=InnoDB;

CREATE TABLE flight (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    flight_number VARCHAR(20) COLLATE utf8mb4_nopad_bin NOT NULL,
    route_id BIGINT UNSIGNED NOT NULL,
    departure_at_utc DATETIME(6) NOT NULL,
    arrival_at_utc DATETIME(6) NOT NULL,
    sale_status VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (sale_status IN ('ON_SALE', 'CLOSED')),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    KEY ix_flight_search (route_id, departure_at_utc, sale_status, id),
    CONSTRAINT fk_flight_route FOREIGN KEY (route_id) REFERENCES air_route(id),
    CHECK (arrival_at_utc > departure_at_utc)
) ENGINE=InnoDB;

CREATE TABLE flight_fare (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    flight_id BIGINT UNSIGNED NOT NULL,
    pricing_policy_id BIGINT UNSIGNED NOT NULL,
    priced_distance_km INT UNSIGNED NOT NULL CHECK (priced_distance_km > 0),
    cabin_class VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (cabin_class IN ('ECONOMY', 'BUSINESS')),
    adult_base_fare BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    capacity INT UNSIGNED NOT NULL,
    available_seats INT UNSIGNED NOT NULL,
    is_on_sale BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_on_sale IN (0, 1)),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_flight_cabin (flight_id, cabin_class),
    FOREIGN KEY (pricing_policy_id) REFERENCES pricing_policy(id),
    CONSTRAINT fk_fare_flight FOREIGN KEY (flight_id) REFERENCES flight (id),
    CHECK (available_seats <= capacity)
) ENGINE=InnoDB;

CREATE TABLE passenger_fare_rule (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_scope VARCHAR(32) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (route_scope IN ('VN_DOMESTIC', 'OTHER_DOMESTIC', 'INTERNATIONAL')),
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT')),
    multiplier DECIMAL(7,6) NOT NULL CHECK (multiplier >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_active IN (0, 1)),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_passenger_fare_rule (route_scope, passenger_type)
) ENGINE=InnoDB;

CREATE TABLE fare_benefit_policy (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    flight_fare_id BIGINT UNSIGNED NOT NULL,
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT')),
    carry_on_max_kg INT UNSIGNED NOT NULL,
    checked_pieces INT UNSIGNED NOT NULL,
    checked_piece_max_kg INT UNSIGNED NOT NULL,
    included_meals_per_leg INT UNSIGNED NOT NULL,
    seat_type VARCHAR(16) COLLATE utf8mb4_nopad_bin CHECK (seat_type IN ('STANDARD','BUSINESS')),
    stroller_pieces INT UNSIGNED NOT NULL,
    stroller_max_kg INT UNSIGNED NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_fare_benefit_policy (flight_fare_id, passenger_type),
    CONSTRAINT fk_fare_benefit_policy_fare FOREIGN KEY (flight_fare_id) REFERENCES flight_fare (id),
    CHECK ((passenger_type = 'INFANT' AND seat_type IS NULL AND included_meals_per_leg = 0) OR (passenger_type <> 'INFANT' AND seat_type IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE seat_hold (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    booking_id BIGINT UNSIGNED,
    trip_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (trip_type IN ('ONE_WAY', 'ROUND_TRIP')),
    adults INT UNSIGNED NOT NULL CHECK (adults >= 1),
    children INT UNSIGNED NOT NULL,
    infants INT UNSIGNED NOT NULL,
    status VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED')),
    started_at_utc DATETIME(6) NOT NULL,
    expires_at_utc DATETIME(6) NOT NULL,
    released_at_utc DATETIME(6),
    active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN user_id ELSE NULL END) PERSISTENT,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_seat_hold_owner (id, user_id),
    UNIQUE KEY uq_seat_hold_active_user (active_user_id),
    KEY ix_seat_hold_expiry (status, expires_at_utc, id),
    KEY ix_seat_hold_booking (booking_id, status, expires_at_utc),
    CONSTRAINT fk_seat_hold_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CHECK (expires_at_utc = started_at_utc + INTERVAL 15 MINUTE)
) ENGINE=InnoDB;

CREATE TABLE seat_hold_leg (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    seat_hold_id BIGINT UNSIGNED NOT NULL,
    direction VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (direction IN ('OUTBOUND', 'RETURN')),
    flight_fare_id BIGINT UNSIGNED NOT NULL,
    reserved_seats INT UNSIGNED NOT NULL CHECK (reserved_seats >= 1),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_seat_hold_direction (seat_hold_id, direction),
    CONSTRAINT fk_seat_hold_leg_hold FOREIGN KEY (seat_hold_id) REFERENCES seat_hold (id),
    CONSTRAINT fk_seat_hold_leg_fare FOREIGN KEY (flight_fare_id) REFERENCES flight_fare (id)
) ENGINE=InnoDB;

CREATE TABLE booking_review (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    seat_hold_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    snapshot_ciphertext MEDIUMBLOB,
    snapshot_schema_version SMALLINT UNSIGNED NOT NULL CHECK (snapshot_schema_version >= 1),
    expires_at_utc DATETIME(6) NOT NULL,
    used_at_utc DATETIME(6),
    invalidated_at_utc DATETIME(6),
    snapshot_purged_at_utc DATETIME(6),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_review_owner_hold (id, user_id, seat_hold_id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_review_session_owner FOREIGN KEY (session_id, user_id) REFERENCES auth_session (id, user_id),
    CONSTRAINT fk_review_hold_owner FOREIGN KEY (seat_hold_id, user_id) REFERENCES seat_hold (id, user_id),
    CHECK ((snapshot_ciphertext IS NULL) = (snapshot_purged_at_utc IS NOT NULL)),
    CHECK (snapshot_purged_at_utc IS NULL OR used_at_utc <= snapshot_purged_at_utc AND used_at_utc IS NOT NULL
        OR invalidated_at_utc <= snapshot_purged_at_utc AND invalidated_at_utc IS NOT NULL
        OR expires_at_utc <= snapshot_purged_at_utc)
) ENGINE=InnoDB;

CREATE TABLE booking (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    booking_code VARCHAR(32) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    review_id BIGINT UNSIGNED NOT NULL UNIQUE,
    seat_hold_id BIGINT UNSIGNED NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) COLLATE utf8mb4_nopad_bin NOT NULL,
    request_hash BINARY(32) NOT NULL,
    trip_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (trip_type IN ('ONE_WAY', 'ROUND_TRIP')),
    adults INT UNSIGNED NOT NULL CHECK (adults >= 1),
    children INT UNSIGNED NOT NULL,
    infants INT UNSIGNED NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL,
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    fare_subtotal BIGINT UNSIGNED NOT NULL,
    vat_rate DECIMAL(7,6) NOT NULL CHECK (vat_rate BETWEEN 0 AND 1),
    vat_amount BIGINT UNSIGNED NOT NULL,
    total_amount BIGINT UNSIGNED NOT NULL,
    booking_status VARCHAR(24) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (booking_status IN ('PENDING_PAYMENT', 'PAID', 'EXPIRED')),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_owner (id, user_id),
    UNIQUE KEY uq_booking_idempotency (user_id, idempotency_key),
    KEY ix_booking_history (user_id, created_at_utc, public_id),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_booking_initial_hold FOREIGN KEY (seat_hold_id) REFERENCES seat_hold (id),
    CONSTRAINT fk_booking_review_owner_hold FOREIGN KEY (review_id, user_id, seat_hold_id)
        REFERENCES booking_review (id, user_id, seat_hold_id),
    CHECK (total_amount = fare_subtotal + vat_amount)
) ENGINE=InnoDB;

-- Thêm chiều tham chiếu ngược sau khi booking đã được tạo.
ALTER TABLE seat_hold ADD CONSTRAINT fk_seat_hold_booking_owner
    FOREIGN KEY (booking_id, user_id) REFERENCES booking (id, user_id);

CREATE TABLE booking_leg (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT UNSIGNED NOT NULL,
    direction VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (direction IN ('OUTBOUND', 'RETURN')),
    flight_fare_id BIGINT UNSIGNED NOT NULL,
    flight_public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL,
    flight_number VARCHAR(20) COLLATE utf8mb4_nopad_bin NOT NULL,
    airline_code VARCHAR(10) COLLATE utf8mb4_nopad_bin NOT NULL,
    airline_name VARCHAR(200) NOT NULL,
    cabin_class VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (cabin_class IN ('ECONOMY', 'BUSINESS')),
    origin_code CHAR(3) COLLATE utf8mb4_bin NOT NULL,
    origin_name VARCHAR(200) NOT NULL,
    origin_city VARCHAR(120) NOT NULL,
    origin_country_code CHAR(2) COLLATE utf8mb4_bin NOT NULL,
    origin_time_zone VARCHAR(64) NOT NULL,
    destination_code CHAR(3) COLLATE utf8mb4_bin NOT NULL,
    destination_name VARCHAR(200) NOT NULL,
    destination_city VARCHAR(120) NOT NULL,
    destination_country_code CHAR(2) COLLATE utf8mb4_bin NOT NULL,
    destination_time_zone VARCHAR(64) NOT NULL,
    departure_at_utc DATETIME(6) NOT NULL,
    arrival_at_utc DATETIME(6) NOT NULL,
    duration_minutes INT UNSIGNED NOT NULL,
    route_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (route_type IN ('DOMESTIC', 'INTERNATIONAL')),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_leg_owner (id, booking_id),
    UNIQUE KEY uq_booking_direction (booking_id, direction),
    CONSTRAINT fk_booking_leg_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_booking_leg_fare FOREIGN KEY (flight_fare_id) REFERENCES flight_fare (id),
    CHECK (origin_code <> destination_code AND arrival_at_utc > departure_at_utc),
    CHECK ((route_type = 'DOMESTIC') = (origin_country_code = destination_country_code))
) ENGINE=InnoDB;

CREATE TABLE booking_passenger (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    booking_id BIGINT UNSIGNED NOT NULL,
    client_passenger_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL,
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT')),
    title VARCHAR(8) COLLATE utf8mb4_nopad_bin,
    given_names VARCHAR(200) NOT NULL,
    surname VARCHAR(200) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL,
    document_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (document_type IN ('PERSONAL_ID', 'PASSPORT')),
    document_number_ciphertext VARBINARY(1024) NOT NULL,
    document_number_last4 VARCHAR(4),
    issuing_country_code CHAR(2) COLLATE utf8mb4_bin NOT NULL,
    nationality_country_code CHAR(2) COLLATE utf8mb4_bin,
    document_expires_on DATE,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_passenger_owner (id, booking_id),
    UNIQUE KEY uq_booking_client_passenger (booking_id, client_passenger_id),
    CONSTRAINT fk_booking_passenger_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CHECK ((passenger_type = 'ADULT') = (title IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE booking_fare_line (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT UNSIGNED NOT NULL,
    booking_leg_id BIGINT UNSIGNED NOT NULL,
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT')),
    quantity INT UNSIGNED NOT NULL,
    unit_base_fare BIGINT UNSIGNED NOT NULL,
    fare_subtotal BIGINT UNSIGNED NOT NULL,

    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_fare_line (booking_leg_id, passenger_type),
    CONSTRAINT fk_fare_line_leg_owner FOREIGN KEY (booking_leg_id, booking_id) REFERENCES booking_leg (id, booking_id),
    CHECK (fare_subtotal = unit_base_fare * quantity)
) ENGINE=InnoDB;

CREATE TABLE booking_passenger_benefit (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT UNSIGNED NOT NULL,
    booking_passenger_id BIGINT UNSIGNED NOT NULL,
    booking_leg_id BIGINT UNSIGNED NOT NULL,
    carry_on_max_kg INT UNSIGNED NOT NULL,
    checked_pieces INT UNSIGNED NOT NULL,
    checked_piece_max_kg INT UNSIGNED NOT NULL,
    included_meals_per_leg INT UNSIGNED NOT NULL,
    seat_type VARCHAR(16) COLLATE utf8mb4_nopad_bin CHECK (seat_type IN ('STANDARD','BUSINESS')),
    stroller_pieces INT UNSIGNED NOT NULL,
    stroller_max_kg INT UNSIGNED NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_passenger_benefit (booking_passenger_id, booking_leg_id),
    CONSTRAINT fk_booking_passenger_benefit_passenger_owner FOREIGN KEY (booking_passenger_id, booking_id)
        REFERENCES booking_passenger (id, booking_id),
    CONSTRAINT fk_booking_passenger_benefit_leg_owner FOREIGN KEY (booking_leg_id, booking_id) REFERENCES booking_leg (id, booking_id),
    CHECK (checked_pieces = 0 OR checked_piece_max_kg = 23),
    CHECK (stroller_pieces = 0 OR stroller_max_kg = 15)
) ENGINE=InnoDB;

CREATE TABLE payment (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    booking_id BIGINT UNSIGNED NOT NULL UNIQUE,
    method VARCHAR(24) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (method = 'QR_SIMULATION'),
    status VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'EXPIRED')),
    amount BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    transaction_code VARCHAR(64) COLLATE utf8mb4_nopad_bin UNIQUE,
    paid_at_utc DATETIME(6),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CHECK ((status = 'SUCCEEDED') = (paid_at_utc IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE ticket (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_number VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    booking_id BIGINT UNSIGNED NOT NULL,
    booking_passenger_id BIGINT UNSIGNED NOT NULL UNIQUE,
    issued_at_utc DATETIME(6) NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_ticket_passenger_owner FOREIGN KEY (booking_passenger_id, booking_id)
        REFERENCES booking_passenger (id, booking_id)
) ENGINE=InnoDB;

CREATE TABLE invoice (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    booking_id BIGINT UNSIGNED NOT NULL UNIQUE,
    issued_at_utc DATETIME(6) NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_invoice_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
) ENGINE=InnoDB;

