package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.SeatHold.TripType;
import com.skygo.backend.booking.service.*;
import com.skygo.backend.booking.service.payos.PayosGateway;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL", matches=".+")
@TestPropertySource(properties="skygo.payos.checksum-key=test-webhook-secret")
class PaymentLinkIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired BookingCreationService creation;
  @Autowired BookingBaggageService baggage;
  @Autowired PaymentLinkService paymentLinks;
  @MockitoBean PayosGateway payos;

  private record Created(AuthSession session, BookingCreateResponse booking,
      SeatHoldResponse hold) {}

  @BeforeEach void provider() {
    when(payos.create(anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
      long order = call.getArgument(0);
      long amount = call.getArgument(1);
      String description = call.getArgument(2);
      return new PayosGateway.Link("link-" + order, order, amount, "VND", "PENDING",
          description, "QR-" + order, "https://pay.payos.vn/web/" + order);
    });
  }

  @Test void normalBookingReturnsCompleteQrScreenAndSameLinkOnRetry() throws Exception {
    var created = createBooking(true);
    String path = path(created.booking().bookingId());
    mvc.perform(post(path).with(asSession(session(otherUser))))
        .andExpect(status().isNotFound());
    var first = mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.bookingId").value(created.booking().bookingId()))
        .andExpect(jsonPath("$.data.seatHoldId").value(created.hold().seatHoldId()))
        .andExpect(jsonPath("$.data.expiresAt").value(created.hold().expiresAt().toString()))
        .andExpect(jsonPath("$.data.passengerCounts.adults").value(1))
        .andExpect(jsonPath("$.data.legs[0].origin.code").value("ZHA"))
        .andExpect(jsonPath("$.data.legs[0].destination.code").value("ZHB"))
        .andExpect(jsonPath("$.data.price.extraBaggageAmount").value(325000))
        .andExpect(jsonPath("$.data.paymentLink.status").value("PENDING"))
        .andExpect(jsonPath("$.data.paymentLink.qrCode").isNotEmpty())
        .andExpect(jsonPath("$.data.paymentLink.description").isNotEmpty())
        .andExpect(jsonPath("$.data.paymentLink.checkoutUrl").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    var second = mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(first, second);
    verify(payos, times(1)).create(anyLong(), eq(created.booking().totalAmount()),
        anyString(), eq(created.hold().expiresAt()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE booking_id="
        + "(SELECT id FROM booking WHERE public_id=?)", Integer.class,
        created.booking().bookingId()));
    assertEquals("PAYOS", jdbc.queryForObject("SELECT method FROM payment WHERE booking_id="
        + "(SELECT id FROM booking WHERE public_id=?)", String.class,
        created.booking().bookingId()));
    assertEquals(3, seats(outFare));
  }

  @Test void reheldBookingUsesNewHoldAndTheOriginalBookingAmount() throws Exception {
    var created = createBooking(false);
    when(clock.instant()).thenReturn(created.hold().expiresAt());
    expiry.expireDue();
    String path = path(created.booking().bookingId());
    mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isConflict());
    String opened = mvc.perform(post("/api/v1/bookings/" + created.booking().bookingId()
        + "/open-for-payment").with(asSession(created.session())))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String newHold = com.jayway.jsonpath.JsonPath.read(opened, "$.data.seatHoldId");
    assertNotEquals(created.hold().seatHoldId(), newHold);
    mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seatHoldId").value(newHold))
        .andExpect(jsonPath("$.data.paymentLink.amount").value(created.booking().totalAmount()));
    verify(payos).create(anyLong(), eq(created.booking().totalAmount()),
        anyString(), any());
  }

  @Test void definitiveProviderRejectionCanRetrySameAttemptWithoutDuplicateLink() throws Exception {
    var created = createBooking(false);
    var calls = new AtomicInteger();
    when(payos.create(anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
      if (calls.getAndIncrement() == 0) throw new PayosGateway.Failure(true, null);
      long order = call.getArgument(0);
      long amount = call.getArgument(1);
      String description = call.getArgument(2);
      return new PayosGateway.Link("link-" + order, order, amount, "VND", "PENDING",
          description, "QR-" + order, "https://pay.payos.vn/web/" + order);
    });
    String path = path(created.booking().bookingId());
    mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("PAYOS_LINK_REJECTED"));
    long order = jdbc.queryForObject("SELECT order_code FROM payment_attempt WHERE booking_id="
        + "(SELECT id FROM booking WHERE public_id=?)", Long.class,
        created.booking().bookingId());
    mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentLink.orderCode").value(order));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE order_code=?",
        Integer.class, order));
  }

  @Test void uncertainProviderFailureBlocksAnotherQrForTheSameHold() throws Exception {
    var created = createBooking(false);
    when(payos.create(anyLong(), anyLong(), anyString(), any()))
        .thenThrow(new PayosGateway.Failure(false, null));
    String path = path(created.booking().bookingId());
    mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_LINK_UNVERIFIED"));
    mvc.perform(post(path).with(asSession(created.session())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_LINK_UNVERIFIED"));
    verify(payos, times(1)).create(anyLong(), anyLong(), anyString(), any());
  }

  @Test void concurrentRequestsClaimOneAttemptBeforeCallingPayos() throws Exception {
    var created = createBooking(false);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(payos.create(anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(10, TimeUnit.SECONDS));
      long order = call.getArgument(0);
      return new PayosGateway.Link("link-" + order, order, call.getArgument(1),
          "VND", "PENDING", call.getArgument(2), "QR-" + order,
          "https://pay.payos.vn/web/" + order);
    });
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = pool.submit(() -> paymentLinks.create(user, created.booking().bookingId()));
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      assertEquals("PAYMENT_LINK_UNVERIFIED", assertThrows(BookingException.class,
          () -> paymentLinks.create(user, created.booking().bookingId())).code);
      release.countDown();
      assertEquals("PENDING", first.get(10, TimeUnit.SECONDS).paymentLink().status());
    } finally { release.countDown(); }
    verify(payos, times(1)).create(anyLong(), anyLong(), anyString(), any());
  }

  @Test void linkCreatedAfterHoldExpiryIsStoredButNeverReturnedAsPayableQr() throws Exception {
    var created = createBooking(false);
    when(payos.create(anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
      when(clock.instant()).thenReturn(created.hold().expiresAt());
      long order = call.getArgument(0);
      return new PayosGateway.Link("link-" + order, order, call.getArgument(1),
          "VND", "PENDING", call.getArgument(2), "QR-" + order,
          "https://pay.payos.vn/web/" + order);
    });
    mvc.perform(post(path(created.booking().bookingId())).with(asSession(created.session())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("SEAT_HOLD_EXPIRED"));
    assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM payment_attempt WHERE booking_id="
        + "(SELECT id FROM booking WHERE public_id=?)", String.class,
        created.booking().bookingId()));
    assertEquals(4, seats(outFare));
  }

  @Test void signedWebhookIssuesOnceAndRejectsTampering() throws Exception {
    var created = createBooking(false);
    paymentLinks.create(user, created.booking().bookingId());
    long order = jdbc.queryForObject("""
        SELECT a.order_code FROM payment_attempt a JOIN booking b ON b.id=a.booking_id
        WHERE b.public_id=?
        """, Long.class, created.booking().bookingId());
    String payload = webhook(order, created.booking().totalAmount(), "ref-" + order);
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(payload.replace("\"amount\":" + created.booking().totalAmount(),
            "\"amount\":1"))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(payload)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(payload)).andExpect(status().isOk());
    assertEquals("PAID", jdbc.queryForObject(
        "SELECT booking_status FROM booking WHERE public_id=?", String.class,
        created.booking().bookingId()));
    assertEquals("SUCCEEDED", jdbc.queryForObject("""
        SELECT p.status FROM payment p JOIN booking b ON b.id=p.booking_id WHERE b.public_id=?
        """, String.class, created.booking().bookingId()));
    assertEquals(1, countForBooking("ticket", created.booking().bookingId()));
    assertEquals(1, countForBooking("invoice", created.booking().bookingId()));
    assertEquals(1, countForBooking("payment_transaction t JOIN payment_attempt a ON a.id=t.payment_attempt_id",
        created.booking().bookingId(), "a.booking_id"));
    assertEquals(3, seats(outFare));
  }

  @Test void lateSignedTransferIsRecordedForReconciliationWithoutTickets() throws Exception {
    var created = createBooking(false);
    paymentLinks.create(user, created.booking().bookingId());
    long order = jdbc.queryForObject("""
        SELECT a.order_code FROM payment_attempt a JOIN booking b ON b.id=a.booking_id
        WHERE b.public_id=?
        """, Long.class, created.booking().bookingId());
    when(clock.instant()).thenReturn(created.hold().expiresAt());
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(webhook(order, created.booking().totalAmount(), "late-" + order)))
        .andExpect(status().isOk());
    assertEquals("EXPIRED", jdbc.queryForObject(
        "SELECT booking_status FROM booking WHERE public_id=?", String.class,
        created.booking().bookingId()));
    assertEquals("RECONCILIATION_REQUIRED", jdbc.queryForObject("""
        SELECT p.status FROM payment p JOIN booking b ON b.id=p.booking_id WHERE b.public_id=?
        """, String.class, created.booking().bookingId()));
    assertEquals(0, countForBooking("ticket", created.booking().bookingId()));
    assertEquals(4, seats(outFare));
  }

  @Test void partialTransfersAccumulateAndOverpaymentDoesNotIssueTickets() throws Exception {
    var c = createBooking(false);
    var link = paymentLinks.create(user, c.booking().bookingId()).paymentLink();
    postWebhook(webhook(link.orderCode(), 100, "partial-a"));
    assertEquals(0, countForBooking("ticket", c.booking().bookingId()));
    postWebhook(webhook(link.orderCode(), link.amount() - 100, "partial-b"));
    assertEquals(1, countForBooking("ticket", c.booking().bookingId()));
    postWebhook(webhook(link.orderCode(), 10, "additional"));
    assertEquals(1, countForBooking("ticket", c.booking().bookingId()));
    assertEquals("RECONCILIATION_REQUIRED", jdbc.queryForObject(
        "SELECT handling_status FROM payment_transaction WHERE provider_reference='additional'", String.class));
  }

  @Test void overpaymentIsRetainedForReconciliation() throws Exception {
    var c = createBooking(false);
    var link = paymentLinks.create(user, c.booking().bookingId()).paymentLink();
    postWebhook(webhook(link.orderCode(), link.amount() + 1, "over"));
    assertEquals(0, countForBooking("ticket", c.booking().bookingId()));
    assertEquals("OVERPAYMENT", jdbc.queryForObject(
        "SELECT handling_reason FROM payment_transaction WHERE provider_reference='over'", String.class));
  }

  @Test void mismatchedLinkAndCurrencyAreRetainedWithoutIssuingTickets() throws Exception {
    var c = createBooking(false);
    var link = paymentLinks.create(user, c.booking().bookingId()).paymentLink();
    var data = new TreeMap<String,Object>(Map.of("orderCode",link.orderCode(),
        "amount",link.amount(),"paymentLinkId","wrong-link","reference","wrong-link-ref",
        "currency","VND","code","00"));
    postWebhook(signedWebhook(data));
    data.put("paymentLinkId",link.paymentLinkId());
    data.put("reference","wrong-currency-ref"); data.put("currency","USD");
    postWebhook(signedWebhook(data));
    assertEquals(0, countForBooking("ticket", c.booking().bookingId()));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payment_transaction WHERE "
        + "provider_reference IN ('wrong-link-ref','wrong-currency-ref') AND handling_status='RECONCILIATION_REQUIRED'",Integer.class));
  }

  @Test void webhookBeforeLinkPersistenceIsRetriedRatherThanPermanentlyReconciled() throws Exception {
    var c = createBooking(false);
    when(payos.create(anyLong(),anyLong(),anyString(),any())).thenAnswer(call -> {
      long order=call.getArgument(0), amount=call.getArgument(1);
      mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
          .content(webhook(order,amount,"early-ref"))).andExpect(status().isServiceUnavailable());
      return new PayosGateway.Link("link-"+order,order,amount,"VND","PENDING",
          call.getArgument(2),"QR-"+order,"https://pay.payos.vn/web/"+order);
    });
    var link=paymentLinks.create(user,c.booking().bookingId()).paymentLink();
    postWebhook(webhook(link.orderCode(),link.amount(),"early-ref"));
    assertEquals(1,countForBooking("ticket",c.booking().bookingId()));
  }

  @Test void simultaneousDuplicateWebhooksIssueOnlyOneTicket() throws Exception {
    var c=createBooking(false);
    var link=paymentLinks.create(user,c.booking().bookingId()).paymentLink();
    String payload=webhook(link.orderCode(),link.amount(),"concurrent-ref");
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
      var gate=new CountDownLatch(1);
      var a=pool.submit(()->{gate.await();postWebhook(payload);return true;});
      var b=pool.submit(()->{gate.await();postWebhook(payload);return true;});
      gate.countDown(); a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
    }
    assertEquals(1,countForBooking("ticket",c.booking().bookingId()));
    assertEquals(1,countForBooking("invoice",c.booking().bookingId()));
  }

  private void postWebhook(String payload) throws Exception {
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(payload)).andExpect(status().isOk());
  }

  @Test void bookingGetRequiresOwnerAndDoesNotExpireOrReholdAtTheDeadline() throws Exception {
    var c=createBooking(true);
    String url="/api/v1/bookings/"+c.booking().bookingId();
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).with(as(otherUser))).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
    mvc.perform(get("/api/v1/bookings/not-found").with(as(user))).andExpect(status().isNotFound());
    String json=mvc.perform(get(url).with(as(user))).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.data.seatHold.seatHoldId").value(c.hold().seatHoldId()))
        .andExpect(jsonPath("$.data.seatHold.expired").value(false))
        .andExpect(jsonPath("$.data.passengers[0].benefits[0].checkedPieces").value(1))
        .andExpect(jsonPath("$.data.passengers[0].benefits[0].extraCheckedPieces").value(1))
        .andExpect(jsonPath("$.data.passengers[0].benefits[0].checkedPieceMaxKg").doesNotExist())
        .andExpect(jsonPath("$.data.passengers[0].document.maskedNumber").value("****5678"))
        .andExpect(jsonPath("$.data.passengers[0].ticketNumber").isEmpty())
        .andExpect(jsonPath("$.data.invoice").isEmpty())
        .andReturn().getResponse().getContentAsString();
    assertFalse(json.contains("000012345678"));
    when(clock.instant()).thenReturn(c.hold().expiresAt());
    mvc.perform(get(url).with(as(user))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.bookingStatus").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.data.serverTime").value(c.hold().expiresAt().toString()))
        .andExpect(jsonPath("$.data.seatHold.expired").value(true));
    assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM seat_hold WHERE public_id=?",String.class,c.hold().seatHoldId()));
    assertEquals(3,seats(outFare));
    assertEquals(0,countForBooking("payment_attempt",c.booking().bookingId()));
    var reopened=resume.open(user,c.booking().bookingId());
    mvc.perform(get(url).with(as(user))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seatHold.seatHoldId").value(reopened.seatHoldId()))
        .andExpect(jsonPath("$.data.seatHold.expiresAt").value(reopened.holdExpiresAt().toString()));
    assertNotEquals(c.hold().seatHoldId(),reopened.seatHoldId());
  }

  @Test void bookingGetReflectsWebhookPaymentTicketsAndInvoice() throws Exception {
    var c=createBooking(false);
    String url="/api/v1/bookings/"+c.booking().bookingId();
    var link=paymentLinks.create(user,c.booking().bookingId()).paymentLink();
    postWebhook(webhook(link.orderCode(),link.amount(),"get-paid-ref"));
    mvc.perform(get(url).with(as(user))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.bookingStatus").value("PAID"))
        .andExpect(jsonPath("$.data.payment.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data.payment.amount").value(link.amount()))
        .andExpect(jsonPath("$.data.payment.paidAt").isNotEmpty())
        .andExpect(jsonPath("$.data.seatHold.status").value("CONSUMED"))
        .andExpect(jsonPath("$.data.seatHold.expired").value(false))
        .andExpect(jsonPath("$.data.passengers[0].ticketNumber").isNotEmpty())
        .andExpect(jsonPath("$.data.invoice.invoiceId").isNotEmpty())
        .andExpect(jsonPath("$.data.invoice.issuedAt").isNotEmpty());
  }

  @Test void bookingGetReturnsRoundTripSnapshotAndBaggageCountsPerPassengerAndLeg() throws Exception {
    var h=hold(TripType.ROUND_TRIP,1,1,1);
    service.leg(user,h.seatHoldId(),"OUTBOUND",new SeatHoldLegRequest(outFlight,
        com.skygo.backend.booking.model.SeatHold.CabinClass.BUSINESS));
    h=service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,
        com.skygo.backend.booking.model.SeatHold.CabinClass.ECONOMY));
    var s=session(user);
    var review=reviews.create(s,reviewRequest(h,List.of(passenger("a",PassengerType.ADULT),
        passenger("c",PassengerType.CHILD),passenger("i",PassengerType.INFANT))));
    var saved=baggage.save(s,review.reviewId(),new BaggageSaveRequest(review.version(),List.of(
        new BaggageSaveRequest.Selection("a",com.skygo.backend.booking.model.SeatHold.Direction.OUTBOUND,1),
        new BaggageSaveRequest.Selection("a",com.skygo.backend.booking.model.SeatHold.Direction.RETURN,2),
        new BaggageSaveRequest.Selection("c",com.skygo.backend.booking.model.SeatHold.Direction.OUTBOUND,0),
        new BaggageSaveRequest.Selection("c",com.skygo.backend.booking.model.SeatHold.Direction.RETURN,0))));
    var b=creation.create(s,UUID.randomUUID().toString(),new BookingCreateRequest(review.reviewId(),saved.version())).booking();
    jdbc.update("UPDATE airport SET city='Catalog changed' WHERE code='ZHB'");
    jdbc.update("UPDATE flight_fare SET adult_base_fare=999 WHERE id=?",outFare);
    mvc.perform(get("/api/v1/bookings/"+b.bookingId()).with(as(user))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.legs.length()").value(2))
        .andExpect(jsonPath("$.data.legs[0].destination.city").value("ZHB"))
        .andExpect(jsonPath("$.data.legs[0].cabinClass").value("BUSINESS"))
        .andExpect(jsonPath("$.data.legs[1].cabinClass").value("ECONOMY"))
        .andExpect(jsonPath("$.data.passengers.length()").value(3))
        .andExpect(jsonPath("$.data.passengers[0].benefits[0].checkedPieces").value(2))
        .andExpect(jsonPath("$.data.passengers[0].benefits[0].extraCheckedPieces").value(1))
        .andExpect(jsonPath("$.data.passengers[0].benefits[1].extraCheckedPieces").value(2))
        .andExpect(jsonPath("$.data.passengers[2].benefits[0].seatType").isEmpty())
        .andExpect(jsonPath("$.data.price.totalAmount").value(b.totalAmount()));
  }

  @Test void bookingGetExposesReconciliationAndExpiredHoldWithoutIssuingTickets() throws Exception {
    var c=createBooking(false);
    var link=paymentLinks.create(user,c.booking().bookingId()).paymentLink();
    postWebhook(webhook(link.orderCode(),link.amount()+1,"get-reconcile-ref"));
    when(clock.instant()).thenReturn(c.hold().expiresAt());
    expiry.expireDue();
    mvc.perform(get("/api/v1/bookings/"+c.booking().bookingId()).with(as(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.bookingStatus").value("EXPIRED"))
        .andExpect(jsonPath("$.data.payment.status").value("RECONCILIATION_REQUIRED"))
        .andExpect(jsonPath("$.data.seatHold.expired").value(true))
        .andExpect(jsonPath("$.data.passengers[0].ticketNumber").isEmpty())
        .andExpect(jsonPath("$.data.invoice").isEmpty());
    assertEquals(0,countForBooking("ticket",c.booking().bookingId()));
  }

  @Test void outOfRangeSignedAmountIsRejectedBeforeItCanWrapToAnotherAmount() throws Exception {
    var data=Map.<String,Object>of("orderCode",123,"amount",
        new java.math.BigInteger("18446744073709551617"),"paymentLinkId","sample",
        "reference","overflow","currency","VND","code","00");
    mvc.perform(post("/api/v1/payos/webhook").contentType("application/json")
        .content(signedWebhook(data))).andExpect(status().isBadRequest());
  }

  @Test void failureDuringIssuanceRollsBackTransferAndAllPaymentChanges() throws Exception {
    var c=createBooking(false);
    var link=paymentLinks.create(user,c.booking().bookingId()).paymentLink();
    jdbc.update("INSERT INTO invoice(public_id,booking_id,issued_at_utc) "
        + "SELECT ?,id,? FROM booking WHERE public_id=?",UUID.randomUUID().toString(),utc(now),c.booking().bookingId());
    String payload=webhook(link.orderCode(),link.amount(),"rollback-ref");
    assertThrows(jakarta.servlet.ServletException.class,()->postWebhook(payload));
    assertEquals(0,countForBooking("ticket",c.booking().bookingId()));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment_transaction WHERE provider_reference='rollback-ref'",Integer.class));
    assertEquals("PENDING_PAYMENT",jdbc.queryForObject("SELECT booking_status FROM booking WHERE public_id=?",String.class,c.booking().bookingId()));
    jdbc.update("DELETE FROM invoice WHERE booking_id=(SELECT id FROM booking WHERE public_id=?)",c.booking().bookingId());
    postWebhook(payload);
    assertEquals(1,countForBooking("ticket",c.booking().bookingId()));
  }

  private int countForBooking(String table, String publicId) {
    return countForBooking(table, publicId, "booking_id");
  }
  private int countForBooking(String table, String publicId, String bookingColumn) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + bookingColumn
        + "=(SELECT id FROM booking WHERE public_id=?)", Integer.class, publicId);
  }

  private String webhook(long order, long amount, String reference) throws Exception {
    var data = Map.<String, Object>of("orderCode", order, "amount", amount,
        "paymentLinkId", "link-" + order, "reference", reference, "currency", "VND",
        "code", "00", "transactionDateTime", "2030-01-01 10:00:00");
    return signedWebhook(data);
  }

  private String signedWebhook(Map<String,Object> data) throws Exception {
    String signed = new TreeMap<>(data).entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(java.util.stream.Collectors.joining("&"));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8),
        "HmacSHA256"));
    String signature = HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    return mapper.writeValueAsString(Map.of("code", "00", "success", true,
        "data", data, "signature", signature));
  }

  private Created createBooking(boolean withBaggage) {
    var hold = hold(TripType.ONE_WAY, 1, 0, 0);
    var session = session(user);
    var review = reviews.create(session, reviewRequest(hold,
        List.of(passenger("adult", PassengerType.ADULT))));
    long version = review.version();
    if (withBaggage) version = baggage.save(session, review.reviewId(),
        new BaggageSaveRequest(version,
            List.of(new BaggageSaveRequest.Selection("adult",
                com.skygo.backend.booking.model.SeatHold.Direction.OUTBOUND, 1)))).version();
    var booking = creation.create(session, UUID.randomUUID().toString(),
        new BookingCreateRequest(review.reviewId(), version)).booking();
    return new Created(session, booking, hold);
  }

  private static String path(String id) {
    return "/api/v1/bookings/" + id + "/payment-link";
  }
}
