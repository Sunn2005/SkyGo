package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class BookingHoldSwitchMigrationIntegrationTest {
  @Test void upgradesV9WithoutRewritingHistoryAndEnforcesSwitchOwnershipAndUniqueness() throws Exception {
    String configured=System.getenv("SKYGO_TEST_DB_URL");
    int path=configured.indexOf('/',"jdbc:mariadb://".length());
    assertTrue(configured.startsWith("jdbc:mariadb://") && path>0);
    String database="skygo_hold_switch_test_"+UUID.randomUUID().toString().replace("-","");
    String url=configured.substring(0,path+1)+database+"?createDatabaseIfNotExist=true";
    String username=System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME","root");
    String password=System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD","");
    var source=new DriverManagerDataSource(url,username,password);var jdbc=new JdbcTemplate(source);
    try {
      Flyway.configure().dataSource(source).target("9").cleanDisabled(true).load().migrate();
      CheckedBaggageMigrationIntegrationTest.seedHistory(jdbc);
      jdbc.execute("UPDATE seat_hold SET booking_id=999999 WHERE id=999999");
      jdbc.execute("""
          INSERT INTO payment_attempt(id,payment_id,booking_id,seat_hold_id,order_code,payment_link_id,status,amount,expires_at_utc)
          SELECT 999999,id,booking_id,999999,999999,'migration-link','PENDING',amount,'2026-01-01 00:15:00'
          FROM payment WHERE booking_id=999999
          """);
      var holds=jdbc.queryForList("SELECT * FROM seat_hold ORDER BY id");
      var orders=jdbc.queryForList("SELECT id,public_id,booking_status,total_amount,updated_at_utc FROM booking ORDER BY id");
      var attempts=jdbc.queryForList("SELECT * FROM payment_attempt ORDER BY id");
      var inventory=jdbc.queryForList("SELECT id,capacity,available_seats FROM flight_fare ORDER BY id");
      var flyway=Flyway.configure().dataSource(source).target("10").cleanDisabled(true).load();
      assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();
      assertEquals(0,flyway.migrate().migrationsExecuted);
      assertEquals(holds,jdbc.queryForList("SELECT * FROM seat_hold ORDER BY id"));
      assertEquals(orders,jdbc.queryForList("SELECT id,public_id,booking_status,total_amount,updated_at_utc FROM booking ORDER BY id"));
      assertEquals(attempts,jdbc.queryForList("SELECT * FROM payment_attempt ORDER BY id"));
      assertEquals(inventory,jdbc.queryForList("SELECT id,capacity,available_seats FROM flight_fare ORDER BY id"));
      assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM flight WHERE booking_closes_at_utc IS NOT NULL",Integer.class));
      jdbc.execute("UPDATE seat_hold SET status='RELEASED' WHERE id=999999");
      jdbc.execute("UPDATE booking SET booking_status='AWAITING_REHOLD' WHERE id=999999");
      jdbc.execute("UPDATE booking SET booking_status='CANCELLED',cancelled_at_utc=UTC_TIMESTAMP(6),cancellation_reason='BOOKING_WINDOW_CLOSED' WHERE id=999999");
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE seat_hold SET status='INVALID' WHERE id=999999"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE flight SET booking_closes_at_utc=departure_at_utc+INTERVAL 1 SECOND LIMIT 1"));
      jdbc.execute("""
          INSERT INTO seat_hold(id,public_id,user_id,trip_type,adults,children,infants,status,started_at_utc,expires_at_utc)
          VALUES(1000000,'switch-target',999999,'ONE_WAY',1,0,0,'ACTIVE','2030-01-01','2030-01-01 00:15:00')
          """);
      String insert="""
          INSERT INTO booking_hold_switch(public_id,user_id,booking_id,source_hold_id,expected_hold_version,payment_attempt_id,
            idempotency_key,request_hash,requested_selection,status)
          VALUES(?,999999,999999,999999,1,999999,?,UNHEX(SHA2('request',256)),'{}','VERIFYING_PAYMENT')
          """;
      jdbc.update(insert,"switch-1","key-1");
      assertThrows(DataAccessException.class,()->jdbc.update(insert,"switch-2","key-2"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_hold_switch SET user_id=1 WHERE public_id='switch-1'"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_hold_switch SET status='COMPLETED' WHERE public_id='switch-1'"));
      jdbc.update("UPDATE booking_hold_switch SET status='COMPLETED',target_hold_id=1000000 WHERE public_id='switch-1'");
      assertThrows(DataAccessException.class,()->jdbc.update(insert,"switch-duplicate","key-1"));
      jdbc.update(insert,"switch-2","key-2");
      assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_hold_switch WHERE active_user_id=999999",Integer.class));
    } finally {
      assertTrue(database.matches("skygo_hold_switch_test_[a-f0-9]{32}"));
      try(var connection=DriverManager.getConnection(url,username,password);var statement=connection.createStatement()) {
        statement.execute("DROP DATABASE `"+database+"`");
      }
    }
  }
}
