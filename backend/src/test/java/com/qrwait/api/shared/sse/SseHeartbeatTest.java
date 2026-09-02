package com.qrwait.api.shared.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class SseHeartbeatTest {

  @Test
  void heartbeat_레지스트리에_하트비트_전송을_위임() {
    SseEmitterRegistry registry = mock(SseEmitterRegistry.class);

    new SseHeartbeat(registry).heartbeat();

    verify(registry).sendHeartbeat();
  }
}
