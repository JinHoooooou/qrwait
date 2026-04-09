package com.qrwait.api.shared.sse;

import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingSseService {

  private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30분
  private static final int MINUTES_PER_TEAM = 5;

  private final SseEmitterRegistry registry;
  private final WaitingRepository waitingRepository;

  /**
   * 클라이언트를 storeId 단위 SSE 채널에 구독시킨다. 연결 즉시 현재 대기 현황을 초기 이벤트로 전송한다.
   */
  public SseEmitter subscribe(UUID storeId, UUID waitingId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    emitter.onCompletion(() -> registry.remove(storeId, emitter));
    emitter.onTimeout(() -> registry.remove(storeId, emitter));
    emitter.onError(e -> registry.remove(storeId, emitter));

    registry.register(storeId, emitter);

    try {
      emitter.send(SseEmitter.event()
          .name("waiting-update")
          .data(buildStoreStatus(storeId)));
    } catch (IOException e) {
      log.warn("초기 SSE 이벤트 전송 실패 — waitingId={}", waitingId);
      registry.remove(storeId, emitter);
    }

    return emitter;
  }

  /**
   * 해당 매장의 현재 대기 현황을 조회한 뒤 모든 구독자에게 브로드캐스트한다. currentRank는 클라이언트별로 다르므로 totalWaiting으로 대체한다. 클라이언트는 이 이벤트를 트리거로 GET /api/waitings/{id} 를 호출해 자신의 순위를
   * 갱신한다.
   */
  public void broadcastUpdate(UUID storeId) {
    registry.broadcast(storeId, "waiting-update", buildStoreStatus(storeId));
  }

  private WaitingStatusResponse buildStoreStatus(UUID storeId) {
    int total = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
    int estimated = total * MINUTES_PER_TEAM;
    return new WaitingStatusResponse(total, total, estimated);
  }
}
