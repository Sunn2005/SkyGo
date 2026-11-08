package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.model.SeatHold.TripType;
import com.skygo.backend.booking.contract.SeatHoldCreateRequest.Selection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SeatHoldSelectionRequest(
    @NotNull @Min(1) @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
    @NotNull TripType tripType,
    @NotNull @Valid PassengerCountsRequest passengerCounts,
    @NotNull @Size(min = 1, max = 2) List<@NotNull @Valid Selection> selections) {
  public static class StrictLong extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
        return context.reportInputMismatch(Long.class, "Expected a JSON integer.");
      return parser.getLongValue();
    }
  }
}
