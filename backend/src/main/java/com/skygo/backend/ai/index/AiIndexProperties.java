package com.skygo.backend.ai.index;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("skygo.ai.index")
public record AiIndexProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("5m") Duration fixedDelay,
    @Min(1) @Max(64) @DefaultValue("8") int batchSize,
    @DefaultValue("10s") Duration batchDelay,
    @Min(1) @Max(365) @DefaultValue("30") int flightDays,
    @Min(1) @Max(3650) @DefaultValue("15") int bookingDays,
    @Min(100) @Max(500000) @DefaultValue("50000") int maxDocuments) {
  public AiIndexProperties {
    if (fixedDelay == null || fixedDelay.compareTo(Duration.ofMinutes(1)) < 0)
      throw new IllegalArgumentException("AI index fixed-delay must be at least one minute.");
    if (batchDelay == null || batchDelay.isNegative() || batchDelay.compareTo(Duration.ofMinutes(1)) > 0)
      throw new IllegalArgumentException("AI index batch-delay must be between zero and one minute.");
  }
}
