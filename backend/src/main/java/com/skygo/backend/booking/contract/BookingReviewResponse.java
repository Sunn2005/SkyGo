package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.contract.PassengerRequest.*;
import java.time.*;
import java.util.List;

public record BookingReviewResponse(String reviewId, Instant expiresAt, long totalAmount,
    String currency, List<Passenger> passengers, BookingReviewRequest.Contact contact) {
  public record Passenger(String clientPassengerId, PassengerType passengerType, Title title,
      String givenNames, String surname, LocalDate dateOfBirth, Gender gender, Document document) {
    @Override public String toString() { return "Passenger[redacted]"; }
  }
  public record Document(DocumentType documentType, String maskedNumber, String issuingCountryCode,
      String nationalityCountryCode, LocalDate expiresOn) {}
  @Override public String toString() { return "BookingReviewResponse[redacted]"; }
}
