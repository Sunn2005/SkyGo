package com.skygo.backend.booking.contract;

import jakarta.validation.constraints.*;

public record BookingCreateRequest(
    @NotBlank @Size(max = 64) String reviewId,
    @NotNull @Min(1) @Max(4294967295L)
        @tools.jackson.databind.annotation.JsonDeserialize(using = BaggageSaveRequest.StrictVersion.class)
        Long expectedVersion) {}
