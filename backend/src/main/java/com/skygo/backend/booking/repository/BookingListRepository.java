package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.contract.BookingListResponse;
import com.skygo.backend.booking.contract.BookingListResponse.*;
import com.skygo.backend.booking.contract.PassengerCountsRequest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;

@Repository
public class BookingListRepository {
  private final JdbcTemplate jdbc;

  public BookingListRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private record Row(long id, BookingSummary summary) {}

  public BookingListResponse list(long userId, int page, int size, String status) {
    String where = switch (status) {
      case "paid" -> " AND b.booking_status='PAID' AND p.status='SUCCEEDED'";
      case "pending" -> " AND b.booking_status IN ('PENDING_PAYMENT','AWAITING_REHOLD')"
          + " AND p.status<>'RECONCILIATION_REQUIRED'";
      default -> "";
    };
    long total = jdbc.queryForObject("SELECT COUNT(*) FROM booking b"
        + " LEFT JOIN payment p ON p.booking_id=b.id WHERE b.user_id=?" + where,
        Long.class, userId);
    var rows = jdbc.query("""
        SELECT b.id,b.public_id,b.booking_code,b.booking_status,p.status AS payment_status,
          b.created_at_utc,b.trip_type,b.adults,b.children,b.infants,b.total_amount,b.currency
        FROM booking b LEFT JOIN payment p ON p.booking_id=b.id
        WHERE b.user_id=?
        """ + where + " ORDER BY b.created_at_utc DESC,b.id DESC LIMIT ? OFFSET ?",
        (rs, n) -> new Row(rs.getLong("id"), new BookingSummary(
        rs.getString("public_id"), rs.getString("booking_code"),
        rs.getString("booking_status"), rs.getString("payment_status"),
        instant(rs, "created_at_utc"), rs.getString("trip_type"),
        new PassengerCountsRequest(rs.getInt("adults"), rs.getInt("children"),
            rs.getInt("infants")), rs.getLong("total_amount"),
        rs.getString("currency"), List.of())), userId, size, (long) page * size);

    Map<Long, List<Leg>> legs = new HashMap<>();
    if (!rows.isEmpty()) {
      String placeholders = String.join(",", java.util.Collections.nCopies(rows.size(), "?"));
      Object[] ids = rows.stream().map(Row::id).toArray();
      jdbc.query("""
          SELECT booking_id,direction,origin_code,origin_city,destination_code,
            destination_city,departure_at_utc,origin_time_zone,cabin_class
          FROM booking_leg WHERE booking_id IN (
          """ + placeholders + ") ORDER BY booking_id,id", rs -> {
        while (rs.next()) {
          legs.computeIfAbsent(rs.getLong("booking_id"), ignored -> new ArrayList<>())
              .add(new Leg(rs.getString("direction"),
                  new Airport(rs.getString("origin_code"), rs.getString("origin_city")),
                  new Airport(rs.getString("destination_code"), rs.getString("destination_city")),
                  instant(rs, "departure_at_utc")
                      .atZone(ZoneId.of(rs.getString("origin_time_zone"))).toOffsetDateTime(),
                  rs.getString("cabin_class")));
        }
        return null;
      }, ids);
    }
    var items = rows.stream().map(row -> {
      var s = row.summary();
      return new BookingSummary(s.bookingId(), s.bookingCode(), s.bookingStatus(),
          s.paymentStatus(), s.createdAt(), s.tripType(), s.passengerCounts(),
          s.totalAmount(), s.currency(), legs.getOrDefault(row.id(), List.of()));
    }).toList();
    return new BookingListResponse(items,
        new Pagination(page, size, total, total / size + (total % size == 0 ? 0 : 1)));
  }
}
