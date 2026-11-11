package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.chat.contract.AiChatResponse.Clarification;
import com.skygo.backend.ai.chat.contract.AiChatResponse.Airport;
import java.util.List;

public final class AiClarification extends RuntimeException {
  private final Clarification detail;
  public AiClarification(String field, String message) { this(field, message, List.of()); }
  public AiClarification(String field, String message, List<Airport> options) {
    super(message);
    detail = new Clarification(field, message, List.copyOf(options));
  }
  public Clarification detail() { return detail; }
}
