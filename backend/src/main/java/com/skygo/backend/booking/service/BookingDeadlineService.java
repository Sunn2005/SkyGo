package com.skygo.backend.booking.service;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.repository.*;
import java.time.Instant;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingDeadlineService {
  private final BookingRepository bookings;
  private final SeatHoldRepository holds;
  private final FlightInventoryRepository inventory;
  private final Clock clock;
  private final TransactionTemplate tx;
  public BookingDeadlineService(BookingRepository bookings,SeatHoldRepository holds,FlightInventoryRepository inventory,
      Clock clock,PlatformTransactionManager manager) {
    this.bookings=bookings;this.holds=holds;this.inventory=inventory;
    this.clock=clock;this.tx=new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }
  // Caller owns account/hold/booking locks and, when switching, the union of old/new fare locks.
  public boolean cancelIfClosed(BookingRepository.Booking b,Instant now) {
    if (b.status().equals("CANCELLED")) return true;
    if (b.status().equals("PAID") || Set.of("SUCCEEDED","RECONCILIATION_REQUIRED").contains(b.paymentStatus())) return false;
    var legs=bookings.legs(b.id());
    inventory.lock(legs.stream().map(BookingRepository.BookedLeg::fareId).toList());
    boolean closed=false;
    for(var leg:legs) closed |= !now.isBefore(inventory.reservationFare(leg.fareId()).bookingClosesAt());
    if (!closed) return false;
    holds.active(b.userId()).filter(h->h.bookingId()!=null && h.bookingId()==b.id()).ifPresent(h->release(h,now));
    bookings.cancel(b.id(),now);
    return true;
  }
  public void release(SeatHold h,Instant now) {
    inventory.lock(h.legs().stream().map(SeatHold.Leg::fareId).toList());
    if (holds.release(h.id(),now)) {
      for(var leg:h.legs()) inventory.release(leg.fareId(),leg.reservedSeats());
      holds.invalidateReviews(h.id(),now);
    }
  }
  public static BookingException closed() {
    return new BookingException(409,"BOOKING_WINDOW_CLOSED","Đơn đã quá thời hạn đặt vé và không thể tiếp tục thanh toán.");
  }
  public void cancelDue() {
    for(var due:bookings.closedUnpaid(clock.instant(),100)) tx.executeWithoutResult(status->{
      holds.lockUser(due.userId());
      holds.active(due.userId());
      cancelIfClosed(bookings.lock(due.id(),due.userId()),clock.instant());
    });
  }
}
