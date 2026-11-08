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
import com.skygo.backend.booking.repository.BookingHoldSwitchRepository;
import com.skygo.backend.booking.service.*;
import com.skygo.backend.booking.service.payos.PayosGateway;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.*;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
@TestPropertySource(properties="skygo.payos.checksum-key=switch-test-secret")
class BookingHoldSwitchIntegrationTest extends PricingFixture {
  @Autowired BookingHoldSwitchService switches;
  @Autowired BookingReviewService reviews;
  @Autowired BookingCreationService creation;
  @Autowired PaymentLinkService links;
  @Autowired BookingDeadlineService deadlines;
  @MockitoBean PayosGateway payos;
  @MockitoSpyBean BookingHoldSwitchRepository operations;
  final Map<Long,PayosGateway.Status> provider=new ConcurrentHashMap<>();
  record Created(String booking,SeatHoldResponse hold,AuthSession session,long order,long amount) {}

  @BeforeEach void provider() {
    provider.clear();
    when(payos.create(anyLong(),anyLong(),anyString(),any())).thenAnswer(call->{
      long order=call.getArgument(0),amount=call.getArgument(1);
      provider.put(order,new PayosGateway.Status("link-"+order,order,amount,0,"PENDING",List.of()));
      return new PayosGateway.Link("link-"+order,order,amount,"VND","PENDING",call.getArgument(2),"qr-"+order,"https://example.com/"+order);
    });
    when(payos.lookup(anyLong())).thenAnswer(call->provider.get(call.<Long>getArgument(0)));
    when(payos.cancel(anyLong())).thenAnswer(call->close(call.getArgument(0)));
  }

  @Test void endpointPreservesBookingHistoryAndTransfersReservationWithoutExtendingDeadline() throws Exception {
    var c=created(true);
    var before=jdbc.queryForList("SELECT * FROM booking_leg WHERE booking_id=(SELECT id FROM booking WHERE public_id=?) ORDER BY id",c.booking());
    var passengers=jdbc.queryForList("SELECT id,public_id,given_names,surname FROM booking_passenger WHERE booking_id=(SELECT id FROM booking WHERE public_id=?)",c.booking());
    String json=mvc.perform(post(path(c)).with(asSession(c.session())).header("Idempotency-Key","switch-1")
        .contentType("application/json").content(mapper.writeValueAsString(request(c))))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.data.previousBooking.bookingId").value(c.booking()))
        .andExpect(jsonPath("$.data.previousBooking.status").value("AWAITING_REHOLD"))
        .andExpect(jsonPath("$.data.seatHold.bookingId").isEmpty())
        .andExpect(jsonPath("$.data.seatHold.version").value(1))
        .andExpect(jsonPath("$.data.seatHold.expiresAt").value(c.hold().expiresAt().toString()))
        .andExpect(jsonPath("$.data.seatHold.legs[0].cabinClass").value("BUSINESS"))
        .andReturn().getResponse().getContentAsString();
    String target=com.jayway.jsonpath.JsonPath.read(json,"$.data.seatHold.seatHoldId");
    assertNotEquals(c.hold().seatHoldId(),target);
    assertEquals("RELEASED",holdStatus(c.hold().seatHoldId()));
    assertEquals("CANCELLED",attemptStatus(c.order()));
    assertEquals(4,seats(outFare));assertEquals(4,seats(backFare));assertEquals(3,seats(businessFare));
    assertEquals(before,jdbc.queryForList("SELECT * FROM booking_leg WHERE booking_id=(SELECT id FROM booking WHERE public_id=?) ORDER BY id",c.booking()));
    assertEquals(passengers,jdbc.queryForList("SELECT id,public_id,given_names,surname FROM booking_passenger WHERE booking_id=(SELECT id FROM booking WHERE public_id=?)",c.booking()));
    mvc.perform(get("/api/v1/bookings/"+c.booking()).with(asSession(c.session())))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiresRehold").value(true))
        .andExpect(jsonPath("$.data.canResumePayment").value(true))
        .andExpect(jsonPath("$.data.seatHold.status").value("RELEASED"));
  }

  @Test void replayReturnsSameTargetAndDifferentPayloadWithSameKeyIsRejected() {
    var c=created(false);var request=request(c);
    var first=switches.switchHold(user,c.booking(),"retry",request);
    assertEquals(first,switches.switchHold(user,c.booking(),"retry",request));
    var changed=new BookingHoldSwitchRequest(request.expectedSeatHoldId(),request.expectedVersion(),TripType.ONE_WAY,
        new PassengerCountsRequest(2,0,0),request.selections());
    assertEquals("IDEMPOTENCY_KEY_REUSED",failure(()->switches.switchHold(user,c.booking(),"retry",changed)));
    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?",Integer.class,user));
    verify(payos,times(1)).cancel(c.order());
  }

  @Test void sameFareAtZeroAvailabilityReusesReservedSeat() {
    var c=created(false);
    jdbc.update("UPDATE flight_fare SET capacity=1,available_seats=0 WHERE id=?",outFare);
    var request=new BookingHoldSwitchRequest(c.hold().seatHoldId(),c.hold().version(),TripType.ONE_WAY,
        new PassengerCountsRequest(1,0,1),List.of(new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,outFlight,CabinClass.ECONOMY)));
    var changed=switches.switchHold(user,c.booking(),"reuse",request);
    assertEquals(0,seats(outFare));assertEquals(1,changed.seatHold().passengerCounts().infants());
    assertEquals("RELEASED",holdStatus(c.hold().seatHoldId()));
  }

  @Test void knownInsufficientCapacityDoesNotCloseQrOrChangeOldHold() {
    var c=created(false);
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",businessFare);
    assertEquals("INSUFFICIENT_CAPACITY",failure(()->switches.switchHold(user,c.booking(),"full",request(c))));
    assertEquals("PENDING_PAYMENT",bookingStatus(c));assertEquals("ACTIVE",holdStatus(c.hold().seatHoldId()));
    assertEquals("PENDING",attemptStatus(c.order()));verify(payos,never()).cancel(anyLong());
    assertEquals(3,seats(outFare));
  }

  @Test void lastSeatLostAfterQrClosureKeepsOrderResumableAndDoesNotPartiallyReserve() {
    var c=created(false);
    doAnswer(call->{
      jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",backFare);
      return close(c.order());
    }).when(payos).cancel(c.order());
    var request=new BookingHoldSwitchRequest(c.hold().seatHoldId(),c.hold().version(),TripType.ROUND_TRIP,
        new PassengerCountsRequest(1,0,0),List.of(
        new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,outFlight,CabinClass.BUSINESS),
        new SeatHoldCreateRequest.Selection(Direction.RETURN,backFlight,CabinClass.ECONOMY)));
    assertEquals("INSUFFICIENT_CAPACITY",failure(()->switches.switchHold(user,c.booking(),"lost",request)));
    assertEquals("AWAITING_REHOLD",bookingStatus(c));assertEquals("RELEASED",holdStatus(c.hold().seatHoldId()));
    assertEquals(4,seats(outFare));assertEquals(4,seats(businessFare));assertNull(service.current(user));
    assertEquals("FAILED",operationStatus());
    var resumed=resume.resume(user,c.booking());
    assertNotEquals(c.hold().seatHoldId(),resumed.seatHoldId());
    var newQr=links.create(user,c.booking());
    assertNotEquals(c.order(),newQr.paymentLink().orderCode());
  }

  @Test void unknownProviderStatusBlocksMutationsAndSameKeyCanRecover() {
    var c=created(false);
    doThrow(new PayosGateway.Failure(false,null)).when(payos).lookup(c.order());
    assertEquals("PAYMENT_STATUS_UNVERIFIED",failure(()->switches.switchHold(user,c.booking(),"network",request(c))));
    assertEquals("VERIFYING_PAYMENT",operationStatus());assertEquals("ACTIVE",holdStatus(c.hold().seatHoldId()));
    assertEquals("HOLD_SWITCH_IN_PROGRESS",failure(()->links.create(user,c.booking())));
    assertEquals("HOLD_SWITCH_IN_PROGRESS",failure(()->resume.resume(user,c.booking())));
    assertEquals("HOLD_SWITCH_IN_PROGRESS",failure(()->switches.switchHold(user,c.booking(),"other-key",request(c))));
    doAnswer(call->provider.get(c.order())).when(payos).lookup(c.order());
    assertNotNull(switches.switchHold(user,c.booking(),"network",request(c)).seatHold());
    assertEquals("COMPLETED",operationStatus());
  }

  @Test void lostCancellationResponseIsConfirmedByLookup() {
    var c=created(false);
    doAnswer(call->{close(c.order());throw new PayosGateway.Failure(false,null);}).when(payos).cancel(c.order());
    switches.switchHold(user,c.booking(),"cancel-timeout",request(c));
    assertEquals("COMPLETED",operationStatus());assertEquals("CANCELLED",attemptStatus(c.order()));
  }

  @Test void failedFinalDatabaseWriteCanBeRecoveredWithoutDoubleReleasingSeats() {
    var c=created(false);
    doThrow(new IllegalStateException("Simulated commit preparation failure")).doCallRealMethod()
        .when(operations).state(anyLong(),eq("COMPLETED"),isNull(),anyLong(),any());
    assertThrows(org.springframework.dao.DataAccessException.class,()->switches.switchHold(user,c.booking(),"db-retry",request(c)));
    assertEquals("QR_CLOSED",operationStatus());assertEquals("ACTIVE",holdStatus(c.hold().seatHoldId()));
    assertEquals(3,seats(outFare));assertEquals(4,seats(businessFare));
    switches.recoverPending();
    assertEquals("COMPLETED",operationStatus());assertEquals(4,seats(outFare));assertEquals(3,seats(businessFare));
    switches.recoverPending();assertEquals(3,seats(businessFare));
  }

  @Test void providerPaymentIsPersistedForReconciliationInsteadOfSwitching() {
    var c=created(false);
    provider.put(c.order(),new PayosGateway.Status("link-"+c.order(),c.order(),c.amount(),100,"PROCESSING",
        List.of(new PayosGateway.Transfer("switch-partial",100,"VND","2030-01-10 07:00:00"))));
    assertEquals("PAYMENT_RECONCILIATION_REQUIRED",failure(()->switches.switchHold(user,c.booking(),"paid",request(c))));
    assertEquals("RECONCILIATION_REQUIRED",operationStatus());
    assertEquals("ACTIVE",holdStatus(c.hold().seatHoldId()));assertEquals(3,seats(outFare));assertEquals(4,seats(businessFare));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payment_transaction WHERE provider_reference='switch-partial'",Integer.class));
    verify(payos,never()).cancel(anyLong());
  }

  @Test void signedWebhookWinningDuringLookupStopsSwitchAndKeepsTickets() throws Exception {
    var c=created(false);
    when(payos.lookup(c.order())).thenAnswer(call->{
      webhook(c,"won-payment");
      return new PayosGateway.Status("link-"+c.order(),c.order(),c.amount(),c.amount(),"PAID",List.of());
    });
    assertEquals("BOOKING_ALREADY_PAID",failure(()->switches.switchHold(user,c.booking(),"payment-race",request(c))));
    assertEquals("PAID",bookingStatus(c));assertEquals("CONSUMED",holdStatus(c.hold().seatHoldId()));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE booking_id=(SELECT id FROM booking WHERE public_id=?)",Integer.class,c.booking()));
    assertEquals(4,seats(businessFare));verify(payos,never()).cancel(anyLong());
  }

  @Test void lateSignedWebhookCannotConsumeNewHold() throws Exception {
    var c=created(false);var target=switches.switchHold(user,c.booking(),"late",request(c)).seatHold();
    webhook(c,"late-transfer");
    assertEquals("ACTIVE",holdStatus(target.seatHoldId()));assertEquals("RELEASED",holdStatus(c.hold().seatHoldId()));
    assertEquals(3,seats(businessFare));assertEquals(4,seats(outFare));
    assertEquals("RECONCILIATION_REQUIRED",jdbc.queryForObject("SELECT handling_status FROM payment_transaction WHERE provider_reference='late-transfer'",String.class));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE booking_id=(SELECT id FROM booking WHERE public_id=?)",Integer.class,c.booking()));
  }

  @Test void sameKeyConcurrentRequestsProduceExactlyOneNewHold() throws Exception {
    var c=created(false);var gate=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      Callable<String> action=()->{gate.await();return switches.switchHold(user,c.booking(),"parallel",request(c)).seatHold().seatHoldId();};
      var a=pool.submit(action);var b=pool.submit(action);gate.countDown();
      assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
    }
    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?",Integer.class,user));
    assertEquals(4,seats(outFare));assertEquals(3,seats(businessFare));
  }

  @Test void insufficientSeatsOnResumeRetainOrderAndLaterRetrySucceeds() {
    var c=created(false);var next=switches.switchHold(user,c.booking(),"resume",request(c)).seatHold();
    when(clock.instant()).thenReturn(next.expiresAt());expiry.expireDue();
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",outFare);
    var ex=assertThrows(BookingException.class,()->resume.resume(user,c.booking()));
    assertEquals("INSUFFICIENT_CAPACITY",ex.code);assertTrue(ex.getMessage().contains("Hãy quay lại sau nhé"));
    assertEquals("AWAITING_REHOLD",bookingStatus(c));
    jdbc.update("UPDATE flight_fare SET available_seats=4 WHERE id=?",outFare);
    resume.resume(user,c.booking());assertEquals("PENDING_PAYMENT",bookingStatus(c));
  }

  @Test void exactBookingDeadlineCancelsUnpaidOrderAndReleasesActiveSeats() throws Exception {
    var c=created(false);
    jdbc.update("UPDATE flight SET booking_closes_at_utc=? WHERE public_id=?",utc(now),outFlight);
    assertEquals("BOOKING_WINDOW_CLOSED",failure(()->resume.resume(user,c.booking())));
    assertEquals("CANCELLED",bookingStatus(c));assertEquals(4,seats(outFare));
    mvc.perform(get("/api/v1/bookings/"+c.booking()).with(asSession(c.session())))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.canResumePayment").value(false))
        .andExpect(jsonPath("$.data.cancellationReason").value("BOOKING_WINDOW_CLOSED"));
  }

  @Test void closingNewSelectionDoesNotCancelOldOrderAndBackgroundCancelsOnlyPastDeadline() {
    var c=created(false);
    jdbc.update("UPDATE flight SET booking_closes_at_utc=? WHERE public_id=?",utc(now),backFlight);
    var request=new BookingHoldSwitchRequest(c.hold().seatHoldId(),c.hold().version(),TripType.ONE_WAY,
        new PassengerCountsRequest(1,0,0),List.of(new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,backFlight,CabinClass.ECONOMY)));
    assertEquals("BOOKING_WINDOW_CLOSED",failure(()->switches.switchHold(user,c.booking(),"closed-target",request)));
    deadlines.cancelDue();assertEquals("PENDING_PAYMENT",bookingStatus(c));
    jdbc.update("UPDATE flight SET booking_closes_at_utc=? WHERE public_id=?",utc(now),outFlight);
    deadlines.cancelDue();deadlines.cancelDue();
    assertEquals("CANCELLED",bookingStatus(c));assertEquals(4,seats(outFare));
  }

  @Test void expiryDuringProviderCallReleasesOnceAndDoesNotCreateTarget() {
    var c=created(false);
    doAnswer(call->{when(clock.instant()).thenReturn(c.hold().expiresAt());return close(c.order());}).when(payos).cancel(c.order());
    assertEquals("SEAT_HOLD_EXPIRED",failure(()->switches.switchHold(user,c.booking(),"expired",request(c))));
    assertEquals("AWAITING_REHOLD",bookingStatus(c));assertEquals(4,seats(outFare));assertEquals(4,seats(businessFare));
    assertNull(service.current(user));assertEquals("FAILED",operationStatus());
  }

  @Test void ownershipVersionAndValidationAreCheckedBeforeCallingProvider() throws Exception {
    var c=created(false);String body=mapper.writeValueAsString(request(c));
    mvc.perform(post(path(c)).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    mvc.perform(post(path(c)).with(as(otherUser)).header("Idempotency-Key","other").contentType("application/json").content(body))
        .andExpect(status().isNotFound());
    for(String invalid:List.of("{}",body.replace("\"expectedVersion\":1","\"expectedVersion\":0"),
        body.replace("\"expectedVersion\":1","\"expectedVersion\":1.5"),
        body.replace("\"expectedVersion\":1","\"expectedVersion\":\"1\""),
        body.replace("OUTBOUND","RETURN"),body.replace("\"adults\":1","\"adults\":0")))
      mvc.perform(post(path(c)).with(as(user)).header("Idempotency-Key","invalid").contentType("application/json").content(invalid))
          .andExpect(status().is(422));
    mvc.perform(post(path(c)).with(as(user)).contentType("application/json").content(body)).andExpect(status().is(422));
    mvc.perform(post(path(c)).with(as(user)).header("Idempotency-Key","stale").contentType("application/json")
        .content(body.replace("\"expectedVersion\":1","\"expectedVersion\":2")))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SEAT_HOLD_VERSION_CONFLICT"));
    verify(payos,never()).lookup(anyLong());verify(payos,never()).cancel(anyLong());
  }

  @Test void expiredUnknownOperationDoesNotBlockNewSearchHoldForever() {
    var c=created(false);
    doThrow(new PayosGateway.Failure(false,null)).when(payos).lookup(c.order());
    assertEquals("PAYMENT_STATUS_UNVERIFIED",failure(()->switches.switchHold(user,c.booking(),"unknown",request(c))));
    when(clock.instant()).thenReturn(c.hold().expiresAt());
    switches.recoverPending();
    assertEquals("FAILED",operationStatus());assertEquals(4,seats(outFare));
    assertNotNull(hold(TripType.ONE_WAY,1,0,0));
    assertEquals("AWAITING_REHOLD",bookingStatus(c));
  }

  @RepeatedTest(10) void twoCustomersSwitchingIntoLastSeatCannotOversell() throws Exception {
    var a=created(false);var b=created(otherUser,false);
    jdbc.update("UPDATE flight_fare SET capacity=1,available_seats=1 WHERE id=?",businessFare);
    var both=new CountDownLatch(2);
    doAnswer(call->{both.countDown();assertTrue(both.await(10,TimeUnit.SECONDS));return provider.get(call.<Long>getArgument(0));})
        .when(payos).lookup(anyLong());
    try(var pool=Executors.newFixedThreadPool(2)) {
      var first=pool.submit(()->attempt(user,a,"customer-a"));
      var second=pool.submit(()->attempt(otherUser,b,"customer-b"));
      var result=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
      assertEquals(1,Collections.frequency(result,"OK"));assertEquals(1,Collections.frequency(result,"INSUFFICIENT_CAPACITY"));
    }
    assertEquals(0,seats(businessFare));assertEquals(4,seats(outFare));
    assertEquals("AWAITING_REHOLD",bookingStatus(a));assertEquals("AWAITING_REHOLD",bookingStatus(b));
  }

  @Test void bookingDeadlineNeverCancelsPaidBookingAndLatePaymentRequiresReconciliation() throws Exception {
    var c=created(false);
    webhook(c,"before-cutoff");
    jdbc.update("UPDATE flight SET booking_closes_at_utc=? WHERE public_id=?",utc(now),outFlight);
    deadlines.cancelDue();
    assertEquals("PAID",bookingStatus(c));assertEquals("CONSUMED",holdStatus(c.hold().seatHoldId()));
  }

  @Test void signedPaymentAtBookingCutoffDoesNotIssueTickets() throws Exception {
    var c=created(false);
    jdbc.update("UPDATE flight SET booking_closes_at_utc=? WHERE public_id=?",utc(now),outFlight);
    webhook(c,"at-cutoff");
    assertEquals("CANCELLED",bookingStatus(c));assertEquals(4,seats(outFare));
    assertEquals("RECONCILIATION_REQUIRED",jdbc.queryForObject("SELECT handling_status FROM payment_transaction WHERE provider_reference='at-cutoff'",String.class));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE booking_id=(SELECT id FROM booking WHERE public_id=?)",Integer.class,c.booking()));
  }

  @Test void normalAndNearbySearchHideFlightsPastBookingCutoff() {
    var search=context.getBean(com.skygo.backend.flight.service.FlightSearchService.class);
    var day=now.plusSeconds(86400).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
    var request=new com.skygo.backend.flight.contract.NearbyFlightSearchRequest(
        com.skygo.backend.flight.contract.FlightSearchRequest.TripType.ONE_WAY,"ZHA","ZHB",day,null,
        new com.skygo.backend.flight.contract.FlightSearchRequest.PassengerCounts(1,0,0),
        com.skygo.backend.flight.contract.FlightSearchRequest.Direction.OUTBOUND);
    assertEquals(1,search.search(request.toSearchRequest()).data().size());
    jdbc.update("UPDATE flight SET booking_closes_at_utc=? WHERE public_id=?",utc(now),outFlight);
    assertTrue(search.search(request.toSearchRequest()).data().isEmpty());
    assertTrue(search.nearbyDays(request).days().stream().allMatch(d->d.flights().isEmpty()));
  }

  @Test void confirmedResumeReplacesBrowsingHoldAndCreatesNewQrForSavedOrder() throws Exception {
    var c=created(false);var target=switches.switchHold(user,c.booking(),"return-old",request(c)).seatHold();
    assertEquals("ACTIVE_HOLD_EXISTS",failure(()->resume.resume(user,c.booking())));
    var confirmation=new ResumePaymentRequest(target.seatHoldId(),target.version());
    String json=mvc.perform(post("/api/v1/bookings/"+c.booking()+"/resume-payment").with(asSession(c.session()))
        .contentType("application/json").content(mapper.writeValueAsString(confirmation)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(c.booking()))
        .andReturn().getResponse().getContentAsString();
    String hold=com.jayway.jsonpath.JsonPath.read(json,"$.data.seatHoldId");
    assertNotEquals(target.seatHoldId(),hold);
    assertEquals("RELEASED",holdStatus(target.seatHoldId()));
    assertEquals(3,seats(outFare));assertEquals(4,seats(businessFare));
    assertEquals(hold,resume.resume(user,c.booking(),confirmation).seatHoldId());
    assertNotEquals(c.order(),links.create(user,c.booking()).paymentLink().orderCode());
  }

  @Test void confirmedResumeWithInsufficientSeatsOrStaleVersionPreservesBrowsingHold() {
    var c=created(false);var target=switches.switchHold(user,c.booking(),"keep-browse",request(c)).seatHold();
    assertEquals("SEAT_HOLD_VERSION_CONFLICT",failure(()->resume.resume(user,c.booking(),new ResumePaymentRequest(target.seatHoldId(),2L))));
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id=?",outFare);
    assertEquals("INSUFFICIENT_CAPACITY",failure(()->resume.resume(user,c.booking(),new ResumePaymentRequest(target.seatHoldId(),1L))));
    assertEquals(target,service.current(user));assertEquals(3,seats(businessFare));
    assertEquals("AWAITING_REHOLD",bookingStatus(c));
  }

  @Test void readOnlyDetailsReportNeedToReholdBeforeCleanupRuns() throws Exception {
    var c=created(false);
    when(clock.instant()).thenReturn(c.hold().expiresAt());
    mvc.perform(get("/api/v1/bookings/"+c.booking()).with(asSession(c.session())))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.data.seatHold.expired").value(true))
        .andExpect(jsonPath("$.data.requiresRehold").value(true));
    assertEquals("ACTIVE",holdStatus(c.hold().seatHoldId()));
  }

  Created created(boolean round) {
    return created(user,round);
  }
  Created created(long owner,boolean round) {
    var h=service.create(owner,new SeatHoldCreateRequest(round?TripType.ROUND_TRIP:TripType.ONE_WAY,
        new PassengerCountsRequest(1,0,0),new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,outFlight,CabinClass.ECONOMY)));
    if(round) h=service.leg(owner,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var s=session(owner);
    var review=reviews.create(s,reviewRequest(h,List.of(passenger("a",PassengerType.ADULT))));
    var booking=creation.create(s,UUID.randomUUID().toString(),new BookingCreateRequest(review.reviewId(),1L)).booking();
    var qr=links.create(owner,booking.bookingId());
    return new Created(booking.bookingId(),h,s,qr.paymentLink().orderCode(),booking.totalAmount());
  }
  BookingHoldSwitchRequest request(Created c) {
    return new BookingHoldSwitchRequest(c.hold().seatHoldId(),c.hold().version(),TripType.ONE_WAY,new PassengerCountsRequest(1,0,0),
        List.of(new SeatHoldCreateRequest.Selection(Direction.OUTBOUND,outFlight,CabinClass.BUSINESS)));
  }
  PayosGateway.Status close(long order) {
    var old=provider.get(order);var closed=new PayosGateway.Status(old.paymentLinkId(),order,old.amount(),old.amountPaid(),"CANCELLED",old.transactions());
    provider.put(order,closed);return closed;
  }
  String bookingStatus(Created c) { return jdbc.queryForObject("SELECT booking_status FROM booking WHERE public_id=?",String.class,c.booking()); }
  String holdStatus(String id) { return jdbc.queryForObject("SELECT status FROM seat_hold WHERE public_id=?",String.class,id); }
  String attemptStatus(long order) { return jdbc.queryForObject("SELECT status FROM payment_attempt WHERE order_code=?",String.class,order); }
  String operationStatus() { return jdbc.queryForObject("SELECT status FROM booking_hold_switch WHERE user_id=?",String.class,user); }
  String path(Created c) { return "/api/v1/bookings/"+c.booking()+"/switch-hold"; }
  String failure(org.junit.jupiter.api.function.Executable action) { return assertThrows(BookingException.class,action).code; }
  String attempt(long owner,Created c,String key) {
    try { switches.switchHold(owner,c.booking(),key,request(c));return "OK"; }
    catch(BookingException ex) { return ex.code; }
  }
  void webhook(Created c,String reference) throws Exception {
    var data=Map.<String,Object>of("orderCode",c.order(),"amount",c.amount(),"paymentLinkId","link-"+c.order(),
        "reference",reference,"currency","VND","code","00","transactionDateTime","2030-01-10 07:00:00");
    String signed=new TreeMap<>(data).entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("&"));
    Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("switch-test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    String signature=HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("code","00","success",true,"data",data,"signature",signature))))
        .andExpect(status().isOk());
  }
}
