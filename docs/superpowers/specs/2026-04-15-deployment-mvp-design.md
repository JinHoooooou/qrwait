# 배포 MVP 제품 설계 스펙

| 항목    | 내용                                                  |
|-------|-----------------------------------------------------|
| 문서 유형 | Product Design Spec                                 |
| 작성일   | 2026년 04월 15일                                       |
| 연관 문서 | Phase 3 구현 계획 (2026-04-15-phase3-implementation.md) |
| 상태    | Approved                                            |

---

## 배경 및 목적

Phase 2 기능 완성 이후 실제 배포를 위해 필요한 최소 요구사항을 정의한다. Phase 3 전체 구현 이전에 우선 배포 가능한 상태를 만드는 것이 목표다.

---

## 스코프

| 항목             | 포함 여부 | 비고                         |
|----------------|-------|----------------------------|
| QR 인쇄 페이지      | ✅     | 신규 설계                      |
| 오늘의 웨이팅 이력 페이지 | ✅     | 신규 설계                      |
| 전화번호 전환        | ✅     | Phase 3 계획 Task 1~6 그대로 구현 |

---

## 추가 라우트

```
/owner/qr-print                    → QR 인쇄 페이지
/owner/history                     → 오늘의 웨이팅 이력 (전체 탭)
/owner/history?status=ENTERED      → 입장 탭으로 진입
/owner/history?status=NO_SHOW      → 노쇼 탭으로 진입
/owner/history?status=CANCELLED    → 취소 탭으로 진입
```

---

## Section 1 — 대시보드 변경

### 헤더

로그아웃 버튼 옆에 "QR 인쇄" 버튼 추가. 클릭 시 `/owner/qr-print`로 이동.

```
[QR 인쇄]  [로그아웃]
```

### 통계 카드

| 카드              | 변경 내용                                  |
|-----------------|----------------------------------------|
| "등록" → **"전체"** | 이름 변경 + 클릭 시 `/owner/history` (전체 탭)   |
| 입장              | 클릭 시 `/owner/history?status=ENTERED`   |
| 노쇼              | 클릭 시 `/owner/history?status=NO_SHOW`   |
| 취소              | 클릭 시 `/owner/history?status=CANCELLED` |

통계 카드에 `cursor: pointer` 스타일 적용. 기존 카운트 표시 방식은 유지.

---

## Section 2 — QR 인쇄 페이지

### 라우트

`/owner/qr-print`

### 데이터 소스

| 데이터    | API                                 |
|--------|-------------------------------------|
| QR 이미지 | `GET /api/stores/{storeId}/qr` (기존) |
| 매장명    | `GET /api/owner/stores/me` (기존)     |

`storeId`는 `useOwnerStore`에서 가져온다.

### 화면 레이아웃

```
┌─────────────────────────────┐
│  [← 대시보드]               │  ← 화면 전용 (@media print 숨김)
│                             │
│   ┌─────────────────┐       │
│   │                 │       │
│   │    QR 코드      │       │
│   │   (이미지)      │       │
│   └─────────────────┘       │
│          홍콩반점            │  ← 실제 매장명
│                             │
│     [인쇄 / PDF 저장]        │  ← 화면 전용 (@media print 숨김)
└─────────────────────────────┘
```

### @media print 동작

- "← 대시보드" 버튼, "인쇄 / PDF 저장" 버튼: `display: none`
- QR 이미지 + 매장명만 A4 용지 중앙에 배치
- `window.print()` 호출 → 브라우저 인쇄 / PDF 저장 다이얼로그 처리

### 백엔드 변경

없음. 기존 `GET /api/stores/{storeId}/qr` 엔드포인트 그대로 활용.

---

## Section 3 — 오늘의 웨이팅 이력 페이지

### 라우트

`/owner/history` (쿼리스트링 `?status=ENTERED|NO_SHOW|CANCELLED` 선택적)

### 새 백엔드 API

```
GET /api/owner/stores/me/waitings/today
```

**응답:**

```json
[
  {
    "waitingId": "uuid",
    "waitingNumber": 3,
    "phoneNumber": "010-3333-3333",
    "partySize": 2,
    "status": "CANCELLED",
    "createdAt": "2026-04-15T10:32:00"
  }
]
```

오늘(`DATE(created_at) = CURRENT_DATE`) 등록된 전체 웨이팅을 `waitingNumber` 내림차순으로 반환. 상태 필터 없음 — 프론트엔드에서 탭으로 처리.

### 백엔드 변경 범위

| 파일                               | 변경 내용                                           |
|----------------------------------|-------------------------------------------------|
| `WaitingRepository.java`         | `findAllByStoreIdAndDate(storeId, date)` 메서드 추가 |
| `WaitingEntryJpaRepository.java` | 오늘 날짜 기준 전체 조회 쿼리 추가                            |
| `WaitingRepositoryImpl.java`     | 위 메서드 구현                                        |
| `WaitingManagementService.java`  | `getTodayWaitings(ownerId)` 메서드 추가              |
| `OwnerWaitingController.java`    | `GET /stores/me/waitings/today` 엔드포인트 추가        |

### 응답 DTO

```java
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

### 화면 레이아웃

```
┌────────────────────────────────────────┐
│ [← 대시보드]         오늘의 웨이팅      │
│                                        │
│ [전체] [등록] [입장] [노쇼] [취소]      │
│   ↑                                    │
│   탭 클릭 → ?status= 쿼리 업데이트     │
│   URL 진입 시 쿼리 없으면 "전체" 선택  │
│                                        │
│ #3  010-3333-3333  2명  취소  10:32   │
│ #2  010-2222-2222  1명  입장  10:15   │
│ #1  010-1111-1111  3명  입장  09:55   │
│                                        │
│ (해당 항목 없으면 "없음" 안내 메시지)   │
└────────────────────────────────────────┘
```

### 탭 필터 정의

| 탭  | 포함 상태           |
|----|-----------------|
| 전체 | 모든 상태           |
| 등록 | WAITING, CALLED |
| 입장 | ENTERED         |
| 노쇼 | NO_SHOW         |
| 취소 | CANCELLED       |

### 날짜 확장 고려

현재는 오늘만 조회. 추후 날짜 선택 UI 추가 시 `GET /api/owner/stores/me/waitings/today`를 `GET /api/owner/stores/me/waitings/entries?date=YYYY-MM-DD`로 확장할 것을 고려해 백엔드
메서드 시그니처를 `date` 파라미터 포함으로 설계한다.

---

## 구현 순서

Phase 3 계획(전화번호 전환)이 이 스펙의 Section 2~3에 영향을 주므로 아래 순서로 구현한다.

```
1. Phase 3 Task 1~6 (전화번호 전환)          ← phoneNumber 필드 선행 필요
2. Section 2: QR 인쇄 페이지                  ← 독립적
3. Section 3: 이력 페이지 백엔드 API           ← 1번 완료 후
4. Section 3: 이력 페이지 프론트엔드           ← 3번 완료 후
5. Section 1: 대시보드 변경                   ← 2, 4번 완료 후
```
