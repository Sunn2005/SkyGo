package com.skygo.backend.booking.service;

import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.repository.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SeatHoldExpiryService {
  private final SeatHoldRepository holds;
  private final FlightInventoryRepository inventory;
  private final BookingRepository bookings;
  private final Clock clock;
  private final int batchSize;
  private final TransactionTemplate tx;

  public SeatHoldExpiryService(SeatHoldRepository holds, FlightInventoryRepository inventory,
      BookingRepository bookings, Clock clock, PlatformTransactionManager manager,
      @Value("${skygo.seat-hold.cleanup-batch-size:100}") int batchSize) {
    this.holds = holds;
    this.inventory = inventory;
    this.bookings = bookings;
    this.clock = clock;
    if (batchSize < 1) throw new IllegalArgumentException("cleanup-batch-size must be positive");
    this.batchSize = batchSize;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public int expireBatch() {
    var due = holds.due(clock.instant(), batchSize);
    for (var candidate : due) tx.executeWithoutResult(status -> {
      holds.lockUser(candidate.userId());
      holds.byId(candidate.id()).ifPresent(this::expireLocked);
    });
    return due.size();
  }

  // Called before entering the transaction that reads/reserves inventory.
  public void expireDue() {
    while (expireBatch() == batchSize) { /* Drain overdue holds even if the scheduler ran late. */ }
  }

  // Caller owns the account and hold locks. Returns false when no expiry is needed.
  public boolean expireLocked(SeatHold hold) {
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (!hold.status().equals("ACTIVE") || !hold.expired(now)) return false;
    if (hold.bookingId() != null) {
      var booking = bookings.lock(hold.bookingId(), hold.userId());
      if (booking.status().equals("PAID") || booking.paymentStatus().equals("SUCCEEDED"))
        throw new IllegalStateException("Paid booking cannot have an active expired hold");
    }
    inventory.lock(hold.legs().stream().map(SeatHold.Leg::fareId).toList());
    if (!holds.expire(hold.id(), now)) return false;
    for (var leg : hold.legs()) inventory.release(leg.fareId(), leg.reservedSeats());
    holds.invalidateReviews(hold.id(), now);
    if (hold.bookingId() != null) bookings.expire(hold.bookingId());
    return true;
  }
}
