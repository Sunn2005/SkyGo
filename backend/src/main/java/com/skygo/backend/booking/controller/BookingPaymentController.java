package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.ResumePaymentResponse;
import com.skygo.backend.booking.service.ResumePaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingPaymentController {
  private final ResumePaymentService service;
  public BookingPaymentController(ResumePaymentService service) { this.service = service; }
  @PostMapping("/api/v1/bookings/{bookingId}/resume-payment")
  public DataResponse<ResumePaymentResponse> resume(@AuthenticationPrincipal AuthSession session,
      @PathVariable String bookingId) {
    return new DataResponse<>(service.resume(session.user().id(), bookingId));
  }
}
