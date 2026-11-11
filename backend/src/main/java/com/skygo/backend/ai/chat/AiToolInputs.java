package com.skygo.backend.ai.chat;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class AiToolInputs {
  private AiToolInputs() {}
  public enum Cabin { ANY, ECONOMY, BUSINESS }
  public enum Sort { PRICE_ASC, DEPARTURE_ASC, DURATION_ASC }
  public enum BookingStatus { ALL, PAID, PENDING }

  public record FlightQuery(@NotBlank @Size(max = 120) String origin,
      @NotBlank @Size(max = 120) String destination, @NotNull LocalDate departureDate,
      @Min(1) @Max(9) Integer adults, @Min(0) @Max(8) Integer children,
      @Min(0) @Max(9) Integer infants, Cabin cabin, Sort sort, @Min(1) @Max(10) Integer limit) {
    public FlightQuery {
      adults = adults == null ? 1 : adults;
      children = children == null ? 0 : children;
      infants = infants == null ? 0 : infants;
      cabin = cabin == null ? Cabin.ANY : cabin;
      sort = sort == null ? Sort.PRICE_ASC : sort;
      limit = limit == null ? 5 : limit;
    }
  }

  public record BookingQuery(@Min(1) @Max(365) Integer days,
      @Min(1) @Max(10) Integer limit, BookingStatus status) {
    public BookingQuery {
      days = days == null ? 15 : days;
      limit = limit == null ? 3 : limit;
      status = status == null ? BookingStatus.ALL : status;
    }
  }
  public record KnowledgeQuery(@NotBlank @Size(max = 500) String query) {}
}
