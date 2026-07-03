# SMS 호출 알림 제품 설계 스펙

| 항목    | 내용                  |
|-------|---------------------|
| 문서 유형 | Product Design Spec |
| 작성일   | 2026년 04월 23일       |
| 상태    | Approved            |

---

## 배경 및 목적

손님이 웨이팅 대기 중 브라우저를 닫거나 화면이 꺼진 상태에서 호출을 받으면 SSE 알림을 놓친다. 이를 보완하기 위해 점주가 "호출" 버튼을 누르는 시점에 손님의 전화번호로 SMS를 발송한다.

---

## 스코프

| 항목           | 포함 여부 | 비고               |
|--------------|-------|------------------|
| 호출 시 SMS 발송  | ✅     | CALLED 상태 전이 시점  |
| 등록 확인 SMS    | ❌     | 추후 검토            |
| 자동 노쇼 경고 SMS | ❌     | 추후 검토            |
| 카카오 알림톡      | ❌     | Phase 4 이후 추가 예정 |

---

## 발송 정책

| 항목      | 내용                             |
|---------|--------------------------------|
| 트리거     | 점주 호출 (WAITING → CALLED 상태 전이) |
| 수신자     | 호출된 손님 1명 (1:1 발송, 브로드캐스트 아님)  |
| 발송 시점   | 트랜잭션 AFTER_COMMIT (DB 저장 완료 후) |
| 재시도     | 없음 (과금 중복 방지)                  |
| 실패 처리   | 로그 기록 + 점주 대시보드 SSE 알림         |
| SMS 제공사 | Solapi                         |
| 발신번호    | 개인 휴대폰 번호 (환경변수)               |

**메시지 형식:**

```
[QR Wait] 3번 손님, 홍콩반점에서 입장 안내드립니다. 매장으로 와주세요.
```

---

## 아키텍처

### 전체 흐름

```
점주 "호출" 버튼 클릭
    ↓
WaitingManagementService.call()
    ├─ WaitingEntry.call() → CALLED 상태 전이
    ├─ DB 저장
    └─ WaitingCalledEvent 발행
           (storeId, waitingId, phoneNumber, waitingNumber, storeName)
                  ↓ AFTER_COMMIT
    ┌─────────────┴─────────────────────┐
    │                                   │
SseEventListener                SmsNotificationListener  ← 신규
    │                                   │
SSE 브로드캐스트 (기존)           SmsClient.send()
                                        │
                              ┌─────────┴──────────┐
                              │ 성공                │ 실패
                              │ (끝)                │ 로그 기록
                                                    │
                                            SsePublisher
                                            .notifyOwnerSmsFailed()
                                                    ↓
                                        점주 대시보드 SSE
                                        "sms-send-failed" 이벤트
                                                    ↓
                                        DashboardPage 알림 배너
                                        "⚠️ #3번 손님 SMS 발송 실패.
                                         직접 연락해주세요. (010-1234-5678)"
```

### 기존 SseEventListener 영향

`SseEventListener.onWaitingCalled()`는 `event.storeId()`, `event.waitingId()` 만 사용하므로 `WaitingCalledEvent` 필드 추가에 영향 없음.

---

## 변경 파일

### 백엔드

| 파일                                                  | 변경 내용                                                          |
|-----------------------------------------------------|----------------------------------------------------------------|
| `waiting/domain/event/WaitingCalledEvent.java`      | `phoneNumber`, `waitingNumber`, `storeName` 필드 추가              |
| `waiting/application/WaitingManagementService.java` | `call()` 에서 enriched 이벤트 발행                                    |
| `shared/sms/SmsClient.java`                         | 신규 — `send(to, content)` 인터페이스                                 |
| `shared/sms/SolapiSmsClient.java`                   | 신규 — Solapi REST API 구현체 (RestClient + HmacSHA256 인증)          |
| `shared/sms/SmsProperties.java`                     | 신규 — `@ConfigurationProperties(prefix = "sms")`                |
| `shared/sms/SmsNotificationListener.java`           | 신규 — `@TransactionalEventListener(AFTER_COMMIT)`               |
| `shared/sse/SsePublisher.java`                      | `notifyOwnerSmsFailed(storeId, waitingNumber, phoneNumber)` 추가 |
| `resources/application.yml`                         | `sms.api-key`, `sms.api-secret`, `sms.sender` 환경변수 추가          |

### 프론트엔드

| 파일                                     | 변경 내용                                    |
|----------------------------------------|------------------------------------------|
| `frontend/src/pages/DashboardPage.tsx` | `sms-send-failed` SSE 이벤트 핸들러 + 알림 배너 추가 |

---

## 컴포넌트 상세

### WaitingCalledEvent

```java
// 변경 전
public record WaitingCalledEvent(UUID storeId, UUID waitingId) {

}

// 변경 후
public record WaitingCalledEvent(
    UUID storeId,
    UUID waitingId,
    String phoneNumber,
    int waitingNumber,
    String storeName
) {

}
```

### SmsClient 인터페이스

```java
public interface SmsClient {

  void send(String to, String content);
}
```

### SmsProperties

```java

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    String apiKey,
    String apiSecret,
    String sender
) {

}
```

### SmsNotificationListener

```java

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
```

### SolapiSmsClient 인증 방식

Solapi REST API는 HmacSHA256 서명 인증을 사용한다.

```
Authorization: HMAC-SHA256 apiKey={apiKey}, date={ISO8601}, salt={random}, signature={HmacSHA256(secret, date+salt)}
```

`RestClient`(Spring 내장)로 `POST https://api.solapi.com/messages/v4/send` 호출.

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

점주 대시보드(`DashboardPage`)에서 수신 시 상단 배너 표시:

```
⚠️ #3번 손님 SMS 발송 실패. 직접 연락해주세요. (010-1234-5678)
```

---

## 환경변수

```yaml
sms:
  api-key: ${SMS_API_KEY}
  api-secret: ${SMS_API_SECRET}
  sender: ${SMS_SENDER_NUMBER}
```

세 값 모두 미설정 시 앱 구동은 정상 진행되며, 호출 시점에 SMS 발송 실패 → `sms-send-failed` SSE 이벤트로 점주에게 알림.

---

## 카카오 알림톡 확장 경로

`SmsClient` 인터페이스 기반이므로, 추후 `KakaoAlimtalkClient implements SmsClient` 구현체를 추가하고 설정으로 전환하면 됨. `SmsNotificationListener` 코드 변경 불필요.
