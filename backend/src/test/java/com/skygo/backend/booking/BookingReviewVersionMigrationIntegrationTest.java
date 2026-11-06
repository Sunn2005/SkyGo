package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
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
class BookingReviewVersionMigrationIntegrationTest {
  @Test
  void upgradesV4PreservingHistoryAndSupportsGuardedTransactionalUpdates() throws Exception {
    String configured=System.getenv("SKYGO_TEST_DB_URL");
    int path=configured.indexOf('/',"jdbc:mariadb://".length());
    assertTrue(configured.startsWith("jdbc:mariadb://") && path>0);
    String database="skygo_review_version_test_"+UUID.randomUUID().toString().replace("-","");
    String url=configured.substring(0,path+1)+database+"?createDatabaseIfNotExist=true";
    String username=System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME","root");
    String password=System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD","");
    var source=new DriverManagerDataSource(url,username,password);
    var jdbc=new JdbcTemplate(source);
    try {
      Flyway.configure().dataSource(source).target("4").cleanDisabled(true).load().migrate();
      CheckedBaggageMigrationIntegrationTest.seedHistory(jdbc);
      // Cover unused, invalidated, expired and purged reviews, including an independent JSON schema version.
      jdbc.execute("""
          INSERT INTO booking_review(public_id,seat_hold_id,user_id,session_id,snapshot_ciphertext,
              snapshot_schema_version,expires_at_utc,invalidated_at_utc,snapshot_purged_at_utc)
          VALUES
            ('version-unused',999999,999999,999999,X'11',1,'2030-01-01',NULL,NULL),
            ('version-invalidated',999999,999999,999999,X'12',2,'2030-01-01','2026-01-01',NULL),
            ('version-expired',999999,999999,999999,X'13',1,'2020-01-01',NULL,NULL),
            ('version-purged',999999,999999,999999,NULL,1,'2030-01-01','2026-01-01','2026-01-02')
          """);
      String historyQuery="""
          SELECT id,public_id,seat_hold_id,user_id,session_id,HEX(snapshot_ciphertext) AS ciphertext,
            snapshot_schema_version,expires_at_utc,used_at_utc,invalidated_at_utc,snapshot_purged_at_utc,
            created_at_utc,updated_at_utc FROM booking_review ORDER BY id
          """;
      var before=jdbc.queryForList(historyQuery);
      var orders=jdbc.queryForList("SELECT id,review_id,fare_subtotal,extra_baggage_amount,vat_rate,vat_amount,total_amount FROM booking ORDER BY id");
      var payments=jdbc.queryForList("SELECT id,booking_id,amount,status FROM payment ORDER BY id");
      var flyway=Flyway.configure().dataSource(source).cleanDisabled(true).load();
      assertEquals(1,flyway.migrate().migrationsExecuted);
      flyway.validate();
      assertEquals(0,flyway.migrate().migrationsExecuted);
      assertEquals("5",flyway.info().current().getVersion().toString());
      assertEquals(before,jdbc.queryForList(historyQuery));
      assertEquals(orders,jdbc.queryForList("SELECT id,review_id,fare_subtotal,extra_baggage_amount,vat_rate,vat_amount,total_amount FROM booking ORDER BY id"));
      assertEquals(payments,jdbc.queryForList("SELECT id,booking_id,amount,status FROM payment ORDER BY id"));
      assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM booking_review WHERE review_version=1",Integer.class));
      assertEquals(2,jdbc.queryForObject("SELECT snapshot_schema_version FROM booking_review WHERE public_id='version-invalidated'",Integer.class));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_review SET review_version=0 WHERE public_id='version-unused'"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_review SET review_version=NULL WHERE public_id='version-unused'"));
      jdbc.execute("""
          INSERT INTO booking_review(public_id,seat_hold_id,user_id,session_id,snapshot_ciphertext,snapshot_schema_version,expires_at_utc)
          VALUES('version-new',999999,999999,999999,X'14',1,'2030-01-01')
          """);
      assertEquals(1,jdbc.queryForObject("SELECT review_version FROM booking_review WHERE public_id='version-new'",Integer.class));
      String guardedUpdate="""
          UPDATE booking_review SET review_version=review_version+1,snapshot_ciphertext=?
          WHERE public_id='version-unused' AND review_version=?
            AND used_at_utc IS NULL AND invalidated_at_utc IS NULL AND expires_at_utc>UTC_TIMESTAMP(6)
          """;
      assertEquals(1,jdbc.update(guardedUpdate,new byte[]{0x21},1));
      assertEquals(0,jdbc.update(guardedUpdate,new byte[]{0x22},1));
      assertEquals(2,jdbc.queryForObject("SELECT review_version FROM booking_review WHERE public_id='version-unused'",Integer.class));
      assertArrayEquals(new byte[]{0x21},jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE public_id='version-unused'",byte[].class));
      var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
      assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
        assertEquals(1,jdbc.update(guardedUpdate,new byte[]{0x23},2));
        throw new IllegalStateException("Simulated failure before commit");
      }));
      assertEquals(2,jdbc.queryForObject("SELECT review_version FROM booking_review WHERE public_id='version-unused'",Integer.class));
      assertArrayEquals(new byte[]{0x21},jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE public_id='version-unused'",byte[].class));
    } finally {
      assertTrue(database.matches("skygo_review_version_test_[a-f0-9]{32}"));
      try(var connection=DriverManager.getConnection(url,username,password);var statement=connection.createStatement()) {
        statement.execute("DROP DATABASE `"+database+"`");
      }
    }
  }
}
