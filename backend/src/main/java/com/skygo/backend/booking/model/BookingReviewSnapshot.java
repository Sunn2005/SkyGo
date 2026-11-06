package com.skygo.backend.booking.model;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public record BookingReviewSnapshot(int schemaVersion, String reviewId, String seatHoldId,
    long userId, long sessionId, Instant createdAt, Instant expiresAt, BookingQuote quote,
    List<PassengerRequest> passengers, BookingReviewRequest.Contact contact,
    List<PassengerBenefit> passengerBenefits, Long version, List<BaggageLeg> baggage, Price price) {
  public record PassengerBenefit(String clientPassengerId, Direction direction, BookingQuote.Benefit benefit) {}
  public record CheckedPolicy(long policyId,int policyVersion,int pieceMaxKg,int maxLinearDimensionsCm,
      int maxExtraPieces,long extraPiecePriceExclVat,String currency) {}
  public record BaggageSelection(String clientPassengerId,int extraPieces,long extraAmountExclVat) {}
  public record BaggageLeg(Direction direction,CheckedPolicy policy,List<BaggageSelection> passengers) {}
  public record Price(long fareSubtotal,long extraBaggageAmount,BigDecimal vatRate,long vatAmount,long totalAmount) {}
  @Override public String toString() { return "BookingReviewSnapshot[redacted]"; }
}
