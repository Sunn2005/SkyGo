package com.skygo.backend.ai.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class KnowledgeIndexLifecycleTest {
  @Test
  void oneShotCompletesThenClosesTheApplicationWithoutLeavingAWorkerRunning() {
    var application = new SpringApplication(OneShotApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    var context = application.run("--skygo.ai.enabled=false", "--spring.main.banner-mode=off");
    assertThat(context.isActive()).isFalse();
    assertThat(OneShotApplication.indexer.lastReport()).isNotNull();
    assertThat(OneShotApplication.indexer.lastReport().total()).isZero();
  }

  @Configuration(proxyBeanMethods = false)
  static class OneShotApplication {
    static KnowledgeIndexer indexer;

    @Bean(destroyMethod = "close")
    KnowledgeIndexLifecycle lifecycle() {
      var store = mock(KnowledgeIndexStore.class);
      when(store.hashes()).thenReturn(Map.of());
      indexer = new KnowledgeIndexer(now -> List.of(), store, KnowledgeIndexerTest.INDEX, Clock.systemUTC());
      return new KnowledgeIndexLifecycle(indexer, Duration.ofMinutes(5), true);
    }
  }
}
