package com.skygo.backend.ai.chat;

public final class AiChatException extends RuntimeException {
  private final int status;
  private final String code;

  public AiChatException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
  public int status() { return status; }
  public String code() { return code; }
}
