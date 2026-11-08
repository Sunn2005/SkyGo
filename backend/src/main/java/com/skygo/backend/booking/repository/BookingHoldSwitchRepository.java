package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.exception.BookingException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.utc;

@Repository
public class BookingHoldSwitchRepository {
  private final JdbcTemplate jdbc;
  public BookingHoldSwitchRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record Switch(long id,String publicId,long userId,long bookingId,long sourceHoldId,
      long expectedVersion,long attemptId,String key,byte[] hash,String selection,Long targetHoldId,String status,String errorCode) {
    public boolean pending() { return Set.of("VERIFYING_PAYMENT","QR_CLOSED").contains(status); }
  }
  private List<Switch> query(String where,Object... args) {
    return jdbc.query("SELECT * FROM booking_hold_switch "+where,(rs,n)->new Switch(rs.getLong("id"),
        rs.getString("public_id"),rs.getLong("user_id"),rs.getLong("booking_id"),rs.getLong("source_hold_id"),
        rs.getLong("expected_hold_version"),rs.getLong("payment_attempt_id"),rs.getString("idempotency_key"),
        rs.getBytes("request_hash"),rs.getString("requested_selection"),rs.getObject("target_hold_id",Long.class),
        rs.getString("status"),rs.getString("error_code")),args);
  }
  public Optional<Switch> byKey(long user,String key) {
    return query("WHERE user_id=? AND idempotency_key=? FOR UPDATE",user,key).stream().findFirst();
  }
  public Switch byId(long id) { return query("WHERE id=? FOR UPDATE",id).getFirst(); }
  public void assertIdle(long user) {
    if (jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_hold_switch WHERE active_user_id=?)",Integer.class,user)!=0)
      throw new BookingException(409,"HOLD_SWITCH_IN_PROGRESS","Đang chuyển lượt giữ chỗ. Vui lòng kiểm tra lại thao tác hiện tại.");
  }
  public Switch create(long user,long booking,long hold,long version,long attempt,String key,byte[] hash,String json,Instant now) {
    jdbc.update("""
        INSERT INTO booking_hold_switch(public_id,user_id,booking_id,source_hold_id,expected_hold_version,
          payment_attempt_id,idempotency_key,request_hash,requested_selection,status,created_at_utc,updated_at_utc)
        VALUES(?,?,?,?,?,?,?,?,?,'VERIFYING_PAYMENT',?,?)
        """,UUID.randomUUID().toString(),user,booking,hold,version,attempt,key,hash,json,utc(now),utc(now));
    return byKey(user,key).orElseThrow();
  }
  public void state(long id,String state,String error,Long target,Instant now) {
    jdbc.update("""
        UPDATE booking_hold_switch SET status=?,error_code=?,target_hold_id=?,updated_at_utc=?,
          completed_at_utc=CASE WHEN ? IN ('COMPLETED','FAILED','RECONCILIATION_REQUIRED') THEN ? ELSE NULL END
        WHERE id=?
        """,state,error,target,utc(now),state,utc(now),id);
  }
  public record Pending(long id,long userId,String bookingId,String key,String selection) {}
  public List<Pending> pending(int limit) {
    return jdbc.query("""
        SELECT s.id,s.user_id,b.public_id,s.idempotency_key,s.requested_selection
        FROM booking_hold_switch s JOIN booking b ON b.id=s.booking_id
        WHERE s.active_user_id IS NOT NULL ORDER BY s.updated_at_utc,s.id LIMIT ?
        """,(rs,n)->new Pending(rs.getLong("id"),rs.getLong("user_id"),rs.getString("public_id"),
        rs.getString("idempotency_key"),rs.getString("requested_selection")),limit);
  }
}
