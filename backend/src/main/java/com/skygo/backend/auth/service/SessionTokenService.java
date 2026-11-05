package com.skygo.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenService {
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public byte[] hash(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
