package com.skygo.backend.ai.chat;

import static com.skygo.backend.ai.AiTestSupport.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.skygo.backend.ai.chat.contract.AiChatRequest;
import com.skygo.backend.ai.chat.contract.AiChatResponse.Status;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/** Real Spring AI + Google SDK boundary, with HTTP confined to an in-process stub. */
class GeminiChatAdapterTest {
  @Test
  void sendsProviderSpecificOptionsAndReceivesAFunctionCallWithoutRealGeminiOrDatabase() throws Exception {
    var requests = new AtomicInteger();
    var posted = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      posted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] reply = """
          {"modelVersion":"gemini-test-version","candidates":[{"content":{"role":"model","parts":[
            {"functionCall":{"name":"askForDetails","args":{"fields":["destination"]}}}
          ]},"finishReason":"STOP"}],
          "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, reply.length);
      try (var body = exchange.getResponseBody()) { body.write(reply); }
      exchange.close();
    });
    server.start();
    try (var client = Client.builder().apiKey("fake-test-key").vertexAI(false)
        .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .timeout(3000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()) {
      var model = GoogleGenAiChatModel.builder().genAiClient(client)
          .options(GoogleGenAiChatOptions.builder().model(properties().chatModel()).build()).build();
      // Preserve adapter failures in the test report; the service intentionally
      // removes provider causes from exceptions returned to HTTP clients.
      var observed = new org.springframework.ai.chat.model.ChatModel() {
        @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() { return model.getOptions(); }
        @Override public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
          try { return model.call(prompt); }
          catch (RuntimeException failure) { throw new AssertionError("Local Gemini adapter stub failed", failure); }
        }
      };
      var repository = mock(AiReadRepository.class);
      var knowledge = mock(AiKnowledgeReader.class);
      var validator = mock(Validator.class);
      when(validator.validate(any())).thenReturn(Set.of());
      var clock = Clock.systemUTC();
      var airportResolver = new AiAirportResolver(mock(org.springframework.ai.vectorstore.VectorStore.class),
          properties(), new AiAirportProperties(5, .65, .85, .10));
      var service = new AiChatService(observed, repository, knowledge, airportResolver, validator, properties(),
          new AiChatProperties(2, 4, 4), new AiChatGate(clock, 4), clock, "Use tools only.");
      var result = service.ask(new AiChatRequest("Từ Sài Gòn đi Nhật Bản", List.of()), null);
      assertThat(result.status()).isEqualTo(Status.NEEDS_CLARIFICATION);
      assertThat(result.clarifications()).extracting(c -> c.field()).containsExactly("destination");
      assertThat(requests.get()).isEqualTo(1);
      assertThat(posted.get()).contains("askForDetails", "searchFlights");
      verifyNoInteractions(repository, knowledge);
    } finally { server.stop(0); }
  }
}
