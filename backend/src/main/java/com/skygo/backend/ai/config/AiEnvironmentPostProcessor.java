package com.skygo.backend.ai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** Optional, AI-only dotenv fallback. OS/IDE variables and application config take precedence. */
public final class AiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    Path cwd = Path.of("").toAbsolutePath();
    Map<String, Object> values = readAiVariables(envPaths(cwd));
    environment.getPropertySources().addLast(new MapPropertySource("skygoAiDotenv", values));
    try {
      var defaults = PropertiesLoaderUtils.loadProperties(new ClassPathResource("skygo-ai.properties"));
      environment.getPropertySources().addLast(new PropertiesPropertySource("skygoAiDefaults", defaults));
    } catch (IOException ex) {
      throw new IllegalStateException("Không đọc được cấu hình mặc định module AI.");
    }
  }

  static Map<String, Object> readAiVariables(List<Path> paths) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (Path path : paths) {
      if (!Files.isRegularFile(path)) continue;
      try {
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
          String line = raw.replace("\uFEFF", "").strip();
          if (line.startsWith("export ")) line = line.substring(7).strip();
          int equals = line.indexOf('=');
          if (equals < 1 || line.startsWith("#")) continue;
          String key = line.substring(0, equals).strip();
          if (!key.equals("GEMINI_API_KEY") && !key.startsWith("SKYGO_AI_")) continue;
          String value = line.substring(equals + 1).strip();
          if (value.startsWith("\"") || value.startsWith("'")) {
            int closing = value.indexOf(value.charAt(0), 1);
            if (closing < 1 || (!value.substring(closing + 1).strip().isEmpty()
                && !value.substring(closing + 1).strip().startsWith("#")))
              throw new IllegalStateException("Giá trị AI trong .env có dấu nháy không hợp lệ.");
            value = value.substring(1, closing);
          } else {
            int comment = value.indexOf(" #");
            if (comment >= 0) value = value.substring(0, comment).stripTrailing();
          }
          values.put(key, value);
        }
      } catch (IOException ex) {
        // Never include file contents, API keys or the provider's request in an exception.
        throw new IllegalStateException("Không đọc được file .env của module AI.");
      }
    }
    return values;
  }

  static List<Path> envPaths(Path cwd) {
    if (cwd.getFileName() != null && cwd.getFileName().toString().equals("backend"))
      return List.of(cwd.getParent().resolve(".env"), cwd.resolve(".env"));
    return List.of(cwd.resolve(".env"), cwd.resolve("backend/.env"));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
