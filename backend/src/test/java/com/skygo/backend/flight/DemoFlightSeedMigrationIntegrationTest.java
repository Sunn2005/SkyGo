package com.skygo.backend.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
class DemoFlightSeedMigrationIntegrationTest {
  @Test
  void migratesFixedNovemberScheduleWithBookableFaresAndBenefits() throws Exception {
    String configured = System.getenv("SKYGO_TEST_DB_URL");
    assertTrue(configured.matches("jdbc:mariadb://[^/]+/[a-zA-Z0-9_]*_test[a-zA-Z0-9_]*(\\?.*)?"),
        "Use an explicitly named test database, never the main SkyGo database");
    String server = configured.substring(0, configured.indexOf('/', "jdbc:mariadb://".length()) + 1);
    String database = "skygo_demo_flights_test_" + UUID.randomUUID().toString().replace("-", "");
    String user = System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME", "root");
    String password = System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD", "");
    String url = server + database;

    try (var connection = DriverManager.getConnection(server, user, password);
         var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
    try {
      var source = new DriverManagerDataSource(url, user, password);
      var jdbc = new JdbcTemplate(source);
      var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
      flyway.migrate();
      flyway.validate();
      assertEquals(0, flyway.migrate().migrationsExecuted);

      assertEquals(31, jdbc.queryForObject(
          "SELECT COUNT(*) FROM flight WHERE public_id LIKE 'demo-202611-%'", Integer.class));
      assertEquals(62, jdbc.queryForObject("""
          SELECT COUNT(*) FROM flight_fare fare JOIN flight flight ON flight.id = fare.flight_id
          WHERE flight.public_id LIKE 'demo-202611-%' AND fare.is_on_sale = 1
          AND fare.available_seats > 0
          """, Integer.class));
      assertEquals(186, jdbc.queryForObject("""
          SELECT COUNT(*) FROM fare_benefit_policy benefit
          JOIN flight_fare fare ON fare.id = benefit.flight_fare_id
          JOIN flight flight ON flight.id = fare.flight_id
          WHERE flight.public_id LIKE 'demo-202611-%'
          """, Integer.class));

      var days = jdbc.queryForList("""
          SELECT origin.code AS origin, destination.code AS destination,
                 DATE(flight.departure_at_utc + INTERVAL 7 HOUR) AS local_day,
                 COUNT(*) AS flight_count
          FROM flight
          JOIN air_route route ON route.id = flight.route_id
          JOIN airport origin ON origin.id = route.origin_airport_id
          JOIN airport destination ON destination.id = route.destination_airport_id
          WHERE flight.public_id LIKE 'demo-202611-%' AND flight.sale_status = 'ON_SALE'
          GROUP BY origin.code, destination.code, DATE(flight.departure_at_utc + INTERVAL 7 HOUR)
          ORDER BY origin.code, local_day
          """);
      assertEquals(7, days.size());
      var outboundDates = Set.of("2026-11-23", "2026-11-24", "2026-11-25",
          "2026-11-26", "2026-11-27", "2026-11-28");
      for (Map<String, Object> day : days) {
        boolean outbound = "SGN".equals(day.get("origin"));
        assertEquals(outbound ? "PQC" : "SGN", day.get("destination"));
        assertTrue(outbound ? outboundDates.contains(day.get("local_day").toString())
            : "2026-11-25".equals(day.get("local_day").toString()));
        int expected = outbound ? 5 : 1;
        assertEquals(expected, ((Number) day.get("flight_count")).intValue());
      }
      assertEquals(1, jdbc.queryForObject("""
          SELECT COUNT(*) FROM flight flight
          JOIN air_route route ON route.id = flight.route_id
          JOIN airport origin ON origin.id = route.origin_airport_id
          JOIN airport destination ON destination.id = route.destination_airport_id
          WHERE flight.public_id = 'demo-202611-pqc-sgn-25-1'
            AND origin.code = 'PQC' AND destination.code = 'SGN'
            AND flight.departure_at_utc = '2026-11-25 09:30:00'
          """, Integer.class));

    } finally {
      try (var connection = DriverManager.getConnection(server, user, password);
           var statement = connection.createStatement()) {
        statement.execute("DROP DATABASE `" + database + "`");
      }
    }
  }
}
