package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.*;
import com.skygo.backend.booking.model.BookingReviewSnapshot;
import com.skygo.backend.booking.model.SeatHold.TripType;
import com.skygo.backend.booking.service.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class BookingReviewIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired ReviewEncryptionService encryption;

  @Test
  void persistsAuthenticatedEncryptedSnapshotAndReturnsOnlyMaskedDocuments() throws Exception {
    var h=hold(TripType.ONE_WAY,1,1,1);
    var s=session(user);
    var request=reviewRequest(h,List.of(passenger("p1",PassengerType.ADULT),passenger("p2",PassengerType.CHILD),passenger("p3",PassengerType.INFANT)));
    var response=mvc.perform(post("/api/v1/booking-reviews").with(asSession(s)).contentType("application/json")
        .content(mapper.writeValueAsString(request)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(1122000))
        .andExpect(jsonPath("$.data.passengers[0].document.maskedNumber").value("********5678"))
        .andExpect(jsonPath("$.data.passengers[0].document.number").doesNotExist())
        .andExpect(jsonPath("$.data.contact.phoneNumber").value("+84901234567"))
        .andReturn().getResponse().getContentAsString();
    assertFalse(response.contains("000012345678"));
    String id=mapper.readTree(response).at("/data/reviewId").asString();
    long hold=jdbc.queryForObject("SELECT id FROM seat_hold WHERE public_id=?",Long.class,h.seatHoldId());
    byte[] stored=jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE public_id=?",byte[].class,id);
    assertFalse(new String(stored,StandardCharsets.UTF_8).contains("000012345678"));
    byte[] clear=encryption.decrypt(stored,new ReviewEncryptionService.Context(id,user,s.id(),hold,1));
    var snapshot=mapper.readValue(clear,BookingReviewSnapshot.class);
    assertEquals("000012345678",snapshot.passengers().getFirst().document().number());
    assertEquals(3,snapshot.passengerBenefits().size());
    assertEquals(h.expiresAt(),snapshot.expiresAt());
    assertEquals(1122000,snapshot.quote().totalAmount());
    assertEquals(2,seats(outFare));
    var newer=reviews.create(s,request);
    assertNotEquals(id,newer.reviewId());
    assertNotNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",java.time.LocalDateTime.class,id));
    assertArrayEquals(stored,jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE public_id=?",byte[].class,id));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE user_id=?",Integer.class,user));
  }

  @Test
  void rejectsInvalidAgesCountsDocumentsAndContactWithoutSavingReview() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var s=session(user);
    String valid=mapper.writeValueAsString(reviewRequest(h,List.of(passenger("p1",PassengerType.ADULT))));
    for(String invalid:List.of(valid.replace("1990-01-01","2025-01-01"),
        valid.replace("\"title\":\"MR\"","\"title\":null"),
        valid.replace("test@example.com","bad-email"),valid.replace("090 123 4567","invalid"),
        valid.replace("\"issuingCountryCode\":\"VN\"","\"issuingCountryCode\":\"ZZ\""),
        valid.replace("\"adults\":1","\"adults\":1.5"))) {
      mvc.perform(post("/api/v1/booking-reviews").with(asSession(s)).contentType("application/json").content(invalid))
          .andExpect(status().is(422));
    }
    jdbc.update("UPDATE airport SET country_code='SG' WHERE code='ZHB'");
    mvc.perform(post("/api/v1/booking-reviews").with(asSession(s)).contentType("application/json").content(valid))
        .andExpect(status().is(422)).andExpect(jsonPath("$.error.details[0].field").value("passengers[0].document.documentType"));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking_review WHERE user_id=?",Integer.class,user));
    assertEquals(3,seats(outFare));
  }

  @Test
  void sessionOwnershipLogoutAndExpiryProtectReviews() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var s=session(user);
    var request=reviewRequest(h,List.of(passenger("p1",PassengerType.ADULT)));
    String body=mapper.writeValueAsString(request);
    mvc.perform(post("/api/v1/booking-reviews").contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/booking-reviews").with(asSession(session(otherUser))).contentType("application/json").content(body))
        .andExpect(status().isNotFound());
    var review=reviews.create(s,request);
    context.getBean(com.skygo.backend.auth.service.AuthService.class).logout(s);
    assertNotNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",java.time.LocalDateTime.class,review.reviewId()));
    mvc.perform(post("/api/v1/booking-reviews").with(asSession(s)).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    var next=session(user);
    when(clock.instant()).thenReturn(h.expiresAt());
    mvc.perform(post("/api/v1/booking-reviews").with(asSession(next)).contentType("application/json").content(body))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals(4,seats(outFare));
  }

  @Test
  void changingHoldInvalidatesUnusedReviews() {
    var h=hold(TripType.ONE_WAY,1,0,0);
    var review=reviews.create(session(user),reviewRequest(h,List.of(passenger("p1",PassengerType.ADULT))));
    service.counts(user,h.seatHoldId(),new PassengerCountsRequest(1,0,1));
    assertNotNull(jdbc.queryForObject("SELECT invalidated_at_utc FROM booking_review WHERE public_id=?",java.time.LocalDateTime.class,review.reviewId()));
  }
}
