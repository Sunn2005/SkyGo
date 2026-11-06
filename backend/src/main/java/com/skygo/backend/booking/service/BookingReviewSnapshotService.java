package com.skygo.backend.booking.service;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.*;
import com.skygo.backend.booking.model.BookingReviewSnapshot.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.BookingReviewRepository.StoredReview;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import static com.skygo.backend.booking.service.SeatHoldService.Outcome;

@Service
public class BookingReviewSnapshotService {
  private final SeatHoldService holds;
  private final SeatHoldRepository holdRepository;
  private final SeatHoldExpiryService expiry;
  private final BookingReviewRepository reviews;
  private final BookingBaggagePricingService pricing;
  private final ReviewEncryptionService encryption;
  private final ObjectMapper mapper;
  public BookingReviewSnapshotService(SeatHoldService holds,SeatHoldRepository holdRepository,SeatHoldExpiryService expiry,
      BookingReviewRepository reviews,BookingBaggagePricingService pricing,ReviewEncryptionService encryption,ObjectMapper mapper) {
    this.holds=holds; this.holdRepository=holdRepository; this.expiry=expiry;
    this.reviews=reviews; this.pricing=pricing; this.encryption=encryption; this.mapper=mapper;
  }
  public record Loaded(StoredReview review,SeatHold hold,BookingReviewSnapshot snapshot) {}
  Outcome<Loaded> load(AuthSession session,String id) {
    long user=session.user().id();
    reviews.requireSession(user,session.id(),holds.now());
    var review=reviews.owned(id,user,session.id()).orElseThrow(
        ()->new BookingException(404,"REVIEW_NOT_FOUND","Không tìm thấy bản rà soát trong phiên đăng nhập hiện tại."));
    var hold=holdRepository.byId(review.holdId()).filter(h->h.userId()==user).orElseThrow(BookingReviewSnapshotService::invalid);
    if(review.used() || hold.bookingId()!=null) throw new BookingException(409,"REVIEW_ALREADY_USED","Bản rà soát đã được dùng để tạo đơn.");
    if(!hold.status().equals("ACTIVE")) throw SeatHoldService.expired();
    if(expiry.expireLocked(hold)) return Outcome.failed(SeatHoldService.expired());
    requireUsable(review);
    byte[] payload=encryption.decrypt(review.ciphertext(),new ReviewEncryptionService.Context(
        review.publicId(),user,session.id(),hold.id(),review.schemaVersion(),review.version()));
    BookingReviewSnapshot s;
    try { s=mapper.readValue(payload,BookingReviewSnapshot.class); }
    catch(JacksonException|IllegalArgumentException ex) { throw invalid(); }
    finally { Arrays.fill(payload,(byte)0); }
    try {
      validateBase(s,review,session,hold);
      if(review.schemaVersion()==1) {
        if(review.version()!=1 || s.version()!=null && s.version()!=1) throw invalid();
        var calculated=pricing.calculate(s.quote(),s.passengers(),BookingBaggagePricingService.zeroSelections(s.quote(),s.passengers()));
        s=new BookingReviewSnapshot(1,s.reviewId(),s.seatHoldId(),s.userId(),s.sessionId(),s.createdAt(),s.expiresAt(),
            s.quote(),s.passengers(),s.contact(),s.passengerBenefits(),1L,calculated.baggage(),calculated.price());
      } else if(s.version()==null || s.version()!=review.version()) throw invalid();
      validateBaggage(s);
    } catch(NullPointerException|IllegalArgumentException|ArithmeticException ex) { throw invalid(); }
    return Outcome.ok(new Loaded(review,hold,s));
  }
  <T> Outcome<T> finish(AuthSession session,Loaded loaded,T response) {
    if(expiry.expireLocked(loaded.hold())) return Outcome.failed(SeatHoldService.expired());
    requireUsable(loaded.review());
    reviews.requireSession(session.user().id(),session.id(),holds.now());
    return Outcome.ok(response);
  }
  private void requireUsable(StoredReview review) {
    if(!review.expiresAt().isAfter(holds.now())) throw new BookingException(409,"REVIEW_EXPIRED","Bản rà soát đã hết hạn. Vui lòng xác nhận lại thông tin.");
    if(review.invalidated() || review.ciphertext()==null || review.version()<1 || review.schemaVersion()<1 || review.schemaVersion()>2) throw invalid();
  }
  byte[] encode(BookingReviewSnapshot s,SeatHold hold) {
    byte[] payload=mapper.writeValueAsBytes(s);
    try { return encryption.encrypt(payload,new ReviewEncryptionService.Context(s.reviewId(),s.userId(),s.sessionId(),hold.id(),s.schemaVersion(),s.version())); }
    finally { Arrays.fill(payload,(byte)0); }
  }
  static List<PassengerBenefit> benefits(BookingQuote quote,List<PassengerRequest> passengers) {
    var result=new ArrayList<PassengerBenefit>();
    for(var p:passengers) for(var leg:quote.legs()) result.add(new PassengerBenefit(p.clientPassengerId(),leg.direction(),
        leg.benefits().stream().filter(b->b.passengerType()==p.passengerType()).findFirst().orElseThrow(BookingReviewSnapshotService::invalid)));
    return List.copyOf(result);
  }
  public static List<BaggageSaveRequest.Selection> selections(BookingReviewSnapshot s) {
    var eligible=new HashSet<String>();
    for(var p:s.passengers()) if(p.passengerType()!=PassengerType.INFANT) eligible.add(p.clientPassengerId());
    return s.baggage().stream().flatMap(l->l.passengers().stream().filter(p->eligible.contains(p.clientPassengerId()))
        .map(p->new BaggageSaveRequest.Selection(p.clientPassengerId(),l.direction(),p.extraPieces()))).toList();
  }
  private record Key(String passenger,Direction direction) {}
  private void validateBase(BookingReviewSnapshot s,StoredReview r,AuthSession session,SeatHold hold) {
    if(s==null || s.schemaVersion()!=r.schemaVersion() || !r.publicId().equals(s.reviewId()) || s.userId()!=session.user().id()
        || s.sessionId()!=session.id() || !hold.publicId().equals(s.seatHoldId()) || !r.createdAt().equals(s.createdAt())
        || !r.expiresAt().equals(s.expiresAt()) || !hold.expiresAt().equals(s.expiresAt()) || s.quote()==null
        || s.quote().tripType()!=hold.tripType() || !"VND".equals(s.quote().currency()) || s.passengers()==null
        || s.passengerBenefits()==null || s.quote().legs()==null || s.contact()==null) throw invalid();
    if(!new PassengerCountsRequest(hold.adults(),hold.children(),hold.infants()).equals(s.quote().passengerCounts())) throw invalid();
    var passengers=new HashMap<String,PassengerRequest>();
    var actual=new EnumMap<PassengerType,Integer>(PassengerType.class);
    for(var p:s.passengers()) {
      if(p==null || p.clientPassengerId()==null || passengers.put(p.clientPassengerId(),p)!=null || p.passengerType()==null
          || p.surname()==null || p.givenNames()==null || p.document()==null) throw invalid();
      actual.merge(p.passengerType(),1,Integer::sum);
    }
    if(actual.getOrDefault(PassengerType.ADULT,0)!=hold.adults() || actual.getOrDefault(PassengerType.CHILD,0)!=hold.children()
        || actual.getOrDefault(PassengerType.INFANT,0)!=hold.infants()) throw invalid();
    int expected=hold.tripType()==SeatHold.TripType.ONE_WAY?1:2;
    if(s.quote().legs().size()!=expected || hold.legs().size()!=expected) throw invalid();
    var legs=new EnumMap<Direction,BookingQuote.Leg>(Direction.class);
    for(var leg:s.quote().legs()) {
      if(leg==null || leg.direction()==null || legs.put(leg.direction(),leg)!=null || leg.cabinClass()==null
          || leg.origin()==null || leg.destination()==null || leg.departureAt()==null || leg.arrivalAt()==null
          || hold.legs().stream().noneMatch(h->h.direction()==leg.direction() && h.fareId()==leg.fareId())) throw invalid();
    }
    if(!legs.containsKey(Direction.OUTBOUND)) throw invalid();
    var keys=new HashSet<Key>();
    for(var row:s.passengerBenefits()) {
      if(row==null || !passengers.containsKey(row.clientPassengerId()) || !legs.containsKey(row.direction()) || row.benefit()==null
          || !keys.add(new Key(row.clientPassengerId(),row.direction()))) throw invalid();
      var p=passengers.get(row.clientPassengerId()); var b=row.benefit();
      if(b.passengerType()!=p.passengerType() || b.carryOnMaxKg()<0 || b.checkedPieces()<0
          || (p.passengerType()==PassengerType.INFANT)!=(b.seatType()==null)
          || legs.get(row.direction()).benefits().stream().noneMatch(b::equals)) throw invalid();
    }
    if((long)keys.size()!=(long)passengers.size()*expected) throw invalid();
  }
  private void validateBaggage(BookingReviewSnapshot s) {
    if(s.baggage()==null || s.price()==null || s.baggage().size()!=s.quote().legs().size()) throw invalid();
    var types=new HashMap<String,PassengerType>(); s.passengers().forEach(p->types.put(p.clientPassengerId(),p.passengerType()));
    var directions=EnumSet.noneOf(Direction.class); long extra=0;
    for(var leg:s.baggage()) {
      if(leg==null || leg.direction()==null || !directions.add(leg.direction())
          || s.quote().legs().stream().noneMatch(l->l.direction()==leg.direction()) || leg.passengers()==null) throw invalid();
      var p=leg.policy();
      if(p==null || p.policyId()<1 || p.policyVersion()<1 || p.pieceMaxKg()<1 || p.maxLinearDimensionsCm()<1
          || p.maxExtraPieces()<0 || p.maxExtraPieces()>2 || p.extraPiecePriceExclVat()<1 || !s.quote().currency().equals(p.currency())) throw invalid();
      var ids=new HashSet<String>();
      for(var row:leg.passengers()) {
        if(row==null || !types.containsKey(row.clientPassengerId()) || !ids.add(row.clientPassengerId())
            || row.extraPieces()<0 || row.extraPieces()>p.maxExtraPieces() || types.get(row.clientPassengerId())==PassengerType.INFANT && row.extraPieces()!=0
            || row.extraAmountExclVat()!=Math.multiplyExact(row.extraPieces(),p.extraPiecePriceExclVat())) throw invalid();
        extra=Math.addExact(extra,row.extraAmountExclVat());
      }
      if(!ids.equals(types.keySet())) throw invalid();
      for(var b:s.passengerBenefits()) if(b.direction()==leg.direction() && b.benefit().checkedPieces()>0 && b.benefit().checkedPieceMaxKg()!=p.pieceMaxKg()) throw invalid();
    }
    if(!BookingBaggagePricingService.price(s.quote().fareSubtotal(),extra,s.quote().vatRate()).equals(s.price())) throw invalid();
  }
  BaggageOptionsResponse options(BookingReviewSnapshot s) {
    var passengers=s.passengers().stream().map(p->new BaggageOptionsResponse.Passenger(p.clientPassengerId(),p.surname()+" "+p.givenNames(),p.passengerType())).toList();
    var legs=s.quote().legs().stream().sorted(Comparator.comparing(BookingQuote.Leg::direction)).map(l->new BaggageOptionsResponse.Leg(
        l.direction(),l.origin().code(),l.destination().code(),localDate(l),l.cabinClass(),policy(s,l.direction()),baggage(s,l.direction()))).toList();
    return new BaggageOptionsResponse(s.version(),s.expiresAt(),s.quote().currency(),s.quote().passengerCounts(),passengers,legs,s.price().extraBaggageAmount());
  }
  BookingReviewResponse response(BookingReviewSnapshot s) {
    var passengers=s.passengers().stream().map(p->new BookingReviewResponse.Passenger(p.clientPassengerId(),p.passengerType(),p.title(),p.givenNames(),p.surname(),
        p.dateOfBirth(),p.gender(),new BookingReviewResponse.Document(p.document().documentType(),PassengerValidationService.mask(p.document().number()),
        p.document().issuingCountryCode(),p.document().nationalityCountryCode(),p.document().expiresOn()))).toList();
    List<BookingReviewResponse.Leg> legs;
    try {
      legs=s.quote().legs().stream().sorted(Comparator.comparing(BookingQuote.Leg::direction)).map(l->new BookingReviewResponse.Leg(l.direction(),l.flightId(),l.flightNumber(),
          new BookingReviewResponse.Airline(l.airlineCode(),l.airlineName()),l.cabinClass(),airport(l.origin()),airport(l.destination()),
          l.departureAt().atZone(ZoneId.of(l.origin().timeZone())).toOffsetDateTime(),l.arrivalAt().atZone(ZoneId.of(l.destination().timeZone())).toOffsetDateTime(),
          l.durationMinutes(),policy(s,l.direction()),baggage(s,l.direction()))).toList();
    } catch(DateTimeException|NullPointerException ex) { throw invalid(); }
    var p=s.price();
    return new BookingReviewResponse(s.reviewId(),s.version(),s.expiresAt(),p.totalAmount(),s.quote().currency(),passengers,s.contact(),s.quote().tripType(),
        s.quote().passengerCounts(),legs,new BookingReviewResponse.Price(p.fareSubtotal(),p.extraBaggageAmount(),p.vatRate(),p.vatAmount(),p.totalAmount()));
  }
  private BookingReviewResponse.Airport airport(BookingQuote.Airport a) { return new BookingReviewResponse.Airport(a.code(),a.name(),a.city(),a.countryCode()); }
  private LocalDate localDate(BookingQuote.Leg l) {
    try { return l.departureAt().atZone(ZoneId.of(l.origin().timeZone())).toLocalDate(); }
    catch(DateTimeException|NullPointerException ex) { throw invalid(); }
  }
  private BaggageOptionsResponse.CheckedBaggagePolicy policy(BookingReviewSnapshot s,Direction direction) {
    var p=s.baggage().stream().filter(l->l.direction()==direction).findFirst().orElseThrow(BookingReviewSnapshotService::invalid).policy();
    return new BaggageOptionsResponse.CheckedBaggagePolicy(p.pieceMaxKg(),p.maxLinearDimensionsCm(),p.maxExtraPieces(),p.extraPiecePriceExclVat());
  }
  private List<BaggageOptionsResponse.PassengerBaggage> baggage(BookingReviewSnapshot s,Direction direction) {
    var leg=s.baggage().stream().filter(l->l.direction()==direction).findFirst().orElseThrow(BookingReviewSnapshotService::invalid);
    return s.passengers().stream().map(p->{
      var b=s.passengerBenefits().stream().filter(row->row.direction()==direction && row.clientPassengerId().equals(p.clientPassengerId())).findFirst().orElseThrow(BookingReviewSnapshotService::invalid).benefit();
      var row=leg.passengers().stream().filter(r->r.clientPassengerId().equals(p.clientPassengerId())).findFirst().orElseThrow(BookingReviewSnapshotService::invalid);
      return new BaggageOptionsResponse.PassengerBaggage(p.clientPassengerId(),new BaggageOptionsResponse.Included(b.carryOnMaxKg(),b.checkedPieces(),b.strollerPieces(),b.strollerMaxKg()),
          p.passengerType()!=PassengerType.INFANT && leg.policy().maxExtraPieces()>0,row.extraPieces(),row.extraAmountExclVat());
    }).toList();
  }
  static BookingException conflict() { return new BookingException(409,"REVIEW_VERSION_CONFLICT","Thông tin đặt vé đã thay đổi. Vui lòng tải lại bản rà soát."); }
  static BookingException invalid() { return new BookingException(409,"REVIEW_INVALID","Bản rà soát không còn hợp lệ. Vui lòng xác nhận lại thông tin."); }
}
