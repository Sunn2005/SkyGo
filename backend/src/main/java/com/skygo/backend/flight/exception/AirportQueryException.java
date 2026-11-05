package com.skygo.backend.flight.exception;

public class AirportQueryException extends RuntimeException {
  private final String field;

  public AirportQueryException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
