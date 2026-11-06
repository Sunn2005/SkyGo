package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.ResumePaymentResponse;
import com.skygo.backend.booking.contract.BookingOpenResponse;
import com.skygo.backend.booking.contract.PaymentLinkResponse;
import com.skygo.backend.booking.service.PaymentLinkService;
import com.skygo.backend.booking.service.ResumePaymentService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingPaymentController {
  private final ResumePaymentService service;
  private final PaymentLinkService paymentLinks;
  public BookingPaymentController(ResumePaymentService service, PaymentLinkService paymentLinks) {
    this.service = service;
    this.paymentLinks = paymentLinks;
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
      @PathVariable String bookingId) {
    return new DataResponse<>(service.resume(session.user().id(), bookingId));
  }
}
