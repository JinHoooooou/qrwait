package com.qrwait.api.shared.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@ExtendWith(MockitoExtension.class)
class SsePublisherTest {

  @Mock
  SseEmitterRegistry registry;
  @Mock
  WaitingRepository waitingRepository;
  @Mock
  StoreSettingsRepository storeSettingsRepository;
  @Mock
  SseEmitterFactory emitterFactory;

  SsePublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new SsePublisher(registry, waitingRepository, storeSettingsRepository, emitterFactory);
  }

  @Test
  void notifyOwnerSmsFailed_점주_채널로_sms_send_failed_이벤트_발송() {
    UUID storeId = UUID.randomUUID();

    publisher.notifyOwnerSmsFailed(storeId, 3, "010-1234-5678");

    verify(registry).broadcastToOwner(eq(storeId), eq("sms-send-failed"), any());
  }

  @Test
  void subscribe_연결_직후_2KB_이상의_패딩_주석을_먼저_전송() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter emitter = Mockito.mock(SseEmitter.class);
    when(emitterFactory.create()).thenReturn(emitter);

    publisher.subscribe(storeId, UUID.randomUUID());

    assertThat(firstSentPayload(emitter))
        .startsWith(":")
        .hasSizeGreaterThanOrEqualTo(2048);
  }

  @Test
  void subscribeOwner_연결_직후_2KB_이상의_패딩_주석을_먼저_전송() throws IOException {
    UUID storeId = UUID.randomUUID();
    SseEmitter emitter = Mockito.mock(SseEmitter.class);
    when(emitterFactory.create()).thenReturn(emitter);

    publisher.subscribeOwner(storeId);

    assertThat(firstSentPayload(emitter))
        .startsWith(":")
        .hasSizeGreaterThanOrEqualTo(2048);
  }

  private static String firstSentPayload(SseEmitter emitter) throws IOException {
    ArgumentCaptor<SseEventBuilder> captor = ArgumentCaptor.forClass(SseEventBuilder.class);
    verify(emitter, Mockito.atLeastOnce()).send(captor.capture());
    List<DataWithMediaType> data = captor.getAllValues().get(0).build().stream().toList();
    return data.stream()
        .map(d -> String.valueOf(d.getData()))
        .reduce("", String::concat);
  }
}
