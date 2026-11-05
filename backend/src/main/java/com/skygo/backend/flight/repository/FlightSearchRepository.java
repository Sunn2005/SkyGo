package com.skygo.backend.flight.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FlightSearchRepository {
  private final JdbcTemplate jdbc;

  public FlightSearchRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record AirportRow(long id, String code, String city, String timeZone) {}
  public record Criteria(long originId, long destinationId, Instant dayStart, Instant dayEnd,
      Instant now, long requiredSeats, int page, int size) {}
  public record FareRow(String flightId, String flightNumber, Instant departure, Instant arrival,
      String cabinClass, BigDecimal baseFare, BigDecimal vatRate) {}
  public record SearchRows(long total, List<FareRow> fares) {}

  public Optional<AirportRow> activeAirport(String code) {
    return jdbc.query("SELECT id, code, city, time_zone FROM airport WHERE code = ? AND is_active = 1",
        (rs, n) -> new AirportRow(rs.getLong("id"), rs.getString("code"),
            rs.getString("city"), rs.getString("time_zone")), code).stream().findFirst();
  }

  public SearchRows search(Criteria c) {
    String from = """
         FROM flight f JOIN air_route r ON r.id = f.route_id
         JOIN airport o ON o.id = r.origin_airport_id
         JOIN airport d ON d.id = r.destination_airport_id
         WHERE r.origin_airport_id = ? AND r.destination_airport_id = ?
           AND r.is_active = 1 AND o.is_active = 1 AND d.is_active = 1
           AND f.sale_status = 'ON_SALE' AND f.departure_at_utc >= ?
           AND f.departure_at_utc < ? AND f.departure_at_utc > ?
           AND EXISTS (SELECT 1 FROM flight_fare eligible WHERE eligible.flight_id = f.id
             AND eligible.is_on_sale = 1 AND eligible.available_seats >= ?)
        """;
    Object[] filters = {c.originId(), c.destinationId(),
        LocalDateTime.ofInstant(c.dayStart(), ZoneOffset.UTC),
        LocalDateTime.ofInstant(c.dayEnd(), ZoneOffset.UTC),
        LocalDateTime.ofInstant(c.now(), ZoneOffset.UTC), c.requiredSeats()};
    long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, filters);
    if (total == 0) return new SearchRows(0, List.of());

    // Page flights before joining fares so two cabins never split a flight across pages.
    String sql = """
        SELECT selected.public_id, selected.flight_number, selected.departure_at_utc,
          selected.arrival_at_utc, fare.cabin_class, fare.adult_base_fare, policy.vat_rate
        FROM (SELECT f.id, f.public_id, f.flight_number, f.departure_at_utc, f.arrival_at_utc
        """ + from + """
          ORDER BY f.departure_at_utc, f.public_id LIMIT ? OFFSET ?) selected
        JOIN flight_fare fare ON fare.flight_id = selected.id
        JOIN pricing_policy policy ON policy.id = fare.pricing_policy_id
        WHERE fare.is_on_sale = 1 AND fare.available_seats >= ?
        ORDER BY selected.departure_at_utc, selected.public_id,
          CASE fare.cabin_class WHEN 'ECONOMY' THEN 0 ELSE 1 END
        """;
    var args = new java.util.ArrayList<>(List.of(filters));
    args.add(c.size());
    args.add((long) c.page() * c.size());
    args.add(c.requiredSeats());
    return new SearchRows(total, jdbc.query(sql, (rs, n) -> new FareRow(
        rs.getString("public_id"), rs.getString("flight_number"),
        rs.getObject("departure_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
        rs.getObject("arrival_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
        rs.getString("cabin_class"), rs.getBigDecimal("adult_base_fare"),
        rs.getBigDecimal("vat_rate")), args.toArray()));
  }
}
