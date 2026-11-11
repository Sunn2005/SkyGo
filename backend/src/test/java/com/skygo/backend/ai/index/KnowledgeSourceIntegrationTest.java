package com.skygo.backend.ai.index;

import static com.skygo.backend.ai.AiTestSupport.properties;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses an already migrated test DB. Never migrates, seeds or modifies business tables. */
@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
class KnowledgeSourceIntegrationTest {
  @Test
  void allProjectionQueriesExecuteAgainstTheCurrentMariaDbSchema() {
    String url = System.getenv("SKYGO_TEST_DB_URL");
    if (!url.matches("jdbc:mariadb://[^/]+/[^?]*_test[^?]*(\\?.*)?"))
      throw new IllegalArgumentException("Only a separate _test database is allowed for this test.");
    var datasource = new DriverManagerDataSource(url,
        System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME", "root"),
        System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD", ""));
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    transaction.setReadOnly(true);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    var index = new AiIndexProperties(true, Duration.ofMinutes(5), 8, Duration.ZERO, 1, 15, 500000);
    var source = new JdbcKnowledgeSource(new JdbcTemplate(datasource), transaction, properties(), index);
    assertThat(source.snapshot(Instant.now())).allSatisfy(doc -> {
      assertThat(UUID.fromString(doc.getId())).isNotNull();
      assertThat(doc.getMetadata()).containsEntry("managed_by", KnowledgeDocuments.MANAGED_BY);
      if ("private".equals(doc.getMetadata().get("scope")))
        assertThat(((Number) doc.getMetadata().get("owner_id")).longValue()).isPositive();
      else assertThat(doc.getMetadata()).doesNotContainKey("owner_id");
    });
  }
}
