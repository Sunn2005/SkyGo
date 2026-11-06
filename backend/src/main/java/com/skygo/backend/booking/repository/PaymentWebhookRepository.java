package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.service.payos.PayosWebhookVerifier.Transfer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.utc;

@Repository
public class PaymentWebhookRepository {
  private final JdbcTemplate jdbc;
  public PaymentWebhookRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Target(long userId, long holdId, long bookingId) {}
  public Optional<Target> target(long orderCode) {
    return jdbc.query("""
        SELECT h.user_id, a.seat_hold_id, a.booking_id FROM payment_attempt a
        JOIN seat_hold h ON h.id=a.seat_hold_id WHERE a.order_code=?
        """, (rs, i) -> new Target(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
        orderCode).stream().findFirst();
  }

  public record Recorded(long attemptId, String paymentLinkId, long amount, String currency) {}
  public Optional<Recorded> reference(String reference) {
    return jdbc.query("""
        SELECT payment_attempt_id,payment_link_id,amount,currency FROM payment_transaction
        WHERE provider_reference=?
        """, (rs, i) -> new Recorded(rs.getLong(1), rs.getString(2),
            rs.getLong(3), rs.getString(4)),
        reference).stream().findFirst();
  }

  public void record(long attemptId, Transfer transfer) {
    jdbc.update("""
        INSERT INTO payment_transaction(payment_attempt_id,payment_link_id,provider_reference,
          amount,currency,provider_transaction_at) VALUES (?,?,?,?,?,?)
        """, attemptId, transfer.paymentLinkId(), transfer.reference(), transfer.amount(), transfer.currency(),
        transfer.transactionDateTime().isBlank() ? null : transfer.transactionDateTime());
  }

  public long total(long attemptId) {
    return jdbc.queryForObject("""
        SELECT COALESCE(SUM(amount),0) FROM payment_transaction
        WHERE payment_attempt_id=? AND handling_status='PENDING'
        """, Long.class, attemptId);
  }

  public boolean hasReconciliation(long attemptId) {
    return jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM payment_transaction
          WHERE payment_attempt_id=? AND handling_status='RECONCILIATION_REQUIRED')
        """, Integer.class, attemptId) != 0;
  }

  public void reconcileTransfer(String reference, String reason) {
    jdbc.update("""
        UPDATE payment_transaction SET handling_status='RECONCILIATION_REQUIRED',
          handling_reason=? WHERE provider_reference=?
        """, reason, reference);
  }

  public void reconcilePending(long attemptId, String reason) {
    jdbc.update("""
        UPDATE payment_transaction SET handling_status='RECONCILIATION_REQUIRED',
          handling_reason=? WHERE payment_attempt_id=? AND handling_status='PENDING'
        """, reason, attemptId);
  }

  public void reconcilePayment(long bookingId) {
    jdbc.update("""
        UPDATE payment SET status='RECONCILIATION_REQUIRED',updated_at_utc=UTC_TIMESTAMP(6)
        WHERE booking_id=? AND status<>'SUCCEEDED'
        """, bookingId);
  }

  public void apply(long bookingId, long holdId, long attemptId, String reference,
      Instant now) {
    if (jdbc.update("""
        UPDATE seat_hold SET status='CONSUMED',updated_at_utc=?
        WHERE id=? AND status='ACTIVE' AND expires_at_utc>?
        """, utc(now), holdId, utc(now)) != 1)
      throw new IllegalStateException("Hold changed while applying payment");
    if (jdbc.update("""
        UPDATE booking SET booking_status='PAID',updated_at_utc=?
        WHERE id=? AND booking_status='PENDING_PAYMENT'
        """, utc(now), bookingId) != 1)
      throw new IllegalStateException("Booking changed while applying payment");
    if (jdbc.update("""
        UPDATE payment SET status='SUCCEEDED',transaction_code=?,paid_at_utc=?,
          updated_at_utc=? WHERE booking_id=? AND status IN ('PENDING','RECONCILIATION_REQUIRED')
        """, reference.length() <= 64 ? reference : null, utc(now), utc(now), bookingId) != 1)
      throw new IllegalStateException("Payment changed while applying payment");
    if (jdbc.update("""
        UPDATE payment_attempt SET status='PAID',updated_at_utc=?
        WHERE id=? AND status='PENDING'
        """, utc(now), attemptId) != 1)
      throw new IllegalStateException("Attempt changed while applying payment");
    jdbc.update("""
        UPDATE payment_transaction SET handling_status='APPLIED'
        WHERE payment_attempt_id=? AND handling_status='PENDING'
        """, attemptId);
    var passengers = jdbc.queryForList("SELECT id FROM booking_passenger WHERE booking_id=?",
        Long.class, bookingId);
    if (passengers.isEmpty()) throw new IllegalStateException("Booking has no passengers");
    for (long passenger : passengers)
      jdbc.update("""
          INSERT INTO ticket(ticket_number,booking_id,booking_passenger_id,issued_at_utc)
          VALUES (?,?,?,?)
          """, "SKYGO-" + bookingId + "-" + passenger, bookingId, passenger, utc(now));
    jdbc.update("""
        INSERT INTO invoice(public_id,booking_id,issued_at_utc) VALUES (?,?,?)
        """, UUID.randomUUID().toString(), bookingId, utc(now));
  }
}
