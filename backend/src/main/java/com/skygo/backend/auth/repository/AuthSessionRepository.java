package com.skygo.backend.auth.repository;

import com.skygo.backend.auth.model.AuthSession;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepository {
  private final JdbcTemplate jdbc;

  public AuthSessionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void create(long userId, byte[] hash, Instant expiry) {
    jdbc.update(
        "INSERT INTO auth_session(public_id,user_id,token_hash,expires_at_utc) VALUES(?,?,?,?)",
        UUID.randomUUID().toString(),
        userId,
        hash,
        java.time.LocalDateTime.ofInstant(expiry, java.time.ZoneOffset.UTC));
  }

  public Optional<AuthSession> find(byte[] hash) {
    return jdbc
        .query(
            "SELECT u.*,s.id AS session_id FROM auth_session s JOIN app_user u ON u.id=s.user_id"
                + " WHERE s.token_hash=? AND s.revoked_at_utc IS NULL AND"
                + " s.expires_at_utc>UTC_TIMESTAMP(6)",
            (r, n) -> new AuthSession(r.getLong("session_id"), AppUserRepository.map(r, n)),
            hash)
        .stream()
        .findFirst();
  }

  public void revoke(long id) {
    jdbc.update(
        "UPDATE auth_session SET revoked_at_utc=UTC_TIMESTAMP(6),updated_at_utc=UTC_TIMESTAMP(6)"
            + " WHERE id=? AND revoked_at_utc IS NULL",
        id);
    jdbc.update(
        "UPDATE booking_review SET invalidated_at_utc=UTC_TIMESTAMP(6) WHERE session_id=? AND"
            + " used_at_utc IS NULL AND invalidated_at_utc IS NULL",
        id);
  }
}
