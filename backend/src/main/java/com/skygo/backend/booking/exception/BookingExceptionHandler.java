package com.skygo.backend.booking.exception;

import com.skygo.backend.booking.controller.*;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Order(-2)
@RestControllerAdvice(assignableTypes = {SeatHoldController.class, BookingPaymentController.class,
    BookingQuoteController.class, BookingReviewController.class})
public class BookingExceptionHandler {
  public record Detail(String field, String code, String message) {}
  public record Error(String code, String message, List<Detail> details, String requestId,
      String activeSeatHoldId, String bookingId) {}
  public record Envelope(Error error) {}

  @ExceptionHandler(BookingException.class)
  public ResponseEntity<Envelope> domain(BookingException ex) {
    return ResponseEntity.status(ex.status).body(new Envelope(new Error(ex.code, ex.getMessage(),
        ex.field == null ? List.of() : List.of(new Detail(ex.field, "INVALID_VALUE", ex.getMessage())),
        UUID.randomUUID().toString(), ex.activeSeatHoldId, ex.bookingId)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Envelope> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.unprocessableContent().body(new Envelope(new Error("VALIDATION_ERROR",
        "Thông tin đặt vé không hợp lệ.", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new Detail(e.getField(), "INVALID_VALUE", e.getDefaultMessage())).toList(),
        UUID.randomUUID().toString(), null, null)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Envelope> malformed(HttpMessageNotReadableException ex) {
    return domain(BookingException.invalid(null, "JSON hoặc kiểu dữ liệu không hợp lệ."));
  }
}
