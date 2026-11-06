package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import com.skygo.backend.booking.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingQuoteService {
  private final SeatHoldService holds;
  private final SeatHoldRepository repository;
  private final FlightInventoryRepository inventory;
  private final SeatHoldExpiryService expiry;
  private final BookingPricingService pricing;
  public BookingQuoteService(SeatHoldService holds,SeatHoldRepository repository,FlightInventoryRepository inventory,
      SeatHoldExpiryService expiry,BookingPricingService pricing) {
    this.holds=holds; this.repository=repository; this.inventory=inventory; this.expiry=expiry; this.pricing=pricing;
  }
  record Quoted(SeatHold hold,BookingQuote quote) {}
  public BookingQuoteResponse quote(long user,BookingQuoteRequest request) {
    return holds.withUser(user,()->{
      var result=prepare(user,request);
      if(result.error()!=null) return Outcome.failed(result.error());
      var q=result.value();
      return Outcome.ok(new BookingQuoteResponse(q.quote().totalAmount(),q.quote().currency(),q.hold().expiresAt()));
    });
  }
  Outcome<Quoted> prepare(long user,BookingQuoteRequest request) {
    var h=repository.owned(request.seatHoldId(),user).orElseThrow(
        ()->new BookingException(404,"SEAT_HOLD_NOT_FOUND","Không tìm thấy lượt giữ chỗ."));
    if(!h.status().equals("ACTIVE")) throw SeatHoldService.expired();
    if(h.bookingId()!=null) throw new BookingException(409,"HOLD_ATTACHED_TO_BOOKING","Lượt giữ chỗ đã được dùng để tạo đơn.");
    int expected=h.tripType()==SeatHold.TripType.ONE_WAY?1:2;
    var counts=request.passengerCounts();
    if(h.tripType()!=request.tripType() || h.adults()!=counts.adults() || h.children()!=counts.children()
        || h.infants()!=counts.infants() || h.legs().size()!=expected || request.selections().size()!=expected) throw mismatch();
    var selections=new EnumMap<Direction,BookingQuoteRequest.Selection>(Direction.class);
    for(var selection:request.selections())
      if(selections.put(selection.direction(),selection)!=null) throw mismatch();
    var pair=new EnumMap<Direction,FlightInventoryRepository.Fare>(Direction.class);
    for(var leg:h.legs()) {
      var selected=selections.get(leg.direction());
      var fare=inventory.fare(leg.fareId());
      if(selected==null || !selected.flightId().equals(fare.flightId()) || !selected.cabinClass().name().equals(fare.cabin())
          || leg.reservedSeats()!=h.seats()) throw mismatch();
      pair.put(leg.direction(),fare);
    }
    SeatHoldService.validatePair(pair);
    var quote=pricing.calculate(h);
    // Recheck against the catalog snapshot now protected by the pricing transaction's locks.
    pair.clear();
    for(var leg:quote.legs()) {
      var selected=selections.get(leg.direction());
      if(!selected.flightId().equals(leg.flightId()) || selected.cabinClass()!=leg.cabinClass()) throw mismatch();
      pair.put(leg.direction(),inventory.fare(leg.fareId()));
    }
    SeatHoldService.validatePair(pair);
    if(expiry.expireLocked(h)) return Outcome.failed(SeatHoldService.expired());
    return Outcome.ok(new Quoted(h,quote));
  }
  private BookingException mismatch() { return new BookingException(409,"HOLD_MISMATCH","Hành trình hoặc số hành khách không khớp lượt giữ chỗ."); }
}
