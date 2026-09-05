# 호출 SMS 알림 설계 스펙 (채널 교체 가능 포트 구조)

| 항목    | 내용                  |
|-------|---------------------|
| 문서 유형 | Product / Tech Design Spec |
| 작성일   | 2026년 07월 01일       |
| 상태    | Draft               |
| 관련 이력 | `2026-04-23-sms-notification-design.md` (구 설계는 Solapi 기반, 구현 후 revert됨: `b5e8d2b`, `db9c4ce`) |
| SMS 제공사 | **NHN Cloud SMS v3.0** (구 설계의 Solapi 대신 채택) |

---

## 배경 및 목적

손님은 QR 스캔으로 앱 설치 없이 웨이팅에 등록한다. 그런데 현재 손님이 호출(`WAITING → CALLED`)을 인지하는 **유일한 경로는 브라우저 탭을 계속 열어두고 SSE를 수신하는 것**뿐이다. 화면을 닫거나·폰을 잠그거나·다른 앱을 쓰면 순서를 놓친다.

게다가 등록 화면 동의 문구는 *"전화번호는 웨이팅 호출 알림 목적으로만 사용됩니다"* 라고 약속하지만, 실제로는 어떤 알림도 발송되지 않아 **동의 문구와 실제 동작이 어긋나 있다.**

**목표:** 점주가 손님을 호출하면, 화면을 보고 있지 않은 손님도 **SMS로 입장 안내**를 받는다. 나중에 카카오 알림톡·웹푸시를 **같은 포트에 어댑터로 추가**할 수 있도록 채널 중립 구조로 만든다.

과거 revert 사유(외부 크리덴셜이 없으면 앱 부팅/개발이 막힘)를 **기본 `log` 어댑터**로 제거하는 것이 이번 설계의 핵심 개선점이다.

---

## 스코프

| 항목                       | 포함 | 비고                              |
|--------------------------|----|---------------------------------|
| 호출 시 알림 발송               | ✅  | `CALLED` 상태 전이 시점               |
| SMS 채널(NHN Cloud) 어댑터      | ✅  | 운영 환경 실제 발송                     |
| 로그 어댑터 (dev 기본값)          | ✅  | 크리덴셜 없이 부팅·동작                   |
| 발송 실패 → 점주 대시보드 배너         | ✅  | 백엔드 SSE 이벤트 + 프론트 배너            |
| 채널 교체 포트(`CallNotificationSender`) | ✅  | 알림톡·웹푸시 확장 지점만 마련              |
| 카카오 알림톡 / 웹푸시 어댑터          | ❌  | 이번 범위 밖 (포트만 열어둠)              |
| 등록확인 / 노쇼경고 알림            | ❌  | 추후 검토                           |
| 발송 재시도 / 전달상태 웹훅          | ❌  | 과금 중복 방지 위해 재시도 없음             |

---

## 발송 정책

| 항목      | 내용                                          |
|---------|---------------------------------------------|
| 트리거     | 점주 호출 (`WAITING → CALLED` 상태 전이)             |
| 수신자     | 호출된 손님 1명 (1:1 발송, 브로드캐스트 아님)               |
| 발송 시점   | 트랜잭션 `AFTER_COMMIT` (DB 저장 완료 후)            |
| 재시도     | 없음                                          |
| 실패 처리   | 로그 기록 + 점주 대시보드 SSE 알림(`call-notification-failed`) |
| 채널 선택   | `notification.channel` 프로퍼티 (`log` 기본 / `sms`) |
| SMS 제공사 | NHN Cloud SMS v3.0 (`POST https://sms.api.nhncloudservice.com/sms/v3.0/appKeys/{appKey}/sender/sms`) |
| 인증       | `X-Secret-Key` 헤더 + URL 경로의 `appKey` (HMAC 서명 불필요) |
| 발신번호    | 환경변수 (`NHN_SMS_SEND_NO`) — NHN 콘솔에서 사전 등록(서류·본인인증) |

**메시지 형식(SMS):**

```
[QR Wait] 3번 손님, 홍콩반점 입장 안내드립니다. 매장으로 와주세요.
```

---

## 아키텍처

### 전체 흐름

```
점주 "호출" 클릭
   ↓
WaitingManagementService.call()
   ├─ WaitingEntry.call()  → CALLED 상태 전이
   ├─ waitingRepository.save()
   └─ WaitingCalledEvent 발행  (기존 그대로 — 변경 없음)
          (storeId, waitingId, phoneNumber, waitingNumber, storeName)
                 ↓ AFTER_COMMIT
   ┌─────────────┴───────────────────────────────┐
   │                                             │
SseEventListener.onWaitingCalled          CallNotificationListener  ← 신규
   │  (기존)                                    │
SSE "waiting-called" 브로드캐스트            CallNotificationSender.send(CallNotification)
   (화면 켠 손님)                                  │  (포트)
                                    ┌──────────────┴───────────────┐
                                    │ notification.channel=log     │ =sms
                                 LoggingCallNotificationSender   NhnSmsSender
                                    │  (dev 기본)                    │
                                  로그 출력                     NHN Cloud SMS REST 호출
                                                                    │
                                                          ┌─────────┴─────────┐
                                                          │ 성공                │ 예외
                                                          │ (끝)                │ 로그 + 실패 SSE
                                                                                │
                                                    SsePublisher.notifyCallNotificationFailed()
                                                                                ↓
                                                        점주 대시보드 SSE "call-notification-failed"
                                                                                ↓
                                                        DashboardPage 배너
                                                        "⚠️ 3번 손님 알림 발송 실패.
                                                         직접 연락해주세요. (010-1234-5678)"
```

**핵심:** SMS는 기존 SSE를 대체하지 않고 **보완**한다. 화면을 켠 손님은 SSE로 즉시 페이지 전환, 화면을 끈 손님은 SMS로 인지한다. 두 리스너는 같은 `WaitingCalledEvent`를 독립적으로 구독한다.

---

## 컴포넌트

패키지 위치는 알림이 **여러 애그리거트에 걸친 횡단 외부연동**이므로 `shared/notification/`에 둔다. (기존 `shared/sms`, `shared/sse`와 동일한 결)

### 1. 포트 (채널 중립)

```
shared/notification/CallNotificationSender      (인터페이스)
    void send(CallNotification notification);

shared/notification/CallNotification            (record)
    (String phoneNumber, int waitingNumber, String storeName)
```

- **구조화된 데이터**를 넘긴다(문구 완성본이 아님). 메시지 조립은 각 어댑터 책임 — SMS는 자유 텍스트, 훗날 알림톡은 템플릿 변수, 웹푸시는 title/body로 각자 포맷하기 위함이다.

### 2. 어댑터

| 어댑터                            | 활성 조건                                    | 책임                                   |
|--------------------------------|------------------------------------------|--------------------------------------|
| `LoggingCallNotificationSender` | `notification.channel=log` (기본값)         | 조립된 SMS 문구를 `log.info`로 출력           |
| `NhnSmsSender`                  | `notification.channel=sms`               | SMS 문구 조립 → NHN Cloud SMS REST 호출 (`X-Secret-Key` 헤더 인증) |

- `@ConditionalOnProperty(name = "notification.channel", havingValue = "...", matchIfMissing = ...)`로 정확히 하나만 빈 등록.
- 기본값 `log` → **크리덴셜 없이 앱이 항상 부팅되고 개발/테스트가 막히지 않는다** (revert 사유 제거).
- `NhnSmsSender`는 `RestClient`로 `POST /sms/v3.0/appKeys/{appKey}/sender/sms` 호출. 요청 바디: `{ body, sendNo, recipientList: [{ recipientNo }] }`. **HMAC 서명 계산이 없어** 구 Solapi 어댑터보다 단순하다. 응답의 `header.isSuccessful == false` 또는 HTTP 오류를 실패로 간주한다.

### 3. 리스너

```
shared/notification/CallNotificationListener
    @TransactionalEventListener(AFTER_COMMIT) onWaitingCalled(WaitingCalledEvent)
        try   → callNotificationSender.send(new CallNotification(...))
        catch → log.error + ssePublisher.notifyCallNotificationFailed(storeId, waitingNumber, phoneNumber)
```

- 기존 `SseEventListener.onWaitingCalled`와 **공존**한다(서로 독립).
- `WaitingCalledEvent`는 이미 필요한 필드를 모두 담고 있어 **이벤트 변경 없음**.

### 4. 설정 프로퍼티

```
shared/notification/NhnSmsProperties   (record, @ConfigurationProperties(prefix="sms.nhn"))
    (String appKey, String secretKey, String sendNo)   // 모두 @DefaultValue("")
```

`application.yml`:
```yaml
notification:
  channel: ${NOTIFICATION_CHANNEL:log}   # log | sms
sms:
  nhn:
    app-key: ${NHN_SMS_APP_KEY:}
    secret-key: ${NHN_SMS_SECRET_KEY:}
    send-no: ${NHN_SMS_SEND_NO:}
```

- `application-local.yml`은 별도 지정 없이 기본 `log` 사용.
- 실제 발송 테스트 시에만 `NOTIFICATION_CHANNEL=sms` + NHN 3개 크리덴셜(`appKey`, `secretKey`, 등록된 `sendNo`)을 환경변수로 주입.

### 5. SsePublisher 확장

```
notifyCallNotificationFailed(UUID storeId, int waitingNumber, String phoneNumber)
    → registry.broadcastToOwner(storeId, "call-notification-failed",
          Map.of("waitingNumber", .., "phoneNumber", ..))
```

- 과거 revert된 `notifyOwnerSmsFailed`와 동일 목적이나, **채널 중립 이름**으로 재도입.

### 6. 프론트엔드 — DashboardPage

- SSE 파서(`pages/DashboardPage.tsx`)의 `eventName` 스위치에 분기 추가:
  ```
  else if (eventName === 'call-notification-failed') {
    // data(JSON) 파싱 → 배너 문구 구성
    setActionBanner('⚠️ {waitingNumber}번 손님 알림 발송 실패. 직접 연락해주세요. ({phoneNumber})')
  }
  ```
- 기존 `alertBanner` 배너 UI 패턴을 재사용하되, 임계값 배너와 구분되도록 별도 상태(예: `notifFailBanner`)로 둔다. 닫기(dismiss) 가능.

---

## 에러 처리

| 상황                | 처리                                                        |
|-------------------|-----------------------------------------------------------|
| NHN 응답 실패(`isSuccessful=false`)/예외 | `log.error` + 점주 SSE `call-notification-failed` 발행 → 대시보드 배너 |
| 재시도               | 하지 않음 (과금 중복 방지). 점주가 배너 보고 수동 연락                          |
| `log` 채널          | 실패 개념 없음(항상 성공 로그)                                        |
| 리스너 예외가 호출 트랜잭션에 영향 | 없음. `AFTER_COMMIT`라 이미 커밋 완료. 알림 실패가 호출 상태 전이를 롤백하지 않음     |

---

## 테스트

도메인 우선 원칙에 따라, 이번 기능은 도메인 변경이 없고 **횡단 연동/리스너**가 핵심이므로 그 경계를 테스트한다.

| 대상                            | 검증 내용                                                       |
|-------------------------------|-------------------------------------------------------------|
| `CallNotificationListener`      | `WaitingCalledEvent` 수신 시 sender 호출(인자 검증) / sender 예외 시 `notifyCallNotificationFailed` 호출 |
| `NhnSmsSender`                  | 메시지 문구 포맷 / 요청 URL·헤더(`X-Secret-Key`)·바디(`body,sendNo,recipientList`) 조립 및 실패 응답 처리 (RestClient mock 또는 `MockRestServiceServer`) |
| `LoggingCallNotificationSender` | 조립된 문구가 로깅되는지 (간단)                                          |
| 어댑터 토글                        | `notification.channel` 값에 따라 정확히 한 어댑터만 빈 등록되는지            |

---

## 구현 순서 (초안)

1. `shared/notification` 패키지: `CallNotificationSender` 포트 + `CallNotification` 레코드.
2. `LoggingCallNotificationSender` (기본) + `NhnSmsProperties`.
3. `NhnSmsSender` + `@ConditionalOnProperty` 토글.
4. `CallNotificationListener` (AFTER_COMMIT) — 성공/실패 경로.
5. `SsePublisher.notifyCallNotificationFailed` + `application.yml` 프로퍼티.
6. 프론트 `DashboardPage` 실패 배너 분기.
7. 테스트: 리스너 / NhnSmsSender / 토글.
8. `README` 또는 스펙에 NHN Cloud 설정 가이드(프로젝트 생성·SMS 서비스 활성화·발신번호 등록·`appKey`/`secretKey`/환경변수) 정리 — 내 폰 실발송 테스트용.

---

## 열린 결정 사항 (구현 계획 단계에서 확정)

- `notification.channel` 토글을 `@ConditionalOnProperty` vs Spring `@Profile` 중 무엇으로 할지 → 프로퍼티 방식 권장(프로필과 독립적으로 채널만 바꿀 수 있어 유연).
- 실패 배너 문구·디자인 세부.
