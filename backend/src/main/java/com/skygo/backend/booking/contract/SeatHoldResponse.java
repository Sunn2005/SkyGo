package com.skygo.backend.booking.contract;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record SeatHoldResponse(String seatHoldId, String status, String tripType,
    PassengerCountsRequest passengerCounts, Instant expiresAt, List<Leg> legs, String bookingId) {
  public record Airport(String code, String city) {}
  public record Leg(String direction, String flightId, String cabinClass, Airport origin,
      Airport destination, OffsetDateTime departureAt, OffsetDateTime arrivalAt) {}
}
