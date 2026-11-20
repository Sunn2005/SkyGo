package com.skygo.backend.booking.service;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.exception.FlightSwitchRateLimitException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class FlightSwitchRateLimiter {
  private static final RedisScript<Long> SCRIPT = RedisScript.of(
      new ClassPathResource("redis/flight-switch-rate-limit.lua"), Long.class);
  private static final String KEY_PREFIX = "skygo:rate-limit:flight-switch:v1:user:";

  private final StringRedisTemplate redis;
  private final Clock clock;
  private final int maxRequests;
  private final long windowMillis;

  public FlightSwitchRateLimiter(StringRedisTemplate redis, Clock clock,
      @Value("${skygo.flight-switch.rate-limit.max-requests:10}") int maxRequests,
      @Value("${skygo.flight-switch.rate-limit.window:60s}") Duration window) {
    if (maxRequests <= 0 || window.isZero() || window.isNegative())
      throw new IllegalArgumentException("Flight switch rate limit must be positive");
    this.redis = redis;
    this.clock = clock;
    this.maxRequests = maxRequests;
    this.windowMillis = window.toMillis();
    if (windowMillis == 0) throw new IllegalArgumentException("Flight switch window must be at least 1 ms");
  }

  public void check(long userId) {
    final Long retryAfterSeconds;
    try {
      retryAfterSeconds = redis.execute(SCRIPT, List.of(KEY_PREFIX + userId),
          Long.toString(clock.millis()), Integer.toString(maxRequests),
          Long.toString(windowMillis), UUID.randomUUID().toString());
    } catch (DataAccessException ex) {
      throw unavailable();
    }
    if (retryAfterSeconds == null) throw unavailable();
    if (retryAfterSeconds > 0) throw new FlightSwitchRateLimitException(retryAfterSeconds);
  }

  private static BookingException unavailable() {
    return new BookingException(503, "RATE_LIMIT_UNAVAILABLE",
        "Chưa thể kiểm tra giới hạn đổi chuyến. Vui lòng thử lại sau.");
  }
}
