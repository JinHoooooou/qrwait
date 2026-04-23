package com.qrwait.api.shared.sms;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationListener {

  private final SmsClient smsClient;
  private final SsePublisher ssePublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingCalled(WaitingCalledEvent event) {
    String message = "[QR Wait] %d번 손님, %s에서 입장 안내드립니다. 매장으로 와주세요."
        .formatted(event.waitingNumber(), event.storeName());
    try {
      smsClient.send(event.phoneNumber(), message);
    } catch (Exception e) {
      log.error("SMS 발송 실패: waitingNumber={}, phone={}", event.waitingNumber(), event.phoneNumber(), e);
      ssePublisher.notifyOwnerSmsFailed(event.storeId(), event.waitingNumber(), event.phoneNumber());
    }
  }
}
