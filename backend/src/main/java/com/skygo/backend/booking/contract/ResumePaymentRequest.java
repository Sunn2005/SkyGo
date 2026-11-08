package com.skygo.backend.booking.contract;

import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Optional confirmation to replace the current unbooked selection with the saved booking. */
public record ResumePaymentRequest(
    @NotBlank @Size(max=64) String expectedSeatHoldId,
    @NotNull @Min(1) @JsonDeserialize(using=SeatHoldSelectionRequest.StrictLong.class) Long expectedVersion) {}
