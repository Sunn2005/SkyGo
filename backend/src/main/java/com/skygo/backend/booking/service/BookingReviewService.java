package com.skygo.backend.booking.service;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.model.BookingReviewSnapshot;
import com.skygo.backend.booking.repository.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingReviewService {
  private final SeatHoldService holds;
  private final BookingQuoteService quotes;
  private final PassengerValidationService validation;
  private final BookingReviewRepository repository;
  private final SeatHoldRepository holdRepository;
  private final SeatHoldExpiryService expiry;
  private final BookingReviewSnapshotService snapshots;
  private final BookingBaggagePricingService pricing;
  public BookingReviewService(SeatHoldService holds,BookingQuoteService quotes,PassengerValidationService validation,
      BookingReviewRepository repository,SeatHoldRepository holdRepository,SeatHoldExpiryService expiry,
      BookingReviewSnapshotService snapshots,BookingBaggagePricingService pricing) {
    this.holds=holds; this.quotes=quotes; this.validation=validation; this.repository=repository;
    this.holdRepository=holdRepository; this.expiry=expiry; this.snapshots=snapshots; this.pricing=pricing;
  }
  public BookingReviewResponse get(AuthSession session,String id) {
    return holds.withUser(session.user().id(),()->{
      var result=snapshots.load(session,id);
      if(result.error()!=null) return Outcome.failed(result.error());
      return snapshots.finish(session,result.value(),snapshots.response(result.value().snapshot()));
    });
  }
  public BookingReviewResponse create(AuthSession session,BookingReviewRequest request) {
    long user=session.user().id();
    return holds.withUser(user,()->{
      repository.requireSession(user,session.id(),holds.now());
      var prepared=quotes.prepare(user,request.quoteRequest());
      if(prepared.error()!=null) return Outcome.failed(prepared.error());
      var h=prepared.value().hold();
      var q=prepared.value().quote();
      var checked=validation.validate(request.passengers(),request.contact(),q);
      var calculated=pricing.calculate(q,checked.passengers(),BookingBaggagePricingService.zeroSelections(q,checked.passengers()));
      String id=UUID.randomUUID().toString();
      var now=holds.now();
      var snapshot=new BookingReviewSnapshot(2,id,h.publicId(),user,session.id(),now,h.expiresAt(),q,checked.passengers(),checked.contact(),
          BookingReviewSnapshotService.benefits(q,checked.passengers()),1L,calculated.baggage(),calculated.price());
      byte[] ciphertext=snapshots.encode(snapshot,h);
      var response=snapshots.response(snapshot);
      if(expiry.expireLocked(h)) return Outcome.failed(SeatHoldService.expired());
      repository.requireSession(user,session.id(),holds.now());
      holdRepository.invalidateReviews(h.id(),holds.now());
      repository.save(id,user,session.id(),h.id(),2,now,h.expiresAt(),ciphertext);
      return Outcome.ok(response);
    });
  }
}
