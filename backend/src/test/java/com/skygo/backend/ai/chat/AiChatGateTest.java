package com.skygo.backend.ai.chat;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class AiChatGateTest {
  @Test
  void logicalCallBudgetExpiresAndConcurrentRequestsCannotMultiplyIt() {
    class MutableClock extends Clock {
      Instant now = Instant.parse("2026-11-10T12:00:00Z");
      @Override public ZoneId getZone() { return ZoneOffset.UTC; }
      @Override public Clock withZone(ZoneId zone) { return this; }
      @Override public Instant instant() { return now; }
    }
    var clock = new MutableClock();
    var gate = new AiChatGate(clock, 2);
    gate.enter();
    assertThatThrownBy(gate::enter).isInstanceOf(AiChatException.class);
    gate.beforeModelCall(); gate.beforeModelCall();
    assertThatThrownBy(gate::beforeModelCall).isInstanceOf(AiChatException.class);
    gate.leave(); gate.enter(); gate.leave();
    clock.now = clock.now.plusSeconds(60);
    assertThatCode(gate::beforeModelCall).doesNotThrowAnyException();
  }
}
