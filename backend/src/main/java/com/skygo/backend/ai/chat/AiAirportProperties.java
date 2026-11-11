package com.skygo.backend.ai.chat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("skygo.ai.chat.airports")
public record AiAirportProperties(
    @Min(2) @Max(10) @DefaultValue("5") int topK,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.65") double minimumSimilarity,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.85") double autoSelectSimilarity,
    @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") @DefaultValue("0.10") double minimumScoreGap) {
  public AiAirportProperties {
    if (autoSelectSimilarity < minimumSimilarity)
      throw new IllegalArgumentException("Airport auto-select similarity must not be below retrieval similarity.");
  }
}
