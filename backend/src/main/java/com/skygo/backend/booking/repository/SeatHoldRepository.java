package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.model.SeatHold.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeatHoldRepository {
  private final JdbcTemplate jdbc;
  public SeatHoldRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
  public static Instant instant(ResultSet rs, String field) throws SQLException {
    return rs.getObject(field, LocalDateTime.class).toInstant(ZoneOffset.UTC);
  }
  public void lockUser(long user) {
    jdbc.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", Long.class, user);
  }
  public Optional<SeatHold> active(long user) {
    return load("SELECT * FROM seat_hold WHERE active_user_id = ? FOR UPDATE", user);
  }
  public Optional<SeatHold> owned(String publicId, long user) {
    return load("SELECT * FROM seat_hold WHERE public_id = ? AND user_id = ? FOR UPDATE", publicId, user);
  }
  public Optional<SeatHold> byId(long id) {
    return load("SELECT * FROM seat_hold WHERE id = ? FOR UPDATE", id);
  }
  private Optional<SeatHold> load(String sql, Object... args) {
    var holds = jdbc.query(sql, (rs, n) -> new SeatHold(rs.getLong("id"), rs.getString("public_id"),
        rs.getLong("user_id"), rs.getObject("booking_id", Long.class),
        TripType.valueOf(rs.getString("trip_type")), rs.getInt("adults"), rs.getInt("children"),
        rs.getInt("infants"), rs.getString("status"), instant(rs, "started_at_utc"),
        instant(rs, "expires_at_utc"), List.of()), args);
    if (holds.isEmpty()) return Optional.empty();
    var h = holds.getFirst();
    var legs = jdbc.query("SELECT direction, flight_fare_id, reserved_seats FROM seat_hold_leg WHERE seat_hold_id = ? ORDER BY direction",
        (rs, n) -> new Leg(Direction.valueOf(rs.getString("direction")),
            rs.getLong("flight_fare_id"), rs.getLong("reserved_seats")), h.id());
    return Optional.of(new SeatHold(h.id(), h.publicId(), h.userId(), h.bookingId(), h.tripType(),
        h.adults(), h.children(), h.infants(), h.status(), h.startedAt(), h.expiresAt(), legs));
  }
  public SeatHold create(long user, Long booking, TripType type, int adults, int children, int infants, Instant now) {
    String id = UUID.randomUUID().toString();
    jdbc.update("""
        INSERT INTO seat_hold (public_id,user_id,booking_id,trip_type,adults,children,infants,
          status,started_at_utc,expires_at_utc) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)
        """, id, user, booking, type.name(), adults, children, infants, utc(now), utc(now.plusSeconds(900)));
    return owned(id, user).orElseThrow();
  }
  public void leg(long hold, Direction direction, long fare, long seats) {
    jdbc.update("""
        INSERT INTO seat_hold_leg (seat_hold_id,direction,flight_fare_id,reserved_seats)
        VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE flight_fare_id=VALUES(flight_fare_id),
          reserved_seats=VALUES(reserved_seats),updated_at_utc=UTC_TIMESTAMP(6)
        """, hold, direction.name(), fare, seats);
  }
  public void counts(long hold, int adults, int children, int infants) {
    jdbc.update("UPDATE seat_hold SET adults=?,children=?,infants=?,updated_at_utc=UTC_TIMESTAMP(6) WHERE id=?",
        adults, children, infants, hold);
  }
  public void invalidateReviews(long hold, Instant now) {
    jdbc.update("""
        UPDATE booking_review SET invalidated_at_utc=?,updated_at_utc=?
        WHERE seat_hold_id=? AND used_at_utc IS NULL AND invalidated_at_utc IS NULL
        """, utc(now), utc(now), hold);
  }
  public boolean attachBooking(long hold,long user,long booking,Instant now) {
    return jdbc.update("""
        UPDATE seat_hold SET booking_id=?,updated_at_utc=?
        WHERE id=? AND user_id=? AND booking_id IS NULL AND status='ACTIVE' AND expires_at_utc>?
        """,booking,utc(now),hold,user,utc(now))==1;
  }
  public boolean expire(long hold, Instant now) {
    return jdbc.update("""
        UPDATE seat_hold SET status='EXPIRED',released_at_utc=?,updated_at_utc=?
        WHERE id=? AND status='ACTIVE' AND expires_at_utc<=?
        """, utc(now), utc(now), hold, utc(now)) == 1;
  }
  public record Due(long id, long userId) {}
  public List<Due> due(Instant now, int limit) {
    return jdbc.query("SELECT id,user_id FROM seat_hold WHERE status='ACTIVE' AND expires_at_utc<=? ORDER BY expires_at_utc,id LIMIT ?",
        (rs, n) -> new Due(rs.getLong("id"), rs.getLong("user_id")), utc(now), limit);
  }
}
