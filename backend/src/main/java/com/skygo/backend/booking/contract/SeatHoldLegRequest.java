package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.model.SeatHold.CabinClass;
import jakarta.validation.constraints.*;

public record SeatHoldLegRequest(
    @NotBlank @Size(max = 64) String flightId, @NotNull CabinClass cabinClass) {}
