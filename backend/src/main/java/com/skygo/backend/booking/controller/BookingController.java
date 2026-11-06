package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.service.BookingCreationService;
import com.skygo.backend.booking.service.BookingDetailService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {
  private final BookingCreationService service;
  private final BookingDetailService details;

  public BookingController(BookingCreationService service, BookingDetailService details) {
    this.service = service;
    this.details = details;
  }

  @GetMapping("/api/v1/bookings/{bookingId}")
  public ResponseEntity<DataResponse<BookingDetailResponse>> get(
      @AuthenticationPrincipal AuthSession session, @PathVariable String bookingId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(details.get(session.user().id(),bookingId)));
  }

  @PostMapping("/api/v1/bookings")
  public ResponseEntity<DataResponse<BookingCreateResponse>> create(
      @AuthenticationPrincipal AuthSession session,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody BookingCreateRequest request) {
    var result = service.create(session, idempotencyKey, request);
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore()).body(new DataResponse<>(result.booking()));
  }
}
