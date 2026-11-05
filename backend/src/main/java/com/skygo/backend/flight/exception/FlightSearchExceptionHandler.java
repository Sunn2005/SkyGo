package com.skygo.backend.flight.exception;

import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.skygo.backend.flight.controller.FlightSearchController;

@Order(-1)
@RestControllerAdvice(assignableTypes = FlightSearchController.class)
public class FlightSearchExceptionHandler {
  public record Detail(String field, String code, String message) {}
  public record Error(String code, String message, List<Detail> details, String requestId) {}
  public record Envelope(Error error) {}

  @ExceptionHandler(FlightSearchException.class)
  public ResponseEntity<Envelope> invalid(FlightSearchException ex) {
    return response(ex.getMessage(), List.of(new Detail(ex.field(), "INVALID_VALUE", ex.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Envelope> validation(MethodArgumentNotValidException ex) {
    return response("Thông tin tìm kiếm không hợp lệ.", ex.getBindingResult().getFieldErrors()
        .stream().map(e -> new Detail(e.getField(), "INVALID_VALUE", e.getDefaultMessage())).toList());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Envelope> malformed(HttpMessageNotReadableException ex) {
    return response("JSON, kiểu dữ liệu hoặc định dạng ngày không hợp lệ.", List.of());
  }

  private ResponseEntity<Envelope> response(String message, List<Detail> details) {
    return ResponseEntity.unprocessableContent().body(new Envelope(
        new Error("VALIDATION_ERROR", message, details, UUID.randomUUID().toString())));
  }
}
