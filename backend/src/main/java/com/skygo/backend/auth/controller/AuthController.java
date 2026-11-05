package com.skygo.backend.auth.controller;

import com.skygo.backend.auth.contract.*;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  public ResponseEntity<DataResponse<AuthUserResponse>> register(
      @Valid @RequestBody RegisterRequest input) {
    return ResponseEntity.status(201).body(new DataResponse<>(service.register(input)));
  }

  @PostMapping("/login")
  public DataResponse<LoginResponse> login(@Valid @RequestBody LoginRequest input) {
    return new DataResponse<>(service.login(input));
  }

  @GetMapping("/me")
  public DataResponse<AuthUserResponse> me(@AuthenticationPrincipal AuthSession session) {
    return new DataResponse<>(session.user().response());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal AuthSession session) {
    service.logout(session);
  }
}
