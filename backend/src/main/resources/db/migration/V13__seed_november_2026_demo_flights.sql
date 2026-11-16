-- Fixed demo schedule for the 21 November 2026 presentation.
-- Five SGN -> PQC flights daily on 23-28 November, plus one PQC -> SGN flight
-- on 25 November. Local times are Asia/Ho_Chi_Minh (UTC+07:00); DB times are UTC.
-- This migration only inserts catalog data. It never changes existing flights,
-- fares, holds, bookings, payments, or tickets.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

INSERT INTO flight
  (public_id, flight_number, route_id, departure_at_utc, arrival_at_utc, sale_status)
SELECT CONCAT('demo-202611-sgn-pqc-', DATE_FORMAT(days.flight_date, '%d'), '-', slots.n),
       CONCAT('SG11', DATE_FORMAT(days.flight_date, '%d'), slots.n),
       route.id,
       DATE_ADD(CAST(days.flight_date AS DATETIME), INTERVAL (slots.local_minute - 420) MINUTE),
       DATE_ADD(CAST(days.flight_date AS DATETIME), INTERVAL (slots.local_minute - 420 + 75) MINUTE),
       'ON_SALE'
FROM air_route route
JOIN airport origin ON origin.id = route.origin_airport_id AND origin.code = 'SGN'
JOIN airport destination ON destination.id = route.destination_airport_id AND destination.code = 'PQC'
CROSS JOIN (
  SELECT DATE('2026-11-23') AS flight_date UNION ALL
  SELECT DATE('2026-11-24') UNION ALL
  SELECT DATE('2026-11-25') UNION ALL
  SELECT DATE('2026-11-26') UNION ALL
  SELECT DATE('2026-11-27') UNION ALL
  SELECT DATE('2026-11-28')
) days
CROSS JOIN (
  SELECT 1 AS n, 390 AS local_minute UNION ALL  -- 06:30
  SELECT 2, 540 UNION ALL                       -- 09:00
  SELECT 3, 690 UNION ALL                       -- 11:30
  SELECT 4, 900 UNION ALL                       -- 15:00
  SELECT 5, 1170                                -- 19:30
) slots
WHERE route.is_active = 1 AND origin.is_active = 1 AND destination.is_active = 1
  AND NOT EXISTS (
    SELECT 1 FROM flight existing
    WHERE existing.public_id = CONCAT('demo-202611-sgn-pqc-', DATE_FORMAT(days.flight_date, '%d'), '-', slots.n)
  );

INSERT INTO flight
  (public_id, flight_number, route_id, departure_at_utc, arrival_at_utc, sale_status)
SELECT 'demo-202611-pqc-sgn-25-1', 'SG1125R1', route.id,
       '2026-11-25 09:30:00', '2026-11-25 10:45:00', 'ON_SALE'
FROM air_route route
JOIN airport origin ON origin.id = route.origin_airport_id AND origin.code = 'PQC'
JOIN airport destination ON destination.id = route.destination_airport_id AND destination.code = 'SGN'
WHERE route.is_active = 1 AND origin.is_active = 1 AND destination.is_active = 1
  AND NOT EXISTS (
    SELECT 1 FROM flight existing WHERE existing.public_id = 'demo-202611-pqc-sgn-25-1'
  );

-- Keep the same pricing formula as V2 so booking recalculation matches the
-- search result. The two cabins are choices on each flight, not extra flights.
INSERT INTO flight_fare
  (flight_id, pricing_policy_id, priced_distance_km, cabin_class,
   adult_base_fare, capacity, available_seats, is_on_sale)
SELECT flight.id, pricing.id, route.air_distance_km, cabin.class_name,
       ROUND((pricing.economy_fixed_vnd + pricing.economy_per_km_vnd * route.air_distance_km)
             * cabin.multiplier),
       cabin.seats, cabin.seats, 1
FROM flight
JOIN air_route route ON route.id = flight.route_id
JOIN pricing_policy pricing ON pricing.version = 1
CROSS JOIN (
  SELECT 'ECONOMY' AS class_name, 1 AS multiplier, 60 AS seats UNION ALL
  SELECT 'BUSINESS', 2.5, 12
) cabin
WHERE flight.public_id LIKE 'demo-202611-%'
  AND NOT EXISTS (
    SELECT 1 FROM flight_fare existing
    WHERE existing.flight_id = flight.id AND existing.cabin_class = cabin.class_name
  );

INSERT INTO fare_benefit_policy
  (flight_fare_id, passenger_type, carry_on_max_kg, checked_pieces,
   checked_baggage_policy_id, included_meals_per_leg, seat_type,
   stroller_pieces, stroller_max_kg)
SELECT fare.id, passenger.passenger_type,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 3
            WHEN fare.cabin_class = 'BUSINESS' THEN 15 ELSE 7 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 0
            WHEN fare.cabin_class = 'BUSINESS' THEN 2 ELSE 1 END,
       baggage.id,
       CASE WHEN passenger.passenger_type <> 'INFANT' AND fare.cabin_class = 'BUSINESS'
            THEN 1 ELSE 0 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN NULL
            WHEN fare.cabin_class = 'BUSINESS' THEN 'BUSINESS' ELSE 'STANDARD' END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 1 ELSE 0 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 15 ELSE 0 END
FROM flight_fare fare
JOIN flight ON flight.id = fare.flight_id
JOIN checked_baggage_policy baggage ON baggage.version = 1
CROSS JOIN (
  SELECT 'ADULT' AS passenger_type UNION ALL
  SELECT 'CHILD' UNION ALL
  SELECT 'INFANT'
) passenger
WHERE flight.public_id LIKE 'demo-202611-%'
  AND NOT EXISTS (
    SELECT 1 FROM fare_benefit_policy existing
    WHERE existing.flight_fare_id = fare.id
      AND existing.passenger_type = passenger.passenger_type
  );
