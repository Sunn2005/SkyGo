package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class ResumePaymentIntegrationTest extends BookingFixture {
  @Test
  void expiredBookingResumesAtomicallyAndRetriesReuseHoldAndPayment() throws Exception {
    String booking=expiredBooking(true);
    String payment=jdbc.queryForObject("SELECT p.public_id FROM payment p JOIN booking b ON b.id=p.booking_id WHERE b.public_id=?",String.class,booking);
    assertEquals(4,seats(outFare)); assertEquals(4,seats(backFare));
    mvc.perform(post("/api/v1/bookings/"+booking+"/resume-payment").with(as(otherUser)))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/bookings/"+booking+"/resume-payment").with(as(user)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.data.totalAmount").value(2200));
    var first=resume.resume(user,booking);
    assertEquals(2,seats(outFare)); assertEquals(2,seats(backFare));
    when(clock.instant()).thenReturn(first.holdExpiresAt().minusSeconds(1));
    var repeat=resume.resume(user,booking);
    assertEquals(first.seatHoldId(),repeat.seatHoldId());
    assertEquals(first.holdExpiresAt(),repeat.holdExpiresAt());
    assertEquals(payment,jdbc.queryForObject("SELECT p.public_id FROM payment p JOIN booking b ON b.id=p.booking_id WHERE b.public_id=?",String.class,booking));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE user_id=?",Integer.class,user));
    when(clock.instant()).thenReturn(first.holdExpiresAt());
    expiry.expireDue();
    assertEquals(4,seats(outFare)); assertEquals(4,seats(backFare));
    assertEquals("EXPIRED",jdbc.queryForObject("SELECT booking_status FROM booking WHERE public_id=?",String.class,booking));
    var next=resume.resume(user,booking);
    assertNotEquals(first.seatHoldId(),next.seatHoldId());
    assertEquals(2200,next.totalAmount());
  }

  @Test
  void missingReturnSeatsRollbackAllChangesAndOtherActiveHoldBlocksResume() {
    String booking=expiredBooking(true);
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",backFare);
    assertEquals("INSUFFICIENT_CAPACITY",assertThrows(BookingException.class,() ->resume.resume(user,booking)).code);
    assertEquals(4,seats(outFare));
    assertEquals("EXPIRED",jdbc.queryForObject("SELECT booking_status FROM booking WHERE public_id=?",String.class,booking));
    create(user,TripType.ONE_WAY,1);
    assertEquals("ACTIVE_HOLD_EXISTS",assertThrows(BookingException.class,() ->resume.resume(user,booking)).code);
  }

  @Test
  void attachedHoldCannotBeEditedAndPaidOrUnavailableBookingCannotResume() {
    String booking=expiredBooking(false);
    var active=resume.resume(user,booking);
    assertEquals("HOLD_ATTACHED_TO_BOOKING",assertThrows(BookingException.class,() ->
        service.counts(user,active.seatHoldId(),new PassengerCountsRequest(1,0,0))).code);
    jdbc.update("UPDATE seat_hold SET status='CONSUMED' WHERE public_id=?",active.seatHoldId());
    jdbc.update("UPDATE booking SET booking_status='PAID' WHERE public_id=?",booking);
    jdbc.update("UPDATE payment p JOIN booking b ON b.id=p.booking_id SET p.status='SUCCEEDED',p.paid_at_utc=? WHERE b.public_id=?",utc(now),booking);
    assertEquals("BOOKING_ALREADY_PAID",assertThrows(BookingException.class,() ->resume.resume(user,booking)).code);
  }

  @Test
  void closedFlightCannotResumeAndConcurrentRetriesShareOneNewHold() throws Exception {
    String booking=expiredBooking(true);
    jdbc.update("UPDATE flight SET sale_status='CLOSED' WHERE public_id=?",backFlight);
    assertEquals("FLIGHT_UNAVAILABLE",assertThrows(BookingException.class,() -> resume.resume(user,booking)).code);
    assertEquals(4,seats(outFare));
    jdbc.update("UPDATE flight SET sale_status='ON_SALE' WHERE public_id=?",backFlight);
    var gate=new java.util.concurrent.CountDownLatch(1);
    try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a=pool.submit(() -> {gate.await();return resume.resume(user,booking);});
      var b=pool.submit(() -> {gate.await();return resume.resume(user,booking);});
      gate.countDown();
      var first=a.get(15,java.util.concurrent.TimeUnit.SECONDS);
      var second=b.get(15,java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(first.seatHoldId(),second.seatHoldId());
      assertEquals(first.holdExpiresAt(),second.holdExpiresAt());
    }
    assertEquals(2,seats(outFare)); assertEquals(2,seats(backFare));
  }

  String expiredBooking(boolean roundTrip) {
    var h=create(user,roundTrip?TripType.ROUND_TRIP:TripType.ONE_WAY,2);
    if(roundTrip) service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    long hold=jdbc.queryForObject("SELECT id FROM seat_hold WHERE public_id=?",Long.class,h.seatHoldId());
    String sessionId=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO auth_session(public_id,user_id,token_hash,expires_at_utc) VALUES(?,?,UNHEX(SHA2(?,256)),?)",sessionId,user,sessionId,utc(now.plusSeconds(86400)));
    long session=jdbc.queryForObject("SELECT id FROM auth_session WHERE public_id=?",Long.class,sessionId);
    String reviewId=UUID.randomUUID().toString();
    jdbc.update("""
        INSERT INTO booking_review(public_id,seat_hold_id,user_id,session_id,snapshot_ciphertext,snapshot_schema_version,expires_at_utc,used_at_utc)
        VALUES(?,?,?,?,?,1,?,?)
        """,reviewId,hold,user,session,new byte[]{1},utc(h.expiresAt()),utc(now));
    long review=jdbc.queryForObject("SELECT id FROM booking_review WHERE public_id=?",Long.class,reviewId);
    String id=UUID.randomUUID().toString();
    jdbc.update("""
        INSERT INTO booking(public_id,booking_code,user_id,review_id,seat_hold_id,idempotency_key,request_hash,
          trip_type,adults,children,infants,contact_email,contact_phone,fare_subtotal,vat_rate,vat_amount,total_amount,booking_status)
        VALUES(?,?,?,?,?,?,UNHEX(SHA2(?,256)),?,2,0,2,'test@example.com','+84900000000',2000,0.1,200,2200,'PENDING_PAYMENT')
        """,id,id.substring(0,16),user,review,hold,id,id,roundTrip?"ROUND_TRIP":"ONE_WAY");
    long b=jdbc.queryForObject("SELECT id FROM booking WHERE public_id=?",Long.class,id);
    jdbc.update("UPDATE seat_hold SET booking_id=? WHERE id=?",b,hold);
    jdbc.update("INSERT INTO payment(public_id,booking_id,method,status,amount) VALUES(?,?,'QR_SIMULATION','PENDING',2200)",UUID.randomUUID().toString(),b);
    jdbc.update("""
        INSERT INTO booking_leg(booking_id,direction,flight_fare_id,flight_public_id,flight_number,airline_code,airline_name,
          cabin_class,origin_code,origin_name,origin_city,origin_country_code,origin_time_zone,
          destination_code,destination_name,destination_city,destination_country_code,destination_time_zone,
          departure_at_utc,arrival_at_utc,duration_minutes,route_type)
        SELECT ?,l.direction,fare.id,f.public_id,f.flight_number,'SG','SkyGo',fare.cabin_class,
          o.code,o.name,o.city,o.country_code,o.time_zone,d.code,d.name,d.city,d.country_code,d.time_zone,
          f.departure_at_utc,f.arrival_at_utc,120,'DOMESTIC'
        FROM seat_hold_leg l JOIN flight_fare fare ON fare.id=l.flight_fare_id JOIN flight f ON f.id=fare.flight_id
          JOIN air_route r ON r.id=f.route_id JOIN airport o ON o.id=r.origin_airport_id
          JOIN airport d ON d.id=r.destination_airport_id WHERE l.seat_hold_id=?
        """,b,hold);
    when(clock.instant()).thenReturn(h.expiresAt());
    expiry.expireDue();
    return id;
  }
}
