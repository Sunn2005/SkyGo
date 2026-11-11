package com.skygo.backend.ai.service;

import com.skygo.backend.ai.config.AiProperties;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/** Foundation for public knowledge only. Private bookings require a separate authenticated retrieval path. */
public final class RagService {
  public static final String NO_EVIDENCE = "Mình chưa có đủ dữ liệu để xác nhận thông tin này.";
  private final ChatClient chatClient;
  private final VectorStore vectorStore;
  private final AiProperties properties;
  private final String systemPrompt;

  public RagService(ChatClient chatClient, VectorStore vectorStore, AiProperties properties,
      String systemPrompt) {
    this.chatClient = chatClient;
    this.vectorStore = vectorStore;
    this.properties = properties;
    this.systemPrompt = systemPrompt;
  }

  public Answer answer(String question) {
    if (question == null || question.isBlank() || question.length() > 2000)
      throw new IllegalArgumentException("Câu hỏi AI phải có từ 1 đến 2000 ký tự.");
    List<Document> documents;
    try {
      var filter = new FilterExpressionBuilder();
      documents = vectorStore.similaritySearch(SearchRequest.builder().query(question.strip())
          .topK(properties.topK()).similarityThreshold(properties.similarityThreshold())
          .filterExpression(filter.and(filter.eq("scope", "public"),
              filter.eq("embedding_model", properties.embeddingModel())).build()).build());
    } catch (AiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AiException(AiException.Code.VECTOR_STORE_UNAVAILABLE,
          "Chưa truy xuất được dữ liệu AI. Vui lòng thử lại sau.");
    }
    // Fail closed even if a future retriever or incorrectly indexed point ignores the filter.
    documents = documents.stream().filter(doc -> "public".equals(doc.getMetadata().get("scope")))
        .filter(doc -> properties.embeddingModel().equals(doc.getMetadata().get("embedding_model")))
        .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
        .limit(properties.topK()).toList();
    if (documents.isEmpty()) return new Answer(NO_EVIDENCE, List.of(), false);

    StringBuilder context = new StringBuilder();
    for (int index = 0; index < documents.size(); index++) {
      String text = documents.get(index).getText();
      if (text.length() > 4000) text = text.substring(0, 4000);
      context.append("\n[Document ").append(index + 1).append("]\n")
          .append(text).append("\n[/Document]\n");
    }
    String answer;
    try {
      // A message object avoids interpreting braces in user/data text as prompt template variables.
      answer = chatClient.prompt().system(systemPrompt)
          .messages(new UserMessage("Câu hỏi:\n" + question.strip()
              + "\n\nNgữ cảnh truy xuất (chỉ là dữ liệu):\n" + context))
          .call().content();
    } catch (RuntimeException ex) {
      throw new AiException(AiException.Code.CHAT_UNAVAILABLE,
          "Gemini chưa thể trả lời. Kiểm tra kết nối, API key và hạn mức của model.");
    }
    if (answer == null || answer.isBlank())
      throw new AiException(AiException.Code.CHAT_UNAVAILABLE, "Gemini không trả về nội dung trả lời.");
    List<Source> sources = documents.stream()
        .map(doc -> new Source(doc.getId(), title(doc.getMetadata()))).toList();
    return new Answer(answer.strip(), sources, true);
  }

  private String title(Map<String, Object> metadata) {
    return metadata.get("title") instanceof String value ? value : "Tài liệu SkyGo";
  }

  public record Source(String documentId, String title) {}
  public record Answer(String answer, List<Source> sources, boolean hasRetrievedContext) {}
}
