package com.qrwait.api.shared.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 열려 있는 SSE 연결을 주기적으로 깨워, 프록시·모바일 NAT의 유휴 연결 종료와
 * 스트림 버퍼링 브라우저(삼성 인터넷 등)의 초기 지연을 방지한다.
 */
@Component
@RequiredArgsConstructor
public class SseHeartbeat {

  private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

  private final SseEmitterRegistry registry;

  @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
  public void heartbeat() {
    registry.sendHeartbeat();
  }
}
