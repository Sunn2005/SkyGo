package com.skygo.backend.ai;

import com.skygo.backend.ai.config.AiProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import java.util.Map;
import org.springframework.ai.document.Document;
import com.skygo.backend.ai.index.KnowledgeDocuments;

public final class AiTestSupport {
  private AiTestSupport() {}

  public static AiProperties properties(String... entries) {
    var environment = new MockEnvironment();
    for (String entry : entries) {
      int split = entry.indexOf('=');
      environment.setProperty(entry.substring(0, split), entry.substring(split + 1));
    }
    return Binder.get(environment).bindOrCreate("skygo.ai", AiProperties.class);
  }

  public static float[] vector() {
    var vector = new float[768];
    vector[0] = 1;
    return vector;
  }

  public static Document airportDocument(String code, double score) {
    return Document.builder().id("airport-" + code).text("Airport knowledge " + code)
        .metadata(Map.of("scope", "public", "document_type", "airport", "airport_code", code,
            "source_id", code, "managed_by", KnowledgeDocuments.MANAGED_BY,
            "embedding_model", properties().embeddingModel(), "title", "Sân bay " + code,
            "indexed_at", "2026-11-10T12:00:00Z"))
        .score(score).build();
  }
}
