package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL", matches=".+")
class SeatHoldVersionMigrationIntegrationTest {
  @Test
  void upgradesV8PreservingHoldsInventoryAndOrders() throws Exception {
    String configured=System.getenv("SKYGO_TEST_DB_URL");
    int path=configured.indexOf('/',"jdbc:mariadb://".length());
    assertTrue(configured.startsWith("jdbc:mariadb://") && path>0);
    String database="skygo_hold_version_test_"+UUID.randomUUID().toString().replace("-","");
    String url=configured.substring(0,path+1)+database+"?createDatabaseIfNotExist=true";
    String username=System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME","root");
    String password=System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD","");
    var source=new DriverManagerDataSource(url,username,password);
    var jdbc=new JdbcTemplate(source);
    try {
      Flyway.configure().dataSource(source).target("8").cleanDisabled(true).load().migrate();
      CheckedBaggageMigrationIntegrationTest.seedHistory(jdbc);
      jdbc.execute("""
          INSERT INTO seat_hold(public_id,user_id,trip_type,adults,children,infants,status,started_at_utc,expires_at_utc)
          VALUES('version-active',999999,'ROUND_TRIP',1,1,1,'ACTIVE','2030-01-01','2030-01-01 00:15:00'),
            ('version-expired',999999,'ONE_WAY',1,0,0,'EXPIRED','2020-01-01','2020-01-01 00:15:00')
          """);
      jdbc.execute("""
          INSERT INTO seat_hold_leg(seat_hold_id,direction,flight_fare_id,reserved_seats)
          SELECT h.id,'OUTBOUND',MIN(f.id),2 FROM seat_hold h CROSS JOIN flight_fare f
          WHERE h.public_id='version-active' GROUP BY h.id
          """);
      jdbc.execute("UPDATE seat_hold SET booking_id=999999 WHERE public_id='migration-hold'");
      String history="""
          SELECT id,public_id,user_id,booking_id,trip_type,adults,children,infants,status,
            started_at_utc,expires_at_utc,released_at_utc,active_user_id,created_at_utc,updated_at_utc
          FROM seat_hold ORDER BY id
          """;
      var before=jdbc.queryForList(history);
      var inventory=jdbc.queryForList("SELECT id,capacity,available_seats FROM flight_fare ORDER BY id");
      var legs=jdbc.queryForList("SELECT * FROM seat_hold_leg ORDER BY id");
      var orders=jdbc.queryForList("SELECT id,review_id,seat_hold_id,total_amount,booking_status FROM booking ORDER BY id");
      var flyway=Flyway.configure().dataSource(source).target("9").cleanDisabled(true).load();
      assertEquals(1,flyway.migrate().migrationsExecuted);
      flyway.validate();
      assertEquals(0,flyway.migrate().migrationsExecuted);
      assertEquals("9",flyway.info().current().getVersion().toString());
      assertEquals(before,jdbc.queryForList(history));
      assertEquals(inventory,jdbc.queryForList("SELECT id,capacity,available_seats FROM flight_fare ORDER BY id"));
      assertEquals(legs,jdbc.queryForList("SELECT * FROM seat_hold_leg ORDER BY id"));
      assertEquals(orders,jdbc.queryForList("SELECT id,review_id,seat_hold_id,total_amount,booking_status FROM booking ORDER BY id"));
      assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE hold_version=1",Integer.class));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE seat_hold SET hold_version=0 WHERE public_id='version-active'"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE seat_hold SET hold_version=NULL WHERE public_id='version-active'"));
      jdbc.execute("""
          INSERT INTO seat_hold(public_id,user_id,trip_type,adults,children,infants,status,started_at_utc,expires_at_utc)
          VALUES('version-new',999999,'ONE_WAY',1,0,0,'EXPIRED','2030-01-01','2030-01-01 00:15:00')
          """);
      assertEquals(1,jdbc.queryForObject("SELECT hold_version FROM seat_hold WHERE public_id='version-new'",Long.class));
      String guarded="UPDATE seat_hold SET hold_version=hold_version+1 WHERE public_id='version-active' AND hold_version=?";
      assertEquals(1,jdbc.update(guarded,1));
      assertEquals(0,jdbc.update(guarded,1));
      var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
      assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
        assertEquals(1,jdbc.update(guarded,2));
        throw new IllegalStateException("Simulated failure before commit");
      }));
      assertEquals(2,jdbc.queryForObject("SELECT hold_version FROM seat_hold WHERE public_id='version-active'",Long.class));
    } finally {
      assertTrue(database.matches("skygo_hold_version_test_[a-f0-9]{32}"));
      try(var connection=DriverManager.getConnection(url,username,password);var statement=connection.createStatement()) {
        statement.execute("DROP DATABASE `"+database+"`");
      }
    }
  }
}
