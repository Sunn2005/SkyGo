package com.skygo.backend.flight.repository;

import com.skygo.backend.flight.contract.AirportPageResponse;
import com.skygo.backend.flight.contract.AirportPageResponse.Pagination;
import com.skygo.backend.flight.contract.AirportResponse;
import com.skygo.backend.flight.contract.RegionResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AirportRepository {
  private final JdbcTemplate jdbc;

  public AirportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RegionResponse> regions() {
    return jdbc.query("""
        SELECT region_code, region_name, region_sort_order
          FROM airport WHERE is_active = 1
         GROUP BY region_code, region_name, region_sort_order
         ORDER BY region_sort_order, region_code
        """, (rs, n) -> new RegionResponse(rs.getString("region_code"),
        rs.getString("region_name"), rs.getInt("region_sort_order")));
  }

  public boolean activeOriginExists(String code) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM airport WHERE code = ? AND is_active = 1", Long.class, code) > 0;
  }

  public boolean regionExists(String code) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM airport WHERE region_code = ? AND is_active = 1", Long.class, code) > 0;
  }

  public boolean countryExists(String code) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM airport WHERE country_code = ? AND is_active = 1", Long.class, code) > 0;
  }

  public AirportPageResponse airports(String keyword, String country, String region,
      String origin, int page, int size, Instant now) {
    var where = new StringBuilder(" FROM airport a WHERE a.is_active = 1");
    var args = new ArrayList<Object>();
    if (country != null) {
      where.append(" AND a.country_code = ?");
      args.add(country);
    }
    if (region != null) {
      where.append(" AND a.region_code = ?");
      args.add(region);
    }
    if (origin != null) {
      where.append("""
           AND a.code <> ? AND EXISTS (
             SELECT 1 FROM air_route r
             JOIN airport o ON o.id = r.origin_airport_id
             JOIN flight f ON f.route_id = r.id
             WHERE r.destination_airport_id = a.id AND o.code = ? AND o.is_active = 1
               AND r.is_active = 1 AND f.sale_status = 'ON_SALE' AND f.departure_at_utc > ?)
          """);
      args.add(origin);
      args.add(origin);
      args.add(Timestamp.from(now));
    }
    if (keyword != null && !keyword.isEmpty()) {
      where.append("""
           AND (CONVERT(a.code USING utf8mb4) COLLATE utf8mb4_unicode_ci LIKE ? ESCAPE '!'
             OR REPLACE(REPLACE(a.name, 'đ', 'd'), 'Đ', 'D') COLLATE utf8mb4_unicode_ci LIKE ? ESCAPE '!'
             OR REPLACE(REPLACE(a.city, 'đ', 'd'), 'Đ', 'D') COLLATE utf8mb4_unicode_ci LIKE ? ESCAPE '!')
          """);
      // MariaDB's accent-insensitive LIKE still distinguishes Vietnamese đ from d.
      String pattern = "%" + keyword.replace('đ', 'd').replace('Đ', 'D')
          .replace("!", "!!").replace("%", "!%")
          .replace("_", "!_") + "%";
      args.add(pattern);
      args.add(pattern);
      args.add(pattern);
    }
    long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var airports = jdbc.query(
        "SELECT a.code, a.name, a.city, a.country_name, a.region_code" + where
            + " ORDER BY a.code LIMIT ? OFFSET ?",
        (rs, n) -> new AirportResponse(rs.getString("code"), rs.getString("name"),
            rs.getString("city"), rs.getString("country_name"), rs.getString("region_code")),
        args.toArray());
    return new AirportPageResponse(airports, new Pagination(page, size, total, (total + size - 1) / size));
  }
}
