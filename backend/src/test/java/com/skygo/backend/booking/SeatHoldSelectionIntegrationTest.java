package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.service.*;
import com.skygo.backend.booking.repository.SeatHoldRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL", matches=".+")
class SeatHoldSelectionIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired BookingBaggageService baggage;
  @Autowired BookingCreationService bookings;
  @MockitoSpyBean SeatHoldRepository repository;

  @Test
  void replacesFlightAndCabinWithoutChangingHoldOrDeadline() throws Exception {
    var h=hold(TripType.ONE_WAY,2,0,0);
    String next=prefix+"-next";
    jdbc.update("""
        INSERT INTO flight(public_id,flight_number,route_id,departure_at_utc,arrival_at_utc,sale_status)
        SELECT ?,'SGNEXT',route_id,departure_at_utc+INTERVAL 1 HOUR,arrival_at_utc+INTERVAL 1 HOUR,'ON_SALE'
        FROM flight WHERE public_id=?
        """,next,outFlight);
    long nextFare=fare(jdbc.queryForObject("SELECT id FROM flight WHERE public_id=?",Long.class,next),"BUSINESS");
    var before=jdbc.queryForList("SELECT id,public_id,started_at_utc,expires_at_utc FROM seat_hold WHERE public_id=?",h.seatHoldId());
    var request=request(1,TripType.ONE_WAY,2,0,0,selection(Direction.OUTBOUND,next,CabinClass.BUSINESS));
    mvc.perform(put(path(h)).with(as(user)).contentType("application/json").content(mapper.writeValueAsString(request)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.seatHoldId").value(h.seatHoldId()))
        .andExpect(jsonPath("$.data.version").value(2))
        .andExpect(jsonPath("$.data.expiresAt").value(h.expiresAt().toString()))
        .andExpect(jsonPath("$.data.legs[0].flightId").value(next))
        .andExpect(jsonPath("$.data.legs[0].cabinClass").value("BUSINESS"));
    assertEquals(before,jdbc.queryForList("SELECT id,public_id,started_at_utc,expires_at_utc FROM seat_hold WHERE public_id=?",h.seatHoldId()));
    assertEquals(4,seats(outFare));
    assertEquals(2,seats(nextFare));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?",Integer.class,user));
  }

  @Test
  void invalidatesSavedBaggageReviewAndAllowsRebuildingWithPassengerDraft() {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var s=session(user);
    var passengers=List.of(passenger("a",PassengerType.ADULT));
    var old=reviews.create(s,reviewRequest(h,passengers));
    baggage.save(s,old.reviewId(),new BaggageSaveRequest(1L,
        List.of(new BaggageSaveRequest.Selection("a",Direction.OUTBOUND,2))));
    var changed=service.selection(user,h.seatHoldId(),business(1,1));
    assertNotNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",LocalDateTime.class,old.reviewId()));
    assertEquals("REVIEW_INVALID",assertThrows(BookingException.class,()->
        bookings.create(s,"old-review",new BookingCreateRequest(old.reviewId(),2L))).code);
    var rebuilt=reviews.create(s,reviewRequest(changed,passengers));
    var saved=baggage.save(s,rebuilt.reviewId(),new BaggageSaveRequest(1L,
        List.of(new BaggageSaveRequest.Selection("a",Direction.OUTBOUND,2))));
    assertEquals(2,saved.version());
    assertEquals(2,service.current(user).version());
    assertEquals(4,seats(outFare));
    assertEquals(3,seats(businessFare));
  }

  @Test
  void unchangedSelectionAtZeroAvailabilityPreservesReviewAndVersion() {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var s=session(user);
    var review=reviews.create(s,reviewRequest(h,List.of(passenger("a",PassengerType.ADULT))));
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",outFare);
    var before=jdbc.queryForList("SELECT * FROM seat_hold WHERE public_id=?",h.seatHoldId());
    var result=service.selection(user,h.seatHoldId(),request(1,TripType.ONE_WAY,1,0,0,out()));
    assertEquals(h,result);
    assertEquals(before,jdbc.queryForList("SELECT * FROM seat_hold WHERE public_id=?",h.seatHoldId()));
    assertNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",LocalDateTime.class,review.reviewId()));
    assertEquals(0,seats(outFare));
  }

  @Test
  void staleVersionAndRetryCannotOverwriteCurrentSelection() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var changed=service.selection(user,h.seatHoldId(),business(1,1));
    for(var stale:List.of(business(1,1),request(1,TripType.ONE_WAY,1,0,0,out())))
      mvc.perform(put(path(h)).with(as(user)).contentType("application/json").content(mapper.writeValueAsString(stale)))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_VERSION_CONFLICT"));
    assertEquals(changed,service.current(user));
    assertEquals(changed,service.selection(user,h.seatHoldId(),business(2,1)));
    assertEquals(3,seats(businessFare));
  }

  @Test
  void insufficientSecondLegRollsBackReleasedSeatsVersionAndReview() {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var review=reviews.create(session(user),reviewRequest(h,List.of(passenger("a",PassengerType.ADULT))));
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",backFare);
    var request=request(1,TripType.ROUND_TRIP,2,0,0,
        selection(Direction.OUTBOUND,outFlight,CabinClass.BUSINESS),back());
    assertEquals("INSUFFICIENT_CAPACITY",assertThrows(BookingException.class,()->service.selection(user,h.seatHoldId(),request)).code);
    assertEquals(h,service.current(user));
    assertEquals(3,seats(outFare));
    assertEquals(4,seats(businessFare));
    assertEquals(0,seats(backFare));
    assertNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",LocalDateTime.class,review.reviewId()));
  }

  @Test
  void adjustsOnlySeatDifferencesAndInfantChangeDoesNotNeedExtraSeats() {
    var h=hold(TripType.ONE_WAY,4,0,0);
    assertEquals(0,seats(outFare));
    var infants=service.selection(user,h.seatHoldId(),request(1,TripType.ONE_WAY,4,0,1,out()));
    assertEquals(2,infants.version());
    assertEquals(0,seats(outFare));
    var reduced=service.selection(user,h.seatHoldId(),request(2,TripType.ONE_WAY,1,1,1,out()));
    assertEquals(3,reduced.version());
    assertEquals(2,seats(outFare));
    service.selection(user,h.seatHoldId(),request(3,TripType.ONE_WAY,2,1,1,out()));
    assertEquals(1,seats(outFare));
    assertEquals(4,service.current(user).version());
    assertEquals(h.expiresAt(),service.current(user).expiresAt());
  }

  @Test
  void replacesWholeRoundTripAndRemovesOmittedReturn() {
    var h=hold(TripType.ONE_WAY,2,0,0);
    var round=service.selection(user,h.seatHoldId(),request(1,TripType.ROUND_TRIP,2,0,0,out(),back()));
    assertEquals(2,round.legs().size());
    assertEquals(2,seats(backFare));
    var partial=service.selection(user,h.seatHoldId(),request(2,TripType.ROUND_TRIP,2,0,0,out()));
    assertEquals("ROUND_TRIP",partial.tripType());
    assertEquals(1,partial.legs().size());
    assertEquals(4,seats(backFare));
    var one=service.selection(user,h.seatHoldId(),request(3,TripType.ONE_WAY,2,0,0,out()));
    assertEquals("ONE_WAY",one.tripType());
    assertEquals(4,one.version());
    assertEquals(2,seats(outFare));
  }

  @Test
  void validatesDirectionCountsAndStrictIntegerVersion() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var valid=mapper.writeValueAsString(business(1,1));
    for(String invalid:List.of("{}","{",valid.replace("\"expectedVersion\":1","\"expectedVersion\":0"),
        valid.replace("\"expectedVersion\":1","\"expectedVersion\":1.5"),
        valid.replace("\"expectedVersion\":1","\"expectedVersion\":\"1\""),
        valid.replace("\"expectedVersion\":1","\"expectedVersion\":9223372036854775808"),
        valid.replace("\"expectedVersion\":1","\"expectedVersion\":null"),
        valid.replace("\"adults\":1","\"adults\":0"),valid.replace("\"adults\":1","\"adults\":1.5"),
        valid.replace("BUSINESS","INVALID"),valid.replace("OUTBOUND","INVALID"),
        valid.replace("\"selections\":[{","\"selections\":[null,{"),valid.replace("\"flightId\":\""+outFlight+"\"","\"flightId\":\"\""),
        mapper.writeValueAsString(request(1,TripType.ONE_WAY,1,0,0)),
        mapper.writeValueAsString(request(1,TripType.ROUND_TRIP,1,0,0,back())),
        mapper.writeValueAsString(request(1,TripType.ONE_WAY,1,0,0,out(),out())),
        mapper.writeValueAsString(request(1,TripType.ONE_WAY,1,0,0,out(),back())),
        mapper.writeValueAsString(request(1,TripType.ROUND_TRIP,1,0,0,out(),selection(Direction.RETURN,outFlight,CabinClass.ECONOMY)))))
      mvc.perform(put(path(h)).with(as(user)).contentType("application/json").content(invalid))
          .andExpect(status().is(422));
    assertEquals(h,service.current(user));
  }

  @Test
  void protectsOwnershipAuthenticationAndAttachedBooking() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var json=mapper.writeValueAsString(business(1,1));
    mvc.perform(put(path(h)).contentType("application/json").content(json)).andExpect(status().isUnauthorized());
    mvc.perform(put(path(h)).with(as(otherUser)).contentType("application/json").content(json)).andExpect(status().isNotFound());
    var s=session(user);
    var review=reviews.create(s,reviewRequest(h,List.of(passenger("a",PassengerType.ADULT))));
    var created=bookings.create(s,"attached",new BookingCreateRequest(review.reviewId(),1L));
    mvc.perform(put(path(h)).with(as(user)).contentType("application/json").content(json))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOLD_ATTACHED_TO_BOOKING"));
    assertEquals(created.booking().bookingId(),service.current(user).bookingId());
    assertEquals(1,service.current(user).version());
    assertEquals(3,seats(outFare));
    assertEquals(4,seats(businessFare));
  }

  @Test
  void unavailableOrInvalidNewTripKeepsExistingSelection() {
    var h=hold(TripType.ONE_WAY,1,0,0);
    assertEquals("FLIGHT_UNAVAILABLE",assertThrows(BookingException.class,()->service.selection(user,h.seatHoldId(),
        request(1,TripType.ONE_WAY,1,0,0,selection(Direction.OUTBOUND,"missing",CabinClass.ECONOMY)))).code);
    jdbc.update("UPDATE flight SET sale_status='CLOSED' WHERE public_id=?",outFlight);
    assertEquals("FLIGHT_UNAVAILABLE",assertThrows(BookingException.class,()->service.selection(user,h.seatHoldId(),business(1,1))).code);
    assertEquals(h,service.current(user));
    assertEquals(3,seats(outFare));
    assertEquals(4,seats(businessFare));
  }

  @Test
  void expiryCommitsSeatReleaseOnceAndNeverCreatesReplacement() throws Exception {
    var h=hold(TripType.ONE_WAY,2,0,0);
    when(clock.instant()).thenReturn(h.expiresAt());
    for(int i=0;i<2;i++)
      mvc.perform(put(path(h)).with(as(user)).contentType("application/json").content(mapper.writeValueAsString(business(1,2))))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals(4,seats(outFare));
    assertEquals(4,seats(businessFare));
    assertNull(service.current(user));
    assertEquals(1,jdbc.queryForObject("SELECT hold_version FROM seat_hold WHERE public_id=?",Long.class,h.seatHoldId()));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?",Integer.class,user));
  }

  @Test
  void legacyMutationsAdvanceVersionOnlyWhenContentChanges() {
    var h=hold(TripType.ROUND_TRIP,1,0,0);
    assertEquals(1,h.version());
    assertEquals(1,service.leg(user,h.seatHoldId(),"OUTBOUND",new SeatHoldLegRequest(outFlight,CabinClass.ECONOMY)).version());
    assertEquals(2,service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY)).version());
    assertEquals(2,service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY)).version());
    assertEquals(3,service.counts(user,h.seatHoldId(),new PassengerCountsRequest(1,0,1)).version());
    assertEquals(3,service.counts(user,h.seatHoldId(),new PassengerCountsRequest(1,0,1)).version());
    assertEquals("SEAT_HOLD_VERSION_CONFLICT",assertThrows(BookingException.class,()->service.selection(user,h.seatHoldId(),business(2,1))).code);
  }

  @Test
  void expiryDuringWritesRollsBackReplacementBeforeReleasingOriginalSeats() {
    var h=hold(TripType.ONE_WAY,2,0,0);
    doAnswer(invocation -> {
      invocation.callRealMethod();
      when(clock.instant()).thenReturn(h.expiresAt());
      return null;
    }).when(repository).selection(anyLong(),any(),anyInt(),anyInt(),anyInt(),anyList());
    assertEquals("SEAT_HOLD_EXPIRED",assertThrows(BookingException.class,()->service.selection(user,h.seatHoldId(),business(1,1))).code);
    assertEquals(4,seats(outFare));
    assertEquals(4,seats(businessFare));
    assertEquals("EXPIRED",jdbc.queryForObject("SELECT status FROM seat_hold WHERE public_id=?",String.class,h.seatHoldId()));
    assertEquals(1,jdbc.queryForObject("SELECT hold_version FROM seat_hold WHERE public_id=?",Long.class,h.seatHoldId()));
    assertEquals(2,jdbc.queryForObject("SELECT adults FROM seat_hold WHERE public_id=?",Integer.class,h.seatHoldId()));
    assertEquals(outFare,jdbc.queryForObject("SELECT l.flight_fare_id FROM seat_hold_leg l JOIN seat_hold h ON h.id=l.seat_hold_id WHERE h.public_id=?",Long.class,h.seatHoldId()));
    expiry.expireDue();
    assertEquals(4,seats(outFare));
  }

  @Test
  void simultaneousEditsWithSameVersionHaveOneWinner() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var results=race(List.of(() -> attempt(user,h,business(1,1)),
        () -> attempt(user,h,request(1,TripType.ROUND_TRIP,1,0,0,out(),back()))));
    assertEquals(1,Collections.frequency(results,"OK"));
    assertEquals(1,Collections.frequency(results,"SEAT_HOLD_VERSION_CONFLICT"));
    assertEquals(2,service.current(user).version());
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?",Integer.class,user));
  }

  @Test
  void twoHoldsCannotReplaceIntoSameLastSeat() throws Exception {
    var a=hold(TripType.ONE_WAY,1,0,0);
    var b=create(otherUser,TripType.ONE_WAY,1);
    jdbc.update("UPDATE flight_fare SET capacity=1,available_seats=1 WHERE id=?",businessFare);
    var results=race(List.of(() -> attempt(user,a,business(1,1)),() -> attempt(otherUser,b,business(1,1))));
    assertEquals(1,Collections.frequency(results,"OK"));
    assertEquals(1,Collections.frequency(results,"INSUFFICIENT_CAPACITY"));
    assertEquals(0,seats(businessFare));
    assertEquals(3,seats(outFare));
    assertEquals(3,service.current(user).version()+service.current(otherUser).version());
  }

  SeatHoldCreateRequest.Selection selection(Direction direction,String flight,CabinClass cabin) {
    return new SeatHoldCreateRequest.Selection(direction,flight,cabin);
  }
  SeatHoldCreateRequest.Selection out() { return selection(Direction.OUTBOUND,outFlight,CabinClass.ECONOMY); }
  SeatHoldCreateRequest.Selection back() { return selection(Direction.RETURN,backFlight,CabinClass.ECONOMY); }
  SeatHoldSelectionRequest request(long version,TripType trip,int adults,int children,int infants,SeatHoldCreateRequest.Selection... legs) {
    return new SeatHoldSelectionRequest(version,trip,new PassengerCountsRequest(adults,children,infants),List.of(legs));
  }
  SeatHoldSelectionRequest business(long version,int adults) {
    return request(version,TripType.ONE_WAY,adults,0,0,selection(Direction.OUTBOUND,outFlight,CabinClass.BUSINESS));
  }
  String path(SeatHoldResponse h) { return "/api/v1/seat-holds/"+h.seatHoldId()+"/selection"; }
  String attempt(long owner,SeatHoldResponse h,SeatHoldSelectionRequest request) {
    try { service.selection(owner,h.seatHoldId(),request); return "OK"; }
    catch(BookingException ex) { return ex.code; }
  }
  List<String> race(List<Callable<String>> actions) throws Exception {
    var gate=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(actions.size())) {
      var futures=new ArrayList<Future<String>>();
      for(var action:actions) futures.add(pool.submit(() -> {gate.await();return action.call();}));
      gate.countDown();
      var results=new ArrayList<String>();
      for(var future:futures) results.add(future.get(15,TimeUnit.SECONDS));
      return results;
    }
  }
}
