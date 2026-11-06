package com.skygo.backend.booking.contract;

import java.time.Instant;
import java.util.List;

public record PaymentLinkResponse(
    String bookingId, String bookingCode, String bookingStatus, String seatHoldId,
    Instant expiresAt, String tripType, PassengerCountsRequest passengerCounts,
    List<BookingOpenResponse.Leg> legs, BookingOpenResponse.Price price, String currency,
    PaymentLink paymentLink) {
  public record PaymentLink(String paymentId, String paymentLinkId, long orderCode,
      String status, long amount, String currency, String description, String qrCode,
      String checkoutUrl) {}
}
