package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.contract.PassengerRequest.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record SavedPassengerRequest(Title title,
    @NotBlank @Size(max = 200) String givenNames,
    @NotBlank @Size(max = 200) String surname,
    @NotNull LocalDate dateOfBirth, @NotNull Gender gender,
    @NotNull @Valid Document document) {
  @Override public String toString() { return "SavedPassengerRequest[redacted]"; }
}
