package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL", matches=".+")
class CheckedBaggageMigrationIntegrationTest {
  @Test
  void upgradesV3WithoutRepricingHistoryAndEnforcesExtraBaggageConstraints() throws Exception {
    // Always use a disposable sibling database, never the configured application database.
    String configured=System.getenv("SKYGO_TEST_DB_URL");
    int path=configured.indexOf('/',"jdbc:mariadb://".length());
    assertTrue(configured.startsWith("jdbc:mariadb://") && path>0);
    String database="skygo_baggage_migration_test_"+UUID.randomUUID().toString().replace("-", "");
    String url=configured.substring(0,path+1)+database+"?createDatabaseIfNotExist=true";
    String username=System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME","root");
    String password=System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD","");
    var source=new DriverManagerDataSource(url,username,password);
    var jdbc=new JdbcTemplate(source);
    try {
      Flyway.configure().dataSource(source).target("3").cleanDisabled(true).load().migrate();
      seedHistory(jdbc);
      long benefits=jdbc.queryForObject("SELECT COUNT(*) FROM fare_benefit_policy",Long.class);
      var weights=jdbc.query("SELECT id,checked_pieces,checked_piece_max_kg FROM fare_benefit_policy ORDER BY id",
          (rs,n)->List.of(rs.getLong("id"),rs.getInt("checked_pieces"),rs.getInt("checked_piece_max_kg")));
      var amounts=jdbc.queryForMap("SELECT fare_subtotal,vat_rate,vat_amount,total_amount FROM booking WHERE id=999999");
      byte[] review=jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE id=999999",byte[].class);
      var flyway=Flyway.configure().dataSource(source).cleanDisabled(true).load();
      assertEquals(1,flyway.migrate().migrationsExecuted);
      flyway.validate();
      assertEquals(0,flyway.migrate().migrationsExecuted);
      assertEquals(amounts,jdbc.queryForMap("SELECT fare_subtotal,vat_rate,vat_amount,total_amount FROM booking WHERE id=999999"));
      assertEquals(0L,jdbc.queryForObject("SELECT extra_baggage_amount FROM booking WHERE id=999999",Long.class));
      assertArrayEquals(review,jdbc.queryForObject("SELECT snapshot_ciphertext FROM booking_review WHERE id=999999",byte[].class));
      assertEquals(1,jdbc.queryForObject("SELECT snapshot_schema_version FROM booking_review WHERE id=999999",Integer.class));
      var historical=jdbc.queryForMap("SELECT * FROM booking_passenger_benefit WHERE id=999999");
      assertNull(historical.get("checked_baggage_policy_id"));
      assertNull(historical.get("checked_piece_max_linear_cm"));
      assertEquals(23,((Number)historical.get("checked_piece_max_kg")).intValue());
      assertEquals(0,((Number)historical.get("extra_checked_pieces")).intValue());
      assertEquals(0L,((Number)historical.get("extra_baggage_amount")).longValue());
      assertEquals(benefits,jdbc.queryForObject("SELECT COUNT(*) FROM fare_benefit_policy",Long.class));
      assertEquals(weights,jdbc.query("""
          SELECT b.id,b.checked_pieces,CASE WHEN b.checked_pieces=0 THEN 0 ELSE p.piece_max_kg END AS checked_piece_max_kg
          FROM fare_benefit_policy b JOIN checked_baggage_policy p ON p.id=b.checked_baggage_policy_id ORDER BY b.id
          """,(rs,n)->List.of(rs.getLong("id"),rs.getInt("checked_pieces"),rs.getInt("checked_piece_max_kg"))));
      var policy=jdbc.queryForMap("SELECT * FROM checked_baggage_policy WHERE version=1");
      assertEquals(23,((Number)policy.get("piece_max_kg")).intValue());
      assertEquals(158,((Number)policy.get("max_linear_dimensions_cm")).intValue());
      assertEquals(2,((Number)policy.get("max_extra_pieces")).intValue());
      assertEquals(325000L,((Number)policy.get("extra_piece_price_excl_vat")).longValue());
      assertEquals("VND",policy.get("currency"));
      assertThrows(DataAccessException.class,()->jdbc.update("""
          UPDATE booking_passenger_benefit SET extra_checked_pieces=1,
            extra_unit_price_excl_vat=325000,extra_baggage_amount=325000 WHERE id=999999
          """)); // Purchased baggage must have a complete policy snapshot.
      jdbc.update("""
          UPDATE booking_passenger_benefit SET checked_baggage_policy_id=?,checked_piece_max_linear_cm=158,
            extra_checked_pieces=2,extra_unit_price_excl_vat=325000,extra_baggage_amount=650000 WHERE id=999999
          """,policy.get("id"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_passenger_benefit SET extra_checked_pieces=3,extra_baggage_amount=975000 WHERE id=999999"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_passenger_benefit SET extra_baggage_amount=325000 WHERE id=999999"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_passenger_benefit SET seat_type=NULL WHERE id=999999"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking_passenger_benefit SET checked_baggage_policy_id=999999 WHERE id=999999"));
      assertThrows(DataAccessException.class,()->jdbc.update("UPDATE booking SET extra_baggage_amount=650000 WHERE id=999999"));
      // (1,000,000 net ticket + 650,000 net extras) + 10% VAT = 1,815,000.
      jdbc.update("UPDATE booking SET extra_baggage_amount=650000,vat_amount=165000,total_amount=1815000 WHERE id=999999");
      assertEquals(1815000L,jdbc.queryForObject("SELECT total_amount FROM booking WHERE id=999999",Long.class));
    } finally {
      assertTrue(database.matches("skygo_baggage_migration_test_[a-f0-9]{32}"));
      try(var connection=DriverManager.getConnection(url,username,password);var statement=connection.createStatement()) {
        statement.execute("DROP DATABASE `"+database+"`");
      }
    }
  }

  private void seedHistory(JdbcTemplate jdbc) {
    jdbc.execute("INSERT INTO app_user(id,public_id,full_name,phone_normalized,password_hash) VALUES(999999,'migration-user','Test','+84999999999','test')");
    jdbc.execute("""
        INSERT INTO auth_session(id,public_id,user_id,token_hash,expires_at_utc)
        VALUES(999999,'migration-session',999999,UNHEX(SHA2('test',256)),'2030-01-01')
        """);
    jdbc.execute("""
        INSERT INTO seat_hold(id,public_id,user_id,trip_type,adults,children,infants,status,started_at_utc,expires_at_utc)
        VALUES(999999,'migration-hold',999999,'ONE_WAY',1,0,0,'CONSUMED','2026-01-01','2026-01-01 00:15:00')
        """);
    jdbc.execute("""
        INSERT INTO booking_review(id,public_id,seat_hold_id,user_id,session_id,snapshot_ciphertext,
          snapshot_schema_version,expires_at_utc,used_at_utc)
        VALUES(999999,'migration-review',999999,999999,999999,X'010203',1,'2026-01-01 00:15:00','2026-01-01 00:01:00')
        """);
    jdbc.execute("""
        INSERT INTO booking(id,public_id,booking_code,user_id,review_id,seat_hold_id,idempotency_key,request_hash,
          trip_type,adults,children,infants,contact_email,contact_phone,fare_subtotal,vat_rate,vat_amount,total_amount,booking_status)
        VALUES(999999,'migration-booking','MIGRATION',999999,999999,999999,'migration',UNHEX(SHA2('test',256)),
          'ONE_WAY',1,0,0,'test@example.com','+84999999999',1000000,0.1,100000,1100000,'PENDING_PAYMENT')
        """);
    jdbc.execute("""
        INSERT INTO booking_leg(id,booking_id,direction,flight_fare_id,flight_public_id,flight_number,airline_code,
          airline_name,cabin_class,origin_code,origin_name,origin_city,origin_country_code,origin_time_zone,
          destination_code,destination_name,destination_city,destination_country_code,destination_time_zone,
          departure_at_utc,arrival_at_utc,duration_minutes,route_type)
        SELECT 999999,999999,'OUTBOUND',fare.id,f.public_id,f.flight_number,'SG','SkyGo',fare.cabin_class,
          o.code,o.name,o.city,o.country_code,o.time_zone,d.code,d.name,d.city,d.country_code,d.time_zone,
          f.departure_at_utc,f.arrival_at_utc,TIMESTAMPDIFF(MINUTE,f.departure_at_utc,f.arrival_at_utc),
          IF(o.country_code=d.country_code,'DOMESTIC','INTERNATIONAL')
        FROM flight_fare fare JOIN flight f ON f.id=fare.flight_id JOIN air_route r ON r.id=f.route_id
          JOIN airport o ON o.id=r.origin_airport_id JOIN airport d ON d.id=r.destination_airport_id
        ORDER BY fare.id LIMIT 1
        """);
    jdbc.execute("""
        INSERT INTO booking_passenger(id,public_id,booking_id,client_passenger_id,passenger_type,title,given_names,
          surname,date_of_birth,gender,document_type,document_number_ciphertext,issuing_country_code)
        VALUES(999999,'migration-passenger',999999,'p1','ADULT','MR','Test','User','1990-01-01','MALE','PERSONAL_ID',X'01','VN')
        """);
    jdbc.execute("""
        INSERT INTO booking_passenger_benefit(id,booking_id,booking_passenger_id,booking_leg_id,carry_on_max_kg,
          checked_pieces,checked_piece_max_kg,included_meals_per_leg,seat_type,stroller_pieces,stroller_max_kg)
        VALUES(999999,999999,999999,999999,7,1,23,0,'STANDARD',0,0)
        """);
    jdbc.execute("INSERT INTO payment(public_id,booking_id,method,status,amount) VALUES('migration-payment',999999,'QR_SIMULATION','PENDING',1100000)");
  }
}
