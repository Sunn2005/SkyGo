package com.skygo.backend.ai.chat.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiChatRequest(
    @NotBlank @Size(max = 2000) String message,
    @Size(max = 6) List<@NotNull @Valid Turn> history) {
  public enum Role { USER, ASSISTANT }
  public record Turn(@NotNull Role role, @NotBlank @Size(max = 2000) String content) {}
}
