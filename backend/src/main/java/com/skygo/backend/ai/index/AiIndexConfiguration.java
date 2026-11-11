package com.skygo.backend.ai.index;

import com.skygo.backend.ai.config.AiProperties;
import java.time.Clock;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"skygo.ai.enabled", "skygo.ai.index.enabled"}, havingValue = "true")
@EnableConfigurationProperties(AiIndexProperties.class)
public class AiIndexConfiguration {
  @Bean
  KnowledgeSource skygoKnowledgeSource(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
      AiProperties ai, AiIndexProperties index) {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(60);
    return new JdbcKnowledgeSource(jdbc, transaction, ai, index);
  }

  @Bean
  KnowledgeIndexStore skygoKnowledgeIndexStore(JdbcTemplate jdbc, VectorStore vectors,
      AiProperties ai, AiIndexProperties index, PlatformTransactionManager transactionManager) {
    return new MariaDbKnowledgeIndexStore(jdbc, vectors, ai, index, transactionManager);
  }

  @Bean
  KnowledgeIndexer skygoKnowledgeIndexer(KnowledgeSource source, KnowledgeIndexStore store,
      AiIndexProperties index, Clock clock) {
    return new KnowledgeIndexer(source, store, index, clock);
  }

  @Bean(destroyMethod = "close")
  KnowledgeIndexLifecycle skygoKnowledgeIndexLifecycle(KnowledgeIndexer indexer,
      AiIndexProperties properties, org.springframework.core.env.Environment environment) {
    return new KnowledgeIndexLifecycle(indexer, properties.fixedDelay(),
        environment.getProperty("skygo.ai.index.run-once", Boolean.class, false));
  }
}
