package com.skygo.backend.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.ai.config.*;
import com.skygo.backend.ai.index.KnowledgeDocuments;
import com.skygo.backend.ai.service.RagService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Opt-in paid Gemini test. Requires an already migrated separate _test database; no DDL. */
@EnabledIfEnvironmentVariable(named="SKYGO_AI_LIVE_TEST",matches="true")
class LiveRagSmokeTest {
  @Test @Timeout(180)
  void geminiEmbeddingMariaDbRetrievalAndGroundedChatRoundTrip() {
    String url=System.getenv("SKYGO_TEST_DB_URL");
    if(url==null || !url.matches("jdbc:mariadb://[^/]+/[^?]*_test[^?]*(\\?.*)?"))
      throw new IllegalArgumentException("Set SKYGO_TEST_DB_URL to a separately migrated _test database.");
    var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME","root"),
        System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD",""));
    var jdbc=new JdbcTemplate(ds);
    var context=new AnnotationConfigApplicationContext();
    new AiEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(),new SpringApplication());
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("liveTest",Map.of(
        "skygo.ai.enabled","true","skygo.ai.similarity-threshold","0.0")));
    var ai=Binder.get(context.getEnvironment()).bindOrCreate("skygo.ai",AiProperties.class);
    var doc=KnowledgeDocuments.create(ai,"smoke",UUID.randomUUID().toString(),"Tài liệu thử nghiệm",
        "Quầy hỗ trợ hành lý mẫu có mã MAYXANH42. Dữ liệu giả để kiểm thử RAG.",Map.of(),null,Instant.now());
    try {
      context.registerBean(JdbcTemplate.class,() -> jdbc);
      context.register(AiConfiguration.class); context.refresh();
      context.getBean(VectorStore.class).add(List.of(doc));
      var result=context.getBean(RagService.class).answer("Mã nhận diện nơi trợ giúp vali trong tài liệu thử nghiệm?");
      assertTrue(result.hasRetrievedContext());
      assertTrue(result.sources().stream().anyMatch(s -> s.documentId().equals(doc.getId())));
      assertTrue(result.answer().contains("MAYXANH42"));
    } finally {
      context.close();
      jdbc.update("DELETE FROM ai_document WHERE id=? AND JSON_VALUE(metadata,'$.document_type')='smoke'",doc.getId());
    }
  }
}
