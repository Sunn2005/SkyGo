package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.service.BookingQuoteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingQuoteController {
  private final BookingQuoteService service;
  public BookingQuoteController(BookingQuoteService service) { this.service=service; }
  @PostMapping("/api/v1/booking-quotes")
  public DataResponse<BookingQuoteResponse> quote(@AuthenticationPrincipal AuthSession session,
      @Valid @RequestBody BookingQuoteRequest request) {
    return new DataResponse<>(service.quote(session.user().id(),request));
  }
}
