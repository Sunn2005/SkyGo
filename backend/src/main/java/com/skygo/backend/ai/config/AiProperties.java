package com.skygo.backend.ai.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Secrets deliberately stay out of this record and its generated toString(). */
@Validated
@ConfigurationProperties("skygo.ai")
public record AiProperties(
    @DefaultValue("false") boolean enabled,
    @NotBlank @DefaultValue("gemini-3.8-flash") String chatModel,
    @NotBlank @DefaultValue("gemini-embedding-001") String embeddingModel,
    @Min(128) @Max(3072) @DefaultValue("768") int embeddingDimensions,
    @NotNull @DefaultValue("30s") Duration timeout,
    @Min(1) @Max(10) @DefaultValue("4") int topK,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.65") double similarityThreshold,
    @Min(128) @Max(8192) @DefaultValue("2048") int maxOutputTokens) {

  public AiProperties {
    if (timeout != null && (timeout.isNegative() || timeout.isZero()
        || timeout.compareTo(Duration.ofMinutes(2)) > 0))
      throw new IllegalArgumentException("Thời gian chờ AI phải lớn hơn 0 và không quá 2 phút.");
  }

}
