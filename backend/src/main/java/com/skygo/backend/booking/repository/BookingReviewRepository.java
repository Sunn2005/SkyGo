package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.exception.BookingException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.*;

@Repository
public class BookingReviewRepository {
  private final JdbcTemplate jdbc;
  public BookingReviewRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record StoredReview(String publicId,long holdId,int schemaVersion,long version,Instant createdAt,Instant expiresAt,
      boolean used,boolean invalidated,byte[] ciphertext) {
    @Override public String toString() { return "StoredReview[redacted]"; }
  }
  public Optional<StoredReview> owned(String id,long user,long session) {
    return jdbc.query("""
        SELECT public_id,seat_hold_id,snapshot_schema_version,review_version,created_at_utc,expires_at_utc,used_at_utc,
          invalidated_at_utc,snapshot_ciphertext FROM booking_review
        WHERE public_id=? AND user_id=? AND session_id=? LOCK IN SHARE MODE
        """,(rs,n)->new StoredReview(rs.getString("public_id"),rs.getLong("seat_hold_id"),
            rs.getInt("snapshot_schema_version"),rs.getLong("review_version"),instant(rs,"created_at_utc"),instant(rs,"expires_at_utc"),
            rs.getObject("used_at_utc")!=null,rs.getObject("invalidated_at_utc")!=null,
            rs.getBytes("snapshot_ciphertext")),id,user,session).stream().findFirst();
  }
  public boolean update(String id,long user,long session,long expectedVersion,int schemaVersion,
      Instant now,byte[] ciphertext) {
    return jdbc.update("""
        UPDATE booking_review SET snapshot_ciphertext=?,snapshot_schema_version=?,
          review_version=review_version+1,updated_at_utc=?
        WHERE public_id=? AND user_id=? AND session_id=? AND review_version=?
          AND used_at_utc IS NULL AND invalidated_at_utc IS NULL
          AND snapshot_purged_at_utc IS NULL AND expires_at_utc>?
        """,ciphertext,schemaVersion,utc(now),id,user,session,expectedVersion,utc(now))==1;
  }
  public void requireSession(long user,long session,Instant now) {
    var live=jdbc.query("""
        SELECT expires_at_utc,revoked_at_utc FROM auth_session
        WHERE id=? AND user_id=? LOCK IN SHARE MODE
        """,(rs,n)->rs.getObject("revoked_at_utc")==null && instant(rs,"expires_at_utc").isAfter(now),session,user);
    if(live.isEmpty() || !live.getFirst())
      throw new BookingException(401,"UNAUTHENTICATED","Phiên đăng nhập không còn hiệu lực.");
  }
  public void save(String id,long user,long session,long hold,int version,Instant now,Instant expires,byte[] ciphertext) {
    jdbc.update("""
        INSERT INTO booking_review(public_id,user_id,session_id,seat_hold_id,snapshot_schema_version,
          snapshot_ciphertext,expires_at_utc,created_at_utc,updated_at_utc) VALUES(?,?,?,?,?,?,?,?,?)
        """,id,user,session,hold,version,ciphertext,utc(expires),utc(now),utc(now));
  }
}
