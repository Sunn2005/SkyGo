package com.skygo.backend.auth.exception;

import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class AuthExceptionHandler {
  public record Detail(String field, String code, String message) {}

  public record Error(
      String code, String message, List<Detail> details, String requestId, Instant lockedUntil) {}

  public record Envelope(Error error) {}

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<Envelope> auth(AuthException ex) {
    var details =
        ex.field == null
            ? List.<Detail>of()
            : List.of(new Detail(ex.field, ex.code, ex.getMessage()));
    var builder = ResponseEntity.status(ex.status);
    if (ex.lockedUntil != null)
      builder.header(
          "Retry-After",
          Long.toString(
              Math.max(1, Duration.between(Instant.now(), ex.lockedUntil).toSeconds() + 1)));
    return builder.body(
        new Envelope(
            new Error(
                ex.code, ex.getMessage(), details, UUID.randomUUID().toString(), ex.lockedUntil)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Envelope> validation(MethodArgumentNotValidException ex) {
    var details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new Detail(e.getField(), "INVALID_VALUE", e.getDefaultMessage()))
            .toList();
    return ResponseEntity.unprocessableContent()
        .body(
            new Envelope(
                new Error(
                    "VALIDATION_ERROR",
                    "Please check the submitted fields.",
                    details,
                    UUID.randomUUID().toString(),
                    null)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Envelope> malformed(HttpMessageNotReadableException ex) {
    return auth(new AuthException(422, "VALIDATION_ERROR", "Invalid JSON request."));
  }
}
