# 배포 MVP 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 전화번호 전환, QR 인쇄 페이지, 오늘의 웨이팅 이력 페이지, 대시보드 통계 카드 클릭 기능을 구현하여 배포 가능한 상태를 만든다.

**Architecture:** 전화번호 전환(Task 1~6)을 먼저 완료한다. Task 7(이력 API)·Task 9(이력 페이지)는 전화번호 전환 완료 후 실행한다. Task 8(QR 인쇄)·Task 10(대시보드)은 독립적으로 실행 가능하다.

**Tech Stack:** Spring Boot 3.5 / Spring Data JPA / Flyway / React / TypeScript

---

## 파일 맵

| 파일                                                      | 역할                                                                   |
|---------------------------------------------------------|----------------------------------------------------------------------|
| `db/migration/V1__init.sql`                             | visitor_name → phone_number 컬럼 변경 (운영 배포 전이므로 V1 직접 수정)              |
| `waiting/domain/WaitingEntry.java`                      | visitorName → phoneNumber 필드                                         |
| `waiting/infrastructure/WaitingEntryJpaEntity.java`     | phone_number 컬럼 매핑 반영                                                |
| `waiting/application/dto/RegisterWaitingRequest.java`   | phoneNumber + @Pattern 검증                                            |
| `waiting/application/dto/OwnerWaitingResponse.java`     | phoneNumber 컴포넌트                                                     |
| `waiting/application/WaitingService.java`               | phoneNumber 파라미터 전달                                                  |
| `waiting/application/WaitingManagementService.java`     | toOwnerWaitingResponse phoneNumber 반영                                |
| `waiting/presentation/WaitingController.java`           | GET /stores/{id}/waitings/status 노출                                  |
| `waiting/domain/WaitingRepository.java`                 | `findAllByStoreIdAndDate()` 추가                                       |
| `waiting/infrastructure/WaitingEntryJpaRepository.java` | 날짜 범위 전체 조회 쿼리 추가                                                    |
| `waiting/infrastructure/WaitingRepositoryImpl.java`     | findAllByStoreIdAndDate 구현                                           |
| `waiting/application/dto/TodayWaitingResponse.java`     | 오늘 웨이팅 응답 DTO (신규)                                                   |
| `waiting/application/WaitingManagementService.java`     | `getTodayWaitings()` 추가                                              |
| `waiting/presentation/OwnerWaitingController.java`      | `GET /stores/me/waitings/today` 추가                                   |
| `test/.../WaitingManagementServiceTest.java`            | 신규 메서드 테스트 추가                                                        |
| `test/.../OwnerWaitingControllerTest.java`              | 신규 엔드포인트 테스트 추가                                                      |
| `frontend/src/api/waiting.ts`                           | RegisterWaitingRequest phoneNumber + getStoreWaitingStatus 추가        |
| `frontend/src/api/owner.ts`                             | OwnerWaitingItem phoneNumber + TodayWaiting 타입 + getTodayWaitings 추가 |
| `frontend/src/pages/LandingPage.tsx`                    | 전화번호 입력·대기 현황·동의 체크박스                                                |
| `frontend/src/pages/DashboardPage.tsx`                  | phoneNumber 표시 + QR 인쇄 버튼 + 통계 카드 클릭                                 |
| `frontend/src/pages/QrPrintPage.tsx`                    | QR 인쇄 페이지 (신규)                                                       |
| `frontend/src/pages/HistoryPage.tsx`                    | 오늘의 웨이팅 이력 페이지 (신규)                                                  |
| `frontend/src/App.tsx`                                  | `/owner/qr-print`, `/owner/history` 라우트 추가                           |

---

## Task 1: DB 마이그레이션 — visitor_name → phone_number

> **참고:** 운영 배포 전 / 사용자 없음 → V1을 직접 수정하고 DB를 초기화한다.

**Files:**

- Modify: `backend/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: V1__init.sql — visitor_name → phone_number 컬럼 수정**

`waiting_entries` 테이블의 `visitor_name` 컬럼을 아래로 교체:

```sql
-- Before
visitor_name   VARCHAR(50) NOT NULL,

-- After
phone_number   VARCHAR(20) NOT NULL,
```

- [ ] **Step 2: DB 초기화 후 마이그레이션 재적용 확인**

Flyway는 V1 체크섬이 변경되면 오류를 낸다. DB를 드롭하고 재생성해야 한다:

```bash
# PostgreSQL에서 DB 초기화 (psql 또는 pgAdmin 사용)
# DROP DATABASE qrwait; CREATE DATABASE qrwait;

cd backend
./gradlew bootRun
# 로그에서 "Successfully applied 1 migration to schema" 확인
```

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V1__init.sql
git commit -m "db: waiting_entries visitor_name → phone_number 컬럼 변경 (V1 직접 수정)"
```

---

## Task 2: WaitingEntry 도메인 — visitorName → phoneNumber

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`

- [ ] **Step 1: 컴파일 에러 먼저 확인 (변경 전 기준)**

```bash
cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest" 2>&1 | head -30
```

Expected: 아직 코드 변경 전이므로 PASS

- [ ] **Step 2: WaitingEntry.java — visitorName → phoneNumber 전환**

`WaitingEntry.java` 전체를 아래로 교체:

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

Expected: `visitorName`을 참조하는 파일들의 컴파일 에러 목록 출력 (Task 3에서 수정)

- [ ] **Step 4: WaitingRepositoryImplTest — 전화번호 문자열로 업데이트**

`WaitingRepositoryImplTest.java`에서 `"손님A"`, `"손님B"` 를 `"010-1111-1111"`, `"010-2222-2222"` 로 교체:

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

- [ ] **Step 4: WaitingService.java — getPhoneNumber() 사용**

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

## Task 4: 대기 현황 API 노출 + 프론트 타입 업데이트

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/presentation/WaitingController.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/presentation/WaitingControllerTest.java`
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

import 추가: `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;`

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.WaitingControllerTest.getStoreWaitingStatus_성공_200반환" 2>&1 | tail -15
```

Expected: FAIL — 404 Not Found (엔드포인트 없음)

- [ ] **Step 3: WaitingController.java에 엔드포인트 추가**

기존 `stream` 메서드 앞에 삽입:

```java

@Operation(summary = "매장 전체 대기 현황 조회")
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

- [ ] **Step 5: api/waiting.ts — RegisterWaitingRequest 타입 + getStoreWaitingStatus 추가**

`api/waiting.ts`에서 `visitorName: string` → `phoneNumber: string` 으로 교체하고 `StoreWaitingStatus` + `getStoreWaitingStatus` 추가:

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

- [ ] **Step 2: DashboardPage.tsx — visitorName → phoneNumber 표시 (4곳)**

`item.visitorName` → `item.phoneNumber` 로 전체 교체 (표시 span, 호출 확인 메시지, 입장 확인 메시지, 노쇼 확인 메시지):

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

## Task 7: 오늘의 웨이팅 이력 백엔드 API

**Files:**

- Modify: `backend/src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImpl.java`
- Create: `backend/src/main/java/com/qrwait/api/waiting/application/dto/TodayWaitingResponse.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/presentation/OwnerWaitingController.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/presentation/OwnerWaitingControllerTest.java`

- [ ] **Step 1: WaitingManagementServiceTest — getTodayWaitings 테스트 추가**

`WaitingManagementServiceTest.java` 파일 끝(마지막 `}` 앞)에 추가:

```java
// ===== getTodayWaitings =====

@Test
void getTodayWaitings_오늘_전체_목록_반환() {
  WaitingEntry waiting = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
      WaitingStatus.WAITING, LocalDateTime.now(), null);
  WaitingEntry entered = WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 1, 2,
      WaitingStatus.ENTERED, LocalDateTime.now().minusMinutes(30), null);

  given(storeRepository.findByOwnerId(ownerId))
      .willReturn(Optional.of(Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now())));
  given(waitingRepository.findAllByStoreIdAndDate(eq(storeId), any(LocalDate.class)))
      .willReturn(List.of(waiting, entered));

  List<TodayWaitingResponse> result = service.getTodayWaitings(ownerId);

  assertThat(result).hasSize(2);
  assertThat(result.get(0).phoneNumber()).isEqualTo("010-1111-0001");
  assertThat(result.get(0).status()).isEqualTo(WaitingStatus.WAITING);
  assertThat(result.get(1).phoneNumber()).isEqualTo("010-1111-0002");
  assertThat(result.get(1).status()).isEqualTo(WaitingStatus.ENTERED);
}

@Test
void getTodayWaitings_매장_없음_예외발생() {
  given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

  assertThatThrownBy(() -> service.getTodayWaitings(ownerId))
      .isInstanceOf(StoreNotFoundException.class);
}
```

import 추가:

```java
import com.qrwait.api.waiting.application.dto.TodayWaitingResponse;
import java.time.LocalDate;
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.WaitingManagementServiceTest.getTodayWaitings_*" 2>&1 | tail -15
```

Expected: FAIL — `getTodayWaitings`, `TodayWaitingResponse`, `findAllByStoreIdAndDate` 없음

- [ ] **Step 3: TodayWaitingResponse.java 생성**

```java
package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TodayWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    LocalDateTime createdAt
) {

}
```

- [ ] **Step 4: WaitingRepository.java — findAllByStoreIdAndDate 추가**

```java
List<WaitingEntry> findAllByStoreIdAndDate(UUID storeId, LocalDate date);
```

import 추가: `import java.time.LocalDate;`

- [ ] **Step 5: WaitingEntryJpaRepository.java — 날짜 범위 쿼리 추가**

```java

@Query("SELECT w FROM WaitingEntryJpaEntity w WHERE w.storeId = :storeId AND w.createdAt >= :startOfDay AND w.createdAt < :endOfDay ORDER BY w.waitingNumber DESC")
List<WaitingEntryJpaEntity> findAllByStoreIdBetween(
    @Param("storeId") UUID storeId,
    @Param("startOfDay") LocalDateTime startOfDay,
    @Param("endOfDay") LocalDateTime endOfDay
);
```

- [ ] **Step 6: WaitingRepositoryImpl.java — findAllByStoreIdAndDate 구현**

```java

@Override
public List<WaitingEntry> findAllByStoreIdAndDate(UUID storeId, LocalDate date) {
  LocalDateTime startOfDay = date.atStartOfDay();
  LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
  return waitingEntryJpaRepository.findAllByStoreIdBetween(storeId, startOfDay, endOfDay)
      .stream()
      .map(WaitingEntryJpaEntity::toDomain)
      .toList();
}
```

import 추가: `import java.time.LocalDate;`

- [ ] **Step 7: WaitingManagementService.java — getTodayWaitings 추가**

```java

@Transactional(readOnly = true)
public List<TodayWaitingResponse> getTodayWaitings(UUID ownerId) {
  UUID storeId = resolveStoreId(ownerId);
  return waitingRepository.findAllByStoreIdAndDate(storeId, LocalDate.now())
      .stream()
      .map(entry -> new TodayWaitingResponse(
          entry.getId(),
          entry.getWaitingNumber(),
          entry.getPhoneNumber(),
          entry.getPartySize(),
          entry.getStatus(),
          entry.getCreatedAt()
      ))
      .toList();
}
```

import 추가:

```java
import com.qrwait.api.waiting.application.dto.TodayWaitingResponse;
import java.time.LocalDate;
import java.util.List;
```

- [ ] **Step 8: 서비스 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test --tests "*.WaitingManagementServiceTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: OwnerWaitingControllerTest — 새 엔드포인트 테스트 추가**

`OwnerWaitingControllerTest.java` 파일 끝(마지막 `}` 앞)에 추가:

```java
// ===== getTodayWaitings =====

@Test
void getTodayWaitings_인증없음_401반환() throws Exception {
  mockMvc.perform(get("/api/owner/stores/me/waitings/today"))
      .andExpect(status().isUnauthorized());
}

@Test
void getTodayWaitings_인증된_점주_200반환() throws Exception {
  UUID ownerId = UUID.randomUUID();
  UUID waitingId = UUID.randomUUID();

  given(jwtTokenProvider.validateToken(any())).willReturn(true);
  given(jwtTokenProvider.extractOwnerId(any())).willReturn(ownerId);
  given(waitingManagementService.getTodayWaitings(eq(ownerId)))
      .willReturn(List.of(new TodayWaitingResponse(
          waitingId, 1, "010-1234-5678", 2, WaitingStatus.ENTERED, java.time.LocalDateTime.now()
      )));

  mockMvc.perform(get("/api/owner/stores/me/waitings/today")
          .header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].phoneNumber").value("010-1234-5678"))
      .andExpect(jsonPath("$[0].waitingNumber").value(1))
      .andExpect(jsonPath("$[0].status").value("ENTERED"));
}
```

import 추가:

```java
import com.qrwait.api.waiting.application.dto.TodayWaitingResponse;
import java.util.List;
```

- [ ] **Step 10: 테스트 실행 — FAIL 확인**

```bash
cd backend && ./gradlew test --tests "*.OwnerWaitingControllerTest.getTodayWaitings_*" 2>&1 | tail -15
```

Expected: FAIL — `GET /stores/me/waitings/today` 엔드포인트 없음

- [ ] **Step 11: OwnerWaitingController.java — 엔드포인트 추가**

기존 `getDailySummary` 메서드 아래에 추가:

```java

@Operation(summary = "오늘의 웨이팅 이력 전체 조회")
@GetMapping("/stores/me/waitings/today")
public ResponseEntity<List<TodayWaitingResponse>> getTodayWaitings(@AuthenticationPrincipal UUID ownerId) {
  return ResponseEntity.ok(waitingManagementService.getTodayWaitings(ownerId));
}
```

import 추가:

```java
import com.qrwait.api.waiting.application.dto.TodayWaitingResponse;
import java.util.List;
```

- [ ] **Step 12: 전체 테스트 실행 — PASS 확인**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 13: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java
git add backend/src/main/java/com/qrwait/api/waiting/infrastructure/
git add backend/src/main/java/com/qrwait/api/waiting/application/
git add backend/src/main/java/com/qrwait/api/waiting/presentation/OwnerWaitingController.java
git add backend/src/test/java/com/qrwait/api/waiting/
git commit -m "feat: 오늘의 웨이팅 이력 전체 조회 API 추가 (GET /owner/stores/me/waitings/today)"
```

---

## Task 8: QR 인쇄 페이지

**Files:**

- Create: `frontend/src/pages/QrPrintPage.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: QrPrintPage.tsx 작성**

```typescript
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import type {MyStoreResponse} from '../api/owner'
import {getMyStore} from '../api/owner'
import {getStoreQrUrl} from '../api/waiting'
import useOwnerStore from '../store/ownerStore'
import LoadingSpinner from '../components/LoadingSpinner'

function QrPrintPage() {
  const navigate = useNavigate()
  const storeId = useOwnerStore((s) => s.storeId)
  const [store, setStore] = useState<MyStoreResponse | null>(null)

  useEffect(() => {
    getMyStore().then(setStore)
  }, [])

  if (!store || !storeId) return <LoadingSpinner / >

  const qrUrl = getStoreQrUrl(storeId)

  return (
      <>
          <div className = "no-print"
  style = {styles.actions} >
  <button style = {styles.backBtn}
  onClick = {()
=>
  navigate('/owner/dashboard')
}>
← 대시보드
  < /button>
  < button
  style = {styles.printBtn}
  onClick = {()
=>
  window.print()
}>
  인쇄 / PDF
  저장
  < /button>
  < /div>

  < div
  style = {styles.printContent} >
  <img src = {qrUrl}
  alt = "QR 코드"
  style = {styles.qrImage}
  />
  < p
  style = {styles.storeName} > {store.name} < /p>
      < /div>

      < style > {`
          @media print {
            .no-print { display: none !important; }
            body { margin: 0; }
          }
        `
}
  </style>
  < />
)
}

const styles: Record<string, React.CSSProperties> = {
  actions: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '1rem 1.5rem',
  },
  backBtn: {
    background: 'none',
    border: 'none',
    color: '#3b82f6',
    cursor: 'pointer',
    fontSize: '0.875rem',
  },
  printBtn: {
    padding: '0.625rem 1.25rem',
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    background: '#fff',
    fontSize: '0.875rem',
    fontWeight: 600,
    cursor: 'pointer',
  },
  printContent: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '80vh',
    gap: '1.5rem',
  },
  qrImage: {
    width: 300,
    height: 300,
  },
  storeName: {
    fontSize: '1.75rem',
    fontWeight: 700,
    textAlign: 'center',
    margin: 0,
  },
}

export default QrPrintPage
```

- [ ] **Step 2: App.tsx — QrPrintPage 라우트 추가**

import 추가:

```typescript
import QrPrintPage from './pages/QrPrintPage'
```

점주 라우트 블록에 추가 (`/owner/settings` 아래):

```tsx
<Route path="/owner/qr-print" element={<PrivateRoute><QrPrintPage/></PrivateRoute>}/>
```

- [ ] **Step 3: 동작 확인**

```bash
cd frontend && npm run dev
```

브라우저에서 `/owner/dashboard` 로그인 후 수동으로 `/owner/qr-print` 접속:

- QR 이미지와 매장명이 화면 중앙에 표시되는지 확인
- "인쇄 / PDF 저장" 버튼 클릭 → 브라우저 인쇄 다이얼로그가 열리고 QR + 매장명만 표시되는지 확인

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/QrPrintPage.tsx frontend/src/App.tsx
git commit -m "feat: QR 인쇄 페이지 추가 (/owner/qr-print, @media print A4 레이아웃)"
```

---

## Task 9: 오늘의 웨이팅 이력 페이지

> Task 7 완료 후 실행

**Files:**

- Modify: `frontend/src/api/owner.ts`
- Create: `frontend/src/pages/HistoryPage.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: api/owner.ts — TodayWaiting 타입 + getTodayWaitings 추가**

파일 끝에 추가:

```typescript
export interface TodayWaiting {
  waitingId: string
  waitingNumber: number
  phoneNumber: string
  partySize: number
  status: 'WAITING' | 'CALLED' | 'ENTERED' | 'NO_SHOW' | 'CANCELLED'
  createdAt: string
}

export const getTodayWaitings = (): Promise<TodayWaiting[]> =>
    ownerClient.get('/owner/stores/me/waitings/today').then((res) => res.data)
```

- [ ] **Step 2: HistoryPage.tsx 작성**

```typescript
import {useEffect, useState} from 'react'
import {useNavigate, useSearchParams} from 'react-router-dom'
import type {TodayWaiting} from '../api/owner'
import {getTodayWaitings} from '../api/owner'
import LoadingSpinner from '../components/LoadingSpinner'
import ErrorMessage from '../components/ErrorMessage'

type TabKey = 'ALL' | 'CURRENT' | 'ENTERED' | 'NO_SHOW' | 'CANCELLED'

const TABS: { key: TabKey; label: string; statuses: TodayWaiting['status'][] }[] = [
  {key: 'ALL', label: '전체', statuses: ['WAITING', 'CALLED', 'ENTERED', 'NO_SHOW', 'CANCELLED']},
  {key: 'CURRENT', label: '등록', statuses: ['WAITING', 'CALLED']},
  {key: 'ENTERED', label: '입장', statuses: ['ENTERED']},
  {key: 'NO_SHOW', label: '노쇼', statuses: ['NO_SHOW']},
  {key: 'CANCELLED', label: '취소', statuses: ['CANCELLED']},
]

const STATUS_LABELS: Record<TodayWaiting['status'], string> = {
  WAITING: '대기',
  CALLED: '호출됨',
  ENTERED: '입장',
  NO_SHOW: '노쇼',
  CANCELLED: '취소',
}

const STATUS_COLORS: Record<TodayWaiting['status'], string> = {
  WAITING: '#374151',
  CALLED: '#d97706',
  ENTERED: '#16a34a',
  NO_SHOW: '#dc2626',
  CANCELLED: '#6b7280',
}

function statusParamToTabKey(param: string | null): TabKey {
  if (param === 'ENTERED') return 'ENTERED'
  if (param === 'NO_SHOW') return 'NO_SHOW'
  if (param === 'CANCELLED') return 'CANCELLED'
  if (param === 'CURRENT') return 'CURRENT'
  return 'ALL'
}

function tabKeyToStatusParam(key: TabKey): string | null {
  if (key === 'ALL') return null
  return key
}

function HistoryPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [entries, setEntries] = useState<TodayWaiting[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const activeTab = statusParamToTabKey(searchParams.get('status'))

  useEffect(() => {
    getTodayWaitings()
        .then(setEntries)
        .catch(() => setError('이력을 불러오지 못했습니다.'))
        .finally(() => setLoading(false))
  }, [])

  const handleTabChange = (key: TabKey) => {
    const param = tabKeyToStatusParam(key)
    if (param === null) {
      setSearchParams({})
    } else {
      setSearchParams({status: param})
    }
  }

  const currentTab = TABS.find((t) => t.key === activeTab)!
  const filtered = entries.filter((e) => currentTab.statuses.includes(e.status))

  const formatTime = (iso: string) => {
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  if (loading) return <LoadingSpinner / >

  return (
      <div style = {styles.container} >
      <div style = {styles.header} >
      <button style = {styles.backBtn}
  onClick = {()
=>
  navigate('/owner/dashboard')
}>
← 대시보드
  < /button>
  < h1
  style = {styles.title} > 오늘의
  웨이팅 < /h1>
  < /div>

  {
    error && <ErrorMessage message = {error}
    />}

    < div
    style = {styles.tabs} >
        {
          TABS.map(({key, label}) => (
              <button
                  key = {key}
          style = {
    {
    ...
      styles.tab,
          backgroundColor
    :
      activeTab === key ? '#3b82f6' : '#f3f4f6',
          color
    :
      activeTab === key ? '#fff' : '#374151',
    }
  }
    onClick = {()
  =>
    handleTabChange(key)
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
      filtered.length === 0 ? (
          <p style = {styles.empty} > 해당 항목이
      없습니다. < /p>
    ) :
      (
          <div style = {styles.list} >
              {
                filtered.map((entry) => (
                    <div key = {entry.waitingId} style = {styles.card} >
                <span style = {styles.number} >
    #
      {
        entry.waitingNumber
      }
      </span>
      < span
      style = {styles.phone} > {entry.phoneNumber} < /span>
          < span
      style = {styles.meta} > {entry.partySize}
      명 < /span>
      < span
      style = {
      {...
        styles.status, color
      :
        STATUS_COLORS[entry.status]
      }
    }>
      {
        STATUS_LABELS[entry.status]
      }
      </span>
      < span
      style = {styles.time} > {formatTime(entry.createdAt
    )
    }
      </span>
      < /div>
    ))
    }
      </div>
    )
    }
    </div>
  )
  }

  const styles: Record<string, React.CSSProperties> = {
    container: {
      maxWidth: 480,
      margin: '0 auto',
      padding: '1.5rem',
      display: 'flex',
      flexDirection: 'column',
      gap: '1.25rem',
    },
    header: {
      display: 'flex',
      alignItems: 'center',
      gap: '1rem',
    },
    backBtn: {
      background: 'none',
      border: 'none',
      color: '#3b82f6',
      cursor: 'pointer',
      fontSize: '0.875rem',
      flexShrink: 0,
    },
    title: {
      fontSize: '1.125rem',
      fontWeight: 700,
      margin: 0,
    },
    tabs: {
      display: 'flex',
      gap: '0.375rem',
      flexWrap: 'wrap',
    },
    tab: {
      padding: '0.5rem 0.875rem',
      borderRadius: '0.5rem',
      border: 'none',
      fontSize: '0.8125rem',
      fontWeight: 600,
      cursor: 'pointer',
    },
    empty: {
      color: '#9ca3af',
      fontSize: '0.875rem',
      textAlign: 'center',
      padding: '3rem 0',
    },
    list: {
      display: 'flex',
      flexDirection: 'column',
      gap: '0.5rem',
    },
    card: {
      display: 'flex',
      alignItems: 'center',
      gap: '0.625rem',
      padding: '0.875rem 1rem',
      borderRadius: '0.75rem',
      border: '1px solid #e2e8f0',
      backgroundColor: '#fff',
      fontSize: '0.875rem',
    },
    number: {
      fontWeight: 700,
      color: '#3b82f6',
      minWidth: '2.5rem',
    },
    phone: {
      flex: 1,
      fontWeight: 500,
    },
    meta: {
      color: '#6b7280',
      minWidth: '2rem',
    },
    status: {
      fontWeight: 600,
      minWidth: '3.5rem',
      textAlign: 'right',
    },
    time: {
      color: '#9ca3af',
      minWidth: '2.75rem',
      textAlign: 'right',
    },
  }

  export default HistoryPage
```

- [ ] **Step 3: App.tsx — HistoryPage 라우트 추가**

import 추가:

```typescript
import HistoryPage from './pages/HistoryPage'
```

점주 라우트 블록에 추가:

```tsx
<Route path="/owner/history" element={<PrivateRoute><HistoryPage/></PrivateRoute>}/>
```

- [ ] **Step 4: 동작 확인**

브라우저에서 `/owner/history` 접속:

- "전체" 탭이 기본 선택되고 오늘 등록된 웨이팅 목록이 표시되는지 확인
- 탭 클릭 시 URL의 `?status=` 쿼리가 변경되는지 확인
- 데이터 없을 때 "해당 항목이 없습니다." 메시지 확인
- `/owner/history?status=ENTERED` 직접 접속 시 "입장" 탭이 선택되는지 확인

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/api/owner.ts frontend/src/pages/HistoryPage.tsx frontend/src/App.tsx
git commit -m "feat: 오늘의 웨이팅 이력 페이지 추가 (/owner/history, 탭 필터)"
```

---

## Task 10: 대시보드 변경 — QR 인쇄 버튼 + 통계 카드 클릭

> Task 8, Task 9 완료 후 실행

**Files:**

- Modify: `frontend/src/pages/DashboardPage.tsx`

- [ ] **Step 1: DashboardPage.tsx — QR 인쇄 버튼 추가**

헤더 영역에서 로그아웃 버튼 바로 앞에 "QR 인쇄" 버튼 추가:

```tsx
<div style={styles.header}>
  <div>
    <h1 style={styles.storeName}>{store?.name ?? '대시보드'}</h1>
    {store && (
        <span style={{...styles.statusBadge, backgroundColor: STATUS_COLORS[store.status]}}>
          {STATUS_LABELS[store.status]}
        </span>
    )}
  </div>
  <div style={styles.headerActions}>
    <Button variant="secondary" onClick={() => navigate('/owner/qr-print')} style={styles.headerBtn}>
      QR 인쇄
    </Button>
    <Button variant="secondary" onClick={handleLogout} style={styles.headerBtn}>
      로그아웃
    </Button>
  </div>
</div>
```

`styles` 객체에 추가:

```typescript
headerActions: {
  display: 'flex',
      gap
:
  '0.5rem',
      alignItems
:
  'center',
}
,
headerBtn: {
  width: 'auto',
      minHeight
:
  'auto',
      padding
:
  '0.5rem 1rem',
      fontSize
:
  '0.875rem',
}
,
```

- [ ] **Step 2: 통계 카드 "등록" → "전체" 이름 변경 + 클릭 핸들러 추가**

`summaryGrid` 섹션 전체를 아래로 교체:

```tsx
{
  summary && (
      <section>
        <p style={styles.sectionTitle}>오늘의 통계</p>
        <div style={styles.summaryGrid}>
          <div
              style={{...styles.summaryCard, cursor: 'pointer'}}
              onClick={() => navigate('/owner/history')}
          >
            <p style={styles.summaryValue}>{summary.totalRegistered}</p>
            <p style={styles.summaryLabel}>전체</p>
          </div>
          <div
              style={{...styles.summaryCard, cursor: 'pointer'}}
              onClick={() => navigate('/owner/history?status=ENTERED')}
          >
            <p style={styles.summaryValue}>{summary.totalEntered}</p>
            <p style={styles.summaryLabel}>입장</p>
          </div>
          <div
              style={{...styles.summaryCard, cursor: 'pointer'}}
              onClick={() => navigate('/owner/history?status=NO_SHOW')}
          >
            <p style={styles.summaryValue}>{summary.totalNoShow}</p>
            <p style={styles.summaryLabel}>노쇼</p>
          </div>
          <div
              style={{...styles.summaryCard, cursor: 'pointer'}}
              onClick={() => navigate('/owner/history?status=CANCELLED')}
          >
            <p style={styles.summaryValue}>{summary.totalCancelled}</p>
            <p style={styles.summaryLabel}>취소</p>
          </div>
        </div>
      </section>
  )
}
```

- [ ] **Step 3: 동작 확인**

브라우저에서 `/owner/dashboard` 접속:

- 헤더에 "QR 인쇄", "로그아웃" 버튼이 나란히 표시되는지 확인
- "QR 인쇄" 버튼 클릭 → `/owner/qr-print`로 이동 확인
- 통계 카드 "전체" 표시 확인 (기존 "등록"에서 변경)
- "전체" 카드 클릭 → `/owner/history` 이동 확인
- "입장" 카드 클릭 → `/owner/history?status=ENTERED` 이동 + "입장" 탭 선택 확인
- "노쇼", "취소" 카드도 동일하게 동작 확인

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/DashboardPage.tsx
git commit -m "feat: 대시보드 QR 인쇄 버튼 추가, 통계 카드 이력 페이지 연결"
```

---

## 셀프 리뷰

### 스펙 커버리지 체크

| 스펙 요구사항                         | 구현 태스크  |
|---------------------------------|---------|
| 전화번호 전환 DB 마이그레이션               | Task 1  |
| WaitingEntry phoneNumber 도메인 전환 | Task 2  |
| JPA·DTO·서비스 phoneNumber 반영      | Task 3  |
| 대기 현황 API 노출                    | Task 4  |
| 랜딩 페이지 전화번호 입력 + 대기 현황 + 동의     | Task 5  |
| 대시보드 phoneNumber 표시             | Task 6  |
| 오늘의 웨이팅 이력 API                  | Task 7  |
| A4 QR 인쇄 페이지                    | Task 8  |
| 오늘의 웨이팅 이력 탭 필터 페이지             | Task 9  |
| 대시보드 QR 인쇄 버튼 + 통계 카드 클릭        | Task 10 |
| URL 쿼리스트링 ↔ 탭 연동                | Task 9  |
| 항목 없을 때 "없음" 안내                 | Task 9  |

### 타입 일관성 체크

- `TodayWaiting.status` 타입: `'WAITING' | 'CALLED' | 'ENTERED' | 'NO_SHOW' | 'CANCELLED'` — Task 7(프론트)과 Task 9에서 동일하게 사용 ✓
- `tabKeyToStatusParam('CURRENT')` → `'CURRENT'`; `statusParamToTabKey('CURRENT')` → `'CURRENT'` — 왕복 일관성 ✓
- `TodayWaitingResponse.phoneNumber` (백엔드) ↔ `TodayWaiting.phoneNumber` (프론트) ✓
- `OwnerWaitingController` 엔드포인트 `/stores/me/waitings/today` ↔ `ownerClient.get('/owner/stores/me/waitings/today')` ✓
- `WaitingEntry.restore(id, storeId, phoneNumber, ...)` — Task 2 도메인과 Task 7 테스트 코드 일치 ✓
