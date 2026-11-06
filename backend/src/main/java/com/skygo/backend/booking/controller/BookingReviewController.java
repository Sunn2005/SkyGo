package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.service.BookingReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingReviewController {
  private final BookingReviewService service;
  public BookingReviewController(BookingReviewService service) { this.service=service; }
  @GetMapping("/api/v1/booking-reviews/{reviewId}")
  public ResponseEntity<DataResponse<BookingReviewResponse>> get(@AuthenticationPrincipal AuthSession session,@PathVariable String reviewId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new DataResponse<>(service.get(session,reviewId)));
  }
  @PostMapping("/api/v1/booking-reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public DataResponse<BookingReviewResponse> review(@AuthenticationPrincipal AuthSession session,
      @Valid @RequestBody BookingReviewRequest request) {
    return new DataResponse<>(service.create(session,request));
  }
}
