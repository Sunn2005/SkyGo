package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.*;
import com.skygo.backend.booking.model.BookingQuote.*;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.*;
import com.skygo.backend.booking.repository.BookingPricingRepository.*;
import com.skygo.backend.booking.service.BookingPricingService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BookingPricingServiceTest {
  final BookingPricingRepository repository=mock(BookingPricingRepository.class);
  final FlightInventoryRepository inventory=mock(FlightInventoryRepository.class);
  final Instant now=Instant.parse("2030-01-01T00:00:00Z");
  final BookingPricingService service=new BookingPricingService(repository,inventory,Clock.fixed(now,ZoneOffset.UTC),"SG","SkyGo");
  @Test
  void calculatesGroupRatesAndOneVatOnTheOrderWithPreservedSnapshots() {
    configure("VN","VN");
    when(repository.multiplier("VN_DOMESTIC",PassengerType.CHILD)).thenReturn(Optional.of(new BigDecimal("0.9")));
    when(repository.multiplier("VN_DOMESTIC",PassengerType.INFANT)).thenReturn(Optional.of(new BigDecimal("0.1")));
    var quote=service.calculate(hold());
    assertEquals(1020000,quote.fareSubtotal());
    assertEquals(102000,quote.vatAmount());
    assertEquals(1122000,quote.totalAmount());
    assertEquals(3,quote.legs().getFirst().benefits().size());
    assertEquals("DOMESTIC",quote.legs().getFirst().routeType());
    assertEquals(51,BookingPricingService.unitFare(new BigDecimal("101"),new BigDecimal("0.5")));
    verify(inventory,never()).reserve(anyLong(),anyLong());
  }
  @Test
  void missingRulesOrBenefitsFailRatherThanProduceZeroPrices() {
    configure("US","US");
    assertEquals("UNSUPPORTED_FARE_RULE",assertThrows(BookingException.class,()->service.calculate(hold())).code);
    configure("VN","SG");
    when(repository.multiplier("INTERNATIONAL",PassengerType.CHILD)).thenReturn(Optional.of(new BigDecimal("0.75")));
    when(repository.multiplier("INTERNATIONAL",PassengerType.INFANT)).thenReturn(Optional.of(new BigDecimal("0.1")));
    assertEquals(1037850,service.calculate(hold()).totalAmount()); // (510000+382500+51000)*1.1
    when(repository.benefit(1,PassengerType.INFANT)).thenReturn(Optional.empty());
    assertEquals("PRICING_UNAVAILABLE",assertThrows(BookingException.class,()->service.calculate(hold())).code);
  }
  @Test
  void rejectsStaleOrInconsistentCabinPrices() {
    configure("VN","VN");
    when(repository.siblings(1)).thenReturn(List.of(new Sibling("ECONOMY",1,100,new BigDecimal("1"))));
    assertEquals("PRICING_UNAVAILABLE",assertThrows(BookingException.class,()->service.calculate(hold())).code);
  }
  SeatHold hold() { return new SeatHold(1,"h",1,null,TripType.ONE_WAY,1,1,1,"ACTIVE",now,now.plusSeconds(900),List.of(new SeatHold.Leg(Direction.OUTBOUND,1,2))); }
  void configure(String from,String to) {
    var o=new Airport("AAA","A","A",from,"UTC"); var d=new Airport("BBB","B","B",to,"UTC");
    when(repository.allCabinIds(anyList())).thenReturn(List.of(1L));
    when(repository.fare(1)).thenReturn(new FareData(1,1,"f","SG1","ECONOMY",o,d,now.plusSeconds(86400),
        now.plusSeconds(90000),1,1,100,100,new BigDecimal("510000"),new BigDecimal("250000"),new BigDecimal("2600"),new BigDecimal("2.5"),new BigDecimal("0.1"),"VND"));
    when(repository.siblings(1)).thenReturn(List.of(new Sibling("ECONOMY",1,100,new BigDecimal("510000"))));
    for(var type:PassengerType.values()) when(repository.benefit(1,type)).thenReturn(Optional.of(new Benefit(type,7,1,23,0,"STANDARD",0,0)));
  }
}
