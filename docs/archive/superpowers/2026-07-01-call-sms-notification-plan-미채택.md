# 호출 SMS 알림 (NHN Cloud) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 점주가 손님을 호출하면, 화면을 보고 있지 않은 손님도 NHN Cloud SMS로 입장 안내를 받는다.

**Architecture:** 기존 `WaitingCalledEvent`(호출 시 발행)에 SMS 리스너를 SSE 리스너와 나란히 `AFTER_COMMIT`으로 추가한다. 발송은 채널 중립 포트 `CallNotificationSender` 뒤의 어댑터가 담당하며, `notification.channel` 프로퍼티로 `log`(기본, dev)와 `sms`(NHN Cloud, 운영)를 전환한다. 발송 실패는 점주 대시보드 SSE 배너로 알린다.

**Tech Stack:** Java 21 · Spring Boot 3.5 (`RestClient`, `@ConditionalOnProperty`, `@TransactionalEventListener`) · JUnit5 · Mockito · `MockRestServiceServer` · React 19 + TypeScript(프론트 배너).

## Global Constraints

- 백엔드 아키텍처 규칙은 `backend/CLAUDE.md`를 따른다. 알림은 여러 애그리거트에 걸친 횡단 외부연동이므로 `shared/notification/` 패키지에 둔다.
- 도메인 계층은 Spring/JPA를 import하지 않는다. (이번 작업은 도메인 변경 없음 — `WaitingCalledEvent` 재사용)
- `notification.channel` 기본값은 `log`. 크리덴셜 없이 앱이 항상 부팅되어야 한다(과거 revert 사유 제거).
- NHN Cloud SMS v3.0 엔드포인트: `POST https://sms.api.nhncloudservice.com/sms/v3.0/appKeys/{appKey}/sender/sms`, 인증 헤더 `X-Secret-Key`, 요청 바디 `{ body, sendNo, recipientList:[{recipientNo}] }`.
- 발송 재시도 없음(과금 중복 방지). 실패는 로그 + 점주 SSE `call-notification-failed`로만 처리.
- 커밋 메시지는 한글로 작성한다.
- 백엔드 테스트 실행: `cd backend && ./gradlew test --tests "<FQCN>"`.

---

## File Structure

**백엔드 (생성)**
- `backend/src/main/java/com/qrwait/api/shared/notification/CallNotification.java` — 알림 데이터 레코드
- `backend/src/main/java/com/qrwait/api/shared/notification/CallNotificationSender.java` — 채널 중립 포트
- `backend/src/main/java/com/qrwait/api/shared/notification/LoggingCallNotificationSender.java` — dev 기본 어댑터
- `backend/src/main/java/com/qrwait/api/shared/notification/NhnSmsProperties.java` — NHN 크리덴셜 프로퍼티
- `backend/src/main/java/com/qrwait/api/shared/notification/NhnSmsSender.java` — NHN Cloud SMS 어댑터
- `backend/src/main/java/com/qrwait/api/shared/notification/NotificationConfig.java` — 채널 토글 빈 등록
- `backend/src/main/java/com/qrwait/api/shared/notification/CallNotificationListener.java` — `WaitingCalledEvent` 구독

**백엔드 (수정)**
- `backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java` — `notifyCallNotificationFailed` 추가
- `backend/src/main/resources/application.yml` — `notification.channel`, `sms.nhn.*` 프로퍼티

**백엔드 (테스트 생성)**
- `.../shared/notification/LoggingCallNotificationSenderTest.java`
- `.../shared/notification/NotificationChannelToggleTest.java`
- `.../shared/notification/NhnSmsSenderTest.java`
- `.../shared/notification/CallNotificationListenerTest.java`
- `.../shared/sse/SsePublisherTest.java`

**프론트 (수정)**
- `frontend/src/pages/DashboardPage.tsx` — `call-notification-failed` 배너

**문서 (수정)**
- `README.md` — NHN Cloud SMS 설정 가이드
- `.env.example` — NHN 환경변수 예시

---

### Task 1: 포트 · 알림 레코드 · 로그 어댑터 · 채널 토글(기본 log)

**Files:**
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/CallNotification.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/CallNotificationSender.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/LoggingCallNotificationSender.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/NotificationConfig.java`
- Test: `backend/src/test/java/com/qrwait/api/shared/notification/LoggingCallNotificationSenderTest.java`
- Test: `backend/src/test/java/com/qrwait/api/shared/notification/NotificationChannelToggleTest.java`

**Interfaces:**
- Produces:
  - `record CallNotification(String phoneNumber, int waitingNumber, String storeName)`
  - `interface CallNotificationSender { void send(CallNotification notification); }`
  - `class LoggingCallNotificationSender implements CallNotificationSender`
  - `NotificationConfig` — `notification.channel=log`(기본, matchIfMissing) 일 때 `CallNotificationSender` 빈으로 `LoggingCallNotificationSender` 등록. (`sms` 빈은 Task 3에서 추가)

- [ ] **Step 1: 포트와 레코드 작성**

`CallNotification.java`:
```java
package com.qrwait.api.shared.notification;

public record CallNotification(String phoneNumber, int waitingNumber, String storeName) {

}
```

`CallNotificationSender.java`:
```java
package com.qrwait.api.shared.notification;

public interface CallNotificationSender {

  void send(CallNotification notification);
}
```

- [ ] **Step 2: 실패하는 로그 어댑터 테스트 작성**

`LoggingCallNotificationSenderTest.java`:
```java
package com.qrwait.api.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingCallNotificationSenderTest {

  @Test
  void send_호출번호와_매장명을_로그로_출력한다() {
    Logger logger = (Logger) LoggerFactory.getLogger(LoggingCallNotificationSender.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    new LoggingCallNotificationSender()
        .send(new CallNotification("010-1234-5678", 3, "테스트매장"));

    assertThat(appender.list)
        .anyMatch(e -> e.getFormattedMessage().contains("3")
            && e.getFormattedMessage().contains("테스트매장")
            && e.getFormattedMessage().contains("010-1234-5678"));
  }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.LoggingCallNotificationSenderTest"`
Expected: 컴파일 실패 (`LoggingCallNotificationSender` 없음)

- [ ] **Step 4: 로그 어댑터 구현**

`LoggingCallNotificationSender.java`:
```java
package com.qrwait.api.shared.notification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingCallNotificationSender implements CallNotificationSender {

  @Override
  public void send(CallNotification notification) {
    log.info("[알림-로그] {}번 손님({}) 호출 알림 — 매장: {}",
        notification.waitingNumber(), notification.phoneNumber(), notification.storeName());
  }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.LoggingCallNotificationSenderTest"`
Expected: PASS

- [ ] **Step 6: 채널 토글 설정 클래스 작성 (기본 log 빈만)**

`NotificationConfig.java`:
```java
package com.qrwait.api.shared.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {

  @Bean
  @ConditionalOnProperty(name = "notification.channel", havingValue = "log", matchIfMissing = true)
  public CallNotificationSender loggingCallNotificationSender() {
    return new LoggingCallNotificationSender();
  }
}
```

- [ ] **Step 7: 채널 토글 기본값 테스트 작성**

`NotificationChannelToggleTest.java`:
```java
package com.qrwait.api.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.ConfigurableApplicationContext;

class NotificationChannelToggleTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
      .withUserConfiguration(NotificationConfig.class);

  @Test
  void 채널_미설정이면_로그_어댑터가_등록된다() {
    runner.run(ctx -> assertThat(ctx)
        .hasSingleBean(CallNotificationSender.class)
        .getBean(CallNotificationSender.class)
        .isInstanceOf(LoggingCallNotificationSender.class));
  }

  @Test
  void 채널_log면_로그_어댑터가_등록된다() {
    runner.withPropertyValues("notification.channel=log")
        .run(ctx -> assertThat(ctx).getBean(CallNotificationSender.class)
            .isInstanceOf(LoggingCallNotificationSender.class));
  }
}
```

> 참고: `RestClientAutoConfiguration`은 Task 3에서 추가할 `sms` 어댑터가 요구하는 `RestClient.Builder`를 제공하기 위해 미리 포함한다. Task 1 시점엔 log 빈만 검증한다.

- [ ] **Step 8: 토글 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.NotificationChannelToggleTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/notification backend/src/test/java/com/qrwait/api/shared/notification
git commit -m "feat: 호출 알림 포트와 로그 어댑터 추가 (채널 토글 기본 log)"
```

---

### Task 2: 실패 SSE 발행 · 호출 알림 리스너

**Files:**
- Modify: `backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/CallNotificationListener.java`
- Test: `backend/src/test/java/com/qrwait/api/shared/sse/SsePublisherTest.java`
- Test: `backend/src/test/java/com/qrwait/api/shared/notification/CallNotificationListenerTest.java`

**Interfaces:**
- Consumes: `CallNotificationSender.send(CallNotification)` (Task 1), `WaitingCalledEvent(storeId, waitingId, phoneNumber, waitingNumber, storeName)` (기존).
- Produces:
  - `SsePublisher.notifyCallNotificationFailed(UUID storeId, int waitingNumber, String phoneNumber)`
  - `CallNotificationListener` — `@TransactionalEventListener(AFTER_COMMIT) onWaitingCalled(WaitingCalledEvent)`

- [ ] **Step 1: SsePublisher 실패 발행 테스트 작성**

`SsePublisherTest.java`:
```java
package com.qrwait.api.shared.sse;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.util.Map;
import java.util.UUID;
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

  @Test
  void notifyCallNotificationFailed_점주에게_실패_이벤트를_보낸다() {
    SsePublisher publisher = new SsePublisher(registry, waitingRepository, storeSettingsRepository);
    UUID storeId = UUID.randomUUID();

    publisher.notifyCallNotificationFailed(storeId, 3, "010-1234-5678");

    verify(registry).broadcastToOwner(eq(storeId), eq("call-notification-failed"),
        eq(Map.of("waitingNumber", 3, "phoneNumber", "010-1234-5678")));
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.sse.SsePublisherTest"`
Expected: 컴파일 실패 (`notifyCallNotificationFailed` 없음)

- [ ] **Step 3: SsePublisher에 실패 발행 메서드 추가**

`SsePublisher.java`의 `broadcastStoreStatus(...)` 메서드 아래에 추가:
```java
  /**
   * 호출 알림(SMS 등) 발송 실패 시 호출. 점주 대시보드에 실패한 손님 정보를 전달해 수동 연락을 유도한다.
   */
  public void notifyCallNotificationFailed(UUID storeId, int waitingNumber, String phoneNumber) {
    registry.broadcastToOwner(storeId, "call-notification-failed",
        Map.of("waitingNumber", waitingNumber, "phoneNumber", phoneNumber));
  }
```
> `java.util.Map`, `java.util.UUID`는 이미 import되어 있다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.sse.SsePublisherTest"`
Expected: PASS

- [ ] **Step 5: 리스너 테스트 작성 (성공/실패 경로)**

`CallNotificationListenerTest.java`:
```java
package com.qrwait.api.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qrwait.api.shared.sse.SsePublisher;
import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallNotificationListenerTest {

  @Mock
  CallNotificationSender sender;
  @Mock
  SsePublisher ssePublisher;

  private WaitingCalledEvent event(UUID storeId) {
    return new WaitingCalledEvent(storeId, UUID.randomUUID(), "010-1234-5678", 3, "테스트매장");
  }

  @Test
  void 호출_이벤트를_받으면_알림을_발송한다() {
    CallNotificationListener listener = new CallNotificationListener(sender, ssePublisher);
    UUID storeId = UUID.randomUUID();

    listener.onWaitingCalled(event(storeId));

    ArgumentCaptor<CallNotification> captor = ArgumentCaptor.forClass(CallNotification.class);
    verify(sender).send(captor.capture());
    assertThat(captor.getValue().waitingNumber()).isEqualTo(3);
    assertThat(captor.getValue().phoneNumber()).isEqualTo("010-1234-5678");
    assertThat(captor.getValue().storeName()).isEqualTo("테스트매장");
    verify(ssePublisher, never()).notifyCallNotificationFailed(any(), anyInt(), any());
  }

  @Test
  void 발송_실패하면_점주에게_실패_알림을_보낸다() {
    CallNotificationListener listener = new CallNotificationListener(sender, ssePublisher);
    UUID storeId = UUID.randomUUID();
    willThrow(new RuntimeException("boom")).given(sender).send(any());

    listener.onWaitingCalled(event(storeId));

    verify(ssePublisher).notifyCallNotificationFailed(eq(storeId), eq(3), eq("010-1234-5678"));
  }
}
```

- [ ] **Step 6: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.CallNotificationListenerTest"`
Expected: 컴파일 실패 (`CallNotificationListener` 없음)

- [ ] **Step 7: 리스너 구현**

`CallNotificationListener.java`:
```java
package com.qrwait.api.shared.notification;

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
public class CallNotificationListener {

  private final CallNotificationSender sender;
  private final SsePublisher ssePublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingCalled(WaitingCalledEvent event) {
    try {
      sender.send(new CallNotification(
          event.phoneNumber(), event.waitingNumber(), event.storeName()));
    } catch (Exception e) {
      log.error("호출 알림 발송 실패 — waitingNumber={}, phone={}",
          event.waitingNumber(), event.phoneNumber(), e);
      ssePublisher.notifyCallNotificationFailed(
          event.storeId(), event.waitingNumber(), event.phoneNumber());
    }
  }
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.CallNotificationListenerTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java backend/src/main/java/com/qrwait/api/shared/notification/CallNotificationListener.java backend/src/test/java/com/qrwait/api/shared/sse/SsePublisherTest.java backend/src/test/java/com/qrwait/api/shared/notification/CallNotificationListenerTest.java
git commit -m "feat: 호출 알림 리스너와 발송 실패 점주 SSE 알림 추가"
```

---

### Task 3: NHN Cloud SMS 어댑터 · 프로퍼티 · 채널 sms 등록

**Files:**
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/NhnSmsProperties.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/notification/NhnSmsSender.java`
- Modify: `backend/src/main/java/com/qrwait/api/shared/notification/NotificationConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/qrwait/api/shared/notification/NhnSmsSenderTest.java`
- Test(수정): `backend/src/test/java/com/qrwait/api/shared/notification/NotificationChannelToggleTest.java`

**Interfaces:**
- Consumes: `CallNotificationSender`, `CallNotification` (Task 1).
- Produces:
  - `record NhnSmsProperties(String appKey, String secretKey, String sendNo)` — `@ConfigurationProperties(prefix = "sms.nhn")`
  - `class NhnSmsSender implements CallNotificationSender` — 생성자 `(NhnSmsProperties, RestClient.Builder)`
  - `NotificationConfig` — `notification.channel=sms` 일 때 `NhnSmsSender` 등록

- [ ] **Step 1: NHN 프로퍼티 작성**

`NhnSmsProperties.java`:
```java
package com.qrwait.api.shared.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sms.nhn")
public record NhnSmsProperties(
    @DefaultValue("") String appKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("") String sendNo
) {

}
```

- [ ] **Step 2: NHN 어댑터 테스트 작성 (성공 요청 + 실패 응답)**

`NhnSmsSenderTest.java`:
```java
package com.qrwait.api.shared.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NhnSmsSenderTest {

  private static final String URL =
      "https://sms.api.nhncloudservice.com/sms/v3.0/appKeys/appkey123/sender/sms";

  private NhnSmsSender senderWith(MockRestServiceServer[] serverOut) {
    RestClient.Builder builder = RestClient.builder();
    serverOut[0] = MockRestServiceServer.bindTo(builder).build();
    NhnSmsProperties props = new NhnSmsProperties("appkey123", "secret456", "028881234");
    return new NhnSmsSender(props, builder);
  }

  @Test
  void send_NHN_규격의_요청을_보낸다() {
    MockRestServiceServer[] holder = new MockRestServiceServer[1];
    NhnSmsSender sender = senderWith(holder);

    holder[0].expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Secret-Key", "secret456"))
        .andExpect(jsonPath("$.sendNo").value("028881234"))
        .andExpect(jsonPath("$.body").value("[QR Wait] 3번 손님, 테스트매장 입장 안내드립니다. 매장으로 와주세요."))
        .andExpect(jsonPath("$.recipientList[0].recipientNo").value("01011112222"))
        .andRespond(withSuccess(
            "{\"header\":{\"isSuccessful\":true,\"resultCode\":0,\"resultMessage\":\"SUCCESS\"}}",
            MediaType.APPLICATION_JSON));

    sender.send(new CallNotification("010-1111-2222", 3, "테스트매장"));

    holder[0].verify();
  }

  @Test
  void send_NHN이_실패응답이면_예외를_던진다() {
    MockRestServiceServer[] holder = new MockRestServiceServer[1];
    NhnSmsSender sender = senderWith(holder);

    holder[0].expect(requestTo(URL))
        .andRespond(withSuccess(
            "{\"header\":{\"isSuccessful\":false,\"resultCode\":-1,\"resultMessage\":\"실패\"}}",
            MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> sender.send(new CallNotification("010-1111-2222", 3, "테스트매장")))
        .isInstanceOf(IllegalStateException.class);
  }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.NhnSmsSenderTest"`
Expected: 컴파일 실패 (`NhnSmsSender` 없음)

- [ ] **Step 4: NHN 어댑터 구현**

`NhnSmsSender.java`:
```java
package com.qrwait.api.shared.notification;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
public class NhnSmsSender implements CallNotificationSender {

  private static final String SEND_URL =
      "https://sms.api.nhncloudservice.com/sms/v3.0/appKeys/{appKey}/sender/sms";

  private final NhnSmsProperties properties;
  private final RestClient restClient;

  public NhnSmsSender(NhnSmsProperties properties, RestClient.Builder builder) {
    this.properties = properties;
    this.restClient = builder.build();
  }

  @Override
  public void send(CallNotification notification) {
    Map<String, Object> body = Map.of(
        "body", buildMessage(notification),
        "sendNo", properties.sendNo().replace("-", ""),
        "recipientList", List.of(Map.of(
            "recipientNo", notification.phoneNumber().replace("-", "")))
    );

    NhnSmsResponse response = restClient.post()
        .uri(SEND_URL, properties.appKey())
        .header("X-Secret-Key", properties.secretKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(NhnSmsResponse.class);

    if (response == null || response.header() == null || !response.header().isSuccessful()) {
      throw new IllegalStateException("NHN SMS 발송 실패 응답: " + response);
    }
    log.info("SMS 발송 성공: waitingNumber={}", notification.waitingNumber());
  }

  private String buildMessage(CallNotification n) {
    return "[QR Wait] %d번 손님, %s 입장 안내드립니다. 매장으로 와주세요."
        .formatted(n.waitingNumber(), n.storeName());
  }

  private record NhnSmsResponse(Header header) {

    private record Header(boolean isSuccessful, int resultCode, String resultMessage) {

    }
  }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.NhnSmsSenderTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 6: NotificationConfig에 sms 빈 등록**

`NotificationConfig.java` 를 아래로 교체 (기존 클래스에 `@EnableConfigurationProperties`와 sms 빈 추가):
```java
package com.qrwait.api.shared.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NhnSmsProperties.class)
public class NotificationConfig {

  @Bean
  @ConditionalOnProperty(name = "notification.channel", havingValue = "log", matchIfMissing = true)
  public CallNotificationSender loggingCallNotificationSender() {
    return new LoggingCallNotificationSender();
  }

  @Bean
  @ConditionalOnProperty(name = "notification.channel", havingValue = "sms")
  public CallNotificationSender nhnSmsSender(NhnSmsProperties properties, RestClient.Builder builder) {
    return new NhnSmsSender(properties, builder);
  }
}
```

- [ ] **Step 7: 채널 sms 토글 테스트 추가**

`NotificationChannelToggleTest.java`에 테스트 메서드 추가 (`import static org.assertj.core.api.Assertions.assertThat;`는 이미 있음):
```java
  @Test
  void 채널_sms면_NHN_어댑터가_등록된다() {
    runner.withPropertyValues("notification.channel=sms")
        .run(ctx -> assertThat(ctx).getBean(CallNotificationSender.class)
            .isInstanceOf(NhnSmsSender.class));
  }
```

- [ ] **Step 8: application.yml에 프로퍼티 추가**

`backend/src/main/resources/application.yml`의 `jwt:` 블록 아래(또는 최상위 적절한 위치)에 추가:
```yaml
notification:
  channel: ${NOTIFICATION_CHANNEL:log}   # log | sms

sms:
  nhn:
    app-key: ${NHN_SMS_APP_KEY:}
    secret-key: ${NHN_SMS_SECRET_KEY:}
    send-no: ${NHN_SMS_SEND_NO:}
```

- [ ] **Step 9: 전체 알림 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.shared.notification.*"`
Expected: PASS (LoggingCallNotificationSenderTest, NotificationChannelToggleTest 3개, NhnSmsSenderTest 2개, CallNotificationListenerTest 2개)

- [ ] **Step 10: 앱 부팅 회귀 확인 (기본 log로 문제없이 뜨는지)**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (기본 채널 `log`라 크리덴셜 없이 동작)

- [ ] **Step 11: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/notification backend/src/main/resources/application.yml backend/src/test/java/com/qrwait/api/shared/notification
git commit -m "feat: NHN Cloud SMS 어댑터와 채널 sms 토글 추가"
```

---

### Task 4: 점주 대시보드 발송 실패 배너 (프론트)

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx`

**Interfaces:**
- Consumes: 점주 대시보드 SSE 이벤트 `call-notification-failed`, data `{ waitingNumber:number, phoneNumber:string }` (Task 2에서 발행).

> 프론트에는 단위 테스트 프레임워크가 없다. 검증은 `npm run build`(타입체크) + `npm run lint` + 수동 확인으로 한다.

- [ ] **Step 1: 실패 배너 상태 추가**

`DashboardPage.tsx` 50번 줄 `const [alertBanner, setAlertBanner] = useState<string | null>(null)` 아래에 추가:
```tsx
  const [notifFailBanner, setNotifFailBanner] = useState<string | null>(null)
```

- [ ] **Step 2: SSE 파서에 이벤트 분기 추가**

`DashboardPage.tsx`의 `else if (eventName === 'alert-threshold-reached') { ... }` 블록 (118~124줄) 바로 다음에 추가:
```tsx
                } else if (eventName === 'call-notification-failed') {
                  try {
                    const data = JSON.parse(line.slice(5).trim())
                    setNotifFailBanner(
                        `${data.waitingNumber}번 손님 알림 발송 실패. 직접 연락해주세요. (${data.phoneNumber})`
                    )
                  } catch {
                    // payload 파싱 실패 시 무시
                  }
```

- [ ] **Step 3: 배너 렌더 추가**

`DashboardPage.tsx`의 `{alertBanner && ( ... )}` 블록(210~215줄) 바로 아래에 추가:
```tsx
        {notifFailBanner && (
            <div style={styles.notifFailBanner}>
              <span>⚠️ {notifFailBanner}</span>
              <button style={styles.alertClose} onClick={() => setNotifFailBanner(null)}>✕</button>
            </div>
        )}
```

- [ ] **Step 4: 배너 스타일 추가**

`DashboardPage.tsx`의 `styles` 객체에서 `alertBanner: { ... }` 정의 아래에 추가:
```tsx
  notifFailBanner: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '0.875rem 1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#fee2e2',
    border: '1px solid #fca5a5',
    fontSize: '0.875rem',
    fontWeight: 500,
    color: '#b91c1c',
  },
```

- [ ] **Step 5: 타입체크 + 린트**

Run: `cd frontend && npm run build && npm run lint`
Expected: 타입 오류·린트 오류 없이 성공

- [ ] **Step 6: 수동 확인 (선택, 인프라 필요)**

DB/Redis + 백엔드(`NOTIFICATION_CHANNEL=sms` + 잘못된 NHN 크리덴셜로 실패 유도) + 프론트 기동 후, 점주 로그인 → 손님 등록 → 점주 호출 시 대시보드 상단에 빨간 실패 배너가 뜨는지 확인. (자동화 아님)

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/pages/DashboardPage.tsx
git commit -m "feat: 점주 대시보드에 호출 알림 발송 실패 배너 추가"
```

---

### Task 5: NHN Cloud 설정 가이드 문서화

**Files:**
- Modify: `README.md`
- Modify: `.env.example`

**Interfaces:**
- Consumes: Task 3의 환경변수 `NOTIFICATION_CHANNEL`, `NHN_SMS_APP_KEY`, `NHN_SMS_SECRET_KEY`, `NHN_SMS_SEND_NO`.

- [ ] **Step 1: .env.example에 NHN 변수 추가**

`.env.example` 파일 끝에 추가:
```
# 호출 SMS 알림 (NHN Cloud). 미설정 시 채널은 log(콘솔 출력)로 동작.
NOTIFICATION_CHANNEL=log        # 실제 발송하려면 sms
NHN_SMS_APP_KEY=
NHN_SMS_SECRET_KEY=
NHN_SMS_SEND_NO=                # NHN 콘솔에서 사전 등록·승인된 발신번호
```

- [ ] **Step 2: README에 설정 가이드 섹션 추가**

`README.md`의 "전체 스택 Docker 배포"의 환경변수 표 아래, 또는 문서 하단 적절한 위치에 추가:
```markdown
### 호출 SMS 알림 (NHN Cloud) 설정

점주가 손님을 호출하면 손님에게 SMS로 입장 안내를 보낸다. 기본값은 `log`(콘솔 출력)라 별도 설정 없이 동작하며, 실제 발송은 아래 절차 후 `NOTIFICATION_CHANNEL=sms`로 활성화한다.

1. [NHN Cloud 콘솔](https://www.nhncloud.com/)에서 프로젝트 생성 → **Notification > SMS** 서비스 활성화.
2. **발신번호 등록**: 콘솔에서 소유 번호를 등록하고 서류·본인인증 승인을 받는다(전기통신사업법상 필수).
3. SMS 서비스의 **appKey**와 **SecretKey**(콘솔 > URL & Appkey / 보안 설정)를 확인한다.
4. 환경변수 주입:

   | 변수                   | 설명                          |
   |----------------------|-----------------------------|
   | `NOTIFICATION_CHANNEL` | `sms`로 설정 시 실제 발송 (기본 `log`) |
   | `NHN_SMS_APP_KEY`      | SMS 서비스 appKey              |
   | `NHN_SMS_SECRET_KEY`   | SMS 서비스 SecretKey           |
   | `NHN_SMS_SEND_NO`      | 등록·승인된 발신번호                 |

발송 실패 시 점주 대시보드 상단에 실패 배너가 표시되어 수동 연락을 유도한다. (재시도는 하지 않는다)
```

- [ ] **Step 3: 커밋**

```bash
git add README.md .env.example
git commit -m "docs: NHN Cloud SMS 알림 설정 가이드 추가"
```

---

## Self-Review

**1. Spec coverage:**
- 호출 시 알림 발송 → Task 2 (리스너) ✅
- SMS 채널(NHN Cloud) 어댑터 → Task 3 ✅
- 로그 어댑터(dev 기본, 크리덴셜 없이 부팅) → Task 1 ✅
- 발송 실패 → 점주 대시보드 배너(백+프론트) → Task 2(백) + Task 4(프론트) ✅
- 채널 교체 포트 → Task 1(포트) + Task 3(sms 어댑터) ✅
- `notification.channel` 토글 + 프로퍼티 → Task 1/3 ✅
- 재시도 없음 → Task 2 리스너 catch에서 단발 처리 ✅
- NHN 설정 가이드(내 폰 실발송) → Task 5 ✅
- 범위 밖(알림톡·웹푸시·등록/노쇼 알림) → 계획에 없음(의도적) ✅

**2. Placeholder scan:** "TBD/TODO/적절히 처리" 없음. 모든 코드·명령·기대출력 구체화됨. (Task 4 Step 6은 인프라 의존 수동 확인이라 "선택"으로 명시)

**3. Type consistency:**
- `CallNotification(String phoneNumber, int waitingNumber, String storeName)` — Task 1 정의, Task 2 리스너·Task 3 어댑터에서 동일 사용 ✅
- `CallNotificationSender.send(CallNotification)` — 전 태스크 일관 ✅
- `SsePublisher.notifyCallNotificationFailed(UUID, int, String)` — Task 2 정의/호출/테스트 일관 ✅
- `NhnSmsSender(NhnSmsProperties, RestClient.Builder)` — Task 3 정의·테스트 일관 ✅
- SSE 이벤트명 `call-notification-failed` — 백엔드(Task 2)·프론트(Task 4) 일치 ✅
- SSE data 필드 `waitingNumber`, `phoneNumber` — 백엔드 Map·프론트 파싱 일치 ✅
