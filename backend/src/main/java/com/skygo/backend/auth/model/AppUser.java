package com.skygo.backend.auth.model;

import com.skygo.backend.auth.contract.AuthUserResponse;
import java.time.Instant;

public record AppUser(
    long id,
    String publicId,
    String fullName,
    String phone,
    String email,
    String passwordHash,
    Instant createdAt) {
  @Override
  public String toString() {
    return "AppUser[publicId=" + publicId + "]";
  }

  public AuthUserResponse response() {
    return new AuthUserResponse(publicId, fullName, phone, email, createdAt);
  }
}
