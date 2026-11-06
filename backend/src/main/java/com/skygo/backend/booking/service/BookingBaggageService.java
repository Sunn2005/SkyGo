package com.skygo.backend.booking.service;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.BookingReviewSnapshot;
import com.skygo.backend.booking.repository.BookingReviewRepository;
import org.springframework.stereotype.Service;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingBaggageService {
  private final SeatHoldService holds;
  private final BookingReviewSnapshotService snapshots;
  private final BookingBaggagePricingService pricing;
  private final BookingQuoteService quotes;
  private final PassengerValidationService validation;
  private final BookingReviewRepository reviews;
  public BookingBaggageService(SeatHoldService holds,BookingReviewSnapshotService snapshots,
      BookingBaggagePricingService pricing,BookingQuoteService quotes,PassengerValidationService validation,BookingReviewRepository reviews) {
    this.holds=holds; this.snapshots=snapshots; this.pricing=pricing; this.quotes=quotes; this.validation=validation; this.reviews=reviews;
  }
  public BaggageOptionsResponse options(AuthSession session,String id) {
    return holds.withUser(session.user().id(),()->{
      var result=snapshots.load(session,id);
      if(result.error()!=null) return Outcome.failed(result.error());
      var loaded=result.value();
      var s=loaded.snapshot();
      var current=pricing.calculate(s.quote(),s.passengers(),BookingReviewSnapshotService.selections(s));
      if(!current.baggage().equals(s.baggage()))
        throw new BookingException(409,"REVIEW_OUTDATED","Chính sách hành lý đã thay đổi. Vui lòng xác nhận lại lựa chọn.");
      return snapshots.finish(session,loaded,snapshots.options(s));
    });
  }
  public BookingReviewResponse save(AuthSession session,String id,BaggageSaveRequest request) {
    return holds.withUser(session.user().id(),()->{
      var result=snapshots.load(session,id);
      if(result.error()!=null) return Outcome.failed(result.error());
      var loaded=result.value();
      var old=loaded.snapshot();
      if(loaded.review().version()!=request.expectedVersion()) throw BookingReviewSnapshotService.conflict();
      var selections=old.quote().legs().stream().map(l->new BookingQuoteRequest.Selection(l.direction(),l.flightId(),l.cabinClass())).toList();
      var prepared=quotes.prepare(session.user().id(),new BookingQuoteRequest(old.seatHoldId(),old.quote().tripType(),old.quote().passengerCounts(),selections));
      if(prepared.error()!=null) return Outcome.failed(prepared.error());
      var quote=prepared.value().quote();
      var checked=validation.validate(old.passengers(),old.contact(),quote);
      var calculated=pricing.calculate(quote,checked.passengers(),request.selections());
      var benefits=BookingReviewSnapshotService.benefits(quote,checked.passengers());
      if(old.quote().equals(quote) && old.passengers().equals(checked.passengers()) && old.contact().equals(checked.contact())
          && old.passengerBenefits().equals(benefits) && old.baggage().equals(calculated.baggage()) && old.price().equals(calculated.price()))
        return snapshots.finish(session,loaded,snapshots.response(old));
      if(old.version()>=4294967295L) throw BookingReviewSnapshotService.conflict();
      var updated=new BookingReviewSnapshot(2,old.reviewId(),old.seatHoldId(),old.userId(),old.sessionId(),old.createdAt(),old.expiresAt(),
          quote,checked.passengers(),checked.contact(),benefits,old.version()+1,calculated.baggage(),calculated.price());
      byte[] ciphertext=snapshots.encode(updated,loaded.hold());
      var response=snapshots.response(updated);
      var finished=snapshots.finish(session,loaded,response);
      if(finished.error()!=null) return finished;
      if(!reviews.update(id,session.user().id(),session.id(),old.version(),2,holds.now(),ciphertext)) {
        var expired=snapshots.finish(session,loaded,response);
        if(expired.error()!=null) return expired;
        throw BookingReviewSnapshotService.conflict();
      }
      return Outcome.ok(response);
    });
  }
}
