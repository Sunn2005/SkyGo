package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.*;
import java.util.*;
import java.time.Instant;
import static com.skygo.backend.booking.repository.SeatHoldRepository.utc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
  private final JdbcTemplate jdbc;
  public BookingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public record Booking(long id, long userId, String publicId, TripType tripType, int adults, int children,
      int infants, String status, String paymentStatus, long total, String currency) {}
  public record BookedLeg(Direction direction, long fareId) {}
  public String publicId(Long id) {
    return id == null ? null : jdbc.queryForObject("SELECT public_id FROM booking WHERE id=?", String.class, id);
  }
  public Booking lock(String publicId, long user) {
    var ids = jdbc.queryForList("SELECT id FROM booking WHERE public_id=? AND user_id=? FOR UPDATE", Long.class, publicId, user);
    if (ids.isEmpty()) throw new BookingException(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé.");
    return lock(ids.getFirst(), user);
  }
  public Booking lock(long id, long user) {
    var rows = jdbc.query("SELECT * FROM booking WHERE id=? AND user_id=? FOR UPDATE", (rs, n) -> new Booking(
        rs.getLong("id"), rs.getLong("user_id"), rs.getString("public_id"), TripType.valueOf(rs.getString("trip_type")),
        rs.getInt("adults"), rs.getInt("children"), rs.getInt("infants"), rs.getString("booking_status"),
        "", rs.getLong("total_amount"), rs.getString("currency")), id, user);
    if (rows.isEmpty()) throw new BookingException(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé.");
    var b = rows.getFirst();
    String payment = jdbc.queryForObject("SELECT status FROM payment WHERE booking_id=? FOR UPDATE", String.class, id);
    return new Booking(b.id(), b.userId(), b.publicId(), b.tripType(), b.adults(), b.children(), b.infants(),
        b.status(), payment, b.total(), b.currency());
  }
  public List<BookedLeg> legs(long id) {
    return jdbc.query("SELECT direction,flight_fare_id FROM booking_leg WHERE booking_id=? ORDER BY direction",
        (rs,n) -> new BookedLeg(Direction.valueOf(rs.getString("direction")), rs.getLong("flight_fare_id")), id);
  }
  public boolean hasPaymentAttempt(long bookingId) {
    return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_attempt WHERE booking_id=?)",
        Integer.class, bookingId) != 0;
  }
  public void expire(long id) {
    jdbc.update("UPDATE booking SET booking_status='AWAITING_REHOLD',updated_at_utc=UTC_TIMESTAMP(6) WHERE id=? AND booking_status='PENDING_PAYMENT'", id);
    jdbc.update("UPDATE payment SET status='EXPIRED',updated_at_utc=UTC_TIMESTAMP(6) WHERE booking_id=? AND status='PENDING'", id);
  }
  public void pending(long id) {
    jdbc.update("UPDATE booking SET booking_status='PENDING_PAYMENT',updated_at_utc=UTC_TIMESTAMP(6) WHERE id=?", id);
    jdbc.update("UPDATE payment SET status='PENDING',updated_at_utc=UTC_TIMESTAMP(6) WHERE booking_id=?", id);
  }
  public void cancel(long id,Instant now) {
    jdbc.update("""
        UPDATE booking SET booking_status='CANCELLED',cancelled_at_utc=?,cancellation_reason='BOOKING_WINDOW_CLOSED',updated_at_utc=?
        WHERE id=? AND booking_status IN ('PENDING_PAYMENT','EXPIRED','AWAITING_REHOLD')
        """,utc(now),utc(now),id);
    jdbc.update("UPDATE payment SET status='EXPIRED',updated_at_utc=? WHERE booking_id=? AND status='PENDING'",utc(now),id);
  }
  public record DueBooking(long id,long userId) {}
  public List<DueBooking> closedUnpaid(Instant now,int limit) {
    return jdbc.query("""
        SELECT b.id,b.user_id FROM booking b JOIN payment p ON p.booking_id=b.id
          JOIN booking_leg l ON l.booking_id=b.id JOIN flight_fare fare ON fare.id=l.flight_fare_id
          JOIN flight f ON f.id=fare.flight_id
        WHERE b.booking_status IN ('PENDING_PAYMENT','EXPIRED','AWAITING_REHOLD')
          AND p.status NOT IN ('SUCCEEDED','RECONCILIATION_REQUIRED')
        GROUP BY b.id,b.user_id HAVING MIN(COALESCE(f.booking_closes_at_utc,f.departure_at_utc))<=?
        ORDER BY b.id LIMIT ?
        """,(rs,n)->new DueBooking(rs.getLong("id"),rs.getLong("user_id")),utc(now),limit);
  }
}
