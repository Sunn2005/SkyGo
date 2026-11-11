package com.skygo.backend.ai.service;

import static com.skygo.backend.ai.AiTestSupport.vector;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;

class RetrievalEmbeddingModelTest {
  EmbeddingModel documents = mock(EmbeddingModel.class);
  EmbeddingModel queries = mock(EmbeddingModel.class);
  RetrievalEmbeddingModel model = new RetrievalEmbeddingModel(documents, queries, 768);

  @Test
  void queriesAndDocumentsUseSeparateTaskModels() {
    var document = new Document("Một tài liệu thử nghiệm.");
    when(documents.embed(document)).thenReturn(vector());
    when(queries.embed("Một câu hỏi khác cách diễn đạt?")).thenReturn(vector());
    assertEquals(768, model.embed(document).length);
    assertEquals(768, model.embed("Một câu hỏi khác cách diễn đạt?").length);
    verify(documents, never()).embed(anyString());
    verify(queries, never()).embed(any(Document.class));
  }

  @Test
  void documentBatchesUseDocumentModelAndValidateEveryVector() {
    var batch = List.of(new Document("first"), new Document("second"));
    var options = EmbeddingOptions.builder().build();
    var strategy = new TokenCountBatchingStrategy();
    when(documents.embed(batch, options, strategy)).thenReturn(List.of(vector(), vector()));
    assertEquals(2, model.embed(batch, options, strategy).size());
    verifyNoInteractions(queries);
    when(documents.embed(batch, options, strategy)).thenReturn(List.of(vector(), new float[100]));
    assertEquals(AiException.Code.COLLECTION_INCOMPATIBLE,
        assertThrows(AiException.class, () -> model.embed(batch, options, strategy)).code());
  }

  @Test
  void rejectsIncorrectDimensionsNonFiniteAndZeroVectors() {
    when(queries.embed(anyString())).thenReturn(new float[1536]);
    assertEquals(AiException.Code.COLLECTION_INCOMPATIBLE,
        assertThrows(AiException.class, () -> model.embed("query")).code());
    float[] invalid = vector();
    invalid[0] = Float.NaN;
    when(queries.embed(anyString())).thenReturn(invalid);
    assertThrows(AiException.class, () -> model.embed("query"));
    when(queries.embed(anyString())).thenReturn(new float[768]);
    assertThrows(AiException.class, () -> model.embed("query"));
  }

  @Test
  void providerFailureIsSanitized() {
    when(queries.embed(anyString())).thenThrow(new RuntimeException("api-key=do-not-expose"));
    var exception = assertThrows(AiException.class, () -> model.embed("query"));
    assertEquals(AiException.Code.EMBEDDING_UNAVAILABLE, exception.code());
    assertFalse(exception.getMessage().contains("do-not-expose"));
    assertNull(exception.getCause());
  }
}
