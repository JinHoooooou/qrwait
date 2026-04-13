package com.qrwait.api.shared.sse;

import com.qrwait.api.store.domain.event.StoreStatusChangedEvent;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import com.qrwait.api.waiting.domain.event.WaitingRegisteredEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SseEventListener {

  private final SsePublisher ssePublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingRegistered(WaitingRegisteredEvent event) {
    ssePublisher.broadcastRegistered(event.storeId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingUpdated(WaitingUpdatedEvent event) {
    ssePublisher.broadcastUpdate(event.storeId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingCalled(WaitingCalledEvent event) {
    ssePublisher.broadcastCalled(event.storeId(), event.waitingId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onStoreStatusChanged(StoreStatusChangedEvent event) {
    ssePublisher.broadcastStoreStatus(event.storeId(), event.status());
  }
}
