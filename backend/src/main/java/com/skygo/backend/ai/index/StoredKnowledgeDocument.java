package com.skygo.backend.ai.index;

import com.skygo.backend.ai.config.AiProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.ai.document.Document;

/** Shared validation for indexing and importing already embedded documents. */
public final class StoredKnowledgeDocument {
  private StoredKnowledgeDocument() {}

  public static void validate(Document doc, AiProperties ai) {
    if (doc == null || doc.getText() == null || doc.getText().isBlank()
        || doc.getText().getBytes(StandardCharsets.UTF_8).length > 65535) throw invalid();
    var m = doc.getMetadata();
    if (!KnowledgeDocuments.MANAGED_BY.equals(m.get("managed_by"))
        || !ai.embeddingModel().equals(m.get("embedding_model"))
        || !(m.get("content_hash") instanceof String h) || !h.matches("[a-f0-9]{64}")
        || !(m.get("document_type") instanceof String type) || type.isBlank()
        || !(m.get("source_id") instanceof String source) || source.isBlank()
        || !(m.get("schema_version") instanceof Number schema) || schema.doubleValue()!=1d) throw invalid();
    String expected = UUID.nameUUIDFromBytes((KnowledgeDocuments.MANAGED_BY+":"+type+":"+source)
        .getBytes(StandardCharsets.UTF_8)).toString();
    if (!expected.equals(doc.getId())) throw invalid();
    if ("public".equals(m.get("scope"))) {
      if (m.containsKey("owner_id")) throw invalid();
    } else if ("private".equals(m.get("scope"))) {
      if (!validOwner(m.get("owner_id"))) throw invalid();
    } else throw invalid();
    try { Instant.parse((String)m.get("indexed_at")); }
    catch (RuntimeException ex) { throw invalid(); }
  }

  public static void validateVector(float[] vector, int dimensions) {
    if (vector == null || vector.length!=dimensions) throw invalid();
    double norm=0;
    for (float v:vector) { if (!Float.isFinite(v)) throw invalid(); norm+=(double)v*v; }
    if (norm==0) throw invalid();
  }

  private static boolean validOwner(Object value) {
    if (value instanceof Number owner) return owner.longValue()>0 && owner.doubleValue()==owner.longValue();
    // The previous Qdrant payload writer serialized Java Long metadata as strings.
    // Preserve those exact metadata/hash values while validating the same positive ID domain.
    if (value instanceof String text && text.matches("[1-9][0-9]{0,18}")) {
      try { return Long.parseLong(text)>0; } catch (NumberFormatException ex) { return false; }
    }
    return false;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid or incompatible SkyGo vector document. No source content is logged.");
  }
}
