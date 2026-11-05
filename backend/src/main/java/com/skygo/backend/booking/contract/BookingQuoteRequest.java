package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.model.SeatHold.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record BookingQuoteRequest(@NotBlank @Size(max=64) String seatHoldId,
    @NotNull TripType tripType, @NotNull @Valid PassengerCountsRequest passengerCounts,
    @NotNull @Size(min=1,max=2) List<@NotNull @Valid Selection> selections) {
  public record Selection(@NotNull Direction direction,
      @NotBlank @Size(max=64) String flightId, @NotNull CabinClass cabinClass) {}
}
