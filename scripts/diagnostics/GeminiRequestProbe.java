import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.skygo.backend.ai.chat.AiReadTools;
import com.skygo.backend.ai.config.AiEnvironmentPostProcessor;
import com.skygo.backend.ai.config.AiProperties;
import com.skygo.backend.ai.service.GeminiEmbeddingFailure;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Explicit manual probe only. No Spring context startup or tool execution. */
public class GeminiRequestProbe {
  public static void main(String[] args) throws Exception {
    boolean offline = args.length > 0 && args[0].equals("--offline-check");
    var environment = new StandardEnvironment();
    new AiEnvironmentPostProcessor().postProcessEnvironment(environment, null);
    var ai = Binder.get(environment).bind("skygo.ai", AiProperties.class).get();
    String key = offline ? "offline-probe-key" : environment.getProperty("GEMINI_API_KEY", "").strip();
    if (key.isEmpty()) throw new IllegalStateException("GEMINI_API_KEY is missing.");
    String modelName = offline ? "gemini-offline-probe" : args.length > 0 ? args[0] : ai.chatModel();
    System.out.println("Java: " + System.getProperty("java.version") + "; model: " + modelName);
    System.out.println("At most THREE short requests; stop at first failure. No retries or tool execution.");
    var http = HttpOptions.builder().timeout(Math.toIntExact(ai.timeout().toMillis()))
        .retryOptions(HttpRetryOptions.builder().attempts(1).build());
    com.sun.net.httpserver.HttpServer stub = null;
    var requests = new java.util.concurrent.atomic.AtomicInteger();
    if (offline) {
      if (safe("key=" + key + " https://example.test/secret", key).contains(key))
        throw new IllegalStateException("Key redaction failed.");
      stub = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      stub.createContext("/", exchange -> {
        requests.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        byte[] body = """
            {"modelVersion":"offline-test","candidates":[{"content":{"role":"model",
            "parts":[{"text":"OK"}]},"finishReason":"STOP"}],
            "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
        exchange.close();
      });
      stub.start();
      http.baseUrl("http://127.0.0.1:" + stub.getAddress().getPort());
    }
    boolean completed = false;
    try (var client = Client.builder().apiKey(key).vertexAI(false).httpOptions(http.build()).build()) {
      if (!run("1_JAVA_SDK_PLAIN", key, () -> client.models.generateContent(modelName,
          "Reply with exactly OK.", GenerateContentConfig.builder().maxOutputTokens(256).build()))) return;

      var adapter = GoogleGenAiChatModel.builder().genAiClient(client)
          .options(GoogleGenAiChatOptions.builder().model(modelName).maxOutputTokens(ai.maxOutputTokens())
              .googleSearchRetrieval(false).build())
          .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build())).build();
      if (!run("2_SPRING_AI_PLAIN", key, () -> adapter.call(new Prompt("Reply with exactly OK.",
          GoogleGenAiChatOptions.builder().model(modelName).maxOutputTokens(256).build())))) return;

      String system = new ClassPathResource("ai/chat-system.txt")
          .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
      var tools = new AiReadTools(null, null, null, null, null);
      String user = JsonMapper.builder().build().writeValueAsString(Map.of(
          "message", "Tôi muốn bay từ Sài Gòn đến Nhật Bản, hãy hỏi rõ sân bay đến trước.", "history", List.of()));
      if (!run("3_SPRING_AI_SKYGO_TOOLS", key, () -> adapter.call(new Prompt(List.of(
          new SystemMessage(system + "\nKhách đã đăng nhập: false"), new UserMessage(user)),
          GoogleGenAiChatOptions.builder().model(modelName).maxOutputTokens(ai.maxOutputTokens())
              .toolCallbacks(tools.callbacks()).build())))) return;
      System.out.println("ALL_JAVA_STAGES_OK: inspect the IDE configuration or later conversation rounds next.");
      completed = true;
    } finally {
      if (stub != null) {
        stub.stop(0);
        if (!completed || requests.get() != 3) throw new IllegalStateException("Offline diagnostic stages failed.");
        System.out.println("OFFLINE_CHECK_OK: three local stub requests only; no Google calls.");
      }
    }
  }

  private static boolean run(String stage, String key, Supplier<?> action) {
    System.out.println("Running " + stage);
    try {
      action.get();
      System.out.println(stage + "_OK");
      return true;
    } catch (RuntimeException failure) {
      System.out.println(stage + "_FAILED");
      System.out.println(GeminiEmbeddingFailure.diagnostic(failure));
      var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
      Throwable current = failure;
      for (int depth = 0; current != null && depth < 32 && seen.add(current); depth++, current = current.getCause()) {
        if (current instanceof ApiException api) {
          System.out.println("Google status: " + safe(api.status(), key));
          System.out.println("Google message: " + safe(api.message(), key));
          return false;
        }
      }
      System.out.println("No provider HTTP response. Compare Java/IDE transport settings.");
      return false;
    }
  }

  private static String safe(String value, String key) {
    String text = String.valueOf(value).replace(key, "[REDACTED]")
        .replaceAll("AIza[A-Za-z0-9_-]+", "[REDACTED]")
        .replaceAll("https?://[^\\s\\\"]+", "[URL]").replaceAll("[\\r\\n]+", " ");
    return text.substring(0, Math.min(1200, text.length()));
  }
}
