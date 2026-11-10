package com.skygo.backend.flight.repository;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.flight.contract.FavoriteFlightResponse;
import com.skygo.backend.flight.contract.FlightSearchResponse.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;

@Repository
public class FavoriteFlightRepository {
  private final JdbcTemplate jdbc;
  public FavoriteFlightRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void save(long user, String publicId, Instant now) {
    var flights = jdbc.query("SELECT id,departure_at_utc FROM flight WHERE public_id=? FOR UPDATE",
        (rs,n) -> new FlightTime(rs.getLong("id"),instant(rs,"departure_at_utc")),publicId);
    if (flights.isEmpty())
      throw new BookingException(404,"FLIGHT_NOT_FOUND","Không tìm thấy chuyến bay.");
    var f = flights.getFirst();
    if (jdbc.queryForObject("SELECT COUNT(*) FROM favorite_flight WHERE user_id=? AND flight_id=?",
        Long.class,user,f.id()) > 0) return;
    if (!f.departure().isAfter(now))
      throw BookingException.invalid("flightId","Chuyến bay đã khởi hành.");
    jdbc.update("""
        INSERT INTO favorite_flight(user_id,flight_id) VALUES(?,?)
        ON DUPLICATE KEY UPDATE flight_id=VALUES(flight_id)
        """,user,f.id());
  }
  private record FlightTime(long id, Instant departure) {}

  public void delete(long user, String publicId) {
    jdbc.update("""
        DELETE saved FROM favorite_flight saved JOIN flight f ON f.id=saved.flight_id
        WHERE saved.user_id=? AND f.public_id=?
        """,user,publicId);
  }

  public FavoriteFlightResponse list(long user, int page, int size, Instant now, Airline airline) {
    var ids = jdbc.queryForList("""
        SELECT f.public_id FROM favorite_flight s JOIN flight f ON f.id=s.flight_id
        WHERE s.user_id=? ORDER BY s.created_at_utc DESC,s.flight_id DESC
        """,String.class,user);
    // Page saved flights before joining fares; retain unavailable and departed flights.
    var data = jdbc.query("""
        SELECT s.created_at_utc AS saved_at,f.public_id,f.flight_number,f.departure_at_utc,
          f.arrival_at_utc,f.sale_status,COALESCE(f.booking_closes_at_utc,f.departure_at_utc) AS closes_at,
          o.code AS origin_code,o.city AS origin_city,o.time_zone AS origin_zone,
          d.code AS destination_code,d.city AS destination_city,d.time_zone AS destination_zone,
          (r.is_active AND o.is_active AND d.is_active) AS route_active,
          fare.cabin_class,fare.adult_base_fare,fare.available_seats,fare.is_on_sale,policy.vat_rate
        FROM (SELECT * FROM favorite_flight WHERE user_id=?
          ORDER BY created_at_utc DESC,flight_id DESC LIMIT ? OFFSET ?) s
        JOIN flight f ON f.id=s.flight_id JOIN air_route r ON r.id=f.route_id
        JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id
        LEFT JOIN flight_fare fare ON fare.flight_id=f.id
        LEFT JOIN pricing_policy policy ON policy.id=fare.pricing_policy_id
        ORDER BY s.created_at_utc DESC,s.flight_id DESC,fare.cabin_class
        """, rs -> {
      var items = new LinkedHashMap<String,FavoriteFlightResponse.Item>();
      while (rs.next()) {
        String id=rs.getString("public_id");
        var departure=instant(rs,"departure_at_utc");
        var arrival=instant(rs,"arrival_at_utc");
        String state=!departure.isAfter(now)?"DEPARTED":
            !rs.getBoolean("route_active") || !rs.getString("sale_status").equals("ON_SALE")
              || !instant(rs,"closes_at").isAfter(now)?"CLOSED":"SOLD_OUT";
        var existing=items.get(id);
        var fares=new ArrayList<Fare>(existing==null?List.of():existing.flight().fares());
        if (rs.getString("cabin_class")!=null && rs.getBoolean("is_on_sale")
            && rs.getInt("available_seats")>0 && !state.equals("CLOSED") && !state.equals("DEPARTED")) {
          long amount=rs.getBigDecimal("adult_base_fare")
              .multiply(BigDecimal.ONE.add(rs.getBigDecimal("vat_rate")))
              .setScale(0,RoundingMode.HALF_UP).longValueExact();
          fares.add(new Fare(rs.getString("cabin_class"),new Price(amount,"VND",true)));
        }
        if (!fares.isEmpty()) state="AVAILABLE";
        var flight=new Flight(id,rs.getString("flight_number"),airline,
            new Airport(rs.getString("origin_code"),rs.getString("origin_city")),
            new Airport(rs.getString("destination_code"),rs.getString("destination_city")),
            departure.atZone(ZoneId.of(rs.getString("origin_zone"))).toOffsetDateTime(),
            arrival.atZone(ZoneId.of(rs.getString("destination_zone"))).toOffsetDateTime(),
            Duration.between(departure,arrival).toMinutes(),fares);
        items.put(id,new FavoriteFlightResponse.Item(instant(rs,"saved_at"),state,flight));
      }
      return List.copyOf(items.values());
    },user,size,(long)page*size);
    long total=ids.size();
    return new FavoriteFlightResponse(data,new Pagination(page,size,total,(total+size-1)/size),ids);
  }
}
