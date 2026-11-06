package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.BookingQuote;
import com.skygo.backend.booking.model.BookingReviewSnapshot.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import com.skygo.backend.booking.repository.CheckedBaggagePolicyRepository;
import com.skygo.backend.booking.repository.CheckedBaggagePolicyRepository.Policy;
import java.math.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BookingBaggagePricingService {
  private final CheckedBaggagePolicyRepository policies;
  private final Clock clock;
  public BookingBaggagePricingService(CheckedBaggagePolicyRepository policies,Clock clock) {
    this.policies=policies; this.clock=clock;
  }
  public record Calculation(List<BaggageLeg> baggage,Price price) {}
  private record Key(String passenger,Direction direction) {}
  private record FareKey(long fare,PassengerType type) {}

  public static List<BaggageSaveRequest.Selection> zeroSelections(BookingQuote quote,List<PassengerRequest> passengers) {
    return quote.legs().stream().flatMap(l->passengers.stream().filter(p->p.passengerType()!=PassengerType.INFANT)
        .map(p->new BaggageSaveRequest.Selection(p.clientPassengerId(),l.direction(),0))).toList();
  }
  public Calculation calculate(BookingQuote quote,List<PassengerRequest> passengers,List<BaggageSaveRequest.Selection> input) {
    var expected=new HashSet<Key>();
    for(var leg:quote.legs()) for(var p:passengers) if(p.passengerType()!=PassengerType.INFANT)
      expected.add(new Key(p.clientPassengerId(),leg.direction()));
    var selections=new HashMap<Key,Integer>();
    for(var s:input) {
      var key=new Key(s.clientPassengerId(),s.direction());
      if(!expected.contains(key) || s.extraPieces()==null || s.extraPieces()<0 || s.extraPieces()>2
          || selections.put(key,s.extraPieces())!=null) throw selectionError();
    }
    if(!selections.keySet().equals(expected)) throw selectionError();
    var byFare=new HashMap<FareKey,Policy>();
    for(var row:policies.forFares(quote.legs().stream().map(BookingQuote.Leg::fareId).distinct().sorted().toList()))
      byFare.put(new FareKey(row.fareId(),row.passengerType()),row.policy());
    var baggage=new ArrayList<BaggageLeg>();
    long total=0;
    try {
      for(var leg:quote.legs().stream().sorted(Comparator.comparing(BookingQuote.Leg::direction)).toList()) {
        CheckedPolicy common=null;
        var rows=new ArrayList<BaggageSelection>();
        for(var p:passengers) {
          var policy=byFare.get(new FareKey(leg.fareId(),p.passengerType()));
          requirePolicy(policy,quote.currency());
          var snapshot=new CheckedPolicy(policy.id(),policy.version(),policy.pieceMaxKg(),policy.maxLinearDimensionsCm(),
              policy.maxExtraPieces(),policy.extraPiecePriceExclVat(),policy.currency());
          if(common!=null && !common.equals(snapshot)) throw BookingPricingService.unavailable();
          common=snapshot;
          var benefit=leg.benefits().stream().filter(b->b.passengerType()==p.passengerType()).findFirst()
              .orElseThrow(BookingPricingService::unavailable);
          if(benefit.checkedPieces()>0 && benefit.checkedPieceMaxKg()!=policy.pieceMaxKg()) throw BookingPricingService.unavailable();
          int count=p.passengerType()==PassengerType.INFANT?0:selections.get(new Key(p.clientPassengerId(),leg.direction()));
          if(count>policy.maxExtraPieces()) throw selectionError();
          long amount=Math.multiplyExact(count,policy.extraPiecePriceExclVat());
          total=Math.addExact(total,amount);
          rows.add(new BaggageSelection(p.clientPassengerId(),count,amount));
        }
        baggage.add(new BaggageLeg(leg.direction(),common,List.copyOf(rows)));
      }
      return new Calculation(List.copyOf(baggage),price(quote.fareSubtotal(),total,quote.vatRate()));
    } catch(ArithmeticException ex) { throw BookingPricingService.unavailable(); }
  }
  public static Price price(long fare,long extra,BigDecimal vat) {
    try {
      if(fare<0 || extra<0 || vat==null || vat.compareTo(new BigDecimal("0.10"))!=0) throw BookingPricingService.unavailable();
      long subtotal=Math.addExact(fare,extra);
      long tax=BigDecimal.valueOf(subtotal).multiply(vat).setScale(0,RoundingMode.HALF_UP).longValueExact();
      return new Price(fare,extra,vat.setScale(6),tax,Math.addExact(subtotal,tax));
    } catch(ArithmeticException ex) { throw BookingPricingService.unavailable(); }
  }
  private void requirePolicy(Policy p,String currency) {
    var now=clock.instant();
    if(p==null || p.version()<1 || p.pieceMaxKg()<1 || p.maxLinearDimensionsCm()<1 || p.maxExtraPieces()<0
        || p.maxExtraPieces()>2 || p.extraPiecePriceExclVat()<1 || !currency.equals(p.currency())
        || p.effectiveFrom().isAfter(now) || p.effectiveTo()!=null && !p.effectiveTo().isAfter(now)) throw BookingPricingService.unavailable();
  }
  private static BookingException selectionError() {
    return BookingException.invalid("selections","Gửi đúng một lựa chọn 0–2 kiện cho mỗi người lớn/trẻ em trên từng chiều; em bé không mua riêng.");
  }
}
