package com.skygo.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.skygo.backend.auth.contract.*;
import com.skygo.backend.auth.exception.AuthException;
import com.skygo.backend.auth.model.*;
import com.skygo.backend.auth.repository.*;
import com.skygo.backend.auth.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
  AppUserRepository users = mock(AppUserRepository.class);
  LoginGuardRepository guards = mock(LoginGuardRepository.class);
  AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  Instant now = Instant.parse("2026-11-05T10:00:00Z");
  BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  AuthService service =
      new AuthService(
          users,
          guards,
          sessions,
          new PhoneNumberNormalizer(),
          new SessionTokenService(),
          encoder,
          Clock.fixed(now, ZoneOffset.UTC),
          Duration.ofHours(24));

  @Test
  void phoneNormalization() {
    var p = new PhoneNumberNormalizer();
    assertEquals("+84912345678", p.normalize("0912 345 678"));
    assertEquals("+84912345678", p.normalize("+84 912345678"));
    assertThrows(AuthException.class, () -> p.normalize("123"));
  }

  @Test
  void fifthFailureLocksFromFifthAttempt() {
    when(guards.lock(anyString()))
        .thenReturn(
            new LoginGuard(
                List.of(
                    now.minusSeconds(900),
                    now.minusSeconds(700),
                    now.minusSeconds(500),
                    now.minusSeconds(100)),
                null));
    when(users.byPhone(anyString())).thenReturn(Optional.empty());
    var e =
        assertThrows(
            AuthException.class, () -> service.login(new LoginRequest("0912345678", "wrong")));
    assertEquals(429, e.status);
    assertEquals(now.plusSeconds(900), e.lockedUntil);
    verify(guards).save(eq("+84912345678"), argThat(a -> a.size() == 5), eq(now.plusSeconds(900)));
  }

  @Test
  void slidingWindowDropsOnlyExpiredAttempts() {
    when(guards.lock(anyString()))
        .thenReturn(
            new LoginGuard(
                List.of(
                    now.minusSeconds(901),
                    now.minusSeconds(700),
                    now.minusSeconds(500),
                    now.minusSeconds(100)),
                null));
    when(users.byPhone(anyString())).thenReturn(Optional.empty());
    assertEquals(
        401,
        assertThrows(
                AuthException.class, () -> service.login(new LoginRequest("0912345678", "wrong")))
            .status);
    verify(guards).save(anyString(), argThat(a -> a.size() == 4), isNull());
  }

  @Test
  void lockedAttemptDoesNotExtendLock() {
    when(guards.lock(anyString())).thenReturn(new LoginGuard(List.of(), now.plusSeconds(10)));
    assertEquals(
        429,
        assertThrows(
                AuthException.class, () -> service.login(new LoginRequest("0912345678", "wrong")))
            .status);
    verify(guards, never()).save(anyString(), anyList(), any());
    verifyNoInteractions(users);
  }

  @Test
  void successfulLoginResetsFailuresAndStoresOnlyTokenHash() {
    when(guards.lock(anyString())).thenReturn(new LoginGuard(List.of(now.minusSeconds(5)), null));
    when(users.byPhone(anyString()))
        .thenReturn(
            Optional.of(
                new AppUser(
                    1,
                    "usr",
                    "Test",
                    "+84912345678",
                    null,
                    encoder.encode("SkyGoDemo@2027"),
                    now)));
    var result = service.login(new LoginRequest("0912345678", "SkyGoDemo@2027"));
    assertEquals(43, result.accessToken().length());
    assertEquals(now.plusSeconds(86400), result.expiresAt());
    verify(guards).save("+84912345678", List.of(), null);
    verify(sessions).create(eq(1L), argThat(a -> a.length == 32), eq(result.expiresAt()));
  }

  @Test
  void expiredLockStartsNewWindow() {
    when(guards.lock(anyString())).thenReturn(new LoginGuard(List.of(now.minusSeconds(901)), now));
    when(users.byPhone(anyString())).thenReturn(Optional.empty());
    assertThrows(AuthException.class, () -> service.login(new LoginRequest("0912345678", "wrong")));
    verify(guards).save("+84912345678", List.of(now), null);
  }

  @Test
  void invalidPhoneDoesNotCountAsFailure() {
    assertThrows(AuthException.class, () -> service.login(new LoginRequest("invalid", "wrong")));
    verifyNoInteractions(guards);
  }

  @Test
  void registerHashesPasswordAndNormalizesPhone() {
    when(users.insert(anyString(), anyString(), eq("test@example.com"), anyString()))
        .thenAnswer(
            call -> {
              assertTrue(encoder.matches("SkyGoDemo@2027", call.getArgument(3)));
              return new AppUser(
                  1,
                  "usr",
                  call.getArgument(0),
                  call.getArgument(1),
                  call.getArgument(2),
                  call.getArgument(3),
                  now);
            });
    var result =
        service.register(
            new RegisterRequest(
                " Test ", "0912345678", "test@example.com", "SkyGoDemo@2027", "SkyGoDemo@2027"));
    assertEquals("Test", result.fullName());
    assertEquals("+84912345678", result.phoneNumber());
    assertEquals("test@example.com", result.email());
  }
}
