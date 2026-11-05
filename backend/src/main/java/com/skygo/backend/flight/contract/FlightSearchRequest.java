package com.skygo.backend.flight.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record FlightSearchRequest(
    @NotNull TripType tripType,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String origin,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String destination,
    @NotNull LocalDate departureDate,
    LocalDate returnDate,
    @NotNull @Valid PassengerCounts passengerCounts,
    @NotNull Direction direction,
    @Min(0) @JsonDeserialize(using = StrictInteger.class) Integer page,
    @Min(1) @Max(100) @JsonDeserialize(using = StrictInteger.class) Integer size) {
  public enum TripType { ONE_WAY, ROUND_TRIP }
  public enum Direction { OUTBOUND, RETURN }

  public record PassengerCounts(
      @NotNull @Min(1) @JsonDeserialize(using = StrictInteger.class) Integer adults,
      @NotNull @Min(0) @JsonDeserialize(using = StrictInteger.class) Integer children,
      @NotNull @Min(0) @JsonDeserialize(using = StrictInteger.class) Integer infants) {}

  // Reject fractions and numeric strings instead of silently coercing passenger counts.
  public static class StrictInteger extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
        return context.reportInputMismatch(Integer.class, "Expected a JSON integer.");
      return parser.getIntValue();
    }
  }
}
