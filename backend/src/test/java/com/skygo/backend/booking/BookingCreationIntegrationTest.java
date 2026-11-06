package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class BookingCreationIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired BookingBaggageService baggage;
  @Autowired BookingCreationService bookings;
  @MockitoSpyBean BookingDocumentEncryptionService documents;

  @Test
  void openingActiveBookingReturnsSavedTripPassengerBaggageAndPriceWithoutExtendingHold() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT))));
    var saved=baggage.save(session,review.reviewId(),new BaggageSaveRequest(1L,
        List.of(new BaggageSaveRequest.Selection("a",Direction.OUTBOUND,1))));
    var created=bookings.create(session,"open-snapshot",new BookingCreateRequest(review.reviewId(),saved.version()));
    mvc.perform(post("/api/v1/bookings/"+created.booking().bookingId()+"/open-for-payment")
        .with(asSession(session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seatHoldId").value(hold.seatHoldId()))
        .andExpect(jsonPath("$.data.holdExpiresAt").value(hold.expiresAt().toString()))
        .andExpect(jsonPath("$.data.legs[0].flightId").value(outFlight))
        .andExpect(jsonPath("$.data.legs[0].fares.length()").value(1))
        .andExpect(jsonPath("$.data.passengers[0].givenNames").value("An"))
        .andExpect(jsonPath("$.data.passengers[0].document.maskedNumber").value("****5678"))
        .andExpect(jsonPath("$.data.passengers[0].benefits[0].extraCheckedPieces").value(1))
        .andExpect(jsonPath("$.data.price.extraBaggageAmount").value(325000));
    assertEquals(3,seats(outFare));
  }

  @Test
  void createsBookingAndPaymentFromLatestReviewWithoutReservingSeatsAgain() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,1);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT),
        passenger("i",PassengerType.INFANT))));
    var saved=baggage.save(session,review.reviewId(),new BaggageSaveRequest(1L,
        List.of(new BaggageSaveRequest.Selection("a",Direction.OUTBOUND,2))));
    String body=mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),saved.version()));
    String json=mvc.perform(post("/api/v1/bookings").with(asSession(session))
        .header("Idempotency-Key","create-one").contentType("application/json").content(body))
        .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.data.payment.paymentStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.payment.paymentMethod").value("QR_SIMULATION"))
        .andExpect(jsonPath("$.data.totalAmount").value(1332100))
        .andExpect(jsonPath("$.data.passengers.length()").value(2))
        .andReturn().getResponse().getContentAsString();
    JsonNode data=mapper.readTree(json).get("data");
    assertEquals(3,seats(outFare));
    assertEquals(hold.expiresAt().toString(),data.get("holdExpiresAt").asText());
    assertEquals(1,count("booking"));
    assertEquals(1,count("payment"));
    assertEquals(1,count("booking_leg"));
    assertEquals(2,count("booking_passenger"));
    assertEquals(2,count("booking_passenger_benefit"));
    assertEquals(2,count("booking_fare_line"));
    assertEquals(0,count("ticket"));
    assertEquals(0,count("invoice"));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_review WHERE public_id=? AND used_at_utc IS NOT NULL",Integer.class,review.reviewId()));
    assertEquals(saved.version(),jdbc.queryForObject("SELECT review_version FROM booking_review WHERE public_id=?",Long.class,review.reviewId()));
    assertEquals(650000,jdbc.queryForObject("SELECT extra_baggage_amount FROM booking WHERE public_id=?",Long.class,data.get("bookingId").asText()));
    assertEquals(121100,jdbc.queryForObject("SELECT vat_amount FROM booking WHERE public_id=?",Long.class,data.get("bookingId").asText()));
    assertEquals(2,jdbc.queryForObject("SELECT extra_checked_pieces FROM booking_passenger_benefit WHERE booking_passenger_id="
        +"(SELECT id FROM booking_passenger WHERE client_passenger_id='a' AND booking_id="
        +"(SELECT id FROM booking WHERE public_id=?))",Integer.class,data.get("bookingId").asText()));
    var document=jdbc.queryForObject("SELECT document_number_ciphertext FROM booking_passenger WHERE public_id=?",
        byte[].class,data.get("passengers").get(0).get("passengerId").asText());
    assertEquals("000012345678",documents.decrypt(document,data.get("bookingId").asText(),
        data.get("passengers").get(0).get("passengerId").asText()));
    mvc.perform(get("/api/v1/booking-reviews/"+review.reviewId()).with(asSession(session)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_USED"));
  }

  @Test
  void retriesSameRequestButRejectsReusedKeyAndReusedReview() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT))));
    var request=new BookingCreateRequest(review.reviewId(),1L);
    var first=bookings.create(session,"retry-key",request);
    var repeated=bookings.create(session,"retry-key",request);
    assertFalse(first.replayed()); assertTrue(repeated.replayed());
    assertEquals(first.booking().bookingId(),repeated.booking().bookingId());
    String body=mapper.writeValueAsString(request);
    mvc.perform(post("/api/v1/bookings").with(asSession(session)).header("Idempotency-Key","retry-key")
        .contentType("application/json").content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(first.booking().bookingId()));
    mvc.perform(post("/api/v1/bookings").with(asSession(session)).header("Idempotency-Key","retry-key")
        .contentType("application/json").content(mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),2L))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    mvc.perform(post("/api/v1/bookings").with(asSession(session)).header("Idempotency-Key","new-key")
        .contentType("application/json").content(body))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_USED"));
    assertEquals(1,count("booking")); assertEquals(1,count("payment")); assertEquals(3,seats(outFare));
  }

  @Test
  void refusesStaleVersionChangedFlightAndExpiredHoldWithoutPartialOrder() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT))));
    String path="/api/v1/bookings";
    mvc.perform(post(path).with(asSession(session)).header("Idempotency-Key","stale")
        .contentType("application/json").content(mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),2L))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_VERSION_CONFLICT"));
    jdbc.update("UPDATE flight SET departure_at_utc=DATE_ADD(departure_at_utc,INTERVAL 1 MINUTE),"
        +"arrival_at_utc=DATE_ADD(arrival_at_utc,INTERVAL 1 MINUTE) WHERE public_id=?",outFlight);
    mvc.perform(post(path).with(asSession(session)).header("Idempotency-Key","changed")
        .contentType("application/json").content(mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),1L))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_OUTDATED"));
    assertEquals(0,count("booking"));
    whenTime(hold.expiresAt());
    mvc.perform(post(path).with(asSession(session)).header("Idempotency-Key","expired")
        .contentType("application/json").content(mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),1L))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals(0,count("booking")); assertEquals(4,seats(outFare));
  }

  @Test
  void simultaneousCreateRequestsProduceOneBooking() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT))));
    var request=new BookingCreateRequest(review.reviewId(),1L);
    var gate=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(()->{gate.await();return bookings.create(session,"same-key",request);});
      var b=pool.submit(()->{gate.await();return bookings.create(session,"same-key",request);});
      gate.countDown();
      var one=a.get(15,TimeUnit.SECONDS); var two=b.get(15,TimeUnit.SECONDS);
      assertEquals(one.booking().bookingId(),two.booking().bookingId());
      assertNotEquals(one.replayed(),two.replayed());
    }
    assertEquals(1,count("booking")); assertEquals(1,count("payment")); assertEquals(3,seats(outFare));
  }

  @Test
  void roundTripCopiesEachPassengerBenefitAndFareLineForBothLegs() {
    var hold=hold(TripType.ROUND_TRIP,1,1,1);
    hold=service.leg(user,hold.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT),
        passenger("c",PassengerType.CHILD),passenger("i",PassengerType.INFANT))));
    var saved=baggage.save(session,review.reviewId(),new BaggageSaveRequest(1L,List.of(
        new BaggageSaveRequest.Selection("a",Direction.OUTBOUND,1),
        new BaggageSaveRequest.Selection("c",Direction.OUTBOUND,0),
        new BaggageSaveRequest.Selection("a",Direction.RETURN,0),
        new BaggageSaveRequest.Selection("c",Direction.RETURN,2))));
    var created=bookings.create(session,"round-trip",new BookingCreateRequest(review.reviewId(),saved.version()));
    assertEquals(saved.totalAmount(),created.booking().totalAmount());
    assertEquals(2,count("booking_leg")); assertEquals(3,count("booking_passenger"));
    assertEquals(6,count("booking_passenger_benefit")); assertEquals(6,count("booking_fare_line"));
    assertEquals(975000,jdbc.queryForObject("SELECT extra_baggage_amount FROM booking WHERE public_id=?",
        Long.class,created.booking().bookingId()));
    assertEquals(2,seats(outFare)); assertEquals(2,seats(backFare));
  }

  @Test
  void encryptionFailureAfterInitialInsertsRollsBackTheWholeBooking() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT))));
    org.mockito.Mockito.doThrow(new BookingException(503,"DOCUMENT_ENCRYPTION_UNAVAILABLE","Test failure"))
        .when(documents).encrypt(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    mvc.perform(post("/api/v1/bookings").with(asSession(session)).header("Idempotency-Key","rollback")
        .contentType("application/json")
        .content(mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),1L))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("DOCUMENT_ENCRYPTION_UNAVAILABLE"));
    assertEquals(0,count("booking")); assertEquals(0,count("booking_leg"));
    assertEquals(0,count("payment")); assertEquals(3,seats(outFare));
    assertNull(jdbc.queryForObject("SELECT used_at_utc FROM booking_review WHERE public_id=?",Object.class,review.reviewId()));
    assertNull(jdbc.queryForObject("SELECT booking_id FROM seat_hold WHERE public_id=?",Object.class,hold.seatHoldId()));
  }

  @Test
  void expiryDuringInsertRollsBackBookingAndReturnsHeldSeat() throws Exception {
    var hold=hold(TripType.ONE_WAY,1,0,0);
    var session=session(user);
    var review=reviews.create(session,reviewRequest(hold,List.of(passenger("a",PassengerType.ADULT))));
    org.mockito.Mockito.doAnswer(call->{
      var encrypted=call.callRealMethod();
      whenTime(hold.expiresAt());
      return encrypted;
    }).when(documents).encrypt(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    mvc.perform(post("/api/v1/bookings").with(asSession(session)).header("Idempotency-Key","expires-during-write")
        .contentType("application/json")
        .content(mapper.writeValueAsString(new BookingCreateRequest(review.reviewId(),1L))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals(0,count("booking")); assertEquals(0,count("payment"));
    assertEquals(4,seats(outFare));
    assertEquals("EXPIRED",jdbc.queryForObject("SELECT status FROM seat_hold WHERE public_id=?",
        String.class,hold.seatHoldId()));
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+switch(table) {
      case "booking","booking_passenger","booking_leg","booking_passenger_benefit","booking_fare_line" ->
          "booking".equals(table)?"user_id=?":"booking_id IN (SELECT id FROM booking WHERE user_id=?)";
      case "payment","ticket","invoice" -> "booking_id IN (SELECT id FROM booking WHERE user_id=?)";
      default -> throw new IllegalArgumentException(table);
    },Integer.class,user);
  }
  private void whenTime(java.time.Instant instant) { org.mockito.Mockito.when(clock.instant()).thenReturn(instant); }
}
