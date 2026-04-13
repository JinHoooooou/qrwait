package com.qrwait.api.shared.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.store.domain.event.StoreStatusChangedEvent;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import com.qrwait.api.waiting.domain.event.WaitingRegisteredEvent;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseEventListenerTest {

  @Mock
  SseEmitterRegistry registry;
  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;

  SseEventListener listener;

  @BeforeEach
  void setUp() {
    SsePublisher ssePublisher = new SsePublisher(registry, waitingRepository, storeSettingsRepository);
    listener = new SseEventListener(ssePublisher);
  }

  @Test
  void onWaitingRegistered_손님과_점주에게_브로드캐스트() {
    UUID storeId = UUID.randomUUID();
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(3);

    listener.onWaitingRegistered(new WaitingRegisteredEvent(storeId));

    verify(registry).broadcast(eq(storeId), eq("waiting-updated"), any());
    verify(registry).broadcastToOwner(eq(storeId), eq("waiting-registered"), any());
  }

  @Test
  void onWaitingUpdated_손님과_점주에게_브로드캐스트() {
    UUID storeId = UUID.randomUUID();
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(2);

    listener.onWaitingUpdated(new WaitingUpdatedEvent(storeId));

    verify(registry).broadcast(eq(storeId), eq("waiting-updated"), any());
    verify(registry).broadcastToOwner(eq(storeId), eq("waiting-updated"), any());
  }

  @Test
  void onWaitingCalled_손님에게_waitingId_포함_브로드캐스트() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    listener.onWaitingCalled(new WaitingCalledEvent(storeId, waitingId));

    verify(registry).broadcast(eq(storeId), eq("waiting-called"), any());
  }

  @Test
  void onStoreStatusChanged_손님과_점주에게_브로드캐스트() {
    UUID storeId = UUID.randomUUID();

    listener.onStoreStatusChanged(new StoreStatusChangedEvent(storeId, StoreStatus.CLOSED));

    verify(registry).broadcast(eq(storeId), eq("store-status-changed"), any());
    verify(registry).broadcastToOwner(eq(storeId), eq("store-status-changed"), any());
  }
}
