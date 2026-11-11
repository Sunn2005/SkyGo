package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.chat.AiToolInputs.*;
import com.skygo.backend.ai.chat.contract.AiChatResponse.*;
import com.skygo.backend.booking.contract.BookingListResponse.BookingSummary;
import com.skygo.backend.booking.contract.BookingListResponse.Leg;
import com.skygo.backend.booking.contract.PassengerCountsRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, parameterized, read-only projections. No arbitrary SQL or booking mutation tools. */
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AiReadRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public AiReadRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public List<Airport> airports() {
    var catalog = jdbc.query("""
        SELECT code,name,city,country_name,time_zone FROM airport
        WHERE is_active=1 ORDER BY code LIMIT 501
        """, Map.of(), (rs, row) -> new Airport(rs.getString("code"), rs.getString("name"),
            rs.getString("city"), rs.getString("country_name"), rs.getString("time_zone")));
    if (catalog.size() > 500) throw new AiChatException(503, "AI_CATALOG_TOO_LARGE", "Danh mục sân bay cần được thu hẹp trước khi tra cứu AI.");
    return catalog;
  }

  public FlightResult flights(FlightQuery query) {
    var catalog = airports();
    Airport origin = resolve(query.origin(), "origin", catalog);
    Airport destination = resolve(query.destination(), "destination", catalog);
    if (origin.code().equals(destination.code()))
      throw new AiClarification("destination", "Điểm đến phải khác điểm khởi hành.");
    Instant now = clock.instant();
    var zone = ZoneId.of(origin.timeZone());
    if (query.departureDate().isBefore(now.atZone(zone).toLocalDate())
        || query.departureDate().isAfter(now.atZone(zone).toLocalDate().plusDays(365)))
      throw new AiClarification("departureDate", "Vui lòng chọn ngày bay từ hôm nay đến 365 ngày tới theo giờ sân bay đi.");
    if (query.adults() + query.children() > 9 || query.infants() > query.adults())
      throw new AiClarification("passengerCounts", "Mỗi lượt tra cứu tối đa 9 người có ghế; số em bé không vượt số người lớn.");

    // Compute each passenger's base fare with HALF_UP, then VAT once on the party subtotal,
    // as in BookingPricingService. Rank the ENTIRE eligible set before LIMIT.
    String order = switch (query.sort()) {
      case PRICE_ASC -> "party_total,departure_at_utc,public_id,cabin_class";
      case DEPARTURE_ASC -> "departure_at_utc,party_total,public_id,cabin_class";
      case DURATION_ASC -> "TIMESTAMPDIFF(SECOND,departure_at_utc,arrival_at_utc),party_total,public_id,cabin_class";
    };
    String sql = """
        WITH candidates AS (
          SELECT f.public_id,f.flight_number,f.departure_at_utc,f.arrival_at_utc,
            ff.cabin_class,ff.available_seats,p.vat_rate,
            ROUND(ff.adult_base_fare*(1+p.vat_rate),0) AS adult_total,
            ff.adult_base_fare*:adults
              +CASE WHEN :children=0 THEN 0 ELSE ROUND(ff.adult_base_fare*child.multiplier,0)*:children END
              +CASE WHEN :infants=0 THEN 0 ELSE ROUND(ff.adult_base_fare*infant.multiplier,0)*:infants END AS base_total,
            COUNT(*) OVER() AS total_fares
          FROM flight f JOIN flight_fare ff ON ff.flight_id=f.id
          JOIN air_route r ON r.id=f.route_id
          JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id
          JOIN pricing_policy p ON p.id=ff.pricing_policy_id
          LEFT JOIN passenger_fare_rule child ON child.passenger_type='CHILD' AND child.is_active=1
            AND child.route_scope=CASE WHEN o.country_code<>d.country_code THEN 'INTERNATIONAL'
              WHEN o.country_code='VN' THEN 'VN_DOMESTIC' ELSE 'OTHER_DOMESTIC' END
          LEFT JOIN passenger_fare_rule infant ON infant.passenger_type='INFANT' AND infant.is_active=1
            AND infant.route_scope=CASE WHEN o.country_code<>d.country_code THEN 'INTERNATIONAL'
              WHEN o.country_code='VN' THEN 'VN_DOMESTIC' ELSE 'OTHER_DOMESTIC' END
          WHERE o.code=:origin AND d.code=:destination
            AND o.is_active=1 AND d.is_active=1 AND r.is_active=1
            AND f.sale_status='ON_SALE' AND ff.is_on_sale=1 AND ff.available_seats>=:seats
            AND f.departure_at_utc>=:dayStart AND f.departure_at_utc<:dayEnd
            AND f.departure_at_utc>:now AND COALESCE(f.booking_closes_at_utc,f.departure_at_utc)>:now
            AND (:cabin='ANY' OR ff.cabin_class=:cabin)
            AND (:children=0 OR child.id IS NOT NULL) AND (:infants=0 OR infant.id IS NOT NULL)
            AND ff.currency='VND' AND p.vat_rate=0.10 AND ff.priced_distance_km=r.air_distance_km
            AND ff.adult_base_fare=ROUND((p.economy_fixed_vnd+p.economy_per_km_vnd*r.air_distance_km)
              *CASE WHEN ff.cabin_class='ECONOMY' THEN 1 ELSE p.business_multiplier END,0)
            AND NOT EXISTS (SELECT 1 FROM flight_fare sibling WHERE sibling.flight_id=f.id
              AND (sibling.pricing_policy_id<>p.id OR sibling.priced_distance_km<>r.air_distance_km
                OR sibling.adult_base_fare<>ROUND((p.economy_fixed_vnd+p.economy_per_km_vnd*r.air_distance_km)
                  *CASE WHEN sibling.cabin_class='ECONOMY' THEN 1 ELSE p.business_multiplier END,0)))
        ), priced AS (
          SELECT candidates.*,base_total+ROUND(base_total*vat_rate,0) AS party_total FROM candidates
        ) SELECT * FROM priced ORDER BY
        """ + order + " LIMIT :limit";
    var params = new MapSqlParameterSource().addValue("origin", origin.code()).addValue("destination", destination.code())
        .addValue("dayStart", utc(query.departureDate().atStartOfDay(zone).toInstant()))
        .addValue("dayEnd", utc(query.departureDate().plusDays(1).atStartOfDay(zone).toInstant()))
        .addValue("now", utc(now)).addValue("adults", query.adults()).addValue("children", query.children())
        .addValue("infants", query.infants()).addValue("seats", query.adults() + query.children())
        .addValue("cabin", query.cabin().name()).addValue("limit", query.limit());
    record Row(long total, FlightOption option) {}
    var rows = jdbc.query(sql, params, (rs, row) -> {
      Instant departure = instant(rs, "departure_at_utc");
      Instant arrival = instant(rs, "arrival_at_utc");
      return new Row(rs.getLong("total_fares"), new FlightOption(rs.getString("public_id"),
          rs.getString("flight_number"), rs.getString("cabin_class"), departure.atZone(zone).toOffsetDateTime(),
          arrival.atZone(ZoneId.of(destination.timeZone())).toOffsetDateTime(), Duration.between(departure, arrival).toMinutes(),
          rs.getLong("available_seats"), rs.getLong("adult_total"), rs.getLong("party_total"), "VND"));
    });
    long total = rows.isEmpty() ? 0 : rows.getFirst().total();
    return new FlightResult(origin, destination, query.departureDate(),
        new PassengerCounts(query.adults(), query.children(), query.infants()), query.sort().name(), total,
        total > rows.size(), true, false, "ONE_WAY_SELECTED_PASSENGERS_NO_EXTRA_BAGGAGE", now,
        rows.stream().map(Row::option).toList());
  }

  public BookingResult bookings(long authenticatedUserId, BookingQuery query) {
    if (authenticatedUserId <= 0) throw new IllegalArgumentException("An authenticated owner is required.");
    Instant now = clock.instant();
    var params = new MapSqlParameterSource().addValue("owner", authenticatedUserId)
        .addValue("since", utc(now.minus(Duration.ofDays(query.days())))).addValue("now", utc(now))
        .addValue("limit", query.limit());
    String status = switch (query.status()) {
      case ALL -> "";
      case PAID -> " AND b.booking_status='PAID' AND p.status='SUCCEEDED'";
      case PENDING -> " AND b.booking_status IN ('PENDING_PAYMENT','AWAITING_REHOLD')"
          + " AND p.status<>'RECONCILIATION_REQUIRED'";
    };
    record Row(long id, BookingSummary summary) {}
    var rows = jdbc.query("""
        SELECT b.id,b.public_id,b.booking_code,b.booking_status,p.status AS payment_status,
          b.created_at_utc,b.trip_type,b.adults,b.children,b.infants,b.total_amount,b.currency
        FROM booking b LEFT JOIN payment p ON p.booking_id=b.id
        WHERE b.user_id=:owner AND b.created_at_utc>=:since AND b.created_at_utc<=:now
        """ + status + " ORDER BY b.created_at_utc DESC,b.id DESC LIMIT :limit", params, (rs, row) ->
        new Row(rs.getLong("id"), new BookingSummary(rs.getString("public_id"), rs.getString("booking_code"),
            rs.getString("booking_status"), rs.getString("payment_status"), instant(rs, "created_at_utc"),
            rs.getString("trip_type"), new PassengerCountsRequest(rs.getInt("adults"), rs.getInt("children"),
                rs.getInt("infants")), rs.getLong("total_amount"), rs.getString("currency"), List.of())));
    if (rows.isEmpty()) return new BookingResult(query.days(), query.limit(), query.status().name(), now, List.of());
    params.addValue("ids", rows.stream().map(Row::id).toList());
    Map<Long, List<Leg>> legs = new HashMap<>();
    jdbc.query("""
        SELECT bl.booking_id,bl.direction,bl.origin_code,bl.origin_city,bl.destination_code,
          bl.destination_city,bl.departure_at_utc,bl.origin_time_zone,bl.cabin_class
        FROM booking_leg bl JOIN booking b ON b.id=bl.booking_id
        WHERE b.user_id=:owner AND bl.booking_id IN (:ids) ORDER BY bl.booking_id,bl.id
        """, params, rs -> {
      while (rs.next()) legs.computeIfAbsent(rs.getLong("booking_id"), ignored -> new ArrayList<>()).add(
          new Leg(rs.getString("direction"),
              new com.skygo.backend.booking.contract.BookingListResponse.Airport(rs.getString("origin_code"), rs.getString("origin_city")),
              new com.skygo.backend.booking.contract.BookingListResponse.Airport(rs.getString("destination_code"), rs.getString("destination_city")),
              instant(rs, "departure_at_utc").atZone(ZoneId.of(rs.getString("origin_time_zone"))).toOffsetDateTime(),
              rs.getString("cabin_class")));
      return null;
    });
    var summaries = rows.stream().map(row -> {
      var b = row.summary();
      return new BookingSummary(b.bookingId(), b.bookingCode(), b.bookingStatus(), b.paymentStatus(),
          b.createdAt(), b.tripType(), b.passengerCounts(), b.totalAmount(), b.currency(),
          List.copyOf(legs.getOrDefault(row.id(), List.of())));
    }).toList();
    return new BookingResult(query.days(), query.limit(), query.status().name(), now, summaries);
  }

  static List<Airport> exactAirports(String value, List<Airport> airports) {
    String name = normalizePlace(value);
    String rawKey = normalize(value);
    var exact = airports.stream().filter(a -> normalize(a.code()).equals(rawKey)).toList();
    if (!exact.isEmpty()) return exact;
    if (rawKey.startsWith("sanbay"))
      exact = airports.stream().filter(a -> normalize(a.code()).equals(name)).toList();
    if (!exact.isEmpty()) return exact;
    String key = cityAlias(name);
    // Aliases identify a city, not a hard-coded airport. Multiple active airports
    // serving that city must still produce a choice instead of silently using SGN.
    exact = key.isEmpty() ? List.of() : airports.stream().filter(a ->
        cityAlias(normalizePlace(a.city())).equals(key) || cityAlias(normalizePlace(a.name())).equals(key)).toList();
    return exact;
  }

  static boolean sameCity(Airport first, Airport second) {
    String city = cityAlias(normalizePlace(first.city()));
    return !city.isEmpty() && city.equals(cityAlias(normalizePlace(second.city())))
        && first.country().equalsIgnoreCase(second.country());
  }

  static Airport resolve(String value, String field, List<Airport> airports) {
    var exact = exactAirports(value, airports);
    String key = cityAlias(normalizePlace(value));
    if (exact.isEmpty() && key.length() >= 3) {
      exact = airports.stream().filter(a -> cityAlias(normalizePlace(a.name())).contains(key)
          || cityAlias(normalizePlace(a.city())).contains(key)).toList();
    }
    if (exact.size() == 1) return exact.getFirst();
    String place = "origin".equals(field) ? "điểm khởi hành" : "điểm đến";
    throw new AiClarification(field, exact.isEmpty() ? "Chưa tìm thấy sân bay phù hợp với " + place
        + ". Bạn kiểm tra lại tên thành phố hoặc sân bay giúp mình nhé."
        : "Có nhiều sân bay phù hợp. Vui lòng chọn một sân bay.", exact.stream().limit(10).toList());
  }

  private static String cityAlias(String key) {
    return switch (key) {
      case "saigon", "hcm", "hcmc", "tphcm", "hochiminh", "tphochiminh", "hochiminhcity" -> "hochiminh";
      default -> key;
    };
  }

  private static String normalizePlace(String value) {
    if (value == null) return "";
    String words = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT).replace('đ', 'd').replaceAll("[^a-z0-9]+", " ").strip();
    // Strip presentation labels at word boundaries; leave the actual place name.
    words = words.replaceFirst("^san bay(?: quoc te| quoc noi| noi dia)?(?: +|$)", "")
        .replaceFirst("^(?:thanh pho|tp)(?: +|$)", "")
        .replaceFirst("(?: +|^)(?:international +)?airport$", "")
        .replaceFirst("(?: +|^)city$", "");
    return words.replace(" ", "");
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT).replace('đ', 'd').replaceAll("[^a-z0-9]", "");
  }
  private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
  }
}
