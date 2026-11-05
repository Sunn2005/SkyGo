package com.skygo.backend.flight.contract;

import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResponse(List<Flight> data, Pagination pagination) {
  public record Pagination(int page, int size, long totalElements, long totalPages) {}
  public record Airline(String code, String name) {}
  public record Airport(String code, String city) {}
  public record Price(long amount, String currency, boolean includesVat) {}
  public record Fare(String cabinClass, Price price) {}
  public record Flight(
      String flightId, String flightNumber, Airline airline, Airport origin, Airport destination,
      OffsetDateTime departureAt, OffsetDateTime arrivalAt, long durationMinutes, List<Fare> fares) {}
}
