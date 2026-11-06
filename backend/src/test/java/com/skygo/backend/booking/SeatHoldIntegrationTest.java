package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.auth.model.*;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.service.*;
import com.skygo.backend.booking.exception.BookingException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
class SeatHoldIntegrationTest extends BookingFixture {
  @Test
  void endpointsRequireLoginAndCurrentIsInitiallyNull() throws Exception {
    mvc.perform(get("/api/v1/seat-holds/current")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/seat-holds").contentType("application/json").content(body()))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/bookings/test/resume-payment")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/seat-holds/current").with(as(user)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(post("/api/v1/seat-holds").with(as(user)).contentType("application/json").content(body()))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.legs[0].flightId").value(outFlight))
        .andExpect(jsonPath("$.data.passengerCounts.infants").value(2));
    assertEquals(2, seats(outFare));
    mvc.perform(post("/api/v1/seat-holds").with(as(user)).contentType("application/json").content(body()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ACTIVE_HOLD_EXISTS"))
        .andExpect(jsonPath("$.error.activeSeatHoldId").isString());
  }

  @Test
  void roundTripEditsAndRetriesPreserveDeadlineAndRollbackOnFailure() {
    var h = create(user, TripType.ROUND_TRIP, 2);
    var back = service.leg(user, h.seatHoldId(), "RETURN", new SeatHoldLegRequest(backFlight, CabinClass.ECONOMY));
    assertEquals(h.expiresAt(), back.expiresAt());
    assertEquals(2, seats(backFare));
    service.leg(user, h.seatHoldId(), "RETURN", new SeatHoldLegRequest(backFlight, CabinClass.ECONOMY));
    assertEquals(2, seats(backFare));
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?", businessFare);
    assertEquals("INSUFFICIENT_CAPACITY", assertThrows(BookingException.class, () ->
        service.leg(user, h.seatHoldId(), "OUTBOUND", new SeatHoldLegRequest(outFlight, CabinClass.BUSINESS))).code);
    assertEquals(2, seats(outFare));
    // Second leg cannot fit the increase; the first leg's change must roll back too.
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?", backFare);
    assertThrows(BookingException.class, () -> service.counts(user, h.seatHoldId(), new PassengerCountsRequest(3,0,0)));
    assertEquals(2, seats(outFare));
    assertEquals(2, service.current(user).passengerCounts().adults());
    var reduced = service.counts(user, h.seatHoldId(), new PassengerCountsRequest(1,0,4));
    assertEquals(3, seats(outFare));
    assertEquals(1, seats(backFare));
    assertEquals(h.expiresAt(), reduced.expiresAt());
  }

  @Test
  void expiryAtExactDeadlineReleasesOnceEvenWhenEditReturns409() throws Exception {
    var h = create(user, TripType.ROUND_TRIP, 2);
    service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    when(clock.instant()).thenReturn(h.expiresAt());
    mvc.perform(put("/api/v1/seat-holds/"+h.seatHoldId()+"/passenger-counts").with(as(user))
        .contentType("application/json").content("{\"adults\":1,\"children\":0,\"infants\":0}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals(4, seats(outFare));
    assertEquals(4, seats(backFare));
    assertNull(service.current(user));
    expiry.expireDue();
    assertEquals(4, seats(outFare));
    assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM seat_hold WHERE public_id=?",String.class,h.seatHoldId()));
  }

  @Test
  void validatesOwnershipAndPassengerNumbers() throws Exception {
    var h = create(user, TripType.ONE_WAY, 1);
    mvc.perform(put("/api/v1/seat-holds/"+h.seatHoldId()+"/legs/OUTBOUND").with(as(otherUser))
        .contentType("application/json").content("{\"flightId\":\""+outFlight+"\",\"cabinClass\":\"ECONOMY\"}"))
        .andExpect(status().isNotFound());
    for (String invalid : new String[]{body().replace("\"adults\":2","\"adults\":0"),
        body().replace("\"adults\":2","\"adults\":1.5"),"{}","{"})
      mvc.perform(post("/api/v1/seat-holds").with(as(otherUser)).contentType("application/json").content(invalid))
          .andExpect(status().is(422));
    assertEquals("VALIDATION_ERROR", assertThrows(BookingException.class, () -> service.leg(
        user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY))).code);
  }

  @Test
  void searchReclaimsExpiredSeatsBeforeReadingAvailability() {
    var h=create(user,TripType.ONE_WAY,4);
    assertEquals(0,seats(outFare));
    when(clock.instant()).thenReturn(h.expiresAt());
    var request=new com.skygo.backend.flight.contract.FlightSearchRequest(
        com.skygo.backend.flight.contract.FlightSearchRequest.TripType.ONE_WAY,"ZHA","ZHB",
        now.plusSeconds(86400).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(),null,
        new com.skygo.backend.flight.contract.FlightSearchRequest.PassengerCounts(4,0,0),
        com.skygo.backend.flight.contract.FlightSearchRequest.Direction.OUTBOUND,0,20);
    var result=context.getBean(com.skygo.backend.flight.service.FlightSearchService.class).search(request);
    assertEquals(1,result.data().size());
    assertEquals(4,seats(outFare));
  }
}

@SpringBootTest(properties={
    "spring.datasource.url=${SKYGO_TEST_DB_URL}",
    "spring.datasource.username=${SKYGO_TEST_DB_USERNAME:root}",
    "spring.datasource.password=${SKYGO_TEST_DB_PASSWORD:}",
    "skygo.seat-hold.cleanup-enabled=false"})
abstract class BookingFixture {
  @Autowired JdbcTemplate jdbc;
  @Autowired WebApplicationContext context;
  @Autowired SeatHoldService service;
  @Autowired SeatHoldExpiryService expiry;
  @Autowired ResumePaymentService resume;
  @MockitoBean Clock clock;
  MockMvc mvc;
  long user,otherUser,outFare,businessFare,backFare;
  String outFlight,backFlight,prefix;
  final Instant now=Instant.parse("2030-01-10T00:00:00Z");
  final List<Long> users=new ArrayList<>();

  @BeforeEach
  void fixture() {
    when(clock.instant()).thenReturn(now);
    prefix=UUID.randomUUID().toString();
    mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    user=addUser(); otherUser=addUser();
    for(String code:List.of("ZHA","ZHB")) jdbc.update("""
        INSERT INTO airport(code,name,city,country_code,country_name,region_code,region_name,region_sort_order,time_zone)
        VALUES(?,?,?,'ZZ','Test','TEST_HOLD','Test',999,'Asia/Ho_Chi_Minh')
        """,code,code,code);
    outFlight=prefix+"-out"; backFlight=prefix+"-back";
    long out=flight("ZHA","ZHB",outFlight,now.plusSeconds(86400));
    long back=flight("ZHB","ZHA",backFlight,now.plusSeconds(172800));
    outFare=fare(out,"ECONOMY"); businessFare=fare(out,"BUSINESS"); backFare=fare(back,"ECONOMY");
  }
  long addUser() {
    String id=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO app_user(public_id,full_name,phone_normalized,password_hash) VALUES(?,'Hold Test',?,'test-hash')",id,id.substring(0,16));
    long result=jdbc.queryForObject("SELECT id FROM app_user WHERE public_id=?",Long.class,id);
    users.add(result); return result;
  }
  long flight(String from,String to,String id,Instant dep) {
    jdbc.update("""
        INSERT INTO air_route(origin_airport_id,destination_airport_id,air_distance_km)
        SELECT o.id,d.id,100 FROM airport o JOIN airport d WHERE o.code=? AND d.code=?
        """,from,to);
    long route=jdbc.queryForObject("SELECT r.id FROM air_route r JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id WHERE o.code=? AND d.code=?",Long.class,from,to);
    jdbc.update("INSERT INTO flight(public_id,flight_number,route_id,departure_at_utc,arrival_at_utc,sale_status) VALUES(?,'SGTEST',?,?,?,'ON_SALE')",
        id,route,utc(dep),utc(dep.plusSeconds(7200)));
    return jdbc.queryForObject("SELECT id FROM flight WHERE public_id=?",Long.class,id);
  }
  long fare(long flight,String cabin) {
    jdbc.update("""
        INSERT INTO flight_fare(flight_id,pricing_policy_id,priced_distance_km,cabin_class,adult_base_fare,capacity,available_seats)
        SELECT ?,MIN(id),100,?,1000,4,4 FROM pricing_policy
        """,flight,cabin);
    return jdbc.queryForObject("SELECT id FROM flight_fare WHERE flight_id=? AND cabin_class=?",Long.class,flight,cabin);
  }
  SeatHoldResponse create(long owner,TripType trip,int adults) {
    return service.create(owner,new SeatHoldCreateRequest(trip,new PassengerCountsRequest(adults,0,2),
        new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,outFlight,CabinClass.ECONOMY)));
  }
  long seats(long fare) { return jdbc.queryForObject("SELECT available_seats FROM flight_fare WHERE id=?",Long.class,fare); }
  static LocalDateTime utc(Instant time) { return LocalDateTime.ofInstant(time,ZoneOffset.UTC); }
  RequestPostProcessor as(long owner) {
    var u=new AppUser(owner,"test","Test","test",null,"test",now);
    return authentication(new UsernamePasswordAuthenticationToken(new AuthSession(0,u),null,List.of()));
  }
  String body() { return "{\"tripType\":\"ONE_WAY\",\"passengerCounts\":{\"adults\":2,\"children\":0,\"infants\":2},\"selection\":{\"direction\":\"OUTBOUND\",\"flightId\":\""+outFlight+"\",\"cabinClass\":\"ECONOMY\"}}"; }

  // Fixtures are committed so separate request/expiry/concurrency transactions can see them.
  @AfterEach
  void cleanup() {
    for(long owner:users) {
      jdbc.update("UPDATE seat_hold SET booking_id=NULL WHERE user_id=?",owner);
      jdbc.update("DELETE t FROM ticket t JOIN booking b ON b.id=t.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE i FROM invoice i JOIN booking b ON b.id=i.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE x FROM booking_passenger_benefit x JOIN booking b ON b.id=x.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE x FROM booking_fare_line x JOIN booking b ON b.id=x.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE x FROM booking_passenger x JOIN booking b ON b.id=x.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE p FROM payment p JOIN booking b ON b.id=p.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE l FROM booking_leg l JOIN booking b ON b.id=l.booking_id WHERE b.user_id=?",owner);
      jdbc.update("DELETE FROM booking WHERE user_id=?",owner);
      jdbc.update("DELETE FROM booking_review WHERE user_id=?",owner);
      jdbc.update("DELETE l FROM seat_hold_leg l JOIN seat_hold h ON h.id=l.seat_hold_id WHERE h.user_id=?",owner);
      jdbc.update("DELETE FROM seat_hold WHERE user_id=?",owner);
      jdbc.update("DELETE FROM auth_session WHERE user_id=?",owner);
      jdbc.update("DELETE FROM app_user WHERE id=?",owner);
    }
    users.clear();
    jdbc.update("DELETE fare FROM flight_fare fare JOIN flight f ON f.id=fare.flight_id WHERE f.public_id LIKE ?",prefix+"%");
    jdbc.update("DELETE FROM flight WHERE public_id LIKE ?",prefix+"%");
    jdbc.update("DELETE r FROM air_route r JOIN airport o ON o.id=r.origin_airport_id WHERE o.code IN ('ZHA','ZHB')");
    jdbc.update("DELETE FROM airport WHERE code IN ('ZHA','ZHB')");
  }
}
