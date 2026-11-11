package com.skygo.backend.ai.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("skygo.ai.chat")
public record AiChatProperties(
    @Min(1) @Max(3) @DefaultValue("2") int maxToolRounds,
    @Min(1) @Max(6) @DefaultValue("4") int maxToolCalls,
    @Min(1) @Max(120) @DefaultValue("4") int modelCallsPerMinute) {}
