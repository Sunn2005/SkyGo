package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.contract.BookingDetailResponse.Invoice;
import com.skygo.backend.booking.exception.BookingException;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;

@Repository
public class BookingDetailRepository {
  private final JdbcTemplate jdbc;
  public BookingDetailRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public long ownedId(String publicId, long user) {
    var ids = jdbc.queryForList("SELECT id FROM booking WHERE public_id=? AND user_id=?",
        Long.class, publicId, user);
    if (ids.isEmpty()) throw new BookingException(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé.");
    return ids.getFirst();
  }

  public record Hold(String publicId, String status, Instant expiresAt) {}
  public Hold latestHold(long bookingId) {
    return jdbc.query("""
        SELECT public_id,status,expires_at_utc FROM seat_hold
        WHERE booking_id=? ORDER BY id DESC LIMIT 1
        """, (rs,n) -> new Hold(rs.getString("public_id"),rs.getString("status"),
        instant(rs,"expires_at_utc")),bookingId).stream().findFirst().orElse(null);
  }

  public Invoice invoice(long bookingId) {
    return jdbc.query("SELECT public_id,issued_at_utc FROM invoice WHERE booking_id=?",
        (rs,n) -> new Invoice(rs.getString("public_id"),instant(rs,"issued_at_utc")),
        bookingId).stream().findFirst().orElse(null);
  }
}
