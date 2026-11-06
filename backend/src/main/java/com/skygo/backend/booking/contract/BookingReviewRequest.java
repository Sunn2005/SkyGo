package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.model.SeatHold.TripType;
import com.skygo.backend.booking.contract.BookingQuoteRequest.Selection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record BookingReviewRequest(@NotBlank @Size(max=64) String seatHoldId,
    @NotNull TripType tripType, @NotNull @Valid PassengerCountsRequest passengerCounts,
    @NotNull @Size(min=1,max=2) List<@NotNull @Valid Selection> selections,
    @NotEmpty List<@NotNull @Valid PassengerRequest> passengers, @NotNull @Valid Contact contact) {
  public record Contact(@NotBlank @Email @Size(max=254) String email,
      @NotBlank @Size(max=40) String phoneNumber) {
    @Override public String toString() { return "Contact[redacted]"; }
  }
  public BookingQuoteRequest quoteRequest() {
    return new BookingQuoteRequest(seatHoldId,tripType,passengerCounts,selections);
  }
  @Override public String toString() { return "BookingReviewRequest[redacted]"; }
}
