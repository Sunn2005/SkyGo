package com.skygo.backend.ai.config;

import com.skygo.backend.ai.service.AiException;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only compatibility check. No embeddings, DDL, table drops or automatic rebuilding. */
public final class MariaDbVectorSchema {
  public static final String TABLE = "ai_document";
  private MariaDbVectorSchema() {}

  public static void validate(JdbcTemplate jdbc, AiProperties ai) {
    try {
      var settings = jdbc.queryForMap("SELECT embedding_model,dimensions,distance_type FROM ai_vector_schema WHERE id=1");
      if (!ai.embeddingModel().equals(settings.get("embedding_model"))
          || ((Number) settings.get("dimensions")).intValue() != ai.embeddingDimensions()
          || !"COSINE".equals(settings.get("distance_type"))) throw incompatible();
      String column = jdbc.queryForObject("""
          SELECT COLUMN_TYPE FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_document' AND COLUMN_NAME='embedding'
          """, String.class);
      if (!("vector(" + ai.embeddingDimensions() + ")").equalsIgnoreCase(column)) throw incompatible();
      String ddl = jdbc.queryForObject("SHOW CREATE TABLE ai_document", (rs, row) -> rs.getString(2));
      if (!hasCosineVectorIndex(ddl)) throw incompatible();
    } catch (AiException ex) { throw ex; }
    catch (RuntimeException ex) {
      throw new AiException(AiException.Code.VECTOR_STORE_UNAVAILABLE,
          "Chưa đọc được bảng vector MariaDB. Cần MariaDB 11.7.1+ và migration V12 đã chạy thành công.");
    }
  }

  /** MariaDB 11.8 prints {@code `DISTANCE`='cosine'} (quoted); older output was {@code DISTANCE=cosine}. */
  static boolean hasCosineVectorIndex(String ddl) {
    return ddl != null && ddl.lines()
        .map(line -> line.toLowerCase(Locale.ROOT).replaceAll("[`'\"\\s]", ""))
        .anyMatch(line -> line.startsWith("vectorkey") && line.contains("(embedding)") && line.contains("distance=cosine"));
  }

  private static AiException incompatible() {
    return new AiException(AiException.Code.COLLECTION_INCOMPATIBLE,
        "Model, số chiều hoặc cosine index không khớp bảng vector MariaDB. Không tự ghi đè; cần migration tương ứng trước khi đổi cấu hình embedding.");
  }
}
