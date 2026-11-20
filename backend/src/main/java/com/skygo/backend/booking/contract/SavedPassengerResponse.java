package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.contract.PassengerRequest.*;
import java.time.LocalDate;

public record SavedPassengerResponse(String savedPassengerId, Title title, String givenNames,
    String surname, LocalDate dateOfBirth, Gender gender, Document document) {
  public record Summary(String savedPassengerId, String givenNames, String surname,
      LocalDate dateOfBirth, DocumentType documentType, String documentNumberLast4) {}
  @Override public String toString() { return "SavedPassengerResponse[redacted]"; }
}
