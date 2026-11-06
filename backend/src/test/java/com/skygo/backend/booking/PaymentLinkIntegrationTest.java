package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL", matches=".+")
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
