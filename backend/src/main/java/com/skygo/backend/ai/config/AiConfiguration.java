package com.skygo.backend.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.skygo.backend.ai.service.AiException;
import com.skygo.backend.ai.service.RagService;
import com.skygo.backend.ai.service.RetrievalEmbeddingModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore.MariaDBDistanceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "skygo.ai.enabled", havingValue = "true")
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
  @Bean(destroyMethod = "close")
  Client skygoGenAiClient(Environment environment, AiProperties properties) {
    String key = environment.getProperty("GEMINI_API_KEY", "").strip();
    if (key.isEmpty())
      throw new AiException(AiException.Code.INVALID_CONFIGURATION,
          "Module AI đã bật nhưng thiếu GEMINI_API_KEY trong môi trường hoặc .env.");
    return Client.builder().apiKey(key).vertexAI(false)
        .httpOptions(HttpOptions.builder().timeout(Math.toIntExact(properties.timeout().toMillis()))
            .retryOptions(HttpRetryOptions.builder().attempts(3)
                .httpStatusCodes(408, 429, 500, 502, 503, 504)
                .initialDelay(15.0).maxDelay(30.0).expBase(2.0).jitter(0.2).build()).build())
        .build();
  }

  @Bean
  GoogleGenAiChatModel skygoChatModel(@Qualifier("skygoChatGenAiClient") Client skygoChatGenAiClient, AiProperties properties) {
    return GoogleGenAiChatModel.builder().genAiClient(skygoChatGenAiClient)
        // The SDK and Spring AI each have a retry layer. Bound both so one
        // model call counted by AiChatGate cannot generate hidden retries.
        .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()))
        .options(GoogleGenAiChatOptions.builder().model(properties.chatModel())
            .maxOutputTokens(properties.maxOutputTokens()).googleSearchRetrieval(false).build())
        .build();
  }

  @Bean(destroyMethod = "close")
  Client skygoChatGenAiClient(Environment environment, AiProperties properties) {
    String key = environment.getProperty("GEMINI_API_KEY", "").strip();
    if (key.isEmpty()) throw new AiException(AiException.Code.INVALID_CONFIGURATION,
        "Module AI đã bật nhưng thiếu GEMINI_API_KEY.");
    // Interactive requests fail explicitly instead of multiplying scarce chat quota through SDK retries.
    // The embedding client keeps its independent retry policy for indexing.
    return Client.builder().apiKey(key).vertexAI(false)
        .httpOptions(HttpOptions.builder().timeout(Math.toIntExact(properties.timeout().toMillis()))
            .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
  }

  @Bean
  RetrievalEmbeddingModel skygoEmbeddingModel(@Qualifier("skygoGenAiClient") Client skygoGenAiClient, AiProperties properties) {
    var connection = GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(skygoGenAiClient).build();
    var documents = new GoogleGenAiTextEmbeddingModel(connection,
        GoogleGenAiTextEmbeddingOptions.builder().model(properties.embeddingModel())
            .dimensions(properties.embeddingDimensions()).taskType(TaskType.RETRIEVAL_DOCUMENT)
            .autoTruncate(false).build());
    var queries = new GoogleGenAiTextEmbeddingModel(connection,
        GoogleGenAiTextEmbeddingOptions.builder().model(properties.embeddingModel())
            .dimensions(properties.embeddingDimensions()).taskType(TaskType.RETRIEVAL_QUERY)
            .autoTruncate(false).build());
    return new RetrievalEmbeddingModel(documents, queries, properties.embeddingDimensions());
  }

  @Bean
  @DependsOnDatabaseInitialization
  MariaDBVectorStore skygoVectorStore(JdbcTemplate jdbc,
      RetrievalEmbeddingModel skygoEmbeddingModel, AiProperties properties) {
    MariaDbVectorSchema.validate(jdbc, properties);
    return MariaDBVectorStore.builder(jdbc, skygoEmbeddingModel)
        .vectorTableName(MariaDbVectorSchema.TABLE).dimensions(properties.embeddingDimensions())
        .distanceType(MariaDBDistanceType.COSINE).initializeSchema(false)
        .removeExistingVectorStoreTable(false).schemaValidation(true).build();
  }

  @Bean
  RagService ragService(GoogleGenAiChatModel skygoChatModel, VectorStore skygoVectorStore,
      AiProperties properties) throws IOException {
    String prompt = new ClassPathResource("ai/rag-system.txt").getContentAsString(StandardCharsets.UTF_8);
    return new RagService(ChatClient.builder(skygoChatModel).build(), skygoVectorStore, properties, prompt);
  }
}
