package com.skygo.backend.auth.service;

import com.skygo.backend.auth.exception.AuthException;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {
  public String normalize(String value) {
    String p = value.trim().replaceAll("[\\s()-]", "");
    if (p.matches("0[35789][0-9]{8}")) return "+84" + p.substring(1);
    if (p.matches("\\+84[35789][0-9]{8}")) return p;
    throw new AuthException(
        422, "VALIDATION_ERROR", "Invalid Vietnamese mobile number.", "phoneNumber", null);
  }
}
