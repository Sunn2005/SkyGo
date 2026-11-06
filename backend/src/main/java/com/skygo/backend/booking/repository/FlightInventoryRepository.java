package com.skygo.backend.booking.repository;

import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;
import com.skygo.backend.booking.exception.BookingException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FlightInventoryRepository {
  private final JdbcTemplate jdbc;
  public FlightInventoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public record Fare(long id, String flightId, String cabin, String origin, String originCity,
      String originZone, String destination, String destinationCity, String destinationZone,
      Instant departure, Instant arrival, boolean onSale) {
    public LocalDate localDate() { return departure.atZone(ZoneId.of(originZone)).toLocalDate(); }
  }
  private static final String SELECT = """
      SELECT fare.id,fare.cabin_class,f.public_id,o.code AS origin,o.city AS origin_city,
        o.time_zone AS origin_zone,d.code AS destination,d.city AS destination_city,
        d.time_zone AS destination_zone,f.departure_at_utc,f.arrival_at_utc,
        (fare.is_on_sale AND f.sale_status='ON_SALE' AND r.is_active AND o.is_active AND d.is_active) AS on_sale
      FROM flight_fare fare JOIN flight f ON f.id=fare.flight_id JOIN air_route r ON r.id=f.route_id
        JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id
      """;
  public Optional<Fare> find(String flightId, String cabin) {
    return query(" WHERE f.public_id=? AND fare.cabin_class=?", flightId, cabin).stream().findFirst();
  }
  public Fare fare(long id) { return query(" WHERE fare.id=?", id).getFirst(); }
  // Called only after all fare locks have been acquired in ascending ID order.
  // Shared catalog locks keep flight/route/airport sale metadata stable until commit.
  public Fare reservationFare(long id) { return query(" WHERE fare.id=? LOCK IN SHARE MODE", id).getFirst(); }
  private List<Fare> query(String where, Object... args) {
    return jdbc.query(SELECT + where, (rs, n) -> new Fare(rs.getLong("id"), rs.getString("public_id"),
        rs.getString("cabin_class"), rs.getString("origin"), rs.getString("origin_city"),
        rs.getString("origin_zone"), rs.getString("destination"), rs.getString("destination_city"),
        rs.getString("destination_zone"), instant(rs, "departure_at_utc"),
        instant(rs, "arrival_at_utc"), rs.getBoolean("on_sale")), args);
  }
  public void lock(Collection<Long> ids) {
    for (long id : new TreeSet<>(ids))
      jdbc.queryForObject("SELECT id FROM flight_fare WHERE id=? FOR UPDATE", Long.class, id);
  }
  public void requireAvailable(Fare fare, Instant now) {
    if (!fare.onSale() || !fare.departure().isAfter(now))
      throw new BookingException(409, "FLIGHT_UNAVAILABLE", "Chuyến bay hoặc hạng vé không còn mở bán.");
  }
  public void reserve(long fare, long seats) {
    if (seats == 0) return;
    if (jdbc.update("""
        UPDATE flight_fare SET available_seats=available_seats-?,updated_at_utc=UTC_TIMESTAMP(6)
        WHERE id=? AND is_on_sale=1 AND available_seats>=?
        """, seats, fare, seats) != 1)
      throw new BookingException(409, "INSUFFICIENT_CAPACITY", "Không còn đủ chỗ. Vui lòng chọn chuyến hoặc hạng vé khác.");
  }
  public void release(long fare, long seats) {
    if (seats == 0) return;
    if (jdbc.update("""
        UPDATE flight_fare SET available_seats=available_seats+?,updated_at_utc=UTC_TIMESTAMP(6)
        WHERE id=? AND available_seats+?<=capacity
        """, seats, fare, seats) != 1)
      throw new IllegalStateException("Seat inventory exceeds configured capacity for fare " + fare);
  }
}
