package com.skygo.backend.booking.exception;

public class BookingException extends RuntimeException {
  public final int status;
  public final String code;
  public final String field;
  public final String activeSeatHoldId;
  public final String bookingId;

  public BookingException(int status, String code, String message) {
    this(status, code, message, null, null, null);
  }

  public BookingException(int status, String code, String message, String field,
      String activeSeatHoldId, String bookingId) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
    this.activeSeatHoldId = activeSeatHoldId;
    this.bookingId = bookingId;
  }

  public static BookingException invalid(String field, String message) {
    return new BookingException(422, "VALIDATION_ERROR", message, field, null, null);
  }
}
