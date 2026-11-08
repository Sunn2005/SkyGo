package com.skygo.backend.flight.contract;

import com.skygo.backend.flight.contract.FlightSearchRequest.Direction;
import com.skygo.backend.flight.contract.FlightSearchResponse.Flight;
import java.time.LocalDate;
import java.util.List;

public record NearbyFlightSearchResponse(
    LocalDate centerDate, LocalDate startDate, LocalDate endDate,
    Direction direction, List<Day> days) {
  public enum DisabledReason {
    PAST_DATE, RETURN_NOT_AFTER_DEPARTURE, DEPARTURE_NOT_BEFORE_RETURN
  }

  public record Day(LocalDate date, boolean selectable, DisabledReason disabledReason,
      List<Flight> flights, Pagination pagination) {}

  public record Pagination(int page, int size, long totalElements, long totalPages,
      boolean hasMore) {}
}
