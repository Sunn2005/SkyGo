-- Development fixture for the home screen. Run after Flyway V4 on `skygo`.
-- It uses the only existing app_user and never changes that account or its password.
-- Re-running after a successful run does nothing. Do not run skygo-demo.sql with Flyway.
-- The six paid bookings are synthetic, solely to exercise route rankings and the
-- upcoming-trip card. Passenger documents contain a non-reversible demo marker.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DROP PROCEDURE IF EXISTS seed_skygo_home_demo;
DELIMITER $$
CREATE PROCEDURE seed_skygo_home_demo()
BEGIN
    DECLARE v_user_id BIGINT UNSIGNED;
    DECLARE v_user_count INT;
    DECLARE v_contact_email VARCHAR(254);
    DECLARE v_contact_phone VARCHAR(16);
    DECLARE v_session_id BIGINT UNSIGNED;
    DECLARE v_policy_id BIGINT UNSIGNED;
    DECLARE v_baggage_policy_id BIGINT UNSIGNED;
    DECLARE v_sgn BIGINT UNSIGNED;
    DECLARE v_han BIGINT UNSIGNED;
    DECLARE v_dad BIGINT UNSIGNED;
    DECLARE v_pqc BIGINT UNSIGNED;
    DECLARE v_route_id BIGINT UNSIGNED;
    DECLARE v_distance INT UNSIGNED;
    DECLARE v_i INT DEFAULT 1;
    DECLARE v_flight_id BIGINT UNSIGNED;
    DECLARE v_fare_id BIGINT UNSIGNED;
    DECLARE v_business_fare_id BIGINT UNSIGNED;
    DECLARE v_hold_id BIGINT UNSIGNED;
    DECLARE v_review_id BIGINT UNSIGNED;
    DECLARE v_booking_id BIGINT UNSIGNED;
    DECLARE v_leg_id BIGINT UNSIGNED;
    DECLARE v_passenger_id BIGINT UNSIGNED;
    DECLARE v_base_fare BIGINT UNSIGNED;
    DECLARE v_vat BIGINT UNSIGNED;
    DECLARE v_departure DATETIME(6);
    DECLARE v_hold_start DATETIME(6);
    DECLARE v_flight_public_id VARCHAR(64);
    DECLARE v_flight_number VARCHAR(20);
    DECLARE v_booking_code VARCHAR(32);
    DECLARE v_duration INT UNSIGNED;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (SELECT 1 FROM booking WHERE booking_code = 'HDEMO001') THEN
        SELECT COUNT(*) INTO v_user_count FROM app_user;
        IF v_user_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Home demo seed requires exactly one existing app_user';
        END IF;
        START TRANSACTION;
        SELECT id, COALESCE(NULLIF(email, ''), 'demo@example.invalid'), phone_normalized
          INTO v_user_id, v_contact_email, v_contact_phone FROM app_user LIMIT 1;

        INSERT IGNORE INTO airport
          (code, name, city, country_code, country_name, region_code, region_name,
           region_sort_order, time_zone)
        VALUES
          ('SGN', 'Tân Sơn Nhất', 'Sài Gòn', 'VN', 'Việt Nam', 'SOUTH', 'Miền Nam', 3, 'Asia/Ho_Chi_Minh'),
          ('HAN', 'Nội Bài', 'Hà Nội', 'VN', 'Việt Nam', 'NORTH', 'Miền Bắc', 1, 'Asia/Ho_Chi_Minh'),
          ('DAD', 'Đà Nẵng', 'Đà Nẵng', 'VN', 'Việt Nam', 'CENTRAL', 'Miền Trung', 2, 'Asia/Ho_Chi_Minh'),
          ('PQC', 'Phú Quốc', 'Phú Quốc', 'VN', 'Việt Nam', 'SOUTH', 'Miền Nam', 3, 'Asia/Ho_Chi_Minh');
        SELECT id INTO v_sgn FROM airport WHERE code = 'SGN';
        SELECT id INTO v_han FROM airport WHERE code = 'HAN';
        SELECT id INTO v_dad FROM airport WHERE code = 'DAD';
        SELECT id INTO v_pqc FROM airport WHERE code = 'PQC';

        -- Configured demo air distances, not road distances or verified route measurements.
        INSERT IGNORE INTO air_route (origin_airport_id, destination_airport_id, air_distance_km)
        VALUES (v_sgn, v_han, 1150), (v_han, v_dad, 600), (v_sgn, v_pqc, 300);

        INSERT IGNORE INTO pricing_policy
          (version, economy_fixed_vnd, economy_per_km_vnd, business_multiplier,
           vat_rate, effective_from_utc)
        VALUES (1, 250000, 2600, 2.5, 0.1, UTC_TIMESTAMP(6) - INTERVAL 1 DAY);
        SELECT id INTO v_policy_id FROM pricing_policy WHERE version = 1;
        SELECT id INTO v_baggage_policy_id FROM checked_baggage_policy WHERE version = 1;
        IF NOT EXISTS (
            SELECT 1 FROM pricing_policy
             WHERE id = v_policy_id AND economy_fixed_vnd = 250000
               AND economy_per_km_vnd = 2600 AND business_multiplier = 2.5
               AND vat_rate = 0.1
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pricing policy version 1 differs from home demo assumptions';
        END IF;

        INSERT IGNORE INTO passenger_fare_rule (route_scope, passenger_type, multiplier)
        VALUES ('VN_DOMESTIC', 'ADULT', 1), ('VN_DOMESTIC', 'CHILD', 0.9),
               ('VN_DOMESTIC', 'INFANT', 0.1), ('INTERNATIONAL', 'ADULT', 1),
               ('INTERNATIONAL', 'CHILD', 0.75), ('INTERNATIONAL', 'INFANT', 0.1);

        -- This revoked synthetic session exists only to satisfy booking_review history.
        INSERT INTO auth_session
          (public_id, user_id, token_hash, expires_at_utc, revoked_at_utc, created_at_utc)
        VALUES
          ('home-demo-revoked-session', v_user_id, UNHEX(SHA2(UUID(), 256)),
           UTC_TIMESTAMP(6) - INTERVAL 1 HOUR, UTC_TIMESTAMP(6) - INTERVAL 1 HOUR,
           UTC_TIMESTAMP(6) - INTERVAL 2 HOUR);
        SET v_session_id = LAST_INSERT_ID();

        WHILE v_i <= 6 DO
            IF v_i <= 3 THEN
                SELECT id, air_distance_km INTO v_route_id, v_distance FROM air_route
                 WHERE origin_airport_id = v_sgn AND destination_airport_id = v_han;
                SET v_duration = 125;
            ELSEIF v_i <= 5 THEN
                SELECT id, air_distance_km INTO v_route_id, v_distance FROM air_route
                 WHERE origin_airport_id = v_han AND destination_airport_id = v_dad;
                SET v_duration = 80;
            ELSE
                SELECT id, air_distance_km INTO v_route_id, v_distance FROM air_route
                 WHERE origin_airport_id = v_sgn AND destination_airport_id = v_pqc;
                SET v_duration = 60;
            END IF;

            SET v_base_fare = 250000 + 2600 * v_distance;
            SET v_vat = ROUND(v_base_fare * 0.1);
            SET v_departure = DATE_ADD(DATE_ADD(UTC_DATE(), INTERVAL (v_i + 10) DAY), INTERVAL 210 MINUTE);
            SET v_flight_public_id = CONCAT('home-demo-flight-', LPAD(v_i, 2, '0'));
            SET v_flight_number = CONCAT('SG', 1000 + v_i);
            SET v_booking_code = CONCAT('HDEMO', LPAD(v_i, 3, '0'));
            SET v_hold_start = UTC_TIMESTAMP(6) - INTERVAL 2 HOUR;

            INSERT INTO flight
              (public_id, flight_number, route_id, departure_at_utc, arrival_at_utc, sale_status)
            VALUES
              (v_flight_public_id, v_flight_number, v_route_id, v_departure,
               v_departure + INTERVAL v_duration MINUTE, 'ON_SALE');
            SET v_flight_id = LAST_INSERT_ID();

            INSERT INTO flight_fare
              (flight_id, pricing_policy_id, priced_distance_km, cabin_class,
               adult_base_fare, capacity, available_seats)
            VALUES (v_flight_id, v_policy_id, v_distance, 'ECONOMY', v_base_fare, 50, 49);
            SET v_fare_id = LAST_INSERT_ID();
            INSERT INTO flight_fare
              (flight_id, pricing_policy_id, priced_distance_km, cabin_class,
               adult_base_fare, capacity, available_seats)
            VALUES (v_flight_id, v_policy_id, v_distance, 'BUSINESS', v_base_fare * 2.5, 10, 10);
            SET v_business_fare_id = LAST_INSERT_ID();

            INSERT INTO fare_benefit_policy
              (flight_fare_id, passenger_type, carry_on_max_kg, checked_pieces,
               checked_baggage_policy_id, included_meals_per_leg, seat_type,
               stroller_pieces, stroller_max_kg)
            VALUES
              (v_fare_id, 'ADULT', 7, 1, v_baggage_policy_id, 0, 'STANDARD', 0, 0),
              (v_fare_id, 'CHILD', 7, 1, v_baggage_policy_id, 0, 'STANDARD', 0, 0),
              (v_fare_id, 'INFANT', 3, 0, v_baggage_policy_id, 0, NULL, 1, 15),
              (v_business_fare_id, 'ADULT', 15, 2, v_baggage_policy_id, 1, 'BUSINESS', 0, 0),
              (v_business_fare_id, 'CHILD', 15, 2, v_baggage_policy_id, 1, 'BUSINESS', 0, 0),
              (v_business_fare_id, 'INFANT', 3, 0, v_baggage_policy_id, 0, NULL, 1, 15);

            INSERT INTO seat_hold
              (public_id, user_id, trip_type, adults, children, infants, status,
               started_at_utc, expires_at_utc, created_at_utc)
            VALUES
              (CONCAT('home-demo-hold-', LPAD(v_i, 2, '0')), v_user_id,
               'ONE_WAY', 1, 0, 0, 'CONSUMED', v_hold_start,
               v_hold_start + INTERVAL 15 MINUTE, v_hold_start);
            SET v_hold_id = LAST_INSERT_ID();
            INSERT INTO seat_hold_leg (seat_hold_id, direction, flight_fare_id, reserved_seats)
            VALUES (v_hold_id, 'OUTBOUND', v_fare_id, 1);

            INSERT INTO booking_review
              (public_id, seat_hold_id, user_id, session_id, snapshot_ciphertext,
               snapshot_schema_version, expires_at_utc, used_at_utc,
               snapshot_purged_at_utc, created_at_utc)
            VALUES
              (CONCAT('home-demo-review-', LPAD(v_i, 2, '0')), v_hold_id,
               v_user_id, v_session_id, NULL, 1,
               v_hold_start + INTERVAL 15 MINUTE, v_hold_start + INTERVAL 5 MINUTE,
               v_hold_start + INTERVAL 5 MINUTE, v_hold_start);
            SET v_review_id = LAST_INSERT_ID();

            INSERT INTO booking
              (public_id, booking_code, user_id, review_id, seat_hold_id,
               idempotency_key, request_hash, trip_type, adults, children, infants,
               contact_email, contact_phone, fare_subtotal, vat_rate, vat_amount,
               total_amount, booking_status, created_at_utc)
            VALUES
              (CONCAT('home-demo-booking-', LPAD(v_i, 2, '0')), v_booking_code,
               v_user_id, v_review_id, v_hold_id,
               CONCAT('home-demo-idempotency-', v_i), UNHEX(SHA2(CONCAT('home-demo-', v_i), 256)),
               'ONE_WAY', 1, 0, 0, v_contact_email, v_contact_phone,
               v_base_fare, 0.1, v_vat, v_base_fare + v_vat, 'PAID',
               v_hold_start + INTERVAL 5 MINUTE);
            SET v_booking_id = LAST_INSERT_ID();
            UPDATE seat_hold SET booking_id = v_booking_id WHERE id = v_hold_id;

            INSERT INTO booking_leg
              (booking_id, direction, flight_fare_id, flight_public_id, flight_number,
               airline_code, airline_name, cabin_class, origin_code, origin_name,
               origin_city, origin_country_code, origin_time_zone, destination_code,
               destination_name, destination_city, destination_country_code,
               destination_time_zone, departure_at_utc, arrival_at_utc,
               duration_minutes, route_type)
            SELECT v_booking_id, 'OUTBOUND', v_fare_id, f.public_id, f.flight_number,
                   'SG', 'SkyGo', 'ECONOMY', oa.code, oa.name, oa.city,
                   oa.country_code, oa.time_zone, da.code, da.name, da.city,
                   da.country_code, da.time_zone, f.departure_at_utc,
                   f.arrival_at_utc, v_duration, 'DOMESTIC'
              FROM flight f JOIN air_route r ON r.id = f.route_id
              JOIN airport oa ON oa.id = r.origin_airport_id
              JOIN airport da ON da.id = r.destination_airport_id
             WHERE f.id = v_flight_id;
            SET v_leg_id = LAST_INSERT_ID();

            INSERT INTO booking_passenger
              (public_id, booking_id, client_passenger_id, passenger_type, title,
               given_names, surname, date_of_birth, gender, document_type,
               document_number_ciphertext, issuing_country_code, nationality_country_code)
            VALUES
              (CONCAT('home-demo-passenger-', LPAD(v_i, 2, '0')), v_booking_id,
               'demo-passenger', 'ADULT', 'MR', 'Khách', 'Demo', '1990-01-01',
               'MALE', 'PERSONAL_ID', UNHEX(SHA2('NOT_A_REAL_DOCUMENT_HOME_DEMO', 256)),
               'VN', 'VN');
            SET v_passenger_id = LAST_INSERT_ID();
            INSERT INTO booking_fare_line
              (booking_id, booking_leg_id, passenger_type, quantity,
               unit_base_fare, fare_subtotal)
            VALUES (v_booking_id, v_leg_id, 'ADULT', 1, v_base_fare, v_base_fare);
            INSERT INTO booking_passenger_benefit
              (booking_id, booking_passenger_id, booking_leg_id, carry_on_max_kg,
               checked_pieces, checked_piece_max_kg, checked_baggage_policy_id,
               checked_piece_max_linear_cm, included_meals_per_leg,
               seat_type, stroller_pieces, stroller_max_kg)
            VALUES (v_booking_id, v_passenger_id, v_leg_id, 7, 1, 23,
                    v_baggage_policy_id, 158, 0, 'STANDARD', 0, 0);

            INSERT INTO payment
              (public_id, booking_id, method, status, amount, transaction_code,
               paid_at_utc)
            VALUES
              (CONCAT('home-demo-payment-', LPAD(v_i, 2, '0')), v_booking_id,
               'QR_SIMULATION', 'SUCCEEDED', v_base_fare + v_vat,
               CONCAT('HDEMO-TXN-', LPAD(v_i, 3, '0')),
               v_hold_start + INTERVAL 10 MINUTE);
            INSERT INTO ticket
              (ticket_number, booking_id, booking_passenger_id, issued_at_utc)
            VALUES
              (CONCAT('HDEMO-TKT-', LPAD(v_i, 3, '0')), v_booking_id,
               v_passenger_id, v_hold_start + INTERVAL 10 MINUTE);
            INSERT INTO invoice (public_id, booking_id, issued_at_utc)
            VALUES
              (CONCAT('home-demo-invoice-', LPAD(v_i, 2, '0')), v_booking_id,
               v_hold_start + INTERVAL 10 MINUTE);

            SET v_i = v_i + 1;
        END WHILE;
        COMMIT;
    END IF;
END$$
DELIMITER ;

CALL seed_skygo_home_demo();
DROP PROCEDURE seed_skygo_home_demo;
