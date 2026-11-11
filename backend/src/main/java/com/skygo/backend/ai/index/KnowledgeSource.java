package com.skygo.backend.ai.index;

import java.time.Instant;
import java.util.List;
import org.springframework.ai.document.Document;

@FunctionalInterface
public interface KnowledgeSource {
  /** Complete, bounded snapshot. Throw on partial reads; never silently truncate. */
  List<Document> snapshot(Instant now);
}
