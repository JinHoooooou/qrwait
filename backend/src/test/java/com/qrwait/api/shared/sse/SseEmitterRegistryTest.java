package com.qrwait.api.shared.sse;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

  private SseEmitterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SseEmitterRegistry();
  }

  @Test
  void broadcast_등록된_모든_Emitter에_이벤트_전송() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter emitter1 = mock(SseEmitter.class);
    SseEmitter emitter2 = mock(SseEmitter.class);

    registry.register(storeId, emitter1);
    registry.register(storeId, emitter2);

    registry.broadcast(storeId, "waiting-update", "data");

    verify(emitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void broadcast_구독자_없으면_예외_없이_스킵() {
    UUID storeId = UUID.randomUUID();

    assertThatNoException()
        .isThrownBy(() -> registry.broadcast(storeId, "waiting-update", "data"));
  }

  @Test
  void broadcast_전송_실패한_Emitter는_다음_broadcast_시_제거됨() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter broken = mock(SseEmitter.class);
    SseEmitter healthy = mock(SseEmitter.class);

    doThrow(new IOException("연결 끊김")).when(broken).send(any(SseEmitter.SseEventBuilder.class));

    registry.register(storeId, broken);
    registry.register(storeId, healthy);

    registry.broadcast(storeId, "waiting-update", "data");

    // broken은 1회 시도 후 제거, 두 번째 broadcast에서는 호출되지 않아야 함
    registry.broadcast(storeId, "waiting-update", "data2");

    verify(broken, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    verify(healthy, times(2)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void remove_명시적_제거_후_broadcast_호출_안됨() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);

    registry.register(storeId, emitter);
    registry.remove(storeId, emitter);

    registry.broadcast(storeId, "waiting-update", "data");

    verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  // ===== 점주용 =====

  @Test
  void broadcastToOwner_등록된_점주_Emitter에_이벤트_전송() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter ownerEmitter = mock(SseEmitter.class);

    registry.registerOwner(storeId, ownerEmitter);
    registry.broadcastToOwner(storeId, "waiting-registered", "data");

    verify(ownerEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void broadcastToOwner_점주_미연결_시_예외_없이_스킵() {
    UUID storeId = UUID.randomUUID();

    assertThatNoException()
        .isThrownBy(() -> registry.broadcastToOwner(storeId, "waiting-registered", "data"));
  }

  @Test
  void registerOwner_재연결_시_기존_Emitter_complete_후_교체() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter oldEmitter = mock(SseEmitter.class);
    SseEmitter newEmitter = mock(SseEmitter.class);

    registry.registerOwner(storeId, oldEmitter);
    registry.registerOwner(storeId, newEmitter);

    verify(oldEmitter, times(1)).complete();

    registry.broadcastToOwner(storeId, "event", "data");

    verify(newEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    verify(oldEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void removeOwner_제거_후_broadcastToOwner_호출_안됨() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter ownerEmitter = mock(SseEmitter.class);

    registry.registerOwner(storeId, ownerEmitter);
    registry.removeOwner(storeId);

    registry.broadcastToOwner(storeId, "event", "data");

    verify(ownerEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void broadcastToOwner_전송_실패_시_emitter_제거() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter brokenEmitter = mock(SseEmitter.class);

    doThrow(new IOException("연결 끊김")).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

    registry.registerOwner(storeId, brokenEmitter);
    registry.broadcastToOwner(storeId, "event", "data");

    // 제거 후 두 번째 broadcast에서는 호출되지 않아야 함
    registry.broadcastToOwner(storeId, "event", "data2");

    verify(brokenEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
  }
}
