package com.skygo.backend.booking.service;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.BaggageOptionsResponse.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.BookingReviewRepository.StoredReview;
import com.skygo.backend.booking.repository.CheckedBaggagePolicyRepository.Policy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingBaggageService {
  private final SeatHoldService holds;
  private final SeatHoldRepository holdRepository;
  private final SeatHoldExpiryService expiry;
  private final BookingReviewRepository reviews;
  private final CheckedBaggagePolicyRepository policies;
  private final ReviewEncryptionService encryption;
  private final ObjectMapper mapper;

  public BookingBaggageService(SeatHoldService holds,SeatHoldRepository holdRepository,
      SeatHoldExpiryService expiry,BookingReviewRepository reviews,CheckedBaggagePolicyRepository policies,
      ReviewEncryptionService encryption,ObjectMapper mapper) {
    this.holds=holds; this.holdRepository=holdRepository; this.expiry=expiry;
    this.reviews=reviews; this.policies=policies; this.encryption=encryption; this.mapper=mapper;
  }

  public BaggageOptionsResponse options(AuthSession session,String reviewId) {
    long user=session.user().id();
    return holds.withUser(user,()->{
      reviews.requireSession(user,session.id(),holds.now());
      var review=reviews.owned(reviewId,user,session.id()).orElseThrow(BookingBaggageService::notFound);
      var hold=holdRepository.byId(review.holdId()).filter(h->h.userId()==user)
          .orElseThrow(BookingBaggageService::notFound);
      if(review.used() || hold.bookingId()!=null)
        throw new BookingException(409,"REVIEW_ALREADY_USED","Bản rà soát đã được dùng để tạo đơn.");
      if(!hold.status().equals("ACTIVE")) throw SeatHoldService.expired();
      if(expiry.expireLocked(hold)) return Outcome.failed(SeatHoldService.expired());
      requireUsable(review);
      var snapshot=read(review,session,hold);
      validate(snapshot,review,session,hold);
      var response=response(snapshot);
      // A request can wait on policy locks until after expiry; commit seat release before returning 409.
      if(expiry.expireLocked(hold)) return Outcome.failed(SeatHoldService.expired());
      requireUsable(review);
      reviews.requireSession(user,session.id(),holds.now());
      return Outcome.ok(response);
    });
  }

  private void requireUsable(StoredReview review) {
    if(!review.expiresAt().isAfter(holds.now()))
      throw new BookingException(409,"REVIEW_EXPIRED","Bản rà soát đã hết hạn. Vui lòng xác nhận lại thông tin.");
    if(review.invalidated() || review.ciphertext()==null || review.schemaVersion()!=1) throw invalid();
  }

  private BookingReviewSnapshot read(StoredReview review,AuthSession session,SeatHold hold) {
    byte[] payload=encryption.decrypt(review.ciphertext(),new ReviewEncryptionService.Context(
        review.publicId(),session.user().id(),session.id(),hold.id(),review.schemaVersion()));
    try { return mapper.readValue(payload,BookingReviewSnapshot.class); }
    catch(JacksonException|IllegalArgumentException ex) { throw invalid(); }
    finally { Arrays.fill(payload,(byte)0); }
  }

  private record BenefitKey(String passengerId,Direction direction) {}
  private record PolicyKey(long fareId,PassengerType passengerType) {}

  private void validate(BookingReviewSnapshot s,StoredReview review,AuthSession session,SeatHold hold) {
    if(s==null || s.schemaVersion()!=review.schemaVersion() || !review.publicId().equals(s.reviewId())
        || s.userId()!=session.user().id() || s.sessionId()!=session.id() || !hold.publicId().equals(s.seatHoldId())
        || !review.expiresAt().equals(s.expiresAt()) || !hold.expiresAt().equals(s.expiresAt())
        || s.quote()==null || s.quote().tripType()!=hold.tripType() || !"VND".equals(s.quote().currency())
        || s.passengers()==null || s.passengerBenefits()==null || s.quote().legs()==null) throw invalid();
    var counts=s.quote().passengerCounts();
    if(!new PassengerCountsRequest(hold.adults(),hold.children(),hold.infants()).equals(counts)) throw invalid();
    var ids=new HashSet<String>();
    var actual=new EnumMap<PassengerType,Integer>(PassengerType.class);
    for(var p:s.passengers()) {
      if(p==null || p.clientPassengerId()==null || !ids.add(p.clientPassengerId()) || p.passengerType()==null
          || p.surname()==null || p.givenNames()==null) throw invalid();
      actual.merge(p.passengerType(),1,Integer::sum);
    }
    if(actual.getOrDefault(PassengerType.ADULT,0)!=hold.adults()
        || actual.getOrDefault(PassengerType.CHILD,0)!=hold.children()
        || actual.getOrDefault(PassengerType.INFANT,0)!=hold.infants()) throw invalid();
    int expected=hold.tripType()==SeatHold.TripType.ONE_WAY?1:2;
    if(s.quote().legs().size()!=expected || hold.legs().size()!=expected) throw invalid();
    var directions=EnumSet.noneOf(Direction.class);
    for(var leg:s.quote().legs()) {
      if(leg==null || leg.direction()==null || !directions.add(leg.direction()) || leg.cabinClass()==null
          || leg.origin()==null || leg.destination()==null || leg.departureAt()==null
          || hold.legs().stream().noneMatch(h->h.direction()==leg.direction() && h.fareId()==leg.fareId())) throw invalid();
    }
    if(!directions.contains(Direction.OUTBOUND)) throw invalid();
    var keys=new HashSet<BenefitKey>();
    for(var benefit:s.passengerBenefits()) {
      if(benefit==null || !ids.contains(benefit.clientPassengerId()) || !directions.contains(benefit.direction())
          || benefit.benefit()==null || !keys.add(new BenefitKey(benefit.clientPassengerId(),benefit.direction()))) throw invalid();
    }
    if((long)keys.size()!=(long)ids.size()*expected) throw invalid();
  }

  private BaggageOptionsResponse response(BookingReviewSnapshot snapshot) {
    var quote=snapshot.quote();
    var policyByFare=new HashMap<PolicyKey,Policy>();
    for(var row:policies.forFares(quote.legs().stream().map(BookingQuote.Leg::fareId).distinct().sorted().toList()))
      policyByFare.put(new PolicyKey(row.fareId(),row.passengerType()),row.policy());
    var benefits=new HashMap<BenefitKey,BookingQuote.Benefit>();
    for(var row:snapshot.passengerBenefits())
      benefits.put(new BenefitKey(row.clientPassengerId(),row.direction()),row.benefit());
    var passengers=snapshot.passengers().stream().map(p->new Passenger(
        p.clientPassengerId(),p.surname()+" "+p.givenNames(),p.passengerType())).toList();
    var legs=new ArrayList<Leg>();
    for(var leg:quote.legs().stream().sorted(Comparator.comparing(BookingQuote.Leg::direction)).toList()) {
      Policy common=null;
      var baggage=new ArrayList<PassengerBaggage>();
      for(var p:snapshot.passengers()) {
        var policy=policyByFare.get(new PolicyKey(leg.fareId(),p.passengerType()));
        requirePolicy(policy,quote.currency());
        if(common!=null && !common.equals(policy)) throw BookingPricingService.unavailable();
        common=policy;
        var benefit=benefits.get(new BenefitKey(p.clientPassengerId(),leg.direction()));
        if(benefit.passengerType()!=p.passengerType()) throw invalid();
        if(benefit.checkedPieces()>0 && benefit.checkedPieceMaxKg()!=policy.pieceMaxKg())
          throw BookingPricingService.unavailable();
        boolean eligible=p.passengerType()!=PassengerType.INFANT;
        if(eligible && benefit.seatType()==null) throw invalid();
        // Schema 1 reviews contain included benefits only. Opening this screen never saves a selection.
        baggage.add(new PassengerBaggage(p.clientPassengerId(),new Included(benefit.carryOnMaxKg(),
            benefit.checkedPieces(),benefit.strollerPieces(),benefit.strollerMaxKg()),
            eligible && policy.maxExtraPieces()>0,0,0));
      }
      LocalDate departureDate;
      try { departureDate=leg.departureAt().atZone(ZoneId.of(leg.origin().timeZone())).toLocalDate(); }
      catch(DateTimeException|NullPointerException ex) { throw invalid(); }
      legs.add(new Leg(leg.direction(),leg.origin().code(),leg.destination().code(),departureDate,leg.cabinClass(),
          new CheckedBaggagePolicy(common.pieceMaxKg(),common.maxLinearDimensionsCm(),common.maxExtraPieces(),
              common.extraPiecePriceExclVat()),List.copyOf(baggage)));
    }
    return new BaggageOptionsResponse(snapshot.expiresAt(),quote.currency(),quote.passengerCounts(),passengers,List.copyOf(legs),0);
  }

  private void requirePolicy(Policy policy,String currency) {
    var now=holds.now();
    if(policy==null || policy.version()<1 || policy.pieceMaxKg()<1 || policy.maxLinearDimensionsCm()<1
        || policy.maxExtraPieces()<0 || policy.maxExtraPieces()>2 || policy.extraPiecePriceExclVat()<1
        || !currency.equals(policy.currency()) || policy.effectiveFrom().isAfter(now)
        || policy.effectiveTo()!=null && !policy.effectiveTo().isAfter(now)) throw BookingPricingService.unavailable();
  }
  private static BookingException notFound() {
    return new BookingException(404,"REVIEW_NOT_FOUND","Không tìm thấy bản rà soát trong phiên đăng nhập hiện tại.");
  }
  private static BookingException invalid() {
    return new BookingException(409,"REVIEW_INVALID","Bản rà soát không còn hợp lệ. Vui lòng xác nhận lại thông tin.");
  }
}
