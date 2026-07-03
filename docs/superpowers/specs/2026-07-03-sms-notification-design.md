# SMS 호출 알림 제품 설계 스펙 (v2 — NHN Cloud)

| 항목      | 내용                                                                     |
|---------|------------------------------------------------------------------------|
| 문서 유형   | Product Design Spec                                                    |
| 작성일     | 2026년 07월 03일                                                           |
| 상태      | Approved (구현 대기)                                                       |
| 이전 문서   | `docs/archive/superpowers/2026-04-23-sms-notification-design.md` (Solapi 기반, superseded) |

---

## 배경

손님이 웨이팅 대기 중 브라우저를 닫거나 화면이 꺼진 상태에서 호출을 받으면 SSE 알림을 놓친다. 점주가 "호출" 버튼을 누르는 시점에 손님의 전화번호로 SMS를 발송해 이 갭을 메운다.

## v1(2026-04-23) 대비 변경

| 항목        | v1 (Solapi)                             | v2 (NHN Cloud, 본 문서)                    |
|-----------|----------------------------------------|-----------------------------------------|
| SMS 제공사   | Solapi                                 | NHN Cloud SMS                           |
| 인증        | HMAC-SHA256 서명 (요청마다 계산)                | X-Secret-Key 정적 헤더                      |
| 실패 감지     | HTTP 4xx/5xx → 예외                       | HTTP 200이어도 body `header.isSuccessful=false` → 예외 |
| 파일 경로     | `waiting/application/...`               | `waiting/management/application/...` (액터 서브패키지 분리 반영) |
| Backend Task 1 | 계획됨                                 | **이미 반영 완료** — `WaitingCalledEvent` 5필드, `WaitingManagementService.call()` enriched 이벤트 발행 |

**변경 없음**: `SmsClient` 인터페이스 추상화, `SmsNotificationListener`(AFTER_COMMIT), 실패 시 `SsePublisher.notifyOwnerSmsFailed` → 점주 대시보드 배너, 메시지 형식, 카카오 알림톡 확장 경로.

---

## 스코프

| 항목           | 포함 여부 | 비고               |
|--------------|-------|------------------|
| 호출 시 SMS 발송  | ✅     | CALLED 상태 전이 시점  |
| 등록 확인 SMS    | ❌     | 추후 검토            |
| 자동 노쇼 경고 SMS | ❌     | 추후 검토            |
| 카카오 알림톡      | ❌     | 인터페이스 기반, 추후 구현체 추가 |
| 재시도          | ❌     | 과금 중복 방지         |

---

## 발송 정책

| 항목      | 내용                                            |
|---------|-----------------------------------------------|
| 트리거     | 점주 호출 (WAITING → CALLED 상태 전이)                |
| 수신자     | 호출된 손님 1명 (1:1 발송, 브로드캐스트 아님)                 |
| 발송 시점   | 트랜잭션 AFTER_COMMIT (DB 저장 완료 후)                |
| 재시도     | 없음 (과금 중복 방지)                                 |
| 실패 처리   | 로그 기록 + 점주 대시보드 SSE 알림                        |
| SMS 제공사 | NHN Cloud SMS                                 |
| 발신번호    | 사전 등록된 발신번호 (환경변수)                            |

**메시지 형식** (v1과 동일):

```
[QR Wait] 3번 손님, 홍콩반점에서 입장 안내드립니다. 매장으로 와주세요.
```

---

## 아키텍처

### 전체 흐름

```
점주 "호출" 버튼 클릭
    ↓
WaitingManagementService.call() (waiting/management/application/)
    ├─ WaitingEntry.call() → CALLED 상태 전이
    ├─ DB 저장
    └─ WaitingCalledEvent 발행 [이미 반영됨]
           (storeId, waitingId, phoneNumber, waitingNumber, storeName)
                  ↓ AFTER_COMMIT
    ┌─────────────┴─────────────────────┐
    │                                   │
SseEventListener                SmsNotificationListener  ← 신규
    │                                   │
SSE 브로드캐스트 (기존)           SmsClient.send()  ← NhnCloudSmsClient
                                        │
                              ┌─────────┴──────────┐
                              │ 성공                │ 실패
                              │ (끝)                │ SmsSendException throw
                                                    │
                                            catch → 로그 + SsePublisher
                                                     .notifyOwnerSmsFailed()
                                                            ↓
                                                점주 대시보드 SSE
                                                "sms-send-failed" 이벤트
                                                            ↓
                                                DashboardPage 알림 배너
```

### 실패 감지 방침 (A안)

NHN Cloud SMS는 HTTP 200으로 응답해도 `header.isSuccessful=false`인 경우가 발생한다 (잔액 부족, 발신번호 미등록, 수신번호 형식 오류 등). `SmsClient.send()`의 인터페이스 계약을 **"실패 = 예외"** 로 유지하기 위해:

- `NhnCloudSmsClient`가 응답 body를 파싱해 `header.isSuccessful` 검사.
- `false`면 `header.resultCode` / `resultMessage`를 메시지에 담아 `SmsSendException`(RuntimeException) throw.
- 기존 `SmsNotificationListener`의 catch 블록이 그대로 잡아 SSE 알림 발송.

향후 카카오 알림톡 등 다른 구현체가 추가되어도 이 계약을 따르면 리스너 코드 변경 불필요.

---

## 변경 파일

### Backend

| 파일                                                                              | 변경 내용                                                   |
|---------------------------------------------------------------------------------|---------------------------------------------------------|
| `waiting/domain/event/WaitingCalledEvent.java`                                  | ✅ **이미 반영됨** (5필드 record)                              |
| `waiting/management/application/WaitingManagementService.java`                  | ✅ **이미 반영됨** (enriched 이벤트 발행) — 경로가 v1 스펙과 다름 (액터 분리) |
| `shared/sms/SmsClient.java`                                                     | 신규 — `send(to, content)` 인터페이스                          |
| `shared/sms/SmsSendException.java`                                              | 신규 — RuntimeException                                   |
| `shared/sms/SmsProperties.java`                                                 | 신규 — `@ConfigurationProperties(prefix = "sms")`         |
| `shared/sms/SmsConfig.java`                                                     | 신규 — `@EnableConfigurationProperties` + `SmsClient` 빈   |
| `shared/sms/NhnCloudSmsClient.java`                                             | 신규 — NHN Cloud REST API 구현체 (X-Secret-Key)              |
| `shared/sms/SmsNotificationListener.java`                                       | 신규 — `@TransactionalEventListener(AFTER_COMMIT)`        |
| `shared/sse/SsePublisher.java`                                                  | `notifyOwnerSmsFailed(storeId, waitingNumber, phoneNumber)` 추가 |
| `resources/application.yml`                                                     | `sms.app-key`, `sms.secret-key`, `sms.sender` 환경변수 추가   |

### Frontend

| 파일                                     | 변경 내용                                    |
|----------------------------------------|------------------------------------------|
| `frontend/src/pages/DashboardPage.tsx` | `sms-send-failed` SSE 이벤트 핸들러 + 알림 배너 추가 |

---

## 컴포넌트 상세

### SmsClient 인터페이스

```java
public interface SmsClient {
  /**
   * @throws SmsSendException 발송 실패 시 (네트워크, provider 응답 실패 등)
   */
  void send(String to, String content);
}
```

### SmsSendException

```java
public class SmsSendException extends RuntimeException {
  public SmsSendException(String message) { super(message); }
  public SmsSendException(String message, Throwable cause) { super(message, cause); }
}
```

### SmsProperties

```java
@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    @DefaultValue("") String appKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("") String sender
) {}
```

빈 문자열 기본값 — 미설정 상태로 앱 구동은 가능하며, 호출 시점에 provider가 인증 실패로 응답 → `SmsSendException` → SSE 알림 흐름.

### NhnCloudSmsClient

**엔드포인트**: `POST https://api-sms.cloud.toast.com/sms/v3.0/appKeys/{appKey}/sender/sms`

**요청 헤더**:
- `X-Secret-Key: {secretKey}`
- `Content-Type: application/json`

**요청 body**:
```json
{
  "body": "[QR Wait] 3번 손님, 홍콩반점에서 입장 안내드립니다. 매장으로 와주세요.",
  "sendNo": "01012345678",
  "recipientList": [
    { "recipientNo": "01087654321" }
  ]
}
```

**응답 body** (관심 필드):
```json
{
  "header": {
    "isSuccessful": true,
    "resultCode": 0,
    "resultMessage": "SUCCESS"
  }
}
```

**구현 스켈레톤**:

```java
@RequiredArgsConstructor
public class NhnCloudSmsClient implements SmsClient {

  private static final String URL_TEMPLATE =
      "https://api-sms.cloud.toast.com/sms/v3.0/appKeys/{appKey}/sender/sms";

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
          .uri(URL_TEMPLATE, properties.appKey())
          .header("X-Secret-Key", properties.secretKey())
          .body(body)
          .retrieve()
          .body(NhnSmsResponse.class);
    } catch (RestClientException e) {
      throw new SmsSendException("NHN Cloud SMS 호출 실패", e);
    }

    if (response == null || response.header() == null || !response.header().isSuccessful()) {
      throw new SmsSendException("NHN Cloud SMS 발송 실패: %s".formatted(
          response == null || response.header() == null
              ? "empty response"
              : response.header().resultMessage()));
    }
  }

  private record NhnSmsResponse(Header header) {
    private record Header(boolean isSuccessful, int resultCode, String resultMessage) {}
  }
}
```

### SmsConfig

```java
@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

  @Bean
  public SmsClient smsClient(SmsProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3_000);
    factory.setReadTimeout(3_000);
    RestClient restClient = RestClient.builder().requestFactory(factory).build();
    return new NhnCloudSmsClient(properties, restClient);
  }
}
```

**타임아웃 근거**: `SmsNotificationListener`는 `AFTER_COMMIT`에서 동기 실행되며, 이 시점의 servlet 스레드는 "호출" API 응답을 아직 반환하지 않은 상태다. NHN Cloud SMS 호출이 무한 대기하면 그만큼 점주의 "호출" 버튼 응답이 지연된다. 3초 상한으로 최악의 UX 지연을 제한 (초과 시 예외 → SSE 알림 흐름).

### SmsNotificationListener

```java
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

### 기존 SseEventListener 영향

`SseEventListener.onWaitingCalled()`는 `event.storeId()`, `event.waitingId()`만 사용하므로 `WaitingCalledEvent`의 추가 필드에 영향 없음. 별도 리스너(`SmsNotificationListener`)가 동일 이벤트를 소비. Spring `TransactionalEventListener`는 병렬이 아니라 순차 실행되지만 두 리스너는 독립적이라 순서 무관.

### SSE 이벤트 — sms-send-failed

```json
{
  "event": "sms-send-failed",
  "data": {
    "waitingNumber": 3,
    "phoneNumber": "010-1234-5678"
  }
}
```

점주 대시보드(`DashboardPage`) 수신 시 상단 배너:

```
⚠️ #3번 손님 SMS 발송 실패. 직접 연락해주세요. (010-1234-5678)
```

---

## 환경변수

```yaml
sms:
  app-key: ${NHN_SMS_APP_KEY:}
  secret-key: ${NHN_SMS_SECRET_KEY:}
  sender: ${NHN_SMS_SENDER_NUMBER:}
```

세 값 모두 미설정이어도 앱 구동은 정상 진행. 호출 시점에 `NhnCloudSmsClient`가 provider 인증 실패로 `SmsSendException`을 던지고, 리스너가 SSE로 점주에게 알린다.

**발신번호는 NHN Cloud 콘솔에 사전 등록**되어 있어야 실제 발송 성공.

---

## 테스트 전략

### 단위 테스트

| 대상                          | 케이스                                                                    |
|-----------------------------|------------------------------------------------------------------------|
| `SmsNotificationListener`   | 성공 시 SSE 알림 없음 / `SmsClient.send()`가 예외 던지면 `notifyOwnerSmsFailed` 호출  |
| `NhnCloudSmsClient`         | 응답 `isSuccessful=false` 시 `SmsSendException` throw / `RestClientException` 을 `SmsSendException`으로 감쌈. RestClient는 `MockWebServer`(OkHttp) 또는 `HttpServer` 스텁으로 로컬 HTTP 응답을 세팅해 검증. |
| `SsePublisher`              | `notifyOwnerSmsFailed`가 registry로 올바른 이벤트명·payload 전달                  |

### 수동 검증

1. `NHN_SMS_*` 미설정 상태로 점주 호출 → 대시보드 배너 표시.
2. 실제 키 설정 + 발신번호 등록 상태로 호출 → 손님 폰에 SMS 도착.

---

## 카카오 알림톡 확장 경로

`SmsClient` 인터페이스 기반이므로, 추후 `KakaoAlimtalkClient implements SmsClient` 구현체를 추가하고 `SmsConfig`에서 프로파일/설정 기반 전환 (`@ConditionalOnProperty` 등)만 하면 됨. `SmsNotificationListener` 코드 변경 불필요.

---

## Non-Goals

- SMS 발송 이력 DB 저장 (별도 요구 시 재설계)
- 손님이 알림 수신 여부 확인 (delivery receipt)
- 다국어 메시지 템플릿
- 발송 배치 / rate limiting (1:1 소량이라 불필요)
