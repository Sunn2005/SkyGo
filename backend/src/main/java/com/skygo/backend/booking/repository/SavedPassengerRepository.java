package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.utc;

@Repository
public class SavedPassengerRepository {
  private final JdbcTemplate jdbc;
  public SavedPassengerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void lockUser(long user) {
    jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, user);
  }
  public long count(long user) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM saved_passenger WHERE user_id=?", Long.class, user);
  }
  public List<SavedPassengerResponse.Summary> list(long user) {
    return jdbc.query("""
        SELECT public_id,given_names,surname,date_of_birth,document_type,document_number_last4
        FROM saved_passenger WHERE user_id=? ORDER BY created_at_utc DESC,id DESC
        """, (rs, n) -> new SavedPassengerResponse.Summary(rs.getString("public_id"),
        rs.getString("given_names"), rs.getString("surname"), rs.getObject("date_of_birth", LocalDate.class),
        DocumentType.valueOf(rs.getString("document_type")), rs.getString("document_number_last4")), user);
  }
  public record Stored(String publicId, Title title, String givenNames, String surname,
      LocalDate dateOfBirth, Gender gender, DocumentType documentType, byte[] ciphertext,
      String issuingCountry, String nationality, LocalDate expiresOn) {
    @Override public String toString() { return "Stored[redacted]"; }
  }
  public Optional<Stored> find(long user, String id) {
    return jdbc.query("SELECT * FROM saved_passenger WHERE user_id=? AND public_id=?", (rs, n) ->
        new Stored(rs.getString("public_id"), rs.getString("title") == null ? null : Title.valueOf(rs.getString("title")),
            rs.getString("given_names"), rs.getString("surname"), rs.getObject("date_of_birth", LocalDate.class),
            Gender.valueOf(rs.getString("gender")), DocumentType.valueOf(rs.getString("document_type")),
            rs.getBytes("document_number_ciphertext"), rs.getString("issuing_country_code"),
            rs.getString("nationality_country_code"), rs.getObject("document_expires_on", LocalDate.class)),
        user, id).stream().findFirst();
  }
  public void create(long user, String id, SavedPassengerRequest p, byte[] ciphertext, Instant now) {
    var d = p.document();
    jdbc.update("""
        INSERT INTO saved_passenger(public_id,user_id,title,given_names,surname,date_of_birth,gender,
          document_type,document_number_ciphertext,document_number_last4,issuing_country_code,
          nationality_country_code,document_expires_on,created_at_utc,updated_at_utc)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, id, user, p.title() == null ? null : p.title().name(), p.givenNames(), p.surname(), p.dateOfBirth(),
        p.gender().name(), d.documentType().name(), ciphertext, last4(d.number()), d.issuingCountryCode(),
        d.nationalityCountryCode(), d.expiresOn(), utc(now), utc(now));
  }
  public int update(long user, String id, SavedPassengerRequest p, byte[] ciphertext, Instant now) {
    var d = p.document();
    return jdbc.update("""
        UPDATE saved_passenger SET title=?,given_names=?,surname=?,date_of_birth=?,gender=?,
          document_type=?,document_number_ciphertext=?,document_number_last4=?,issuing_country_code=?,
          nationality_country_code=?,document_expires_on=?,updated_at_utc=? WHERE user_id=? AND public_id=?
        """, p.title() == null ? null : p.title().name(), p.givenNames(), p.surname(), p.dateOfBirth(),
        p.gender().name(), d.documentType().name(), ciphertext, last4(d.number()), d.issuingCountryCode(),
        d.nationalityCountryCode(), d.expiresOn(), utc(now), user, id);
  }
  public int delete(long user, String id) {
    return jdbc.update("DELETE FROM saved_passenger WHERE user_id=? AND public_id=?", user, id);
  }
  private static String last4(String number) { return number.substring(Math.max(0, number.length() - 4)); }
}
