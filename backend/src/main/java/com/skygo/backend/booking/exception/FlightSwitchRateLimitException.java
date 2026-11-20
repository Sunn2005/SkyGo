package com.skygo.backend.booking.exception;

public class FlightSwitchRateLimitException extends RuntimeException {
  private final long retryAfterSeconds;

  public FlightSwitchRateLimitException(long retryAfterSeconds) {
    super("Bạn đổi chuyến quá nhiều lần. Vui lòng thử lại sau.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
