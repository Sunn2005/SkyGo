package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.contract.PassengerRequest.*;
import com.skygo.backend.booking.model.SeatHold.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record BookingReviewResponse(String reviewId, long version, Instant expiresAt, long totalAmount,
    String currency, List<Passenger> passengers, BookingReviewRequest.Contact contact,
    TripType tripType, PassengerCountsRequest passengerCounts, List<Leg> legs, Price price) {
  public record Airline(String code,String name) {}
  public record Airport(String code,String name,String city,String countryCode) {}
  public record Leg(Direction direction,String flightId,String flightNumber,Airline airline,CabinClass cabinClass,
      Airport origin,Airport destination,OffsetDateTime departureAt,OffsetDateTime arrivalAt,long durationMinutes,
      BaggageOptionsResponse.CheckedBaggagePolicy checkedBaggagePolicy,
      List<BaggageOptionsResponse.PassengerBaggage> passengerBaggage) {}
  public record Price(long fareSubtotal,long extraBaggageAmount,BigDecimal vatRate,long vatAmount,long totalAmount) {}
  public record Passenger(String clientPassengerId, PassengerType passengerType, Title title,
      String givenNames, String surname, LocalDate dateOfBirth, Gender gender, Document document) {
    @Override public String toString() { return "Passenger[redacted]"; }
  }
  public record Document(DocumentType documentType, String maskedNumber, String issuingCountryCode,
      String nationalityCountryCode, LocalDate expiresOn) {}
  @Override public String toString() { return "BookingReviewResponse[redacted]"; }
}
