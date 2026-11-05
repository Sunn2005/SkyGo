package com.skygo.backend.booking.model;

import com.skygo.backend.booking.contract.PassengerCountsRequest;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.model.SeatHold.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingQuote(TripType tripType, PassengerCountsRequest passengerCounts, List<Leg> legs,
    long fareSubtotal, BigDecimal vatRate, long vatAmount, long totalAmount, String currency) {
  public record Airport(String code, String name, String city, String countryCode, String timeZone) {}
  public record FareLine(PassengerType passengerType, int quantity, long unitBaseFare, long fareSubtotal) {}
  public record Benefit(PassengerType passengerType, int carryOnMaxKg, int checkedPieces,
      int checkedPieceMaxKg, int includedMealsPerLeg, String seatType, int strollerPieces, int strollerMaxKg) {}
  public record Leg(Direction direction, long fareId, String flightId, String flightNumber,
      String airlineCode, String airlineName, CabinClass cabinClass, Airport origin, Airport destination,
      Instant departureAt, Instant arrivalAt, long durationMinutes, String routeType,
      long pricingPolicyId, int pricingPolicyVersion, long pricedDistanceKm, long adultBaseFare,
      List<FareLine> fareLines, List<Benefit> benefits) {}
}
