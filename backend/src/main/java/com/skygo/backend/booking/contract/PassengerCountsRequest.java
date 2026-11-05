package com.skygo.backend.booking.contract;

import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PassengerCountsRequest(
    @NotNull @Min(1) @JsonDeserialize(using = StrictInteger.class) Integer adults,
    @NotNull @Min(0) @JsonDeserialize(using = StrictInteger.class) Integer children,
    @NotNull @Min(0) @JsonDeserialize(using = StrictInteger.class) Integer infants) {
  public long seats() { return (long) adults + children; }
  public static class StrictInteger extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
        return context.reportInputMismatch(Integer.class, "Expected a JSON integer.");
      return parser.getIntValue();
    }
  }
}
