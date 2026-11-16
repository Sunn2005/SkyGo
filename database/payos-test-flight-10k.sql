-- One test flight for a small real PayOS payment.
-- Run once against the SkyGo database in HeidiSQL. This script only INSERTs:
-- one pricing policy, one flight, one ECONOMY fare and three passenger benefits.
-- It does not change existing catalog, bookings, payments or Flyway migrations.
-- One adult, one way, no extra baggage: 10,000 VND fare + 1,000 VND VAT = 11,000 VND.
-- Route: SGN -> PQC, departing at 09:00 Vietnam time three days after execution.

USE skygo;
SET time_zone = '+00:00';
START TRANSACTION;

SET @test_public_id = 'payos-test-10k-sgn-pqc-20261110';
SET @route_id = (
    SELECT r.id
    FROM air_route r
    JOIN airport origin ON origin.id = r.origin_airport_id
    JOIN airport destination ON destination.id = r.destination_airport_id
    WHERE origin.code = 'SGN' AND destination.code = 'PQC'
      AND r.is_active = 1 AND origin.is_active = 1 AND destination.is_active = 1
    LIMIT 1
);
SET @distance_km = (SELECT air_distance_km FROM air_route WHERE id = @route_id);
SET @baggage_policy_id = (
    SELECT id FROM checked_baggage_policy
    WHERE version = 1 AND effective_from_utc <= UTC_TIMESTAMP(6)
      AND (effective_to_utc IS NULL OR effective_to_utc > UTC_TIMESTAMP(6))
    LIMIT 1
);
SET @create_test_flight = IF(
    @route_id IS NOT NULL AND @baggage_policy_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM flight WHERE public_id = @test_public_id), 1, 0
);
SET @test_policy_version = (SELECT COALESCE(MAX(version), 0) + 1 FROM pricing_policy);

-- A dedicated policy is necessary: the backend recalculates the fare from its policy.
-- The existing 250,000 VND + 2,600 VND/km policy is left untouched.
INSERT INTO pricing_policy
    (version, economy_fixed_vnd, economy_per_km_vnd, business_multiplier,
     vat_rate, effective_from_utc)
SELECT @test_policy_version, 10000, 0, 2.5, 0.1, UTC_TIMESTAMP(6)
WHERE @create_test_flight = 1;
SET @test_policy_id = IF(@create_test_flight = 1, LAST_INSERT_ID(), NULL);

INSERT INTO flight
    (public_id, flight_number, route_id, departure_at_utc, arrival_at_utc, sale_status)
SELECT @test_public_id, 'SGTEST10K', @route_id,
       UTC_DATE() + INTERVAL 3 DAY + INTERVAL 2 HOUR,
       UTC_DATE() + INTERVAL 3 DAY + INTERVAL 3 HOUR + INTERVAL 15 MINUTE,
       'ON_SALE'
WHERE @create_test_flight = 1;
SET @test_flight_id = IF(@create_test_flight = 1, LAST_INSERT_ID(), NULL);

INSERT INTO flight_fare
    (flight_id, pricing_policy_id, priced_distance_km, cabin_class,
     adult_base_fare, currency, capacity, available_seats, is_on_sale)
SELECT @test_flight_id, @test_policy_id, @distance_km, 'ECONOMY',
       10000, 'VND', 10, 10, 1
WHERE @create_test_flight = 1;
SET @test_fare_id = IF(@create_test_flight = 1, LAST_INSERT_ID(), NULL);

INSERT INTO fare_benefit_policy
    (flight_fare_id, passenger_type, carry_on_max_kg, checked_pieces,
     checked_baggage_policy_id, included_meals_per_leg, seat_type,
     stroller_pieces, stroller_max_kg)
SELECT @test_fare_id, types.passenger_type,
       CASE WHEN types.passenger_type = 'INFANT' THEN 3 ELSE 7 END,
       CASE WHEN types.passenger_type = 'INFANT' THEN 0 ELSE 1 END,
       @baggage_policy_id, 0,
       CASE WHEN types.passenger_type = 'INFANT' THEN NULL ELSE 'STANDARD' END,
       CASE WHEN types.passenger_type = 'INFANT' THEN 1 ELSE 0 END,
       CASE WHEN types.passenger_type = 'INFANT' THEN 15 ELSE 0 END
FROM (
    SELECT 'ADULT' AS passenger_type
    UNION ALL SELECT 'CHILD'
    UNION ALL SELECT 'INFANT'
) AS types
WHERE @create_test_flight = 1;

COMMIT;

-- A rerun on the same database will not add another flight or policy.
-- Confirm that this query returns exactly one flight and its 10,000 VND fare.
SELECT CASE
    WHEN @route_id IS NULL THEN 'MISSING_SGN_PQC_ROUTE'
    WHEN @baggage_policy_id IS NULL THEN 'MISSING_ACTIVE_BAGGAGE_POLICY'
    WHEN @create_test_flight = 0 THEN 'ALREADY_EXISTS'
    ELSE 'CREATED'
END AS script_result;

SELECT f.public_id, f.flight_number, origin.code AS origin,
       destination.code AS destination, f.departure_at_utc,
       fare.cabin_class, fare.adult_base_fare, fare.available_seats,
       (SELECT COUNT(*) FROM fare_benefit_policy benefit
        WHERE benefit.flight_fare_id = fare.id) AS benefit_count
FROM flight f
JOIN air_route route ON route.id = f.route_id
JOIN airport origin ON origin.id = route.origin_airport_id
JOIN airport destination ON destination.id = route.destination_airport_id
JOIN flight_fare fare ON fare.flight_id = f.id
WHERE f.public_id = @test_public_id;
