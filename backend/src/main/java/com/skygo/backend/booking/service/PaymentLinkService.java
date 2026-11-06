package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PaymentLinkResponse.PaymentLink;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.PaymentAttemptRepository.Attempt;
import com.skygo.backend.booking.service.payos.PayosGateway;
import java.security.SecureRandom;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;
import org.springframework.stereotype.Service;

@Service
public class PaymentLinkService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final SeatHoldService holdsService;
  private final SeatHoldRepository holds;
  private final BookingRepository bookings;
  private final BookingOpenRepository details;
  private final PaymentAttemptRepository attempts;
  private final PayosGateway payos;

  public PaymentLinkService(SeatHoldService holdsService, SeatHoldRepository holds,
      BookingRepository bookings, BookingOpenRepository details,
      PaymentAttemptRepository attempts, PayosGateway payos) {
    this.holdsService = holdsService;
    this.holds = holds;
    this.bookings = bookings;
    this.details = details;
    this.attempts = attempts;
    this.payos = payos;
  }

  private record Claim(BookingOpenResponse booking, SeatHold hold, Attempt attempt,
      boolean needsCreation) {}

  public PaymentLinkResponse create(long user, String bookingId) {
    Claim claim = holdsService.withUser(user, () -> {
      var booking = bookings.lock(bookingId, user);
      if (booking.status().equals("PAID") || booking.paymentStatus().equals("SUCCEEDED"))
        throw new BookingException(409, "BOOKING_ALREADY_PAID", "Đơn đã thanh toán.");
      if (booking.paymentStatus().equals("RECONCILIATION_REQUIRED"))
        throw new BookingException(409, "PAYMENT_RECONCILIATION_REQUIRED",
            "Đơn có giao dịch cần đối soát trước khi thanh toán tiếp.");
      if (!booking.status().equals("PENDING_PAYMENT")
          || !booking.paymentStatus().equals("PENDING"))
        throw new BookingException(409, "BOOKING_NOT_PAYABLE", "Đơn chưa sẵn sàng thanh toán.");
      var active = holds.active(user);
      if (active.isEmpty()) throw expired();
      var hold = active.get();
      if (hold.bookingId() == null || hold.bookingId() != booking.id())
        throw holdsService.activeConflict(hold);
      if (hold.expired(holdsService.now())) throw expired();
      if (booking.total() <= 0 || !"VND".equals(booking.currency()))
        throw new BookingException(409, "PAYMENT_AMOUNT_INVALID", "Số tiền thanh toán không hợp lệ.");
      if (hold.expiresAt().getEpochSecond() > Integer.MAX_VALUE)
        throw new BookingException(503, "PAYOS_EXPIRY_UNSUPPORTED",
            "Thời hạn thanh toán không được PayOS hỗ trợ.");

      var existing = attempts.byHold(hold.id());
      if (existing.isPresent()) {
        var attempt = existing.get();
        if (attempt.bookingId() != booking.id() || attempt.amount() != booking.total()
            || !attempt.currency().equals(booking.currency())
            || !attempt.expiresAt().equals(hold.expiresAt()))
          throw new BookingException(409, "PAYMENT_ATTEMPT_MISMATCH",
              "Lượt thanh toán không khớp với đơn đặt vé.");
        if (attempt.status().equals("PENDING")) {
          if (attempt.paymentLinkId() == null || attempt.qrCode() == null
              || attempt.checkoutUrl() == null)
            throw unverified();
          return Outcome.ok(new Claim(details.load(booking.id(), hold), hold, attempt, false));
        }
        if (!attempt.status().equals("FAILED")) throw unverified();
        payos.requireConfigured();
        attempts.retry(attempt.id());
        return Outcome.ok(new Claim(details.load(booking.id(), hold), hold, attempt, true));
      }
      payos.requireConfigured();
      long orderCode = RANDOM.nextLong(1_000_000_000_000L, 9_000_000_000_000_000L);
      attempts.create(booking.id(), hold.id(), orderCode, booking.total(),
          booking.currency(), hold.expiresAt());
      return Outcome.ok(new Claim(details.load(booking.id(), hold), hold,
          attempts.byHold(hold.id()).orElseThrow(), true));
    });
    if (!claim.needsCreation()) return response(claim);

    Attempt attempt = claim.attempt();
    final PayosGateway.Link link;
    try {
      link = payos.create(attempt.orderCode(), attempt.amount(),
          description(attempt.orderCode()), attempt.expiresAt());
    } catch (PayosGateway.Failure ex) {
      if (ex.definitive()) {
        holdsService.withUser(user, () -> {
          bookings.lock(bookingId, user);
          attempts.fail(attempt.id());
          return Outcome.ok(null);
        });
        throw new BookingException(502, "PAYOS_LINK_REJECTED",
            "PayOS từ chối tạo link thanh toán. Vui lòng thử lại.");
      }
      throw new BookingException(503, "PAYMENT_LINK_UNVERIFIED",
          "Chưa xác định được PayOS đã tạo link hay chưa. Cần đối soát trước khi thử lại.");
    }

    return holdsService.withUser(user, () -> {
      var booking = bookings.lock(bookingId, user);
      var current = attempts.byHold(attempt.holdId()).orElseThrow();
      if (current.id() != attempt.id() || !current.status().equals("CREATING"))
        throw unverified();
      attempts.complete(attempt.id(), link.paymentLinkId(), link.checkoutUrl(),
          link.qrCode());
      var active = holds.active(user);
      if (active.isEmpty() || active.get().id() != attempt.holdId()
          || !booking.status().equals("PENDING_PAYMENT")
          || !booking.paymentStatus().equals("PENDING")) {
        attempts.expire(attempt.id());
        return Outcome.failed(expired());
      }
      return Outcome.ok(response(new Claim(details.load(booking.id(), active.get()),
          active.get(), attempts.byHold(attempt.holdId()).orElseThrow(), false)));
    });
  }

  private static PaymentLinkResponse response(Claim claim) {
    var b = claim.booking();
    var a = claim.attempt();
    return new PaymentLinkResponse(b.bookingId(), b.bookingCode(), b.bookingStatus(),
        claim.hold().publicId(), a.expiresAt(), b.tripType(), b.passengerCounts(),
        b.legs(), b.price(), b.currency(),
        new PaymentLink(b.payment().paymentId(), a.paymentLinkId(), a.orderCode(),
            a.status(), a.amount(), a.currency(), description(a.orderCode()),
            a.qrCode(), a.checkoutUrl()));
  }

  private static String description(long orderCode) { return "SKYGO " + orderCode; }
  private static BookingException expired() {
    return new BookingException(409, "SEAT_HOLD_EXPIRED",
        "Lượt giữ chỗ đã hết hạn. Vui lòng mở lại đơn trước khi thanh toán.");
  }
  private static BookingException unverified() {
    return new BookingException(409, "PAYMENT_LINK_UNVERIFIED",
        "Lượt thanh toán cần được đối soát trước khi tạo hoặc hiển thị lại QR.");
  }
}
