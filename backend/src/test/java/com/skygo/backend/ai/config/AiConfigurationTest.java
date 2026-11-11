package com.skygo.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static com.skygo.backend.ai.AiTestSupport.properties;

import com.skygo.backend.ai.service.AiException;
import com.skygo.backend.ai.service.RagService;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.skygo.backend.ai.index.AiIndexConfiguration;
import com.skygo.backend.ai.index.KnowledgeIndexer;
import org.springframework.ai.vectorstore.VectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;

class AiConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(AiConfiguration.class, AiIndexConfiguration.class);

  @Test
  void configuredChatModelMakesOnlyOneHttpAttemptOnProviderUnavailable() throws Exception {
    var attempts = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      attempts.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      byte[] body = "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"Stub unavailable\"}}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(503, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
      exchange.close();
    });
    server.start();
    try (var client = Client.builder().apiKey("fake-test-key").vertexAI(false)
        .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .timeout(3000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()) {
      var model = new AiConfiguration().skygoChatModel(client, properties());
      assertThrows(RuntimeException.class, () -> model.call(new Prompt("test", GoogleGenAiChatOptions.builder()
          .model(properties().chatModel()).maxOutputTokens(256).build())));
      assertThat(attempts.get()).isEqualTo(1);
    } finally { server.stop(0); }
  }

  @Test
  void googleModelsCanBeWiredLocallyWithoutRequestingCredentialsFromGoogleCloud() {
    var configuration = new AiConfiguration();
    var settings = properties();
    try (var client = configuration.skygoGenAiClient(
        new MockEnvironment().withProperty("GEMINI_API_KEY", "fake-test-key"), settings)) {
      assertThat(configuration.skygoEmbeddingModel(client, settings).dimensions()).isEqualTo(768);
      assertThat(configuration.skygoChatModel(client, settings).getOptions().getModel())
          .isEqualTo(settings.chatModel());
    }
  }

  @Test
  void disabledAiStartsWithoutKeyVectorStoreOrModelBeans() {
    runner.withPropertyValues("skygo.ai.enabled=false", "skygo.ai.index.enabled=true", "GEMINI_API_KEY=").run(context -> {
      assertThat(context).hasNotFailed().doesNotHaveBean(RagService.class)
          .doesNotHaveBean(EmbeddingModel.class).doesNotHaveBean(VectorStore.class)
          .doesNotHaveBean(KnowledgeIndexer.class);
    });
  }

  @Test
  void enabledAiWithoutKeyFailsClearlyBeforeAnyNetworkRequest() {
    runner.withPropertyValues("skygo.ai.enabled=true", "GEMINI_API_KEY=").run(context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(AiException.class);
      assertThat(context.getStartupFailure()).hasStackTraceContaining("thiếu GEMINI_API_KEY");
    });
  }

  @Test
  void embeddingSettingsAreExplicitAndDoNotProbeGoogleForDimensions() {
    var first = properties();
    var dimensionChange = properties("skygo.ai.embedding-dimensions=1536");
    var modelChange = properties("skygo.ai.embedding-model=another-model");
    assertThat(first.embeddingDimensions()).isEqualTo(768);
    assertThat(dimensionChange.embeddingDimensions()).isEqualTo(1536);
    assertThat(modelChange.embeddingModel()).isNotEqualTo(first.embeddingModel());
  }

  @Test
  void realSpringBootstrapLoadsAiDefaultsWhileAiIsDisabled() {
    var application = new SpringApplication(DisabledApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    try (var context = application.run("--skygo.ai.enabled=false", "--spring.main.banner-mode=off")) {
      assertThat(context.containsBean("ragService")).isFalse();
      assertThat(context.getEnvironment().getProperty("skygo.ai.chat-model")).isNotBlank();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import(AiConfiguration.class)
  static class DisabledApplication {}
}
