package com.skygo.backend.ai.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class AiEnvironmentPostProcessorTest {
  @TempDir Path directory;

  @Test
  void loadsOnlyAiSettingsAndHandlesQuotesCommentsBomAndExport() throws Exception {
    Path env = directory.resolve(".env");
    Files.writeString(env, """
        \uFEFFGEMINI_API_KEY="fake-test-key" # local only
        DB_PASSWORD=not-an-ai-setting
        export SKYGO_AI_ENABLED=true
        SKYGO_AI_CHAT_MODEL='test-model'
        SKYGO_AI_TOP_K=3 # a comment
        # GEMINI_API_KEY=ignored
        """);
    var values = AiEnvironmentPostProcessor.readAiVariables(List.of(env));
    assertEquals("fake-test-key", values.get("GEMINI_API_KEY"));
    assertEquals("true", values.get("SKYGO_AI_ENABLED"));
    assertEquals("test-model", values.get("SKYGO_AI_CHAT_MODEL"));
    assertEquals("3", values.get("SKYGO_AI_TOP_K"));
    assertFalse(values.containsKey("DB_PASSWORD"));
  }

  @Test
  void rootAndBackendWorkingDirectoriesUseTheSameEnvFilePrecedence() {
    Path root = directory.resolve("SkyGo");
    assertEquals(List.of(root.resolve(".env"), root.resolve("backend/.env")),
        AiEnvironmentPostProcessor.envPaths(root));
    assertEquals(AiEnvironmentPostProcessor.envPaths(root),
        AiEnvironmentPostProcessor.envPaths(root.resolve("backend")));
  }

  @Test
  void optionalMissingFilesAndBackendOverrideWork() throws Exception {
    Path root = directory.resolve("root.env");
    Path backend = directory.resolve("backend.env");
    Files.writeString(root, "SKYGO_AI_TOP_K=2");
    Files.writeString(backend, "SKYGO_AI_TOP_K=5");
    assertEquals("5", AiEnvironmentPostProcessor.readAiVariables(
        List.of(directory.resolve("missing"), root, backend)).get("SKYGO_AI_TOP_K"));
    assertTrue(AiEnvironmentPostProcessor.readAiVariables(List.of(directory.resolve("missing"))).isEmpty());
  }

  @Test
  void ideAndSystemSettingsOverrideDotenvFallbackAndDefaultsLoad() {
    var environment = new MockEnvironment().withProperty("GEMINI_API_KEY", "ide-test-key")
        .withProperty("SKYGO_AI_CHAT_MODEL", "ide-model");
    new AiEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
    assertEquals("ide-test-key", environment.getProperty("GEMINI_API_KEY"));
    assertEquals("ide-model", environment.getProperty("skygo.ai.chat-model"));
    assertNotNull(environment.getProperty("skygo.ai.embedding-dimensions"));
  }
}
