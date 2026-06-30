# 호출 알림 — 전용 페이지 이동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 손님이 등록 완료/실시간 현황 화면 어디에 있든, 본인이 호출되면 전용 "호출됨" 페이지로 자동 이동하고, 새로고침해도 호출 상태가 유지된다.

**Architecture:** 백엔드는 개별 손님 조회 응답에 `status`를 노출하도록 DTO를 분리한다(매장 집계 DTO는 불변). 프론트는 공통 SSE 훅을 추출해 등록완료·실시간·호출됨 세 페이지가 동일한 구독 로직을 공유하고, 본인 `waiting-called` 이벤트 또는 로드 시 `status === 'CALLED'` 조건에서 전용 페이지로 이동한다.

**Tech Stack:** Spring Boot(Java, JUnit/Mockito), React + TypeScript(Vite), zustand, EventSource(SSE).

## Global Constraints

- 도메인 모델 getter는 Lombok `@Getter` 사용 (기존 규칙).
- 백엔드 외부 응답 DTO는 record 사용 (기존 패턴).
- 프론트는 테스트 러너가 없으므로 검증은 `npx tsc --noEmit`(프로젝트 루트 `frontend/`에서 실행) + 수동 확인.
- 커밋 메시지는 한글, 기존 prefix 관례(`backend feat:`, `frontend fix:` 등) 사용.
- 기존 SSE 이벤트 이름: 갱신 `waiting-updated`, 호출 `waiting-called`(payload `{"waitingId": "..."}`).
- 호출 이벤트는 매장 전체에 브로드캐스트됨 → 클라이언트가 payload의 `waitingId`를 본인과 비교해야 함.

---

## File Structure

백엔드:
- `backend/src/main/java/com/qrwait/api/waiting/application/dto/MyWaitingStatusResponse.java` (신규) — 개별 손님 조회 응답 (status 포함)
- `backend/src/main/java/com/qrwait/api/waiting/application/WaitingService.java` (수정) — `getStatus` 반환 타입
- `backend/src/main/java/com/qrwait/api/waiting/presentation/WaitingController.java` (수정) — `getStatus` 반환 타입
- `backend/src/test/java/com/qrwait/api/waiting/application/WaitingServiceTest.java` (수정) — `getStatus` 케이스

프론트:
- `frontend/src/api/waiting.ts` (수정) — `WaitingStatusResponse`에 `status` 추가
- `frontend/src/hooks/useWaitingSse.ts` (신규) — 공통 SSE 구독 훅
- `frontend/src/pages/WaitingCalledPage.tsx` (신규) — 호출됨 페이지
- `frontend/src/App.tsx` (수정) — `/waiting/:waitingId/called` 라우트
- `frontend/src/pages/WaitingConfirmPage.tsx` (수정) — SSE 구독 + 호출 시 이동
- `frontend/src/pages/WaitingStatusPage.tsx` (수정) — 훅으로 이관, 모달/진단로그 제거, 호출/로드 시 이동

---

## Task 1: 백엔드 — 개별 조회 응답에 status 노출 (DTO 분리)

**Files:**
- Create: `backend/src/main/java/com/qrwait/api/waiting/application/dto/MyWaitingStatusResponse.java`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/application/WaitingService.java:66-87`
- Modify: `backend/src/main/java/com/qrwait/api/waiting/presentation/WaitingController.java:55-58`
- Test: `backend/src/test/java/com/qrwait/api/waiting/application/WaitingServiceTest.java:159-181`

**Interfaces:**
- Produces: `MyWaitingStatusResponse(int currentRank, int totalWaiting, int estimatedWaitMinutes, WaitingStatus status)`
- Produces: `WaitingService.getStatus(UUID waitingId): MyWaitingStatusResponse`
- 불변: `WaitingStatusResponse`(매장 집계용)와 `getStoreWaitingStatus`, `SsePublisher`, `StoreController`는 건드리지 않는다.

- [ ] **Step 1: getStatus 테스트를 새 타입+status 기대로 수정 (실패하게)**

`WaitingServiceTest.java`의 import에 다음이 이미 있는지 확인하고 없으면 추가:
`import com.qrwait.api.waiting.application.dto.MyWaitingStatusResponse;`

`getStatus_currentRank_정확성_검증()` 본문(159-181줄)을 아래로 교체:

```java
  @Test
  void getStatus_currentRank_정확성_검증() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    WaitingEntry target = WaitingEntry.restore(
        waitingId, storeId, "010-1234-5678", 2, 3, WaitingStatus.WAITING, LocalDateTime.now());

    List<WaitingEntry> waitingList = List.of(
        WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0001", 2, 1, WaitingStatus.WAITING, LocalDateTime.now()),
        WaitingEntry.restore(UUID.randomUUID(), storeId, "010-1111-0002", 2, 2, WaitingStatus.WAITING, LocalDateTime.now()),
        target
    );

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(target));
    given(waitingRepository.findByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(waitingList);

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.currentRank()).isEqualTo(3);
    assertThat(response.totalWaiting()).isEqualTo(3);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(10); // 앞 2팀 × 5분 (fallback)
    assertThat(response.status()).isEqualTo(WaitingStatus.WAITING);
  }
```

추가로 CALLED 상태가 노출되는지 검증하는 테스트를 같은 `// ===== getStatus =====` 섹션에 추가:

```java
  @Test
  void getStatus_호출된_웨이팅은_CALLED_상태를_반환() {
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    WaitingEntry called = WaitingEntry.restore(
        waitingId, storeId, "010-1234-5678", 2, 1, WaitingStatus.CALLED, LocalDateTime.now());

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(called));
    given(waitingRepository.findByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(List.of());

    MyWaitingStatusResponse response = waitingService.getStatus(waitingId);

    assertThat(response.status()).isEqualTo(WaitingStatus.CALLED);
  }
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `cd backend && ./gradlew compileTestJava`
Expected: FAIL — `MyWaitingStatusResponse` 심볼 없음 / `getStatus`가 `WaitingStatusResponse` 반환.

- [ ] **Step 3: 새 DTO 생성**

`backend/src/main/java/com/qrwait/api/waiting/application/dto/MyWaitingStatusResponse.java`:

```java
package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.WaitingStatus;

public record MyWaitingStatusResponse(
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes,
    WaitingStatus status
) {

}
```

- [ ] **Step 4: WaitingService.getStatus 수정**

`WaitingService.java` import에 추가:
`import com.qrwait.api.waiting.application.dto.MyWaitingStatusResponse;`

`getStatus` 메서드(66-87줄)를 교체 — 반환 타입과 마지막 생성자만 변경:

```java
  @Transactional(readOnly = true)
  public MyWaitingStatusResponse getStatus(UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    if (entry.getStatus() != WaitingStatus.WAITING && entry.getStatus() != WaitingStatus.CALLED) {
      throw new WaitingNotFoundException(waitingId);
    }

    List<WaitingEntry> waitingList = waitingRepository
        .findByStoreIdAndStatus(entry.getStoreId(), WaitingStatus.WAITING);

    long ahead = waitingList.stream()
        .filter(e -> e.getWaitingNumber() < entry.getWaitingNumber())
        .count();

    int currentRank = (int) ahead + 1;
    int totalWaiting = waitingList.size();

    int estimatedWaitMinutes = estimatedWaitMinutes(entry.getStoreId(), (int) ahead);

    return new MyWaitingStatusResponse(currentRank, totalWaiting, estimatedWaitMinutes, entry.getStatus());
  }
```

- [ ] **Step 5: WaitingController.getStatus 반환 타입 수정**

`WaitingController.java` import에 추가:
`import com.qrwait.api.waiting.application.dto.MyWaitingStatusResponse;`

`getStatus` 핸들러(55-58줄)를 교체:

```java
  @GetMapping("/waitings/{waitingId}")
  public ResponseEntity<MyWaitingStatusResponse> getStatus(@PathVariable UUID waitingId) {
    return ResponseEntity.ok(waitingService.getStatus(waitingId));
  }
```

(`WaitingStatusResponse` import는 더 이상 이 파일에서 안 쓰이면 제거. cancel/register는 다른 타입 사용.)

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.waiting.application.WaitingServiceTest"`
Expected: PASS (getStatus 2개 케이스 포함 전부 통과).

- [ ] **Step 7: 전체 빌드 확인**

Run: `cd backend && ./gradlew test`
Expected: PASS (기존 컨트롤러 테스트 등 영향 없음).

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/waiting/application/dto/MyWaitingStatusResponse.java \
  backend/src/main/java/com/qrwait/api/waiting/application/WaitingService.java \
  backend/src/main/java/com/qrwait/api/waiting/presentation/WaitingController.java \
  backend/src/test/java/com/qrwait/api/waiting/application/WaitingServiceTest.java
git commit -m "$(cat <<'EOF'
backend feat: 개별 웨이팅 조회 응답에 status 노출 (DTO 분리)

호출됨 페이지의 새로고침 견고성을 위해 GET /api/waitings/{id} 응답에
WaitingStatus를 포함하도록 MyWaitingStatusResponse를 분리 신설.
매장 집계용 WaitingStatusResponse 및 SSE/매장 경로는 불변.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 프론트 — getWaiting 응답 타입에 status 추가

**Files:**
- Modify: `frontend/src/api/waiting.ts:29-33`

**Interfaces:**
- Produces: `WaitingStatusResponse.status: 'WAITING' | 'CALLED'` — 이후 모든 페이지가 `res.status`로 사용.

- [ ] **Step 1: 타입에 status 추가**

`frontend/src/api/waiting.ts`의 `WaitingStatusResponse` 인터페이스(29-33줄)를 교체:

```ts
export interface WaitingStatusResponse {
  currentRank: number
  totalWaiting: number
  estimatedWaitMinutes: number
  status: 'WAITING' | 'CALLED'
}
```

- [ ] **Step 2: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS (status는 옵셔널이 아니지만 아직 소비처에서 미사용 → 에러 없음).

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/api/waiting.ts
git commit -m "$(cat <<'EOF'
frontend feat: 웨이팅 상태 조회 응답 타입에 status 필드 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 프론트 — 공통 SSE 구독 훅

**Files:**
- Create: `frontend/src/hooks/useWaitingSse.ts`

**Interfaces:**
- Produces:
  ```ts
  function useWaitingSse(
    waitingId: string | undefined,
    storeId: string | null,
    options: {
      enabled: boolean
      onUpdated?: () => void
      onCalled?: () => void
      onConnectionChange?: (status: 'connecting' | 'connected' | 'error') => void
    },
  ): void
  ```
- 동작: `enabled && waitingId && storeId`일 때만 `/api/waitings/{waitingId}/stream?storeId={storeId}` 구독. `waiting-updated`→`onUpdated`, 본인(`payload.waitingId === waitingId`) `waiting-called`→`onCalled`. 재연결 최대 3회. 콜백은 ref로 보관해 재연결 churn 방지.

- [ ] **Step 1: 훅 생성**

`frontend/src/hooks/useWaitingSse.ts`:

```ts
import {useEffect, useRef} from 'react'

const MAX_RETRIES = 3

export type SseConnectionStatus = 'connecting' | 'connected' | 'error'

interface UseWaitingSseOptions {
  enabled: boolean
  onUpdated?: () => void
  onCalled?: () => void
  onConnectionChange?: (status: SseConnectionStatus) => void
}

export function useWaitingSse(
    waitingId: string | undefined,
    storeId: string | null,
    options: UseWaitingSseOptions,
): void {
  const {enabled} = options
  const onUpdatedRef = useRef(options.onUpdated)
  const onCalledRef = useRef(options.onCalled)
  const onConnectionChangeRef = useRef(options.onConnectionChange)

  onUpdatedRef.current = options.onUpdated
  onCalledRef.current = options.onCalled
  onConnectionChangeRef.current = options.onConnectionChange

  useEffect(() => {
    if (!enabled || !waitingId || !storeId) return

    let unmounted = false
    let retryCount = 0
    let retryTimer: ReturnType<typeof setTimeout> | null = null
    let es: EventSource | null = null

    const connect = () => {
      if (unmounted) return

      es = new EventSource(`/api/waitings/${waitingId}/stream?storeId=${storeId}`)

      es.onopen = () => {
        if (unmounted) return
        onConnectionChangeRef.current?.('connected')
        retryCount = 0
      }

      es.onerror = () => {
        if (unmounted) return
        es?.close()
        if (retryCount < MAX_RETRIES) {
          retryCount++
          onConnectionChangeRef.current?.('connecting')
          retryTimer = setTimeout(connect, 3000)
        } else {
          onConnectionChangeRef.current?.('error')
        }
      }

      es.addEventListener('waiting-updated', () => {
        if (!unmounted) onUpdatedRef.current?.()
      })

      es.addEventListener('waiting-called', (e) => {
        if (unmounted) return
        try {
          const data = JSON.parse((e as MessageEvent).data)
          if (data.waitingId === waitingId) onCalledRef.current?.()
        } catch {
          // payload 파싱 실패 시 무시
        }
      })
    }

    onConnectionChangeRef.current?.('connecting')
    connect()

    return () => {
      unmounted = true
      if (retryTimer) clearTimeout(retryTimer)
      es?.close()
    }
  }, [waitingId, storeId, enabled])
}
```

- [ ] **Step 2: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS (미사용 export 경고 없이 통과; eslint의 미사용 규칙은 빌드 차단 아님).

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/hooks/useWaitingSse.ts
git commit -m "$(cat <<'EOF'
frontend feat: 손님 웨이팅 SSE 구독 공통 훅 추가

등록완료/실시간/호출됨 페이지가 공유할 useWaitingSse 훅 신설.
waiting-updated/본인 waiting-called 이벤트 처리 + 재연결 로직 포함.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 프론트 — 호출됨 페이지 + 라우트

**Files:**
- Create: `frontend/src/pages/WaitingCalledPage.tsx`
- Modify: `frontend/src/App.tsx:5-6,49-50`

**Interfaces:**
- Consumes: `useWaitingSse` (Task 3), `getWaiting` (`api/waiting.ts`), `getWaitingSession`/`clearWaitingSession` (`utils/session.ts`), `useWaitingStore` (`store/waitingStore.ts`), `Button`.
- Produces: 라우트 `/waiting/:waitingId/called` → `WaitingCalledPage`.
- 동작: 초기 `getWaiting`으로 CALLED 확인(아니면 분기), 입장완료/취소/노쇼(404)거나 SSE 갱신 후 CALLED 아님 → "웨이팅 종료" 화면. [웨이팅 취소]→cancel 페이지, [미루기]→disabled.

- [ ] **Step 1: 호출됨 페이지 생성**

`frontend/src/pages/WaitingCalledPage.tsx`:

```tsx
import {useCallback, useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import {getWaiting} from '../api/waiting'
import {clearWaitingSession, getWaitingSession} from '../utils/session'
import {useWaitingSse} from '../hooks/useWaitingSse'
import Button from '../components/Button'

function WaitingCalledPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()

  const {waitingNumber, storeId, clearWaiting} = useWaitingStore()
  const session = getWaitingSession()
  const resolvedStoreId = storeId ?? session?.storeId ?? null
  const resolvedWaitingNumber = waitingNumber ?? session?.waitingNumber ?? null

  const [initialized, setInitialized] = useState(false)
  const [ended, setEnded] = useState(false)

  // 입장완료/취소/노쇼로 더 이상 CALLED가 아닌 경우 종료 처리
  const endSession = useCallback(() => {
    clearWaitingSession()
    clearWaiting()
    setEnded(true)
  }, [clearWaiting])

  const refresh = useCallback(() => {
    if (!waitingId) return
    getWaiting(waitingId)
        .then((res) => {
          if (res.status === 'WAITING') {
            // 아직 호출 전 (비정상 진입) → 실시간 현황으로
            navigate(`/waiting/${waitingId}/status`, {replace: true})
          }
          // CALLED면 그대로 유지
        })
        .catch((err: unknown) => {
          const status = (err as { status?: number }).status
          if (status === 404) endSession()
          // 일시적 오류는 무시 (다음 이벤트/새로고침 때 재시도)
        })
        .finally(() => setInitialized(true))
  }, [waitingId, navigate, endSession])

  useEffect(() => {
    refresh()
  }, [refresh])

  useWaitingSse(waitingId, resolvedStoreId, {
    enabled: !!waitingId && !!resolvedStoreId && !ended,
    onUpdated: refresh,
  })

  if (!initialized) return null

  if (ended) {
    return (
        <div style={styles.container}>
          <div style={styles.endedIcon}>✓</div>
          <p style={styles.endedTitle}>웨이팅이 종료되었습니다</p>
          <p style={styles.endedDesc}>입장이 완료되었거나 취소된 웨이팅입니다.</p>
          <Button onClick={() => navigate('/')}>처음으로</Button>
        </div>
    )
  }

  return (
      <div style={styles.container}>
        <div style={styles.callBadge}>입장해 주세요!</div>

        <div style={styles.card}>
          <p style={styles.label}>내 웨이팅 번호</p>
          <p style={styles.number}>{resolvedWaitingNumber ?? '-'}</p>
        </div>

        <p style={styles.desc}>순서가 되었습니다. 지금 입장해 주세요.</p>

        <div style={styles.buttons}>
          <Button variant="secondary" onClick={() => navigate(`/waiting/${waitingId}/cancel`)}>
            웨이팅 취소
          </Button>
          <Button variant="secondary" disabled>
            미루기 (준비 중)
          </Button>
        </div>
      </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '2rem 1.5rem',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '1.5rem',
  },
  callBadge: {
    backgroundColor: '#dbeafe',
    color: '#1d4ed8',
    padding: '0.5rem 1.25rem',
    borderRadius: '999px',
    fontSize: '1rem',
    fontWeight: 700,
  },
  card: {
    width: '100%',
    textAlign: 'center',
    padding: '2rem',
    borderRadius: '1rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  label: {
    fontSize: '0.875rem',
    color: '#6b7280',
    marginBottom: '0.5rem',
  },
  number: {
    fontSize: '4rem',
    fontWeight: 700,
    color: '#1d4ed8',
    lineHeight: 1,
  },
  desc: {
    fontSize: '0.875rem',
    color: '#6b7280',
    textAlign: 'center',
  },
  buttons: {
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  endedIcon: {
    width: 64,
    height: 64,
    borderRadius: '50%',
    backgroundColor: '#f3f4f6',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '2rem',
    color: '#6b7280',
  },
  endedTitle: {
    fontSize: '1.25rem',
    fontWeight: 700,
    textAlign: 'center',
  },
  endedDesc: {
    fontSize: '0.875rem',
    color: '#6b7280',
    textAlign: 'center',
  },
}

export default WaitingCalledPage
```

- [ ] **Step 2: 라우트 등록**

`frontend/src/App.tsx`의 import 블록(5-6줄 근처)에 추가:

```tsx
import WaitingCalledPage from './pages/WaitingCalledPage'
```

손님 라우트(49줄 `status` 라우트 아래)에 추가:

```tsx
        <Route path="/waiting/:waitingId/called" element={<WaitingCalledPage />} />
```

- [ ] **Step 3: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/WaitingCalledPage.tsx frontend/src/App.tsx
git commit -m "$(cat <<'EOF'
frontend feat: 호출됨 전용 페이지 및 라우트 추가

/waiting/:id/called 라우트와 WaitingCalledPage 신설.
입장 안내 + 웨이팅 번호 표시, 취소 버튼, 미루기(비활성) 버튼.
입장완료/취소/노쇼 시 종료 화면으로 전환.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 프론트 — 등록 완료 페이지에서 호출 시 이동

**Files:**
- Modify: `frontend/src/pages/WaitingConfirmPage.tsx:1-17`

**Interfaces:**
- Consumes: `useWaitingSse` (Task 3).
- 동작: 등록 직후 store가 채워진 상태에서 SSE 구독. 본인 호출 시 `/waiting/{id}/called`로 이동. (새로고침 시에는 기존 로직대로 store가 비어 `/status`로 바운스되며, status 페이지가 CALLED 처리를 담당.)

- [ ] **Step 1: ConfirmPage에 SSE 구독 추가**

`frontend/src/pages/WaitingConfirmPage.tsx` 상단 import에 추가:

```tsx
import {useWaitingSse} from '../hooks/useWaitingSse'
```

컴포넌트 본문에서 store 구조분해에 `storeId`를 포함하고, 기존 redirect effect 아래에 SSE 구독을 추가. 1-17줄을 아래로 교체:

```tsx
import {useEffect} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import {useWaitingSse} from '../hooks/useWaitingSse'
import Button from '../components/Button'

function WaitingConfirmPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()
  const {waitingNumber, storeId, currentRank, estimatedWaitMinutes} = useWaitingStore()

  useEffect(() => {
    if (!waitingNumber) {
      navigate(`/waiting/${waitingId}/status`, {replace: true})
    }
  }, [waitingNumber, waitingId, navigate])

  useWaitingSse(waitingId, storeId, {
    enabled: !!waitingId && !!storeId && !!waitingNumber,
    onCalled: () => navigate(`/waiting/${waitingId}/called`),
  })

  if (!waitingNumber) return null
```

(이후 `return (...)` JSX 본문과 styles는 그대로 둔다.)

- [ ] **Step 2: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/pages/WaitingConfirmPage.tsx
git commit -m "$(cat <<'EOF'
frontend fix: 등록 완료 화면에서 호출 시 호출됨 페이지로 이동

등록 완료 화면에도 SSE를 구독해, 손님이 이 화면에 머물러도
호출되면 /called로 자동 이동하도록 수정.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: 프론트 — 실시간 현황 페이지 훅 이관 + 모달 제거 + 호출 이동

**Files:**
- Modify: `frontend/src/pages/WaitingStatusPage.tsx` (전체 재작성: SSE 로직을 훅으로 이관, 모달/진단로그/`showCalledModal` 제거, 호출 및 로드 시 CALLED → `/called` 이동)

**Interfaces:**
- Consumes: `useWaitingSse` (Task 3), `getWaiting`(이제 `status` 포함).
- 동작: 초기 `getWaiting`에서 `status === 'CALLED'`면 즉시 `/called`로 이동(새로고침 견고성). SSE `onUpdated`→재조회(CALLED면 이동, 아니면 순번 갱신), `onCalled`→이동. 연결 배지 유지.

- [ ] **Step 1: WaitingStatusPage 전체 교체**

`frontend/src/pages/WaitingStatusPage.tsx` 전체를 아래로 교체:

```tsx
import {useCallback, useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import {getWaiting} from '../api/waiting'
import {clearWaitingSession, getWaitingSession} from '../utils/session'
import {useWaitingSse, type SseConnectionStatus} from '../hooks/useWaitingSse'
import Button from '../components/Button'

function WaitingStatusPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()

  const {waitingNumber, storeId, currentRank, totalWaiting, estimatedWaitMinutes, updateStatus, clearWaiting} =
      useWaitingStore()

  const session = getWaitingSession()
  const resolvedStoreId = storeId ?? session?.storeId ?? null
  const resolvedWaitingNumber = waitingNumber ?? session?.waitingNumber ?? null

  const [connectionStatus, setConnectionStatus] = useState<SseConnectionStatus>('connecting')
  const [expired, setExpired] = useState(false)
  const [initialized, setInitialized] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  // API 응답 이후에만 리다이렉트 평가
  useEffect(() => {
    if (!resolvedStoreId && initialized && !expired) {
      navigate('/', {replace: true})
    }
  }, [resolvedStoreId, initialized, expired, navigate])

  // 상태 로드/갱신: CALLED면 호출됨 페이지로, 종료면 expired
  const loadStatus = useCallback((isInitial: boolean) => {
    if (!waitingId) return
    getWaiting(waitingId)
        .then((res) => {
          if (res.status === 'CALLED') {
            navigate(`/waiting/${waitingId}/called`, {replace: true})
            return
          }
          updateStatus({
            currentRank: res.currentRank,
            totalWaiting: res.totalWaiting,
            estimatedWaitMinutes: res.estimatedWaitMinutes,
          })
        })
        .catch((err: unknown) => {
          const status = (err as { status?: number }).status
          if (status === 404) {
            clearWaitingSession()
            clearWaiting()
            setExpired(true)
          } else if (isInitial) {
            setLoadError('서버에 연결할 수 없습니다. 잠시 후 새로고침해 주세요.')
          }
        })
        .finally(() => {
          if (isInitial) setInitialized(true)
        })
  }, [waitingId, navigate, updateStatus, clearWaiting])

  useEffect(() => {
    loadStatus(true)
  }, [loadStatus])

  useWaitingSse(waitingId, resolvedStoreId, {
    enabled: !!waitingId && !!resolvedStoreId && !expired,
    onUpdated: () => loadStatus(false),
    onCalled: () => navigate(`/waiting/${waitingId}/called`),
    onConnectionChange: setConnectionStatus,
  })

  if (!initialized) return null

  if (loadError) {
    return (
        <div style={styles.container}>
          <p style={styles.expiredTitle}>연결 오류</p>
          <p style={styles.expiredDesc}>{loadError}</p>
          <Button onClick={() => window.location.reload()}>새로고침</Button>
        </div>
    )
  }

  if (expired) {
    return (
        <div style={styles.container}>
          <div style={styles.expiredIcon}>✓</div>
          <p style={styles.expiredTitle}>웨이팅이 종료되었습니다</p>
          <p style={styles.expiredDesc}>취소되었거나 이미 입장이 완료된 웨이팅입니다.</p>
          <Button onClick={() => navigate('/')}>처음으로</Button>
        </div>
    )
  }

  return (
      <div style={styles.container}>
        <div style={{...styles.statusBadge, ...statusBadgeVariant[connectionStatus]}}>
          {connectionStatus === 'connected'
              ? '● 실시간 업데이트 중'
              : connectionStatus === 'error'
                  ? '● 연결 오류'
                  : '● 연결 중...'}
        </div>

        <div style={styles.card}>
          <p style={styles.label}>내 웨이팅 번호</p>
          <p style={styles.number}>{resolvedWaitingNumber ?? '-'}</p>
        </div>

        <div style={styles.infoRow}>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>현재 대기 순서</p>
            <p style={styles.infoValue}>{currentRank != null ? `${currentRank}번째` : '-'}</p>
          </div>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>앞 대기 팀</p>
            <p style={styles.infoValue}>{currentRank != null ? `${currentRank - 1}팀` : '-'}</p>
          </div>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>총 대기 팀</p>
            <p style={styles.infoValue}>{totalWaiting != null ? `${totalWaiting}팀` : '-'}</p>
          </div>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>예상 대기시간</p>
            <p style={styles.infoValue}>
              {estimatedWaitMinutes != null ? `약 ${estimatedWaitMinutes}분` : '-'}
            </p>
          </div>
        </div>

        <div style={styles.buttons}>
          <Button variant="secondary" onClick={() => navigate(`/waiting/${waitingId}/cancel`)}>
            웨이팅 취소
          </Button>
        </div>
      </div>
  )
}

const statusBadgeVariant: Record<string, React.CSSProperties> = {
  connected: {backgroundColor: '#dcfce7', color: '#16a34a'},
  connecting: {backgroundColor: '#fef9c3', color: '#ca8a04'},
  error: {backgroundColor: '#fee2e2', color: '#dc2626'},
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '2rem 1.5rem',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '1.5rem',
  },
  statusBadge: {
    padding: '0.375rem 1rem',
    borderRadius: '999px',
    fontSize: '0.875rem',
    fontWeight: 600,
  },
  card: {
    width: '100%',
    textAlign: 'center',
    padding: '2rem',
    borderRadius: '1rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  label: {
    fontSize: '0.875rem',
    color: '#6b7280',
    marginBottom: '0.5rem',
  },
  number: {
    fontSize: '4rem',
    fontWeight: 700,
    color: '#1d4ed8',
    lineHeight: 1,
  },
  infoRow: {
    width: '100%',
    display: 'flex',
    gap: '0.75rem',
  },
  infoItem: {
    flex: 1,
    textAlign: 'center',
    padding: '1rem 0.5rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  infoLabel: {
    fontSize: '0.7rem',
    color: '#6b7280',
    marginBottom: '0.25rem',
  },
  infoValue: {
    fontSize: '1rem',
    fontWeight: 600,
  },
  buttons: {
    width: '100%',
  },
  expiredIcon: {
    width: 64,
    height: 64,
    borderRadius: '50%',
    backgroundColor: '#f3f4f6',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '2rem',
    color: '#6b7280',
  },
  expiredTitle: {
    fontSize: '1.25rem',
    fontWeight: 700,
    textAlign: 'center',
  },
  expiredDesc: {
    fontSize: '0.875rem',
    color: '#6b7280',
    textAlign: 'center',
  },
}

export default WaitingStatusPage
```

- [ ] **Step 2: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS (모달/`showCalledModal`/진단 `console.log` 모두 제거됨).

- [ ] **Step 3: 빌드 확인**

Run: `cd frontend && npm run build`
Expected: 빌드 성공.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/WaitingStatusPage.tsx
git commit -m "$(cat <<'EOF'
frontend fix: 실시간 현황 SSE를 공통 훅으로 이관, 호출 시 페이지 이동

모달 대신 /called 페이지로 이동하도록 변경.
초기 로드 시 status=CALLED면 즉시 호출됨 페이지로 이동(새로고침 견고성).
임시 진단 로그 및 호출 모달 제거.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: 수동 통합 검증 + TASKS 체크

**Files:**
- Modify: `QRWait_TASKS_v2.0.md` (6-1 호출 알림 항목 체크)

- [ ] **Step 1: 로컬 실행 후 수동 시나리오 검증**

백엔드/프론트/DB를 로컬 기동 후:
1. 손님 등록 → 등록 완료 화면에서 대기 → 점주 호출 → **호출됨 페이지로 자동 이동** 확인.
2. 손님이 "실시간 현황 보기"로 이동 후 대기 → 점주 호출 → 호출됨 페이지 이동 확인.
3. 호출됨 페이지에서 **새로고침** → 호출됨 화면 유지 확인.
4. 대기 중인 **다른 손님** 화면은 이동하지 않음 확인.
5. 호출됨 상태에서 점주가 입장 처리 → 손님 화면 "웨이팅 종료"로 전환 확인.
6. 호출됨 페이지 [미루기] 버튼 비활성 확인.

- [ ] **Step 2: TASKS 체크 갱신**

`QRWait_TASKS_v2.0.md` 6-1의 호출 알림 항목을 `- [x]`로 변경(검증 완료 시):

```markdown
- [x] 점주 호출 → 호출된 손님 화면에만 호출됨 페이지로 이동 확인 (다른 대기 손님 화면엔 미이동)
```

- [ ] **Step 3: 커밋**

```bash
git add QRWait_TASKS_v2.0.md
git commit -m "$(cat <<'EOF'
docs: 호출 알림 통합 테스트 항목 검증 완료 체크

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- 백엔드 DTO 분리(A1) → Task 1 ✓
- getWaiting 응답 status → Task 2 ✓
- 공통 SSE 훅 → Task 3 ✓
- 호출됨 페이지(취소/미루기 비활성) + 라우트 → Task 4 ✓
- ConfirmPage 호출 시 이동 → Task 5 ✓
- StatusPage 훅 이관·모달 제거·호출/로드 이동 → Task 6 ✓
- 새로고침 견고성(status=CALLED 로드 시 이동, CalledPage 404→종료) → Task 4/Task 6 ✓
- 수동 검증/TASKS → Task 7 ✓

**Placeholder scan:** 코드 단계는 모두 실제 코드 포함. "미루기 (준비 중)"은 의도된 비활성 버튼 라벨이며 placeholder 아님.

**Type consistency:** `useWaitingSse(waitingId, storeId, options)` 시그니처가 Task 4/5/6 호출부와 일치. `SseConnectionStatus` export를 Task 6에서 사용. `WaitingStatusResponse.status: 'WAITING' | 'CALLED'`(Task 2)를 Task 4/6에서 `res.status`로 사용. 백엔드 `MyWaitingStatusResponse`가 Task 1 내 생성/컨트롤러/테스트에서 일관.

**주의(실행 시):** 현재 작업 트리에 `WaitingStatusPage.tsx`의 선행 임시 수정(이벤트 이름/필터/진단로그)과 `QRWait_TASKS_v2.0.md` 변경이 미커밋 상태로 남아 있을 수 있다. Task 6은 `WaitingStatusPage.tsx`를 전체 교체하므로 임시 로그가 자연히 제거된다. 작업은 `main`이 아닌 별도 브랜치에서 진행할 것.
