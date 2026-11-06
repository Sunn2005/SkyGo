package com.skygo.backend.booking.contract;

import java.time.*;
import java.util.List;

public record BookingDetailResponse(
    String bookingId, String bookingCode, String bookingStatus, Instant createdAt,
    Instant serverTime, String tripType, PassengerCountsRequest passengerCounts,
    BookingOpenResponse.Contact contact, Hold seatHold, BookingOpenResponse.Price price,
    String currency, BookingOpenResponse.Payment payment, List<BookingOpenResponse.Leg> legs,
    List<Passenger> passengers, Invoice invoice) {
  public record Hold(String seatHoldId, String status, Instant expiresAt, boolean expired) {}
  public record Invoice(String invoiceId, Instant issuedAt) {}
  public record Benefit(String direction, int carryOnMaxKg, int checkedPieces,
      int extraCheckedPieces, int includedMealsPerLeg, String seatType, int strollerPieces) {}
  public record Passenger(String passengerId, String clientPassengerId, String passengerType,
      String title, String givenNames, String surname, LocalDate dateOfBirth, String gender,
      BookingOpenResponse.Document document, List<Benefit> benefits, String ticketNumber) {}

  @Override public String toString() { return "BookingDetailResponse[redacted]"; }
}
