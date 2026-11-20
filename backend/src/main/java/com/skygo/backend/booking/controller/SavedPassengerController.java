package com.skygo.backend.booking.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.service.SavedPassengerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/saved-passengers")
public class SavedPassengerController {
  private final SavedPassengerService service;
  public SavedPassengerController(SavedPassengerService service) { this.service = service; }

  @GetMapping
  public ResponseEntity<DataResponse<List<SavedPassengerResponse.Summary>>> list(@AuthenticationPrincipal AuthSession session) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(service.list(session.user().id())));
  }
  @GetMapping("/{id}")
  public ResponseEntity<DataResponse<SavedPassengerResponse>> get(@AuthenticationPrincipal AuthSession session, @PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(service.get(session.user().id(), id)));
  }
  @PostMapping
  public ResponseEntity<DataResponse<SavedPassengerResponse>> create(@AuthenticationPrincipal AuthSession session,
      @Valid @RequestBody SavedPassengerRequest request) {
    var result = service.create(session.user().id(), request);
    return ResponseEntity.created(java.net.URI.create("/api/v1/saved-passengers/" + result.savedPassengerId()))
        .cacheControl(CacheControl.noStore()).body(new DataResponse<>(result));
  }
  @PutMapping("/{id}")
  public ResponseEntity<DataResponse<SavedPassengerResponse>> update(@AuthenticationPrincipal AuthSession session,
      @PathVariable String id, @Valid @RequestBody SavedPassengerRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new DataResponse<>(service.update(session.user().id(), id, request)));
  }
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthSession session, @PathVariable String id) {
    service.delete(session.user().id(), id);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
