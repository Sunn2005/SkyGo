package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.config.AiProperties;
import com.skygo.backend.ai.index.KnowledgeDocuments;
import com.skygo.backend.ai.service.AiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/** Retrieve public descriptive knowledge only; live fares and private orders use SQL tools. */
public final class AiKnowledgeReader {
  private static final Set<String> TYPES = Set.of("airport", "route", "fare_benefit", "passenger_fare_rule", "baggage_policy");
  private final VectorStore vectors;
  private final AiProperties ai;

  public AiKnowledgeReader(VectorStore vectors, AiProperties ai) { this.vectors = vectors; this.ai = ai; }

  public List<Hit> search(String query) {
    try {
      var f = new FilterExpressionBuilder();
      var filter = f.and(f.and(f.eq("scope", "public"), f.eq("embedding_model", ai.embeddingModel())),
          f.and(f.eq("managed_by", KnowledgeDocuments.MANAGED_BY),
              f.in("document_type", TYPES.toArray()))).build();
      var docs = vectors.similaritySearch(SearchRequest.builder().query(query).topK(ai.topK())
          .similarityThreshold(ai.similarityThreshold()).filterExpression(filter).build());
      return docs.stream().filter(doc -> "public".equals(doc.getMetadata().get("scope")))
          .filter(doc -> !doc.getMetadata().containsKey("owner_id"))
          .filter(doc -> KnowledgeDocuments.MANAGED_BY.equals(doc.getMetadata().get("managed_by")))
          .filter(doc -> ai.embeddingModel().equals(doc.getMetadata().get("embedding_model")))
          .filter(doc -> TYPES.contains(doc.getMetadata().get("document_type")))
          .filter(doc -> doc.getText() != null && !doc.getText().isBlank()).limit(ai.topK())
          .map(doc -> new Hit(doc.getId(), String.valueOf(doc.getMetadata().getOrDefault("title", "Tài liệu SkyGo")),
              doc.getText().substring(0, Math.min(4000, doc.getText().length())), indexedAt(doc.getMetadata().get("indexed_at")),
              Map.of("documentType", text(doc.getMetadata(), "document_type"), "flightId", text(doc.getMetadata(), "flight_id"),
                  "cabinClass", text(doc.getMetadata(), "cabin_class"), "passengerType", text(doc.getMetadata(), "passenger_type"))))
          .toList();
    } catch (AiException ex) { throw ex; }
    catch (RuntimeException ex) {
      Throwable cause = ex.getCause();
      for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause())
        if (cause instanceof AiException safe) throw safe;
      throw new AiException(AiException.Code.VECTOR_STORE_UNAVAILABLE, "Chưa truy xuất được tài liệu AI từ MariaDB. Vui lòng thử lại sau.");
    }
  }

  private static Instant indexedAt(Object value) {
    try { return value instanceof String text ? Instant.parse(text) : null; }
    catch (RuntimeException ex) { return null; }
  }
  private static String text(Map<String, Object> metadata, String key) {
    return metadata.get(key) instanceof String value ? value : "";
  }
  public record Hit(String id, String title, String text, Instant indexedAt, Map<String, String> applicability) {
    public Hit(String id, String title, String text, Instant indexedAt) {
      this(id, title, text, indexedAt, Map.of());
    }
  }
}
