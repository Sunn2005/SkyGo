package com.skygo.backend.auth.exception;

import java.time.Instant;

public class AuthException extends RuntimeException {
  public final int status;
  public final String code;
  public final String field;
  public final Instant lockedUntil;

  public AuthException(int status, String code, String message) {
    this(status, code, message, null, null);
  }

  public AuthException(int status, String code, String message, String field, Instant lockedUntil) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
    this.lockedUntil = lockedUntil;
  }
}
