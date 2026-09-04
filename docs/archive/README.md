# 📦 아카이브 — 역사 기록 전용

> ## ⚠️ 이 디렉터리의 문서를 구현 근거로 사용하지 마세요.
>
> 여기 있는 문서는 **2026-04-03 시점의 기획/설계 기록**이며, 현행 구현과 **여러 곳에서 어긋납니다.**
> 지금 무엇이 맞는지는 **코드**와 [`backend/CLAUDE.md`](../../backend/CLAUDE.md), 그리고 [`docs/superpowers/specs/`](../superpowers/specs/)의 **최신 날짜 스펙**을 보세요.
> 문서 권위 서열은 루트 [`CLAUDE.md`](../../CLAUDE.md)에 정의되어 있습니다.

이 문서들을 남겨 두는 이유는 **제품이 어떤 판단을 거쳐 지금 모습이 되었는지**를 보여주기 위해서입니다. "현재 스펙"이 아니라 "당시 기록"으로만 읽어 주세요.

| 파일 | 내용 | 시점 |
|------|------|-----|
| `QRWait_PRD_v2.0.md` | 제품 요구사항 (Phase 2 = 점주 B2B) | 2026-04-03, 상태 `Draft` |
| `QRWait_TRD_v2.0.md` | 기술 요구사항 | 2026-04-03, 상태 `Draft` |
| `QRWait_TASKS_v2.0.md` | 구현 체크리스트 | 2026-04-03 |
| `superpowers/2026-04-23-sms-notification-*.md` | SMS 알림 1차 설계 | **폐기** — `2026-07-03` 스펙으로 대체됨 |

---

## 현행 구현과 다른 지점 (확인된 것)

아카이브 문서를 참조해야 한다면, 최소한 아래는 **틀렸다고 알고** 읽으세요.

### PRD v2.0

| 위치 | 문서의 주장 | 실제 구현 |
|------|-----------|----------|
| FR-01 | 손님 입력 항목이 **이름(닉네임)** | **전화번호** (`RegisterWaitingRequest.phoneNumber`, `010-XXXX-XXXX` 정규식). 이름 컬럼은 존재하지 않음 |
| FR-01 | 같은 기기 중복 등록 방지 | 프론트 `localStorage` 세션 복원만 있고, **서버 측 차단은 없음** |
| FR-03 | 비밀번호 이메일 재설정 (Phase 2 범위) | **미구현** |
| FR-04 | 매장 정보에 **업종** 입력 | `stores` 테이블에 업종 컬럼 없음 (`name`, `address`, `status`) |
| FR-06 | 노쇼 → **CANCELLED** 처리 | `WaitingStatus.NO_SHOW` 가 **별도 상태**로 존재 |
| FR-08 | 점주에게 **브라우저 푸시 알림** 발송 | Web Push 아님. SSE 이벤트 `alert-threshold-reached` |
| Out of Scope | SMS/카카오 알림 → **Phase 3** | **SMS는 이미 구현·머지 완료** (`shared/sms/`, PR #1) |

### TRD v2.0

| 위치 | 문서의 주장 | 실제 구현 |
|------|-----------|----------|
| §1 레이어 표 | Cache: Redis 7 — **SSE Emitter 관리** | `SseEmitterRegistry`는 **인메모리 `ConcurrentHashMap`**. Redis는 Refresh Token 전용 (단일 인스턴스 전제) |
| §1 / 스택 | **React 18** | **React 19.2** |
| §6 SSE 예시 | `{"visitorName": "이민지", …}` | 존재하지 않는 필드 (전화번호 기반) |
| **§7 패키지 구조** | `com.qrwait/domain/model/`, `application/usecase/XxxUseCase` | ❌ **완전히 다름.** `com.qrwait.api/{owner,store,waiting}/{domain,application,infrastructure,presentation}` 애그리거트별 4계층. **UseCase 타입은 존재하지 않음** (2026-04-09 `eadfd34`에서 제거, `BACKEND_DECISIONS.md` ADR-003 참조) |

> **§7이 가장 위험합니다.** 현행 규칙(`backend/CLAUDE.md` §2)과 정면으로 충돌합니다. 백엔드 패키지 위치는 **언제나 `backend/CLAUDE.md`를 따르세요.**

---

## 그럼 현행 문서는 어디에 있나

| 알고 싶은 것 | 볼 곳 |
|------------|------|
| 백엔드 아키텍처 규칙 (패키지 위치·계층 책임) | [`backend/CLAUDE.md`](../../backend/CLAUDE.md) |
| 설계 의사결정과 그 번복 이력 | [`backend/BACKEND_DECISIONS.md`](../../backend/BACKEND_DECISIONS.md) |
| 개별 기능의 현행 설계 | [`docs/superpowers/specs/`](../superpowers/specs/) — 파일명 앞 **날짜가 최신인 것** |
| 기능별 구현 절차 | [`docs/superpowers/plans/`](../superpowers/plans/) |
| 동작 검증 시나리오 | [`docs/QA-test-cases.md`](../QA-test-cases.md) |
