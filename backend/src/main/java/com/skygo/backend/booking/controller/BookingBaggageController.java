package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.BaggageOptionsResponse;
import com.skygo.backend.booking.contract.BaggageSaveRequest;
import com.skygo.backend.booking.contract.BookingReviewResponse;
import jakarta.validation.Valid;
import com.skygo.backend.booking.service.BookingBaggageService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingBaggageController {
  private final BookingBaggageService service;

  public BookingBaggageController(BookingBaggageService service) { this.service=service; }
  @PutMapping("/api/v1/booking-reviews/{reviewId}/baggage")
  public ResponseEntity<DataResponse<BookingReviewResponse>> save(@AuthenticationPrincipal AuthSession session,
      @PathVariable String reviewId,@Valid @RequestBody BaggageSaveRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new DataResponse<>(service.save(session,reviewId,request)));
  }

  @GetMapping("/api/v1/booking-reviews/{reviewId}/baggage-options")
  public ResponseEntity<DataResponse<BaggageOptionsResponse>> options(
      @AuthenticationPrincipal AuthSession session, @PathVariable String reviewId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(service.options(session,reviewId)));
  }
}
