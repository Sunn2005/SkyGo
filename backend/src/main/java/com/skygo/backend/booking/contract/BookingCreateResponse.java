package com.skygo.backend.booking.contract;

import java.time.Instant;
import java.util.List;

public record BookingCreateResponse(
    String bookingId, String bookingCode, String bookingStatus, Instant createdAt,
    String currency, String seatHoldId, Instant holdExpiresAt, long totalAmount,
    List<Passenger> passengers, Payment payment) {
  public record Passenger(String passengerId, String clientPassengerId, String ticketNumber) {}
  public record Payment(String paymentId, String paymentMethod, String paymentStatus,
      long amount, String currency, String transactionCode, Instant paidAt) {}
}
