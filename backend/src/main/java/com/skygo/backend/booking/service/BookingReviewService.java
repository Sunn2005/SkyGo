package com.skygo.backend.booking.service;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.model.BookingReviewSnapshot;
import com.skygo.backend.booking.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingReviewService {
  private static final int SCHEMA_VERSION=1;
  private final SeatHoldService holds;
  private final BookingQuoteService quotes;
  private final PassengerValidationService validation;
  private final BookingReviewRepository repository;
  private final SeatHoldRepository holdRepository;
  private final SeatHoldExpiryService expiry;
  private final ReviewEncryptionService encryption;
  private final ObjectMapper mapper;
  public BookingReviewService(SeatHoldService holds,BookingQuoteService quotes,PassengerValidationService validation,
      BookingReviewRepository repository,SeatHoldRepository holdRepository,SeatHoldExpiryService expiry,
      ReviewEncryptionService encryption,ObjectMapper mapper) {
    this.holds=holds; this.quotes=quotes; this.validation=validation; this.repository=repository;
    this.holdRepository=holdRepository; this.expiry=expiry; this.encryption=encryption; this.mapper=mapper;
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
      String id=UUID.randomUUID().toString();
      var now=holds.now();
      var benefits=new ArrayList<BookingReviewSnapshot.PassengerBenefit>();
      for(var p:checked.passengers()) for(var leg:q.legs())
        benefits.add(new BookingReviewSnapshot.PassengerBenefit(p.clientPassengerId(),leg.direction(),
            leg.benefits().stream().filter(b->b.passengerType()==p.passengerType()).findFirst().orElseThrow()));
      var snapshot=new BookingReviewSnapshot(SCHEMA_VERSION,id,h.publicId(),user,session.id(),now,h.expiresAt(),
          q,checked.passengers(),checked.contact(),List.copyOf(benefits));
      byte[] payload=mapper.writeValueAsBytes(snapshot);
      byte[] ciphertext;
      try { ciphertext=encryption.encrypt(payload,new ReviewEncryptionService.Context(id,user,session.id(),h.id(),SCHEMA_VERSION)); }
      finally { Arrays.fill(payload,(byte)0); }
      if(expiry.expireLocked(h)) return Outcome.failed(SeatHoldService.expired());
      repository.requireSession(user,session.id(),holds.now());
      holdRepository.invalidateReviews(h.id(),holds.now());
      repository.save(id,user,session.id(),h.id(),SCHEMA_VERSION,now,h.expiresAt(),ciphertext);
      var passengers=checked.passengers().stream().map(p->new BookingReviewResponse.Passenger(
          p.clientPassengerId(),p.passengerType(),p.title(),p.givenNames(),p.surname(),p.dateOfBirth(),p.gender(),
          new BookingReviewResponse.Document(p.document().documentType(),PassengerValidationService.mask(p.document().number()),
              p.document().issuingCountryCode(),p.document().nationalityCountryCode(),p.document().expiresOn()))).toList();
      return Outcome.ok(new BookingReviewResponse(id,h.expiresAt(),q.totalAmount(),q.currency(),passengers,checked.contact()));
    });
  }
}
