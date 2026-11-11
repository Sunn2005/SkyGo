package com.skygo.backend.ai.service;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Document batches and query strings must use the appropriate Gemini retrieval task. */
public final class RetrievalEmbeddingModel implements EmbeddingModel {
  private final EmbeddingModel documents;
  private final EmbeddingModel queries;
  private final int dimensions;

  public RetrievalEmbeddingModel(EmbeddingModel documents, EmbeddingModel queries, int dimensions) {
    this.documents = documents;
    this.queries = queries;
    this.dimensions = dimensions;
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    try {
      var response = documents.call(request);
      response.getResults().forEach(result -> check(result.getOutput()));
      return response;
    } catch (AiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw GeminiEmbeddingFailure.sanitized(ex);
    }
  }

  @Override
  public float[] embed(Document document) {
    try {
      return check(documents.embed(document));
    } catch (AiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw GeminiEmbeddingFailure.sanitized(ex);
    }
  }

  @Override
  public float[] embed(String query) {
    try {
      return check(queries.embed(query));
    } catch (AiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw GeminiEmbeddingFailure.sanitized(ex);
    }
  }

  @Override
  public List<float[]> embed(List<Document> batch, EmbeddingOptions options, BatchingStrategy strategy) {
    try {
      var vectors = documents.embed(batch, options, strategy);
      if (vectors.size() != batch.size()) throw unavailable();
      return vectors.stream().map(this::check).toList();
    } catch (AiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw GeminiEmbeddingFailure.sanitized(ex);
    }
  }

  private float[] check(float[] vector) {
    if (vector == null || vector.length != dimensions)
      throw new AiException(AiException.Code.COLLECTION_INCOMPATIBLE,
          "Số chiều embedding thực tế không khớp cấu hình. Kiểm tra model và collection.");
    double length = 0;
    for (float value : vector) {
      if (!Float.isFinite(value)) throw unavailable();
      length += (double) value * value;
    }
    if (length == 0) throw unavailable();
    return vector;
  }

  private AiException unavailable() {
    return new AiException(AiException.Code.EMBEDDING_UNAVAILABLE,
        "Không tạo được embedding. Kiểm tra kết nối, model và quyền sử dụng Gemini API.");
  }
}
