package com.skygo.backend.auth.contract;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Size(max = 40) String phoneNumber,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 256) String password,
    @NotBlank @Size(max = 256) String confirmPassword) {
  @Override
  public String toString() {
    return "RegisterRequest[credentials redacted]";
  }
}
