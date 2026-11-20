package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.ResumePaymentResponse;
import com.skygo.backend.booking.contract.BookingOpenResponse;
import com.skygo.backend.booking.contract.PaymentLinkResponse;
import com.skygo.backend.booking.service.PaymentLinkService;
import com.skygo.backend.booking.service.ResumePaymentService;
import com.skygo.backend.booking.service.BookingHoldSwitchService;
import com.skygo.backend.booking.service.FlightSwitchRateLimiter;
import com.skygo.backend.booking.contract.BookingHoldSwitchRequest;
import com.skygo.backend.booking.contract.BookingHoldSwitchResponse;
import com.skygo.backend.booking.contract.ResumePaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingPaymentController {
  private final ResumePaymentService service;
  private final PaymentLinkService paymentLinks;
  private final BookingHoldSwitchService switches;
  private final FlightSwitchRateLimiter switchRateLimiter;
  public BookingPaymentController(ResumePaymentService service, PaymentLinkService paymentLinks,
      BookingHoldSwitchService switches, FlightSwitchRateLimiter switchRateLimiter) {
    this.service = service;
    this.paymentLinks = paymentLinks;
    this.switches=switches;
    this.switchRateLimiter=switchRateLimiter;
  }
  @PostMapping("/api/v1/bookings/{bookingId}/switch-hold")
  public ResponseEntity<DataResponse<BookingHoldSwitchResponse>> switchHold(
      @AuthenticationPrincipal AuthSession session,@PathVariable String bookingId,
      @RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody BookingHoldSwitchRequest request) {
    switchRateLimiter.check(session.user().id());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(switches.switchHold(session.user().id(),bookingId,key,request)));
  }

  @PostMapping("/api/v1/bookings/{bookingId}/payment-link")
  public ResponseEntity<DataResponse<PaymentLinkResponse>> paymentLink(
      @AuthenticationPrincipal AuthSession session, @PathVariable String bookingId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(paymentLinks.create(session.user().id(), bookingId)));
  }

  @PostMapping("/api/v1/bookings/{bookingId}/open-for-payment")
  public ResponseEntity<DataResponse<BookingOpenResponse>> open(
      @AuthenticationPrincipal AuthSession session, @PathVariable String bookingId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(service.open(session.user().id(), bookingId)));
  }

  @PostMapping("/api/v1/bookings/{bookingId}/resume-payment")
  public DataResponse<ResumePaymentResponse> resume(@AuthenticationPrincipal AuthSession session,
      @PathVariable String bookingId,@Valid @RequestBody(required=false) ResumePaymentRequest request) {
    return new DataResponse<>(service.resume(session.user().id(), bookingId,request));
  }
}
