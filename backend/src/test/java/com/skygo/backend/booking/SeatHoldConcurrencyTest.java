package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.TripType;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class SeatHoldConcurrencyTest extends BookingFixture {
  @Test
  void onlyOneBuyerCanReserveTheLastSeat() throws Exception {
    jdbc.update("UPDATE flight_fare SET capacity=1,available_seats=1 WHERE id=?",outFare);
    var results=race(List.of(() ->attempt(user),() ->attempt(otherUser)));
    assertEquals(1,results.stream().filter("CREATED"::equals).count());
    assertEquals(1,results.stream().filter("INSUFFICIENT_CAPACITY"::equals).count());
    assertEquals(0,seats(outFare));
  }
  @Test
  void sameAccountCannotCreateTwoActiveHolds() throws Exception {
    var results=race(List.of(() ->attempt(user),() ->attempt(user)));
    assertEquals(1,results.stream().filter("CREATED"::equals).count());
    assertEquals(1,results.stream().filter("ACTIVE_HOLD_EXISTS"::equals).count());
    assertEquals(3,seats(outFare));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE active_user_id=?",Integer.class,user));
  }
  @Test
  void concurrentExpiryReturnsSeatsOnce() throws Exception {
    var h=create(user,TripType.ONE_WAY,2);
    org.mockito.Mockito.when(clock.instant()).thenReturn(h.expiresAt());
    race(List.of(() -> {expiry.expireDue();return "OK";},() -> {expiry.expireDue();return "OK";}));
    assertEquals(4,seats(outFare));
  }
  String attempt(long owner) {
    try { create(owner,TripType.ONE_WAY,1);return "CREATED"; }
    catch(BookingException ex) { return ex.code; }
  }
  List<String> race(List<Callable<String>> actions) throws Exception {
    var gate=new CountDownLatch(1);
    try(var executor=Executors.newFixedThreadPool(actions.size())) {
      var futures=new ArrayList<Future<String>>();
      for(var action:actions) futures.add(executor.submit(() -> {gate.await();return action.call();}));
      gate.countDown();
      var result=new ArrayList<String>();
      for(var future:futures) result.add(future.get(15,TimeUnit.SECONDS));
      return result;
    }
  }
}
