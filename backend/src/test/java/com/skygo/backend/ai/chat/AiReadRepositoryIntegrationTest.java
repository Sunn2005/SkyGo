package com.skygo.backend.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import com.skygo.backend.ai.chat.AiToolInputs.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in read-only SQL checks against an already migrated _test database. No migrations or seeds. */
@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
class AiReadRepositoryIntegrationTest {
  @Test
  void flightCteAndRecentHistoryExecuteOnMariaDbWithoutCallingModels() {
    String url = System.getenv("SKYGO_TEST_DB_URL");
    if (!url.matches("jdbc:mariadb://[^/]+/[^?]*_test[^?]*(\\?.*)?"))
      throw new IllegalArgumentException("A separate _test database is required.");
    var source = new DriverManagerDataSource(url,
        System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME", "root"),
        System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD", ""));
    var jdbc = new JdbcTemplate(source);
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    transaction.setReadOnly(true);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    var repository = new AiReadRepository(new NamedParameterJdbcTemplate(source), Clock.systemUTC());
    transaction.execute(status -> {
      var airports = jdbc.queryForList("SELECT code FROM airport WHERE is_active=1 ORDER BY code LIMIT 2", String.class);
      assertThat(airports).hasSize(2);
      var tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
      var result = repository.flights(new FlightQuery(airports.getFirst(), airports.getLast(), tomorrow,
          1, 1, 0, Cabin.ANY, Sort.PRICE_ASC, 10));
      assertThat(result.options()).hasSizeLessThanOrEqualTo(10);
      assertThat(result.options()).allSatisfy(option -> {
        assertThat(option.partyTotal()).isGreaterThanOrEqualTo(option.adultPrice());
        assertThat(option.availableSeats()).isGreaterThanOrEqualTo(2);
      });
      assertThat(repository.bookings(Long.MAX_VALUE, new BookingQuery(15, 3, BookingStatus.ALL)).bookings()).isEmpty();
      return null;
    });
  }
}
