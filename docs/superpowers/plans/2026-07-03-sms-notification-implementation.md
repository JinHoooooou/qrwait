# SMS 호출 알림 구현 계획 (v2 — NHN Cloud)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 점주가 손님을 호출하면 NHN Cloud SMS로 손님 전화번호에 알림 문자를 보내고, 발송 실패 시 점주 대시보드에 배너로 알린다.

**Architecture:** 기존 `WaitingCalledEvent`(이미 phoneNumber·waitingNumber·storeName 5필드로 확장 완료)를 `SmsNotificationListener`가 `@TransactionalEventListener(AFTER_COMMIT)`로 수신 → `SmsClient.send()` 호출 → 실패 시 `SmsSendException` 발생 → 리스너 catch → `SsePublisher.notifyOwnerSmsFailed()` → 점주 대시보드가 `sms-send-failed` 이벤트 수신 → 배너 표시. 기존 `SseEventListener`는 무영향.

**Tech Stack:** Java 21 · Spring Boot 3.5 · RestClient(내장) · MockWebServer(OkHttp, 테스트) · React · TypeScript

## Global Constraints

- 스펙: `docs/superpowers/specs/2026-07-03-sms-notification-design.md`
- Backend Task 1(v2 스펙 기준 "이미 반영됨") — `WaitingCalledEvent` 5필드 + `WaitingManagementService.call()` enriched 이벤트 발행 — **완료 상태이므로 재구현 금지**
- 메시지 포맷 (변경 금지): `"[QR Wait] %d번 손님, %s에서 입장 안내드립니다. 매장으로 와주세요."` — 순서 `waitingNumber` → `storeName`
- SSE 이벤트명: `sms-send-failed`, payload: `{"waitingNumber": <int>, "phoneNumber": "<hyphenated>"}`
- Config prefix: `sms`, 환경변수: `NHN_SMS_APP_KEY` / `NHN_SMS_SECRET_KEY` / `NHN_SMS_SENDER_NUMBER`
- NHN Cloud SMS: base URL `https://api-sms.cloud.toast.com`, 경로 `/sms/v3.0/appKeys/{appKey}/sender/sms`, 인증 헤더 `X-Secret-Key`
- 실패 감지: HTTP 예외 + `header.isSuccessful == false` 둘 다 → `SmsSendException`
- 타임아웃: connect 3초 / read 3초 (근거는 스펙 참조)
- 재시도 금지 (과금 중복 방지)
- 전화번호는 발송 시 하이픈 제거 (`010-1234-5678` → `01012345678`), SSE payload는 하이픈 유지
- 아키텍처 규칙: `backend/CLAUDE.md` — `shared/sms/`는 인프라 어댑터 (도메인 아님)

## 사전 준비

현재 브랜치 `refactor/actor-package-split`에 미커밋 변경이 있음:
- `M backend/src/main/resources/application.yml` — Task 1이 같은 파일을 수정하므로 **먼저 커밋 또는 stash 필요**
- `M docker-compose.yml` — 이 플랜 범위 밖, 그대로 둬도 무방

또한 이 플랜 자체를 다른 브랜치에서 진행하고 싶다면 실행 전에 브랜치 결정.

---

## File Structure

### Backend

| 파일 | 역할 | 크기 |
|---|---|---|
| `backend/build.gradle` | test 의존성에 `com.squareup.okhttp3:mockwebserver` 추가 | 수정 (1줄) |
| `backend/src/main/java/com/qrwait/api/shared/sms/SmsClient.java` | 인터페이스 — `send(to, content)` 계약(실패=예외) | 신규 (~10줄) |
| `backend/src/main/java/com/qrwait/api/shared/sms/SmsSendException.java` | RuntimeException — 발송 실패 표현 | 신규 (~10줄) |
| `backend/src/main/java/com/qrwait/api/shared/sms/SmsProperties.java` | `@ConfigurationProperties(prefix="sms")` record | 신규 (~15줄) |
| `backend/src/main/java/com/qrwait/api/shared/sms/SmsConfig.java` | `SmsClient` 빈 등록 + RestClient(timeout, baseUrl) 구성 | 신규 (~25줄) |
| `backend/src/main/java/com/qrwait/api/shared/sms/NhnCloudSmsClient.java` | NHN Cloud REST 호출 + 응답 검증 | 신규 (~55줄) |
| `backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java` | `@TransactionalEventListener(AFTER_COMMIT)` — 메시지 조립 + 실패 시 SSE 알림 | 신규 (~30줄) |
| `backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java` | `notifyOwnerSmsFailed(storeId, waitingNumber, phoneNumber)` 추가 | 수정 (+6줄) |
| `backend/src/main/resources/application.yml` | `sms.*` 환경변수 3개 | 수정 (+4줄) |
| `backend/src/test/java/com/qrwait/api/shared/sms/NhnCloudSmsClientTest.java` | 성공 / 응답 실패 두 케이스 | 신규 (~70줄) |
| `backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java` | 성공 / 실패 시 SSE 알림 두 케이스 | 신규 (~55줄) |
| `backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java` | `notifyOwnerSmsFailed` 검증 케이스 1개 추가 | 수정 (+10줄) |

### Frontend

| 파일 | 역할 | 크기 |
|---|---|---|
| `frontend/src/pages/DashboardPage.tsx` | SSE 파싱 `else if` 분기 + JSON.parse + 배너 세팅 | 수정 (+11줄) |

---

## Task 1: SMS 인프라 (Client · Properties · Config · NhnCloud 구현체)

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsClient.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsSendException.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsProperties.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsConfig.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/NhnCloudSmsClient.java`
- Create: `backend/src/test/java/com/qrwait/api/shared/sms/NhnCloudSmsClientTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: (없음 — 신규 인프라)
- Produces:
  - `interface SmsClient { void send(String to, String content); }` — 실패 시 `SmsSendException` throw
  - `class SmsSendException extends RuntimeException` — `(String)` / `(String, Throwable)` 생성자
  - `record SmsProperties(String appKey, String secretKey, String sender)` — Spring 바인딩
  - Spring 빈 `SmsClient` (`SmsConfig`에서 `NhnCloudSmsClient` 반환)

---

- [ ] **Step 1: 기준 테스트 통과 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: `build.gradle` — MockWebServer testImplementation 추가**

`dependencies { ... }` 블록 안, `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 아래에 다음 줄 추가:

```groovy
	testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
```

- [ ] **Step 3: 의존성 다운로드 확인**

```bash
cd backend && ./gradlew dependencies --configuration testRuntimeClasspath 2>&1 | grep mockwebserver
```

Expected: `+--- com.squareup.okhttp3:mockwebserver:4.12.0` 라인 출력

- [ ] **Step 4: `SmsClient.java` 생성**

```java
package com.qrwait.api.shared.sms;

public interface SmsClient {

  /**
   * @throws SmsSendException 발송 실패 (네트워크, provider 응답 실패 등)
   */
  void send(String to, String content);
}
```

- [ ] **Step 5: `SmsSendException.java` 생성**

```java
package com.qrwait.api.shared.sms;

public class SmsSendException extends RuntimeException {

  public SmsSendException(String message) {
    super(message);
  }

  public SmsSendException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 6: `SmsProperties.java` 생성**

```java
package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    @DefaultValue("") String appKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("") String sender
) {

}
```

- [ ] **Step 7: `NhnCloudSmsClientTest.java` 작성 (FAIL 예정)**

```java
package com.qrwait.api.shared.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NhnCloudSmsClientTest {

  private MockWebServer server;
  private NhnCloudSmsClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    SmsProperties properties = new SmsProperties("APPKEY123", "SECRET456", "01099998888");
    RestClient restClient = RestClient.builder()
        .baseUrl(server.url("/").toString().replaceAll("/$", ""))
        .build();
    client = new NhnCloudSmsClient(properties, restClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void send_성공_응답에서_예외를_던지지_않는다() throws InterruptedException {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""
            {"header":{"isSuccessful":true,"resultCode":0,"resultMessage":"SUCCESS"}}
            """));

    client.send("010-1234-5678", "[QR Wait] 3번 손님, 홍콩반점에서 입장 안내드립니다. 매장으로 와주세요.");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/sms/v3.0/appKeys/APPKEY123/sender/sms");
    assertThat(request.getHeader("X-Secret-Key")).isEqualTo("SECRET456");
    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"sendNo\":\"01099998888\"");
    assertThat(body).contains("\"recipientNo\":\"01012345678\""); // 하이픈 제거
    assertThat(body).contains("3번 손님");
  }

  @Test
  void send_isSuccessful_false_이면_SmsSendException을_던진다() {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""
            {"header":{"isSuccessful":false,"resultCode":-401,"resultMessage":"INVALID_SENDER"}}
            """));

    assertThatThrownBy(() -> client.send("010-1234-5678", "테스트"))
        .isInstanceOf(SmsSendException.class)
        .hasMessageContaining("INVALID_SENDER");
  }
}
```

- [ ] **Step 8: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.NhnCloudSmsClientTest" 2>&1 | tail -15
```

Expected: 컴파일 에러 — `cannot find symbol NhnCloudSmsClient`

- [ ] **Step 9: `NhnCloudSmsClient.java` 생성**

```java
package com.qrwait.api.shared.sms;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@RequiredArgsConstructor
public class NhnCloudSmsClient implements SmsClient {

  private static final String PATH_TEMPLATE = "/sms/v3.0/appKeys/{appKey}/sender/sms";

  private final SmsProperties properties;
  private final RestClient restClient;

  @Override
  public void send(String to, String content) {
    Map<String, Object> body = Map.of(
        "body", content,
        "sendNo", properties.sender().replace("-", ""),
        "recipientList", List.of(Map.of("recipientNo", to.replace("-", "")))
    );

    NhnSmsResponse response;
    try {
      response = restClient.post()
          .uri(PATH_TEMPLATE, properties.appKey())
          .header("X-Secret-Key", properties.secretKey())
          .body(body)
          .retrieve()
          .body(NhnSmsResponse.class);
    } catch (RestClientException e) {
      throw new SmsSendException("NHN Cloud SMS 호출 실패", e);
    }

    if (response == null || response.header() == null || !response.header().isSuccessful()) {
      String reason = (response == null || response.header() == null)
          ? "empty response"
          : response.header().resultMessage();
      throw new SmsSendException("NHN Cloud SMS 발송 실패: " + reason);
    }

    log.info("SMS 발송 성공: to={}", to);
  }

  private record NhnSmsResponse(Header header) {

    private record Header(boolean isSuccessful, int resultCode, String resultMessage) {

    }
  }
}
```

- [ ] **Step 10: 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test --tests "*.NhnCloudSmsClientTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` / 2 tests passed

- [ ] **Step 11: `SmsConfig.java` 생성**

```java
package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

  private static final String NHN_CLOUD_SMS_BASE_URL = "https://api-sms.cloud.toast.com";
  private static final int TIMEOUT_MS = 3_000;

  @Bean
  public SmsClient smsClient(SmsProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT_MS);
    factory.setReadTimeout(TIMEOUT_MS);

    RestClient restClient = RestClient.builder()
        .baseUrl(NHN_CLOUD_SMS_BASE_URL)
        .requestFactory(factory)
        .build();

    return new NhnCloudSmsClient(properties, restClient);
  }
}
```

- [ ] **Step 12: `application.yml` — SMS 설정 추가**

`logging:` 블록 앞에 다음 블록 추가 (`jwt:` 블록 아래):

```yaml
sms:
  app-key: ${NHN_SMS_APP_KEY:}
  secret-key: ${NHN_SMS_SECRET_KEY:}
  sender: ${NHN_SMS_SENDER_NUMBER:}
```

- [ ] **Step 13: 전체 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 14: 커밋**

```bash
git add backend/build.gradle
git add backend/src/main/java/com/qrwait/api/shared/sms/
git add backend/src/main/resources/application.yml
git add backend/src/test/java/com/qrwait/api/shared/sms/NhnCloudSmsClientTest.java
git commit -m "feat: SMS 발송 인프라 추가 (SmsClient · SmsSendException · SmsProperties · SmsConfig · NhnCloudSmsClient)"
```

---

## Task 2: SmsNotificationListener + SsePublisher.notifyOwnerSmsFailed

**Files:**
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java`
- Modify: `backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java`
- Create: `backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java`

**Interfaces:**
- Consumes:
  - `SmsClient.send(String to, String content)` throws `SmsSendException` (Task 1)
  - `WaitingCalledEvent(storeId, waitingId, phoneNumber, waitingNumber, storeName)` (이미 존재)
  - `SseEmitterRegistry.broadcastToOwner(UUID, String, Object)` (이미 존재)
- Produces:
  - `SsePublisher.notifyOwnerSmsFailed(UUID storeId, int waitingNumber, String phoneNumber)` — 점주 SSE로 `sms-send-failed` 발송
  - `SmsNotificationListener` — Spring `@Component`, `WaitingCalledEvent` 소비

---

- [ ] **Step 1: `SseEventListenerTest.java` — notifyOwnerSmsFailed 검증 케이스 추가**

파일 마지막 `}` 앞(마지막 테스트 메서드 다음)에 삽입:

```java
  @Test
  void notifyOwnerSmsFailed_점주_채널로_sms_send_failed_이벤트_발송() {
    UUID storeId = UUID.randomUUID();
    SsePublisher publisher = new SsePublisher(registry, waitingRepository, storeSettingsRepository);

    publisher.notifyOwnerSmsFailed(storeId, 3, "010-1234-5678");

    verify(registry).broadcastToOwner(eq(storeId), eq("sms-send-failed"), any());
  }
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인 (컴파일 에러)**

```bash
cd backend && ./gradlew test --tests "*.SseEventListenerTest" 2>&1 | tail -15
```

Expected: 컴파일 에러 — `cannot find symbol notifyOwnerSmsFailed`

- [ ] **Step 3: `SsePublisher.java` — notifyOwnerSmsFailed 추가**

`broadcastStoreStatus` 메서드 바로 다음에 추가 (파일 마지막 private 메서드 앞):

```java
  /**
   * SMS 발송 실패 시 호출. 점주 채널에만 발신하며 손님 채널에는 영향 없다.
   */
  public void notifyOwnerSmsFailed(UUID storeId, int waitingNumber, String phoneNumber) {
    registry.broadcastToOwner(storeId, "sms-send-failed",
        Map.of("waitingNumber", waitingNumber, "phoneNumber", phoneNumber));
  }
```

- [ ] **Step 4: `SseEventListenerTest` 통과 확인**

```bash
cd backend && ./gradlew test --tests "*.SseEventListenerTest" 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: `SmsNotificationListenerTest.java` 작성 (FAIL 예정)**

```java
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
  void onWaitingCalled_성공시_SMS만_발송하고_SSE_알림_없음() {
    WaitingCalledEvent event = new WaitingCalledEvent(
        storeId, waitingId, "010-1234-5678", 3, "홍콩반점");

    listener.onWaitingCalled(event);

    verify(smsClient).send(eq("010-1234-5678"), contains("3번 손님"));
    verify(smsClient).send(eq("010-1234-5678"), contains("홍콩반점"));
    verify(ssePublisher, never()).notifyOwnerSmsFailed(any(), any(int.class), any());
  }

  @Test
  void onWaitingCalled_SmsSendException_이면_점주_SSE_알림() {
    WaitingCalledEvent event = new WaitingCalledEvent(
        storeId, waitingId, "010-1234-5678", 3, "홍콩반점");
    doThrow(new SmsSendException("NHN Cloud SMS 발송 실패: INVALID_SENDER"))
        .when(smsClient).send(any(), any());

    listener.onWaitingCalled(event);

    verify(ssePublisher).notifyOwnerSmsFailed(storeId, 3, "010-1234-5678");
  }
}
```

- [ ] **Step 6: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.SmsNotificationListenerTest" 2>&1 | tail -15
```

Expected: 컴파일 에러 — `cannot find symbol SmsNotificationListener`

- [ ] **Step 7: `SmsNotificationListener.java` 생성**

```java
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
      log.error("SMS 발송 실패: waitingNumber={}, phone={}",
          event.waitingNumber(), event.phoneNumber(), e);
      ssePublisher.notifyOwnerSmsFailed(
          event.storeId(), event.waitingNumber(), event.phoneNumber());
    }
  }
}
```

- [ ] **Step 8: 전체 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java
git add backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java
git add backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java
git add backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java
git commit -m "feat: SMS 발송 실패 시 점주 SSE 알림 리스너 추가"
```

---

## Task 3: Frontend — DashboardPage `sms-send-failed` 배너

> **선행 조건:** Task 1·2 완료 (백엔드가 `sms-send-failed` 이벤트를 발행해야 통합 검증 가능)

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx:118-124`

**Interfaces:**
- Consumes: SSE 이벤트 `sms-send-failed`, payload `{ waitingNumber: number, phoneNumber: string }`
- Produces: (UI 변경만)

---

- [ ] **Step 1: `DashboardPage.tsx` — `sms-send-failed` 파싱 분기 추가**

파일 118~124줄 (`else if (eventName === 'alert-threshold-reached') { ... }` 블록) 다음, `eventName = ''` 초기화 앞에 새 `else if` 추가.

수정 전 (참고):
```tsx
                } else if (eventName === 'alert-threshold-reached') {
                  if (Notification.permission === 'granted') {
                    new Notification('웨이팅 알림', {body: '대기자 수가 임계값을 초과했습니다.'})
                  } else {
                    setAlertBanner('대기자 수가 임계값을 초과했습니다.')
                  }
                }
                eventName = ''
```

수정 후:
```tsx
                } else if (eventName === 'alert-threshold-reached') {
                  if (Notification.permission === 'granted') {
                    new Notification('웨이팅 알림', {body: '대기자 수가 임계값을 초과했습니다.'})
                  } else {
                    setAlertBanner('대기자 수가 임계값을 초과했습니다.')
                  }
                } else if (eventName === 'sms-send-failed') {
                  try {
                    const data = JSON.parse(line.slice(5).trim())
                    setAlertBanner(
                        `⚠️ #${data.waitingNumber}번 손님 SMS 발송 실패. 직접 연락해주세요. (${data.phoneNumber})`
                    )
                  } catch {
                    setAlertBanner('⚠️ SMS 발송 실패. 손님에게 직접 연락해주세요.')
                  }
                }
                eventName = ''
```

- [ ] **Step 2: 타입 체크**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 에러 없음

- [ ] **Step 3: 수동 검증 시나리오 (선택)**

```bash
cd frontend && npm run dev
```

브라우저에서 `/owner/login` → 로그인 → `/owner/dashboard`.

- 실패 시뮬레이션: `NHN_SMS_APP_KEY`를 빈 값으로 두거나 잘못된 값으로 백엔드 재기동 후 점주가 "호출" 버튼 클릭 → 대시보드 상단에 `⚠️ #N번 손님 SMS 발송 실패. 직접 연락해주세요. (010-XXXX-XXXX)` 배너 표시 확인.
- 정상 발송 시뮬레이션(키·발신번호 등록 상태): 배너 없음.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/DashboardPage.tsx
git commit -m "feat: 대시보드 SMS 발송 실패 배너 추가 (sms-send-failed SSE)"
```

---

## Task 4: README — SMS 알림 설정 및 로컬 테스트 가이드

> **선행 조건:** Task 1~3 완료 (환경변수 이름, SSE 배너 흐름이 확정된 후 문서화)

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 환경변수 이름 `NHN_SMS_APP_KEY` / `NHN_SMS_SECRET_KEY` / `NHN_SMS_SENDER_NUMBER` (Task 1)
- Produces: (문서만)

---

- [ ] **Step 1: `README.md` — 환경변수 표에 NHN SMS 세 항목 추가**

`.env` 환경변수 표 (`APP_BASE_URL` 행 아래) 에 다음 3행 추가:

```markdown
| `NHN_SMS_APP_KEY`        | NHN Cloud SMS 프로젝트 AppKey (선택)    | (콘솔에서 발급)          |
| `NHN_SMS_SECRET_KEY`     | NHN Cloud SMS SecretKey (선택)      | (콘솔에서 발급)          |
| `NHN_SMS_SENDER_NUMBER`  | 사전 등록된 발신번호, 하이픈 무관 (선택)          | `01099998888`         |
```

행 뒤에 다음 안내문 추가:

```markdown
> `NHN_SMS_*` 세 값은 **선택 사항**입니다. 미설정 시 앱 구동은 정상 진행되며, 손님 호출 시점에 SMS 발송이 실패하고 점주 대시보드 상단에 "⚠️ SMS 발송 실패, 직접 연락해주세요" 배너가 표시됩니다.
```

- [ ] **Step 2: `README.md` — "SMS 알림 (NHN Cloud)" 섹션 신설**

`## 관련 문서` 섹션 바로 앞에 새 섹션 추가:

```markdown
---

## SMS 알림 (NHN Cloud)

손님이 화면을 닫아도 호출 알림을 받을 수 있도록, 점주가 호출 버튼을 누르는 시점에 NHN Cloud SMS로 손님 전화번호에 문자를 발송합니다.

- 트리거: 점주 호출 (`WAITING` → `CALLED`)
- 발송자: 사전 등록된 발신번호
- 실패 처리: 재시도 없이 점주 대시보드에 SSE 배너로 즉시 알림 (과금 중복 방지)

### 로컬 개발에서의 SMS 테스트

세 가지 방식이 있습니다. 목적에 따라 선택하세요.

#### 방식 A. 실 발송 검증 (본인 폰으로만)

실제 문자가 도착하는지 확인하고 싶을 때:

1. `NHN_SMS_APP_KEY` / `NHN_SMS_SECRET_KEY` / `NHN_SMS_SENDER_NUMBER` 세 값 세팅 후 백엔드 재기동.
2. 손님 등록 화면에서 **본인 폰번호 입력** → 점주 계정으로 로그인해 해당 웨이팅에 "호출" 클릭.
3. 폰에 SMS 도착 확인. NHN Cloud 콘솔의 "발송 결과 조회" 에서 상세 로그 확인 가능.

**주의**: 손님 폰번호는 입력값 그대로 발송되므로, 로컬 테스트 시 반드시 본인 번호만 사용하세요. 회당 SMS 단문 요금(약 9원)이 회사 계정에서 차감됩니다.

#### 방식 B. 실패 경로 검증 (배너 흐름 확인)

발송 실패 시 점주 대시보드 배너가 뜨는지 확인할 때:

1. `NHN_SMS_*` 환경변수를 **비워둔 채로** 백엔드 기동 (기본값).
2. 손님 등록 → 점주 "호출" 클릭.
3. 대시보드 상단에 `⚠️ #N번 손님 SMS 발송 실패. 직접 연락해주세요. (010-XXXX-XXXX)` 배너 표시 확인.

#### 방식 C. 자동화 테스트

`NhnCloudSmsClientTest` 가 MockWebServer 로 성공/실패 응답 시나리오를 검증합니다.

```bash
cd backend && ./gradlew test --tests "*.NhnCloudSmsClientTest"
```

### 관련 스펙

- 설계: [`docs/superpowers/specs/2026-07-03-sms-notification-design.md`](./docs/superpowers/specs/2026-07-03-sms-notification-design.md)
- 구현 플랜: [`docs/superpowers/plans/2026-07-03-sms-notification-implementation.md`](./docs/superpowers/plans/2026-07-03-sms-notification-implementation.md)

```

- [ ] **Step 3: 링크 유효성 확인**

새로 추가한 문서 링크(`docs/superpowers/specs/2026-07-03-...`, `docs/superpowers/plans/2026-07-03-...`)가 실제 파일과 일치하는지 확인.

```bash
ls docs/superpowers/specs/2026-07-03-sms-notification-design.md docs/superpowers/plans/2026-07-03-sms-notification-implementation.md
```

Expected: 두 파일 모두 존재.

- [ ] **Step 4: 커밋**

```bash
git add README.md
git commit -m "docs: SMS 알림 설정 및 로컬 테스트 가이드 추가"
```

---

## Self-Review

### 스펙 커버리지

| 스펙 요구사항 | 담당 태스크 |
|---|---|
| `SmsClient` 인터페이스 (실패 = 예외) | Task 1 Step 4 |
| `SmsSendException` | Task 1 Step 5 |
| `SmsProperties` (`app-key` / `secret-key` / `sender`, 기본값 빈 문자열) | Task 1 Step 6 |
| `NhnCloudSmsClient` — X-Secret-Key 헤더, `/sms/v3.0/appKeys/{appKey}/sender/sms`, 하이픈 제거 | Task 1 Step 9 |
| 응답 `header.isSuccessful` 검사 → `SmsSendException` | Task 1 Step 9 |
| `RestClientException` 을 `SmsSendException`으로 감쌈 | Task 1 Step 9 |
| RestClient 3초 timeout (connect + read) | Task 1 Step 11 (SmsConfig) |
| `application.yml` `sms.*` 환경변수 | Task 1 Step 12 |
| `SmsNotificationListener` — `@TransactionalEventListener(AFTER_COMMIT)` | Task 2 Step 7 |
| 메시지 포맷 `[QR Wait] %d번 손님, %s에서 ...` | Task 2 Step 7 |
| 실패 catch → `SsePublisher.notifyOwnerSmsFailed` | Task 2 Step 7 |
| `SsePublisher.notifyOwnerSmsFailed(storeId, waitingNumber, phoneNumber)` | Task 2 Step 3 |
| SSE 이벤트명 `sms-send-failed`, payload `{waitingNumber, phoneNumber}` | Task 2 Step 3 (백엔드) + Task 3 Step 1 (프론트) |
| Frontend 배너 표시 | Task 3 Step 1 |
| 카카오 알림톡 확장 경로 (SmsClient 인터페이스 기반) | Task 1 Step 4 (인터페이스만 있어도 충족) |
| 기존 `SseEventListener` 무영향 | Task 2 (별도 리스너 신설, 기존 파일 미수정) |
| Task 1(v1) — WaitingCalledEvent 5필드 | ✅ 완료 (재작업 없음) |
| 환경변수·로컬 테스트 방식 문서화 | Task 4 |

### 타입 · 시그니처 일관성

- `SmsClient.send(String to, String content)` — Task 1 Step 4 인터페이스 ↔ Task 1 Step 9 impl ↔ Task 2 Step 7 리스너 호출 ✓
- `SmsSendException` — Task 1 Step 5 정의 ↔ Task 1 Step 9 throw ↔ Task 2 Step 5 테스트 doThrow ✓
- `SmsProperties(appKey, secretKey, sender)` — Task 1 Step 6 record ↔ Task 1 Step 9 `properties.appKey()/secretKey()/sender()` ↔ Task 1 Step 11 `SmsConfig` DI ↔ Task 1 Step 12 yml `sms.app-key/secret-key/sender` ✓
- `WaitingCalledEvent(storeId, waitingId, phoneNumber, waitingNumber, storeName)` — 이미 존재 ↔ Task 2 Step 5·7 사용 ✓
- `SsePublisher.notifyOwnerSmsFailed(UUID storeId, int waitingNumber, String phoneNumber)` — Task 2 Step 1 테스트 검증 ↔ Task 2 Step 3 구현 ↔ Task 2 Step 5·7 리스너 호출 ✓
- SSE payload `{waitingNumber: int, phoneNumber: string}` — Task 2 Step 3 (`Map.of("waitingNumber", int, "phoneNumber", string)`) ↔ Task 3 Step 1 (`data.waitingNumber`, `data.phoneNumber`) ✓
- 메시지 포맷 `"[QR Wait] %d번 손님, %s에서 입장 안내드립니다. 매장으로 와주세요."` — Task 2 Step 5 테스트 (`contains("3번 손님")`, `contains("홍콩반점")`) ↔ Task 2 Step 7 구현 (`.formatted(waitingNumber, storeName)`) ✓
- HTTP 경로 `/sms/v3.0/appKeys/{appKey}/sender/sms` — Task 1 Step 7 테스트 (`assertThat(request.getPath()).isEqualTo(...)`) ↔ Task 1 Step 9 impl `PATH_TEMPLATE` ✓

### Placeholder scan

플랜 전체를 훑어 "TBD", "TODO", "구현 예정", "적절한 에러 처리 추가" 같은 문구 없음. 모든 코드 단계가 완전한 소스로 작성됨.

### 태스크 크기

- Task 1: 14 스텝, 인프라 6파일 신규 + build.gradle/yml 수정 — 세션 하나에 처리 가능.
- Task 2: 9 스텝, 리스너 1파일 신규 + SsePublisher/테스트 2파일 수정 — 소형.
- Task 3: 4 스텝, 프론트 1파일 수정 — 초소형.
- Task 4: 4 스텝, README 문서만 — 초소형. Task 3 완료 후 실행.

각 태스크는 독립 검증(테스트 실행 + 수동 검증) 가능하며 리뷰 게이트로 적절.

---

## Execution Handoff

플랜 완성 및 저장: `docs/superpowers/plans/2026-07-03-sms-notification-implementation.md`

**두 가지 실행 옵션:**

**1. Subagent-Driven (권장)** — 태스크마다 fresh 서브에이전트 디스패치, 태스크 사이 리뷰 게이트. 문서 크기 · 코드 컨텍스트가 적절히 나뉘어 리뷰가 쉬움.

**2. Inline Execution** — 이 세션에서 그대로 실행, 체크포인트로 리뷰. 컨텍스트 유지가 유리하고 즉시 시작 가능.

어느 쪽으로 진행할까요?
