package com.skygo.backend.ai.chat;

import com.skygo.backend.ai.config.AiProperties;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "skygo.ai.enabled", havingValue = "true")
@EnableConfigurationProperties({AiChatProperties.class, AiAirportProperties.class})
public class AiChatConfiguration {
  @Bean
  AiReadRepository aiReadRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
    return new AiReadRepository(jdbc, clock);
  }
  @Bean
  AiKnowledgeReader aiKnowledgeReader(VectorStore vectors, AiProperties ai) {
    return new AiKnowledgeReader(vectors, ai);
  }
  @Bean
  AiAirportResolver aiAirportResolver(VectorStore vectors, AiProperties ai, AiAirportProperties settings) {
    return new AiAirportResolver(vectors, ai, settings);
  }
  @Bean
  AiChatGate aiChatGate(Clock clock, AiChatProperties settings) {
    return new AiChatGate(clock, settings.modelCallsPerMinute());
  }
  @Bean
  AiChatService aiChatService(GoogleGenAiChatModel model, AiReadRepository repository,
      AiKnowledgeReader knowledge, AiAirportResolver airportResolver, Validator validator, AiProperties ai, AiChatProperties settings,
      AiChatGate gate, Clock clock) throws IOException {
    return new AiChatService(model, repository, knowledge, airportResolver, validator, ai, settings, gate, clock,
        new ClassPathResource("ai/chat-system.txt").getContentAsString(StandardCharsets.UTF_8));
  }
}
