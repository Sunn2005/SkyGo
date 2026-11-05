package com.skygo.backend.auth.repository;

import com.skygo.backend.auth.model.LoginGuard;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginGuardRepository {
  private final JdbcTemplate jdbc;

  public LoginGuardRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public LoginGuard lock(String phone) {
    jdbc.update(
        "INSERT INTO login_guard(phone_normalized) VALUES(?) ON DUPLICATE KEY UPDATE"
            + " phone_normalized=VALUES(phone_normalized)",
        phone);
    return jdbc.queryForObject(
        "SELECT * FROM login_guard WHERE phone_normalized=? FOR UPDATE",
        (r, n) -> {
          var times = new ArrayList<Instant>();
          for (int i = 1; i <= 5; i++) {
            var t = r.getObject("failure_at_" + i + "_utc", java.time.LocalDateTime.class);
            if (t != null) times.add(t.toInstant(java.time.ZoneOffset.UTC));
          }
          var locked = r.getObject("locked_until_utc", java.time.LocalDateTime.class);
          return new LoginGuard(
              times, locked == null ? null : locked.toInstant(java.time.ZoneOffset.UTC));
        },
        phone);
  }

  public void save(String phone, List<Instant> failures, Instant locked) {
    Object[] args = new Object[8];
    args[0] = failures.size();
    for (int i = 0; i < 5; i++)
      args[i + 1] =
          i < failures.size()
              ? java.time.LocalDateTime.ofInstant(failures.get(i), java.time.ZoneOffset.UTC)
              : null;
    args[6] =
        locked == null ? null : java.time.LocalDateTime.ofInstant(locked, java.time.ZoneOffset.UTC);
    args[7] = phone;
    jdbc.update(
        "UPDATE login_guard SET"
            + " failure_count=?,failure_at_1_utc=?,failure_at_2_utc=?,failure_at_3_utc=?,failure_at_4_utc=?,failure_at_5_utc=?,locked_until_utc=?,updated_at_utc=UTC_TIMESTAMP(6)"
            + " WHERE phone_normalized=?",
        args);
  }
}
