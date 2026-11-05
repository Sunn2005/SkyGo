package com.skygo.backend.flight.exception;

public class FlightSearchException extends RuntimeException {
  private final String field;

  public FlightSearchException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
