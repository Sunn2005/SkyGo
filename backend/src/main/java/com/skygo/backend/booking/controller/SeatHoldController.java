package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seat-holds")
public class SeatHoldController {
  private final SeatHoldService service;
  public SeatHoldController(SeatHoldService service) { this.service = service; }
  @GetMapping("/current")
  public DataResponse<SeatHoldResponse> current(@AuthenticationPrincipal AuthSession session) {
    return new DataResponse<>(service.current(session.user().id()));
  }
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DataResponse<SeatHoldResponse> create(@AuthenticationPrincipal AuthSession session,
      @Valid @RequestBody SeatHoldCreateRequest request) {
    return new DataResponse<>(service.create(session.user().id(), request));
  }
  @PutMapping("/{seatHoldId}/legs/{direction}")
  public DataResponse<SeatHoldResponse> leg(@AuthenticationPrincipal AuthSession session,
      @PathVariable String seatHoldId, @PathVariable String direction,
      @Valid @RequestBody SeatHoldLegRequest request) {
    return new DataResponse<>(service.leg(session.user().id(), seatHoldId, direction, request));
  }
  @PutMapping("/{seatHoldId}/passenger-counts")
  public DataResponse<SeatHoldResponse> counts(@AuthenticationPrincipal AuthSession session,
      @PathVariable String seatHoldId, @Valid @RequestBody PassengerCountsRequest request) {
    return new DataResponse<>(service.counts(session.user().id(), seatHoldId, request));
  }
}
