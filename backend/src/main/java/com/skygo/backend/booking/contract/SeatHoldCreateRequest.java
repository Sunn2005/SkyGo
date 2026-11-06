package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.model.SeatHold.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record SeatHoldCreateRequest(@NotNull TripType tripType,
    @NotNull @Valid PassengerCountsRequest passengerCounts, @NotNull @Valid Selection selection) {
  public record Selection(@NotNull Direction direction,
      @NotBlank @Size(max = 64) String flightId, @NotNull CabinClass cabinClass) {}
}
