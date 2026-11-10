package com.skygo.backend.booking.contract;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingListResponse(List<BookingSummary> data, Pagination pagination) {
  public record Pagination(int page, int size, long totalElements, long totalPages) {}

  public record BookingSummary(
      String bookingId, String bookingCode, String bookingStatus, String paymentStatus,
      Instant createdAt, String tripType, PassengerCountsRequest passengerCounts,
      long totalAmount, String currency, List<Leg> legs) {}

  public record Airport(String code, String city) {}

  public record Leg(String direction, Airport origin, Airport destination,
      OffsetDateTime departureAt, String cabinClass) {}
}
