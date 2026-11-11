package com.skygo.backend.ai.service;

import static com.skygo.backend.ai.AiTestSupport.properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagServiceTest {
  ChatModel chat = mock(ChatModel.class);
  VectorStore store = mock(VectorStore.class);
  RagService service = new RagService(ChatClient.builder(chat).build(), store, properties(),
      "Chỉ trả lời theo dữ liệu truy xuất. Trả lời bằng tiếng Việt.");

  @BeforeEach
  void modelDefaults() {
    when(chat.getOptions()).thenReturn(ChatOptions.builder().model("test-model").build());
  }

  @Test
  void noEvidenceReturnsAnExplicitAnswerWithoutCallingTheModel() {
    when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    var answer = service.answer("Giá vé bao nhiêu?");
    assertEquals(RagService.NO_EVIDENCE, answer.answer());
    assertFalse(answer.hasRetrievedContext());
    verifyNoInteractions(chat);
  }

  @Test
  void retrievalHasMandatoryPublicAndEmbeddingFiltersAndRejectsLeakedPrivateDocuments() {
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(document("private-secret", "private", properties().embeddingModel())));
    assertFalse(service.answer("Xem đơn đặt vé").hasRetrievedContext());
    var request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(store).similaritySearch(request.capture());
    String filter = request.getValue().getFilterExpression().toString();
    assertTrue(filter.contains("scope"));
    assertTrue(filter.contains("public"));
    assertTrue(filter.contains("embedding_model"));
    verifyNoInteractions(chat);
  }

  @Test
  void onlyRetrievedPublicDocumentsReachThePromptAndSources() {
    var publicDoc = document("Thông tin mẫu {không phải biến template}", "public", properties().embeddingModel());
    when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(publicDoc,
        document("private-secret", "private", properties().embeddingModel()),
        document("wrong-model", "public", "other-model")));
    when(chat.call(any(Prompt.class))).thenReturn(response("Thông tin mẫu [1]"));
    var answer = service.answer("Câu hỏi có {dấu ngoặc}?");
    assertTrue(answer.hasRetrievedContext());
    assertEquals(List.of(new RagService.Source(publicDoc.getId(), "Tài liệu kiểm thử")), answer.sources());
    var prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chat).call(prompt.capture());
    String contents = prompt.getValue().getContents();
    assertTrue(contents.contains("{dấu ngoặc}"));
    assertTrue(contents.contains("{không phải biến template}"));
    assertFalse(contents.contains("private-secret"));
    assertFalse(contents.contains("wrong-model"));
  }

  @Test
  void invalidQuestionsNeverReachExternalServices() {
    for (String question : new String[]{null, " ", "a".repeat(2001)})
      assertThrows(IllegalArgumentException.class, () -> service.answer(question));
    verifyNoInteractions(store, chat);
  }

  @Test
  void vectorStoreAndProviderFailuresAreReportedWithoutRawDetails() {
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new RuntimeException("private database details"));
    var retrievalFailure = assertThrows(AiException.class, () -> service.answer("question"));
    assertEquals(AiException.Code.VECTOR_STORE_UNAVAILABLE, retrievalFailure.code());
    assertNull(retrievalFailure.getCause());
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(document("data", "public", properties().embeddingModel())));
    when(chat.call(any(Prompt.class))).thenThrow(new RuntimeException("private provider key"));
    var chatFailure = assertThrows(AiException.class, () -> service.answer("question"));
    assertEquals(AiException.Code.CHAT_UNAVAILABLE, chatFailure.code());
    assertNull(chatFailure.getCause());
  }

  private Document document(String text, String scope, String model) {
    return new Document(text, Map.of("scope", scope, "embedding_model", model, "title", "Tài liệu kiểm thử"));
  }

  private ChatResponse response(String content) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
  }
}
