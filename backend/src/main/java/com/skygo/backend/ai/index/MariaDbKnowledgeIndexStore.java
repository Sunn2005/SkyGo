package com.skygo.backend.ai.index;

import com.skygo.backend.ai.config.AiProperties;
import com.skygo.backend.ai.service.AiException;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Only SkyGo-owned rows for this model may participate in reconciliation. */
public final class MariaDbKnowledgeIndexStore implements KnowledgeIndexStore {
  private final JdbcTemplate jdbc;
  private final VectorStore vectors;
  private final AiProperties ai;
  private final AiIndexProperties settings;
  private final PlatformTransactionManager transactions;

  public MariaDbKnowledgeIndexStore(JdbcTemplate jdbc, VectorStore vectors, AiProperties ai,
      AiIndexProperties settings, PlatformTransactionManager transactions) {
    this.jdbc = jdbc; this.vectors = vectors; this.ai = ai; this.settings = settings; this.transactions = transactions;
  }

  @Override public Map<String,String> hashes() {
    var tx = new TransactionTemplate(transactions);
    tx.setReadOnly(true);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    tx.setTimeout(60);
    try {
      return tx.execute(status -> {
        var result = new HashMap<String,String>();
        jdbc.query("""
            SELECT CAST(id AS CHAR) AS id, JSON_VALUE(metadata,'$.content_hash') AS content_hash
            FROM ai_document
            WHERE JSON_VALUE(metadata,'$.managed_by')=? AND JSON_VALUE(metadata,'$.embedding_model')=?
            LIMIT ?
            """, rs -> {
          String id = rs.getString("id"), hash = rs.getString("content_hash");
          if (hash == null || !hash.matches("[a-f0-9]{64}") || result.put(id,hash) != null
              || result.size() > settings.maxDocuments()) throw unavailable();
        }, KnowledgeDocuments.MANAGED_BY, ai.embeddingModel(), settings.maxDocuments()+1);
        return Map.copyOf(result);
      });
    } catch (AiException ex) { throw ex; }
    catch (RuntimeException ex) { throw unavailable(); }
  }

  @Override public void upsert(List<Document> documents) {
    if (documents.isEmpty()) return;
    documents.forEach(doc -> StoredKnowledgeDocument.validate(doc, ai));
    // Stable IDs must not overwrite a row outside this writer's ownership boundary.
    Integer conflicts = new NamedParameterJdbcTemplate(jdbc).queryForObject("""
        SELECT COUNT(*) FROM ai_document WHERE id IN (:ids)
          AND (COALESCE(JSON_VALUE(metadata,'$.managed_by'),'')<>:manager
            OR COALESCE(JSON_VALUE(metadata,'$.embedding_model'),'')<>:model)
        """, Map.of("ids",documents.stream().map(Document::getId).toList(),
            "manager",KnowledgeDocuments.MANAGED_BY,"model",ai.embeddingModel()),Integer.class);
    if (conflicts == null || conflicts > 0) throw unavailable();
    // No SQL transaction is held while the external embedding API is called.
    // Successful individual writes are resumable by stable ID + content_hash.
    vectors.add(documents);
  }

  @Override public void delete(List<String> ids) {
    if (ids.isEmpty()) return;
    ids.forEach(UUID::fromString);
    var tx = new TransactionTemplate(transactions);
    tx.setTimeout(60);
    try {
      tx.executeWithoutResult(status -> new NamedParameterJdbcTemplate(jdbc).update("""
          DELETE FROM ai_document WHERE id IN (:ids)
            AND JSON_VALUE(metadata,'$.managed_by')=:manager
            AND JSON_VALUE(metadata,'$.embedding_model')=:model
          """, Map.of("ids",ids,"manager",KnowledgeDocuments.MANAGED_BY,"model",ai.embeddingModel())));
    } catch (RuntimeException ex) { throw unavailable(); }
  }

  private static AiException unavailable() {
    return new AiException(AiException.Code.VECTOR_STORE_UNAVAILABLE,
        "Không đọc/ghi được toàn bộ chỉ mục MariaDB; dừng đồng bộ để tránh xóa nhầm dữ liệu.");
  }
}
