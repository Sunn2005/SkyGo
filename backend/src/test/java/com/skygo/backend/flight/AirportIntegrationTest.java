package com.skygo.backend.flight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
    "spring.datasource.url=${SKYGO_TEST_DB_URL}",
    "spring.datasource.username=${SKYGO_TEST_DB_USERNAME:root}",
    "spring.datasource.password=${SKYGO_TEST_DB_PASSWORD:}"
})
@Transactional
class AirportIntegrationTest {
  @Autowired WebApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    airport("ZZA", "Điểm đi", true);
    airport("ZZB", "Đà Nẵng", true);
    airport("ZZC", "Sân bay không có chuyến", true);
    airport("ZZD", "Sân bay ngừng hoạt động", false);
  }

  @Test
  void publicCatalogUsesRegionsAndMinimalFieldsWithAccentInsensitiveSearch() throws Exception {
    mvc.perform(get("/api/v1/regions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.regionCode == 'TEST_CATALOG')].regionName")
            .value("Khu vực kiểm thử"));
    mvc.perform(get("/api/v1/airports").param("regionCode", "TEST_CATALOG")
            .param("keyword", "DA NANG"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].code").value("ZZB"))
        .andExpect(jsonPath("$.data[0].countryName").value("Quốc gia kiểm thử"))
        .andExpect(jsonPath("$.data[0].regionCode").value("TEST_CATALOG"))
        .andExpect(jsonPath("$.data[0].timeZone").doesNotExist())
        .andExpect(jsonPath("$.data[0].id").doesNotExist())
        .andExpect(jsonPath("$.pagination.totalElements").value(1));
    mvc.perform(get("/api/v1/airports").param("regionCode", "TEST_CATALOG")
            .param("keyword", "đà nẵng"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("ZZB"));
    mvc.perform(get("/api/v1/airports").param("regionCode", "TEST_CATALOG")
            .param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].code").value("ZZC"))
        .andExpect(jsonPath("$.pagination.totalElements").value(3))
        .andExpect(jsonPath("$.pagination.totalPages").value(2));
    mvc.perform(get("/api/v1/airports").param("keyword", "%"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void destinationsNeedAnActiveDirectedRouteAndFutureOnSaleFlight() throws Exception {
    long route = route("ZZA", "ZZB");
    flight(route, Instant.now().plusSeconds(86400), "ON_SALE");
    flight(route, Instant.now().plusSeconds(172800), "ON_SALE");
    long other = route("ZZA", "ZZC");
    flight(other, Instant.now().minusSeconds(86400), "ON_SALE");
    flight(other, Instant.now().plusSeconds(86400), "CLOSED");
    flight(route("ZZA", "ZZD"), Instant.now().plusSeconds(86400), "ON_SALE");

    mvc.perform(get("/api/v1/airports").param("origin", "ZZA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].code").value("ZZB"))
        .andExpect(jsonPath("$.pagination.totalElements").value(1));
    mvc.perform(get("/api/v1/airports").param("origin", "ZZB"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    jdbc.update("UPDATE air_route SET is_active = 0 WHERE id = ?", route);
    mvc.perform(get("/api/v1/airports").param("origin", "ZZA"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void invalidFiltersReturnContractErrors() throws Exception {
    for (String[] query : new String[][] {
        {"page", "-1"}, {"page", "abc"}, {"size", "101"}, {"size", "0"},
        {"origin", "ZZD"}, {"origin", "xx"}, {"regionCode", "UNKNOWN"},
        {"countryCode", "XX"}}) {
      mvc.perform(get("/api/v1/airports").param(query[0], query[1]))
          .andExpect(status().is(422))
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.details[0].field").value(query[0]));
    }
  }

  private void airport(String code, String name, boolean active) {
    jdbc.update("""
        INSERT INTO airport (code, name, city, country_code, country_name, region_code,
          region_name, region_sort_order, time_zone, is_active)
        VALUES (?, ?, ?, 'ZZ', 'Quốc gia kiểm thử', 'TEST_CATALOG', 'Khu vực kiểm thử',
          999, 'Asia/Ho_Chi_Minh', ?)
        """, code, name, name, active);
  }

  private long route(String origin, String destination) {
    jdbc.update("""
        INSERT INTO air_route (origin_airport_id, destination_airport_id, air_distance_km)
        SELECT o.id, d.id, 100 FROM airport o JOIN airport d
          WHERE o.code = ? AND d.code = ?
        """, origin, destination);
    return jdbc.queryForObject("""
        SELECT r.id FROM air_route r JOIN airport o ON o.id = r.origin_airport_id
          JOIN airport d ON d.id = r.destination_airport_id WHERE o.code = ? AND d.code = ?
        """, Long.class, origin, destination);
  }

  private void flight(long route, Instant departure, String status) {
    jdbc.update("""
        INSERT INTO flight (public_id, flight_number, route_id, departure_at_utc,
          arrival_at_utc, sale_status) VALUES (?, 'SGTEST', ?, ?, ?, ?)
        """, UUID.randomUUID().toString(), route, Timestamp.from(departure),
        Timestamp.from(departure.plusSeconds(3600)), status);
  }
}
