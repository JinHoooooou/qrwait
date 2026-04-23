package com.qrwait.api.shared.sms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmsNotificationListenerTest {

  private final UUID storeId = UUID.randomUUID();
  private final UUID waitingId = UUID.randomUUID();

  @Mock
  SmsClient smsClient;
  @Mock
  SsePublisher ssePublisher;

  SmsNotificationListener listener;

  @BeforeEach
  void setUp() {
    listener = new SmsNotificationListener(smsClient, ssePublisher);
  }

  @Test
  void onWaitingCalled_SMS_발송_성공() {
    WaitingCalledEvent event = new WaitingCalledEvent(storeId, waitingId, "010-1234-5678", 3, "홍콩반점");

    listener.onWaitingCalled(event);

    verify(smsClient).send(eq("010-1234-5678"), contains("3번 손님"));
    verify(smsClient).send(contains("010-1234-5678"), contains("홍콩반점"));
    verify(ssePublisher, never()).notifyOwnerSmsFailed(any(), any(int.class), any());
  }

  @Test
  void onWaitingCalled_SMS_발송_실패시_점주_SSE_알림() {
    WaitingCalledEvent event = new WaitingCalledEvent(storeId, waitingId, "010-1234-5678", 3, "홍콩반점");
    doThrow(new RuntimeException("네트워크 오류")).when(smsClient).send(any(), any());

    listener.onWaitingCalled(event);

    verify(ssePublisher).notifyOwnerSmsFailed(storeId, 3, "010-1234-5678");
  }
}
