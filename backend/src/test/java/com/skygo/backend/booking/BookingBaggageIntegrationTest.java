package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.CheckedBaggagePolicyRepository;
import com.skygo.backend.booking.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class BookingBaggageIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired ReviewEncryptionService encryption;
  @MockitoSpyBean CheckedBaggagePolicyRepository policies;
  final List<Long> policyIds=new ArrayList<>();

  @Test
  void returnsOnlyScreenDataForEveryPassengerWithoutChangingReviewOrInventory() throws Exception {
    var h=hold(TripType.ONE_WAY,1,1,1);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(h,List.of(passenger("adult",PassengerType.ADULT),
        passenger("child",PassengerType.CHILD),passenger("infant",PassengerType.INFANT))));
    byte[] ciphertext=ciphertext(review.reviewId());
    String response=mvc.perform(get(path(review.reviewId())).with(asSession(session)))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.data.reviewId").doesNotExist())
        .andExpect(jsonPath("$.data.expiresAt").value(h.expiresAt().toString()))
        .andExpect(jsonPath("$.data.currency").value("VND"))
        .andExpect(jsonPath("$.data.passengerCounts.adults").value(1))
        .andExpect(jsonPath("$.data.passengerCounts.children").value(1))
        .andExpect(jsonPath("$.data.passengerCounts.infants").value(1))
        .andExpect(jsonPath("$.data.passengers.length()").value(3))
        .andExpect(jsonPath("$.data.passengers[0].fullName").value("Nguyen An"))
        .andExpect(jsonPath("$.data.passengers[0].clientPassengerId").value("adult"))
        .andExpect(jsonPath("$.data.passengers[1].passengerType").value("CHILD"))
        .andExpect(jsonPath("$.data.legs.length()").value(1))
        .andExpect(jsonPath("$.data.legs[0].direction").value("OUTBOUND"))
        .andExpect(jsonPath("$.data.legs[0].checkedBaggagePolicy.pieceMaxKg").value(23))
        .andExpect(jsonPath("$.data.legs[0].checkedBaggagePolicy.maxLinearDimensionsCm").value(158))
        .andExpect(jsonPath("$.data.legs[0].checkedBaggagePolicy.maxExtraPiecesPerPassenger").value(2))
        .andExpect(jsonPath("$.data.legs[0].checkedBaggagePolicy.extraPiecePriceExclVat").value(325000))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].included.carryOnMaxKg").value(7))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].included.checkedPieces").value(1))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].canPurchaseExtra").value(true))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[1].canPurchaseExtra").value(true))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[2].canPurchaseExtra").value(false))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[2].included.checkedPieces").value(0))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[2].included.carryOnMaxKg").value(3))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[2].included.strollerPieces").value(1))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[2].included.strollerMaxKg").value(15))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].selectedExtraPieces").value(0))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].extraAmountExclVat").value(0))
        .andExpect(jsonPath("$.data.totalBaggageAmountExclVat").value(0))
        .andExpect(jsonPath("$.data.contact").doesNotExist())
        .andExpect(jsonPath("$.data.passengers[0].document").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    assertFalse(response.contains("000012345678"));
    assertFalse(response.contains("test@example.com"));
    mvc.perform(get(path(review.reviewId())).with(asSession(session))).andExpect(status().isOk());
    assertArrayEquals(ciphertext,ciphertext(review.reviewId()));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_review WHERE user_id=?",Integer.class,user));
    assertNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",LocalDateTime.class,review.reviewId()));
    assertEquals(2,seats(outFare));
    assertEquals(h.expiresAt(),service.current(user).expiresAt());
  }

  @Test
  void roundTripUsesPerLegCabinsAndSnapshotBenefitsAndLocalDepartureDates() throws Exception {
    jdbc.update("UPDATE flight SET departure_at_utc=?,arrival_at_utc=? WHERE public_id=?",
        utc(Instant.parse("2030-01-10T18:00:00Z")),utc(Instant.parse("2030-01-10T20:00:00Z")),outFlight);
    var h=hold(TripType.ROUND_TRIP,1,0,0);
    service.leg(user,h.seatHoldId(),"OUTBOUND",new SeatHoldLegRequest(outFlight,CabinClass.BUSINESS));
    h=service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var session=session(user);
    var review=review(session,h);
    // Later catalog edits must not replace the itinerary or included benefits already confirmed.
    jdbc.update("UPDATE airport SET time_zone='UTC' WHERE code='ZHA'");
    jdbc.update("UPDATE fare_benefit_policy SET carry_on_max_kg=99 WHERE flight_fare_id=?",businessFare);
    mvc.perform(get(path(review.reviewId())).with(asSession(session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.legs.length()").value(2))
        .andExpect(jsonPath("$.data.legs[0].direction").value("OUTBOUND"))
        .andExpect(jsonPath("$.data.legs[0].originCode").value("ZHA"))
        .andExpect(jsonPath("$.data.legs[0].destinationCode").value("ZHB"))
        .andExpect(jsonPath("$.data.legs[0].departureDate").value("2030-01-11"))
        .andExpect(jsonPath("$.data.legs[0].cabinClass").value("BUSINESS"))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].included.carryOnMaxKg").value(15))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].included.checkedPieces").value(2))
        .andExpect(jsonPath("$.data.legs[1].direction").value("RETURN"))
        .andExpect(jsonPath("$.data.legs[1].originCode").value("ZHB"))
        .andExpect(jsonPath("$.data.legs[1].destinationCode").value("ZHA"))
        .andExpect(jsonPath("$.data.legs[1].cabinClass").value("ECONOMY"))
        .andExpect(jsonPath("$.data.legs[1].passengerBaggage[0].included.carryOnMaxKg").value(7))
        .andExpect(jsonPath("$.data.legs[1].passengerBaggage[0].included.checkedPieces").value(1));
    assertEquals(3,seats(businessFare)); assertEquals(3,seats(backFare));
  }

  @Test
  void ownershipAndCurrentSessionAreCheckedBeforeDecrypting() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,h);
    jdbc.update("UPDATE booking_review SET snapshot_ciphertext=X'01' WHERE public_id=?",review.reviewId());
    mvc.perform(get(path(review.reviewId()))).andExpect(status().isUnauthorized());
    expectError(session(otherUser),review.reviewId(),404,"REVIEW_NOT_FOUND");
    expectError(session(user),review.reviewId(),404,"REVIEW_NOT_FOUND");
    expectError(session,"missing-review",404,"REVIEW_NOT_FOUND");
    expectError(session,review.reviewId(),409,"REVIEW_INVALID");
    context.getBean(com.skygo.backend.auth.service.AuthService.class).logout(session);
    expectError(session,review.reviewId(),401,"UNAUTHENTICATED");
    assertEquals(3,seats(outFare));
  }

  @Test
  void expiredSessionCannotReadReviewEvenWithAnAuthenticatedPrincipal() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,h);
    jdbc.update("UPDATE auth_session SET expires_at_utc=? WHERE id=?",utc(now),session.id());
    expectError(session,review.reviewId(),401,"UNAUTHENTICATED");
  }

  @Test
  void supersededAndEditedReviewsAreRejected() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var first=review(session,h);
    var second=review(session,h);
    expectError(session,first.reviewId(),409,"REVIEW_INVALID");
    mvc.perform(get(path(second.reviewId())).with(asSession(session))).andExpect(status().isOk());
    service.counts(user,h.seatHoldId(),new PassengerCountsRequest(1,0,1));
    expectError(session,second.reviewId(),409,"REVIEW_INVALID");
  }

  @Test
  void rejectsUsedExpiredPurgedUnsupportedAndMalformedSnapshots() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,h);
    jdbc.update("UPDATE booking_review SET used_at_utc=? WHERE public_id=?",utc(now),review.reviewId());
    expectError(session,review.reviewId(),409,"REVIEW_ALREADY_USED");
    jdbc.update("UPDATE booking_review SET used_at_utc=NULL,snapshot_schema_version=2 WHERE public_id=?",review.reviewId());
    expectError(session,review.reviewId(),409,"REVIEW_INVALID");
    jdbc.update("UPDATE booking_review SET snapshot_schema_version=1,expires_at_utc=? WHERE public_id=?",utc(now),review.reviewId());
    expectError(session,review.reviewId(),409,"REVIEW_EXPIRED");
    long hold=jdbc.queryForObject("SELECT id FROM seat_hold WHERE public_id=?",Long.class,h.seatHoldId());
    byte[] malformed=encryption.encrypt("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        new ReviewEncryptionService.Context(review.reviewId(),user,session.id(),hold,1));
    jdbc.update("UPDATE booking_review SET expires_at_utc=?,snapshot_ciphertext=? WHERE public_id=?",utc(h.expiresAt()),malformed,review.reviewId());
    expectError(session,review.reviewId(),409,"REVIEW_INVALID");
    jdbc.update("UPDATE booking_review SET snapshot_ciphertext=NULL,snapshot_purged_at_utc=?,invalidated_at_utc=? WHERE public_id=?",
        utc(now),utc(now),review.reviewId());
    expectError(session,review.reviewId(),409,"REVIEW_INVALID");
    assertEquals(3,seats(outFare));
  }

  @Test
  void exactHoldExpiryReleasesEachLegOnceAndNeverReturnsPassengerData() throws Exception {
    var h=hold(TripType.ROUND_TRIP,1,0,0);
    h=service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var session=session(user);
    var review=review(session,h);
    when(clock.instant()).thenReturn(h.expiresAt().minusSeconds(1));
    mvc.perform(get(path(review.reviewId())).with(asSession(session))).andExpect(status().isOk());
    when(clock.instant()).thenReturn(h.expiresAt());
    expectError(session,review.reviewId(),409,"SEAT_HOLD_EXPIRED");
    expectError(session,review.reviewId(),409,"SEAT_HOLD_EXPIRED");
    assertEquals(4,seats(outFare)); assertEquals(4,seats(backFare));
    assertNull(service.current(user));
  }

  @Test
  void missingInactiveOrInconsistentPoliciesDoNotProducePurchasableOptions() throws Exception {
    var h=hold(TripType.ONE_WAY,1,1,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(h,List.of(passenger("a",PassengerType.ADULT),passenger("c",PassengerType.CHILD))));
    long future=policy("2031-01-01",null,23);
    assignPolicy(future);
    expectError(session,review.reviewId(),409,"PRICING_UNAVAILABLE");
    long expired=policy("2028-01-01","2029-01-01",23);
    assignPolicy(expired);
    expectError(session,review.reviewId(),409,"PRICING_UNAVAILABLE");
    long wrongWeight=policy("2029-01-01",null,24);
    assignPolicy(wrongWeight);
    expectError(session,review.reviewId(),409,"PRICING_UNAVAILABLE");
    long valid=policy("2029-01-01",null,23);
    assignPolicy(valid);
    jdbc.update("UPDATE fare_benefit_policy SET checked_baggage_policy_id=(SELECT id FROM checked_baggage_policy WHERE version=1) WHERE flight_fare_id=? AND passenger_type='CHILD'",outFare);
    expectError(session,review.reviewId(),409,"PRICING_UNAVAILABLE");
    jdbc.update("DELETE FROM fare_benefit_policy WHERE flight_fare_id=? AND passenger_type='CHILD'",outFare);
    expectError(session,review.reviewId(),409,"PRICING_UNAVAILABLE");
    assertEquals(2,seats(outFare));
  }

  @Test
  void expiryWhileWaitingForPoliciesCommitsSeatRelease() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,h);
    doAnswer(call->{
      Object result=call.callRealMethod();
      when(clock.instant()).thenReturn(h.expiresAt());
      return result;
    }).when(policies).forFares(anyList());
    expectError(session,review.reviewId(),409,"SEAT_HOLD_EXPIRED");
    assertEquals(4,seats(outFare));
    assertEquals("EXPIRED",jdbc.queryForObject("SELECT status FROM seat_hold WHERE public_id=?",String.class,h.seatHoldId()));
  }

  @Test
  void sessionExpiryWhileReadingPoliciesIsRecheckedBeforeReturningData() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,h);
    jdbc.update("UPDATE auth_session SET expires_at_utc=? WHERE id=?",utc(now.plusSeconds(5)),session.id());
    doAnswer(call->{
      Object result=call.callRealMethod();
      when(clock.instant()).thenReturn(now.plusSeconds(5));
      return result;
    }).when(policies).forFares(anyList());
    expectError(session,review.reviewId(),401,"UNAUTHENTICATED");
    assertEquals(3,seats(outFare));
  }

  private String path(String review) { return "/api/v1/booking-reviews/"+review+"/baggage-options"; }
  private BookingReviewResponse review(AuthSession session,SeatHoldResponse hold) {
    return reviews.create(session,reviewRequest(hold,List.of(passenger("p1",PassengerType.ADULT))));
  }
  private byte[] ciphertext(String id) {
    return jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE public_id=?",byte[].class,id);
  }
  private void expectError(AuthSession session,String review,int status,String code) throws Exception {
    mvc.perform(get(path(review)).with(asSession(session)))
        .andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
  private long policy(String from,String to,int weight) {
    int version=100000+new Random().nextInt(1000000000);
    jdbc.update("""
        INSERT INTO checked_baggage_policy(version,piece_max_kg,max_linear_dimensions_cm,max_extra_pieces,
          extra_piece_price_excl_vat,effective_from_utc,effective_to_utc) VALUES(?,?,158,2,325000,?,?)
        """,version,weight,from,to);
    long id=jdbc.queryForObject("SELECT id FROM checked_baggage_policy WHERE version=?",Long.class,version);
    policyIds.add(id);
    return id;
  }
  private void assignPolicy(long policy) {
    jdbc.update("UPDATE fare_benefit_policy SET checked_baggage_policy_id=? WHERE flight_fare_id=?",policy,outFare);
  }
  @AfterEach
  void removePolicies() {
    if(policyIds.isEmpty()) return;
    jdbc.update("UPDATE fare_benefit_policy SET checked_baggage_policy_id=(SELECT id FROM checked_baggage_policy WHERE version=1) WHERE flight_fare_id IN (?,?,?)",outFare,businessFare,backFare);
    for(long id:policyIds) jdbc.update("DELETE FROM checked_baggage_policy WHERE id=?",id);
    policyIds.clear();
  }
}
