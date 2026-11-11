package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.chat.contract.AiChatResponse.Airport;
import com.skygo.backend.ai.chat.contract.AiChatResponse.Source;
import com.skygo.backend.ai.config.AiProperties;
import com.skygo.backend.ai.index.KnowledgeDocuments;
import com.skygo.backend.ai.service.AiException;
import java.time.Instant;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/** Semantic identity lookup only. Prices, availability and airport fields come from SQL. */
public final class AiAirportResolver {
  private final VectorStore vectors;
  private final AiProperties ai;
  private final AiAirportProperties settings;

  public AiAirportResolver(VectorStore vectors, AiProperties ai, AiAirportProperties settings) {
    this.vectors = vectors;
    this.ai = ai;
    this.settings = settings;
  }

  public Session newSession() { return new Session(); }

  /** One session per chat request; no cross-user or persistent embedding cache. */
  public final class Session {
    private final Map<String, List<Hit>> cache = new HashMap<>();
    private final Map<String, Source> sources = new LinkedHashMap<>();

    public Airport resolve(String value, String field, List<Airport> catalog) {
      var exact = AiReadRepository.exactAirports(value, catalog);
      if (exact.size() == 1) return exact.getFirst();
      if (exact.size() > 1) throw choose(field, exact, false);
      String query = value == null ? "" : value.strip().replaceAll("\\s+", " ");
      // An unknown explicitly supplied IATA code must not silently become another airport.
      if (query.isEmpty() || query.length() > 120 || query.matches("[A-Z]{3}")) throw missing(field);
      var hits = cache.computeIfAbsent(query.toLowerCase(Locale.ROOT), ignored -> search(query));
      if (hits.isEmpty()) throw missing(field);

      var active = new LinkedHashMap<String, Airport>();
      for (var airport : catalog) active.put(airport.code().toUpperCase(Locale.ROOT), airport);
      var best = hits.getFirst();
      // Never replace the best (now inactive/deleted) identity with a lower-ranked city.
      if (!active.containsKey(best.code())) throw missing(field);
      double gap = hits.size() > 1 ? best.score() - hits.get(1).score() : Double.POSITIVE_INFINITY;
      var choices = new LinkedHashMap<String, Airport>();
      for (var hit : hits) {
        if (best.score() - hit.score() >= settings.minimumScoreGap()) continue;
        var airport = active.get(hit.code());
        if (airport == null) continue;
        choices.put(airport.code(), airport);
        // Vector ranking may favor one airport, but a multi-airport city is still ambiguous.
        for (var other : catalog) {
          if (AiReadRepository.sameCity(airport, other)) choices.putIfAbsent(other.code(), other);
        }
        sources.putIfAbsent(hit.id(), new Source("MARIADB_VECTOR", hit.id(), hit.title(), hit.indexedAt()));
      }
      if (choices.size() == 1 && best.score() >= settings.autoSelectSimilarity()
          && gap >= settings.minimumScoreGap()) return active.get(best.code());
      throw choose(field, choices.values().stream().limit(10).toList(), choices.size() == 1);
    }

    public List<Source> sources() { return List.copyOf(sources.values()); }
  }

  private List<Hit> search(String location) {
    try {
      var f = new FilterExpressionBuilder();
      var filter = f.and(f.and(f.eq("scope", "public"), f.eq("document_type", "airport")),
          f.and(f.eq("managed_by", KnowledgeDocuments.MANAGED_BY), f.eq("embedding_model", ai.embeddingModel()))).build();
      var documents = vectors.similaritySearch(SearchRequest.builder()
          .query("Tên thành phố hoặc sân bay: " + location).topK(settings.topK())
          .similarityThreshold(settings.minimumSimilarity()).filterExpression(filter).build());
      var distinct = new HashMap<String, Hit>();
      for (var doc : documents) {
        if (!eligible(doc)) continue;
        var metadata = doc.getMetadata();
        String code = ((String) metadata.get("airport_code")).strip().toUpperCase(Locale.ROOT);
        String title = metadata.get("title") instanceof String t && !t.isBlank() ? t : "Sân bay " + code;
        var hit = new Hit(code, Math.min(1.0, doc.getScore()), doc.getId(),
            title.substring(0, Math.min(200, title.length())), indexedAt(metadata.get("indexed_at")));
        distinct.merge(code, hit, (a, b) -> a.score() >= b.score() ? a : b);
      }
      return distinct.values().stream().sorted(Comparator.comparingDouble(Hit::score).reversed()
          .thenComparing(Hit::code)).limit(settings.topK()).toList();
    } catch (AiException ex) { throw ex; }
    catch (RuntimeException ex) {
      Throwable cause = ex.getCause();
      for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause())
        if (cause instanceof AiException safe) throw safe;
      throw new AiException(AiException.Code.VECTOR_STORE_UNAVAILABLE,
          "Chưa tìm kiếm được địa danh trong dữ liệu AI. Vui lòng thử lại sau.");
    }
  }

  private boolean eligible(Document doc) {
    if (doc == null || doc.getScore() == null || !Double.isFinite(doc.getScore())
        || doc.getScore() < settings.minimumSimilarity() || doc.getScore() > 1.000001
        || doc.getText() == null || doc.getText().isBlank()) return false;
    var m = doc.getMetadata();
    if (!"public".equals(m.get("scope")) || m.containsKey("owner_id")
        || !"airport".equals(m.get("document_type"))
        || !KnowledgeDocuments.MANAGED_BY.equals(m.get("managed_by"))
        || !ai.embeddingModel().equals(m.get("embedding_model"))) return false;
    if (!(m.get("airport_code") instanceof String code) || !code.strip().matches("(?i)[A-Z]{3}")) return false;
    return m.get("source_id") instanceof String id && code.strip().equalsIgnoreCase(id.strip());
  }

  private static AiClarification missing(String field) {
    return new AiClarification(field, "Chưa xác định được sân bay đang hoạt động cho "
        + ("origin".equals(field) ? "điểm khởi hành" : "điểm đến") + ". Bạn cho biết rõ hơn thành phố hoặc sân bay nhé.");
  }
  private static AiClarification choose(String field, List<Airport> airports, boolean confirm) {
    return new AiClarification(field, confirm
        ? "Mình tìm thấy một sân bay có thể phù hợp. Bạn xác nhận giúp mình nhé."
        : "Có nhiều sân bay có thể phù hợp. Bạn chọn sân bay mình muốn nhé.", airports);
  }
  private static Instant indexedAt(Object value) {
    try { return value instanceof String text ? Instant.parse(text) : null; }
    catch (RuntimeException ex) { return null; }
  }
  private record Hit(String code, double score, String id, String title, Instant indexedAt) {}
}
