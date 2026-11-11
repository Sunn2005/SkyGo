package com.skygo.backend.ai.chat.contract;

import com.skygo.backend.booking.contract.BookingListResponse.BookingSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AiChatResponse(Status status, String answer, List<Source> sources,
    List<FlightResult> flightSearches, List<BookingResult> bookingSearches,
    List<Clarification> clarifications) {
  public enum Status { ANSWERED, NEEDS_CLARIFICATION, LOGIN_REQUIRED, NO_DATA }
  public record Source(String type, String id, String title, Instant asOf) {}
  public record Airport(String code, String name, String city, String country, String timeZone) {}
  public record PassengerCounts(int adults, int children, int infants) {}
  public record FlightOption(String flightId, String flightNumber, String cabinClass,
      OffsetDateTime departureAt, OffsetDateTime arrivalAt, long durationMinutes,
      long availableSeats, long adultPrice, long partyTotal, String currency) {}
  public record FlightResult(Airport origin, Airport destination, LocalDate departureDate,
      PassengerCounts passengerCounts, String sort, long totalFareOptions, boolean hasMore,
      boolean includesVat, boolean includesExtraBaggage, String priceScope,
      Instant asOf, List<FlightOption> options) {}
  public record BookingResult(int days, int limit, String status, Instant asOf,
      List<BookingSummary> bookings) {}
  public record Clarification(String field, String message, List<Airport> airportOptions) {}
}
