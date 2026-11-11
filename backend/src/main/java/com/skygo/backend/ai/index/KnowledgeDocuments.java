package com.skygo.backend.ai.index;

import com.skygo.backend.ai.config.AiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.ai.document.Document;

/** Stable IDs permit upsert; stable hashes avoid paying for unchanged embeddings. */
public final class KnowledgeDocuments {
  public static final String MANAGED_BY = "skygo-db-v1";

  private KnowledgeDocuments() {}

  public static Document create(AiProperties ai, String type, String key, String title,
      String text, Map<String, Object> attributes, Long ownerId, Instant observedAt) {
    var metadata = new TreeMap<String, Object>(attributes);
    metadata.put("managed_by", MANAGED_BY);
    metadata.put("document_type", type);
    metadata.put("source_id", key);
    metadata.put("title", title);
    metadata.put("scope", ownerId == null ? "public" : "private");
    metadata.put("embedding_model", ai.embeddingModel());
    metadata.put("schema_version", 1);
    if (ownerId != null) {
      if (ownerId <= 0) throw new IllegalArgumentException("Invalid document owner.");
      metadata.put("owner_id", ownerId);
    } else if (metadata.containsKey("owner_id")) {
      throw new IllegalArgumentException("A public document cannot have an owner.");
    }
    // Hash all meaningful fields, including visibility/ownership, but not the observation time.
    var canonical = new StringBuilder();
    append(canonical, text);
    metadata.forEach((name, value) -> {
      append(canonical, name);
      append(canonical, value.getClass().getName());
      append(canonical, value.toString());
    });
    metadata.put("content_hash", sha256(canonical.toString()));
    metadata.put("indexed_at", observedAt.toString());
    String id = UUID.nameUUIDFromBytes((MANAGED_BY + ":" + type + ":" + key)
        .getBytes(StandardCharsets.UTF_8)).toString();
    return Document.builder().id(id).text(text).metadata(metadata).build();
  }

  private static void append(StringBuilder result, String value) {
    result.append(value.length()).append(':').append(value);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime.");
    }
  }
}
