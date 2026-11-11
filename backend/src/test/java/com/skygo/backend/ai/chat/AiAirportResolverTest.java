package com.skygo.backend.ai.chat;

import static com.skygo.backend.ai.AiTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.skygo.backend.ai.chat.contract.AiChatResponse.Airport;
import com.skygo.backend.ai.service.AiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class AiAirportResolverTest {
  static final Airport SGN = new Airport("SGN", "Sân bay quốc tế Tân Sơn Nhất", "TP. Hồ Chí Minh", "Việt Nam", "Asia/Ho_Chi_Minh");
  static final Airport PQC = new Airport("PQC", "Sân bay quốc tế Phú Quốc", "Phú Quốc", "Việt Nam", "Asia/Ho_Chi_Minh");
  static final List<Airport> CATALOG = List.of(SGN, PQC);
  final VectorStore vectors = mock(VectorStore.class);
  final AiAirportProperties settings = new AiAirportProperties(5, .65, .85, .10);
  final AiAirportResolver resolver = new AiAirportResolver(vectors, properties(), settings);

  @Test
  void exactCodesNormalizedCitiesAndAliasesSkipEmbeddingCompletely() {
    var session = resolver.newSession();
    for (String value : List.of("SGN", "Sài Gòn", "thành phố Hồ Chí Minh", "TP.HCM"))
      assertThat(session.resolve(value, "origin", CATALOG)).isEqualTo(SGN);
    assertThat(session.resolve("sân bay Phú Quốc", "destination", CATALOG)).isEqualTo(PQC);
    verifyNoInteractions(vectors);
  }

  @Test
  void strongSemanticHitUsesOnlyCurrentCatalogIdentityAndFiltersAirportDocuments() {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(airportDocument("PQC", .95)));
    var session = resolver.newSession();
    assertThat(session.resolve("đảo ngọc", "destination", CATALOG)).isSameAs(PQC);
    assertThat(session.sources()).extracting(s -> s.id()).containsExactly("airport-PQC");
    var request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectors).similaritySearch(request.capture());
    assertThat(request.getValue().getQuery()).contains("đảo ngọc");
    assertThat(request.getValue().getTopK()).isEqualTo(5);
    assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(.65);
    assertThat(request.getValue().getFilterExpression().toString())
        .contains("public", "document_type", "airport", "managed_by", "embedding_model");
  }

  @Test
  void closeHitsRequireChoosingBetweenActualAirports() {
    when(vectors.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(airportDocument("PQC", .92), airportDocument("SGN", .90)));
    var clarification = assertThrows(AiClarification.class,
        () -> resolver.newSession().resolve("địa danh mơ hồ", "destination", CATALOG));
    assertThat(clarification.detail().field()).isEqualTo("destination");
    assertThat(clarification.detail().airportOptions()).containsExactly(PQC, SGN);
  }

  @Test
  void clearScoreGapAllowsOneStrongMatchInsteadOfShowingDistantCities() {
    when(vectors.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(airportDocument("PQC", .96), airportDocument("SGN", .70)));
    assertThat(resolver.newSession().resolve("hòn đảo phía nam", "destination", CATALOG)).isEqualTo(PQC);
  }

  @Test
  void aSingleModerateHitStillNeedsUserConfirmation() {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(airportDocument("PQC", .72)));
    var clarification = assertThrows(AiClarification.class,
        () -> resolver.newSession().resolve("địa danh chưa rõ", "destination", CATALOG));
    assertThat(clarification.detail().airportOptions()).containsExactly(PQC);
    assertThat(clarification.getMessage()).contains("xác nhận");
  }

  @Test
  void oneVectorHitDoesNotHideOtherAirportsServingTheSameCity() {
    var hnd = new Airport("HND", "Haneda", "Tokyo", "Japan", "Asia/Tokyo");
    var nrt = new Airport("NRT", "Narita", "Tokyo", "Japan", "Asia/Tokyo");
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(airportDocument("HND", .98)));
    var clarification = assertThrows(AiClarification.class,
        () -> resolver.newSession().resolve("thủ đô nước Nhật", "destination", List.of(hnd, nrt)));
    assertThat(clarification.detail().airportOptions()).containsExactly(hnd, nrt);
    clearInvocations(vectors);
    assertThrows(AiClarification.class, () -> resolver.newSession().resolve("Tokyo", "destination", List.of(hnd, nrt)));
    verifyNoInteractions(vectors);
  }

  @Test
  void staleLeadingAirportCannotRedirectTheUserToTheRunnerUp() {
    when(vectors.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(airportDocument("SGN", .97), airportDocument("PQC", .91)));
    var clarification = assertThrows(AiClarification.class,
        () -> resolver.newSession().resolve("đầu tàu kinh tế phía nam", "origin", List.of(PQC)));
    assertThat(clarification.detail().airportOptions()).isEmpty();
  }

  @Test
  void staleCompetingAirportPreventsConfidentAutomaticSelection() {
    when(vectors.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(airportDocument("PQC", .95), airportDocument("SGN", .94)));
    var clarification = assertThrows(AiClarification.class,
        () -> resolver.newSession().resolve("địa danh khác", "destination", List.of(PQC)));
    assertThat(clarification.detail().airportOptions()).containsExactly(PQC);
  }

  @Test
  void duplicatePointsAreOneCandidateAndScoresAreSortedIndependentlyOfStoreOrder() {
    var duplicate = Document.builder().id("other-pqc").text("duplicate")
        .metadata(airportDocument("PQC", .96).getMetadata()).score(.91).build();
    when(vectors.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(airportDocument("SGN", .66), duplicate, airportDocument("PQC", .96)));
    var session = resolver.newSession();
    assertThat(session.resolve("đảo du lịch", "destination", CATALOG)).isEqualTo(PQC);
    assertThat(session.sources()).hasSize(1);
  }

  @Test
  void retrievalCacheIsRequestScopedAndStillRevalidatesTheCurrentCatalog() {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(airportDocument("PQC", .95)));
    var session = resolver.newSession();
    assertThat(session.resolve("đảo ngọc", "destination", CATALOG)).isEqualTo(PQC);
    assertThat(session.resolve("ĐẢO NGỌC", "destination", CATALOG)).isEqualTo(PQC);
    assertThrows(AiClarification.class, () -> session.resolve("đảo ngọc", "destination", List.of(SGN)));
    verify(vectors, times(1)).similaritySearch(any(SearchRequest.class));
    resolver.newSession().resolve("đảo ngọc", "destination", CATALOG);
    verify(vectors, times(2)).similaritySearch(any(SearchRequest.class));
  }

  @Test
  void leakedPrivateWrongTypeWrongModelAndInvalidMetadataAreRejectedAgainLocally() {
    when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
        changed("scope", "private"), changed("owner_id", 3L), changed("document_type", "flight_fare"),
        changed("embedding_model", "other"), changed("managed_by", "other"), changed("source_id", "SGN"),
        changed("airport_code", "PQC' OR 1=1"), airportDocument("PQC", .2),
        Document.builder().text("no score").metadata(airportDocument("PQC", .9).getMetadata()).build()));
    var session = resolver.newSession();
    var clarification = assertThrows(AiClarification.class, () -> session.resolve("đảo ngọc", "destination", CATALOG));
    assertThat(clarification.detail().airportOptions()).isEmpty();
    assertThat(session.sources()).isEmpty();
  }

  @Test
  void blankLocationsAndUnknownExplicitCodesNeverUseNearestAirportAsAGuess() {
    for (String query : List.of("", "   ", "ZZZ", "a".repeat(121)))
      assertThrows(AiClarification.class, () -> resolver.newSession().resolve(query, "origin", CATALOG));
    verifyNoInteractions(vectors);
  }

  @Test
  void vectorAndEmbeddingFailuresAreNotPresentedAsNoMatchingAirport() {
    var embedding = new AiException(AiException.Code.EMBEDDING_RATE_LIMITED, "Safe quota message");
    when(vectors.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("SECRET body", embedding));
    assertThat(assertThrows(AiException.class,
        () -> resolver.newSession().resolve("đảo ngọc", "destination", CATALOG))).isSameAs(embedding);
    when(vectors.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("SECRET connection"));
    var failure = assertThrows(AiException.class, () -> resolver.newSession().resolve("đảo ngọc", "destination", CATALOG));
    assertThat(failure.code()).isEqualTo(AiException.Code.VECTOR_STORE_UNAVAILABLE);
    assertThat(failure.getMessage()).doesNotContain("SECRET");
    assertThat(failure.getCause()).isNull();
  }

  private Document changed(String key, Object value) {
    var original = airportDocument("PQC", .99);
    var metadata = new HashMap<>(original.getMetadata()); metadata.put(key, value);
    return Document.builder().id(original.getId()).text(original.getText()).metadata(metadata).score(.99).build();
  }
}
