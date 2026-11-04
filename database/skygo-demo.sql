-- SkyGo | MariaDB 11.8 | UTF-8
-- Chạy một lần trên máy thử nghiệm, dừng ngay khi gặp lỗi.
-- Schema skygo_demo phải chưa tồn tại. Dữ liệu danh mục bên dưới chỉ để demo.
CREATE DATABASE skygo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE skygo;
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

CREATE TABLE flight (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    flight_number VARCHAR(20) COLLATE utf8mb4_nopad_bin NOT NULL,
    origin_airport_id BIGINT UNSIGNED NOT NULL,
    destination_airport_id BIGINT UNSIGNED NOT NULL,
    departure_at_utc DATETIME(6) NOT NULL,
    arrival_at_utc DATETIME(6) NOT NULL,
    sale_status VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (sale_status IN ('ON_SALE', 'CLOSED')),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    KEY ix_flight_search (origin_airport_id, destination_airport_id, departure_at_utc, sale_status, id),
    CONSTRAINT fk_flight_origin FOREIGN KEY (origin_airport_id) REFERENCES airport (id),
    CONSTRAINT fk_flight_destination FOREIGN KEY (destination_airport_id) REFERENCES airport (id),
    CHECK (origin_airport_id <> destination_airport_id),
    CHECK (arrival_at_utc > departure_at_utc)
) ENGINE=InnoDB;

CREATE TABLE flight_fare (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    flight_id BIGINT UNSIGNED NOT NULL,
    cabin_class VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (cabin_class IN ('ECONOMY', 'BUSINESS')),
    adult_base_fare BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    capacity INT UNSIGNED NOT NULL,
    available_seats INT UNSIGNED NOT NULL,
    is_on_sale BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_on_sale IN (0, 1)),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_flight_cabin (flight_id, cabin_class),
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

CREATE TABLE baggage_policy (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    flight_fare_id BIGINT UNSIGNED NOT NULL,
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT')),
    included_pieces INT UNSIGNED NOT NULL,
    can_purchase BOOLEAN NOT NULL CHECK (can_purchase IN (0, 1)),
    max_additional_pieces INT UNSIGNED NOT NULL,
    price_per_piece BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_baggage_policy (flight_fare_id, passenger_type),
    CONSTRAINT fk_baggage_policy_fare FOREIGN KEY (flight_fare_id) REFERENCES flight_fare (id),
    CHECK (can_purchase = 1 OR max_additional_pieces = 0)
) ENGINE=InnoDB;

CREATE TABLE fee_type (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6))
) ENGINE=InnoDB;

CREATE TABLE fare_fee_configuration (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    flight_fare_id BIGINT UNSIGNED NOT NULL,
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT')),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_fare_fee_configuration (flight_fare_id, passenger_type),
    CONSTRAINT fk_fee_configuration_fare FOREIGN KEY (flight_fare_id) REFERENCES flight_fare (id)
) ENGINE=InnoDB;

CREATE TABLE fare_fee (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    flight_fare_id BIGINT UNSIGNED NOT NULL,
    passenger_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL,
    fee_type_id BIGINT UNSIGNED NOT NULL,
    unit_amount BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_fare_fee (flight_fare_id, passenger_type, fee_type_id),
    CONSTRAINT fk_fare_fee_configuration FOREIGN KEY (flight_fare_id, passenger_type)
        REFERENCES fare_fee_configuration (flight_fare_id, passenger_type),
    CONSTRAINT fk_fare_fee_type FOREIGN KEY (fee_type_id) REFERENCES fee_type (id)
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
    fees_subtotal BIGINT UNSIGNED NOT NULL,
    baggage_subtotal BIGINT UNSIGNED NOT NULL,
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
    CHECK (total_amount = fare_subtotal + fees_subtotal + baggage_subtotal)
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
    fees_subtotal BIGINT UNSIGNED NOT NULL,
    line_total BIGINT UNSIGNED NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_fare_line (booking_leg_id, passenger_type),
    CONSTRAINT fk_fare_line_leg_owner FOREIGN KEY (booking_leg_id, booking_id) REFERENCES booking_leg (id, booking_id),
    CHECK (fare_subtotal = unit_base_fare * quantity),
    CHECK (line_total = fare_subtotal + fees_subtotal)
) ENGINE=InnoDB;

CREATE TABLE booking_fee_line (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_fare_line_id BIGINT UNSIGNED NOT NULL,
    fee_code VARCHAR(40) COLLATE utf8mb4_nopad_bin NOT NULL,
    fee_name VARCHAR(200) NOT NULL,
    unit_amount BIGINT UNSIGNED NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_fee_line (booking_fare_line_id, fee_code),
    CONSTRAINT fk_booking_fee_fare_line FOREIGN KEY (booking_fare_line_id) REFERENCES booking_fare_line (id)
) ENGINE=InnoDB;

CREATE TABLE booking_baggage (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT UNSIGNED NOT NULL,
    booking_passenger_id BIGINT UNSIGNED NOT NULL,
    booking_leg_id BIGINT UNSIGNED NOT NULL,
    included_pieces INT UNSIGNED NOT NULL,
    can_purchase BOOLEAN NOT NULL CHECK (can_purchase IN (0, 1)),
    max_additional_pieces INT UNSIGNED NOT NULL,
    additional_pieces INT UNSIGNED NOT NULL,
    price_per_piece BIGINT UNSIGNED NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_booking_baggage (booking_passenger_id, booking_leg_id),
    CONSTRAINT fk_booking_baggage_passenger_owner FOREIGN KEY (booking_passenger_id, booking_id)
        REFERENCES booking_passenger (id, booking_id),
    CONSTRAINT fk_booking_baggage_leg_owner FOREIGN KEY (booking_leg_id, booking_id) REFERENCES booking_leg (id, booking_id),
    CHECK (additional_pieces <= max_additional_pieces),
    CHECK (can_purchase = 1 OR max_additional_pieces = 0),
    CHECK (amount = additional_pieces * price_per_piece)
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

-- Giá, phí, sức chứa và hành lý bên dưới là dữ liệu mẫu cho người lớn/trẻ em.
-- Chưa nạp policy hành lý hoặc phí em bé vì chưa có cấu hình được duyệt.
-- Backend tự cập nhật updated_at_utc khi ghi và xử lý transaction giữ/trả chỗ.
START TRANSACTION;

INSERT INTO airport (code, name, city, country_code, country_name, region_code, region_name, region_sort_order, time_zone)
VALUES
    ('HAN', 'Sân bay quốc tế Nội Bài', 'Hà Nội', 'VN', 'Việt Nam', 'VN', 'Việt Nam', 10, 'Asia/Ho_Chi_Minh'),
    ('SGN', 'Sân bay quốc tế Tân Sơn Nhất', 'TP. Hồ Chí Minh', 'VN', 'Việt Nam', 'VN', 'Việt Nam', 10, 'Asia/Ho_Chi_Minh'),
    ('SIN', 'Sân bay Changi', 'Singapore', 'SG', 'Singapore', 'SEA', 'Đông Nam Á', 20, 'Asia/Singapore'),
    ('BKK', 'Sân bay Suvarnabhumi', 'Bangkok', 'TH', 'Thái Lan', 'SEA', 'Đông Nam Á', 20, 'Asia/Bangkok'),
    ('NRT', 'Sân bay quốc tế Narita', 'Tokyo', 'JP', 'Nhật Bản', 'EAST_ASIA', 'Đông Á', 30, 'Asia/Tokyo');

-- Lịch mẫu bắt đầu từ ngày UTC tiếp theo; các giờ bên dưới đều là UTC.
SET @first_demo_day = UTC_DATE() + INTERVAL 1 DAY;

INSERT INTO flight (public_id, flight_number, origin_airport_id, destination_airport_id,
                    departure_at_utc, arrival_at_utc, sale_status)
SELECT UUID(), routes.flight_number, origin.id, destination.id,
       TIMESTAMP(DATE_ADD(@first_demo_day, INTERVAL days.day_offset DAY), routes.departure_time),
       DATE_ADD(TIMESTAMP(DATE_ADD(@first_demo_day, INTERVAL days.day_offset DAY), routes.departure_time),
                INTERVAL routes.duration_minutes MINUTE),
       'ON_SALE'
FROM (
    SELECT 'SG101' AS flight_number, 'HAN' AS origin_code, 'SGN' AS destination_code, '01:00:00' AS departure_time, 125 AS duration_minutes
    UNION ALL SELECT 'SG102', 'SGN', 'HAN', '07:00:00', 125
    UNION ALL SELECT 'SG103', 'HAN', 'SGN', '10:00:00', 125
    UNION ALL SELECT 'SG104', 'SGN', 'HAN', '13:00:00', 125
    UNION ALL SELECT 'SG201', 'HAN', 'SIN', '02:00:00', 205
    UNION ALL SELECT 'SG202', 'SIN', 'HAN', '08:00:00', 205
    UNION ALL SELECT 'SG301', 'HAN', 'BKK', '03:00:00', 120
    UNION ALL SELECT 'SG302', 'BKK', 'HAN', '09:00:00', 120
    UNION ALL SELECT 'SG401', 'HAN', 'NRT', '00:00:00', 300
    UNION ALL SELECT 'SG402', 'NRT', 'HAN', '07:00:00', 330
) AS routes
JOIN airport AS origin ON origin.code = routes.origin_code
JOIN airport AS destination ON destination.code = routes.destination_code
CROSS JOIN (
    SELECT 0 AS day_offset UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13
) AS days;

INSERT INTO flight_fare (flight_id, cabin_class, adult_base_fare, capacity, available_seats)
SELECT f.id, cabins.cabin_class,
       CASE LEFT(f.flight_number, 3)
           WHEN 'SG1' THEN 1200000 WHEN 'SG2' THEN 2300000
           WHEN 'SG3' THEN 1800000 WHEN 'SG4' THEN 4200000
       END * cabins.price_factor,
       cabins.capacity, cabins.capacity
FROM flight AS f
CROSS JOIN (
    SELECT 'ECONOMY' AS cabin_class, 1 AS price_factor, 40 AS capacity
    UNION ALL SELECT 'BUSINESS', 3, 8
) AS cabins;

INSERT INTO passenger_fare_rule (route_scope, passenger_type, multiplier)
VALUES ('VN_DOMESTIC', 'CHILD', 0.900000), ('INTERNATIONAL', 'CHILD', 0.750000),
       ('VN_DOMESTIC', 'INFANT', 0.100000), ('OTHER_DOMESTIC', 'INFANT', 0.100000),
       ('INTERNATIONAL', 'INFANT', 0.100000);

INSERT INTO baggage_policy (flight_fare_id, passenger_type, included_pieces, can_purchase,
                            max_additional_pieces, price_per_piece)
SELECT fare.id, passengers.passenger_type, IF(fare.cabin_class = 'BUSINESS', 2, 1),
       TRUE, 2, IF(origin.country_code = destination.country_code, 250000, 450000)
FROM flight_fare AS fare
JOIN flight AS f ON f.id = fare.flight_id
JOIN airport AS origin ON origin.id = f.origin_airport_id
JOIN airport AS destination ON destination.id = f.destination_airport_id
CROSS JOIN (SELECT 'ADULT' AS passenger_type UNION ALL SELECT 'CHILD') AS passengers;

INSERT INTO fee_type (code, name) VALUES ('DEMO_AIRPORT_FEE', 'Phí sân bay mẫu');

INSERT INTO fare_fee_configuration (flight_fare_id, passenger_type)
SELECT id, passengers.passenger_type
FROM flight_fare
CROSS JOIN (SELECT 'ADULT' AS passenger_type UNION ALL SELECT 'CHILD') AS passengers;

INSERT INTO fare_fee (flight_fare_id, passenger_type, fee_type_id, unit_amount)
SELECT config.flight_fare_id, config.passenger_type, fee.id,
       IF(origin.country_code = destination.country_code, 120000, 320000)
FROM fare_fee_configuration AS config
JOIN flight_fare AS fare ON fare.id = config.flight_fare_id
JOIN flight AS f ON f.id = fare.flight_id
JOIN airport AS origin ON origin.id = f.origin_airport_id
JOIN airport AS destination ON destination.id = f.destination_airport_id
JOIN fee_type AS fee ON fee.code = 'DEMO_AIRPORT_FEE';

COMMIT;

SELECT (SELECT COUNT(*) FROM airport) AS airports,
       (SELECT COUNT(*) FROM flight) AS flights,
       (SELECT COUNT(*) FROM flight_fare) AS flight_fares,
       (SELECT COUNT(*) FROM baggage_policy) AS baggage_policies,
       (SELECT COUNT(*) FROM fare_fee) AS fare_fees,
       @first_demo_day AS first_demo_day_utc;
