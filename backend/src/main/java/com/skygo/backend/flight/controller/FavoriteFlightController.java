package com.skygo.backend.flight.controller;

import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.flight.contract.FavoriteFlightResponse;
import com.skygo.backend.flight.service.FavoriteFlightService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/favorite-flights")
public class FavoriteFlightController {
  private final FavoriteFlightService service;
  public FavoriteFlightController(FavoriteFlightService service) { this.service=service; }
  @PutMapping("/{flightId}")
  public ResponseEntity<Void> save(@AuthenticationPrincipal AuthSession session,@PathVariable String flightId) {
    service.save(session.user().id(),flightId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
  @DeleteMapping("/{flightId}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthSession session,@PathVariable String flightId) {
    service.delete(session.user().id(),flightId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
  @GetMapping
  public ResponseEntity<FavoriteFlightResponse> list(@AuthenticationPrincipal AuthSession session,
      @RequestParam(defaultValue="0") String page,@RequestParam(defaultValue="20") String size) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.list(session.user().id(),page,size));
  }
}
