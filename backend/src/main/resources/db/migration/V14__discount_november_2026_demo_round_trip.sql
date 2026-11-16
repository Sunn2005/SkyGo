-- Affordable test payment for the fixed 23/25 November 2026 round trip.
-- Outbound: SGN -> PQC on 23 November at 09:00 local time.
-- Return:   PQC -> SGN on 25 November at 16:30 local time.
-- Economy displays as 10,000 VND per leg including 10% VAT.
-- Both cabins on each flight must share a policy and obey its price formula,
-- so base fares are 9,091 VND economy and 22,728 VND business.
-- One adult, no extra baggage: the round trip totals 20,000 VND after VAT.
-- All other flights keep V2 prices.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

SET @demo_discount_policy_version =
  (SELECT COALESCE(MAX(version), 0) + 1 FROM pricing_policy);

INSERT INTO pricing_policy
  (version, economy_fixed_vnd, economy_per_km_vnd, business_multiplier,
   vat_rate, effective_from_utc)
VALUES (@demo_discount_policy_version, 9091, 0, 2.5, 0.1, UTC_TIMESTAMP(6));

SET @demo_discount_policy_id = LAST_INSERT_ID();

UPDATE flight_fare fare
JOIN flight flight ON flight.id = fare.flight_id
SET fare.pricing_policy_id = @demo_discount_policy_id,
    fare.adult_base_fare = CASE fare.cabin_class
      WHEN 'ECONOMY' THEN 9091
      WHEN 'BUSINESS' THEN 22728
    END,
    fare.updated_at_utc = UTC_TIMESTAMP(6)
WHERE flight.public_id IN (
    'demo-202611-sgn-pqc-23-2',
    'demo-202611-pqc-sgn-25-1'
  )
  AND fare.cabin_class IN ('ECONOMY', 'BUSINESS');
