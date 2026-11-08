-- Local test fixture, not a Flyway migration. Run against the SkyGo V10+ database.
-- Adds a separate SGN -> HAN flight tomorrow at 10:30 Vietnam time.
-- One adult, ECONOMY, one way, no extra bags: 9,091 fare + 909 VAT = 10,000 VND.
-- Re-running on the same day preserves existing fares, seats and booking history.
START TRANSACTION;

SET @test_date = DATE(UTC_TIMESTAMP() + INTERVAL 7 HOUR) + INTERVAL 1 DAY;
SET @test_public_id = CONCAT('payos-test-10k-sgn-han-', DATE_FORMAT(@test_date, '%Y%m%d'));
SET @departure_utc = TIMESTAMP(@test_date, '10:30:00') - INTERVAL 7 HOUR;
SET @route_id = (
  SELECT r.id FROM air_route r
  JOIN airport o ON o.id = r.origin_airport_id
  JOIN airport d ON d.id = r.destination_airport_id
  WHERE o.code = 'SGN' AND d.code = 'HAN' AND r.is_active = 1
    AND o.is_active = 1 AND d.is_active = 1
);

INSERT INTO pricing_policy
  (version, economy_fixed_vnd, economy_per_km_vnd, business_multiplier, vat_rate, effective_from_utc)
SELECT COALESCE(MAX(version), 0) + 1, 9091, 0, 2.5, 0.10, UTC_TIMESTAMP(6) - INTERVAL 1 DAY
FROM pricing_policy
HAVING NOT EXISTS (
  SELECT 1 FROM pricing_policy
  WHERE economy_fixed_vnd = 9091 AND economy_per_km_vnd = 0
    AND business_multiplier = 2.5 AND vat_rate = 0.10
    AND effective_from_utc <= UTC_TIMESTAMP(6) AND effective_to_utc IS NULL
);
SET @policy_id = (
  SELECT id FROM pricing_policy
  WHERE economy_fixed_vnd = 9091 AND economy_per_km_vnd = 0
    AND business_multiplier = 2.5 AND vat_rate = 0.10
    AND effective_from_utc <= UTC_TIMESTAMP(6) AND effective_to_utc IS NULL
  ORDER BY id LIMIT 1
);

INSERT INTO flight
  (public_id, flight_number, route_id, departure_at_utc, arrival_at_utc, sale_status)
SELECT @test_public_id, 'SGTEST10K', @route_id, @departure_utc,
       @departure_utc + INTERVAL 131 MINUTE, 'ON_SALE'
WHERE NOT EXISTS (SELECT 1 FROM flight WHERE public_id = @test_public_id);
SET @flight_id = (SELECT id FROM flight WHERE public_id = @test_public_id);

INSERT INTO flight_fare
  (flight_id, pricing_policy_id, priced_distance_km, cabin_class, adult_base_fare,
   currency, capacity, available_seats, is_on_sale)
SELECT @flight_id, @policy_id, r.air_distance_km, 'ECONOMY', 9091, 'VND', 10, 10, 1
FROM air_route r WHERE r.id = @route_id
  AND NOT EXISTS (SELECT 1 FROM flight_fare WHERE flight_id = @flight_id AND cabin_class = 'ECONOMY');
SET @fare_id = (SELECT id FROM flight_fare WHERE flight_id = @flight_id AND cabin_class = 'ECONOMY');

-- Copy the existing economy benefit configuration; keep policy validation intact.
SET @benefit_source = (
  SELECT ff.id FROM flight_fare ff JOIN flight f ON f.id = ff.flight_id
  WHERE f.route_id = @route_id AND ff.cabin_class = 'ECONOMY' AND ff.id <> @fare_id
    AND (SELECT COUNT(*) FROM fare_benefit_policy b WHERE b.flight_fare_id = ff.id) = 3
  ORDER BY ff.id LIMIT 1
);
INSERT INTO fare_benefit_policy
  (flight_fare_id, passenger_type, carry_on_max_kg, checked_pieces,
   checked_baggage_policy_id, included_meals_per_leg, seat_type, stroller_pieces, stroller_max_kg)
SELECT @fare_id, b.passenger_type, b.carry_on_max_kg, b.checked_pieces,
       b.checked_baggage_policy_id, b.included_meals_per_leg, b.seat_type, b.stroller_pieces, b.stroller_max_kg
FROM fare_benefit_policy b WHERE b.flight_fare_id = @benefit_source
  AND NOT EXISTS (SELECT 1 FROM fare_benefit_policy existing
                  WHERE existing.flight_fare_id = @fare_id AND existing.passenger_type = b.passenger_type);

COMMIT;

SELECT f.public_id, f.flight_number, 'SGN' AS origin, 'HAN' AS destination,
       f.departure_at_utc + INTERVAL 7 HOUR AS departure_vietnam,
       ff.adult_base_fare AS fare_before_vat,
       ROUND(ff.adult_base_fare * p.vat_rate) AS vat,
       ff.adult_base_fare + ROUND(ff.adult_base_fare * p.vat_rate) AS total_vnd,
       ff.available_seats,
       (SELECT COUNT(*) FROM fare_benefit_policy b WHERE b.flight_fare_id = ff.id) AS benefit_types
FROM flight f JOIN flight_fare ff ON ff.flight_id = f.id
JOIN pricing_policy p ON p.id = ff.pricing_policy_id
WHERE f.public_id = @test_public_id;
