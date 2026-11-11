package com.skygo.backend.ai.index;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

/** Single-process reconciler. Run only one index writer per collection. */
public final class KnowledgeIndexer {
  private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);
  private final KnowledgeSource source;
  private final KnowledgeIndexStore store;
  private final AiIndexProperties properties;
  private final Clock clock;
  private final Pause pause;
  private final ReentrantLock lock = new ReentrantLock();
  private volatile Report lastReport;

  public KnowledgeIndexer(KnowledgeSource source, KnowledgeIndexStore store,
      AiIndexProperties properties, Clock clock) {
    this(source, store, properties, clock, Thread::sleep);
  }

  KnowledgeIndexer(KnowledgeSource source, KnowledgeIndexStore store,
      AiIndexProperties properties, Clock clock, Pause pause) {
    this.source = source;
    this.store = store;
    this.properties = properties;
    this.clock = clock;
    this.pause = pause;
  }

  public Report synchronize() {
    if (!lock.tryLock()) throw new IllegalStateException("AI index synchronization is already running.");
    Instant started = clock.instant();
    try {
      List<Document> snapshot = source.snapshot(started);
      var desired = new HashSet<String>();
      var types = new TreeMap<String, Integer>();
      if (snapshot.size() > properties.maxDocuments())
        throw new IllegalStateException("AI snapshot exceeds max-documents.");
      for (Document doc : snapshot) {
        if (!desired.add(doc.getId()) || !KnowledgeDocuments.MANAGED_BY.equals(doc.getMetadata().get("managed_by"))
            || !(doc.getMetadata().get("content_hash") instanceof String))
          throw new IllegalStateException("Invalid or duplicate AI document; no reconciliation performed.");
        types.merge(doc.getMetadata().get("document_type").toString(), 1, Integer::sum);
      }
      Map<String, String> existing = store.hashes();
      var changed = snapshot.stream().filter(doc -> !doc.getMetadata().get("content_hash")
          .equals(existing.get(doc.getId()))).toList();
      log.info("AI index starting: documents={}, changed={}, byType={}", snapshot.size(), changed.size(), types);
      int written = 0;
      for (int start = 0; start < changed.size(); start += properties.batchSize()) {
        if (Thread.currentThread().isInterrupted())
          throw new IllegalStateException("AI indexing interrupted before the next batch.");
        if (start > 0 && !properties.batchDelay().isZero()) {
          log.info("AI index pacing: waiting {} ms before the next embedding batch", properties.batchDelay().toMillis());
          try {
            pause.sleep(properties.batchDelay());
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI indexing interrupted while pacing; synchronization incomplete.");
          }
        }
        var batch = changed.subList(start, Math.min(start + properties.batchSize(), changed.size()));
        store.upsert(batch);
        written += batch.size();
        log.info("AI index embedding progress: {}/{}", written, changed.size());
      }
      // Never delete anything after a partial SQL read, failed scan or failed embedding/upsert.
      var stale = new ArrayList<>(existing.keySet());
      stale.removeAll(desired);
      for (int start = 0; start < stale.size(); start += properties.batchSize())
        store.delete(stale.subList(start, Math.min(start + properties.batchSize(), stale.size())));
      lastReport = new Report(started, clock.instant(), snapshot.size(), changed.size(),
          snapshot.size() - changed.size(), stale.size(), Map.copyOf(types));
      log.info("AI index completed: total={}, upserted={}, unchanged={}, deleted={}, byType={}",
          lastReport.total(), lastReport.upserted(), lastReport.unchanged(), lastReport.deleted(), types);
      return lastReport;
    } finally {
      lock.unlock();
    }
  }

  /** Last successful reconciliation; an unsuccessful attempt must not replace it. */
  public Report lastReport() { return lastReport; }

  @FunctionalInterface
  interface Pause { void sleep(Duration duration) throws InterruptedException; }

  public record Report(Instant startedAt, Instant completedAt, int total, int upserted,
      int unchanged, int deleted, Map<String, Integer> byType) {}
}
