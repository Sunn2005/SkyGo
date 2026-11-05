package com.skygo.backend.auth.contract;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Size(max = 40) String phoneNumber, @NotBlank @Size(max = 256) String password) {
  @Override
  public String toString() {
    return "LoginRequest[credentials redacted]";
  }
}
