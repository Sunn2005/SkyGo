package com.skygo.backend.booking.service;

import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.service.payos.PayosWebhookVerifier;
import com.skygo.backend.booking.service.payos.PayosWebhookVerifier.Transfer;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class PaymentWebhookService {
  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
  private final PayosWebhookVerifier verifier;
  private final PaymentWebhookRepository records;
  private final PaymentAttemptRepository attempts;
  private final SeatHoldService holdsService;
  private final SeatHoldRepository holds;
  private final BookingRepository bookings;
  private final Clock clock;

  public PaymentWebhookService(PayosWebhookVerifier verifier, PaymentWebhookRepository records,
      PaymentAttemptRepository attempts, SeatHoldService holdsService,
      SeatHoldRepository holds, BookingRepository bookings, Clock clock) {
    this.verifier = verifier;
    this.records = records;
    this.attempts = attempts;
    this.holdsService = holdsService;
    this.holds = holds;
    this.bookings = bookings;
    this.clock = clock;
  }

  public void receive(JsonNode payload) {
    Transfer transfer = verifier.verify(payload);
    if (!transfer.successful()) return;
    var target = records.target(transfer.orderCode());
    // PayOS sends a signed sample when registering a webhook URL.
    if (target.isEmpty()) {
      log.info("PayOS webhook with unknown orderCode={}", transfer.orderCode());
      return;
    }
    var linked = target.get();
    holdsService.withUser(linked.userId(), () -> {
      SeatHold hold = holds.byId(linked.holdId()).orElseThrow();
      var booking = bookings.lock(linked.bookingId(), linked.userId());
      var attempt = attempts.byHold(hold.id()).orElseThrow();
      if (hold.bookingId() == null || hold.bookingId() != booking.id()
          || attempt.id() <= 0 || attempt.bookingId() != booking.id()
          || attempt.orderCode() != transfer.orderCode())
        throw new IllegalStateException("PayOS order mapping changed");

      var existing = records.reference(transfer.reference());
      if (existing.isPresent()) {
        var old = existing.get();
        if (old.attemptId() != attempt.id() || old.amount() != transfer.amount()
            || !transfer.paymentLinkId().equals(old.paymentLinkId())
            || !old.currency().equals(transfer.currency()))
          throw new IllegalStateException("PayOS reference reused with different transfer");
        return Outcome.ok(null);
      }
      // A bank notification can arrive before create-link persists the provider ID.
      // Roll back and ask PayOS to retry after that request has finished.
      if (attempt.paymentLinkId() == null && "CREATING".equals(attempt.status()))
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "Payment link is still being persisted");
      records.record(attempt.id(), transfer);
      var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
      if (!transfer.paymentLinkId().equals(attempt.paymentLinkId())
          || !transfer.currency().equals(attempt.currency())
          || !transfer.currency().equals(booking.currency())) {
        records.reconcileTransfer(transfer.reference(), "LINK_OR_CURRENCY_MISMATCH");
        records.reconcilePayment(booking.id());
      } else if ("PAID".equals(booking.status()) || "SUCCEEDED".equals(booking.paymentStatus())) {
        records.reconcileTransfer(transfer.reference(), "ADDITIONAL_TRANSFER");
      } else if (!"ACTIVE".equals(hold.status()) || hold.expired(now)
          || !"PENDING_PAYMENT".equals(booking.status())
          || !"PENDING".equals(attempt.status())
          || attempt.amount() != booking.total() || !now.isBefore(attempt.expiresAt())) {
        records.reconcilePending(attempt.id(), "BOOKING_OR_HOLD_NOT_PAYABLE");
        records.reconcilePayment(booking.id());
      } else {
        long received = records.total(attempt.id());
        if (records.hasReconciliation(attempt.id())) {
          records.reconcilePending(attempt.id(), "PRIOR_RECONCILIATION");
          records.reconcilePayment(booking.id());
        } else if (received == attempt.amount()) {
          records.apply(booking.id(), hold.id(), attempt.id(), transfer.reference(), now);
        } else if (received > attempt.amount()) {
          records.reconcilePending(attempt.id(), "OVERPAYMENT");
          records.reconcilePayment(booking.id());
        } else {
          records.reconcilePayment(booking.id());
        }
      }
      return Outcome.ok(null);
    });
  }
}
