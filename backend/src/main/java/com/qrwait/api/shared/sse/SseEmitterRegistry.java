package com.qrwait.api.shared.sse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class SseEmitterRegistry {

  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  public void register(UUID storeId, SseEmitter emitter) {
    emitters.computeIfAbsent(storeId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    log.debug("SSE 등록 — storeId={}, 현재 구독자 수={}", storeId, emitters.get(storeId).size());
  }

  public void remove(UUID storeId, SseEmitter emitter) {
    List<SseEmitter> list = emitters.get(storeId);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) {
        emitters.remove(storeId);
      }
    }
  }

  public void broadcast(UUID storeId, String eventName, Object data) {
    List<SseEmitter> list = emitters.get(storeId);
    if (list == null || list.isEmpty()) {
      return;
    }

    SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventName).data(data);

    for (SseEmitter emitter : list) {
      try {
        emitter.send(event);
      } catch (IOException e) {
        log.warn("SSE 전송 실패 — emitter 제거: storeId={}", storeId);
        remove(storeId, emitter);
      }
    }
  }
}
