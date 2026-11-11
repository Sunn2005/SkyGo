package com.skygo.backend.ai.index;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.skygo.backend.ai.service.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public final class KnowledgeIndexLifecycle implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexLifecycle.class);
  private final KnowledgeIndexer indexer;
  private final Duration fixedDelay;
  private final boolean runOnce;
  private ScheduledExecutorService executor;

  public KnowledgeIndexLifecycle(KnowledgeIndexer indexer, Duration fixedDelay, boolean runOnce) {
    this.indexer = indexer;
    this.fixedDelay = fixedDelay;
    this.runOnce = runOnce;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ready(ApplicationReadyEvent event) {
    if (runOnce) {
      // Propagate errors so the command fails; close all pools/clients after a successful one-shot.
      indexer.synchronize();
      SpringApplication.exit(event.getApplicationContext(), () -> 0);
    } else {
      // A dedicated executor must not enable unrelated booking cleanup jobs when they are disabled.
      executor = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "skygo-ai-index");
        thread.setDaemon(true);
        return thread;
      });
      executor.scheduleWithFixedDelay(this::synchronize, 5_000, fixedDelay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  public void synchronize() {
    try {
      indexer.synchronize();
    } catch (RuntimeException ex) {
      // Provider/JDBC exceptions can include SQL, keys or document contents. Log no raw exception.
      if (ex instanceof AiException ai)
        log.error("AI index failed [{}]: {} Synchronization incomplete; retry on next cycle.", ai.code(), ai.getMessage());
      else log.error("AI index failed ({}). Synchronization incomplete; retry on next cycle.",
          ex.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    if (executor != null) executor.shutdownNow();
  }
}
