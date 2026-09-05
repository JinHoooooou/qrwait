package com.qrwait.api.shared.sse;

import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.customer.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsePublisher {

  /**
   * 스트림을 버퍼링하는 브라우저(삼성 인터넷 등)가 이벤트를 즉시 디스패치하도록, 연결 직후 전송하는 주석 패딩 크기.
   * 일부 브라우저는 수신 버퍼가 ~1~2KB를 넘기 전까지 EventSource 이벤트를 넘겨주지 않는다.
   */
  private static final int INITIAL_PADDING_SIZE = 2048;
  private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(5, 0);

  private final SseEmitterRegistry registry;
  private final WaitingRepository waitingRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final SseEmitterFactory emitterFactory;

  /**
   * 손님을 storeId 단위 SSE 채널에 구독시킨다. 연결 즉시 현재 대기 현황을 초기 이벤트로 전송한다.
   */
  public SseEmitter subscribe(UUID storeId, UUID waitingId) {
    SseEmitter emitter = emitterFactory.create();

    emitter.onCompletion(() -> registry.remove(storeId, emitter));
    emitter.onTimeout(() -> registry.remove(storeId, emitter));
    emitter.onError(e -> registry.remove(storeId, emitter));

    registry.register(storeId, emitter);

    try {
      sendInitialPadding(emitter);
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
    SseEmitter emitter = emitterFactory.create();

    emitter.onCompletion(() -> registry.removeOwner(storeId, emitter));
    emitter.onTimeout(() -> registry.removeOwner(storeId, emitter));
    emitter.onError(e -> registry.removeOwner(storeId, emitter));

    registry.registerOwner(storeId, emitter);

    try {
      sendInitialPadding(emitter);
      emitter.send(SseEmitter.event()
          .name("waiting-updated")
          .data(buildStoreStatus(storeId)));
    } catch (IOException e) {
      log.warn("점주 초기 SSE 이벤트 전송 실패 — storeId={}", storeId);
      registry.removeOwner(storeId, emitter);
    }

    return emitter;
  }

  private void sendInitialPadding(SseEmitter emitter) throws IOException {
    emitter.send(SseEmitter.event().comment(" ".repeat(INITIAL_PADDING_SIZE)));
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
   * 미루기(호출 취소) 시 호출. 호출 화면에 있던 손님이 대기 화면으로 돌아가도록 알린다.
   * 클라이언트가 자신의 waitingId 와 비교해 처리한다.
   */
  public void broadcastPostponed(UUID storeId, UUID waitingId) {
    registry.broadcast(storeId, "waiting-postponed", Map.of("waitingId", waitingId));
  }

  /**
   * 매장 영업 상태 변경 시 호출. 손님 전체 + 점주에게 변경된 상태를 브로드캐스트한다.
   */
  public void broadcastStoreStatus(UUID storeId, StoreStatus status) {
    Map<String, String> data = Map.of("status", status.name());
    registry.broadcast(storeId, "store-status-changed", data);
    registry.broadcastToOwner(storeId, "store-status-changed", data);
  }

  /**
   * SMS 발송 실패 시 호출. 점주 채널에만 발신하며 손님 채널에는 영향 없다.
   */
  public void notifyOwnerSmsFailed(UUID storeId, int waitingNumber, String phoneNumber) {
    registry.broadcastToOwner(storeId, "sms-send-failed",
        Map.of("waitingNumber", waitingNumber, "phoneNumber", phoneNumber));
  }

  private WaitingStatusResponse buildStoreStatus(UUID storeId) {
    Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(storeId);
    LocalDateTime now = LocalDateTime.now();
    LocalDate businessDate = settings
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> now.toLocalTime().isBefore(DEFAULT_OPEN_TIME)
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate());

    int total = waitingRepository.countByStoreIdAndStatus(storeId, businessDate, WaitingStatus.WAITING);
    int estimated = settings
        .map(s -> s.calculateEstimatedWait(total))
        .orElse(total * 5);
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
