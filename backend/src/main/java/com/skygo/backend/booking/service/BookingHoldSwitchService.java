package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.BookingHoldSwitchRepository.Switch;
import com.skygo.backend.booking.repository.FlightInventoryRepository.Fare;
import com.skygo.backend.booking.repository.PaymentAttemptRepository.Attempt;
import com.skygo.backend.booking.service.ExpiredPaymentVerifier.Evidence;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingHoldSwitchService {
  private final SeatHoldService service;
  private final SeatHoldRepository holds;
  private final BookingRepository bookings;
  private final FlightInventoryRepository inventory;
  private final PaymentAttemptRepository attempts;
  private final BookingHoldSwitchRepository switches;
  private final ExpiredPaymentVerifier verifier;
  private final BookingDeadlineService deadlines;
  private final ObjectMapper mapper;
  public BookingHoldSwitchService(SeatHoldService service,SeatHoldRepository holds,BookingRepository bookings,
      FlightInventoryRepository inventory,PaymentAttemptRepository attempts,BookingHoldSwitchRepository switches,
      ExpiredPaymentVerifier verifier,BookingDeadlineService deadlines,ObjectMapper mapper) {
    this.service=service;this.holds=holds;this.bookings=bookings;this.inventory=inventory;
    this.attempts=attempts;this.switches=switches;this.verifier=verifier;this.deadlines=deadlines;this.mapper=mapper;
  }
  private record Claim(Switch operation,Attempt attempt,BookingHoldSwitchResponse replay) {}

  public BookingHoldSwitchResponse switchHold(long user,String bookingId,String key,BookingHoldSwitchRequest request) {
    if(key==null || !key.matches("[\\x21-\\x7E]{1,128}"))
      throw BookingException.invalid("Idempotency-Key","Khóa chống gửi lặp phải gồm 1–128 ký tự ASCII không có khoảng trắng.");
    var normalized=new BookingHoldSwitchRequest(request.expectedSeatHoldId(),request.expectedVersion(),request.tripType(),
        request.passengerCounts(),request.selections().stream().sorted(Comparator.comparing(s->s.direction().name())).toList());
    String json=mapper.writeValueAsString(normalized);
    byte[] hash=hash(bookingId+"\n"+json);
    var claim=service.withUser(user,()->claim(user,bookingId,key,normalized,json,hash));
    if(claim.replay()!=null) return claim.replay();
    var op=claim.operation();
    try {
      // The durable claim blocks creation/resume of QR while this network call is in flight.
      var evidence=verifier.verify(List.of(claim.attempt())).get(claim.attempt().id());
      var accepted=service.withUser(user,()->accept(user,op.id(),evidence));
      if(accepted!=null) return accepted;
      return service.withUser(user,()->finish(user,op.id(),normalized));
    } catch(BookingException ex) {
      var replay=service.withUser(user,()->{
        var current=switches.byId(op.id());
        if(current.status().equals("COMPLETED")) return Outcome.ok(response(current));
        if(!current.pending()) return Outcome.ok(null);
        var h=holds.byId(current.sourceHoldId()).orElseThrow();
        var b=bookings.lock(current.bookingId(),user);
        if(ex.code.equals("PAYMENT_STATUS_UNVERIFIED") && h.status().equals("ACTIVE")) {
          switches.state(current.id(),current.status(),ex.code,null,service.now());
        } else {
          // QR closure cannot be rolled back. End the old reservation if closure was confirmed,
          // even when the target flight sold out during the PayOS round trip.
          if(current.status().equals("QR_CLOSED") && !b.status().equals("PAID")
              && !Set.of("SUCCEEDED","RECONCILIATION_REQUIRED").contains(b.paymentStatus())) {
            deadlines.release(h,service.now());
            bookings.expire(b.id());
            deadlines.cancelIfClosed(bookings.lock(b.id(),user),service.now());
          }
          switches.state(current.id(),"FAILED",ex.code,null,service.now());
        }
        return Outcome.ok(null);
      });
      if(replay!=null) return replay;
      throw ex;
    }
  }

  private Outcome<Claim> claim(long user,String bookingId,String key,BookingHoldSwitchRequest request,String json,byte[] hash) {
    var existing=switches.byKey(user,key);
    if(existing.isPresent()) {
      var op=existing.get();
      if(!MessageDigest.isEqual(op.hash(),hash))
        throw error("IDEMPOTENCY_KEY_REUSED","Khóa chống gửi lặp đã được dùng cho yêu cầu khác.");
      if(op.status().equals("COMPLETED")) return Outcome.ok(new Claim(op,null,response(op)));
      if(!op.pending()) return Outcome.failed(previousError(op));
      var h=holds.byId(op.sourceHoldId()).orElseThrow();
      if(!h.status().equals("ACTIVE")) {
        switches.state(op.id(),"FAILED","SEAT_HOLD_EXPIRED",null,service.now());
        return Outcome.failed(SeatHoldService.expired());
      }
      return Outcome.ok(new Claim(op,attempts.byHold(h.id()).orElseThrow(),null));
    }
    switches.assertIdle(user);
    var h=holds.owned(request.expectedSeatHoldId(),user).orElseThrow(()->
        new BookingException(404,"SEAT_HOLD_NOT_FOUND","Không tìm thấy lượt giữ chỗ."));
    var b=bookings.lock(bookingId,user);
    var invalid=notPayable(b);
    if(invalid!=null) throw invalid;
    if(!h.status().equals("ACTIVE")) throw SeatHoldService.expired();
    if(h.bookingId()==null || h.bookingId()!=b.id()) throw error("HOLD_MISMATCH","Lượt giữ chỗ không thuộc đơn này.");
    if(h.version()!=request.expectedVersion()) throw SeatHoldService.versionConflict();
    var proposed=proposed(request,h);
    if(deadlines.cancelIfClosed(b,service.now())) return Outcome.failed(BookingDeadlineService.closed());
    for(var fare:proposed.values()) inventory.requireAvailable(fare,service.now());
    capacity(h,proposed,request.passengerCounts().seats());
    var a=attempts.byHold(h.id()).orElseThrow(()->error("PAYMENT_LINK_UNVERIFIED","Đơn chưa có mã QR để chuyển lượt giữ chỗ."));
    if(a.paymentLinkId()==null || !Set.of("PENDING","CANCELLED","EXPIRED").contains(a.status()))
      throw ExpiredPaymentVerifier.unverified();
    if(h.expired(service.now())) throw SeatHoldService.expired();
    var op=switches.create(user,b.id(),h.id(),h.version(),a.id(),key,hash,json,service.now());
    return Outcome.ok(new Claim(op,a,null));
  }

  private Outcome<BookingHoldSwitchResponse> accept(long user,long id,Evidence evidence) {
    var op=switches.byId(id);
    if(op.status().equals("COMPLETED")) return Outcome.ok(response(op));
    if(!op.pending()) return Outcome.failed(previousError(op));
    var h=holds.byId(op.sourceHoldId()).orElseThrow();
    var b=bookings.lock(op.bookingId(),user);
    var invalid=notPayable(b);
    if(b.status().equals("PAID") || b.paymentStatus().equals("SUCCEEDED")) {
      switches.state(id,"FAILED",invalid.code,null,service.now());
      return Outcome.failed(invalid);
    }
    var a=attempts.byHold(h.id()).orElseThrow();
    var expected=evidence.expected();
    if(a.id()!=op.attemptId() || a.id()!=expected.id() || a.orderCode()!=expected.orderCode()
        || a.amount()!=expected.amount() || !Objects.equals(a.paymentLinkId(),expected.paymentLinkId()))
      throw ExpiredPaymentVerifier.unverified();
    if(verifier.record(evidence)) {
      switches.state(id,"RECONCILIATION_REQUIRED","PAYMENT_RECONCILIATION_REQUIRED",null,service.now());
      return Outcome.failed(error("PAYMENT_RECONCILIATION_REQUIRED","QR cũ đã có giao dịch cần đối soát; chưa chuyển lượt giữ chỗ."));
    }
    if(invalid!=null) {
      switches.state(id,"FAILED",invalid.code,null,service.now());
      return Outcome.failed(invalid);
    }
    switches.state(id,"QR_CLOSED",null,null,service.now());
    return Outcome.ok(null);
  }

  private Outcome<BookingHoldSwitchResponse> finish(long user,long id,BookingHoldSwitchRequest request) {
    var op=switches.byId(id);
    if(op.status().equals("COMPLETED")) return Outcome.ok(response(op));
    if(!op.status().equals("QR_CLOSED")) return Outcome.failed(previousError(op));
    var h=holds.byId(op.sourceHoldId()).orElseThrow();
    var b=bookings.lock(op.bookingId(),user);
    var invalid=notPayable(b);
    if(invalid!=null) {
      switches.state(id,"FAILED",invalid.code,null,service.now());
      return Outcome.failed(invalid);
    }
    if(!h.status().equals("ACTIVE") || h.expired(service.now())) throw SeatHoldService.expired();
    if(h.version()!=op.expectedVersion()) throw SeatHoldService.versionConflict();
    var proposed=proposed(request,h);
    if(deadlines.cancelIfClosed(b,service.now())) {
      switches.state(id,"FAILED","BOOKING_WINDOW_CLOSED",null,service.now());
      return Outcome.failed(BookingDeadlineService.closed());
    }
    for(var fare:proposed.values()) inventory.requireAvailable(fare,service.now());
    var deltas=deltas(h,proposed,request.passengerCounts().seats());
    for(var entry:deltas.entrySet()) {
      if(entry.getValue()<0) inventory.release(entry.getKey(),-entry.getValue());
      else if(entry.getValue()>0) inventory.reserve(entry.getKey(),entry.getValue());
    }
    if(!holds.release(h.id(),service.now())) throw SeatHoldService.expired();
    holds.invalidateReviews(h.id(),service.now());
    bookings.expire(b.id());
    var count=request.passengerCounts();
    // Preserve the original deadline instead of granting another 15 minutes while browsing.
    var target=holds.create(user,null,request.tripType(),count.adults(),count.children(),count.infants(),h.startedAt());
    for(var entry:proposed.entrySet()) holds.leg(target.id(),entry.getKey(),entry.getValue().id(),count.seats());
    if(h.expired(service.now())) throw SeatHoldService.expired();
    switches.state(id,"COMPLETED",null,target.id(),service.now());
    return Outcome.ok(response(switches.byId(id)));
  }

  private EnumMap<Direction,Fare> proposed(BookingHoldSwitchRequest request,SeatHold h) {
    var result=new EnumMap<Direction,Fare>(Direction.class);
    for(var selection:request.selections()) {
      var fare=inventory.find(selection.flightId(),selection.cabinClass().name()).orElseThrow(()->
          error("FLIGHT_UNAVAILABLE","Chuyến bay hoặc hạng vé không tồn tại."));
      if(result.put(selection.direction(),fare)!=null)
        throw BookingException.invalid("selections","Mỗi chiều bay chỉ được chọn một lần.");
    }
    if(request.tripType()==TripType.ONE_WAY && result.containsKey(Direction.RETURN))
      throw BookingException.invalid("selections","Hành trình một chiều không có chiều về.");
    var ids=new TreeSet<Long>();h.legs().forEach(l->ids.add(l.fareId()));result.values().forEach(f->ids.add(f.id()));
    inventory.lock(ids);
    var checked=new HashMap<Long,Fare>();
    for(long fare:result.values().stream().map(Fare::id).distinct().sorted().toList())
      checked.put(fare,inventory.reservationFare(fare));
    result.replaceAll((direction,fare)->checked.get(fare.id()));
    SeatHoldService.validatePair(result);
    return result;
  }
  private TreeMap<Long,Long> deltas(SeatHold h,Map<Direction,Fare> selected,long count) {
    var deltas=new TreeMap<Long,Long>();
    h.legs().forEach(l->deltas.merge(l.fareId(),-l.reservedSeats(),Long::sum));
    selected.values().forEach(f->deltas.merge(f.id(),count,Long::sum));
    return deltas;
  }
  private void capacity(SeatHold h,Map<Direction,Fare> proposed,long seats) {
    for(var entry:deltas(h,proposed,seats).entrySet())
      if(entry.getValue()>0 && inventory.available(entry.getKey())<entry.getValue())
        throw error("INSUFFICIENT_CAPACITY","Hiện chưa đủ ghế. Hãy quay lại sau nhé.");
  }
  private BookingException notPayable(BookingRepository.Booking b) {
    if(b.status().equals("PAID") || b.paymentStatus().equals("SUCCEEDED"))
      return error("BOOKING_ALREADY_PAID","Đơn đã thanh toán; không chuyển lượt giữ chỗ.");
    if(b.paymentStatus().equals("RECONCILIATION_REQUIRED"))
      return error("PAYMENT_RECONCILIATION_REQUIRED","Đơn có giao dịch cần đối soát.");
    if(b.status().equals("CANCELLED")) return BookingDeadlineService.closed();
    return null;
  }
  private BookingHoldSwitchResponse response(Switch op) {
    var b=bookings.lock(op.bookingId(),op.userId());
    return new BookingHoldSwitchResponse(op.publicId(),new BookingHoldSwitchResponse.PreviousBooking(b.publicId(),b.status()),
        service.response(holds.byId(op.targetHoldId()).orElseThrow()));
  }
  private BookingException previousError(Switch op) {
    String code=op.errorCode()==null?"HOLD_SWITCH_IN_PROGRESS":op.errorCode();
    return error(code,code.equals("INSUFFICIENT_CAPACITY")?"Hiện chưa đủ ghế. Hãy quay lại sau nhé.":
        "Thao tác chuyển chưa hoàn tất hoặc không thể thực hiện. Vui lòng tải lại trạng thái đơn.");
  }
  private static BookingException error(String code,String message) { return new BookingException(409,code,message); }
  private byte[] hash(String value) {
    try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
    catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
  }
  public void recoverPending() {
    for(var op:switches.pending(20)) {
      try { switchHold(op.userId(),op.bookingId(),op.key(),mapper.readValue(op.selection(),BookingHoldSwitchRequest.class)); }
      catch(BookingException ignored) { /* Durable state remains retryable, or records its terminal failure. */ }
    }
  }
}
