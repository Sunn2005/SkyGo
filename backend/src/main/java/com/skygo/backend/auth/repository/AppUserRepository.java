package com.skygo.backend.auth.repository;

import com.skygo.backend.auth.model.AppUser;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class AppUserRepository {
  private final JdbcTemplate jdbc;

  public AppUserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static AppUser map(ResultSet r, int n) throws SQLException {
    return new AppUser(
        r.getLong("id"),
        r.getString("public_id"),
        r.getString("full_name"),
        r.getString("phone_normalized"),
        r.getString("email"),
        r.getString("password_hash"),
        r.getObject("created_at_utc", java.time.LocalDateTime.class)
            .toInstant(java.time.ZoneOffset.UTC));
  }

  public Optional<AppUser> byPhone(String phone) {
    return jdbc
        .query("SELECT * FROM app_user WHERE phone_normalized=?", AppUserRepository::map, phone)
        .stream()
        .findFirst();
  }

  public AppUser insert(String name, String phone, String email, String hash) {
    jdbc.update(
        "INSERT INTO app_user(public_id,full_name,phone_normalized,email,password_hash)"
            + " VALUES(?,?,?,?,?)",
        UUID.randomUUID().toString(),
        name,
        phone,
        email,
        hash);
    return byPhone(phone).orElseThrow();
  }
}
