package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.PassengerCountsRequest;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.*;
import com.skygo.backend.booking.model.BookingQuote.*;
import com.skygo.backend.booking.model.SeatHold.CabinClass;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.BookingPricingRepository.FareData;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BookingPricingService {
  private final BookingPricingRepository repository;
  private final FlightInventoryRepository inventory;
  private final Clock clock;
  private final String airlineCode,airlineName;
  public BookingPricingService(BookingPricingRepository repository,FlightInventoryRepository inventory,
      Clock clock,@Value("${skygo.airline.code}") String airlineCode,@Value("${skygo.airline.name}") String airlineName) {
    this.repository=repository; this.inventory=inventory; this.clock=clock;
    this.airlineCode=airlineCode; this.airlineName=airlineName;
  }
  public BookingQuote calculate(SeatHold hold) {
    try {
      // Lock every cabin of selected flights before shared catalog reads to avoid cross-cabin deadlocks.
      inventory.lock(repository.allCabinIds(hold.legs().stream().map(SeatHold.Leg::fareId).toList()));
      var legs=new ArrayList<Leg>();
      long subtotal=0;
      BigDecimal vat=null;
      for(var held:hold.legs()) {
        inventory.requireAvailable(inventory.reservationFare(held.fareId()),clock.instant());
        var fare=repository.fare(held.fareId());
        verifyPrice(fare);
        if(vat!=null && vat.compareTo(fare.vatRate())!=0) throw unavailable();
        vat=fare.vatRate();
        String scope=scope(fare.origin().countryCode(),fare.destination().countryCode());
        var lines=new ArrayList<FareLine>();
        var benefits=new ArrayList<Benefit>();
        for(var type:PassengerType.values()) {
          int quantity=switch(type) {case ADULT->hold.adults();case CHILD->hold.children();case INFANT->hold.infants();};
          if(quantity==0) continue;
          BigDecimal multiplier=type==PassengerType.ADULT?BigDecimal.ONE:repository.multiplier(scope,type).orElseThrow(() -> {
            if(scope.equals("OTHER_DOMESTIC") && type==PassengerType.CHILD)
              return new BookingException(422,"UNSUPPORTED_FARE_RULE","Chưa có cấu hình giá trẻ em cho hành trình này.");
            return unavailable();
          });
          long unit=unitFare(fare.baseFare(),multiplier);
          long amount=Math.multiplyExact(unit,quantity);
          subtotal=Math.addExact(subtotal,amount);
          lines.add(new FareLine(type,quantity,unit,amount));
          benefits.add(repository.benefit(fare.fareId(),type).orElseThrow(BookingPricingService::unavailable));
        }
        boolean domestic=fare.origin().countryCode().equals(fare.destination().countryCode());
        legs.add(new Leg(held.direction(),fare.fareId(),fare.flightId(),fare.flightNumber(),airlineCode,airlineName,
            CabinClass.valueOf(fare.cabin()),fare.origin(),fare.destination(),fare.departure(),fare.arrival(),
            Duration.between(fare.departure(),fare.arrival()).toMinutes(),domestic?"DOMESTIC":"INTERNATIONAL",
            fare.policyId(),fare.policyVersion(),fare.distance(),fare.baseFare().longValueExact(),List.copyOf(lines),List.copyOf(benefits)));
      }
      long tax=BigDecimal.valueOf(subtotal).multiply(vat).setScale(0,RoundingMode.HALF_UP).longValueExact();
      return new BookingQuote(hold.tripType(),new PassengerCountsRequest(hold.adults(),hold.children(),hold.infants()),
          List.copyOf(legs),subtotal,vat,tax,Math.addExact(subtotal,tax),"VND");
    } catch(ArithmeticException ex) { throw unavailable(); }
  }
  public static long unitFare(BigDecimal adult,BigDecimal multiplier) {
    return adult.multiply(multiplier).setScale(0,RoundingMode.HALF_UP).longValueExact();
  }
  private void verifyPrice(FareData fare) {
    if(!fare.currency().equals("VND") || fare.distance()!=fare.routeDistance()
        || fare.vatRate().compareTo(new BigDecimal("0.10"))!=0) throw unavailable();
    var economy=fare.fixedFare().add(fare.perKm().multiply(BigDecimal.valueOf(fare.distance())));
    for(var sibling:repository.siblings(fare.flightDbId())) {
      var expected=economy.multiply(sibling.cabin().equals("ECONOMY")?BigDecimal.ONE:fare.businessMultiplier())
          .setScale(0,RoundingMode.HALF_UP);
      if(sibling.policyId()!=fare.policyId() || sibling.distance()!=fare.distance()
          || expected.compareTo(sibling.baseFare())!=0) throw unavailable();
    }
  }
  public static String scope(String origin,String destination) {
    return !origin.equals(destination)?"INTERNATIONAL":origin.equals("VN")?"VN_DOMESTIC":"OTHER_DOMESTIC";
  }
  static BookingException unavailable() {
    return new BookingException(409,"PRICING_UNAVAILABLE","Cấu hình giá hoặc quyền lợi chưa hợp lệ. Vui lòng thử lại sau.");
  }
}
