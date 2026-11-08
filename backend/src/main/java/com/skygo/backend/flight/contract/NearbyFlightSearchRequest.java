package com.skygo.backend.flight.contract;

import com.skygo.backend.flight.contract.FlightSearchRequest.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** The nearby window and first-page size are fixed by the server. */
public record NearbyFlightSearchRequest(
    @NotNull TripType tripType,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String origin,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String destination,
    @NotNull LocalDate departureDate,
    LocalDate returnDate,
    @NotNull @Valid PassengerCounts passengerCounts,
    @NotNull Direction direction) {
  public static final int DAYS_EACH_SIDE = 3;
  public static final int PAGE_SIZE = 10;

  public FlightSearchRequest toSearchRequest() {
    return new FlightSearchRequest(tripType, origin, destination, departureDate, returnDate,
        passengerCounts, direction, 0, PAGE_SIZE);
  }
}
