# Phase 3 제품 설계 스펙

| 항목    | 내용                                      |
|-------|-----------------------------------------|
| 문서 유형 | Product Design Spec                     |
| 작성일   | 2026년 04월 14일                           |
| 연관 문서 | QRWait_PRD_v2.0.md · QRWait_TRD_v2.0.md |
| 상태    | Approved                                |

---

## 배경 및 목적

Phase 2 배포 완료 시점에서 제품 설계 관점의 구조적 공백과 신규 기능을 검토하여 Phase 3 요구사항을 새롭게 정의한다. 기존 PRD의 Phase 3 초안을 그대로 계승하는 것이 아니라, 실제 제품 경험 분석을 바탕으로 재구성한다.

---

## 식별된 구조적 공백 (C)

| #   | 위치        | 문제                                             |
|-----|-----------|------------------------------------------------|
| C-1 | 손님 등록 플로우 | QR 스캔 후 대기 인원을 확인할 수 없어 등록 여부 결정이 어려움          |
| C-2 | 호출 알림     | SSE 연결이 끊긴 상태(브라우저 닫힘·백그라운드)에서 호출 알림을 받을 수 없음  |
| C-3 | 점주 운영     | 호출(CALLED) 후 노쇼 처리를 잊으면 CALLED 상태가 무한정 유지됨     |
| C-4 | 점주 운영     | 웨이팅 번호가 날짜를 넘어 누적됨. "오늘 N번" 개념 없음              |
| C-5 | 통계        | `getDailySummary()`가 오늘 날짜만 조회 가능. 날짜 범위 조회 불가 |

---

## Phase 3 요구사항

### 전체 구성

6가지 요구사항을 하나의 Phase 3에 통합한다. 의존성 순서에 따라 아래 순서로 구현한다.

```
1. 전화번호 전환 (이름 → 전화번호)   ← 알림 발송의 선행 조건
2. 등록 전 대기 현황 표시             ← 독립적, 빠르게 배포 가능
3. 호출 시 SMS 알림                  ← 1번 완료 후
4. CALLED 상태 자동 만료             ← 독립적
5. 웨이팅 번호 일별 초기화            ← 독립적 (쿼리 변경만)
6. 날짜 범위 통계 조회               ← 독립적
```

---

## Section 1 — 손님 등록 플로우 변경

### 변경 요약

| 항목         | 현재            | Phase 3              |
|------------|---------------|----------------------|
| 대기 현황 표시   | 등록 완료 후       | 랜딩 페이지 진입 즉시         |
| 등록 입력 항목   | 이름(닉네임) + 인원수 | 전화번호 + 인원수 + 개인정보 동의 |
| 점주 대시보드 표시 | "홍길동, 2명"     | "010-1234-5678, 2명"  |

### 랜딩 페이지 플로우

```
QR 스캔 → 랜딩 페이지
              ├─ [상단] 현재 대기 N팀 · 예상 N분 즉시 표시
              │         (GET /api/stores/{storeId}/waitings/status 활용)
              └─ [하단] 전화번호 입력 + 인원수 + 개인정보 동의 체크박스
          → 대기 확인 페이지 → 실시간 상태 페이지
```

- 매장 상태가 BREAK/FULL/CLOSED이면 폼 대신 사유 메시지 표시 (기존 동작 유지)
- 전화번호 형식 검증: `010-XXXX-XXXX`
- 개인정보 동의 체크박스: **필수** — 미체크 시 등록 버튼 비활성화
- 동의 문구: *"전화번호는 웨이팅 호출 알림 목적으로만 사용됩니다"*

### DB 스키마 변경

```sql
ALTER TABLE waiting_entries
    RENAME COLUMN visitor_name TO phone_number;
ALTER TABLE waiting_entries
    ALTER COLUMN phone_number TYPE VARCHAR(20);
```

### 백엔드 변경 범위

| 파일                            | 변경 내용                                      |
|-------------------------------|--------------------------------------------|
| `WaitingEntry.java`           | `visitorName` → `phoneNumber`              |
| `RegisterWaitingRequest.java` | `visitorName` → `phoneNumber` + 전화번호 형식 검증 |
| `OwnerWaitingResponse.java`   | `visitorName` → `phoneNumber`              |
| `WaitingEntryJpaEntity.java`  | `visitor_name` → `phone_number` 컬럼명 반영     |
| Flyway migration              | `visitor_name` → `phone_number` 컬럼 변경      |

### 프론트엔드 변경 범위

| 파일                   | 변경 내용                                               |
|----------------------|-----------------------------------------------------|
| `LandingPage.tsx`    | 상단 대기 현황 표시 추가, 이름 → 전화번호 입력 필드 전환, 개인정보 동의 체크박스 추가 |
| `OwnerDashboard.tsx` | `visitorName` → `phoneNumber` 표시 반영                 |
| API 클라이언트 타입         | `visitorName` → `phoneNumber` 타입 변경                 |

---

## Section 2 — 호출 알림 (SMS)

### 아키텍처

기존 `@TransactionalEventListener` 이벤트 구조를 활용한다. 비즈니스 로직 변경 없이 리스너만 추가한다.

```
WaitingManagementService.call()
  → WaitingCalledEvent 발행 (기존)
        ├── SseEventListener        → SSE 브로드캐스트 (기존)
        └── SmsNotificationListener → SMS 발송 (신규)
```

### 발송 정책

| 이벤트              | 발송 여부  | 메시지 예시                                          |
|------------------|--------|-------------------------------------------------|
| WAITING → CALLED | ✅ 발송   | `[QR Wait] 3번 손님, 홍콩반점에서 입장 안내드립니다. 매장으로 와주세요.` |
| 그 외 상태 변경        | 발송 안 함 | —                                               |

### SMS 제공사 선택

Phase 3: **SMS** (Solapi 또는 NHN Cloud)

- API 키 발급만으로 즉시 사용 가능
- 사업자 등록 불필요
- 건당 약 10~20원

Phase 4: **카카오 알림톡** 추가 검토

- 카카오 채널 개설 + 템플릿 심사 필요 (1~2주)
- 사업자 등록 필요

### 이벤트 데이터 보강

`SmsNotificationListener`가 SMS 발송에 필요한 전화번호·웨이팅 번호·매장명을 추가 DB 조회 없이 사용할 수 있도록 `WaitingCalledEvent`를 enriched 버전으로 교체한다.

```java
// 현재
public record WaitingCalledEvent(UUID storeId, UUID waitingId) {

}

// 변경 후
public record WaitingCalledEvent(
    UUID storeId,
    UUID waitingId,
    String phoneNumber,    // SMS 수신번호
    int waitingNumber,     // 메시지에 포함할 대기번호
    String storeName       // 메시지에 포함할 매장명
) {

}
```

### 백엔드 변경 범위

| 파일                              | 변경 내용                                                                |
|---------------------------------|----------------------------------------------------------------------|
| `WaitingCalledEvent.java`       | `phoneNumber`, `waitingNumber`, `storeName` 필드 추가                    |
| `WaitingManagementService.java` | `call()` 에서 이벤트 발행 시 enriched 데이터 포함                                 |
| `SmsNotificationListener.java`  | 신규 — `@TransactionalEventListener`로 `WaitingCalledEvent` 수신 후 SMS 발송 |
| `SmsClient.java`                | 신규 — SMS API 호출 추상화 인터페이스 + 구현체                                      |
| `application.yml`               | SMS API Key, 발신번호 환경변수 추가                                            |

### 환경변수 추가

```yaml
sms:
  api-key: ${SMS_API_KEY}
  api-secret: ${SMS_API_SECRET}
  sender: ${SMS_SENDER_NUMBER}
```

---

## Section 3 — 운영 자동화

### Feature A — CALLED 상태 자동 만료

**문제:** 점주가 호출 후 노쇼 처리를 잊으면 CALLED 상태가 무한정 유지됨.

**설계:**

`called_at` 타임스탬프를 추가하고, `@Scheduled` 잡이 1분마다 만료 항목을 자동 NO_SHOW 처리한다.

```
@Scheduled (매 1분)
  → status = 'CALLED' AND called_at < NOW() - {auto-noshow-minutes}분 항목 조회
  → 자동 NO_SHOW 전환
  → WaitingUpdatedEvent 발행 → SSE 브로드캐스트
```

타임아웃 시간은 설정값으로 분리한다.

```yaml
waiting:
  auto-noshow-minutes: 10
```

**DB 스키마 변경:**

```sql
ALTER TABLE waiting_entries
    ADD COLUMN called_at TIMESTAMP;
```

**백엔드 변경 범위:**

| 파일                                | 변경 내용                                 |
|-----------------------------------|---------------------------------------|
| `WaitingEntry.java`               | `calledAt` 필드 추가, `call()` 시 현재 시각 기록 |
| `WaitingEntryJpaEntity.java`      | `called_at` 컬럼 반영                     |
| `WaitingAutoNoShowScheduler.java` | 신규 — `@Scheduled` 잡                   |
| Flyway migration                  | `called_at` 컬럼 추가                     |

---

### Feature B — 웨이팅 번호 일별 초기화

**문제:** 웨이팅 번호가 날짜를 넘어 누적됨.

**설계:**

DB 스키마·스케줄 잡 변경 없이 **쿼리만 수정**한다. 등록 시 오늘 날짜 기준으로 최댓값 + 1을 계산하므로, 날짜가 바뀌면 자동으로 1번부터 시작한다.

```sql
-- 현재
SELECT COALESCE(MAX(waiting_number), 0) + 1
FROM waiting_entries
WHERE store_id = ?

-- 변경 후
SELECT COALESCE(MAX(waiting_number), 0) + 1
FROM waiting_entries
WHERE store_id = ?
  AND DATE(created_at) = CURRENT_DATE
```

**백엔드 변경 범위:**

| 파일                               | 변경 내용                                |
|----------------------------------|--------------------------------------|
| `WaitingEntryJpaRepository.java` | `findNextWaitingNumber` 쿼리에 날짜 조건 추가 |
| `WaitingRepositoryImpl.java`     | 내부 쿼리 변경 (메서드 시그니처 동일)               |

---

## Section 4 — 날짜 범위 통계

### API 설계

```
GET /api/owner/stores/me/waitings/history?from=2026-04-01&to=2026-04-14
```

**응답:**

```json
{
  "from": "2026-04-01",
  "to": "2026-04-14",
  "summary": {
    "totalRegistered": 150,
    "totalEntered": 120,
    "totalNoShow": 15,
    "totalCancelled": 15
  },
  "daily": [
    {
      "date": "2026-04-01",
      "registered": 12,
      "entered": 10,
      "noShow": 1,
      "cancelled": 1
    },
    {
      "date": "2026-04-02",
      "registered": 18,
      "entered": 15,
      "noShow": 2,
      "cancelled": 1
    }
  ]
}
```

DB 스키마 변경 없이 `created_at` 기준으로 집계한다.

```sql
SELECT DATE(created_at)                                      AS date,
       COUNT(*)                                              AS registered,
       SUM(CASE WHEN status = 'ENTERED' THEN 1 ELSE 0 END)   AS entered,
       SUM(CASE WHEN status = 'NO_SHOW' THEN 1 ELSE 0 END)   AS no_show,
       SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled
FROM waiting_entries
WHERE store_id = ?
  AND DATE(created_at) BETWEEN ? AND ?
GROUP BY DATE(created_at)
ORDER BY DATE(created_at)
```

### 프론트엔드

새 페이지 `StatsPage` (`/owner/stats`) 추가.

| 영역        | 내용                            |
|-----------|-------------------------------|
| 날짜 범위 선택  | 오늘 / 어제 / 이번 주 / 이번 달 / 직접 입력 |
| 기간 합계 카드  | 총 등록 / 입장 / 노쇼 / 취소 건수        |
| 일별 상세 테이블 | 날짜별 각 항목 수치                   |

대시보드 네비게이션에 "통계" 탭 추가.

### 백엔드 변경 범위

| 파일                               | 변경 내용                                         |
|----------------------------------|-----------------------------------------------|
| `WaitingManagementService.java`  | `getWaitingHistory(ownerId, from, to)` 메서드 추가 |
| `WaitingHistoryResponse.java`    | 신규 DTO (summary + daily 리스트)                  |
| `WaitingEntryJpaRepository.java` | 날짜 범위 집계 쿼리 추가                                |
| `OwnerWaitingController.java`    | `GET /waitings/history` 엔드포인트 추가              |

---

## DB 스키마 변경 전체 요약

| 마이그레이션                                        | 변경 내용                                       |
|-----------------------------------------------|---------------------------------------------|
| V{n}__rename_visitor_name_to_phone_number.sql | `visitor_name` → `phone_number VARCHAR(20)` |
| V{n+1}__add_called_at_to_waiting_entries.sql  | `called_at TIMESTAMP` 컬럼 추가                 |

---

## Phase 3 제외 범위

기존 PRD Phase 3 초안의 아래 항목은 Phase 4로 이관한다.

| 항목                | 이유                              |
|-------------------|---------------------------------|
| 직원 계정 및 권한 관리     | 단일 점주 운영 모델에서 우선순위 낮음           |
| 다중 매장 관리          | 아키텍처 변경 규모가 큼                   |
| 웨이팅 순서 수동 조정      | 운영 복잡도 대비 효용 검토 필요              |
| 카카오 알림톡           | SMS 안정화 후 추가 (비즈니스 채널 등록 필요)    |
| 일별/주별 통계 리포트 (차트) | Section 4의 테이블로 대체. 차트는 Phase 4 |
