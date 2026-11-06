package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.booking.model.SeatHold;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.FlightInventoryRepository.Fare;
import com.skygo.backend.booking.service.SeatHoldService;
import com.skygo.backend.booking.contract.PassengerCountsRequest;
import com.skygo.backend.booking.exception.BookingException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class SeatHoldServiceTest {
  @Test
  void deadlineIsExclusiveAndInfantsDoNotConsumeSeats() {
    Instant start=Instant.parse("2030-01-01T00:00:00Z");
    var hold=new SeatHold(1,"h",1,null,TripType.ONE_WAY,1,2,5,"ACTIVE",start,start.plusSeconds(900),List.of());
    assertEquals(3,hold.seats());
    assertFalse(hold.expired(start.plusSeconds(899)));
    assertTrue(hold.expired(start.plusSeconds(900)));
    assertEquals(4294967294L,new PassengerCountsRequest(Integer.MAX_VALUE,Integer.MAX_VALUE,0).seats());
  }
  @Test
  void returnMustReverseRouteAndHaveLaterLocalDate() {
    var out=fare("AAA","BBB","2030-01-01T15:00:00Z"); // Jan 1 in UTC.
    assertDoesNotThrow(() -> SeatHoldService.validatePair(Map.of(Direction.OUTBOUND,out,
        Direction.RETURN,fare("BBB","AAA","2030-01-02T15:00:00Z"))));
    assertThrows(BookingException.class,() -> SeatHoldService.validatePair(Map.of(Direction.OUTBOUND,out,
        Direction.RETURN,fare("AAA","BBB","2030-01-02T15:00:00Z"))));
    assertThrows(BookingException.class,() -> SeatHoldService.validatePair(Map.of(Direction.OUTBOUND,out,
        Direction.RETURN,fare("BBB","AAA","2030-01-01T20:00:00Z"))));
  }
  Fare fare(String from,String to,String departure) {
    Instant dep=Instant.parse(departure);
    return new Fare(1,"f","ECONOMY",from,from,"UTC",to,to,"UTC",dep,dep.plusSeconds(3600),true);
  }
}
