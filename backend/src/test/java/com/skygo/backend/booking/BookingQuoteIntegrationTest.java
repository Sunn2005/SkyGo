package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.skygo.backend.auth.model.*;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.*;
import com.skygo.backend.booking.model.SeatHold.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class BookingQuoteIntegrationTest extends PricingFixture {
  @Test
  void quotesLastHeldSeatsWithoutReservingAgainOrExtendingHold() throws Exception {
    var h=hold(TripType.ONE_WAY,4,0,0);
    assertEquals(0,seats(outFare));
    mvc.perform(post("/api/v1/booking-quotes").with(as(user)).contentType("application/json")
        .content(mapper.writeValueAsString(quoteRequest(h))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(2244000))
        .andExpect(jsonPath("$.data.currency").value("VND"))
        .andExpect(jsonPath("$.data.expiresAt").value(h.expiresAt().toString()))
        .andExpect(jsonPath("$.data.fareLines").doesNotExist());
    assertEquals(0,seats(outFare));
    assertEquals(h.expiresAt(),service.current(user).expiresAt());
  }
  @Test
  void supportsDifferentCabinsPerDirectionAndGroupRates() throws Exception {
    var h=hold(TripType.ROUND_TRIP,1,0,1);
    service.leg(user,h.seatHoldId(),"OUTBOUND",new SeatHoldLegRequest(outFlight,CabinClass.BUSINESS));
    h=service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    mvc.perform(post("/api/v1/booking-quotes").with(as(user)).contentType("application/json")
        .content(mapper.writeValueAsString(quoteRequest(h))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(2159850));
  }
  @Test
  void rejectsForeignIncompleteMismatchedExpiredAndInvalidRequests() throws Exception {
    var h=hold(TripType.ROUND_TRIP,1,0,0);
    String request=mapper.writeValueAsString(quoteRequest(h));
    mvc.perform(post("/api/v1/booking-quotes").contentType("application/json").content(request))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/booking-quotes").with(as(otherUser)).contentType("application/json").content(request))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/booking-quotes").with(as(user)).contentType("application/json").content(request))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOLD_MISMATCH"));
    mvc.perform(post("/api/v1/booking-quotes").with(as(user)).contentType("application/json").content("{}"))
        .andExpect(status().is(422));
    when(clock.instant()).thenReturn(h.expiresAt());
    mvc.perform(post("/api/v1/booking-quotes").with(as(user)).contentType("application/json").content(request))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals(4,seats(outFare));
  }
  @Test
  void missingPoliciesOrStalePricesNeverProduceAnImplicitZero() throws Exception {
    var h=hold(TripType.ONE_WAY,1,0,1);
    jdbc.update("DELETE FROM fare_benefit_policy WHERE flight_fare_id=? AND passenger_type='INFANT'",outFare);
    mvc.perform(post("/api/v1/booking-quotes").with(as(user)).contentType("application/json")
        .content(mapper.writeValueAsString(quoteRequest(h))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRICING_UNAVAILABLE"));
    assertEquals(3,seats(outFare));
  }
}

@TestPropertySource(properties={
    "skygo.review.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "skygo.review.key-id=test-v1"})
abstract class PricingFixture extends BookingFixture {
  @Autowired ObjectMapper mapper;
  @BeforeEach
  void pricingFixture() {
    jdbc.update("UPDATE airport SET country_code='VN',country_name='Việt Nam' WHERE code IN ('ZHA','ZHB')");
    jdbc.update("UPDATE flight_fare SET adult_base_fare=510000 WHERE id IN (?,?)",outFare,backFare);
    jdbc.update("UPDATE flight_fare SET adult_base_fare=1275000 WHERE id=?",businessFare);
    for(long fare:List.of(outFare,businessFare,backFare)) for(var type:PassengerType.values()) {
      boolean infant=type==PassengerType.INFANT,business=fare==businessFare;
      jdbc.update("""
          INSERT INTO fare_benefit_policy(flight_fare_id,passenger_type,carry_on_max_kg,checked_pieces,
            checked_baggage_policy_id,included_meals_per_leg,seat_type,stroller_pieces,stroller_max_kg)
          VALUES(?,?,?,?,(SELECT id FROM checked_baggage_policy WHERE version=1),?,?,?,?)
          """,fare,type.name(),infant?3:business?15:7,infant?0:business?2:1,
          !infant&&business?1:0,infant?null:business?"BUSINESS":"STANDARD",infant?1:0,infant?15:0);
    }
  }
  @AfterEach
  void removeBenefits() {
    jdbc.update("DELETE FROM fare_benefit_policy WHERE flight_fare_id IN (?,?,?)",outFare,businessFare,backFare);
  }
  SeatHoldResponse hold(TripType trip,int adults,int children,int infants) {
    return service.create(user,new SeatHoldCreateRequest(trip,new PassengerCountsRequest(adults,children,infants),
        new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,outFlight,CabinClass.ECONOMY)));
  }
  BookingQuoteRequest quoteRequest(SeatHoldResponse h) {
    return new BookingQuoteRequest(h.seatHoldId(),TripType.valueOf(h.tripType()),h.passengerCounts(),
        h.legs().stream().map(l->new BookingQuoteRequest.Selection(Direction.valueOf(l.direction()),l.flightId(),CabinClass.valueOf(l.cabinClass()))).toList());
  }
  AuthSession session(long owner) {
    String id=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO auth_session(public_id,user_id,token_hash,expires_at_utc) VALUES(?,?,UNHEX(SHA2(?,256)),?)",
        id,owner,id,utc(now.plusSeconds(86400)));
    long session=jdbc.queryForObject("SELECT id FROM auth_session WHERE public_id=?",Long.class,id);
    return new AuthSession(session,new AppUser(owner,"test","Test","test",null,"test",now));
  }
  RequestPostProcessor asSession(AuthSession session) {
    return authentication(new UsernamePasswordAuthenticationToken(session,null,List.of()));
  }
  PassengerRequest passenger(String id,PassengerType type) {
    String birth=switch(type) {case ADULT->"1990-01-01";case CHILD->"2025-01-01";case INFANT->"2029-01-01";};
    return new PassengerRequest(id,type,type==PassengerType.ADULT?Title.MR:null," An "," Nguyen ",LocalDate.parse(birth),Gender.MALE,
        new Document(DocumentType.PERSONAL_ID,"000012345678","VN",null,null));
  }
  BookingReviewRequest reviewRequest(SeatHoldResponse h,List<PassengerRequest> passengers) {
    var q=quoteRequest(h);
    return new BookingReviewRequest(q.seatHoldId(),q.tripType(),q.passengerCounts(),q.selections(),passengers,
        new BookingReviewRequest.Contact("test@example.com","090 123 4567"));
  }
}
