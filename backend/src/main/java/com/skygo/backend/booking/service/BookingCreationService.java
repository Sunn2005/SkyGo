package com.skygo.backend.booking.service;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingCreationService {
  private final SeatHoldService holds;
  private final BookingReviewRepository reviews;
  private final BookingReviewSnapshotService snapshots;
  private final BookingQuoteService quotes;
  private final PassengerValidationService validation;
  private final BookingBaggagePricingService pricing;
  private final BookingCreationRepository bookings;
  private final SeatHoldExpiryService expiry;

  public BookingCreationService(SeatHoldService holds,BookingReviewRepository reviews,
      BookingReviewSnapshotService snapshots,BookingQuoteService quotes,PassengerValidationService validation,
      BookingBaggagePricingService pricing,BookingCreationRepository bookings,SeatHoldExpiryService expiry) {
    this.holds=holds; this.reviews=reviews; this.snapshots=snapshots; this.quotes=quotes;
    this.validation=validation; this.pricing=pricing; this.bookings=bookings; this.expiry=expiry;
  }

  public record Result(BookingCreateResponse booking,boolean replayed) {}

  public Result create(AuthSession session,String key,BookingCreateRequest request) {
    if(key==null || !key.matches("[\\x21-\\x7E]{1,128}"))
      throw BookingException.invalid("Idempotency-Key","Khóa chống gửi lặp phải gồm 1–128 ký tự ASCII không có khoảng trắng.");
    byte[] hash=hash(request);
    long user=session.user().id();
    try { return holds.withUser(user,()->{
      reviews.requireSession(user,session.id(),holds.now());
      var existing=bookings.existing(user,key);
      if(existing.isPresent()) {
        if(!MessageDigest.isEqual(hash,existing.get().requestHash()))
          throw new BookingException(409,"IDEMPOTENCY_KEY_REUSED","Khóa chống gửi lặp đã được dùng cho yêu cầu khác.");
        return Outcome.ok(new Result(bookings.response(existing.get().id()),true));
      }

      var result=snapshots.load(session,request.reviewId());
      if(result.error()!=null) return Outcome.failed(result.error());
      var loaded=result.value();
      var old=loaded.snapshot();
      if(loaded.review().version()!=request.expectedVersion()) throw BookingReviewSnapshotService.conflict();
      var selections=old.quote().legs().stream()
          .map(l->new BookingQuoteRequest.Selection(l.direction(),l.flightId(),l.cabinClass())).toList();
      var prepared=quotes.prepare(user,new BookingQuoteRequest(old.seatHoldId(),old.quote().tripType(),
          old.quote().passengerCounts(),selections));
      if(prepared.error()!=null) return Outcome.failed(prepared.error());
      var quote=prepared.value().quote();
      var checked=validation.validate(old.passengers(),old.contact(),quote);
      var calculated=pricing.calculate(quote,checked.passengers(),BookingReviewSnapshotService.selections(old));
      var benefits=BookingReviewSnapshotService.benefits(quote,checked.passengers());
      if(!old.quote().equals(quote) || !old.passengers().equals(checked.passengers())
          || !old.contact().equals(checked.contact()) || !old.passengerBenefits().equals(benefits)
          || !old.baggage().equals(calculated.baggage()) || !old.price().equals(calculated.price()))
        throw new BookingException(409,"REVIEW_OUTDATED","Thông tin chuyến bay hoặc giá đã thay đổi. Vui lòng xác nhận lại.");
      var finished=snapshots.finish(session,loaded,old);
      if(finished.error()!=null) return Outcome.failed(finished.error());
      var booking=bookings.create(user,key,hash,loaded.review(),loaded.hold(),old,holds.now());
      return Outcome.ok(new Result(booking,false));
    }); }
    catch(BookingException ex) {
      // If the deadline passed during the insert, the transaction rolled back.
      // Release its seats in a separate expiry transaction before returning 409.
      if(ex.code.equals("SEAT_HOLD_EXPIRED") || ex.code.equals("REVIEW_EXPIRED")) expiry.expireDue();
      throw ex;
    }
  }

  private static byte[] hash(BookingCreateRequest request) {
    try {
      String canonical=request.reviewId().length()+":"+request.reviewId()+":"+request.expectedVersion();
      return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
    } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable",ex); }
  }
}
