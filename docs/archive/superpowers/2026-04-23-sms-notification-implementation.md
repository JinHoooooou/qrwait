# SMS 호출 알림 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Backend 세션:** Backend Tasks 1–3 실행
> **Frontend 세션:** Frontend Task 1 실행 (Backend Tasks 1–3 완료 후)

**Goal:** 점주가 손님을 호출하면 해당 손님의 전화번호로 SMS를 발송하고, 실패 시 점주 대시보드에 알림 배너를 표시한다.

**Architecture:** 기존 `WaitingCalledEvent`에 phoneNumber·waitingNumber·storeName을 추가해 enriched 이벤트로 교체한다. `SmsNotificationListener`가
`@TransactionalEventListener(AFTER_COMMIT)`으로 이벤트를 수신해 Solapi REST API로 SMS를 발송하며, 실패 시 `SsePublisher`를 통해 점주 대시보드에 `sms-send-failed` 이벤트를 전송한다. 기존
`SseEventListener`는 코드 변경 없이 호환된다.

**Tech Stack:** Spring Boot 3.5 / RestClient(내장) / HmacSHA256 / Solapi REST API / React / TypeScript

---

## 파일 맵

### Backend

| 파일                                                  | 역할                                                             |
|-----------------------------------------------------|----------------------------------------------------------------|
| `waiting/domain/event/WaitingCalledEvent.java`      | phoneNumber·waitingNumber·storeName 필드 추가                      |
| `waiting/application/WaitingManagementService.java` | call()에서 Store 직접 조회 후 enriched 이벤트 발행                         |
| `shared/sse/SsePublisher.java`                      | `notifyOwnerSmsFailed()` 메서드 추가                                |
| `shared/sms/SmsClient.java`                         | 신규 — SMS 발송 인터페이스                                              |
| `shared/sms/SmsProperties.java`                     | 신규 — `@ConfigurationProperties(prefix = "sms")`                |
| `shared/sms/SmsConfig.java`                         | 신규 — `@EnableConfigurationProperties` + `SmsClient` 빈 등록       |
| `shared/sms/SolapiSmsClient.java`                   | 신규 — Solapi REST API HmacSHA256 구현체                            |
| `shared/sms/SmsNotificationListener.java`           | 신규 — `@TransactionalEventListener(AFTER_COMMIT)`               |
| `resources/application.yml`                         | sms.api-key·api-secret·sender 환경변수 추가                          |
| `test/.../WaitingManagementServiceTest.java`        | call() enriched 이벤트 검증으로 업데이트                                  |
| `test/.../SseEventListenerTest.java`                | WaitingCalledEvent 5인자 생성자로 업데이트 + notifyOwnerSmsFailed 테스트 추가 |
| `test/.../SmsNotificationListenerTest.java`         | 신규 — 성공·실패 케이스 단위 테스트                                          |

### Frontend

| 파일                                     | 역할                                    |
|----------------------------------------|---------------------------------------|
| `frontend/src/pages/DashboardPage.tsx` | `sms-send-failed` SSE 이벤트 핸들러 + 배너 표시 |

---

## Backend Tasks

---

### Backend Task 1: WaitingCalledEvent 보강 + 영향받는 테스트 수정

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/event/WaitingCalledEvent.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java`
- Modify: `backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java`

- [ ] **Step 1: 현재 테스트 실행 — PASS 기준선 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: WaitingCalledEvent.java — 5개 필드로 교체**

```java
package com.qrwait.api.waiting.domain.event;

import java.util.UUID;

public record WaitingCalledEvent(
    UUID storeId,
    UUID waitingId,
    String phoneNumber,
    int waitingNumber,
    String storeName
) {

}
```

- [ ] **Step 3: 컴파일 에러 확인**

```bash
cd backend && ./gradlew compileTestJava 2>&1 | grep "error:"
```

Expected: `SseEventListenerTest.java`에서 2인자 생성자 에러, `WaitingManagementServiceTest.java`에서 2인자 생성자 에러 출력

- [ ] **Step 4: SseEventListenerTest.java — 5인자 생성자로 수정**

`onWaitingCalled_손님에게_waitingId_포함_브로드캐스트` 테스트에서:

```java
listener.onWaitingCalled(new WaitingCalledEvent(storeId, waitingId, "010-1234-5678",1,"테스트 매장"));
```

- [ ] **Step 5: WaitingManagementServiceTest.java — call_정상_호출처리 업데이트**

`call_정상_호출처리` 테스트를 아래로 교체:

```java

@Test
void call_정상_호출처리() {
  WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());
  given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
  given(storeRepository.findByOwnerId(ownerId))
      .willReturn(Optional.of(Store.restore(storeId, ownerId, "홍콩반점", "서울", StoreStatus.OPEN, LocalDateTime.now())));
  given(waitingRepository.save(any())).willReturn(entry);

  service.call(ownerId, waitingId);

  verify(waitingRepository).save(any());
  then(eventPublisher).should().publishEvent(
      new WaitingCalledEvent(storeId, waitingId, "010-1111-0001", 1, "홍콩반점")
  );
}
```

- [ ] **Step 6: 테스트 실행 — FAIL 확인 (WaitingManagementService.call()이 아직 2인자 이벤트 발행)**

```bash
cd backend && ./gradlew test --tests "*.WaitingManagementServiceTest.call_정상_호출처리" 2>&1 | tail -15
```

Expected: FAIL — 이벤트 인자 불일치

- [ ] **Step 7: WaitingManagementService.java — call() 메서드 업데이트**

기존 `call()` 메서드를 아래로 교체:

```java

@Transactional
public void call(UUID ownerId, UUID waitingId) {
  WaitingEntry entry = waitingRepository.findById(waitingId)
      .orElseThrow(() -> new WaitingNotFoundException(waitingId));

  com.qrwait.api.store.domain.Store store = storeRepository.findByOwnerId(ownerId)
      .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));

  if (!store.getId().equals(entry.getStoreId())) {
    throw new StoreNotFoundException("ownerId=" + ownerId);
  }

  WaitingEntry called = entry.call();
  waitingRepository.save(called);

  eventPublisher.publishEvent(new WaitingCalledEvent(
      called.getStoreId(),
      waitingId,
      called.getPhoneNumber(),
      called.getWaitingNumber(),
      store.getName()
  ));
}
```

- [ ] **Step 8: 전체 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/domain/event/WaitingCalledEvent.java
git add backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java
git add backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java
git add backend/src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java
git commit -m "refactor: WaitingCalledEvent에 phoneNumber·waitingNumber·storeName 추가"
```

---

### Backend Task 2: SMS 인프라 — SmsClient·SmsProperties·SmsConfig·SolapiSmsClient

**Files:**

- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsClient.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsProperties.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsConfig.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SolapiSmsClient.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: SmsClient.java 생성**

```java
package com.qrwait.api.shared.sms;

public interface SmsClient {

  void send(String to, String content);
}
```

- [ ] **Step 2: SmsProperties.java 생성**

```java
package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("") String apiSecret,
    @DefaultValue("") String sender
) {

}
```

- [ ] **Step 3: SolapiSmsClient.java 생성**

Solapi REST API 호출. 전화번호는 하이픈 제거 후 전송 (`010-1234-5678` → `01012345678`).

```java
package com.qrwait.api.shared.sms;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class SolapiSmsClient implements SmsClient {

  private static final String SOLAPI_URL = "https://api.solapi.com/messages/v4/send";

  private final SmsProperties smsProperties;
  private final RestClient restClient;

  @Override
  public void send(String to, String content) {
    String date = Instant.now().toString();
    String salt = UUID.randomUUID().toString().replace("-", "");
    String signature = hmacSha256(smsProperties.apiSecret(), date + salt);

    String authorization = "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s"
        .formatted(smsProperties.apiKey(), date, salt, signature);

    Map<String, Object> body = Map.of(
        "message", Map.of(
            "to", to.replace("-", ""),
            "from", smsProperties.sender().replace("-", ""),
            "text", content
        )
    );

    restClient.post()
        .uri(SOLAPI_URL)
        .header("Authorization", authorization)
        .body(body)
        .retrieve()
        .toBodilessEntity();

    log.info("SMS 발송 성공: to={}", to);
  }

  private String hmacSha256(String secret, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new RuntimeException("HMAC-SHA256 서명 생성 실패", e);
    }
  }
}
```

- [ ] **Step 4: SmsConfig.java 생성**

```java
package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

  @Bean
  public SmsClient smsClient(SmsProperties smsProperties) {
    return new SolapiSmsClient(smsProperties, RestClient.create());
  }
}
```

- [ ] **Step 5: application.yml — SMS 환경변수 추가**

`logging.level` 블록 앞에 추가:

```yaml
sms:
  api-key: ${SMS_API_KEY:}
  api-secret: ${SMS_API_SECRET:}
  sender: ${SMS_SENDER_NUMBER:}
```

- [ ] **Step 6: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` (컴파일 에러 없음)

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/sms/
git add backend/src/main/resources/application.yml
git commit -m "feat: SMS 발송 인프라 추가 (SmsClient, SolapiSmsClient, SmsConfig, SmsProperties)"
```

---

### Backend Task 3: SmsNotificationListener + SsePublisher.notifyOwnerSmsFailed

**Files:**

- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java`
- Modify: `backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java`
- Create: `backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java`

- [ ] **Step 1: SmsNotificationListenerTest.java 작성**

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
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.SmsNotificationListenerTest" 2>&1 | tail -15
```

Expected: FAIL — `SmsNotificationListener`, `SsePublisher.notifyOwnerSmsFailed` 없음

- [ ] **Step 3: SmsNotificationListener.java 생성**

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
      log.error("SMS 발송 실패: waitingNumber={}, phone={}", event.waitingNumber(), event.phoneNumber(), e);
      ssePublisher.notifyOwnerSmsFailed(event.storeId(), event.waitingNumber(), event.phoneNumber());
    }
  }
}
```

- [ ] **Step 4: 테스트 실행 — FAIL 확인 (notifyOwnerSmsFailed 미구현)**

```bash
cd backend && ./gradlew test --tests "*.SmsNotificationListenerTest" 2>&1 | tail -15
```

Expected: FAIL — `notifyOwnerSmsFailed` 메서드 없음

- [ ] **Step 5: SsePublisher.java — notifyOwnerSmsFailed 추가**

`broadcastStoreStatus` 메서드 아래에 추가:

```java
public void notifyOwnerSmsFailed(UUID storeId, int waitingNumber, String phoneNumber) {
  registry.broadcastToOwner(storeId, "sms-send-failed",
      Map.of("waitingNumber", waitingNumber, "phoneNumber", phoneNumber));
}
```

- [ ] **Step 6: SseEventListenerTest.java — notifyOwnerSmsFailed 테스트 추가**

`SseEventListenerTest.java` 파일 끝(마지막 `}` 앞)에 추가:

```java

@Test
void notifyOwnerSmsFailed_점주_SSE에_실패_이벤트_전송() {
  UUID storeId = UUID.randomUUID();
  SsePublisher publisher = new SsePublisher(registry, waitingRepository, storeSettingsRepository);

  publisher.notifyOwnerSmsFailed(storeId, 3, "010-1234-5678");

  verify(registry).broadcastToOwner(eq(storeId), eq("sms-send-failed"), any());
}
```

- [ ] **Step 7: 전체 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java
git add backend/src/main/java/com/qrwait/api/shared/sse/SsePublisher.java
git add backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java
git add backend/src/test/java/com/qrwait/api/shared/sse/SseEventListenerTest.java
git commit -m "feat: SMS 호출 알림 리스너 추가 (실패 시 점주 SSE 알림)"
```

---

## Frontend Tasks

---

### Frontend Task 1: DashboardPage — sms-send-failed 핸들러

> **선행 조건:** Backend Tasks 1–3 완료 후 실행 (SSE `sms-send-failed` 이벤트는 백엔드에서 발행됨)

**Files:**

- Modify: `frontend/src/pages/DashboardPage.tsx`

- [ ] **Step 1: DashboardPage.tsx — sms-send-failed 이벤트 핸들러 추가**

SSE 이벤트 파싱 블록에서 기존 `alert-threshold-reached` 분기 아래에 추가:

```typescript
} else
if (eventName === 'sms-send-failed') {
  try {
    const rawData = line.slice(5).trim()
    const data = JSON.parse(rawData)
    setAlertBanner(
        `⚠️ #${data.waitingNumber}번 손님 SMS 발송 실패. 직접 연락해주세요. (${data.phoneNumber})`
    )
  } catch {
    setAlertBanner('⚠️ SMS 발송 실패. 손님에게 직접 연락해주세요.')
  }
}
```

정확한 위치 — `DashboardPage.tsx`의 SSE 파싱 블록:

```typescript
if (eventName === 'waiting-registered' || eventName === 'waiting-updated') {
  fetchWaitingList()
  fetchSummary()
} else if (eventName === 'alert-threshold-reached') {
  if (Notification.permission === 'granted') {
    new Notification('웨이팅 알림', {body: '대기자 수가 임계값을 초과했습니다.'})
  } else {
    setAlertBanner('대기자 수가 임계값을 초과했습니다.')
  }
} else if (eventName === 'sms-send-failed') {       // ← 여기 추가
  try {
    const rawData = line.slice(5).trim()
    const data = JSON.parse(rawData)
    setAlertBanner(
        `⚠️ #${data.waitingNumber}번 손님 SMS 발송 실패. 직접 연락해주세요. (${data.phoneNumber})`
    )
  } catch {
    setAlertBanner('⚠️ SMS 발송 실패. 손님에게 직접 연락해주세요.')
  }
}
```

- [ ] **Step 2: 동작 확인**

```bash
cd frontend && npm run dev
```

브라우저에서 `/owner/dashboard` 접속 후:

- 정상 흐름: 손님 호출 → 손님 전화번호로 SMS 발송 (SMS_API_KEY 설정 시)
- 실패 시뮬레이션: `SMS_API_KEY`를 빈 값으로 두고 호출 → 상단에 "⚠️ #N번 손님 SMS 발송 실패. 직접 연락해주세요. (010-XXXX-XXXX)" 배너가 표시되는지 확인

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/pages/DashboardPage.tsx
git commit -m "feat: 대시보드 SMS 발송 실패 알림 배너 추가 (sms-send-failed SSE 이벤트)"
```

---

## 셀프 리뷰

### 스펙 커버리지 체크

| 스펙 요구사항                                                           | 구현 태스크                                            |
|-------------------------------------------------------------------|---------------------------------------------------|
| 호출 시 SMS 발송 (CALLED 상태 전이)                                        | Backend Task 3 (SmsNotificationListener)          |
| WaitingCalledEvent enriched (phoneNumber·waitingNumber·storeName) | Backend Task 1                                    |
| WaitingManagementService.call()에서 Store 직접 조회 후 enriched 이벤트 발행   | Backend Task 1 Step 7                             |
| SmsClient 인터페이스                                                   | Backend Task 2                                    |
| SolapiSmsClient HmacSHA256 구현체                                    | Backend Task 2                                    |
| SmsProperties @ConfigurationProperties                            | Backend Task 2                                    |
| @TransactionalEventListener(AFTER_COMMIT)                         | Backend Task 3                                    |
| SMS 실패 시 로그 + SsePublisher.notifyOwnerSmsFailed()                 | Backend Task 3                                    |
| sms-send-failed SSE 이벤트 발행                                        | Backend Task 3 (SsePublisher)                     |
| DashboardPage sms-send-failed 핸들러 + 배너                            | Frontend Task 1                                   |
| application.yml SMS 환경변수 (기본값 빈 문자열)                              | Backend Task 2 Step 5                             |
| 기존 SseEventListener 무변경 호환                                        | Backend Task 1 (5인자 레코드, storeId·waitingId 위치 동일) |
| 카카오 확장 경로 (SmsClient 인터페이스 기반)                                    | Backend Task 2 (SmsClient 인터페이스)                  |

### 타입 일관성 체크

- `WaitingCalledEvent(storeId, waitingId, phoneNumber, waitingNumber, storeName)` — Backend Task 1 정의 ↔ Backend Task 1 Step 5 테스트 ↔ Backend Task 3
  `SmsNotificationListener` 사용 ✓
- `ssePublisher.notifyOwnerSmsFailed(storeId, waitingNumber, phoneNumber)` — Backend Task 3 Step 5 구현 ↔ Backend Task 3 Step 1 테스트 ✓
- `sms-send-failed` 이벤트 data `{waitingNumber, phoneNumber}` — Backend Task 3 Step 5 (백엔드) ↔ Frontend Task 1 Step 1 (프론트엔드) ✓
- `SmsClient.send(String to, String content)` — Backend Task 2 Step 1 인터페이스 ↔ Backend Task 3 Step 3 `SmsNotificationListener` 호출 ✓
