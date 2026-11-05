package com.skygo.backend.booking.model;

import java.time.Instant;
import java.util.List;

public record SeatHold(long id, String publicId, long userId, Long bookingId,
    TripType tripType, int adults, int children, int infants, String status,
    Instant startedAt, Instant expiresAt, List<Leg> legs) {
  public enum TripType { ONE_WAY, ROUND_TRIP }
  public enum Direction { OUTBOUND, RETURN }
  public enum CabinClass { ECONOMY, BUSINESS }
  public record Leg(Direction direction, long fareId, long reservedSeats) {}
  public long seats() { return (long) adults + children; }
  public boolean expired(Instant now) { return !now.isBefore(expiresAt); }
}
