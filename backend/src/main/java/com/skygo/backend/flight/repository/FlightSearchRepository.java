package com.skygo.backend.flight.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.skygo.backend.flight.contract.NearbyFlightSearchRequest;
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
  public record DayWindow(LocalDate date, Instant start, Instant end) {}
  public record NearbyCriteria(long originId, long destinationId, List<DayWindow> days,
      Instant now, long requiredSeats) {}

  public Map<LocalDate, SearchRows> searchNearby(NearbyCriteria c) {
    if (c.days().isEmpty()) return Map.of();
    var args = new ArrayList<Object>();
    var windows = new ArrayList<String>();
    for (var day : c.days()) {
      windows.add("SELECT CAST(? AS DATE) AS flight_date, CAST(? AS DATETIME(6)) AS day_start, CAST(? AS DATETIME(6)) AS day_end");
      args.add(day.date());
      args.add(LocalDateTime.ofInstant(day.start(), ZoneOffset.UTC));
      args.add(LocalDateTime.ofInstant(day.end(), ZoneOffset.UTC));
    }
    // UTC bounds are computed in Java for each local day, including DST transitions.
    // Rank/count flights before joining fares so each day has its own ten-flight page.
    String sql = "WITH day_windows AS (" + String.join(" UNION ALL ", windows) + """
        ), ranked_flights AS (
          SELECT w.flight_date, f.id, f.public_id, f.flight_number,
            f.departure_at_utc, f.arrival_at_utc,
            COUNT(*) OVER (PARTITION BY w.flight_date) AS total_elements,
            ROW_NUMBER() OVER (PARTITION BY w.flight_date
              ORDER BY f.departure_at_utc, f.public_id) AS flight_rank
          FROM air_route r
          JOIN airport o ON o.id = r.origin_airport_id
          JOIN airport d ON d.id = r.destination_airport_id
          JOIN flight f ON f.route_id = r.id
          JOIN day_windows w ON f.departure_at_utc >= w.day_start AND f.departure_at_utc < w.day_end
          WHERE r.origin_airport_id = ? AND r.destination_airport_id = ?
            AND r.is_active = 1 AND o.is_active = 1 AND d.is_active = 1
            AND f.sale_status = 'ON_SALE' AND f.departure_at_utc >= ?
            AND f.departure_at_utc < ? AND f.departure_at_utc > ?
            AND (SELECT eligible.id FROM flight_fare eligible WHERE eligible.flight_id = f.id
              AND eligible.is_on_sale = 1 AND eligible.available_seats >= ? LIMIT 1) IS NOT NULL
        )
        SELECT selected.flight_date, selected.total_elements, selected.public_id,
          selected.flight_number, selected.departure_at_utc, selected.arrival_at_utc,
          fare.cabin_class, fare.adult_base_fare, policy.vat_rate
        FROM ranked_flights selected
        -- Drive fare lookups from the ranked flights instead of scanning fares by pricing policy.
        STRAIGHT_JOIN flight_fare fare ON fare.flight_id = selected.id
        STRAIGHT_JOIN pricing_policy policy ON policy.id = fare.pricing_policy_id
        WHERE selected.flight_rank <= ? AND fare.is_on_sale = 1 AND fare.available_seats >= ?
        ORDER BY selected.flight_date, selected.departure_at_utc, selected.public_id,
          CASE fare.cabin_class WHEN 'ECONOMY' THEN 0 ELSE 1 END
        """;
    args.add(c.originId());
    args.add(c.destinationId());
    args.add(LocalDateTime.ofInstant(c.days().getFirst().start(), ZoneOffset.UTC));
    args.add(LocalDateTime.ofInstant(c.days().getLast().end(), ZoneOffset.UTC));
    args.add(LocalDateTime.ofInstant(c.now(), ZoneOffset.UTC));
    args.add(c.requiredSeats());
    args.add(NearbyFlightSearchRequest.PAGE_SIZE);
    args.add(c.requiredSeats());
    record DatedFare(LocalDate date, long total, FareRow fare) {}
    var rows = jdbc.query(sql, (rs, n) -> new DatedFare(rs.getObject("flight_date", LocalDate.class),
        rs.getLong("total_elements"), fareRow(rs)), args.toArray());
    var result = new LinkedHashMap<LocalDate, SearchRows>();
    for (var row : rows) {
      result.computeIfAbsent(row.date(), day -> new SearchRows(row.total(), new ArrayList<>()))
          .fares().add(row.fare());
    }
    return result;
  }

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
    return new SearchRows(total, jdbc.query(sql, (rs, n) -> fareRow(rs), args.toArray()));
  }

  private static FareRow fareRow(ResultSet rs) throws SQLException {
    return new FareRow(
        rs.getString("public_id"), rs.getString("flight_number"),
        rs.getObject("departure_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
        rs.getObject("arrival_at_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
        rs.getString("cabin_class"), rs.getBigDecimal("adult_base_fare"),
        rs.getBigDecimal("vat_rate"));
  }
}
