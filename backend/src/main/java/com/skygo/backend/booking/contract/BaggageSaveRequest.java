package com.skygo.backend.booking.contract;

import com.skygo.backend.booking.model.SeatHold.Direction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record BaggageSaveRequest(
    @NotNull @Min(1) @Max(4294967295L) @JsonDeserialize(using=StrictVersion.class) Long expectedVersion,
    @NotEmpty List<@NotNull @Valid Selection> selections) {
  public record Selection(@NotBlank @Size(max=64) String clientPassengerId, @NotNull Direction direction,
      @NotNull @Min(0) @Max(2) @JsonDeserialize(using=PassengerCountsRequest.StrictInteger.class) Integer extraPieces) {}
  public static class StrictVersion extends ValueDeserializer<Long> {
    @Override public Long deserialize(JsonParser parser,DeserializationContext context) {
      if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
        return context.reportInputMismatch(Long.class,"Expected a JSON integer.");
      return parser.getLongValue();
    }
  }
}
