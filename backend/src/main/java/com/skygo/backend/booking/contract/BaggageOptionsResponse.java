package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.model.SeatHold.CabinClass;
import com.skygo.backend.booking.model.SeatHold.Direction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BaggageOptionsResponse(long version, Instant expiresAt, String currency,
    PassengerCountsRequest passengerCounts, List<Passenger> passengers, List<Leg> legs,
    long totalBaggageAmountExclVat) {
  public record Passenger(String clientPassengerId, String fullName, PassengerType passengerType) {}
  public record CheckedBaggagePolicy(int pieceMaxKg, int maxLinearDimensionsCm,
      int maxExtraPiecesPerPassenger, long extraPiecePriceExclVat) {}
  public record Included(int carryOnMaxKg, int checkedPieces, int strollerPieces, int strollerMaxKg) {}
  public record PassengerBaggage(String clientPassengerId, Included included, boolean canPurchaseExtra,
      int selectedExtraPieces, long extraAmountExclVat) {}
  public record Leg(Direction direction, String originCode, String destinationCode,
      LocalDate departureDate, CabinClass cabinClass, CheckedBaggagePolicy checkedBaggagePolicy,
      List<PassengerBaggage> passengerBaggage) {}
}
