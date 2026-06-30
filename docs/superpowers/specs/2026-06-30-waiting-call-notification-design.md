# 호출 알림 — 전용 페이지 이동 설계

| 항목 | 내용 |
|------|------|
| 작성일 | 2026년 06월 30일 |
| 상태 | 승인됨 (구현 대기) |
| 범위 | backend + frontend |

## 배경 / 문제

손님이 QR로 웨이팅을 등록하면 등록 완료 화면(`/waiting/{waitingId}`, `WaitingConfirmPage`)에 도착해 거기 머문다. 그런데 SSE 구독과 호출 알림(모달)은 실시간 현황 화면(`/waiting/{waitingId}/status`, `WaitingStatusPage`)에만 존재한다. 따라서 손님이 "실시간 현황 보기"로 넘어가 그 화면을 켜두지 않으면 점주가 호출해도 손님은 알 수 없다.

추가로 발견된 선행 버그(이미 수정됨):
- 백엔드는 SSE 이벤트를 `waiting-called`로 보내는데 프론트는 `called`를 구독하고 있었음 → 이름 불일치.
- 호출 이벤트는 매장 전체에 브로드캐스트되므로 클라이언트가 payload의 `waitingId`를 자신과 비교해야 하는데 비교 로직이 없었음.

## 목표

- 손님이 **등록 완료 화면이든 실시간 현황 화면이든**, 본인이 호출되면 전용 "호출됨" 페이지로 자동 전환된다.
- 모달이 아니라 **페이지 이동** 방식으로 처리한다.
- 호출 후 **새로고침해도** 호출 상태가 유지된다.

## 비목표 (YAGNI)

- "미루기(대기번호 뒤로 미루기)" 기능의 실제 동작 — 이번엔 버튼만 두고 비활성화. 향후 작업.
- 매장 집계 조회/대시보드 경로 변경.

## 설계

### 1. 백엔드 — DTO 분리 (A1)

현재 `WaitingStatusResponse(currentRank, totalWaiting, estimatedWaitMinutes)`는 성격이 다른 3곳에서 공유된다:

1. `WaitingService.getStatus(waitingId)` → `GET /api/waitings/{id}` — **개별 손님** (status 의미 있음)
2. `WaitingService.getStoreWaitingStatus(storeId)` → `GET /api/stores/{id}/waitings/status` — 매장 집계 (status 의미 없음)
3. `SsePublisher.buildStoreStatus(storeId)` → SSE `waiting-updated` payload — 매장 집계 (status 의미 없음)

공유 record에 `status`를 얹으면 2·3번에 의미 없는 값을 넣어야 하므로, 개별 손님 조회 전용 DTO를 분리한다.

- **신규** `MyWaitingStatusResponse(int currentRank, int totalWaiting, int estimatedWaitMinutes, WaitingStatus status)`
  - `status`는 `WaitingStatus` enum → Jackson이 `"WAITING"` / `"CALLED"` 문자열로 직렬화.
- `WaitingService.getStatus()` 반환 타입을 `MyWaitingStatusResponse`로 변경, `entry.getStatus()`를 포함해 반환.
  - 기존 동작 유지: 상태가 `WAITING`/`CALLED`가 아니면 `WaitingNotFoundException`(404).
- `WaitingController.getStatus()` 반환 타입을 `MyWaitingStatusResponse`로 변경.
- 기존 `WaitingStatusResponse`는 매장 집계용(2·3번)으로 **그대로 유지** → SSE/매장 경로 무영향.

영향:
- 컴파일: `getStatus` 경로만. 매장 집계 call site는 불변.
- 테스트: `WaitingServiceTest`의 `getStatus` 케이스만 새 타입 + `status` 검증으로 수정.

### 2. 프론트엔드 — 호출됨 페이지

- 신규 라우트 `/waiting/:waitingId/called` → `WaitingCalledPage`.
- 표시: "입장해 주세요!" + 웨이팅 번호 (세션/스토어에서 복원).
- 버튼:
  - **[웨이팅 취소]** → 기존 `/waiting/{id}/cancel` 흐름 재사용(다른 페이지와 동일).
  - **[미루기]** → `disabled` (향후 기능 자리만).

### 3. 프론트엔드 — 공통 SSE 훅

- 신규 `useWaitingSse(waitingId, storeId, { onUpdated, onCalled, enabled })` 추출.
  - EventSource 연결·재연결(최대 3회) — 기존 `WaitingStatusPage`의 SSE 로직을 이관.
  - `waiting-updated` 수신 → `onUpdated()` 호출.
  - 본인(`waitingId` 일치) `waiting-called` 수신 → `onCalled()` 호출.
  - 연결 상태를 반환(배지 표시에 사용).
- 사용처:
  - **WaitingConfirmPage**: 훅 구독, `onCalled` → `navigate('/waiting/{id}/called')`.
  - **WaitingStatusPage**: 훅 구독, `onUpdated` → 순번 재조회, `onCalled` → `navigate('/waiting/{id}/called')`. 기존 **모달 제거**, 진단용 `console.log` 제거.

### 4. 새로고침 견고성 (status 필드 활용)

- **ConfirmPage / StatusPage**: 초기 `getWaiting` 응답의 `status === 'CALLED'`면 즉시 `/called`로 이동.
- **CalledPage**: 같은 훅 구독.
  - 초기 `getWaiting`이 404(입장완료/취소/노쇼)거나, SSE `waiting-updated` 이후 재조회 시 더 이상 `CALLED`가 아니면 → "웨이팅 종료" 화면 표시.
  - `status === 'CALLED'`면 호출됨 화면 유지.

## 동작 흐름

```
등록 → ConfirmPage(SSE) ─┐
실시간 보기 → StatusPage(SSE) ─┤── 본인 waiting-called / 로드 시 status=CALLED ──→ /called
새로고침 시 status=CALLED ─────┘                                                (취소 | 미루기[disabled])

/called 에서 입장완료·취소·노쇼 발생 → getWaiting 404 또는 status≠CALLED → "웨이팅 종료" 화면
```

## 영향 받는 파일 (예상)

백엔드:
- `application/dto/MyWaitingStatusResponse.java` (신규)
- `application/WaitingService.java` (getStatus 반환 타입)
- `presentation/WaitingController.java` (getStatus 반환 타입)
- `test/.../WaitingServiceTest.java` (getStatus 케이스)

프론트:
- `pages/WaitingCalledPage.tsx` (신규)
- `hooks/useWaitingSse.ts` (신규)
- `pages/WaitingConfirmPage.tsx` (훅 구독 + 초기 CALLED 리다이렉트)
- `pages/WaitingStatusPage.tsx` (훅으로 이관, 모달/로그 제거)
- `App.tsx` (라우트 추가)
- `api/waiting.ts` (`WaitingStatusResponse` 타입에 `status` 추가)

## 테스트 / 검증

- 백엔드: `WaitingServiceTest` getStatus가 `status` 포함 반환 확인. `./gradlew test` 통과.
- 프론트: `npx tsc --noEmit` 통과.
- 수동: 등록완료/실시간/새로고침 각 화면에서 점주 호출 시 본인만 `/called`로 이동, 타 손님 미이동 확인. (QRWait_TASKS 6-1 통합 테스트 항목과 연계)
