package com.skygo.backend.ai.index;

import static com.skygo.backend.ai.AiTestSupport.properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class KnowledgeIndexerTest {
  static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  static final AiIndexProperties INDEX = new AiIndexProperties(true, Duration.ofMinutes(5), 2, Duration.ZERO, 30, 15, 100);
  final KnowledgeSource source = mock(KnowledgeSource.class);
  final KnowledgeIndexStore store = mock(KnowledgeIndexStore.class);
  final KnowledgeIndexer indexer = new KnowledgeIndexer(source, store, INDEX, Clock.fixed(NOW, ZoneOffset.UTC));

  static Document doc(String key, String text, Long owner, Instant observed) {
    return KnowledgeDocuments.create(properties(), "test", key, "Title", text, Map.of(), owner, observed);
  }

  @Test
  void stableIdsAndHashesIgnoreTimeAndMapOrderButIncludePriceAndOwner() {
    var first = doc("1", "Price 1200000", null, NOW);
    var later = doc("1", "Price 1200000", null, NOW.plusSeconds(300));
    assertThat(UUID.fromString(first.getId())).isNotNull();
    assertThat(first.getId()).isEqualTo(later.getId());
    assertThat(first.getMetadata().get("content_hash")).isEqualTo(later.getMetadata().get("content_hash"));
    assertThat(first.getMetadata().get("content_hash"))
        .isNotEqualTo(doc("1", "Price 1500000", null, NOW).getMetadata().get("content_hash"))
        .isNotEqualTo(doc("1", "Price 1200000", 42L, NOW).getMetadata().get("content_hash"));
    var privateDoc = doc("booking", "Summary", 42L, NOW);
    assertThat(privateDoc.getMetadata()).containsEntry("scope", "private").containsEntry("owner_id", 42L);
    assertThat(privateDoc.getText()).doesNotContain("42");
  }

  @Test
  void updatesChangedDocumentsAndDeletesOnlyManagedIdsAfterSuccessfulWrites() {
    var unchanged = doc("same", "same", null, NOW);
    var changed = doc("changed", "new price", null, NOW);
    var added = doc("added", "new flight", null, NOW);
    when(source.snapshot(NOW)).thenReturn(List.of(unchanged, changed, added));
    when(store.hashes()).thenReturn(Map.of(unchanged.getId(), (String) unchanged.getMetadata().get("content_hash"),
        changed.getId(), "previous-hash", "stale-id", "stale-hash"));
    var report = indexer.synchronize();
    assertThat(report.total()).isEqualTo(3);
    assertThat(report.upserted()).isEqualTo(2);
    assertThat(report.unchanged()).isEqualTo(1);
    assertThat(report.deleted()).isEqualTo(1);
    var order = inOrder(store);
    order.verify(store).hashes();
    order.verify(store).upsert(List.of(changed, added));
    order.verify(store).delete(List.of("stale-id"));
    assertThat(indexer.lastReport()).isEqualTo(report);
  }

  @Test
  void unchangedSecondRunDoesNotRequestAnyEmbeddings() {
    var doc = doc("same", "same", null, NOW);
    when(source.snapshot(NOW)).thenReturn(List.of(doc));
    when(store.hashes()).thenReturn(Map.of(doc.getId(), (String) doc.getMetadata().get("content_hash")));
    assertThat(indexer.synchronize().upserted()).isZero();
    verify(store, never()).upsert(anyList());
    verify(store, never()).delete(anyList());
  }

  @Test
  void embeddingFailureNeverDeletesStalePointsAndNextRunCanRetry() {
    var doc = doc("new", "flight", null, NOW);
    when(source.snapshot(NOW)).thenReturn(List.of(doc));
    when(store.hashes()).thenReturn(Map.of("stale", "hash"));
    doThrow(new IllegalStateException("provider unavailable")).doNothing().when(store).upsert(anyList());
    assertThatThrownBy(indexer::synchronize).isInstanceOf(IllegalStateException.class);
    verify(store, never()).delete(anyList());
    assertThat(indexer.lastReport()).isNull();
    assertThat(indexer.synchronize().deleted()).isEqualTo(1);
  }

  @Test
  void failedSnapshotOrScanNeverTriggersAnyDeletion() {
    when(source.snapshot(NOW)).thenThrow(new IllegalStateException("read failed"));
    assertThatThrownBy(indexer::synchronize).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(store);
    doReturn(List.of()).when(source).snapshot(NOW);
    when(store.hashes()).thenThrow(new IllegalStateException("scan failed"));
    assertThatThrownBy(indexer::synchronize).isInstanceOf(IllegalStateException.class);
    verify(store, never()).delete(anyList());
  }

  @Test
  void duplicateOrOversizedSnapshotsAreRejectedBeforeTouchingVectorStore() {
    var doc = doc("duplicate", "value", null, NOW);
    when(source.snapshot(NOW)).thenReturn(List.of(doc, doc));
    assertThatThrownBy(indexer::synchronize).hasMessageContaining("duplicate");
    when(source.snapshot(NOW)).thenReturn(java.util.Collections.nCopies(101, doc));
    assertThatThrownBy(indexer::synchronize).hasMessageContaining("max-documents");
    verifyNoInteractions(store);
  }

  @Test
  void emptyCompleteSnapshotRetiresOldManagedDocuments() {
    when(source.snapshot(NOW)).thenReturn(List.of());
    when(store.hashes()).thenReturn(Map.of("old", "hash"));
    assertThat(indexer.synchronize().deleted()).isEqualTo(1);
    verify(store).delete(List.of("old"));
  }

  @Test
  void pausesOnlyBetweenChangedBatchesAndSkipsTheUnchangedDocuments() throws Exception {
    var settings = new AiIndexProperties(true, Duration.ofMinutes(5), 2, Duration.ofSeconds(10), 30, 15, 100);
    var pause = mock(KnowledgeIndexer.Pause.class);
    var paced = new KnowledgeIndexer(source, store, settings, Clock.fixed(NOW, ZoneOffset.UTC), pause);
    var same = doc("same", "same", null, NOW);
    var one = doc("1", "new", null, NOW);
    var two = doc("2", "new", null, NOW);
    var three = doc("3", "new", null, NOW);
    when(source.snapshot(NOW)).thenReturn(List.of(same, one, two, three));
    when(store.hashes()).thenReturn(Map.of(same.getId(), (String) same.getMetadata().get("content_hash")));
    assertThat(paced.synchronize().upserted()).isEqualTo(3);
    var order = inOrder(store, pause);
    order.verify(store).hashes();
    order.verify(store).upsert(List.of(one, two));
    order.verify(pause).sleep(Duration.ofSeconds(10));
    order.verify(store).upsert(List.of(three));
    verifyNoMoreInteractions(pause);
    clearInvocations(pause);
    when(source.snapshot(NOW)).thenReturn(List.of(same));
    assertThat(paced.synchronize().upserted()).isZero();
    verifyNoInteractions(pause);
  }

  @Test
  void interruptionDuringThePauseStopsFurtherWritesAndPreservesStalePoints() throws Exception {
    var settings = new AiIndexProperties(true, Duration.ofMinutes(5), 2, Duration.ofSeconds(10), 30, 15, 100);
    var pause = mock(KnowledgeIndexer.Pause.class);
    var paced = new KnowledgeIndexer(source, store, settings, Clock.fixed(NOW, ZoneOffset.UTC), pause);
    when(source.snapshot(NOW)).thenReturn(List.of(doc("1", "new", null, NOW),
        doc("2", "new", null, NOW), doc("3", "new", null, NOW)));
    when(store.hashes()).thenReturn(Map.of("stale", "hash"));
    doThrow(new InterruptedException()).when(pause).sleep(any());
    try {
      assertThatThrownBy(paced::synchronize).hasMessageContaining("interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(store, times(1)).upsert(anyList());
      verify(store, never()).delete(anyList());
      assertThat(paced.lastReport()).isNull();
    } finally {
      Thread.interrupted();
    }
  }
}
