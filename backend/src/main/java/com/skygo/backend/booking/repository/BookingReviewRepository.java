package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.exception.BookingException;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.*;

@Repository
public class BookingReviewRepository {
  private final JdbcTemplate jdbc;
  public BookingReviewRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
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
