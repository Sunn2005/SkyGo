package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.BookingOpenResponse;
import com.skygo.backend.booking.contract.ResumePaymentResponse;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;
import com.skygo.backend.booking.repository.PaymentAttemptRepository.Attempt;
import com.skygo.backend.booking.service.ExpiredPaymentVerifier.Evidence;
import com.skygo.backend.booking.service.payos.PayosWebhookVerifier.Transfer;
import java.util.function.Function;

@Service
public class ResumePaymentService {
  private final SeatHoldService service;
  private final SeatHoldRepository holds;
  private final BookingRepository bookings;
  private final FlightInventoryRepository inventory;
  private final SeatHoldExpiryService expiry;
  private final BookingOpenRepository details;
  private final PaymentAttemptRepository attempts;
  private final PaymentWebhookRepository payments;
  private final ExpiredPaymentVerifier verifier;

  public ResumePaymentService(SeatHoldService service, SeatHoldRepository holds, BookingRepository bookings,
      FlightInventoryRepository inventory, SeatHoldExpiryService expiry, BookingOpenRepository details,
      PaymentAttemptRepository attempts, PaymentWebhookRepository payments, ExpiredPaymentVerifier verifier) {
    this.service = service;
    this.holds = holds;
    this.bookings = bookings;
    this.inventory = inventory;
    this.expiry = expiry;
    this.details = details;
    this.attempts=attempts;
    this.payments=payments;
    this.verifier=verifier;
  }

  private record Prepared(BookingRepository.Booking booking, SeatHold hold) {}

  public BookingOpenResponse open(long user, String bookingId) {
    return execute(user,bookingId,true,p -> details.load(p.booking().id(),p.hold()));
  }

  public ResumePaymentResponse resume(long user, String bookingId) {
    return execute(user,bookingId,false,p -> {
      var b=p.booking(); var h=p.hold();
      return new ResumePaymentResponse(b.publicId(), "PENDING_PAYMENT", "PENDING",
          h.publicId(), h.expiresAt(), b.total(), b.currency());
    });
  }

  private static class NeedsVerification extends RuntimeException {
    final List<Attempt> attempts;
    NeedsVerification(List<Attempt> attempts) { this.attempts=attempts; }
  }

  private <T> T execute(long user,String bookingId,boolean allowClosed,Function<Prepared,T> response) {
    Map<Long,Evidence> verified=Map.of();
    for(int retry=0;retry<3;retry++) {
      final var evidence=verified;
      try {
        return service.withUser(user,()->{
          var prepared=prepare(user,bookingId,allowClosed,evidence);
          if(prepared.retry()) return Outcome.again();
          if(prepared.error()!=null) return Outcome.failed(prepared.error());
          return Outcome.ok(response.apply(prepared.value()));
        });
      } catch(NeedsVerification needed) { verified=verifier.verify(needed.attempts); }
    }
    throw ExpiredPaymentVerifier.unverified();
  }

  private Outcome<Prepared> prepare(long user, String bookingId, boolean allowClosed,
      Map<Long,Evidence> verified) {
      var active = holds.active(user);
      var b = bookings.lock(bookingId, user);
      if (b.status().equals("PAID") || b.paymentStatus().equals("SUCCEEDED")) {
        if (allowClosed) return Outcome.ok(new Prepared(b, null));
        throw new BookingException(409, "BOOKING_ALREADY_PAID", "Đơn đã thanh toán.");
      }
      if (b.paymentStatus().equals("RECONCILIATION_REQUIRED")) {
        if (allowClosed) return Outcome.ok(new Prepared(b, null));
        throw new BookingException(409, "PAYMENT_RECONCILIATION_REQUIRED",
            "Đơn có giao dịch cần đối soát trước khi tiếp tục thanh toán.");
      }
      if (active.isPresent()) {
        var h = active.get();
        if (h.bookingId() == null || h.bookingId() != b.id()) throw service.activeConflict(h);
        if (expiry.expireLocked(h)) return Outcome.again();
        if (!b.status().equals("PENDING_PAYMENT") || !b.paymentStatus().equals("PENDING"))
          throw new BookingException(409, "BOOKING_NOT_RESUMABLE", "Trạng thái đơn không phù hợp với lượt giữ chỗ.");
        return Outcome.ok(new Prepared(b, h));
      }
      if (!b.status().equals("EXPIRED") || !b.paymentStatus().equals("EXPIRED"))
        throw new BookingException(409, "BOOKING_NOT_RESUMABLE", "Đơn không ở trạng thái có thể giữ lại chỗ.");
      var oldAttempts=attempts.forBooking(b.id());
      if(oldAttempts.stream().anyMatch(a -> !verified.containsKey(a.id())
          || !verified.get(a.id()).expected().equals(a)))
        throw new NeedsVerification(oldAttempts);
      boolean hasMoney=false;
      for(var a:oldAttempts) {
        var evidence=verified.get(a.id());
        var status=evidence.status();
        boolean localMoney=payments.hasTransfers(a.id());
        hasMoney |= evidence.hasMoney() || localMoney;
        attempts.verifiedStatus(a.id(),status.paymentLinkId(),
            status.amountPaid()>=a.amount() ? "PAID" : evidence.hasMoney() ? a.status() : status.status());
        for(var t:status.transactions()) {
          var recorded=payments.reference(t.reference());
          if(recorded.isPresent()) {
            var r=recorded.get();
            if(r.attemptId()!=a.id() || r.amount()!=t.amount()
                || !r.currency().equals(t.currency()) || !status.paymentLinkId().equals(r.paymentLinkId()))
              throw ExpiredPaymentVerifier.unverified();
          } else payments.record(a.id(),new Transfer(a.orderCode(),status.paymentLinkId(),
              t.reference(),t.amount(),t.currency(),t.transactionDateTime(),true));
        }
        if(evidence.hasMoney() || localMoney) payments.reconcilePending(a.id(),"EXPIRED_HOLD_PROVIDER_PAYMENT");
      }
      if(hasMoney) {
        payments.reconcilePayment(b.id());
        if(allowClosed) return Outcome.ok(new Prepared(bookings.lock(b.id(),user),null));
        return Outcome.failed(new BookingException(409,"PAYMENT_RECONCILIATION_REQUIRED",
            "Link thanh toán cũ đã có giao dịch cần đối soát; không giữ chỗ hoặc yêu cầu thanh toán thêm."));
      }
      var legs = bookings.legs(b.id());
      if (legs.size() != (b.tripType() == TripType.ONE_WAY ? 1 : 2)
          || legs.stream().noneMatch(l -> l.direction() == Direction.OUTBOUND))
        throw new BookingException(409, "HOLD_MISMATCH", "Hành trình của đơn không đầy đủ.");
      inventory.lock(legs.stream().map(BookingRepository.BookedLeg::fareId).toList());
      var selected = legs.stream().map(leg -> inventory.reservationFare(leg.fareId())).toList();
      var now = service.now();
      for (var fare : selected) inventory.requireAvailable(fare, now);
      for (var leg : legs) inventory.reserve(leg.fareId(), (long) b.adults() + b.children());
      var h = holds.create(user, b.id(), b.tripType(), b.adults(), b.children(), b.infants(), now);
      for (var leg : legs) holds.leg(h.id(), leg.direction(), leg.fareId(), h.seats());
      bookings.pending(b.id());
      return Outcome.ok(new Prepared(b, h));
  }
}
