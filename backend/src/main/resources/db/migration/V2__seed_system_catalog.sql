-- Initial SkyGo catalog. No users, holds, bookings, payments or tickets are seeded.
-- Region is the product's display group, not a geographical continent.
-- Foreign countries have one airport serving their capital; Viet Nam has four.
-- Codes and airport names are real; route distances and SkyGo schedules are demo configuration.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

INSERT INTO airport
  (code, name, city, country_code, country_name, region_code, region_name,
   region_sort_order, time_zone, is_active)
VALUES
  ('HAN', 'Sân bay quốc tế Nội Bài', 'Hà Nội', 'VN', 'Việt Nam', 'VIETNAM', 'Việt Nam', 1, 'Asia/Ho_Chi_Minh', 1),
  ('SGN', 'Sân bay quốc tế Tân Sơn Nhất', 'TP. Hồ Chí Minh', 'VN', 'Việt Nam', 'VIETNAM', 'Việt Nam', 1, 'Asia/Ho_Chi_Minh', 1),
  ('DAD', 'Sân bay quốc tế Đà Nẵng', 'Đà Nẵng', 'VN', 'Việt Nam', 'VIETNAM', 'Việt Nam', 1, 'Asia/Ho_Chi_Minh', 1),
  ('PQC', 'Sân bay quốc tế Phú Quốc', 'Phú Quốc', 'VN', 'Việt Nam', 'VIETNAM', 'Việt Nam', 1, 'Asia/Ho_Chi_Minh', 1),
  ('HND', 'Tokyo Haneda Airport', 'Tokyo', 'JP', 'Nhật Bản', 'NORTHEAST_ASIA', 'Đông Bắc Á', 2, 'Asia/Tokyo', 1),
  ('GMP', 'Gimpo International Airport', 'Seoul', 'KR', 'Hàn Quốc', 'NORTHEAST_ASIA', 'Đông Bắc Á', 2, 'Asia/Seoul', 1),
  ('SIN', 'Singapore Changi Airport', 'Singapore', 'SG', 'Singapore', 'SOUTHEAST_ASIA', 'Đông Nam Á', 3, 'Asia/Singapore', 1),
  ('BKK', 'Suvarnabhumi Airport', 'Bangkok', 'TH', 'Thái Lan', 'SOUTHEAST_ASIA', 'Đông Nam Á', 3, 'Asia/Bangkok', 1),
  ('DEL', 'Indira Gandhi International Airport', 'New Delhi', 'IN', 'Ấn Độ', 'SOUTH_ASIA', 'Nam Á', 4, 'Asia/Kolkata', 1),
  ('KTM', 'Tribhuvan International Airport', 'Kathmandu', 'NP', 'Nepal', 'SOUTH_ASIA', 'Nam Á', 4, 'Asia/Kathmandu', 1),
  ('LHR', 'Heathrow Airport', 'London', 'GB', 'Vương quốc Anh', 'EUROPE', 'Châu Âu', 5, 'Europe/London', 1),
  ('CDG', 'Paris Charles de Gaulle Airport', 'Paris', 'FR', 'Pháp', 'EUROPE', 'Châu Âu', 5, 'Europe/Paris', 1),
  ('CBR', 'Canberra Airport', 'Canberra', 'AU', 'Úc', 'OCEANIA', 'Châu Úc', 6, 'Australia/Sydney', 1),
  ('WLG', 'Wellington Airport', 'Wellington', 'NZ', 'New Zealand', 'OCEANIA', 'Châu Úc', 6, 'Pacific/Auckland', 1),
  ('IAD', 'Washington Dulles International Airport', 'Washington, D.C.', 'US', 'Hoa Kỳ', 'NORTH_AMERICA', 'Bắc Mỹ', 7, 'America/New_York', 1),
  ('YOW', 'Ottawa International Airport', 'Ottawa', 'CA', 'Canada', 'NORTH_AMERICA', 'Bắc Mỹ', 7, 'America/Toronto', 1),
  ('AUH', 'Zayed International Airport', 'Abu Dhabi', 'AE', 'Các Tiểu vương quốc Ả Rập Thống nhất', 'MIDDLE_EAST', 'Trung Đông', 8, 'Asia/Dubai', 1),
  ('DOH', 'Hamad International Airport', 'Doha', 'QA', 'Qatar', 'MIDDLE_EAST', 'Trung Đông', 8, 'Asia/Qatar', 1),
  ('NBO', 'Jomo Kenyatta International Airport', 'Nairobi', 'KE', 'Kenya', 'AFRICA', 'Châu Phi', 9, 'Africa/Nairobi', 1),
  ('ABV', 'Nnamdi Azikiwe International Airport', 'Abuja', 'NG', 'Nigeria', 'AFRICA', 'Châu Phi', 9, 'Africa/Lagos', 1),
  ('NQZ', 'Nursultan Nazarbayev International Airport', 'Astana', 'KZ', 'Kazakhstan', 'CENTRAL_ASIA', 'Trung Á', 10, 'Asia/Almaty', 1),
  ('TAS', 'Islam Karimov Tashkent International Airport', 'Tashkent', 'UZ', 'Uzbekistan', 'CENTRAL_ASIA', 'Trung Á', 10, 'Asia/Tashkent', 1),
  ('ESB', 'Ankara Esenboğa Airport', 'Ankara', 'TR', 'Thổ Nhĩ Kỳ', 'WEST_ASIA', 'Tây Á', 11, 'Europe/Istanbul', 1),
  ('GYD', 'Heydar Aliyev International Airport', 'Baku', 'AZ', 'Azerbaijan', 'WEST_ASIA', 'Tây Á', 11, 'Asia/Baku', 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), city = VALUES(city), country_code = VALUES(country_code),
  country_name = VALUES(country_name), region_code = VALUES(region_code),
  region_name = VALUES(region_name), region_sort_order = VALUES(region_sort_order),
  time_zone = VALUES(time_zone), is_active = VALUES(is_active);

INSERT INTO pricing_policy
  (version, economy_fixed_vnd, economy_per_km_vnd, business_multiplier,
   vat_rate, effective_from_utc)
SELECT 1, 250000, 2600, 2.5, 0.1, UTC_TIMESTAMP(6) - INTERVAL 1 DAY
WHERE NOT EXISTS (SELECT 1 FROM pricing_policy WHERE version = 1);

INSERT INTO passenger_fare_rule (route_scope, passenger_type, multiplier)
VALUES ('VN_DOMESTIC', 'ADULT', 1), ('VN_DOMESTIC', 'CHILD', 0.9),
       ('VN_DOMESTIC', 'INFANT', 0.1), ('INTERNATIONAL', 'ADULT', 1),
       ('INTERNATIONAL', 'CHILD', 0.75), ('INTERNATIONAL', 'INFANT', 0.1)
ON DUPLICATE KEY UPDATE multiplier = VALUES(multiplier), is_active = 1;

-- Both directions are required for round trips. Distances are illustrative air kilometres.
INSERT INTO air_route (origin_airport_id, destination_airport_id, air_distance_km)
SELECT origin.id, destination.id, pairs.km
FROM (
  SELECT 'SGN' AS from_code, 'HAN' AS to_code, 1150 AS km UNION ALL
  SELECT 'HAN', 'SGN', 1150 UNION ALL
  SELECT 'HAN', 'DAD', 600 UNION ALL
  SELECT 'DAD', 'HAN', 600 UNION ALL
  SELECT 'SGN', 'PQC', 300 UNION ALL
  SELECT 'PQC', 'SGN', 300
) pairs
JOIN airport origin ON origin.code = pairs.from_code
JOIN airport destination ON destination.code = pairs.to_code
WHERE NOT EXISTS (
  SELECT 1 FROM air_route r
  WHERE r.origin_airport_id = origin.id AND r.destination_airport_id = destination.id
);

-- The sample airline connects every foreign capital airport to the HAN hub.
INSERT INTO air_route (origin_airport_id, destination_airport_id, air_distance_km)
SELECT han.id, foreign_airport.id,
  CASE foreign_airport.code
    WHEN 'HND' THEN 3660 WHEN 'GMP' THEN 2760
    WHEN 'SIN' THEN 2200 WHEN 'BKK' THEN 990
    WHEN 'DEL' THEN 3030 WHEN 'KTM' THEN 1730
    WHEN 'LHR' THEN 9250 WHEN 'CDG' THEN 9180
    WHEN 'CBR' THEN 7940 WHEN 'WLG' THEN 10070
    WHEN 'IAD' THEN 13500 WHEN 'YOW' THEN 12500
    WHEN 'AUH' THEN 5300 WHEN 'DOH' THEN 5450
    WHEN 'NBO' THEN 8150 WHEN 'ABV' THEN 11100
    WHEN 'NQZ' THEN 4800 WHEN 'TAS' THEN 4450
    WHEN 'ESB' THEN 6500 WHEN 'GYD' THEN 5800
  END
FROM airport han JOIN airport foreign_airport ON foreign_airport.country_code <> 'VN'
WHERE han.code = 'HAN' AND foreign_airport.region_code IN
  ('NORTHEAST_ASIA', 'SOUTHEAST_ASIA', 'SOUTH_ASIA', 'EUROPE', 'OCEANIA',
   'NORTH_AMERICA', 'MIDDLE_EAST', 'AFRICA', 'CENTRAL_ASIA', 'WEST_ASIA')
AND NOT EXISTS (
  SELECT 1 FROM air_route r
  WHERE r.origin_airport_id = han.id AND r.destination_airport_id = foreign_airport.id
);

INSERT INTO air_route (origin_airport_id, destination_airport_id, air_distance_km)
SELECT han.destination_airport_id, han.origin_airport_id, han.air_distance_km
FROM air_route han JOIN airport origin ON origin.id = han.origin_airport_id
JOIN airport destination ON destination.id = han.destination_airport_id
WHERE origin.code = 'HAN' AND destination.country_code <> 'VN'
AND NOT EXISTS (
  SELECT 1 FROM air_route reverse_route
  WHERE reverse_route.origin_airport_id = han.destination_airport_id
    AND reverse_route.destination_airport_id = han.origin_airport_id
);

-- Three future dates per directed route. These are initial demo schedules,
-- not a perpetual flight schedule; future flights need a separate maintenance flow.
INSERT INTO flight
  (public_id, flight_number, route_id, departure_at_utc, arrival_at_utc, sale_status)
SELECT CONCAT('seed-v2-', origin.code, '-', destination.code, '-', dates.n),
       CONCAT('SG', LPAD(route.id, 3, '0'), dates.n), route.id,
       DATE_ADD(DATE_ADD(UTC_DATE(), INTERVAL (7 + dates.n * 14) DAY), INTERVAL 210 MINUTE),
       DATE_ADD(
         DATE_ADD(DATE_ADD(UTC_DATE(), INTERVAL (7 + dates.n * 14) DAY), INTERVAL 210 MINUTE),
         INTERVAL (45 + ROUND(route.air_distance_km * 60 / 800)) MINUTE),
       'ON_SALE'
FROM air_route route
JOIN airport origin ON origin.id = route.origin_airport_id
JOIN airport destination ON destination.id = route.destination_airport_id
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) dates
WHERE (origin.code = 'HAN' OR destination.code = 'HAN'
       OR (origin.code = 'SGN' AND destination.code = 'PQC')
       OR (origin.code = 'PQC' AND destination.code = 'SGN'))
  AND NOT EXISTS (
    SELECT 1 FROM flight existing
    WHERE existing.public_id =
      (CONCAT('seed-v2-', origin.code, '-', destination.code, '-', dates.n)
       COLLATE utf8mb4_nopad_bin)
  );

INSERT INTO flight_fare
  (flight_id, pricing_policy_id, priced_distance_km, cabin_class,
   adult_base_fare, capacity, available_seats, is_on_sale)
SELECT f.id, p.id, route.air_distance_km, cabin.class_name,
       ROUND((p.economy_fixed_vnd + p.economy_per_km_vnd * route.air_distance_km)
             * cabin.multiplier),
       cabin.seats, cabin.seats, 1
FROM flight f
JOIN air_route route ON route.id = f.route_id
JOIN pricing_policy p ON p.version = 1
CROSS JOIN (
  SELECT 'ECONOMY' AS class_name, 1 AS multiplier, 60 AS seats UNION ALL
  SELECT 'BUSINESS', 2.5, 12
) cabin
WHERE f.public_id LIKE 'seed-v2-%'
  AND NOT EXISTS (
    SELECT 1 FROM flight_fare existing
    WHERE existing.flight_id = f.id AND existing.cabin_class = cabin.class_name
  );

INSERT INTO fare_benefit_policy
  (flight_fare_id, passenger_type, carry_on_max_kg, checked_pieces,
   checked_piece_max_kg, included_meals_per_leg, seat_type,
   stroller_pieces, stroller_max_kg)
SELECT fare.id, passenger.passenger_type,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 3
            WHEN fare.cabin_class = 'BUSINESS' THEN 15 ELSE 7 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 0
            WHEN fare.cabin_class = 'BUSINESS' THEN 2 ELSE 1 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 0 ELSE 23 END,
       CASE WHEN passenger.passenger_type <> 'INFANT'
                 AND fare.cabin_class = 'BUSINESS' THEN 1 ELSE 0 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN NULL
            WHEN fare.cabin_class = 'BUSINESS' THEN 'BUSINESS' ELSE 'STANDARD' END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 1 ELSE 0 END,
       CASE WHEN passenger.passenger_type = 'INFANT' THEN 15 ELSE 0 END
FROM flight_fare fare
JOIN flight f ON f.id = fare.flight_id
CROSS JOIN (
  SELECT 'ADULT' AS passenger_type UNION ALL SELECT 'CHILD' UNION ALL SELECT 'INFANT'
) passenger
WHERE f.public_id LIKE 'seed-v2-%'
  AND NOT EXISTS (
    SELECT 1 FROM fare_benefit_policy existing
    WHERE existing.flight_fare_id = fare.id
      AND existing.passenger_type = passenger.passenger_type
  );
