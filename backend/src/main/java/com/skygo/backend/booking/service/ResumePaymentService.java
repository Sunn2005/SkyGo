package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.ResumePaymentResponse;
import com.skygo.backend.booking.exception.BookingException;
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

  public ResumePaymentService(SeatHoldService service, SeatHoldRepository holds, BookingRepository bookings,
      FlightInventoryRepository inventory, SeatHoldExpiryService expiry) {
    this.service = service;
    this.holds = holds;
    this.bookings = bookings;
    this.inventory = inventory;
    this.expiry = expiry;
  }
  public ResumePaymentResponse resume(long user, String bookingId) {
    return service.withUser(user, () -> {
      var active = holds.active(user);
      var b = bookings.lock(bookingId, user);
      if (b.status().equals("PAID") || b.paymentStatus().equals("SUCCEEDED"))
        throw new BookingException(409, "BOOKING_ALREADY_PAID", "Đơn đã thanh toán.");
      if (active.isPresent()) {
        var h = active.get();
        if (h.bookingId() == null || h.bookingId() != b.id()) throw service.activeConflict(h);
        if (expiry.expireLocked(h)) return Outcome.again();
        return Outcome.ok(new ResumePaymentResponse(b.publicId(), b.status(), b.paymentStatus(),
            h.publicId(), h.expiresAt(), b.total(), b.currency()));
      }
      if (!b.status().equals("EXPIRED") || !b.paymentStatus().equals("EXPIRED"))
        throw new BookingException(409, "BOOKING_NOT_RESUMABLE", "Đơn không ở trạng thái có thể giữ lại chỗ.");
      var legs = bookings.legs(b.id());
      if (legs.size() != (b.tripType() == TripType.ONE_WAY ? 1 : 2)
          || legs.stream().noneMatch(l -> l.direction() == Direction.OUTBOUND))
        throw new BookingException(409, "HOLD_MISMATCH", "Hành trình của đơn không đầy đủ.");
      inventory.lock(legs.stream().map(BookingRepository.BookedLeg::fareId).toList());
      var selected = legs.stream().map(leg -> inventory.reservationFare(leg.fareId())).toList();
      var now = service.now();
      for (var fare : selected) inventory.requireAvailable(fare, now);
      for (var leg : legs) inventory.reserve(leg.fareId(), (long) b.adults() + b.children());
      var h = holds.create(user, b.id(), b.tripType(), b.adults(), b.children(), b.infants(), service.now());
      for (var leg : legs) holds.leg(h.id(), leg.direction(), leg.fareId(), h.seats());
      bookings.pending(b.id());
      return Outcome.ok(new ResumePaymentResponse(b.publicId(), "PENDING_PAYMENT", "PENDING",
          h.publicId(), h.expiresAt(), b.total(), b.currency()));
    });
  }
}
