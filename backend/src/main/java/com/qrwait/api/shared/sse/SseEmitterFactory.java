package com.qrwait.api.shared.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SseEmitter 생성 책임을 분리한다. 타임아웃 정책을 한곳에서 관리하고, 테스트에서 주입 가능하게 한다.
 */
@Component
public class SseEmitterFactory {

  private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30분

  public SseEmitter create() {
    return new SseEmitter(SSE_TIMEOUT_MS);
  }
}
