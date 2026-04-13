package com.qrwait.api.shared.sse;

import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.io.IOException;
import java.util.Map;
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
  private final StoreSettingsRepository storeSettingsRepository;

  /**
   * 손님을 storeId 단위 SSE 채널에 구독시킨다. 연결 즉시 현재 대기 현황을 초기 이벤트로 전송한다.
   */
  public SseEmitter subscribe(UUID storeId, UUID waitingId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    emitter.onCompletion(() -> registry.remove(storeId, emitter));
    emitter.onTimeout(() -> registry.remove(storeId, emitter));
    emitter.onError(e -> registry.remove(storeId, emitter));

    registry.register(storeId, emitter);

    try {
      emitter.send(SseEmitter.event()
          .name("waiting-updated")
          .data(buildStoreStatus(storeId)));
    } catch (IOException e) {
      log.warn("손님 초기 SSE 이벤트 전송 실패 — waitingId={}", waitingId);
      registry.remove(storeId, emitter);
    }

    return emitter;
  }

  /**
   * 점주를 storeId 단위 SSE 채널에 구독시킨다. 기존 연결이 있으면 교체한다. 연결 즉시 현재 대기 현황을 초기 이벤트로 전송한다.
   */
  public SseEmitter subscribeOwner(UUID storeId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    emitter.onCompletion(() -> registry.removeOwner(storeId, emitter));
    emitter.onTimeout(() -> registry.removeOwner(storeId, emitter));
    emitter.onError(e -> registry.removeOwner(storeId, emitter));

    registry.registerOwner(storeId, emitter);

    try {
      emitter.send(SseEmitter.event()
          .name("waiting-updated")
          .data(buildStoreStatus(storeId)));
    } catch (IOException e) {
      log.warn("점주 초기 SSE 이벤트 전송 실패 — storeId={}", storeId);
      registry.removeOwner(storeId, emitter);
    }

    return emitter;
  }

  /**
   * 손님 등록 시 호출. 손님 전체 + 점주에게 브로드캐스트하고, 임계값 초과 시 점주에게 알림을 추가 발송한다.
   */
  public void broadcastRegistered(UUID storeId) {
    WaitingStatusResponse status = buildStoreStatus(storeId);
    registry.broadcast(storeId, "waiting-updated", status);
    registry.broadcastToOwner(storeId, "waiting-registered", status);
    checkAndBroadcastThreshold(storeId, status.totalWaiting());
  }

  /**
   * 입장/노쇼/취소 등 대기 상태 변경 시 호출. 손님 전체 + 점주에게 현재 대기 현황을 브로드캐스트한다.
   */
  public void broadcastUpdate(UUID storeId) {
    WaitingStatusResponse status = buildStoreStatus(storeId);
    registry.broadcast(storeId, "waiting-updated", status);
    registry.broadcastToOwner(storeId, "waiting-updated", status);
  }

  /**
   * 손님 호출(CALLED) 시 호출. 해당 매장 손님 전체 채널에 waitingId를 포함한 이벤트를 전송한다. 클라이언트가 자신의 waitingId와 비교해 처리한다.
   */
  public void broadcastCalled(UUID storeId, UUID waitingId) {
    registry.broadcast(storeId, "waiting-called", Map.of("waitingId", waitingId));
  }

  /**
   * 매장 영업 상태 변경 시 호출. 손님 전체 + 점주에게 변경된 상태를 브로드캐스트한다.
   */
  public void broadcastStoreStatus(UUID storeId, StoreStatus status) {
    Map<String, String> data = Map.of("status", status.name());
    registry.broadcast(storeId, "store-status-changed", data);
    registry.broadcastToOwner(storeId, "store-status-changed", data);
  }

  private WaitingStatusResponse buildStoreStatus(UUID storeId) {
    int total = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
    int estimated = total * MINUTES_PER_TEAM;
    return new WaitingStatusResponse(total, total, estimated);
  }

  private void checkAndBroadcastThreshold(UUID storeId, int totalWaiting) {
    storeSettingsRepository.findByStoreId(storeId).ifPresent(settings -> {
      if (settings.isAlertEnabled() && totalWaiting >= settings.getAlertThreshold()) {
        registry.broadcastToOwner(storeId, "alert-threshold-reached",
            Map.of("currentWaiting", totalWaiting, "threshold", settings.getAlertThreshold()));
      }
    });
  }
}
