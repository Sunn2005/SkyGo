package com.skygo.backend.ai.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;

/** Per-process, bounded admission; SDK retries and other applications also consume provider quota. */
public final class AiChatGate {
  private final Semaphore active = new Semaphore(1);
  private final ArrayDeque<Instant> calls = new ArrayDeque<>();
  private final Clock clock;
  private final int limit;

  public AiChatGate(Clock clock, int limit) { this.clock = clock; this.limit = limit; }

  public void enter() {
    if (!active.tryAcquire()) throw new AiChatException(429, "AI_BUSY", "AI đang xử lý câu hỏi khác. Vui lòng thử lại sau.");
  }
  public void leave() { active.release(); }

  public synchronized void beforeModelCall() {
    var now = clock.instant();
    while (!calls.isEmpty() && !calls.peekFirst().isAfter(now.minusSeconds(60))) calls.removeFirst();
    if (calls.size() >= limit)
      throw new AiChatException(429, "AI_RATE_LIMITED", "Đã đạt giới hạn gọi model của máy chủ trong phút này. Vui lòng thử lại sau.");
    calls.addLast(now);
  }
}
