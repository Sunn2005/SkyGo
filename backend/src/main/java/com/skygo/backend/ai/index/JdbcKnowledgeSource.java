package com.skygo.backend.ai.index;

import com.skygo.backend.ai.config.AiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit SELECT allowlists: no contact, passenger identity, credential or payment secret fields. */
public final class JdbcKnowledgeSource implements KnowledgeSource {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate readTransaction;
  private final AiProperties ai;
  private final AiIndexProperties index;

  public JdbcKnowledgeSource(JdbcTemplate jdbc, TransactionTemplate readTransaction,
      AiProperties ai, AiIndexProperties index) {
    this.jdbc = jdbc;
    this.readTransaction = readTransaction;
    this.ai = ai;
    this.index = index;
  }

  @Override
  public List<Document> snapshot(Instant now) {
    // SQL finishes and releases its transaction before any Gemini network calls.
    return readTransaction.execute(status -> readSnapshot(now));
  }

  private List<Document> readSnapshot(Instant now) {
    var documents = new ArrayList<Document>();
    read(documents, "SELECT code,name,city,country_code,country_name,region_name,time_zone"
        + " FROM airport WHERE is_active=1 ORDER BY code", (rs, row) ->
        document("airport", rs.getString("code"), "Sân bay " + rs.getString("code"),
            "Sân bay %s (%s), thành phố %s, quốc gia %s (%s). Nhóm hiển thị: %s. Múi giờ: %s."
                .formatted(rs.getString("name"), rs.getString("code"), rs.getString("city"),
                    rs.getString("country_name"), rs.getString("country_code"),
                    rs.getString("region_name"), rs.getString("time_zone")),
            Map.of("airport_code", rs.getString("code"), "country_code", rs.getString("country_code")),
            null, now));

    read(documents, """
        SELECT r.id,r.air_distance_km,o.code AS origin_code,o.city AS origin_city,
          d.code AS destination_code,d.city AS destination_city
        FROM air_route r JOIN airport o ON o.id=r.origin_airport_id
        JOIN airport d ON d.id=r.destination_airport_id
        WHERE r.is_active=1 AND o.is_active=1 AND d.is_active=1 ORDER BY r.id
        """, (rs, row) -> document("route", rs.getString("id"), route(rs),
            "Tuyến bay SkyGo: %s. Khoảng cách cấu hình: %s km. Có tuyến không có nghĩa là luôn có chuyến đang bán."
                .formatted(route(rs), rs.getString("air_distance_km")), routeMetadata(rs), null, now));

    read(documents, """
        SELECT f.public_id,f.flight_number,f.departure_at_utc,f.arrival_at_utc,
          o.code AS origin_code,o.city AS origin_city,o.time_zone AS origin_time_zone,
          d.code AS destination_code,d.city AS destination_city,d.time_zone AS destination_time_zone,
          ff.id AS fare_id,ff.cabin_class,ff.adult_base_fare,ff.currency,ff.available_seats,p.vat_rate
        FROM flight f JOIN air_route r ON r.id=f.route_id
        JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id
        JOIN flight_fare ff ON ff.flight_id=f.id JOIN pricing_policy p ON p.id=ff.pricing_policy_id
        WHERE r.is_active=1 AND o.is_active=1 AND d.is_active=1 AND f.sale_status='ON_SALE'
          AND ff.is_on_sale=1 AND ff.available_seats>0 AND f.departure_at_utc>=?
          AND f.departure_at_utc<? AND COALESCE(f.booking_closes_at_utc,f.departure_at_utc)>?
        ORDER BY f.departure_at_utc,f.id,ff.id
        """, (rs, row) -> {
          long price = adultPriceIncludingVat(rs.getBigDecimal("adult_base_fare"), rs.getBigDecimal("vat_rate"));
          var metadata = new java.util.HashMap<>(routeMetadata(rs));
          metadata.put("flight_id", rs.getString("public_id"));
          metadata.put("cabin_class", rs.getString("cabin_class"));
          metadata.put("departure_epoch", instant(rs, "departure_at_utc").getEpochSecond());
          metadata.put("price_vnd", price);
          metadata.put("available_seats", rs.getLong("available_seats"));
          return document("flight_fare", rs.getString("fare_id"),
              rs.getString("flight_number") + " " + route(rs) + " " + rs.getString("cabin_class"),
              """
              Chuyến bay SkyGo %s: %s. Hạng vé %s (%s).
              Khởi hành %s; đến %s (giờ địa phương và độ lệch UTC).
              Giá tham khảo tại lần lập chỉ mục: %d %s cho 1 người lớn, 1 chiều, đã gồm VAT; chưa gồm hành lý mua thêm.
              Số ghế tại lần lập chỉ mục: %d. Phải kiểm tra lại giá, ghế và hạn bán trong MariaDB trước khi xác nhận.
              """.formatted(rs.getString("flight_number"), route(rs), cabinName(rs.getString("cabin_class")),
                  rs.getString("cabin_class"), localTime(rs, "departure_at_utc", "origin_time_zone"),
                  localTime(rs, "arrival_at_utc", "destination_time_zone"), price,
                  rs.getString("currency"), rs.getLong("available_seats")), metadata, null, now);
        }, utc(now), utc(now.plus(index.flightDays(), ChronoUnit.DAYS)), utc(now));

    read(documents, """
        SELECT bp.id,bp.passenger_type,bp.carry_on_max_kg,bp.checked_pieces,
          bp.included_meals_per_leg,bp.seat_type,bp.stroller_pieces,bp.stroller_max_kg,
          cb.piece_max_kg,cb.max_linear_dimensions_cm,cb.version,cb.max_extra_pieces,
          cb.extra_piece_price_excl_vat,cb.currency,ff.cabin_class,
          f.public_id,f.flight_number,o.code AS origin_code,o.city AS origin_city,
          d.code AS destination_code,d.city AS destination_city
        FROM fare_benefit_policy bp JOIN checked_baggage_policy cb ON cb.id=bp.checked_baggage_policy_id
        JOIN flight_fare ff ON ff.id=bp.flight_fare_id JOIN flight f ON f.id=ff.flight_id
        JOIN air_route r ON r.id=f.route_id JOIN airport o ON o.id=r.origin_airport_id
        JOIN airport d ON d.id=r.destination_airport_id
        WHERE r.is_active=1 AND o.is_active=1 AND d.is_active=1 AND f.sale_status='ON_SALE'
          AND ff.is_on_sale=1 AND ff.available_seats>0 AND f.departure_at_utc>=?
          AND f.departure_at_utc<? AND COALESCE(f.booking_closes_at_utc,f.departure_at_utc)>?
        ORDER BY bp.id
        """, (rs, row) -> document("fare_benefit", rs.getString("id"),
            "Quyền lợi " + rs.getString("flight_number") + " " + rs.getString("cabin_class")
                + " " + rs.getString("passenger_type"),
            """
            Quyền lợi vé chuyến %s, %s, hạng %s (%s), hành khách %s (%s).
            Xách tay: tối đa %d kg. Ký gửi bao gồm: %d kiện, mỗi kiện tối đa %d kg và tổng 3 chiều %d cm.
            Suất ăn mỗi chặng: %d. Loại ghế: %s. Xe đẩy: %d kiện, tối đa %d kg mỗi kiện.
            Hành lý mua thêm theo chính sách phiên bản %d: tối đa %d kiện, %d %s mỗi kiện CHƯA VAT.
            Quy định chỉ áp dụng cho đúng chuyến, hạng vé và loại hành khách này.
            """.formatted(rs.getString("flight_number"), route(rs), cabinName(rs.getString("cabin_class")),
                rs.getString("cabin_class"), passengerName(rs.getString("passenger_type")),
                rs.getString("passenger_type"), rs.getInt("carry_on_max_kg"), rs.getInt("checked_pieces"),
                rs.getInt("piece_max_kg"), rs.getInt("max_linear_dimensions_cm"),
                rs.getInt("included_meals_per_leg"), rs.getString("seat_type") == null ? "không có ghế riêng" : rs.getString("seat_type"),
                rs.getInt("stroller_pieces"), rs.getInt("stroller_max_kg"), rs.getInt("version"),
                rs.getInt("max_extra_pieces"), rs.getLong("extra_piece_price_excl_vat"), rs.getString("currency")),
            Map.of("flight_id", rs.getString("public_id"), "cabin_class", rs.getString("cabin_class"),
                "passenger_type", rs.getString("passenger_type")), null, now),
        utc(now), utc(now.plus(index.flightDays(), ChronoUnit.DAYS)), utc(now));

    read(documents, """
        SELECT id,route_scope,passenger_type,multiplier FROM passenger_fare_rule
        WHERE is_active=1 ORDER BY id
        """, (rs, row) -> document("passenger_fare_rule", rs.getString("id"),
            "Quy tắc giá " + rs.getString("route_scope") + " " + rs.getString("passenger_type"),
            "Giá cơ bản cho %s (%s), phạm vi tuyến %s: giá cơ bản người lớn nhân %s. Đây là hệ số trước VAT, không phải giá vé cuối cùng."
                .formatted(passengerName(rs.getString("passenger_type")), rs.getString("passenger_type"),
                    rs.getString("route_scope"), rs.getBigDecimal("multiplier").toPlainString()),
            Map.of("route_scope", rs.getString("route_scope"), "passenger_type", rs.getString("passenger_type")), null, now));

    read(documents, """
        SELECT id,version,piece_max_kg,max_linear_dimensions_cm,max_extra_pieces,
          extra_piece_price_excl_vat,currency,effective_from_utc,effective_to_utc
        FROM checked_baggage_policy WHERE effective_from_utc<=?
          AND (effective_to_utc IS NULL OR effective_to_utc>?) ORDER BY version
        """, (rs, row) -> document("baggage_policy", rs.getString("id"),
            "Chính sách hành lý ký gửi phiên bản " + rs.getString("version"),
            "Ký gửi: mỗi kiện tối đa %d kg; tổng 3 chiều tối đa %d cm. Được mua thêm tối đa %d kiện theo chính sách phiên bản %d. Giá một kiện mua thêm: %d %s CHƯA VAT. Hiệu lực từ %s UTC; đến %s."
                .formatted(rs.getInt("piece_max_kg"), rs.getInt("max_linear_dimensions_cm"),
                    rs.getInt("max_extra_pieces"), rs.getInt("version"), rs.getLong("extra_piece_price_excl_vat"),
                    rs.getString("currency"), instant(rs, "effective_from_utc"),
                    rs.getObject("effective_to_utc") == null ? "khi có chính sách thay thế" : instant(rs, "effective_to_utc") + " UTC"),
            Map.of("policy_version", rs.getInt("version")), null, now), utc(now), utc(now));

    // One minimal summary per leg, grouped by booking_id at retrieval time. No passenger/contact joins.
    read(documents, """
        SELECT b.public_id,b.user_id,b.created_at_utc,b.booking_status,b.total_amount,b.currency,
          b.adults,b.children,b.infants,p.status AS payment_status,
          bl.direction,bl.flight_number,bl.cabin_class,bl.origin_code,bl.origin_city,
          bl.destination_code,bl.destination_city,bl.departure_at_utc,bl.origin_time_zone
        FROM booking b JOIN booking_leg bl ON bl.booking_id=b.id
        LEFT JOIN payment p ON p.booking_id=b.id
        WHERE b.created_at_utc>=? AND b.created_at_utc<=? ORDER BY b.id,bl.id
        """, (rs, row) -> document("booking_summary", rs.getString("public_id") + ":" + rs.getString("direction"),
            "Tóm tắt đặt vé " + route(rs),
            """
            Đơn đặt vé được tạo lúc %s UTC. Chặng %s: chuyến %s, %s, khởi hành %s, hạng %s (%s).
            Toàn đơn có %d người lớn, %d trẻ em, %d em bé; tổng tiền TOÀN ĐƠN (không phải riêng chặng): %d %s.
            Trạng thái đơn tại lần lập chỉ mục: %s; thanh toán: %s. Phải kiểm tra lại trạng thái trực tiếp trước khi trả lời.
            """.formatted(instant(rs, "created_at_utc"), rs.getString("direction"), rs.getString("flight_number"),
                route(rs), localTime(rs, "departure_at_utc", "origin_time_zone"),
                cabinName(rs.getString("cabin_class")), rs.getString("cabin_class"),
                rs.getInt("adults"), rs.getInt("children"), rs.getInt("infants"),
                rs.getLong("total_amount"), rs.getString("currency"), rs.getString("booking_status"),
                rs.getString("payment_status") == null ? "chưa có" : rs.getString("payment_status")),
            Map.of("booking_id", rs.getString("public_id"), "direction", rs.getString("direction"),
                "created_epoch", instant(rs, "created_at_utc").getEpochSecond()), rs.getLong("user_id"), now),
        utc(now.minus(index.bookingDays(), ChronoUnit.DAYS)), utc(now));
    return List.copyOf(documents);
  }

  private void read(List<Document> target, String sql, RowMapper<Document> mapper, Object... arguments) {
    int remaining = index.maxDocuments() - target.size();
    var args = new ArrayList<>(List.of(arguments));
    args.add(remaining + 1);
    var rows = jdbc.query(sql + " LIMIT ?", mapper, args.toArray());
    if (rows.size() > remaining)
      throw new IllegalStateException("AI snapshot exceeds max-documents; increase the limit or narrow its time windows.");
    target.addAll(rows);
  }

  private Document document(String type, String key, String title, String text,
      Map<String, Object> attributes, Long ownerId, Instant now) {
    return KnowledgeDocuments.create(ai, type, key, title, text.strip(), attributes, ownerId, now);
  }

  private static Map<String, Object> routeMetadata(ResultSet rs) throws SQLException {
    return Map.of("origin_code", rs.getString("origin_code"), "destination_code", rs.getString("destination_code"));
  }

  private static String route(ResultSet rs) throws SQLException {
    return "%s (%s) → %s (%s)".formatted(rs.getString("origin_city"), rs.getString("origin_code"),
        rs.getString("destination_city"), rs.getString("destination_code"));
  }

  static long adultPriceIncludingVat(BigDecimal base, BigDecimal vat) {
    return base.multiply(BigDecimal.ONE.add(vat)).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static String cabinName(String cabin) {
    return "BUSINESS".equals(cabin) ? "thương gia" : "phổ thông";
  }

  private static String passengerName(String type) {
    return switch (type) { case "CHILD" -> "trẻ em"; case "INFANT" -> "em bé"; default -> "người lớn"; };
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
  }

  private static String localTime(ResultSet rs, String column, String zoneColumn) throws SQLException {
    return instant(rs, column).atZone(ZoneId.of(rs.getString(zoneColumn))).toOffsetDateTime().toString();
  }

  private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
