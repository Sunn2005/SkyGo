package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.repository.BookingReviewRepository;
import com.skygo.backend.booking.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class BookingBaggageSaveIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired BookingBaggageService baggage;
  @MockitoSpyBean BookingReviewRepository repository;
  @MockitoSpyBean ReviewEncryptionService encryption;

  @Test
  void savesOnSameReviewAndReturnsEverythingForReviewScreenWithCombinedVat() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,1);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT),passenger("i",PassengerType.INFANT))));
    byte[] before=ciphertext(review.reviewId());
    String response=mvc.perform(put(path(review.reviewId())).with(asSession(session)).contentType("application/json")
        .content(mapper.writeValueAsString(request(1,selection("a",Direction.OUTBOUND,2)))))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.data.reviewId").value(review.reviewId()))
        .andExpect(jsonPath("$.data.version").value(2))
        .andExpect(jsonPath("$.data.tripType").value("ONE_WAY"))
        .andExpect(jsonPath("$.data.passengerCounts.infants").value(1))
        .andExpect(jsonPath("$.data.passengers.length()").value(2))
        .andExpect(jsonPath("$.data.passengers[0].document.maskedNumber").value("********5678"))
        .andExpect(jsonPath("$.data.contact.phoneNumber").value("+84901234567"))
        .andExpect(jsonPath("$.data.legs[0].origin.code").value("ZHA"))
        .andExpect(jsonPath("$.data.legs[0].destination.code").value("ZHB"))
        .andExpect(jsonPath("$.data.legs[0].airline.name").value("SkyGo"))
        .andExpect(jsonPath("$.data.legs[0].cabinClass").value("ECONOMY"))
        .andExpect(jsonPath("$.data.legs[0].durationMinutes").value(120))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].included.carryOnMaxKg").value(7))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].selectedExtraPieces").value(2))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].extraAmountExclVat").value(650000))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[1].canPurchaseExtra").value(false))
        .andExpect(jsonPath("$.data.legs[0].checkedBaggagePolicy.maxLinearDimensionsCm").value(158))
        .andExpect(jsonPath("$.data.price.fareSubtotal").value(561000))
        .andExpect(jsonPath("$.data.price.extraBaggageAmount").value(650000))
        .andExpect(jsonPath("$.data.price.vatAmount").value(121100))
        .andExpect(jsonPath("$.data.totalAmount").value(1332100))
        .andExpect(jsonPath("$.data.price.totalAmount").value(1332100))
        .andExpect(jsonPath("$.data.expiresAt").value(hold.expiresAt().toString()))
        .andReturn().getResponse().getContentAsString();
    assertFalse(response.contains("000012345678"));
    assertFalse(Arrays.equals(before,ciphertext(review.reviewId())));
    assertEquals(2,version(review.reviewId()));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_review WHERE user_id=?",Integer.class,user));
    assertNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",Object.class,review.reviewId()));
    assertEquals(3,seats(outFare));
    assertEquals(hold.expiresAt(),service.current(user).expiresAt());
    mvc.perform(get(reviewPath(review.reviewId())).with(asSession(session)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.totalAmount").value(1332100));
    mvc.perform(get(reviewPath(review.reviewId())+"/baggage-options").with(asSession(session)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2))
        .andExpect(jsonPath("$.data.legs[0].passengerBaggage[0].selectedExtraPieces").value(2))
        .andExpect(jsonPath("$.data.totalBaggageAmountExclVat").value(650000));
  }

  @Test
  void repeatedSavesDoNotAccumulateAndUnchangedSelectionsKeepVersionAndCiphertext() {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,hold);
    byte[] initial=ciphertext(review.reviewId());
    assertEquals(1,baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,0))).version());
    assertArrayEquals(initial,ciphertext(review.reviewId()));
    var updated=baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,2)));
    byte[] saved=ciphertext(review.reviewId());
    assertEquals(650000,updated.price().extraBaggageAmount());
    assertEquals(2,baggage.save(session,review.reviewId(),request(2,selection("p1",Direction.OUTBOUND,2))).version());
    assertArrayEquals(saved,ciphertext(review.reviewId()));
    var one=baggage.save(session,review.reviewId(),request(2,selection("p1",Direction.OUTBOUND,1)));
    assertEquals(3,one.version()); assertEquals(325000,one.price().extraBaggageAmount());
    var cleared=baggage.save(session,review.reviewId(),request(3,selection("p1",Direction.OUTBOUND,0)));
    assertEquals(4,cleared.version()); assertEquals(561000,cleared.totalAmount());
    assertEquals(0,cleared.price().extraBaggageAmount());
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_review WHERE user_id=?",Integer.class,user));
  }

  @Test
  void roundTripKeepsIndependentSelectionsForEachAdultChildAndLeg() {
    var hold=hold(TripType.ROUND_TRIP,1,1,1);
    service.leg(user,hold.seatHoldId(),"OUTBOUND",new SeatHoldLegRequest(outFlight,CabinClass.BUSINESS));
    hold=service.leg(user,hold.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT),passenger("c",PassengerType.CHILD),passenger("i",PassengerType.INFANT))));
    var result=baggage.save(session,review.reviewId(),request(1,selection("c",Direction.RETURN,2),selection("a",Direction.OUTBOUND,1),
        selection("a",Direction.RETURN,0),selection("c",Direction.OUTBOUND,0)));
    assertEquals(975000,result.price().extraBaggageAmount());
    assertEquals(1,result.legs().get(0).passengerBaggage().get(0).selectedExtraPieces());
    assertEquals(0,result.legs().get(0).passengerBaggage().get(1).selectedExtraPieces());
    assertEquals(0,result.legs().get(1).passengerBaggage().get(0).selectedExtraPieces());
    assertEquals(2,result.legs().get(1).passengerBaggage().get(1).selectedExtraPieces());
    assertEquals(2,result.legs().get(0).passengerBaggage().get(0).included().checkedPieces());
    assertEquals(1,result.legs().get(1).passengerBaggage().get(0).included().checkedPieces());
    assertEquals(2,seats(businessFare)); assertEquals(2,seats(backFare));
  }

  @Test
  void staleAndConcurrentUpdatesCannotOverwriteTheWinningVersion() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=review(session,hold);
    var gate=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var first=pool.submit(()->race(gate,session,review.reviewId(),1));
      var second=pool.submit(()->race(gate,session,review.reviewId(),2));
      gate.countDown();
      var outcomes=List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
      assertEquals(1,outcomes.stream().filter("OK"::equals).count());
      assertEquals(1,outcomes.stream().filter("REVIEW_VERSION_CONFLICT"::equals).count());
    }
    byte[] winner=ciphertext(review.reviewId());
    mvc.perform(put(path(review.reviewId())).with(asSession(session)).contentType("application/json")
        .content(mapper.writeValueAsString(request(1,selection("p1",Direction.OUTBOUND,0)))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_VERSION_CONFLICT"));
    assertArrayEquals(winner,ciphertext(review.reviewId()));
    assertEquals(2,version(review.reviewId()));
  }

  @Test
  void rejectsInvalidSelectionsAndNonIntegerInputsWithoutChangingStoredReview() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,1);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT),passenger("i",PassengerType.INFANT))));
    byte[] before=ciphertext(review.reviewId());
    for(var input:List.of(request(1,selection("a",Direction.OUTBOUND,3)),request(1,selection("a",Direction.OUTBOUND,-1)),
        request(1,selection("other",Direction.OUTBOUND,1)),request(1,selection("i",Direction.OUTBOUND,1)),
        request(1,selection("a",Direction.RETURN,1)),request(1,selection("a",Direction.OUTBOUND,1),selection("a",Direction.OUTBOUND,1)),
        request(1))) {
      mvc.perform(put(path(review.reviewId())).with(asSession(session)).contentType("application/json").content(mapper.writeValueAsString(input)))
          .andExpect(status().is(422));
    }
    String valid=mapper.writeValueAsString(request(1,selection("a",Direction.OUTBOUND,1)));
    for(String invalid:List.of("{}",valid.replace("\"expectedVersion\":1","\"expectedVersion\":1.5"),
        valid.replace("\"expectedVersion\":1","\"expectedVersion\":\"1\""),valid.replace("\"extraPieces\":1","\"extraPieces\":1.5"),
        valid.replace("\"extraPieces\":1","\"extraPieces\":\"1\""),valid.replace("OUTBOUND","OTHER")))
      mvc.perform(put(path(review.reviewId())).with(asSession(session)).contentType("application/json").content(invalid)).andExpect(status().is(422));
    assertEquals(1,version(review.reviewId())); assertArrayEquals(before,ciphertext(review.reviewId()));
  }

  @Test
  void incompleteRoundTripSelectionIsRejectedRatherThanClearingMissingLeg() {
    var hold=hold(TripType.ROUND_TRIP,1,0,0);
    hold=service.leg(user,hold.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var session=session(user); var review=review(session,hold);
    assertEquals(422,assertThrows(BookingException.class,()->baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1)))).status);
    assertEquals(1,version(review.reviewId()));
  }

  @Test
  void guardsLoginOwnershipSessionInvalidationAndUsedReviews() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user); var review=review(session,hold);
    String json=mapper.writeValueAsString(request(1,selection("p1",Direction.OUTBOUND,1)));
    mvc.perform(get(reviewPath(review.reviewId()))).andExpect(status().isUnauthorized());
    mvc.perform(put(path(review.reviewId())).contentType("application/json").content(json)).andExpect(status().isUnauthorized());
    for(var other:List.of(session(otherUser),session(user))) {
      mvc.perform(get(reviewPath(review.reviewId())).with(asSession(other))).andExpect(status().isNotFound());
      mvc.perform(put(path(review.reviewId())).with(asSession(other)).contentType("application/json").content(json)).andExpect(status().isNotFound());
    }
    jdbc.update("UPDATE booking_review SET used_at_utc=? WHERE public_id=?",utc(now),review.reviewId());
    assertEquals("REVIEW_ALREADY_USED",assertThrows(BookingException.class,()->baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1)))).code);
    jdbc.update("UPDATE booking_review SET used_at_utc=NULL,invalidated_at_utc=? WHERE public_id=?",utc(now),review.reviewId());
    assertEquals("REVIEW_INVALID",assertThrows(BookingException.class,()->baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1)))).code);
    context.getBean(com.skygo.backend.auth.service.AuthService.class).logout(session);
    assertEquals(401,assertThrows(BookingException.class,()->reviews.get(session,review.reviewId())).status);
  }

  @Test
  void encryptionFailureAndPostUpdateFailureRollBackPayloadAndVersion() {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user); var review=review(session,hold);
    byte[] before=ciphertext(review.reviewId());
    doThrow(new BookingException(503,"REVIEW_ENCRYPTION_UNAVAILABLE","Test failure")).when(encryption).encrypt(any(),any());
    assertThrows(BookingException.class,()->baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1))));
    assertEquals(1,version(review.reviewId())); assertArrayEquals(before,ciphertext(review.reviewId()));
    doCallRealMethod().when(encryption).encrypt(any(),any());
    doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Failure after SQL update");})
        .when(repository).update(anyString(),anyLong(),anyLong(),anyLong(),anyInt(),any(),any());
    assertThrows(org.springframework.dao.DataAccessException.class,()->baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1))));
    assertEquals(1,version(review.reviewId())); assertArrayEquals(before,ciphertext(review.reviewId()));
    assertEquals(3,seats(outFare));
  }

  @Test
  void holdExpiringDuringEncryptionCommitsExpiryButDoesNotSaveBaggage() {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user); var review=review(session,hold);
    byte[] before=ciphertext(review.reviewId());
    doAnswer(call->{var result=call.callRealMethod();when(clock.instant()).thenReturn(hold.expiresAt());return result;})
        .when(encryption).encrypt(any(),any());
    assertEquals("SEAT_HOLD_EXPIRED",assertThrows(BookingException.class,()->baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1)))).code);
    assertEquals(1,version(review.reviewId())); assertArrayEquals(before,ciphertext(review.reviewId()));
    assertEquals(4,seats(outFare)); assertNull(service.current(user));
  }

  @Test
  void readsLegacyReviewWithoutRewritingThenUpgradesItOnActualChangeAndRejectsReplay() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user); var review=review(session,hold);
    long holdId=jdbc.queryForObject("SELECT id FROM seat_hold WHERE public_id=?",Long.class,hold.seatHoldId());
    byte[] clear=encryption.decrypt(ciphertext(review.reviewId()),new ReviewEncryptionService.Context(review.reviewId(),user,session.id(),holdId,2,1));
    var legacy=mapper.readValue(clear,new tools.jackson.core.type.TypeReference<Map<String,Object>>() {});
    legacy.put("schemaVersion",1); legacy.remove("version"); legacy.remove("baggage"); legacy.remove("price");
    Arrays.fill(clear,(byte)0);
    byte[] payload=mapper.writeValueAsBytes(legacy);
    byte[] old=encryption.encrypt(payload,new ReviewEncryptionService.Context(review.reviewId(),user,session.id(),holdId,1));
    Arrays.fill(payload,(byte)0);
    jdbc.update("UPDATE booking_review SET snapshot_schema_version=1,snapshot_ciphertext=? WHERE public_id=?",old,review.reviewId());
    assertEquals(1,reviews.get(session,review.reviewId()).version());
    assertArrayEquals(old,ciphertext(review.reviewId()));
    assertEquals(2,baggage.save(session,review.reviewId(),request(1,selection("p1",Direction.OUTBOUND,1))).version());
    assertEquals(2,jdbc.queryForObject("SELECT snapshot_schema_version FROM booking_review WHERE public_id=?",Integer.class,review.reviewId()));
    jdbc.update("UPDATE booking_review SET snapshot_schema_version=1,snapshot_ciphertext=? WHERE public_id=?",old,review.reviewId());
    assertEquals("REVIEW_INVALID",assertThrows(BookingException.class,()->reviews.get(session,review.reviewId())).code);
  }

  private String race(CountDownLatch gate,AuthSession session,String id,int count) throws Exception {
    gate.await();
    try { baggage.save(session,id,request(1,selection("p1",Direction.OUTBOUND,count)));return "OK"; }
    catch(BookingException ex) { return ex.code; }
  }
  private BookingReviewResponse review(AuthSession session,SeatHoldResponse hold) {
    return reviews.create(session,reviewRequest(hold,List.of(passenger("p1",PassengerType.ADULT))));
  }
  private BaggageSaveRequest request(long version,BaggageSaveRequest.Selection... input) { return new BaggageSaveRequest(version,List.of(input)); }
  private BaggageSaveRequest.Selection selection(String id,Direction direction,int count) { return new BaggageSaveRequest.Selection(id,direction,count); }
  private String reviewPath(String id) { return "/api/v1/booking-reviews/"+id; }
  private String path(String id) { return reviewPath(id)+"/baggage"; }
  private long version(String id) { return jdbc.queryForObject("SELECT review_version FROM booking_review WHERE public_id=?",Long.class,id); }
  private byte[] ciphertext(String id) { return jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE public_id=?",byte[].class,id); }
}
