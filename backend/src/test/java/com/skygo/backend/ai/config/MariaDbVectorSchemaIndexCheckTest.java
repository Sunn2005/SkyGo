package com.skygo.backend.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MariaDbVectorSchemaIndexCheckTest {
  @Test
  void acceptsMariaDb118QuotedDistance() {
    assertTrue(MariaDbVectorSchema.hasCosineVectorIndex(
        "  PRIMARY KEY (`id`),\n  VECTOR KEY `ai_document_embedding_idx` (`embedding`) `DISTANCE`='cosine',\n"));
  }

  @Test
  void acceptsUnquotedDistance() {
    assertTrue(MariaDbVectorSchema.hasCosineVectorIndex(
        "  VECTOR KEY `ai_document_embedding_idx` (`embedding`) DISTANCE=cosine,\n"));
  }

  @Test
  void rejectsEuclideanAndMissingIndex() {
    assertFalse(MariaDbVectorSchema.hasCosineVectorIndex(
        "  VECTOR KEY `ai_document_embedding_idx` (`embedding`) `DISTANCE`='euclidean',\n"));
    assertFalse(MariaDbVectorSchema.hasCosineVectorIndex("  PRIMARY KEY (`id`)\n"));
    assertFalse(MariaDbVectorSchema.hasCosineVectorIndex(null));
  }
}
