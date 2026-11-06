package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.BookingDetailResponse;
import com.skygo.backend.booking.contract.BookingDetailResponse.*;
import com.skygo.backend.booking.repository.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BookingDetailService {
  private final BookingDetailRepository repository;
  private final BookingOpenRepository snapshots;
  private final Clock clock;
  public BookingDetailService(BookingDetailRepository repository,
      BookingOpenRepository snapshots, Clock clock) {
    this.repository = repository;
    this.snapshots = snapshots;
    this.clock = clock;
  }

  // A consistent view of payment, tickets and invoice while a webhook commits.
  @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
  public BookingDetailResponse get(long user, String bookingId) {
    long id = repository.ownedId(bookingId,user);
    var b = snapshots.load(id,null);
    var hold = repository.latestHold(id);
    var invoice = repository.invoice(id);
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var passengers = b.passengers().stream().map(p -> new Passenger(p.passengerId(),
        p.clientPassengerId(),p.passengerType(),p.title(),p.givenNames(),p.surname(),
        p.dateOfBirth(),p.gender(),p.document(),p.benefits().stream().map(x ->
            new Benefit(x.direction(),x.carryOnMaxKg(),x.checkedPieces(),
                x.extraCheckedPieces(),x.includedMealsPerLeg(),x.seatType(),
                x.strollerPieces())).toList(),p.ticketNumber())).toList();
    return new BookingDetailResponse(b.bookingId(),b.bookingCode(),b.bookingStatus(),
        b.createdAt(),now,b.tripType(),b.passengerCounts(),b.contact(),
        hold == null ? null : new Hold(hold.publicId(),hold.status(),hold.expiresAt(),
            hold.status().equals("EXPIRED") || (hold.status().equals("ACTIVE")
                && !now.isBefore(hold.expiresAt()))),
        b.price(),b.currency(),b.payment(),b.legs(),passengers,invoice);
  }
}
