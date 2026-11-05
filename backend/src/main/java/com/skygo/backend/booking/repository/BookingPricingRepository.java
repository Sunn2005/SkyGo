package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.model.BookingQuote.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;

@Repository
public class BookingPricingRepository {
  private final JdbcTemplate jdbc;
  public BookingPricingRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record FareData(long fareId,long flightDbId,String flightId,String flightNumber,String cabin,
      Airport origin,Airport destination,Instant departure,Instant arrival,long policyId,int policyVersion,
      long distance,long routeDistance,BigDecimal baseFare,BigDecimal fixedFare,BigDecimal perKm,
      BigDecimal businessMultiplier,BigDecimal vatRate,String currency) {}
  public record Sibling(String cabin,long policyId,long distance,BigDecimal baseFare) {}

  public List<Long> allCabinIds(List<Long> selected) {
    String placeholders=String.join(",",Collections.nCopies(selected.size(),"?"));
    return jdbc.queryForList("SELECT DISTINCT sibling.id FROM flight_fare selected JOIN flight_fare sibling ON sibling.flight_id=selected.flight_id WHERE selected.id IN ("+placeholders+")",Long.class,selected.toArray());
  }
  public FareData fare(long id) {
    return jdbc.query("""
        SELECT fare.id,f.id AS flight_db_id,f.public_id,f.flight_number,fare.cabin_class,
          o.code AS o_code,o.name AS o_name,o.city AS o_city,o.country_code AS o_country,o.time_zone AS o_zone,
          d.code AS d_code,d.name AS d_name,d.city AS d_city,d.country_code AS d_country,d.time_zone AS d_zone,
          f.departure_at_utc,f.arrival_at_utc,p.id AS policy_id,p.version,fare.priced_distance_km,
          r.air_distance_km,fare.adult_base_fare,p.economy_fixed_vnd,p.economy_per_km_vnd,
          p.business_multiplier,p.vat_rate,fare.currency
        FROM flight_fare fare JOIN flight f ON f.id=fare.flight_id JOIN air_route r ON r.id=f.route_id
          JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id
          JOIN pricing_policy p ON p.id=fare.pricing_policy_id WHERE fare.id=? LOCK IN SHARE MODE
        """,(rs,n)->new FareData(rs.getLong("id"),rs.getLong("flight_db_id"),rs.getString("public_id"),
        rs.getString("flight_number"),rs.getString("cabin_class"),
        new Airport(rs.getString("o_code"),rs.getString("o_name"),rs.getString("o_city"),rs.getString("o_country"),rs.getString("o_zone")),
        new Airport(rs.getString("d_code"),rs.getString("d_name"),rs.getString("d_city"),rs.getString("d_country"),rs.getString("d_zone")),
        instant(rs,"departure_at_utc"),instant(rs,"arrival_at_utc"),rs.getLong("policy_id"),rs.getInt("version"),
        rs.getLong("priced_distance_km"),rs.getLong("air_distance_km"),rs.getBigDecimal("adult_base_fare"),
        rs.getBigDecimal("economy_fixed_vnd"),rs.getBigDecimal("economy_per_km_vnd"),
        rs.getBigDecimal("business_multiplier"),rs.getBigDecimal("vat_rate"),rs.getString("currency")),id).getFirst();
  }
  public List<Sibling> siblings(long flight) {
    return jdbc.query("SELECT cabin_class,pricing_policy_id,priced_distance_km,adult_base_fare FROM flight_fare WHERE flight_id=? ORDER BY id LOCK IN SHARE MODE",
        (rs,n)->new Sibling(rs.getString("cabin_class"),rs.getLong("pricing_policy_id"),rs.getLong("priced_distance_km"),rs.getBigDecimal("adult_base_fare")),flight);
  }
  public Optional<BigDecimal> multiplier(String scope,PassengerType type) {
    return jdbc.query("SELECT multiplier FROM passenger_fare_rule WHERE route_scope=? AND passenger_type=? AND is_active=1 LOCK IN SHARE MODE",
        (rs,n)->rs.getBigDecimal("multiplier"),scope,type.name()).stream().findFirst();
  }
  public Optional<Benefit> benefit(long fare,PassengerType type) {
    return jdbc.query("SELECT * FROM fare_benefit_policy WHERE flight_fare_id=? AND passenger_type=? LOCK IN SHARE MODE",
        (rs,n)->new Benefit(type,rs.getInt("carry_on_max_kg"),rs.getInt("checked_pieces"),
            rs.getInt("checked_piece_max_kg"),rs.getInt("included_meals_per_leg"),rs.getString("seat_type"),
            rs.getInt("stroller_pieces"),rs.getInt("stroller_max_kg")),fare,type.name()).stream().findFirst();
  }
}
