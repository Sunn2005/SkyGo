package com.skygo.backend.booking.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.*;

@Repository
public class PaymentAttemptRepository {
  private final JdbcTemplate jdbc;
  public PaymentAttemptRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Attempt(long id, long bookingId, long holdId, long orderCode,
      String status, long amount, String currency, Instant expiresAt,
      String paymentLinkId, String checkoutUrl, String qrCode) {}

  public Optional<Attempt> byHold(long holdId) {
    return jdbc.query("SELECT * FROM payment_attempt WHERE seat_hold_id=? FOR UPDATE",
        (rs, n) -> new Attempt(rs.getLong("id"), rs.getLong("booking_id"),
            rs.getLong("seat_hold_id"), rs.getLong("order_code"), rs.getString("status"),
            rs.getLong("amount"), rs.getString("currency"), instant(rs, "expires_at_utc"),
            rs.getString("payment_link_id"), rs.getString("checkout_url"),
            rs.getString("qr_code")), holdId).stream().findFirst();
  }

  public void create(long bookingId, long holdId, long orderCode, long amount,
      String currency, Instant expiresAt) {
    jdbc.update("""
        INSERT INTO payment_attempt(payment_id,booking_id,seat_hold_id,order_code,status,
          amount,currency,expires_at_utc)
        SELECT p.id,?,?,?,?,?,?,? FROM payment p WHERE p.booking_id=?
        """, bookingId, holdId, orderCode, "CREATING", amount, currency,
        utc(expiresAt), bookingId);
    jdbc.update("UPDATE payment SET method='PAYOS',updated_at_utc=UTC_TIMESTAMP(6) WHERE booking_id=?",
        bookingId);
  }

  public void retry(long attemptId) {
    jdbc.update("UPDATE payment_attempt SET status='CREATING',updated_at_utc=UTC_TIMESTAMP(6) "
        + "WHERE id=? AND status='FAILED'", attemptId);
  }

  public void complete(long attemptId, String linkId, String checkoutUrl, String qrCode) {
    jdbc.update("""
        UPDATE payment_attempt SET payment_link_id=?,checkout_url=?,qr_code=?,
          status='PENDING',updated_at_utc=UTC_TIMESTAMP(6)
        WHERE id=? AND status='CREATING'
        """, linkId, checkoutUrl, qrCode, attemptId);
  }

  public void fail(long attemptId) {
    jdbc.update("UPDATE payment_attempt SET status='FAILED',updated_at_utc=UTC_TIMESTAMP(6) "
        + "WHERE id=? AND status='CREATING'", attemptId);
  }

  public void expire(long attemptId) {
    jdbc.update("UPDATE payment_attempt SET status='EXPIRED',updated_at_utc=UTC_TIMESTAMP(6) "
        + "WHERE id=? AND status='PENDING'", attemptId);
  }
}
