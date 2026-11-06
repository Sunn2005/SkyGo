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

@Service
public class ResumePaymentService {
  private final SeatHoldService service;
  private final SeatHoldRepository holds;
  private final BookingRepository bookings;
  private final FlightInventoryRepository inventory;
  private final SeatHoldExpiryService expiry;
  private final BookingOpenRepository details;

  public ResumePaymentService(SeatHoldService service, SeatHoldRepository holds, BookingRepository bookings,
      FlightInventoryRepository inventory, SeatHoldExpiryService expiry, BookingOpenRepository details) {
    this.service = service;
    this.holds = holds;
    this.bookings = bookings;
    this.inventory = inventory;
    this.expiry = expiry;
    this.details = details;
  }

  private record Prepared(BookingRepository.Booking booking, SeatHold hold) {}

  public BookingOpenResponse open(long user, String bookingId) {
    return service.withUser(user, () -> {
      var prepared = prepare(user, bookingId, true);
      if (prepared.retry()) return Outcome.again();
      return Outcome.ok(details.load(prepared.value().booking().id(), prepared.value().hold()));
    });
  }

  public ResumePaymentResponse resume(long user, String bookingId) {
    return service.withUser(user, () -> {
      var prepared = prepare(user, bookingId, false);
      if (prepared.retry()) return Outcome.again();
      var b = prepared.value().booking();
      var h = prepared.value().hold();
      return Outcome.ok(new ResumePaymentResponse(b.publicId(), "PENDING_PAYMENT", "PENDING",
          h.publicId(), h.expiresAt(), b.total(), b.currency()));
    });
  }

  private Outcome<Prepared> prepare(long user, String bookingId, boolean allowClosed) {
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
      // Until the PayOS status lookup is implemented, never reopen a booking whose
      // old payment link might have received money after the hold expired.
      if (bookings.hasPaymentAttempt(b.id()))
        throw new BookingException(409, "PAYMENT_STATUS_UNVERIFIED",
            "Cần đối soát mã thanh toán cũ trước khi giữ lại chỗ.");
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
