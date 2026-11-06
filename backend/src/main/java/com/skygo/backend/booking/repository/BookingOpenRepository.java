package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.BookingOpenResponse.*;
import com.skygo.backend.booking.model.SeatHold;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;

@Repository
public class BookingOpenRepository {
  private final JdbcTemplate jdbc;
  public BookingOpenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  // Mutating callers hold domain locks; read-only callers use a repeatable-read snapshot.
  public BookingOpenResponse load(long bookingId, SeatHold hold) {
    var main = jdbc.queryForObject("""
        SELECT b.public_id,b.booking_code,b.booking_status,b.created_at_utc,b.trip_type,
          b.adults,b.children,b.infants,b.contact_email,b.contact_phone,b.fare_subtotal,
          b.extra_baggage_amount,b.vat_rate,b.vat_amount,b.total_amount,b.currency,
          p.public_id AS payment_public_id,p.method,p.status AS payment_status,
          p.amount AS payment_amount,p.currency AS payment_currency,p.transaction_code,p.paid_at_utc
        FROM booking b JOIN payment p ON p.booking_id=b.id WHERE b.id=?
        """, (rs, n) -> new BookingOpenResponse(
        rs.getString("public_id"), rs.getString("booking_code"), rs.getString("booking_status"),
        instant(rs, "created_at_utc"), rs.getString("trip_type"),
        new PassengerCountsRequest(rs.getInt("adults"), rs.getInt("children"), rs.getInt("infants")),
        new Contact(rs.getString("contact_email"), rs.getString("contact_phone")),
        hold == null ? null : hold.publicId(), hold == null ? null : hold.expiresAt(),
        new Price(rs.getLong("fare_subtotal"), rs.getLong("extra_baggage_amount"),
            rs.getBigDecimal("vat_rate"), rs.getLong("vat_amount"), rs.getLong("total_amount")),
        rs.getString("currency"),
        new Payment(rs.getString("payment_public_id"), rs.getString("method"),
            rs.getString("payment_status"), rs.getLong("payment_amount"),
            rs.getString("payment_currency"), rs.getString("transaction_code"),
            nullableInstant(rs, "paid_at_utc")), List.of(), List.of()), bookingId);
    return new BookingOpenResponse(main.bookingId(), main.bookingCode(), main.bookingStatus(),
        main.createdAt(), main.tripType(), main.passengerCounts(), main.contact(),
        main.seatHoldId(), main.holdExpiresAt(), main.price(), main.currency(), main.payment(),
        legs(bookingId), passengers(bookingId));
  }

  private List<Leg> legs(long bookingId) {
    var fares = fareLines(bookingId);
    return jdbc.query("""
        SELECT id,direction,flight_public_id,flight_number,airline_code,airline_name,cabin_class,
          origin_code,origin_name,origin_city,origin_country_code,origin_time_zone,
          destination_code,destination_name,destination_city,destination_country_code,
          destination_time_zone,departure_at_utc,arrival_at_utc,duration_minutes,route_type
        FROM booking_leg WHERE booking_id=? ORDER BY id
        """, (rs, n) -> new Leg(rs.getString("direction"), rs.getString("flight_public_id"),
        rs.getString("flight_number"),
        new Airline(rs.getString("airline_code"), rs.getString("airline_name")),
        rs.getString("cabin_class"),
        new Airport(rs.getString("origin_code"), rs.getString("origin_name"),
            rs.getString("origin_city"), rs.getString("origin_country_code")),
        new Airport(rs.getString("destination_code"), rs.getString("destination_name"),
            rs.getString("destination_city"), rs.getString("destination_country_code")),
        localTime(rs, "departure_at_utc", "origin_time_zone"),
        localTime(rs, "arrival_at_utc", "destination_time_zone"),
        rs.getInt("duration_minutes"), rs.getString("route_type"),
        fares.getOrDefault(rs.getLong("id"), List.of())),
        bookingId);
  }

  private Map<Long, List<FareLine>> fareLines(long bookingId) {
    return jdbc.query("""
        SELECT booking_leg_id,passenger_type,quantity,unit_base_fare,fare_subtotal
        FROM booking_fare_line WHERE booking_id=? ORDER BY id
        """, rs -> {
          var fares = new HashMap<Long, List<FareLine>>();
          while (rs.next()) fares.computeIfAbsent(rs.getLong("booking_leg_id"), ignored -> new ArrayList<>())
              .add(new FareLine(rs.getString("passenger_type"), rs.getInt("quantity"),
                  rs.getLong("unit_base_fare"), rs.getLong("fare_subtotal")));
          return fares;
        }, bookingId);
  }

  private List<Passenger> passengers(long bookingId) {
    var benefits = benefits(bookingId);
    return jdbc.query("""
        SELECT bp.id,bp.public_id,bp.client_passenger_id,bp.passenger_type,bp.title,
          bp.given_names,bp.surname,bp.date_of_birth,bp.gender,bp.document_type,
          bp.document_number_last4,bp.issuing_country_code,bp.nationality_country_code,
          bp.document_expires_on,t.ticket_number
        FROM booking_passenger bp
        LEFT JOIN ticket t ON t.booking_passenger_id=bp.id
        WHERE bp.booking_id=? ORDER BY bp.id
        """, (rs, n) -> {
          String last4 = rs.getString("document_number_last4");
          return new Passenger(rs.getString("public_id"), rs.getString("client_passenger_id"),
              rs.getString("passenger_type"), rs.getString("title"), rs.getString("given_names"),
              rs.getString("surname"), rs.getObject("date_of_birth", LocalDate.class),
              rs.getString("gender"),
              new Document(rs.getString("document_type"), last4 == null ? null : "****" + last4,
                  rs.getString("issuing_country_code"), rs.getString("nationality_country_code"),
                  rs.getObject("document_expires_on", LocalDate.class)),
              benefits.getOrDefault(rs.getLong("id"), List.of()), rs.getString("ticket_number"));
        }, bookingId);
  }

  private Map<Long, List<Benefit>> benefits(long bookingId) {
    return jdbc.query("""
        SELECT b.booking_passenger_id,l.direction,b.carry_on_max_kg,b.checked_pieces,b.checked_piece_max_kg,
          b.checked_piece_max_linear_cm,b.extra_checked_pieces,b.extra_unit_price_excl_vat,
          b.extra_baggage_amount,b.included_meals_per_leg,b.seat_type,b.stroller_pieces,b.stroller_max_kg
        FROM booking_passenger_benefit b JOIN booking_leg l ON l.id=b.booking_leg_id
        WHERE b.booking_id=? ORDER BY l.id
        """, rs -> {
          var benefits = new HashMap<Long, List<Benefit>>();
          while (rs.next()) benefits.computeIfAbsent(rs.getLong("booking_passenger_id"),
              ignored -> new ArrayList<>()).add(new Benefit(rs.getString("direction"),
                  rs.getInt("carry_on_max_kg"), rs.getInt("checked_pieces"),
                  rs.getInt("checked_piece_max_kg"),
                  rs.getObject("checked_piece_max_linear_cm", Integer.class),
                  rs.getInt("extra_checked_pieces"), rs.getLong("extra_unit_price_excl_vat"),
                  rs.getLong("extra_baggage_amount"), rs.getInt("included_meals_per_leg"),
                  rs.getString("seat_type"), rs.getInt("stroller_pieces"),
                  rs.getInt("stroller_max_kg")));
          return benefits;
        }, bookingId);
  }

  private static OffsetDateTime localTime(ResultSet rs, String timestamp, String zone)
      throws SQLException {
    return instant(rs, timestamp).atZone(ZoneId.of(rs.getString(zone))).toOffsetDateTime();
  }
  private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column) == null ? null : instant(rs, column);
  }
}
