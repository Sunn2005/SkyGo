package com.skygo.backend.booking.model;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import java.time.Instant;
import java.util.List;

public record BookingReviewSnapshot(int schemaVersion, String reviewId, String seatHoldId,
    long userId, long sessionId, Instant createdAt, Instant expiresAt, BookingQuote quote,
    List<PassengerRequest> passengers, BookingReviewRequest.Contact contact,
    List<PassengerBenefit> passengerBenefits) {
  public record PassengerBenefit(String clientPassengerId, Direction direction, BookingQuote.Benefit benefit) {}
  @Override public String toString() { return "BookingReviewSnapshot[redacted]"; }
}
