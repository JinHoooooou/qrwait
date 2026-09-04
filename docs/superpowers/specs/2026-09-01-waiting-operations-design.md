# 현장 웨이팅 운영 완성 — 설계

| 항목 | 내용 |
|---|---|
| 문서 유형 | Design Spec |
| 작성일 | 2026-09-01 |
| 상태 | Approved |
| 범위 | 테이블링식 현장 워크인 대기열의 운영 정확도·완성도. 호출 이후 운영, 예상 대기시간, 영업일 기준 채번, 개인정보 가명처리 |
| 선행 | Flyway 정상화 (본 스펙 Task 0으로 포함) |

---

## 1. 배경 / 문제

제품 컨셉이 **테이블링식 현장 워크인 웨이팅**으로 확정되었다. 손님이 매장 앞 대기줄에 서는 대신 QR을 스캔해 대기를 걸고, 자리를 떠나 있어도 순서를 확인하다가 호출을 받는다.

핵심 루프(QR 발급 → 스캔 → 등록 → 실시간 순서 → 호출 → 입장/노쇼)는 **이미 end-to-end로 동작한다.** 그러나 실제 매장에 투입하기에는 운영 정확도에 다섯 가지 공백이 있다.

| # | 공백 | 현재 상태 |
|---|---|---|
| ② | 예상 대기시간 부정확 | `avgTurnoverMinutes / tableCount * ahead` — 정수 나눗셈 순서 때문에 절사되고, 테이블 수가 회전시간보다 크면 **항상 0분** |
| ③ | 호출 이후 운영 부재 | 유예 개념이 없어 노쇼 판단이 전적으로 점주 수동. 미루기 버튼은 `disabled` (2026-06-30 스펙) |
| ④ | 대기번호가 리셋되지 않음 | 전 기간 `MAX+1`. 며칠이면 "473번 손님". 채번에 락이 없어 동시 등록 시 번호 중복 가능 |
| ⑤ | 손님 식별 정보 | 전화번호만 수집하고 대시보드에 전체 노출 |
| — | 개인정보 무기한 보관 | 등록 화면은 "호출 알림 목적으로만"이라 동의받지만, `phone_number` 삭제·변환 경로가 없음 |
| — | 지난 영업일 대기자가 목록에 잔류 | `findActiveByStoreId`가 상태로만 필터링해, 어제 등록하고 안 온 손님이 오늘 대시보드에 남는다 |

②③④는 서로 맞물린다. 특히 ④의 "하루"는 이미 자정 기준으로 집계 중인 일일 통계와 **같은 기준을 써야만** 어긋나지 않는다.

### 비목표 (Out of Scope)

- **현장성 보장** — QR이 정적이라 사진만 공유하면 원격 등록이 가능하다. 동적 QR·매장 확인 코드·위치 검증 중 무엇을 쓸지는 **실제 매장 조건(화면 유무, 손님 동선)에 달려 있어** 지인 가게가 정해진 뒤 별도 스펙으로 다룬다. 본 스펙의 유예 시간(③)이 원격 등록의 이득을 줄이는 사후 억제로 부분적으로 기능한다.
- **실측 기반 대기시간 예측** — 본 스펙은 `entered_at`을 기록해 **데이터 수집만 시작**한다. 예측 알고리즘은 표본이 쌓인 뒤 별도 스펙.
- **손님 이름 수집** — 호출 식별자는 대기번호로 확정. 전화번호는 SMS 발송과 실패 시 직접 연락 용도로만 유지한다.
- 다중 매장, 직원 계정, 대기 순서 수동 조정(드래그), 예약 기능.

---

## 2. 결정 요약

| 항목 | 결정 |
|---|---|
| 호출 후 정책 | 유예 타이머 + **점주 판단**. 시스템은 자동으로 상태를 전이시키지 않는다 |
| 미루기 | `CALLED → WAITING` (호출 취소). 번호·순서 유지, `called_at` 초기화 |
| 예상 대기시간 | 공식 교정(곱셈 우선 + 반올림) + `entered_at` 실측 수집 시작 |
| 손님 식별 | 이름 수집 안 함. 호출은 대기번호. 대시보드 전화번호는 마스킹 |
| 영업일 경계 | `StoreSettings.businessDayStart` (기본 05:00). **채번·통계·가명처리 공통 기준** |
| 개인정보 | 영업일이 지나면 **가명처리** — 전체 번호를 폐기하고 뒤 4자리 + HMAC 해시만 보관 |
| 대기 목록 | 조회를 **현재 영업일로 한정** (지난 영업일 잔류 제거) |

---

## 3. 핵심 결정 — 영업일을 계산하지 말고 값으로 저장한다

영업일은 이제 **채번·통계·가명처리 세 지점을 동시에 지배**한다. 표현 방식이 구조를 가른다.

| 안 | 방식 | 판단 |
|---|---|---|
| **1 (채택)** | `waiting_entries.business_date` 컬럼에 등록 시점 확정값 저장 | `UNIQUE (store_id, business_date, waiting_number)` 를 걸 수 있어 **채번 경합이 DB 레벨에서 닫힌다.** 통계·가명처리 쿼리가 범위 조건 없이 등호 하나로 끝난다 |
| 2 | 컬럼 없이 매 쿼리에서 시각 범위 계산 | 스키마를 안 건드리지만 **유니크 제약이 불가능**해 채번 경합이 남는다. 비관적 락이나 시퀀스로 따로 풀어야 한다 |
| 3 | `store_waiting_sequence` 시퀀스 테이블 | 채번은 확실하나 테이블이 늘고 영업일 전환 처리가 별도로 필요. 안 1이 제약만으로 같은 목적을 달성하므로 과하다 |

**안 1을 채택한다.** 해결하려던 문제(번호 리셋) 하나로 별개 문제(채번 경합)까지 함께 닫힌다.

부수 효과로, 점주가 나중에 영업일 시작 시각을 바꿔도 **과거 데이터의 소속은 변하지 않는다.** 원장 성격상 이게 옳다.

### `BusinessDay` 값 객체를 만들지 않는 이유

`business_date`를 저장하는 순간 영업일이 필요한 네 지점(등록 시 확정, 통계 조회, 채번, 가명처리 대상 선정)이 **전부 `LocalDate` 하나로 끝난다.** 구간 계산이 사라지므로 VO는 `LocalDate`를 감싼 빈 껍데기가 된다. `backend/CLAUDE.md`가 경고하는 가짜 추상이다.

대신 **1순위 원칙(기존 도메인 객체에 메서드로 넣는다)** 을 따라 `StoreSettings`에 메서드를 추가한다.

---

## 4. 도메인 모델

### 4-1. `StoreSettings` — 영업일과 유예를 아는 객체가 된다

새 필드 두 개:

```java
private final LocalTime businessDayStart;   // 기본 05:00
private final int callGraceMinutes;         // 기본 5
```

새 도메인 메서드 — "이 시각은 어느 영업일에 속하는가":

```java
public LocalDate businessDateOf(LocalDateTime at) {
  return at.toLocalTime().isBefore(businessDayStart)
      ? at.toLocalDate().minusDays(1)
      : at.toLocalDate();
}
```

새벽 2시 등록은 전날 영업일로 귀속된다. 채번·통계·가명처리가 모두 이 한 메서드를 경유하므로 기준이 갈릴 수 없다.

예상 대기시간 공식도 여기서 고친다:

```java
// 현재: avgTurnoverMinutes / tableCount * aheadCount
//   → 30분·7테이블이면 30/7=4 로 절사, 테이블 수 > 회전시간이면 항상 0
public int calculateEstimatedWait(int aheadCount) {
  return Math.toIntExact(Math.round((double) avgTurnoverMinutes * aheadCount / tableCount));
}
```

`tableCount`가 0이면 나눗셈이 깨지므로 `update()`에 범위 검증(1~100)을 넣는다. `createDefault()`에 새 필드의 기본값(05:00, 5분)을 추가한다.

### 4-2. `WaitingEntry` — 시각 세 개가 추가된다

| 필드 | 타입 | 채워지는 시점 | 용도 |
|---|---|---|---|
| `businessDate` | `LocalDate` | 등록 시 확정, 이후 불변 | 채번·통계·가명처리 공통 기준 |
| `calledAt` | `LocalDateTime` (nullable) | `call()` 시 기록, `postpone()` 시 null로 초기화 | 유예 만료 계산 기준점 |
| `enteredAt` | `LocalDateTime` (nullable) | `enter()` 시 기록 | 실측 대기시간(`createdAt→enteredAt`) 수집 |
| `phoneHash` | `String` (nullable) | 가명처리 시 기록 | 반복 노쇼 손님 식별 (HMAC-SHA256) |

`phoneNumber`는 **`NOT NULL`을 유지한다.** 가명처리 후에도 뒤 4자리가 들어가므로 값이 항상 존재하며, null 방어 코드가 필요 없다. 대신 "전체 번호인가 뒤 4자리인가"를 도메인이 알아야 한다 — `phoneHash != null` 이면 가명처리된 것이다.

새 도메인 메서드 둘:

```java
// 유예 만료 판정 — graceMinutes를 인자로 받아 도메인 순수성 유지 (StoreSettings를 import하지 않는다)
public boolean isGraceExpired(LocalDateTime now, int graceMinutes) {
  return status == WaitingStatus.CALLED
      && calledAt != null
      && calledAt.plusMinutes(graceMinutes).isBefore(now);
}

// 가명처리 — 전체 번호를 뒤 4자리로 줄이고 해시를 심는다. 통계 필드는 보존.
// 해시 산출은 비밀키가 필요한 인프라 관심사이므로 계산된 값을 인자로 받는다 (도메인 순수성 유지).
public WaitingEntry pseudonymize(String phoneHash) { ... }

// 가명처리 여부 — SMS 발송 가능 여부의 판단 근거
public boolean isPseudonymized() {
  return phoneHash != null;
}
```

`create()` 시그니처에 `businessDate`가 추가되고, `restore()`에 세 필드가 추가된다.

### 4-3. 상태 전이

**새 상태를 추가하지 않는다.** `WaitingStatus` 5개를 그대로 두고 전이 하나만 늘린다.

```
                 ┌──────────── postpone() ────────────┐
                 ↓                                    │
  create() → WAITING ──── call() ────→ CALLED ────────┘
                 │         (calledAt)     │
                 │                        ├─ enter()  → ENTERED   (enteredAt 기록)
                 ├─ cancel() ─┐           ├─ noShow() → NO_SHOW
                 │            ↓           └─ cancel() → CANCELLED
                 └────→ CANCELLED
```

`postpone()`은 `CALLED → WAITING`으로 되돌리며 `calledAt`을 null로 지운다. 번호도 순서도 그대로라 다른 손님에게 영향이 없고, 점주는 다음 팀을 호출하면 된다 (`call()`은 이미 순서를 강제하지 않는다). 다른 상태에서 호출하면 기존 패턴대로 `IllegalStateException`.

`call()`은 **가명처리된 항목에서 `IllegalStateException`을 던진다.** 전체 번호가 이미 없어 SMS를 보낼 수 없기 때문이다. 정상 흐름에서는 §7의 대기 목록 영업일 한정 때문에 이런 항목이 점주 화면에 뜨지 않지만, 도메인이 스스로를 방어한다.

**유예 만료는 상태가 아니라 파생 값이다.** `EXPIRED` 상태를 만들지 않는 이유는 두 가지다 — 만료 순간에 전이를 일으킬 주체(스케줄러)가 필요해지고, "시스템은 자동으로 내보내지 않는다"는 정책과 어긋난다. `calledAt` + 조회 시점 계산이면 서버 재기동에도 안전하다.

---

## 5. 스키마

### 5-1. Task 0 — Flyway 정상화 (선행)

본 스펙은 스키마를 바꾸는 첫 작업이다. 현재 Flyway는 **테스트에서만** 실행되고, 로컬은 `ddl-auto: update`, 프로덕션 프로필은 마이그레이션을 실행하지 않는다. 이 상태로 V2를 올리면 테스트와 로컬 스키마의 괴리가 확정된다.

| 파일 | 변경 |
|---|---|
| `application.yml` | `flyway.enabled: true` (전역 차단 해제) |
| `application-local.yml` | `flyway.enabled` 오버라이드 제거, `ddl-auto: update` → **`validate`** |
| `application-prod.yml` | 전역 설정을 상속하므로 변경 없음 — 실행 여부만 확인 |
| `V1__init.sql` | **개발 시드 데이터 분리** (아래) |

**시드 분리:** `V1__init.sql` 하단의 개발 시드(owner 1명 + store 3개)를 제거하고 `db/seed/R__dev_seed.sql`로 옮긴다. `application-local.yml`에서만 `spring.flyway.locations: classpath:db/migration,classpath:db/seed`로 지정해 **로컬에서만 적용**한다. 이렇게 하지 않으면 프로덕션 DB에 더미 점주와 가짜 매장 3개가 들어간다.

시드의 `password_hash`가 `dev-password-hash`로 BCrypt 형식이 아니라 로그인이 불가능하므로, 옮기는 김에 실제 BCrypt 해시로 교체한다.

Repeatable 마이그레이션(`R__`)은 **체크섬이 바뀔 때마다 재실행**되므로, 고정 UUID를 쓰는 시드는 반드시 멱등해야 한다. 모든 `INSERT`에 `ON CONFLICT (id) DO NOTHING`을 붙인다.

테스트의 `IntegrationTestSupport`는 Flyway 기본 위치(`classpath:db/migration`)만 마이그레이션하므로 `db/seed`는 실행되지 않는다. 테스트가 시드에 의존하지 않는다는 위 확인과 일관된다.

> **검증 완료:** 시드 UUID를 참조하는 테스트는 0개다(`grep` 확인). 분리해도 기존 테스트에 영향이 없다.

### 5-2. `V2__waiting_operations.sql`

```sql
ALTER TABLE store_settings
  ADD COLUMN business_day_start TIME NOT NULL DEFAULT '05:00',
  ADD COLUMN call_grace_minutes INT  NOT NULL DEFAULT 5;

ALTER TABLE waiting_entries
  ADD COLUMN business_date DATE,
  ADD COLUMN called_at     TIMESTAMP,
  ADD COLUMN entered_at    TIMESTAMP,
  ADD COLUMN phone_hash    VARCHAR(64);   -- HMAC-SHA256 hex, 가명처리 시 채워짐

-- 기존 행 backfill. 실데이터는 없으나 로컬/개발 DB를 위한 안전장치.
-- 기본 영업일 시작(05:00)을 가정한다.
UPDATE waiting_entries
   SET business_date = (created_at - INTERVAL '5 hours')::date
 WHERE business_date IS NULL;

ALTER TABLE waiting_entries ALTER COLUMN business_date SET NOT NULL;
-- phone_number 는 NOT NULL 을 유지한다. 가명처리 후에도 뒤 4자리가 들어간다.

ALTER TABLE waiting_entries
  ADD CONSTRAINT uq_waiting_store_business_date_number
  UNIQUE (store_id, business_date, waiting_number);
```

인덱스는 추가하지 않는다. 통계·가명처리·채번 쿼리가 모두 `(store_id, business_date …)`로 시작해 위 유니크 인덱스의 선두 컬럼으로 커버된다. 기존 `idx_waiting_store_status`는 유지한다.

---

## 6. 채번 — 유니크 제약 + 재시도

```java
int next = waitingRepository.findNextWaitingNumber(storeId, businessDate);  // MAX+1 (영업일 한정)
```

동시 등록으로 같은 번호가 나오면 DB가 유니크 제약으로 거부한다. 애플리케이션은 `DataIntegrityViolationException`을 잡아 **채번부터 다시 시도**한다 (최대 3회, 초과 시 예외 전파).

> **구현 제약 — 재시도는 트랜잭션 밖에서 한다.**
> 제약 위반이 발생한 트랜잭션은 rollback-only로 오염되므로 같은 트랜잭션 안에서 재시도하면 실패한다. `@Transactional registerOnce()`를 분리하고, 트랜잭션 경계 밖의 호출자가 루프를 돈다. 매 시도가 새 트랜잭션이어야 한다.

경합이 심해지면 `pg_advisory_xact_lock` 기반 직렬화로 바꿀 수 있으나, 소규모 매장의 동시 등록 빈도를 감안하면 재시도로 충분하다. YAGNI.

---

## 7. API · DTO

기존 패턴(`POST /api/owner/waitings/{id}/{action}` → 204)을 그대로 따른다.

### 새 엔드포인트 1개

```
POST /api/owner/waitings/{waitingId}/postpone   → 204 No Content
```

소유권 검증은 기존 `loadOwnedEntry()` 헬퍼를 재사용한다(타 매장 접근 시 `StoreNotFoundException` → 404, 존재 은닉).

### DTO 변경

| DTO | 변경 |
|---|---|
| `OwnerWaitingResponse` | `phoneNumber` → **마스킹** (`010-****-5678`), `graceDeadline` 추가 (nullable) |
| `TodayWaitingResponse` | `phoneNumber` 마스킹, `waitedMinutes` 추가 (`createdAt→enteredAt`, ENTERED만 값) |
| `MyWaitingStatusResponse` | `graceDeadline` 추가 — 호출된 손님이 남은 시간을 본다 |

**유예는 남은 초가 아니라 만료 시각(`graceDeadline = calledAt + callGraceMinutes`)으로 내려보낸다.** 상태가 `CALLED`가 아니면(=`calledAt`이 null이면) `graceDeadline`도 null이다. 남은 초는 SSE 재연결이나 화면 복귀 때 상하지만, 절대 시각은 클라이언트가 언제 받아도 스스로 카운트다운할 수 있다. **만료 강조 표시는 클라이언트 타이머가 하므로 만료 감시용 스케줄러가 필요 없다.**

전화번호 전체가 필요한 곳은 SMS 발송 실패 배너뿐이고, 그건 이미 별도 SSE 이벤트(`sms-send-failed`)가 전체 번호를 따로 보낸다. 목록을 마스킹해도 운영에 지장이 없다.

**마스킹 헬퍼는 값의 뒤 4자리만 취해 `****-{last4}` 형태로 렌더한다.** 저장값이 전체 번호든 가명처리된 뒤 4자리든 **출력이 동일**하므로, "이 행이 가명처리되었는가"가 화면에 드러나지 않고 조건 분기도 필요 없다. 가명처리 후 이력 화면의 손님 열이 비어버리는 문제도 함께 사라진다.

### 대기 목록을 현재 영업일로 한정

`findActiveByStoreId`가 지금은 상태(`WAITING`·`CALLED`)로만 필터링해, **지난 영업일에 등록하고 오지 않은 손님이 오늘 대시보드에 그대로 남는다.** 조회 조건에 `business_date = 현재 영업일`을 추가한다.

이는 화면 정리에 그치지 않는다. 가명처리를 도입하면 지난 영업일 항목의 전체 번호가 사라지므로, 그 항목이 목록에 남아 있으면 점주가 호출을 눌렀을 때 뒤 4자리(`5678`)로 SMS 발송을 시도하게 된다. 목록 한정이 그 경로를 막고, §4-3의 `call()` 가드가 최종 방어선이 된다.

지난 영업일의 미처리 항목은 상태를 그대로 둔 채 목록에서만 빠진다(통계에는 남는다). 자동으로 노쇼 처리하지 않는 것은 "시스템은 자동 전이시키지 않는다"는 §2 정책과 일관된다.

---

## 8. SSE

### 새 이벤트 1개

```
event: waiting-postponed
data: {"waitingId": "..."}
```

`waiting-called`와 대칭이다. 호출 화면에 있던 손님이 이 이벤트를 받으면 대기 화면으로 되돌아간다. **이게 없으면 미루기를 당한 손님이 "입장하세요" 화면에 갇힌다.** 브로드캐스트 범위(매장 손님 채널 전체)와 클라이언트 필터링(자신의 `waitingId` 비교)은 기존 `waiting-called`와 동일하다.

`SsePublisher.broadcastPostponed(storeId, waitingId)`를 추가한다. 기존 `broadcastCalled`와 같은 형태다.

`postpone()`은 대기 인원 수를 바꾸지 않지만 점주 대시보드 목록은 갱신되어야 하므로, `WaitingUpdatedEvent`도 함께 발행해 기존 `broadcastUpdate` 경로를 탄다.

---

## 9. 개인정보 가명처리

영업일이 지나면 전체 전화번호를 폐기하고 **뒤 4자리 + HMAC 해시**만 남긴다.

> **용어 주의 — 이것은 "파기"가 아니라 "가명처리"다.**
> 뒤 4자리와 해시가 남으므로 완전한 익명정보가 아니다. 가명정보는 개인정보 보호법상 여전히 보호 대상이며, 문서·UI·동의 문구에서 "파기"라고 표현해서는 안 된다.

### 남기는 값과 그 목적

| 남기는 값 | 목적 | 형태 |
|---|---|---|
| 뒤 4자리 | 점주가 이력에서 눈으로 대조 | `phone_number` 컬럼을 `5678` 로 축약 |
| HMAC 해시 | 반복 노쇼 손님의 **시스템 식별** | `phone_hash` — HMAC-SHA256 hex |

**해시는 반드시 HMAC이어야 한다.** 한국 휴대폰 번호 공간은 `010-XXXX-XXXX` = 10^8 뿐이라, 솔트 없는 SHA-256은 전수 대입으로 수 초 만에 역산된다. 비밀키를 쓰는 HMAC이라야 키를 모르는 쪽에서 원본을 복원할 수 없다.

- 키는 `PHONE_HASH_SECRET` 환경변수로 주입한다 (`JWT_SECRET`과 동일한 방식).
- **키를 교체하면 기존 해시와 매칭이 끊긴다.** 노쇼 이력의 연속성이 필요하므로 로테이션은 신중해야 한다.
- 해시 산출은 비밀키를 다루는 인프라 관심사이므로 `shared/` 에 두고, 도메인의 `pseudonymize(phoneHash)` 에는 **계산된 값만** 넘긴다.

> **반복 노쇼 누적 기능 자체는 본 스펙의 범위가 아니다.** 여기서는 그 기능이 나중에 쓸 데이터를 수집만 한다. 개인정보는 소급 생성이 불가능하므로 `entered_at`과 같은 논리다. 단, **목적을 지금 고지해야** 정당하다 (아래 동의 문구).

### 배치

```java
@Scheduled(cron = "0 0 * * * *")   // 매시 정각
```

매장마다 영업일 경계가 다르므로 매장별로 순회하며, **`business_date < 해당 매장의 현재 영업일`이고 `phone_hash IS NULL`**(아직 가명처리 전) 인 항목을 처리한다. 하루 한 번이 아니라 매시로 도는 이유는, 매장별 경계가 제각각이라 몰아서 돌릴 공통 기준 시각이 없기 때문이다.

- 위치: `waiting/management/application/WaitingRetentionService`. 보관 정책은 웨이팅 애그리거트의 규칙이다.
- `ApiApplication`에 `@EnableScheduling` 추가.
- 대량 `UPDATE` 대신 **도메인의 `pseudonymize()`를 경유**한다. 매시간 돌아 잔량이 적으므로 계층 규칙을 우회할 이유가 없다.
- 멱등해야 한다 — 이미 가명처리된 항목(`phone_hash IS NOT NULL`)은 건너뛴다. 두 번 처리하면 뒤 4자리의 뒤 4자리를 취하게 된다.

### 동의 문구 변경 (필수)

현재 문구는 *"전화번호는 웨이팅 호출 알림 목적으로만 사용됩니다"* 다. 가명처리된 값을 이력 관리·노쇼 방지에 계속 쓰므로 **목적이 확장되었고, 문구를 함께 바꾸지 않으면 약속과 실제가 다시 어긋난다.**

> 전화번호는 웨이팅 호출 알림에 사용합니다. 영업일이 끝나면 전체 번호는 폐기하고, 뒤 4자리와 식별할 수 없는 형태로 변환한 값만 이력 관리 및 반복 노쇼 방지를 위해 보관합니다.

`LandingPage.tsx`의 동의 라벨을 이 문구로 교체한다.

---

## 10. 실측 대기시간 수집

`enteredAt`을 기록하기 시작한다. 예측 알고리즘은 본 스펙의 비목표다.

> **왜 "평균 회전시간"이 아니라 "대기시간"인가**
> 손님이 매장을 언제 떠났는지 시스템은 알 수 없다(입장 처리까지만 기록). 따라서 회전시간은 원리적으로 측정 불가다. 측정 가능하고 손님이 실제로 궁금해하는 값은 **등록 → 입장까지의 대기시간**이다.

즉시 얻는 효용으로 `TodayWaitingResponse.waitedMinutes`를 점주 이력 화면에 노출한다. 점주가 자기 매장의 실제 대기시간을 보고 `avgTurnoverMinutes` 설정을 스스로 교정할 수 있다.

---

## 10-1. 변경 파일 개요

**백엔드 — 생성**
- `db/migration/V2__waiting_operations.sql`
- `db/seed/R__dev_seed.sql`
- `waiting/management/application/WaitingRetentionService.java`
- `shared/privacy/PhoneHasher.java` — HMAC-SHA256 산출 (비밀키 주입)

**백엔드 — 수정**
- `store/domain/StoreSettings.java` — 필드 2개, `businessDateOf()`, 공식 교정, `createDefault`/`restore`/`update`
- `store/infrastructure/StoreSettingsJpaEntity.java` · `StoreSettingsJpaRepository.java`
- `store/management/dto/UpdateStoreSettingsRequest.java` · `StoreSettingsResponse.java`
- `waiting/domain/WaitingEntry.java` — 필드 4개, `postpone()`, `isGraceExpired()`, `pseudonymize()`, `isPseudonymized()`, `call()` 가드, `create`/`restore`
- `waiting/domain/WaitingRepository.java` — `findNextWaitingNumber(storeId, businessDate)`, `findActiveByStoreId(storeId, businessDate)`, 가명처리 대상 조회 추가
- `waiting/infrastructure/WaitingEntryJpaEntity.java` · `WaitingEntryJpaRepository.java` · `WaitingRepositoryImpl.java`
- `waiting/customer/application/WaitingService.java` — 채번 재시도(트랜잭션 경계 분리), `businessDate` 확정
- `waiting/management/application/WaitingManagementService.java` — `postpone()`, 통계 기준을 `businessDate`로
- `waiting/management/presentation/OwnerWaitingController.java` — `postpone` 엔드포인트
- `waiting/management/dto/OwnerWaitingResponse.java` · `TodayWaitingResponse.java`
- `waiting/customer/dto/MyWaitingStatusResponse.java`
- `shared/sse/SsePublisher.java` — `broadcastPostponed()`
- `shared/sse/SseEventListener.java` — 이벤트 구독 추가
- `ApiApplication.java` — `@EnableScheduling`
- `application.yml` · `application-local.yml` · `V1__init.sql` (Task 0)
- `.env.example` · `docker-compose.yml` — `PHONE_HASH_SECRET` 추가

## 11. 프론트 영향

| 파일 | 변경 |
|---|---|
| `DashboardPage.tsx` | [미루기] 버튼 활성화 + `postpone` API 호출, `graceDeadline` 기반 카운트다운·만료 강조, 마스킹된 번호 표시 |
| `WaitingCalledPage.tsx` | 남은 유예 시간 카운트다운, `waiting-postponed` 수신 시 대기 화면 복귀 |
| `useWaitingSse.ts` | `waiting-postponed` 이벤트 핸들러 추가 (`onPostponed`) |
| `StoreSettingsPage.tsx` | 영업일 시작 시각·호출 유예 시간 입력 |
| `HistoryPage.tsx` | 실측 대기시간(`waitedMinutes`) 열 추가 |
| `LandingPage.tsx` | **개인정보 동의 문구 교체** (§9) |
| `api/owner.ts` · `api/waiting.ts` | 타입·엔드포인트 추가 |

---

## 12. 테스트 전략

| 대상 | 검증 |
|---|---|
| `StoreSettings.businessDateOf` | 경계값 — 04:59(전날), 05:00(당일), 자정 직후, 정오 |
| `calculateEstimatedWait` | 절사 케이스(30분·7테이블), **테이블 수 > 회전시간이면 0이 나오던 버그**, `tableCount` 검증 |
| `WaitingEntry.postpone` | `CALLED→WAITING` + `calledAt` 초기화, 다른 상태에서 `IllegalStateException` |
| `WaitingEntry.isGraceExpired` | 만료 직전·정각·직후, `CALLED`가 아닌 상태는 항상 false, `calledAt == null` 방어 |
| `WaitingEntry.pseudonymize` | 뒤 4자리로 축약 + 해시 기록, 통계 필드 보존, **재실행해도 4자리가 더 잘리지 않음**(멱등) |
| `WaitingEntry.call` | 가명처리된 항목에서 `IllegalStateException` |
| `PhoneHasher` | 같은 번호 → 같은 해시, 다른 번호 → 다른 해시, 키가 다르면 결과가 다름 |
| **채번 동시성** | `IntegrationTestSupport` 기반 병렬 등록 → 유니크 위반 시 재시도로 연속 번호 보장, 중복 없음 |
| 가명처리 배치 | 이전 영업일만 처리, 당일 건 보존, 이미 처리된 건(`phone_hash IS NOT NULL`) 건너뜀 |
| 대기 목록 영업일 한정 | 지난 영업일의 `WAITING` 항목이 목록에서 빠지고, 통계에는 남음 |
| `PostponeController` | 204, 타 매장 접근 시 404 |
| 회귀 | 기존 127개 통과 |

채번 동시성 테스트는 2026-08-03 작업으로 도입된 Testcontainers를 실제 PostgreSQL 제약 검증에 처음으로 제대로 활용하는 사례다.

---

## 13. 리스크

| 리스크 | 완화 |
|---|---|
| **`ddl-auto: validate` 전환 시 컨텍스트 로드 실패** — V1/V2와 JPA 엔티티가 컬럼 nullability·타입에서 어긋나 있으면 드러난다 | 2026-08-03 스펙이 이미 예고한 리스크다. 발생 시 엔티티/마이그레이션을 맞춘다. 이는 운영 충실도를 높인 결과이지 회귀가 아니다 |
| 유니크 제약 추가 시 기존 dev 데이터에 중복 번호가 있으면 실패 | 현재 채번이 매장별 전역 `MAX+1`이라 매장 내 번호는 유일하다. 과거 경합으로 중복이 생겼다면 제약 추가가 실패하므로 로컬 DB를 초기화한다 |
| 재시도를 트랜잭션 안에서 구현하면 rollback-only로 항상 실패 | §6에 명시. 구현 플랜에서 트랜잭션 경계를 분리하도록 지시 |
| `graceDeadline` 카운트다운이 클라이언트 시계에 의존 | 유예가 분 단위(기본 5분)라 초 단위 오차는 무해. 만료 판정의 진실은 서버의 `isGraceExpired` |
| **`PHONE_HASH_SECRET` 교체 시 노쇼 이력 단절** — 기존 해시와 매칭 불가 | 키를 `JWT_SECRET`과 동일하게 환경변수로 관리하고 로테이션하지 않는 것을 원칙으로 한다. 불가피하면 이력이 끊김을 감수한다 |
| 가명처리 배치가 두 번 돌면 뒤 4자리가 또 잘림 | `phone_hash IS NULL` 조건으로 대상을 한정해 멱등성 확보. §12에 테스트로 고정 |
| 뒤 4자리 + 해시는 익명정보가 아닌 **가명정보** | 문서·UI에서 "파기"로 표현하지 않는다. 동의 문구에 보관 목적을 명시(§9) |
| 시드 분리로 로컬 개발 계정 소실 | `R__dev_seed.sql`을 local 프로필 `locations`에 포함해 기존과 동일하게 적용. BCrypt 해시로 교체해 오히려 로그인 가능해진다 |

---

## 14. 검증 (완료 기준)

1. `cd backend && ./gradlew test` — 신규 포함 전체 통과.
2. 로컬을 Flyway + `validate`로 기동했을 때 정상 부팅하고, `flyway_schema_history`에 V1·V2가 기록된다.
3. 손님 2명을 동시에 등록해도 대기번호가 중복되지 않는다.
4. 영업일 시작 시각을 넘겨 등록하면 대기번호가 1번부터 다시 시작하고, 같은 시점에 일일 통계도 초기화된다.
5. 호출 후 유예 시간이 지나면 대시보드에서 해당 손님이 강조되지만 **상태는 CALLED 그대로**다.
6. [미루기] → 손님 화면이 호출 화면에서 대기 화면으로 돌아오고, 번호와 순서가 유지된다.
7. 테이블 40개·회전 30분으로 설정해도 예상 대기시간이 0분이 아니다.
8. 영업일이 지난 웨이팅은 전체 번호가 사라지고 뒤 4자리 + 해시만 남으며, 통계 건수는 변하지 않는다. 배치를 두 번 돌려도 결과가 같다.
9. 점주 대시보드 목록에 번호가 `****-5678`로 표시되고, SMS 실패 배너에는 전체 번호가 나온다. 가명처리 전후로 **화면 표시가 동일**하다.
10. 지난 영업일에 등록하고 오지 않은 손님이 오늘 대시보드 목록에 보이지 않는다.
11. 등록 화면의 동의 문구가 실제 보관 방식(뒤 4자리 + 변환값)과 일치한다.

---

## 15. 후속 스펙

| 주제 | 선행 조건 |
|---|---|
| **현장성 보장** (동적 QR / 매장 확인 코드 / 위치 검증) | 지인 가게 확정 — 매장의 화면 유무와 손님 동선에 따라 방식이 갈린다 |
| **실측 기반 대기시간 예측** | 본 스펙의 `entered_at` 데이터가 충분히 축적 |
| 배포 환경 정비 (docker-compose 프로필, SSE 수평 확장) | 서버 준비 |
