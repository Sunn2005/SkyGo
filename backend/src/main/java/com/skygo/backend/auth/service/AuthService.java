package com.skygo.backend.auth.service;

import com.skygo.backend.auth.contract.*;
import com.skygo.backend.auth.exception.AuthException;
import com.skygo.backend.auth.model.AuthSession;
import com.skygo.backend.auth.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final AppUserRepository users;
  private final LoginGuardRepository guards;
  private final AuthSessionRepository sessions;
  private final PhoneNumberNormalizer phones;
  private final SessionTokenService tokens;
  private final PasswordEncoder passwords;
  private final Clock clock;
  private final Duration ttl;
  private final String dummyHash;

  public AuthService(
      AppUserRepository users,
      LoginGuardRepository guards,
      AuthSessionRepository sessions,
      PhoneNumberNormalizer phones,
      SessionTokenService tokens,
      PasswordEncoder passwords,
      Clock clock,
      @Value("${skygo.auth.session-ttl:24h}") Duration ttl) {
    if (ttl.isNegative() || ttl.isZero())
      throw new IllegalArgumentException("Session TTL must be positive");
    this.users = users;
    this.guards = guards;
    this.sessions = sessions;
    this.phones = phones;
    this.tokens = tokens;
    this.passwords = passwords;
    this.clock = clock;
    this.ttl = ttl;
    this.dummyHash = passwords.encode("DummyPassword@123");
  }

  @Transactional
  public AuthUserResponse register(RegisterRequest input) {
    String phone = phones.normalize(input.phoneNumber());
    String password = input.password();
    if (password.getBytes(StandardCharsets.UTF_8).length > 72
        || !password.matches("(?s).*[A-Z].*")
        || !password.matches("(?s).*[a-z].*")
        || !password.matches("(?s).*[0-9].*")
        || !password.matches("(?s).*[^A-Za-z0-9\\s].*"))
      throw new AuthException(
          422,
          "VALIDATION_ERROR",
          "Password requires upper/lowercase, number and symbol; maximum 72 UTF-8 bytes.",
          "password",
          null);
    if (!password.equals(input.confirmPassword()))
      throw new AuthException(
          422, "VALIDATION_ERROR", "Passwords do not match.", "confirmPassword", null);
    String email = input.email().trim();
    try {
      return users
          .insert(input.fullName().trim(), phone, email, passwords.encode(password))
          .response();
    } catch (DuplicateKeyException ex) {
      throw new AuthException(
          409, "PHONE_ALREADY_REGISTERED", "Phone number already registered.", "phoneNumber", null);
    }
  }

  // Invalid credentials must commit login_guard changes before returning 401/429.
  @Transactional(noRollbackFor = AuthException.class)
  public LoginResponse login(LoginRequest input) {
    String phone = phones.normalize(input.phoneNumber());
    var guard = guards.lock(phone);
    Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (guard.lockedUntil() != null && guard.lockedUntil().isAfter(now))
      throw locked(guard.lockedUntil());
    var failures = new ArrayList<Instant>();
    if (guard.lockedUntil() == null)
      guard.failures().stream()
          .filter(t -> !t.isBefore(now.minus(Duration.ofMinutes(15))))
          .forEach(failures::add);
    var user = users.byPhone(phone);
    boolean valid =
        input.password().getBytes(StandardCharsets.UTF_8).length <= 72
            && passwords.matches(
                input.password(), user.map(u -> u.passwordHash()).orElse(dummyHash));
    if (user.isEmpty() || !valid) {
      failures.add(now);
      Instant until = failures.size() >= 5 ? now.plus(Duration.ofMinutes(15)) : null;
      guards.save(phone, failures, until);
      if (until != null) throw locked(until);
      throw new AuthException(401, "INVALID_CREDENTIALS", "Phone number or password is incorrect.");
    }
    guards.save(phone, List.of(), null);
    String token = tokens.generate();
    Instant expiry = now.plus(ttl);
    sessions.create(user.get().id(), tokens.hash(token), expiry);
    return new LoginResponse(token, "Bearer", expiry, user.get().response());
  }

  private AuthException locked(Instant until) {
    return new AuthException(
        429, "LOGIN_TEMPORARILY_LOCKED", "Login temporarily locked.", null, until);
  }

  public Optional<AuthSession> authenticate(String token) {
    return token.matches("[A-Za-z0-9_-]{43}")
        ? sessions.find(tokens.hash(token))
        : Optional.empty();
  }

  @Transactional
  public void logout(AuthSession session) {
    sessions.revoke(session.id());
  }
}
