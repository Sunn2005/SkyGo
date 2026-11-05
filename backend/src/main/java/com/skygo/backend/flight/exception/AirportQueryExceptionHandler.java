package com.skygo.backend.flight.exception;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AirportQueryExceptionHandler {
  public record Detail(String field, String code, String message) {}
  public record Error(String code, String message, List<Detail> details, String requestId) {}
  public record Envelope(Error error) {}

  @ExceptionHandler(AirportQueryException.class)
  public ResponseEntity<Envelope> invalidQuery(AirportQueryException ex) {
    return ResponseEntity.unprocessableContent().body(new Envelope(new Error(
        "VALIDATION_ERROR", ex.getMessage(),
        List.of(new Detail(ex.field(), "INVALID_VALUE", ex.getMessage())),
        UUID.randomUUID().toString())));
  }
}
