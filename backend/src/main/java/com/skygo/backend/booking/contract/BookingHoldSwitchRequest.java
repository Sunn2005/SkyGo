package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.contract.SeatHoldCreateRequest.Selection;
import com.skygo.backend.booking.model.SeatHold.TripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record BookingHoldSwitchRequest(
    @NotBlank @Size(max=64) String expectedSeatHoldId,
    @NotNull @Min(1) @JsonDeserialize(using=SeatHoldSelectionRequest.StrictLong.class) Long expectedVersion,
    @NotNull TripType tripType,
    @NotNull @Valid PassengerCountsRequest passengerCounts,
    @NotNull @Size(min=1,max=2) List<@NotNull @Valid Selection> selections) {}
