package com.qrwait.api.shared.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsePublisherTest {

  @Mock
  SseEmitterRegistry registry;
  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;

  SsePublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new SsePublisher(registry, waitingRepository, storeSettingsRepository);
  }

  @Test
  void notifyOwnerSmsFailed_점주_채널로_sms_send_failed_이벤트_발송() {
    UUID storeId = UUID.randomUUID();

    publisher.notifyOwnerSmsFailed(storeId, 3, "010-1234-5678");

    verify(registry).broadcastToOwner(eq(storeId), eq("sms-send-failed"), any());
  }
}
