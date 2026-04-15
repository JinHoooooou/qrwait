# Phase 3 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 3 스펙(전화번호 전환, 대기 현황 표시, SMS 알림, CALLED 자동 만료, 번호 일별 초기화, 날짜 범위 통계)을 구현한다.

**Architecture:** 기존 `@TransactionalEventListener` 이벤트 구조에 `SmsNotificationListener`를 추가한다. 자동 만료는 `@Scheduled` 잡으로 처리한다. 전화번호 전환은 DB 마이그레이션 → 도메인 →
DTO → 프론트엔드 순으로 진행한다.

**Tech Stack:** Spring Boot 3.5 / JPA / Flyway / React / TypeScript — 외부 라이브러리 추가 없이 RestClient(내장)로 Solapi REST API 호출

---

## 파일 맵

| 파일                                                         | 역할                                         |
|------------------------------------------------------------|--------------------------------------------|
| `db/migration/V2__rename_visitor_name_to_phone_number.sql` | visitor_name → phone_number 컬럼 변경          |
| `db/migration/V3__add_called_at_to_waiting_entries.sql`    | called_at 컬럼 추가                            |
| `waiting/domain/WaitingEntry.java`                         | phoneNumber 필드 + calledAt 필드               |
| `waiting/infrastructure/WaitingEntryJpaEntity.java`        | DB 컬럼 매핑 반영                                |
| `waiting/application/dto/RegisterWaitingRequest.java`      | phoneNumber + @Pattern 검증                  |
| `waiting/application/dto/OwnerWaitingResponse.java`        | phoneNumber 컴포넌트                           |
| `waiting/application/WaitingService.java`                  | getPhoneNumber() + 대기 현황 API               |
| `waiting/application/WaitingManagementService.java`        | call() enriched 이벤트 발행                     |
| `waiting/presentation/WaitingController.java`              | GET /stores/{id}/waitings/status 노출        |
| `waiting/domain/event/WaitingCalledEvent.java`             | phoneNumber + waitingNumber + storeName 추가 |
| `waiting/domain/WaitingRepository.java`                    | findCalledBefore() + findDailyHistory() 추가 |
| `waiting/infrastructure/WaitingRepositoryImpl.java`        | 위 메서드 구현                                   |
| `waiting/infrastructure/WaitingEntryJpaRepository.java`    | 쿼리 추가/수정                                   |
| `waiting/domain/DailyStatRow.java`                         | 통계 도메인 레코드 (신규)                            |
| `waiting/application/dto/WaitingHistoryResponse.java`      | 통계 응답 DTO (신규)                             |
| `waiting/presentation/OwnerWaitingController.java`         | GET /waitings/history 엔드포인트 추가             |
| `waiting/application/WaitingAutoNoShowScheduler.java`      | @Scheduled 자동 노쇼 처리 (신규)                   |
| `shared/scheduler/SchedulerConfig.java`                    | @EnableScheduling 설정 (신규)                  |
| `shared/sms/SmsClient.java`                                | SMS 발송 인터페이스 (신규)                          |
| `shared/sms/SolapiSmsClient.java`                          | Solapi REST 구현체 (신규)                       |
| `shared/sms/SmsProperties.java`                            | SMS 설정 바인딩 (신규)                            |
| `shared/sms/SmsNotificationListener.java`                  | @TransactionalEventListener (신규)           |
| `resources/application.yml`                                | sms.* / waiting.auto-noshow-minutes 추가     |
| `frontend/src/api/waiting.ts`                              | 타입 + getStoreStatus 추가                     |
| `frontend/src/api/owner.ts`                                | phoneNumber 타입 + 통계 API 추가                 |
| `frontend/src/pages/LandingPage.tsx`                       | 전화번호 + 대기 현황 + 동의 체크박스                     |
| `frontend/src/pages/DashboardPage.tsx`                     | phoneNumber 표시 전환 + 통계 탭 링크                |
| `frontend/src/pages/StatsPage.tsx`                         | 날짜 범위 통계 페이지 (신규)                          |
| `frontend/src/App.tsx`                                     | /owner/stats 라우트 추가                        |

---

## Task 1: DB 마이그레이션 — visitor_name → phone_number

**Files:**

- Create: `backend/src/main/resources/db/migration/V2__rename_visitor_name_to_phone_number.sql`

- [ ] **Step 1: 마이그레이션 파일 작성**

```sql
ALTER TABLE waiting_entries
    RENAME COLUMN visitor_name TO phone_number;

ALTER TABLE waiting_entries
    ALTER COLUMN phone_number TYPE VARCHAR(20);
```

- [ ] **Step 2: 백엔드 실행하여 마이그레이션 적용 확인**

```bash
cd backend
./gradlew bootRun
# 서버 시작 시 Flyway가 V2 마이그레이션을 자동 적용함
# 로그에서 "Successfully applied 1 migration to schema" 확인
```

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V2__rename_visitor_name_to_phone_number.sql
git commit -m "db: visitor_name 컬럼을 phone_number로 변경 (V2 마이그레이션)"
```

---

## Task 2: WaitingEntry 도메인 — visitorName → phoneNumber

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`

- [ ] **Step 1: WaitingRepositoryImplTest — 필드명 변경 반영 (컴파일 에러 먼저 확인)**

```bash
cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest" 2>&1 | head -30
```

Expected: 아직 코드 변경 전이므로 PASS (이 테스트는 필드명을 직접 참조하지 않음)

- [ ] **Step 2: WaitingEntry.java — visitorName → phoneNumber 전환**

```java
package com.qrwait.api.waiting.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class WaitingEntry {

  private final UUID id;
  private final UUID storeId;
  private final String phoneNumber;
  private final int partySize;
  private final int waitingNumber;
  private final LocalDateTime createdAt;
  private final WaitingStatus status;

  private WaitingEntry(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, WaitingStatus status, LocalDateTime createdAt) {
    this.id = id;
    this.storeId = storeId;
    this.phoneNumber = phoneNumber;
    this.partySize = partySize;
    this.waitingNumber = waitingNumber;
    this.status = status;
    this.createdAt = createdAt;
  }

  public static WaitingEntry create(UUID storeId, String phoneNumber, int partySize, int waitingNumber) {
    return new WaitingEntry(
        UUID.randomUUID(),
        storeId,
        phoneNumber,
        partySize,
        waitingNumber,
        WaitingStatus.WAITING,
        LocalDateTime.now()
    );
  }

  public static WaitingEntry restore(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, WaitingStatus status, LocalDateTime createdAt) {
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, status, createdAt);
  }

  public WaitingEntry call() {
    if (status != WaitingStatus.WAITING) {
      throw new IllegalStateException(
          "call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.CALLED, createdAt);
  }

  public WaitingEntry enter() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "enter() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.ENTERED, createdAt);
  }

  public WaitingEntry cancel() {
    if (status != WaitingStatus.WAITING && status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "cancel() 은 WAITING 또는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.CANCELLED, createdAt);
  }

  public WaitingEntry noShow() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "noShow() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.NO_SHOW, createdAt);
  }
}
```

- [ ] **Step 3: 컴파일 에러 확인 — visitorName 참조 파일 목록 확인**

```bash
cd backend && ./gradlew compileJava 2>&1 | grep "error:"
```

Expected: `visitorName`을 참조하는 파일들의 컴파일 에러 목록 출력

- [ ] **Step 4: WaitingRepositoryImplTest — 전화번호 문자열로 업데이트**

`backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java` 의 `"손님A"`, `"손님B"` 를 `"010-1111-1111"`, `"010-2222-2222"`
로 교체:

```java

@Test
void findByStoreIdAndStatus_returnsMatchingEntries() {
  waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1));
  waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2));

  List<WaitingEntry> result = waitingRepository.findByStoreIdAndStatus(savedStore.getId(), WaitingStatus.WAITING);

  assertThat(result).hasSize(2);
  assertThat(result).allMatch(e -> e.getStatus() == WaitingStatus.WAITING);
}

@Test
void countByStoreIdAndStatus_returnsCorrectCount() {
  waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1));
  waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2));

  int count = waitingRepository.countByStoreIdAndStatus(savedStore.getId(), WaitingStatus.WAITING);

  assertThat(count).isEqualTo(2);
}
```

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java
git add backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java
git commit -m "refactor: WaitingEntry visitorName → phoneNumber 필드 전환"
```

---

## Task 3: JPA 엔티티 + DTO + 서비스 업데이트

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaEntity.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/dto/RegisterWaitingRequest.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/dto/OwnerWaitingResponse.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingService.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/application/WaitingServiceTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/presentation/WaitingControllerTest.java`

- [ ] **Step 1: WaitingEntryJpaEntity.java — 컬럼명 변경**

`visitor_name` 필드 블록 전체를 아래로 교체:

```java

@Column(name = "phone_number", nullable = false, length = 20)
private String phoneNumber;
```

생성자 시그니처:

```java
private WaitingEntryJpaEntity(UUID id, UUID storeId, String phoneNumber, int partySize,
    int waitingNumber, String status, LocalDateTime createdAt) {
  this.id = id;
  this.storeId = storeId;
  this.phoneNumber = phoneNumber;
  this.partySize = partySize;
  this.waitingNumber = waitingNumber;
  this.status = status;
  this.createdAt = createdAt;
}
```

`from()` 메서드:

```java
public static WaitingEntryJpaEntity from(WaitingEntry entry) {
  return new WaitingEntryJpaEntity(
      entry.getId(),
      entry.getStoreId(),
      entry.getPhoneNumber(),
      entry.getPartySize(),
      entry.getWaitingNumber(),
      entry.getStatus().name(),
      entry.getCreatedAt()
  );
}
```

`toDomain()` 메서드:

```java
public WaitingEntry toDomain() {
  return WaitingEntry.restore(
      id,
      storeId,
      phoneNumber,
      partySize,
      waitingNumber,
      WaitingStatus.valueOf(status),
      createdAt
  );
}
```

- [ ] **Step 2: RegisterWaitingRequest.java — phoneNumber + @Pattern 검증**

```java
package com.qrwait.api.waiting.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterWaitingRequest {

  @NotBlank(message = "전화번호는 필수입니다.")
  @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식은 010-XXXX-XXXX 이어야 합니다.")
  private String phoneNumber;

  @Min(value = 1, message = "인원은 최소 1명이어야 합니다.")
  @Max(value = 10, message = "인원은 최대 10명까지 가능합니다.")
  private int partySize;
}
```

- [ ] **Step 3: OwnerWaitingResponse.java — phoneNumber 컴포넌트**

```java
package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;
import java.util.UUID;

public record OwnerWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    long elapsedMinutes
) {

}
```

- [ ] **Step 4: WaitingService.java — getVisitorName() → getPhoneNumber()**

`register()` 메서드에서 한 줄 변경:

```java
WaitingEntry entry = WaitingEntry.create(
    storeId, request.getPhoneNumber(), request.getPartySize(), waitingNumber);
```

- [ ] **Step 5: WaitingManagementService.java — toOwnerWaitingResponse 업데이트**

```java
private OwnerWaitingResponse toOwnerWaitingResponse(WaitingEntry entry) {
  long elapsedMinutes = ChronoUnit.MINUTES.between(entry.getCreatedAt(), LocalDateTime.now());
  return new OwnerWaitingResponse(
      entry.getId(),
      entry.getWaitingNumber(),
      entry.getPhoneNumber(),
      entry.getPartySize(),
      entry.getStatus(),
      elapsedMinutes
  );
}
```

- [ ] **Step 6: WaitingServiceTest.java — setVisitorName → setPhoneNumber**

`register_정상등록_waitingNumber와_currentRank_반환` 테스트:

```java
RegisterWaitingRequest request = new RegisterWaitingRequest();
request.

setPhoneNumber("010-1234-5678");
request.

setPartySize(2);
```

나머지 테스트에서 `request.setVisitorName(...)` 호출도 동일하게 변경.

- [ ] **Step 7: WaitingManagementServiceTest.java — visitorName → phoneNumber**

`restore()` 호출에서 이름 문자열을 전화번호로 변경:

```java
WaitingEntry waiting = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
    WaitingStatus.WAITING, LocalDateTime.now().minusMinutes(10));
WaitingEntry called = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 3, 2,
    WaitingStatus.CALLED, LocalDateTime.now().minusMinutes(5));
```

assertions:

```java
assertThat(result.get(0).

phoneNumber()).

isEqualTo("010-1111-0001");

assertThat(result.get(1).

phoneNumber()).

isEqualTo("010-1111-0002");
```

나머지 `WaitingEntry.restore(... "김철수" ...)` 호출도 전화번호로 변경.

- [ ] **Step 8: WaitingControllerTest.java — setVisitorName → setPhoneNumber**

```java
RegisterWaitingRequest request = new RegisterWaitingRequest();
request.

setPhoneNumber("010-1234-5678");
request.

setPartySize(2);
```

- [ ] **Step 9: 전체 테스트 실행**

```bash
cd backend && ./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — 모든 테스트 통과

- [ ] **Step 10: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/
git add backend/src/test/java/com/qrwait/api/waiting/
git commit -m "refactor: 웨이팅 등록 필드 visitorName → phoneNumber 전환 (JPA·DTO·서비스·테스트 포함)"
```

---

## Task 4: 대기 현황 엔드포인트 노출 + 프론트엔드 API 타입 업데이트

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/presentation/WaitingController.java`
- Modify: `frontend/src/api/waiting.ts`

**배경:** `WaitingService.getStoreWaitingStatus()`는 이미 구현되어 있지만 컨트롤러에 노출되지 않음.

- [ ] **Step 1: WaitingControllerTest에 새 엔드포인트 테스트 추가**

`WaitingControllerTest.java`에 추가:

```java

@Test
void getStoreWaitingStatus_성공_200반환() throws Exception {
  UUID storeId = UUID.randomUUID();
  given(waitingService.getStoreWaitingStatus(storeId))
      .willReturn(new WaitingStatusResponse(3, 3, 15));

  mockMvc.perform(get("/api/stores/{storeId}/waitings/status", storeId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalWaiting").value(3))
      .andExpect(jsonPath("$.estimatedWaitMinutes").value(15));
}
```

Import 추가: `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;`

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.WaitingControllerTest.getStoreWaitingStatus_성공_200반환" 2>&1 | tail -15
```

Expected: FAIL — 404 Not Found (엔드포인트 없음)

- [ ] **Step 3: WaitingController.java에 엔드포인트 추가**

기존 `stream` 메서드 앞에 삽입:

```java

@Operation(summary = "매장 전체 대기 현황 조회", description = "QR 랜딩 페이지 진입 시 현재 대기 팀 수와 예상 대기 시간을 반환합니다.")
@GetMapping("/stores/{storeId}/waitings/status")
public ResponseEntity<WaitingStatusResponse> getStoreWaitingStatus(@PathVariable UUID storeId) {
  return ResponseEntity.ok(waitingService.getStoreWaitingStatus(storeId));
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test --tests "*.WaitingControllerTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: api/waiting.ts — RegisterWaitingRequest 타입 + getStoreStatus 추가**

`api/waiting.ts` 변경:

```typescript
export interface RegisterWaitingRequest {
  phoneNumber: string
  partySize: number
}

export interface StoreWaitingStatus {
  totalWaiting: number
  estimatedWaitMinutes: number
}

export const getStoreWaitingStatus = (storeId: string) =>
    client.get<StoreWaitingStatus>(`/stores/${storeId}/waitings/status`).then((res) => res.data)
```

(`visitorName: string` 라인을 `phoneNumber: string`으로 교체)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/presentation/WaitingController.java
git add backend/src/test/java/com/qrwait/api/waiting/presentation/WaitingControllerTest.java
git add frontend/src/api/waiting.ts
git commit -m "feat: 매장 대기 현황 조회 API 엔드포인트 추가 (GET /stores/{id}/waitings/status)"
```

---

## Task 5: LandingPage — 전화번호 입력 + 대기 현황 + 동의 체크박스

**Files:**

- Modify: `frontend/src/pages/LandingPage.tsx`

- [ ] **Step 1: LandingPage.tsx 전체 교체**

```typescript
import {useEffect, useRef, useState} from 'react'
import {useNavigate, useSearchParams} from 'react-router-dom'
import type {StoreResponse, StoreWaitingStatus} from '../api/waiting'
import {getStore, getStoreWaitingStatus, registerWaiting} from '../api/waiting'
import useWaitingStore from '../store/waitingStore'
import {getWaitingSession, saveWaitingSession} from '../utils/session'
import Button from '../components/Button'
import LoadingSpinner from '../components/LoadingSpinner'
import ErrorMessage from '../components/ErrorMessage'

const STATUS_MESSAGES: Record<string, string> = {
  BREAK: '현재 브레이크타임입니다.',
  FULL: '현재 만석입니다.',
  CLOSED: '오늘 영업이 종료되었습니다.',
}

function LandingPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const storeId = searchParams.get('storeId')

  const [store, setStore] = useState<StoreResponse | null>(null)
  const [waitingStatus, setWaitingStatus] = useState<StoreWaitingStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [phoneNumber, setPhoneNumber] = useState('')
  const [partySize, setPartySize] = useState(1)
  const [agreed, setAgreed] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const setWaiting = useWaitingStore((s) => s.setWaiting)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const session = getWaitingSession()
    if (session) {
      navigate(`/waiting/${session.waitingId}/status`, {replace: true})
      return
    }

    if (!storeId) {
      setError('유효하지 않은 QR 코드입니다.')
      setLoading(false)
      return
    }

    Promise.all([getStore(storeId), getStoreWaitingStatus(storeId)])
        .then(([storeData, statusData]) => {
          setStore(storeData)
          setWaitingStatus(statusData)
        })
        .catch(() => setError('매장 정보를 불러올 수 없습니다.'))
        .finally(() => setLoading(false))
  }, [storeId, navigate])

  useEffect(() => {
    if (!storeId) return

    const es = new EventSource(`/api/stores/${storeId}/stream`)
    esRef.current = es

    es.addEventListener('store-status-changed', (e) => {
      try {
        const data = JSON.parse(e.data)
        setStore((prev) => prev ? {...prev, status: data.status} : prev)
      } catch {
        // 파싱 실패 시 무시
      }
    })

    return () => {
      es.close()
      esRef.current = null
    }
  }, [storeId])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!storeId || !phoneNumber.trim() || !agreed) return

    setSubmitting(true)
    try {
      const res = await registerWaiting(storeId, {
        phoneNumber: phoneNumber.trim(),
        partySize,
      })
      setWaiting({
        waitingId: res.waitingId,
        waitingNumber: res.waitingNumber,
        storeId,
        currentRank: res.currentRank,
        totalWaiting: res.totalWaiting,
        estimatedWaitMinutes: res.estimatedWaitMinutes,
      })
      saveWaitingSession(res.waitingId, storeId, res.waitingNumber)
      navigate(`/waiting/${res.waitingId}`)
    } catch {
      setError('웨이팅 등록에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <LoadingSpinner / >
  if (error) return <div style = {styles.container} > <ErrorMessage message = {error}
  /></div >

  const statusMessage = store?.status ? STATUS_MESSAGES[store.status] : null

  return (
      <div style = {styles.container} >
      <h1 style = {styles.storeName} > {store?.name
}
  </h1>

  {
    waitingStatus && (
        <div style = {styles.statusBox} >
        <span style = {styles.statusTeam} > 현재
    대기
    {
      waitingStatus.totalWaiting
    }
    팀 < /span>
    < span
    style = {styles.statusDot} >·</span>
  < span
    style = {styles.statusTime} > 예상
    {
      waitingStatus.estimatedWaitMinutes
    }
    분 < /span>
    < /div>
  )
  }

  {
    statusMessage ? (
        <div style = {styles.unavailableBox} >
        <p style = {styles.unavailableMessage} > {statusMessage} < /p>
            < p style = {styles.unavailableHint} > 잠시
    후
    다시
    확인해
    주세요. < /p>
    < /div>
  ) :
    (
        <>
            <p style = {styles.subtitle} > 웨이팅
    등록 < /p>
    < form
    onSubmit = {handleSubmit}
    style = {styles.form} >
    <label style = {styles.label} >
        전화번호
        < input
    style = {styles.input}
    type = "tel"
    value = {phoneNumber}
    onChange = {(e)
  =>
    setPhoneNumber(e.target.value)
  }
    maxLength = {13}
    placeholder = "010-XXXX-XXXX"
    required
    / >
    </label>

    < label
    style = {styles.label} >
        인원수
        < div
    style = {styles.stepper} >
    <button
        type = "button"
    style = {styles.stepBtn}
    onClick = {()
  =>
    setPartySize((n) => Math.max(1, n - 1))
  }
  >
  −
                    </button>
                    < span
    style = {styles.stepValue} > {partySize}
    명 < /span>
    < button
    type = "button"
    style = {styles.stepBtn}
    onClick = {()
  =>
    setPartySize((n) => Math.min(10, n + 1))
  }
  >
    +
        </button>
    < /div>
    < /label>

    < label
    style = {styles.consentLabel} >
    <input
        type = "checkbox"
    checked = {agreed}
    onChange = {(e)
  =>
    setAgreed(e.target.checked)
  }
    style = {styles.checkbox}
    />
    < span
    style = {styles.consentText} >
        전화번호는
    웨이팅
    호출
    알림
    목적으로만
    사용됩니다.
    < /span>
    < /label>

    < Button
    type = "submit"
    disabled = {submitting || !agreed
  }>
    {
      submitting ? '등록 중...' : '웨이팅 등록'
    }
    </Button>
    < /form>
    < />
  )
  }
  </div>
)
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '2rem 1.5rem',
  },
  storeName: {
    fontSize: '1.5rem',
    fontWeight: 700,
    marginBottom: '0.75rem',
  },
  statusBox: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    padding: '0.75rem 1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#eff6ff',
    border: '1px solid #bfdbfe',
    marginBottom: '1.5rem',
    fontSize: '0.9375rem',
    fontWeight: 600,
    color: '#1d4ed8',
  },
  statusDot: {
    color: '#93c5fd',
  },
  statusTeam: {},
  statusTime: {},
  unavailableBox: {
    marginTop: '2rem',
    padding: '2rem',
    borderRadius: '1rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
    textAlign: 'center',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  unavailableMessage: {
    fontSize: '1.125rem',
    fontWeight: 600,
    color: '#374151',
  },
  unavailableHint: {
    fontSize: '0.875rem',
    color: '#9ca3af',
  },
  subtitle: {
    color: '#6b7280',
    marginBottom: '1.5rem',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  label: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    fontWeight: 600,
    fontSize: '0.875rem',
  },
  input: {
    padding: '0.75rem',
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    fontSize: '1rem',
    outline: 'none',
  },
  stepper: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
  },
  stepBtn: {
    width: 44,
    height: 44,
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    background: '#f9fafb',
    fontSize: '1.25rem',
    cursor: 'pointer',
  },
  stepValue: {
    fontSize: '1.125rem',
    fontWeight: 600,
    minWidth: '3rem',
    textAlign: 'center',
  },
  consentLabel: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '0.5rem',
    cursor: 'pointer',
  },
  checkbox: {
    marginTop: '0.125rem',
    width: 16,
    height: 16,
    flexShrink: 0,
    cursor: 'pointer',
  },
  consentText: {
    fontSize: '0.8125rem',
    color: '#6b7280',
    lineHeight: 1.5,
  },
}

export default LandingPage
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/pages/LandingPage.tsx
git commit -m "feat: 랜딩 페이지 — 전화번호 입력·대기 현황 표시·개인정보 동의 체크박스 추가"
```

---

## Task 6: DashboardPage — phoneNumber 표시 전환

**Files:**

- Modify: `frontend/src/api/owner.ts`
- Modify: `frontend/src/pages/DashboardPage.tsx`

- [ ] **Step 1: api/owner.ts — OwnerWaitingItem 타입 수정**

```typescript
export interface OwnerWaitingItem {
  waitingId: string
  waitingNumber: number
  phoneNumber: string
  partySize: number
  status: 'WAITING' | 'CALLED'
  elapsedMinutes: number
}
```

- [ ] **Step 2: DashboardPage.tsx — visitorName → phoneNumber 표시 (3곳)**

`item.visitorName` → `item.phoneNumber` 로 전체 교체 (3곳: 표시 span, 호출 확인 메시지, 입장 확인 메시지):

```tsx
<span style={styles.waitingName}>{item.phoneNumber}</span>
```

```tsx
onClick = {()
=>
handleAction(
    `#${item.waitingNumber} ${item.phoneNumber} 손님을 호출할까요?`,
    item.waitingId,
    callWaiting,
)
}
```

```tsx
onClick = {()
=>
handleAction(
    `#${item.waitingNumber} ${item.phoneNumber} 손님 입장 처리할까요?`,
    item.waitingId,
    enterWaiting,
)
}
```

```tsx
onClick = {()
=>
handleAction(
    `#${item.waitingNumber} ${item.phoneNumber} 손님을 노쇼 처리할까요?`,
    item.waitingId,
    noShowWaiting,
)
}
```

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/api/owner.ts frontend/src/pages/DashboardPage.tsx
git commit -m "refactor: 대시보드 방문자 표시 visitorName → phoneNumber 전환"
```

---

## Task 7: WaitingCalledEvent 보강

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/event/WaitingCalledEvent.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java`

**배경:** `SmsNotificationListener`가 SMS 발송에 필요한 데이터를 추가 DB 조회 없이 얻을 수 있도록 이벤트를 enriched 버전으로 교체.

- [ ] **Step 1: WaitingManagementServiceTest — enriched 이벤트 검증 테스트로 업데이트**

`call_정상_호출처리` 테스트를 아래로 교체:

```java

@Test
void call_정상_호출처리() {
  WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "010-1234-5678", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());
  Store store = Store.restore(storeId, ownerId, "홍콩반점", "서울", StoreStatus.OPEN, LocalDateTime.now());

  given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
  given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
  given(waitingRepository.save(any())).willReturn(entry);

  service.call(ownerId, waitingId);

  verify(waitingRepository).save(any());

  ArgumentCaptor<WaitingCalledEvent> captor = ArgumentCaptor.forClass(WaitingCalledEvent.class);
  then(eventPublisher).should().publishEvent(captor.capture());
  WaitingCalledEvent event = captor.getValue();
  assertThat(event.phoneNumber()).isEqualTo("010-1234-5678");
  assertThat(event.waitingNumber()).isEqualTo(1);
  assertThat(event.storeName()).isEqualTo("홍콩반점");
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.WaitingManagementServiceTest.call_정상_호출처리" 2>&1 | tail -15
```

Expected: FAIL — `WaitingCalledEvent`에 `phoneNumber()` 없음

- [ ] **Step 3: WaitingCalledEvent.java — enriched 버전으로 교체**

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

- [ ] **Step 4: WaitingManagementService.java — call() 메서드 enriched 이벤트 발행으로 수정**

`call()` 메서드를 아래로 교체 (`resolveStoreId`를 직접 Store 조회로 변경):

```java

@Transactional
public void call(UUID ownerId, UUID waitingId) {
  WaitingEntry entry = waitingRepository.findById(waitingId)
      .orElseThrow(() -> new WaitingNotFoundException(waitingId));

  Store ownerStore = storeRepository.findByOwnerId(ownerId)
      .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));

  if (!ownerStore.getId().equals(entry.getStoreId())) {
    throw new StoreNotFoundException("ownerId=" + ownerId);
  }

  WaitingEntry called = entry.call();
  waitingRepository.save(called);

  eventPublisher.publishEvent(new WaitingCalledEvent(
      called.getStoreId(),
      waitingId,
      called.getPhoneNumber(),
      called.getWaitingNumber(),
      ownerStore.getName()
  ));
}
```

Import 추가: `import com.qrwait.api.store.domain.Store;`

- [ ] **Step 5: SseEventListener.java 확인 — 기존 event.storeId(), event.waitingId() 참조 그대로 동작함**

`SseEventListener`의 `onWaitingCalled` 메서드:

```java

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onWaitingCalled(WaitingCalledEvent event) {
  ssePublisher.broadcastCalled(event.storeId(), event.waitingId());
}
```

→ `storeId()`, `waitingId()` 모두 enriched 레코드에도 있으므로 변경 불필요.

- [ ] **Step 6: 전체 테스트 실행**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/domain/event/WaitingCalledEvent.java
git add backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java
git add backend/src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java
git commit -m "feat: WaitingCalledEvent에 phoneNumber·waitingNumber·storeName 필드 추가"
```

---

## Task 8: SMS 클라이언트 + 설정

**Files:**

- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsClient.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SolapiSmsClient.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsProperties.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: SmsClient.java — 인터페이스**

```java
package com.qrwait.api.shared.sms;

public interface SmsClient {

  /**
   * SMS 발송.
   *
   * @param to   수신 전화번호 (010-XXXX-XXXX)
   * @param text 메시지 본문
   */
  void send(String to, String text);
}
```

- [ ] **Step 2: SmsProperties.java — 설정 바인딩**

```java
package com.qrwait.api.shared.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
    String apiKey,
    String apiSecret,
    String sender
) {

}
```

- [ ] **Step 3: SolapiSmsClient.java — Solapi REST 구현체**

```java
package com.qrwait.api.shared.sms;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SmsProperties.class)
public class SolapiSmsClient implements SmsClient {

  private static final String SOLAPI_URL = "https://api.solapi.com/messages/v4/send";
  private static final DateTimeFormatter ISO_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private final SmsProperties props;
  private final RestClient restClient = RestClient.create();

  @Override
  public void send(String to, String text) {
    if (props.apiKey() == null || props.apiKey().isBlank()) {
      log.warn("[SMS] API 키가 설정되지 않아 SMS 발송을 건너뜁니다. to={}", to);
      return;
    }

    String date = ISO_FMT.format(Instant.now());
    String salt = UUID.randomUUID().toString();
    String signature = sign(props.apiSecret(), date + salt);
    String authorization = String.format(
        "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s",
        props.apiKey(), date, salt, signature
    );

    String toDigits = to.replace("-", "");

    try {
      restClient.post()
          .uri(SOLAPI_URL)
          .header("Authorization", authorization)
          .header("Content-Type", "application/json")
          .body(Map.of("message", Map.of(
              "to", toDigits,
              "from", props.sender().replace("-", ""),
              "text", text
          )))
          .retrieve()
          .toBodilessEntity();

      log.info("[SMS] 발송 성공. to={}", to);
    } catch (Exception e) {
      log.error("[SMS] 발송 실패. to={}, error={}", to, e.getMessage());
    }
  }

  private String sign(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("HMAC 서명 생성 실패", e);
    }
  }
}
```

- [ ] **Step 4: application.yml — SMS 설정 추가**

`jwt:` 블록 아래에 추가:

```yaml
sms:
  api-key: ${SMS_API_KEY:}
  api-secret: ${SMS_API_SECRET:}
  sender: ${SMS_SENDER_NUMBER:}
```

- [ ] **Step 5: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava 2>&1 | grep "error:"
```

Expected: 출력 없음 (컴파일 성공)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/sms/
git add backend/src/main/resources/application.yml
git commit -m "feat: SMS 클라이언트 인터페이스 + Solapi 구현체 + 설정 추가"
```

---

## Task 9: SmsNotificationListener

**Files:**

- Create: `backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java`
- Create: `backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java`

- [ ] **Step 1: SmsNotificationListenerTest.java 작성**

```java
package com.qrwait.api.shared.sms;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.qrwait.api.waiting.domain.event.WaitingCalledEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmsNotificationListenerTest {

  @Mock
  SmsClient smsClient;

  @InjectMocks
  SmsNotificationListener listener;

  @Test
  void onWaitingCalled_SMS_발송() {
    WaitingCalledEvent event = new WaitingCalledEvent(
        UUID.randomUUID(), UUID.randomUUID(),
        "010-1234-5678", 3, "홍콩반점"
    );

    listener.onWaitingCalled(event);

    then(smsClient).should().send(
        eq("010-1234-5678"),
        contains("3번 손님")
    );
    then(smsClient).should().send(
        eq("010-1234-5678"),
        contains("홍콩반점")
    );
  }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.SmsNotificationListenerTest" 2>&1 | tail -15
```

Expected: FAIL — `SmsNotificationListener` 클래스 없음

- [ ] **Step 3: SmsNotificationListener.java 구현**

```java
package com.qrwait.api.shared.sms;

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

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingCalled(WaitingCalledEvent event) {
    String message = String.format(
        "[QR Wait] %d번 손님, %s에서 입장 안내드립니다. 매장으로 와주세요.",
        event.waitingNumber(),
        event.storeName()
    );
    smsClient.send(event.phoneNumber(), message);
  }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test --tests "*.SmsNotificationListenerTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/shared/sms/SmsNotificationListener.java
git add backend/src/test/java/com/qrwait/api/shared/sms/SmsNotificationListenerTest.java
git commit -m "feat: SmsNotificationListener — 호출 시 SMS 자동 발송"
```

---

## Task 10: DB 마이그레이션 — called_at + WaitingEntry + JPA 엔티티

**Files:**

- Create: `backend/src/main/resources/db/migration/V3__add_called_at_to_waiting_entries.sql`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaEntity.java`

- [ ] **Step 1: V3 마이그레이션 파일 작성**

```sql
ALTER TABLE waiting_entries
    ADD COLUMN called_at TIMESTAMP;
```

- [ ] **Step 2: WaitingEntry.java — calledAt 필드 추가**

```java
package com.qrwait.api.waiting.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class WaitingEntry {

  private final UUID id;
  private final UUID storeId;
  private final String phoneNumber;
  private final int partySize;
  private final int waitingNumber;
  private final LocalDateTime createdAt;
  private final LocalDateTime calledAt;
  private final WaitingStatus status;

  private WaitingEntry(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, WaitingStatus status, LocalDateTime createdAt, LocalDateTime calledAt) {
    this.id = id;
    this.storeId = storeId;
    this.phoneNumber = phoneNumber;
    this.partySize = partySize;
    this.waitingNumber = waitingNumber;
    this.status = status;
    this.createdAt = createdAt;
    this.calledAt = calledAt;
  }

  public static WaitingEntry create(UUID storeId, String phoneNumber, int partySize, int waitingNumber) {
    return new WaitingEntry(
        UUID.randomUUID(), storeId, phoneNumber, partySize,
        waitingNumber, WaitingStatus.WAITING, LocalDateTime.now(), null
    );
  }

  public static WaitingEntry restore(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, WaitingStatus status, LocalDateTime createdAt, LocalDateTime calledAt) {
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, status, createdAt, calledAt);
  }

  public WaitingEntry call() {
    if (status != WaitingStatus.WAITING) {
      throw new IllegalStateException(
          "call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber,
        WaitingStatus.CALLED, createdAt, LocalDateTime.now());
  }

  public WaitingEntry enter() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "enter() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber,
        WaitingStatus.ENTERED, createdAt, calledAt);
  }

  public WaitingEntry cancel() {
    if (status != WaitingStatus.WAITING && status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "cancel() 은 WAITING 또는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber,
        WaitingStatus.CANCELLED, createdAt, calledAt);
  }

  public WaitingEntry noShow() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "noShow() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber,
        WaitingStatus.NO_SHOW, createdAt, calledAt);
  }
}
```

- [ ] **Step 3: WaitingEntryJpaEntity.java — called_at 필드 추가 + toDomain/from 업데이트**

필드 추가 (`createdAt` 아래):

```java

@Column(name = "called_at")
private LocalDateTime calledAt;
```

생성자 업데이트:

```java
private WaitingEntryJpaEntity(UUID id, UUID storeId, String phoneNumber, int partySize,
    int waitingNumber, String status, LocalDateTime createdAt, LocalDateTime calledAt) {
  this.id = id;
  this.storeId = storeId;
  this.phoneNumber = phoneNumber;
  this.partySize = partySize;
  this.waitingNumber = waitingNumber;
  this.status = status;
  this.createdAt = createdAt;
  this.calledAt = calledAt;
}
```

`from()` 업데이트:

```java
public static WaitingEntryJpaEntity from(WaitingEntry entry) {
  return new WaitingEntryJpaEntity(
      entry.getId(), entry.getStoreId(), entry.getPhoneNumber(),
      entry.getPartySize(), entry.getWaitingNumber(),
      entry.getStatus().name(), entry.getCreatedAt(), entry.getCalledAt()
  );
}
```

`toDomain()` 업데이트:

```java
public WaitingEntry toDomain() {
  return WaitingEntry.restore(
      id, storeId, phoneNumber, partySize, waitingNumber,
      WaitingStatus.valueOf(status), createdAt, calledAt
  );
}
```

- [ ] **Step 4: restore() 호출 사이트 업데이트 — 테스트 파일**

`WaitingManagementServiceTest.java`, `WaitingServiceTest.java` 등에서 `WaitingEntry.restore(...)` 7-인자 호출에 `null`(calledAt) 추가:

```java
// 기존 (7 args)
WaitingEntry.restore(waitingId, storeId, "010-1234-5678",2,1,WaitingStatus.WAITING, LocalDateTime.now())
// 변경 후 (8 args)
    WaitingEntry.

restore(waitingId, storeId, "010-1234-5678",2,1,WaitingStatus.WAITING, LocalDateTime.now(), null)
```

`WaitingStatus.CALLED` 인 항목은 `LocalDateTime.now().minusMinutes(5)` 등을 전달:

```java
WaitingEntry.restore(waitingId, storeId, "010-1111-0002",3,2,
    WaitingStatus.CALLED, LocalDateTime.now().

minusMinutes(5),LocalDateTime.

now().

minusMinutes(3))
```

- [ ] **Step 5: 전체 테스트 실행**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/resources/db/migration/V3__add_called_at_to_waiting_entries.sql
git add backend/src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java
git add backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaEntity.java
git add backend/src/test/java/com/qrwait/api/waiting/
git commit -m "feat: WaitingEntry에 calledAt 타임스탬프 추가 (V3 마이그레이션 포함)"
```

---

## Task 11: CALLED 자동 만료 Scheduler

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImpl.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java`
- Create: `backend/src/main/java/com/qrwait/api/shared/scheduler/SchedulerConfig.java`
- Create: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingAutoNoShowScheduler.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: WaitingRepository.java — findCalledBefore 추가**

```java
List<WaitingEntry> findCalledBefore(LocalDateTime threshold);
```

import 추가: `import java.time.LocalDateTime;`

- [ ] **Step 2: WaitingEntryJpaRepository.java — 쿼리 추가**

```java

@Query("SELECT w FROM WaitingEntryJpaEntity w WHERE w.status = 'CALLED' AND w.calledAt < :threshold")
List<WaitingEntryJpaEntity> findCalledBefore(@Param("threshold") LocalDateTime threshold);
```

import 추가: `import java.time.LocalDateTime;`

- [ ] **Step 3: WaitingRepositoryImpl.java — 구현**

```java

@Override
public List<WaitingEntry> findCalledBefore(LocalDateTime threshold) {
  return waitingEntryJpaRepository.findCalledBefore(threshold).stream()
      .map(WaitingEntryJpaEntity::toDomain)
      .toList();
}
```

- [ ] **Step 4: SchedulerConfig.java 생성**

```java
package com.qrwait.api.shared.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {

}
```

- [ ] **Step 5: application.yml — 자동 노쇼 타임아웃 설정 추가**

`sms:` 블록 아래에 추가:

```yaml
waiting:
  auto-noshow-minutes: 10
```

- [ ] **Step 6: WaitingAutoNoShowScheduler.java 작성**

```java
package com.qrwait.api.waiting.application;

import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.event.WaitingUpdatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingAutoNoShowScheduler {

  private final WaitingRepository waitingRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${waiting.auto-noshow-minutes:10}")
  private int autoNoshowMinutes;

  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void autoNoShow() {
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(autoNoshowMinutes);
    List<WaitingEntry> expired = waitingRepository.findCalledBefore(threshold);

    if (expired.isEmpty())
      return;

    log.info("[AutoNoShow] {}건 자동 노쇼 처리", expired.size());
    for (WaitingEntry entry : expired) {
      WaitingEntry noShowed = entry.noShow();
      waitingRepository.save(noShowed);
      eventPublisher.publishEvent(new WaitingUpdatedEvent(noShowed.getStoreId()));
    }
  }
}
```

- [ ] **Step 7: 전체 테스트 실행**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java
git add backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImpl.java
git add backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java
git add backend/src/main/java/com/qrwait/api/shared/scheduler/SchedulerConfig.java
git add backend/src/main/java/com/qrwait/api/waiting/application/WaitingAutoNoShowScheduler.java
git add backend/src/main/resources/application.yml
git commit -m "feat: CALLED 상태 자동 만료 스케줄러 추가 (10분 타임아웃)"
```

---

## Task 12: 웨이팅 번호 일별 초기화

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`

- [ ] **Step 1: WaitingRepositoryImplTest — 날짜별 번호 초기화 테스트 추가**

```java

@Test
void findNextWaitingNumber_오늘등록기준_최대값플러스1() {
  waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1));
  waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2));

  int next = waitingRepository.findNextWaitingNumber(savedStore.getId());

  assertThat(next).isEqualTo(3);
}
```

- [ ] **Step 2: 테스트 실행 — PASS 확인 (기존 동작과 동일하므로 통과해야 함)**

```bash
cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest.findNextWaitingNumber_오늘등록기준_최대값플러스1" 2>&1 | tail -10
```

Expected: PASS (쿼리 변경 전이지만 오늘 날짜와 동일하므로)

- [ ] **Step 3: WaitingEntryJpaRepository.java — 날짜 필터 쿼리로 변경**

기존 쿼리:

```java

@Query("SELECT COALESCE(MAX(w.waitingNumber), 0) FROM WaitingEntryJpaEntity w WHERE w.storeId = :storeId")
int findMaxWaitingNumberByStoreId(@Param("storeId") UUID storeId);
```

변경 후 (native query로 DATE 함수 사용):

```java

@Query(value = "SELECT COALESCE(MAX(waiting_number), 0) FROM waiting_entries WHERE store_id = :storeId AND DATE(created_at) = CURRENT_DATE", nativeQuery = true)
int findMaxWaitingNumberByStoreId(@Param("storeId") UUID storeId);
```

- [ ] **Step 4: 전체 테스트 실행**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java
git add backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java
git commit -m "feat: 웨이팅 번호 일별 초기화 — 날짜 기준 최대값 쿼리 적용"
```

---

## Task 13: 날짜 범위 통계 백엔드 API

**Files:**

- Create: `backend/src/main/java/com/qrwait/api/waiting/domain/DailyStatRow.java`
- Create: `backend/src/main/java/com/qrwait/api/waiting/application/dto/WaitingHistoryResponse.java`
- Create: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/DailyStatProjection.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImpl.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/presentation/OwnerWaitingController.java`

- [ ] **Step 1: DailyStatRow.java — 도메인 레코드**

```java
package com.qrwait.api.waiting.domain;

import java.time.LocalDate;

public record DailyStatRow(
    LocalDate date,
    long registered,
    long entered,
    long noShow,
    long cancelled
) {

}
```

- [ ] **Step 2: DailyStatProjection.java — JPA 네이티브 쿼리 프로젝션**

```java
package com.qrwait.api.waiting.infrastructure;

import java.time.LocalDate;

public interface DailyStatProjection {

  LocalDate getDate();

  long getRegistered();

  long getEntered();

  long getNoShow();

  long getCancelled();
}
```

- [ ] **Step 3: WaitingHistoryResponse.java — 응답 DTO**

```java
package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.DailyStatRow;
import java.time.LocalDate;
import java.util.List;

public record WaitingHistoryResponse(
    LocalDate from,
    LocalDate to,
    Summary summary,
    List<DailyRow> daily
) {

  public record Summary(
      long totalRegistered,
      long totalEntered,
      long totalNoShow,
      long totalCancelled
  ) {

  }

  public record DailyRow(
      LocalDate date,
      long registered,
      long entered,
      long noShow,
      long cancelled
  ) {

  }

  public static WaitingHistoryResponse from(LocalDate from, LocalDate to, List<DailyStatRow> rows) {
    long totalRegistered = rows.stream().mapToLong(DailyStatRow::registered).sum();
    long totalEntered = rows.stream().mapToLong(DailyStatRow::entered).sum();
    long totalNoShow = rows.stream().mapToLong(DailyStatRow::noShow).sum();
    long totalCancelled = rows.stream().mapToLong(DailyStatRow::cancelled).sum();

    List<DailyRow> daily = rows.stream()
        .map(r -> new DailyRow(r.date(), r.registered(), r.entered(), r.noShow(), r.cancelled()))
        .toList();

    return new WaitingHistoryResponse(from, to, new Summary(totalRegistered, totalEntered, totalNoShow, totalCancelled), daily);
  }
}
```

- [ ] **Step 4: WaitingRepository.java — findDailyHistory 추가**

```java
List<DailyStatRow> findDailyHistory(UUID storeId, LocalDate from, LocalDate to);
```

import 추가: `import java.time.LocalDate;`, `import com.qrwait.api.waiting.domain.DailyStatRow;`

- [ ] **Step 5: WaitingEntryJpaRepository.java — 날짜 범위 집계 native query 추가**

```java

@Query(value = """
    SELECT DATE(created_at)                                            AS date,
           COUNT(*)                                                    AS registered,
           SUM(CASE WHEN status = 'ENTERED'   THEN 1 ELSE 0 END)      AS entered,
           SUM(CASE WHEN status = 'NO_SHOW'   THEN 1 ELSE 0 END)      AS no_show,
           SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END)      AS cancelled
    FROM waiting_entries
    WHERE store_id = :storeId
      AND DATE(created_at) BETWEEN :from AND :to
    GROUP BY DATE(created_at)
    ORDER BY DATE(created_at)
    """, nativeQuery = true)
List<DailyStatProjection> findDailyHistoryByStoreId(
    @Param("storeId") UUID storeId,
    @Param("from") LocalDate from,
    @Param("to") LocalDate to
);
```

import 추가: `import java.time.LocalDate;`, `import java.util.List;`

- [ ] **Step 6: WaitingRepositoryImpl.java — findDailyHistory 구현**

```java

@Override
public List<DailyStatRow> findDailyHistory(UUID storeId, LocalDate from, LocalDate to) {
  return waitingEntryJpaRepository.findDailyHistoryByStoreId(storeId, from, to).stream()
      .map(p -> new DailyStatRow(p.getDate(), p.getRegistered(), p.getEntered(), p.getNoShow(), p.getCancelled()))
      .toList();
}
```

import 추가: `import com.qrwait.api.waiting.domain.DailyStatRow;`

- [ ] **Step 7: WaitingManagementService.java — getWaitingHistory 메서드 추가**

```java

@Transactional(readOnly = true)
public WaitingHistoryResponse getWaitingHistory(UUID ownerId, LocalDate from, LocalDate to) {
  UUID storeId = resolveStoreId(ownerId);
  List<DailyStatRow> rows = waitingRepository.findDailyHistory(storeId, from, to);
  return WaitingHistoryResponse.from(from, to, rows);
}
```

import 추가:

```java
import com.qrwait.api.waiting.application.dto.WaitingHistoryResponse;
import com.qrwait.api.waiting.domain.DailyStatRow;
import java.time.LocalDate;
import java.util.List;
```

- [ ] **Step 8: OwnerWaitingController.java — GET /waitings/history 엔드포인트 추가**

```java

@Operation(summary = "날짜 범위 웨이팅 통계")
@GetMapping("/stores/me/waitings/history")
public ResponseEntity<WaitingHistoryResponse> getWaitingHistory(
    @AuthenticationPrincipal UUID ownerId,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
  return ResponseEntity.ok(waitingManagementService.getWaitingHistory(ownerId, from, to));
}
```

import 추가:

```java
import com.qrwait.api.waiting.application.dto.WaitingHistoryResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
```

- [ ] **Step 9: 전체 테스트 실행**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/
git commit -m "feat: 날짜 범위 웨이팅 통계 API 추가 (GET /owner/stores/me/waitings/history)"
```

---

## Task 14: 통계 페이지 (StatsPage)

**Files:**

- Modify: `frontend/src/api/owner.ts`
- Create: `frontend/src/pages/StatsPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/DashboardPage.tsx`

- [ ] **Step 1: api/owner.ts — 통계 타입 + API 함수 추가**

파일 끝에 추가:

```typescript
export interface DailyRow {
  date: string
  registered: number
  entered: number
  noShow: number
  cancelled: number
}

export interface WaitingHistorySummary {
  totalRegistered: number
  totalEntered: number
  totalNoShow: number
  totalCancelled: number
}

export interface WaitingHistoryResponse {
  from: string
  to: string
  summary: WaitingHistorySummary
  daily: DailyRow[]
}

export const getWaitingHistory = (from: string, to: string): Promise<WaitingHistoryResponse> =>
    ownerClient.get('/owner/stores/me/waitings/history', {params: {from, to}}).then((res) => res.data)
```

- [ ] **Step 2: StatsPage.tsx 작성**

```typescript
import {useState} from 'react'
import {useNavigate} from 'react-router-dom'
import type {WaitingHistoryResponse} from '../api/owner'
import {getWaitingHistory} from '../api/owner'
import Button from '../components/Button'

type Preset = 'today' | 'yesterday' | 'week' | 'month' | 'custom'

function getDateRange(preset: Preset): { from: string; to: string } {
  const today = new Date()
  const fmt = (d: Date) => d.toISOString().slice(0, 10)

  if (preset === 'today') {
    const s = fmt(today)
    return {from: s, to: s}
  }
  if (preset === 'yesterday') {
    const d = new Date(today)
    d.setDate(d.getDate() - 1)
    const s = fmt(d)
    return {from: s, to: s}
  }
  if (preset === 'week') {
    const d = new Date(today)
    d.setDate(d.getDate() - 6)
    return {from: fmt(d), to: fmt(today)}
  }
  if (preset === 'month') {
    const d = new Date(today)
    d.setDate(d.getDate() - 29)
    return {from: fmt(d), to: fmt(today)}
  }
  return {from: fmt(today), to: fmt(today)}
}

function StatsPage() {
  const navigate = useNavigate()
  const [preset, setPreset] = useState<Preset>('today')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [data, setData] = useState<WaitingHistoryResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSearch = async () => {
    const {from, to} = preset === 'custom'
        ? {from: customFrom, to: customTo}
        : getDateRange(preset)

    if (!from || !to) {
      setError('날짜를 선택해주세요.')
      return
    }

    setLoading(true)
    setError(null)
    try {
      const result = await getWaitingHistory(from, to)
      setData(result)
    } catch {
      setError('통계를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const PRESETS: { key: Preset; label: string }[] = [
    {key: 'today', label: '오늘'},
    {key: 'yesterday', label: '어제'},
    {key: 'week', label: '이번 주'},
    {key: 'month', label: '이번 달'},
    {key: 'custom', label: '직접 입력'},
  ]

  return (
      <div style = {styles.container} >
      <div style = {styles.header} >
      <h1 style = {styles.title} > 통계 < /h1>
          < button
  style = {styles.backBtn}
  onClick = {()
=>
  navigate('/owner/dashboard')
}>← 대시보드 < /button>
  < /div>

  < div
  style = {styles.presetRow} >
      {
        PRESETS.map(({key, label}) => (
            <button
                key = {key}
        style = {
  {
  ...
    styles.presetBtn,
        backgroundColor
  :
    preset === key ? '#3b82f6' : '#f3f4f6',
        color
  :
    preset === key ? '#fff' : '#374151',
  }
}
  onClick = {()
=>
  setPreset(key)
}
>
  {
    label
  }
  </button>
))
}
  </div>

  {
    preset === 'custom' && (
        <div style = {styles.customRow} >
        <input type = "date"
    value = {customFrom}
    onChange = {(e)
  =>
    setCustomFrom(e.target.value)
  }
    style = {styles.dateInput}
    />
    < span > ~</span>
    < input
    type = "date"
    value = {customTo}
    onChange = {(e)
  =>
    setCustomTo(e.target.value)
  }
    style = {styles.dateInput}
    />
    < /div>
  )
  }

  <Button onClick = {handleSearch}
  disabled = {loading} >
      {loading ? '조회 중...' : '조회'}
      < /Button>

  {
    error && <p style = {styles.error} > {error} < /p>}

    {
      data && (
          <>
              <section>
                  <p style = {styles.sectionTitle} > 기간
      합계({data.from}
      ~{data.to}
    )
      </p>
      < div
      style = {styles.summaryGrid} >
          {
            [
                {label: '등록', value: data.summary.totalRegistered},
      {
        label: '입장', value
      :
        data.summary.totalEntered
      }
    ,
      {
        label: '노쇼', value
      :
        data.summary.totalNoShow
      }
    ,
      {
        label: '취소', value
      :
        data.summary.totalCancelled
      }
    ,
    ].
      map(({label, value}) => (
          <div key = {label}
      style = {styles.summaryCard} >
      <p style = {styles.summaryValue} > {value} < /p>
          < p
      style = {styles.summaryLabel} > {label} < /p>
          < /div>
    ))
    }
      </div>
      < /section>

      {
        data.daily.length > 0 && (
            <section>
                <p style = {styles.sectionTitle} > 일별
        상세 < /p>
        < div
        style = {styles.tableWrapper} >
        <table style = {styles.table} >
            <thead>
                <tr>
                    {['날짜', '등록', '입장', '노쇼', '취소'
      ].
        map((h) => (
            <th key = {h}
        style = {styles.th} > {h} < /th>
      ))
      }
        </tr>
        < /thead>
        < tbody >
        {
          data.daily.map((row) => (
              <tr key = {row.date} >
              <td style = {styles.td} > {row.date} < /td>
                  < td style = {styles.td} > {row.registered} < /td>
              < td style = {styles.td} > {row.entered} < /td>
              < td style = {styles.td} > {row.noShow} < /td>
              < td style = {styles.td} > {row.cancelled} < /td>
              < /tr>
      ))
      }
        </tbody>
        < /table>
        < /div>
        < /section>
      )
      }

      {
        data.daily.length === 0 && (
            <p style = {styles.emptyText} > 해당
        기간에
        데이터가
        없습니다. < /p>
      )
      }
      </>
    )
    }
    </div>
  )
  }

  const styles: Record<string, React.CSSProperties> = {
    container: {maxWidth: 600, margin: '0 auto', padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem'},
    header: {display: 'flex', justifyContent: 'space-between', alignItems: 'center'},
    title: {fontSize: '1.25rem', fontWeight: 700},
    backBtn: {background: 'none', border: 'none', color: '#3b82f6', cursor: 'pointer', fontSize: '0.875rem'},
    presetRow: {display: 'flex', gap: '0.5rem', flexWrap: 'wrap'},
    presetBtn: {padding: '0.5rem 0.875rem', borderRadius: '0.5rem', border: 'none', fontSize: '0.8125rem', fontWeight: 600, cursor: 'pointer'},
    customRow: {display: 'flex', alignItems: 'center', gap: '0.75rem'},
    dateInput: {padding: '0.5rem', borderRadius: '0.5rem', border: '1px solid #d1d5db', fontSize: '0.875rem'},
    error: {color: '#dc2626', fontSize: '0.875rem'},
    sectionTitle: {fontSize: '0.875rem', fontWeight: 600, color: '#374151', marginBottom: '0.75rem'},
    summaryGrid: {display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '0.5rem'},
    summaryCard: {padding: '0.75rem 0.5rem', borderRadius: '0.75rem', backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', textAlign: 'center'},
    summaryValue: {fontSize: '1.5rem', fontWeight: 700, color: '#111827'},
    summaryLabel: {fontSize: '0.75rem', color: '#6b7280', marginTop: '0.25rem'},
    tableWrapper: {overflowX: 'auto'},
    table: {width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem'},
    th: {
      padding: '0.625rem 0.75rem',
      backgroundColor: '#f8fafc',
      borderBottom: '1px solid #e2e8f0',
      textAlign: 'left',
      fontWeight: 600,
      color: '#374151'
    },
    td: {padding: '0.625rem 0.75rem', borderBottom: '1px solid #f1f5f9', color: '#111827'},
    emptyText: {color: '#9ca3af', fontSize: '0.875rem', textAlign: 'center', padding: '2rem 0'},
  }

  export default StatsPage
```

- [ ] **Step 3: App.tsx — /owner/stats 라우트 추가**

`StatsPage` import 추가:

```typescript
import StatsPage from './pages/StatsPage'
```

점주 라우트 블록에 추가:

```tsx
<Route path="/owner/stats" element={<PrivateRoute><StatsPage/></PrivateRoute>}/>
```

- [ ] **Step 4: DashboardPage.tsx — 통계 탭 링크 추가**

헤더 영역의 `<Button variant="secondary" onClick={handleLogout}>` 앞에 추가:

```tsx
<Button variant="secondary" onClick={() => navigate('/owner/stats')} style={styles.logoutBtn}>
  통계
</Button>
```

`navigate` 사용을 위해 이미 import되어 있으므로 추가 불필요.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/api/owner.ts
git add frontend/src/pages/StatsPage.tsx
git add frontend/src/App.tsx
git add frontend/src/pages/DashboardPage.tsx
git commit -m "feat: 날짜 범위 통계 페이지 추가 (/owner/stats)"
```

---

## 셀프 리뷰

### 스펙 커버리지 체크

| 스펙 요구사항                               | 구현 태스크                                           |
|---------------------------------------|--------------------------------------------------|
| 전화번호 전환 (visitor_name → phone_number) | Task 1, 2, 3                                     |
| 전화번호 형식 검증 (`010-XXXX-XXXX`)          | Task 3 (RegisterWaitingRequest @Pattern)         |
| 개인정보 동의 체크박스 (필수)                     | Task 5 (LandingPage agreed state + 버튼 disabled)  |
| 등록 전 대기 현황 표시                         | Task 4 (API 노출) + Task 5 (LandingPage statusBox) |
| 점주 대시보드 phoneNumber 표시                | Task 6                                           |
| WaitingCalledEvent 보강                 | Task 7                                           |
| SMS 발송 (WAITING → CALLED 시)           | Task 8 + Task 9                                  |
| CALLED 자동 만료 (10분 타임아웃)               | Task 10 + Task 11                                |
| calledAt 타임스탬프                        | Task 10                                          |
| 웨이팅 번호 일별 초기화                         | Task 12                                          |
| 날짜 범위 통계 API                          | Task 13                                          |
| 통계 페이지                                | Task 14                                          |
| 날짜 범위 선택 (오늘/어제/이번 주/이번 달/직접 입력)      | Task 14 (StatsPage PRESETS)                      |
| 기간 합계 카드                              | Task 14 (summaryGrid)                            |
| 일별 상세 테이블                             | Task 14 (table)                                  |
| 대시보드 "통계" 탭                           | Task 14 (DashboardPage 버튼)                       |

### 타입 일관성 체크

- `WaitingCalledEvent(storeId, waitingId, phoneNumber, waitingNumber, storeName)` — Task 7에서 정의, Task 9 리스너에서 사용 ✓
- `DailyStatRow(date, registered, entered, noShow, cancelled)` — Task 13 도메인에서 정의, WaitingRepositoryImpl → WaitingManagementService 순서로 사용 ✓
- `WaitingEntry.restore(8-args)` — Task 10에서 `calledAt` 추가, 모든 호출 사이트 업데이트 ✓
- `RegisterWaitingRequest.phoneNumber` — Task 3 백엔드, Task 4/5 프론트엔드 ✓

### 누락 체크

- `@EnableScheduling` — Task 11의 `SchedulerConfig`에서 처리 ✓
- `SmsProperties` `@EnableConfigurationProperties` — `SolapiSmsClient`에 `@EnableConfigurationProperties(SmsProperties.class)` 추가함 ✓
