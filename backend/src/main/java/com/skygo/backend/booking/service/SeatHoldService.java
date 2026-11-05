package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.FlightInventoryRepository.Fare;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SeatHoldService {
  private final SeatHoldRepository holds;
  private final FlightInventoryRepository inventory;
  private final BookingRepository bookings;
  private final SeatHoldExpiryService expiry;
  private final Clock clock;
  private final TransactionTemplate tx;

  public SeatHoldService(SeatHoldRepository holds, FlightInventoryRepository inventory,
      BookingRepository bookings, SeatHoldExpiryService expiry, Clock clock, PlatformTransactionManager manager) {
    this.holds = holds;
    this.inventory = inventory;
    this.bookings = bookings;
    this.expiry = expiry;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  record Outcome<T>(T value, BookingException error, boolean retry) {
    static <T> Outcome<T> ok(T value) { return new Outcome<>(value, null, false); }
    static <T> Outcome<T> failed(BookingException error) { return new Outcome<>(null, error, false); }
    static <T> Outcome<T> again() { return new Outcome<>(null, null, true); }
  }

  // Domain failures normally roll back. Expiry is committed before retrying or returning 409.
  <T> T withUser(long user, Supplier<Outcome<T>> action) {
    expiry.expireDue();
    while (true) {
      Outcome<T> result = tx.execute(status -> {
        holds.lockUser(user);
        var active = holds.active(user);
        if (active.isPresent() && expiry.expireLocked(active.get())) return Outcome.again();
        return action.get();
      });
      if (result.retry()) continue;
      if (result.error() != null) throw result.error();
      return result.value();
    }
  }

  public SeatHoldResponse current(long user) {
    return withUser(user, () -> Outcome.ok(holds.active(user).map(this::response).orElse(null)));
  }

  public SeatHoldResponse create(long user, SeatHoldCreateRequest request) {
    if (request.selection().direction() != Direction.OUTBOUND)
      throw BookingException.invalid("selection.direction", "Lượt giữ chỗ phải bắt đầu bằng chiều đi.");
    return withUser(user, () -> {
      holds.active(user).ifPresent(h -> { throw activeConflict(h); });
      var selected = selected(request.selection().flightId(), request.selection().cabinClass());
      inventory.lock(List.of(selected.id()));
      selected = inventory.reservationFare(selected.id());
      var now = now();
      inventory.requireAvailable(selected, now);
      inventory.reserve(selected.id(), request.passengerCounts().seats());
      var count = request.passengerCounts();
      var hold = holds.create(user, null, request.tripType(), count.adults(), count.children(), count.infants(), now());
      holds.leg(hold.id(), Direction.OUTBOUND, selected.id(), count.seats());
      return Outcome.ok(response(holds.byId(hold.id()).orElseThrow()));
    });
  }

  public SeatHoldResponse leg(long user, String holdId, String directionValue, SeatHoldLegRequest request) {
    final Direction direction;
    try { direction = Direction.valueOf(directionValue); }
    catch (IllegalArgumentException ex) { throw BookingException.invalid("direction", "Chiều bay không hợp lệ."); }
    return withUser(user, () -> {
      var hold = editable(user, holdId);
      if (direction == Direction.RETURN && hold.tripType() != TripType.ROUND_TRIP)
        throw BookingException.invalid("direction", "Hành trình một chiều không có chiều về.");
      var selection = selected(request.flightId(), request.cabinClass());
      var ids = new ArrayList<>(hold.legs().stream().map(Leg::fareId).toList());
      ids.add(selection.id());
      inventory.lock(ids);
      if (expiry.expireLocked(hold)) return Outcome.failed(expired());
      var old = hold.legs().stream().filter(l -> l.direction() == direction).findFirst();
      if (old.isPresent() && old.get().fareId() == selection.id()) return Outcome.ok(response(hold));
      selection = inventory.reservationFare(selection.id());
      if (expiry.expireLocked(hold)) return Outcome.failed(expired());
      inventory.requireAvailable(selection, now());
      var proposed = new EnumMap<Direction, Fare>(Direction.class);
      for (var leg : hold.legs()) proposed.put(leg.direction(), inventory.fare(leg.fareId()));
      proposed.put(direction, selection);
      validatePair(proposed);
      if (old.isPresent()) inventory.release(old.get().fareId(), old.get().reservedSeats());
      inventory.reserve(selection.id(), hold.seats());
      holds.leg(hold.id(), direction, selection.id(), hold.seats());
      holds.invalidateReviews(hold.id(), now());
      return Outcome.ok(response(holds.byId(hold.id()).orElseThrow()));
    });
  }

  public SeatHoldResponse counts(long user, String holdId, PassengerCountsRequest request) {
    return withUser(user, () -> {
      var hold = editable(user, holdId);
      inventory.lock(hold.legs().stream().map(Leg::fareId).toList());
      if (expiry.expireLocked(hold)) return Outcome.failed(expired());
      if (hold.adults() == request.adults() && hold.children() == request.children() && hold.infants() == request.infants())
        return Outcome.ok(response(hold));
      long delta = request.seats() - hold.seats();
      var fares = new ArrayList<Fare>();
      if (delta > 0) {
        for (var leg : hold.legs()) fares.add(inventory.reservationFare(leg.fareId()));
        if (expiry.expireLocked(hold)) return Outcome.failed(expired());
        var checkedAt = now();
        for (var fare : fares) inventory.requireAvailable(fare, checkedAt);
      }
      for (var leg : hold.legs()) {
        if (delta > 0) {
          inventory.reserve(leg.fareId(), delta);
        } else inventory.release(leg.fareId(), -delta);
        holds.leg(hold.id(), leg.direction(), leg.fareId(), request.seats());
      }
      holds.counts(hold.id(), request.adults(), request.children(), request.infants());
      holds.invalidateReviews(hold.id(), now());
      return Outcome.ok(response(holds.byId(hold.id()).orElseThrow()));
    });
  }

  public static void validatePair(Map<Direction, Fare> legs) {
    var out = legs.get(Direction.OUTBOUND);
    var back = legs.get(Direction.RETURN);
    if (out == null) throw BookingException.invalid("direction", "Chưa chọn chiều đi.");
    if (back != null && (!back.origin().equals(out.destination()) || !back.destination().equals(out.origin())
        || !back.localDate().isAfter(out.localDate())))
      throw BookingException.invalid("flightId", "Chiều về phải đảo tuyến chiều đi và có ngày về sau ngày đi.");
  }

  private SeatHold editable(long user, String id) {
    var h = holds.owned(id, user).orElseThrow(() -> new BookingException(404, "SEAT_HOLD_NOT_FOUND", "Không tìm thấy lượt giữ chỗ."));
    if (!h.status().equals("ACTIVE")) throw expired();
    if (h.bookingId() != null) throw new BookingException(409, "HOLD_ATTACHED_TO_BOOKING", "Không thể sửa lượt giữ chỗ đã tạo đơn.");
    return h;
  }
  private Fare selected(String flight, CabinClass cabin) {
    return inventory.find(flight, cabin.name()).orElseThrow(
        () -> new BookingException(409, "FLIGHT_UNAVAILABLE", "Chuyến bay hoặc hạng vé không tồn tại."));
  }
  BookingException activeConflict(SeatHold h) {
    return new BookingException(409, "ACTIVE_HOLD_EXISTS", "Bạn đang có lượt giữ chỗ còn hiệu lực.",
        null, h.publicId(), bookings.publicId(h.bookingId()));
  }
  static BookingException expired() { return new BookingException(409, "SEAT_HOLD_EXPIRED", "Lượt giữ chỗ đã hết hiệu lực. Vui lòng chọn lại chuyến bay."); }
  Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

  private SeatHoldResponse response(SeatHold h) {
    var legs = h.legs().stream().map(leg -> {
      var f = inventory.fare(leg.fareId());
      return new SeatHoldResponse.Leg(leg.direction().name(), f.flightId(), f.cabin(),
          new SeatHoldResponse.Airport(f.origin(), f.originCity()),
          new SeatHoldResponse.Airport(f.destination(), f.destinationCity()),
          f.departure().atZone(ZoneId.of(f.originZone())).toOffsetDateTime(),
          f.arrival().atZone(ZoneId.of(f.destinationZone())).toOffsetDateTime());
    }).toList();
    return new SeatHoldResponse(h.publicId(), h.status(), h.tripType().name(),
        new PassengerCountsRequest(h.adults(), h.children(), h.infants()), h.expiresAt(), legs, bookings.publicId(h.bookingId()));
  }
}
