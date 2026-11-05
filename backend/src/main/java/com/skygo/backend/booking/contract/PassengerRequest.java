package com.skygo.backend.booking.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PassengerRequest(@NotBlank @Size(max=64) String clientPassengerId,
    @NotNull PassengerType passengerType, Title title,
    @NotBlank @Size(max=200) String givenNames, @NotBlank @Size(max=200) String surname,
    @NotNull LocalDate dateOfBirth, @NotNull Gender gender, @NotNull @Valid Document document) {
  public enum PassengerType { ADULT, CHILD, INFANT }
  public enum Title { MR, MRS, MS }
  public enum Gender { MALE, FEMALE, OTHER }
  public enum DocumentType { PERSONAL_ID, PASSPORT }
  public record Document(@NotNull DocumentType documentType,
      @NotBlank @Size(max=64) String number,
      @NotNull @Pattern(regexp="[A-Z]{2}") String issuingCountryCode,
      @Pattern(regexp="[A-Z]{2}") String nationalityCountryCode, LocalDate expiresOn) {
    @Override public String toString() { return "Document[redacted]"; }
  }
  @Override public String toString() { return "PassengerRequest[redacted]"; }
}
