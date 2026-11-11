package com.skygo.backend.ai.service;

/** Safe to report without exposing provider requests, credentials or document contents. */
public final class AiException extends RuntimeException {
  public enum Code { INVALID_CONFIGURATION, EMBEDDING_UNAVAILABLE, VECTOR_STORE_UNAVAILABLE,
    COLLECTION_INCOMPATIBLE, CHAT_UNAVAILABLE, EMBEDDING_RATE_LIMITED, EMBEDDING_TIMEOUT,
    EMBEDDING_AUTHENTICATION_FAILED, EMBEDDING_INVALID_REQUEST, EMBEDDING_NETWORK_ERROR }

  private final Code code;

  public AiException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
