package com.skygo.backend.ai.chat;

import static com.skygo.backend.ai.AiTestSupport.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.skygo.backend.ai.index.KnowledgeDocuments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class AiKnowledgeReaderTest {
  @Test
  void publicRagRejectsPrivateBookingsLiveFareSnapshotsAndOtherModelsEvenIfTheStoreLeaksThem() {
    var store = mock(VectorStore.class);
    var reader = new AiKnowledgeReader(store, properties());
    var normal = new HashMap<String, Object>(Map.of("scope", "public", "document_type", "baggage_policy",
        "managed_by", KnowledgeDocuments.MANAGED_BY, "embedding_model", properties().embeddingModel(), "title", "Policy"));
    normal.put("flight_id", "flight-on-a-specific-date");
    var privateMetadata = new HashMap<>(normal); privateMetadata.put("scope", "private"); privateMetadata.put("owner_id", 2L);
    var fareMetadata = new HashMap<>(normal); fareMetadata.put("document_type", "flight_fare");
    var wrongModel = new HashMap<>(normal); wrongModel.put("embedding_model", "another");
    var mislabeledOwner = new HashMap<>(normal); mislabeledOwner.put("owner_id", 5L);
    when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
        new Document("safe", normal), new Document("private", privateMetadata),
        new Document("stale price", fareMetadata), new Document("other", wrongModel), new Document("owner", mislabeledOwner)));
    var hits = reader.search("policy");
    assertThat(hits).extracting(AiKnowledgeReader.Hit::text).containsExactly("safe");
    assertThat(hits.getFirst().applicability()).containsEntry("flightId", "flight-on-a-specific-date")
        .doesNotContainKey("owner_id");
    var request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(store).similaritySearch(request.capture());
    assertThat(request.getValue().getFilterExpression().toString()).contains("scope", "public", "managed_by", "document_type", "embedding_model");
  }
}
