package com.skygo.backend.booking.contract;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record BookingOpenResponse(
    String bookingId, String bookingCode, String bookingStatus, Instant createdAt,
    String tripType, PassengerCountsRequest passengerCounts, Contact contact,
    String seatHoldId, Instant holdExpiresAt, Price price, String currency,
    Payment payment, List<Leg> legs, List<Passenger> passengers) {
  public record Contact(String email, String phoneNumber) {}
  public record Price(long fareSubtotal, long extraBaggageAmount, BigDecimal vatRate,
      long vatAmount, long totalAmount) {}
  public record Payment(String paymentId, String method, String status, long amount,
      String currency, String transactionCode, Instant paidAt) {}
  public record Airline(String code, String name) {}
  public record Airport(String code, String name, String city, String countryCode) {}
  public record FareLine(String passengerType, int quantity, long unitBaseFare, long subtotal) {}
  public record Leg(String direction, String flightId, String flightNumber, Airline airline,
      String cabinClass, Airport origin, Airport destination, OffsetDateTime departureAt,
      OffsetDateTime arrivalAt, int durationMinutes, String routeType, List<FareLine> fares) {}
  public record Document(String type, String maskedNumber, String issuingCountryCode,
      String nationalityCountryCode, LocalDate expiresOn) {}
  public record Benefit(String direction, int carryOnMaxKg, int checkedPieces,
      int checkedPieceMaxKg, Integer checkedPieceMaxLinearCm, int extraCheckedPieces,
      long extraUnitPriceExclVat, long extraBaggageAmount, int includedMealsPerLeg,
      String seatType, int strollerPieces, int strollerMaxKg) {}
  public record Passenger(String passengerId, String clientPassengerId, String passengerType,
      String title, String givenNames, String surname, LocalDate dateOfBirth, String gender,
      Document document, List<Benefit> benefits, String ticketNumber) {}

  @Override public String toString() { return "BookingOpenResponse[redacted]"; }
}
