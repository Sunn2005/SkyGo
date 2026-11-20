package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.exception.BookingExceptionHandler;
import com.skygo.backend.booking.exception.FlightSwitchRateLimitException;
import com.skygo.backend.booking.service.FlightSwitchRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class FlightSwitchRateLimiterTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final FlightSwitchRateLimiter limiter = new FlightSwitchRateLimiter(redis,
      Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC), 10, Duration.ofSeconds(60));

  @Test
  void eleventhAttemptReturns429AndRetryAfter() {
    var calls = new AtomicInteger();
    when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
        .thenAnswer(invocation -> calls.incrementAndGet() <= 10 ? 0L : 6L);

    for (int i = 0; i < 10; i++) assertDoesNotThrow(() -> limiter.check(42));
    var rejected = assertThrows(FlightSwitchRateLimitException.class, () -> limiter.check(42));
    var response = new BookingExceptionHandler().flightSwitchLimit(rejected);

    assertEquals(429, response.getStatusCode().value());
    assertEquals("6", response.getHeaders().getFirst("Retry-After"));
    assertEquals("FLIGHT_SWITCH_RATE_LIMITED", response.getBody().error().code());
    verify(redis, times(11)).execute(any(RedisScript.class), eq(List.of(
        "skygo:rate-limit:flight-switch:v1:user:42")), anyString(), eq("10"), eq("60000"), anyString());
  }

  @Test
  void redisOutageRejectsSwitchWithoutBypassingLimit() {
    when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new DataAccessResourceFailureException("Redis offline"));

    var error = assertThrows(BookingException.class, () -> limiter.check(42));
    assertEquals(503, error.status);
    assertEquals("RATE_LIMIT_UNAVAILABLE", error.code);
  }
}
