package com.skygo.backend.ai.index;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

public interface KnowledgeIndexStore {
  /** Only points managed by this index and embedding model. */
  Map<String, String> hashes();
  void upsert(List<Document> documents);
  void delete(List<String> ids);
}
