# 설계 — 백엔드 액터별 서브패키지 분리 (손님 / 점주)

| 항목     | 내용                                   |
|--------|--------------------------------------|
| 문서 유형  | Design Spec                          |
| 작성일    | 2026-06-30                           |
| 대상     | `backend/` — `waiting`, `store` 애그리거트 |
| 동작 변경  | 없음 (순수 리팩토링: 이동·리네임·분할)              |

---

## 1. 배경 & 문제

`waiting` 패키지는 `application/`·`presentation/`가 평평해서, 손님용과 점주용 코드가
같은 폴더에 나란히 섞여 있다.

```
waiting/
  application/   WaitingService(손님)  WaitingManagementService(점주)     ← 섞임
  application/dto/  RegisterWaitingRequest(손님) … OwnerWaitingResponse(점주) …  ← 섞임
  presentation/  WaitingController(손님)  OwnerWaitingController(점주)      ← 섞임
```

패키지를 열었을 때 두 액터의 코드가 한눈에 구분되지 않아 가독성이 떨어진다.
`store` 패키지도 동일한 구조(`StoreController` vs `OwnerStoreController`)다.

### 도메인 관점 정리 (왜 "도메인 분리"가 아닌가)

- 손님은 **모델링된 도메인 엔티티가 아니다.** 계정·식별자가 없고, 남기는 것은
  `WaitingEntry`에 붙는 `phoneNumber`라는 값(value)뿐인 **익명 외부 액터**다.
- 점주만 실제 도메인 엔티티(`Owner`)를 가진다.
- 도메인 협력 사슬은 **`Owner → Store → WaitingEntry`** 하나이며, 손님은 그래프 바깥에서
  `WaitingEntry` 생성을 트리거할 뿐이다.
- 따라서 손님/점주는 **별개 애그리거트가 아니라 같은 애그리거트(`Store`, `WaitingEntry`)를
  쓰는 별개의 액터**다. 분리해야 할 축은 **도메인이 아니라 use case(application)·presentation**이다.

가짜 `Customer` 도메인을 만들면 백엔드 가이드가 경고하는 "빈 껍데기 도메인"이 되므로 만들지 않는다.

---

## 2. 목표 & 원칙

- 손님·점주가 **함께 쓰는 애그리거트**(`waiting`, `store`)에서 `application`·`presentation`을
  `customer/` · `management/` 서브패키지로 분리한다.
- `domain/`·`infrastructure/`는 **공유 커널로 그대로 유지**한다 (애그리거트는 하나, 테이블도 하나).
- **동작 변경 없음.** API 경로·요청/응답 스키마·비즈니스 로직 동일. 순수 이동·리네임·분할이며
  기존 테스트(필요 시 패키지만 이동)가 회귀를 보증한다.
- 단일 액터 애그리거트는 분리하지 않는다 (YAGNI).

**적용 규칙:** *"두 액터가 모두 쓰는 애그리거트만 `customer/`·`management/`로 가른다."*

---

## 3. 목표 구조

### 3.1 waiting (서비스가 이미 둘로 나뉘어 있음 → 대부분 파일 이동)

```
waiting/
  domain/            WaitingEntry, WaitingStatus, WaitingRepository,
                     DailySummary, WaitingNotFoundException, event/      (공유, 변경 없음)
  infrastructure/    WaitingEntryJpaEntity, WaitingEntryJpaRepository,
                     WaitingRepositoryImpl                               (공유, 변경 없음)
  customer/
    application/     WaitingService
    presentation/    WaitingController
    dto/             RegisterWaitingRequest, RegisterWaitingResponse,
                     MyWaitingStatusResponse, WaitingStatusResponse
  management/
    application/     WaitingManagementService
    presentation/    OwnerWaitingController
    dto/             OwnerWaitingResponse, DailySummaryResponse, TodayWaitingResponse
```

> `DailySummary`는 도메인 VO이므로 사용 액터가 점주뿐이어도 `domain/`에 남긴다.
> `WaitingStatusResponse`는 손님용 DTO이며 `StoreController`(손님)가 사용한다 (아래 참조).

### 3.2 store (서비스 분할 필요)

```
store/
  domain/            Store, StoreSettings, StoreStatus,
                     StoreRepository, StoreSettingsRepository,
                     StoreNotFoundException, StoreNotAvailableException, event/   (공유, 변경 없음)
  infrastructure/    StoreJpaEntity, StoreJpaRepository, StoreRepositoryImpl,
                     StoreSettingsJpaEntity, StoreSettingsJpaRepository,
                     StoreSettingsRepositoryImpl                                  (공유, 변경 없음)
  application/dto/   StoreResponse                          ← 손님·점주 공유 DTO (애그리거트 중립 위치)
  customer/
    application/     StoreViewService                       ← StoreService에서 분리
    presentation/    StoreController
  management/
    application/     StoreService(점주), StoreSettingsService
    presentation/    OwnerStoreController
    dto/             StoreSettingsResponse, UpdateStoreInfoRequest,
                     UpdateStoreSettingsRequest, UpdateStoreStatusRequest
```

### 3.3 owner (점주 단일 액터 → 분리하지 않음, 현 구조 유지)

```
owner/
  domain/            Owner, OwnerRepository, DuplicateEmailException, InvalidCredentialsException
  application/       OwnerService, dto/(LoginRequest, LoginResponse, SignUpRequest,
                     SignUpResponse, AccessTokenResponse)
  infrastructure/    OwnerJpaEntity, OwnerJpaRepository, OwnerRepositoryImpl
  presentation/      AuthController
```

### 3.4 shared (현 구조 유지)

`qr/`, `redis/`, `security/`, `sse/`, `web/` — 횡단 관심사이므로 변경하지 않는다.

---

## 4. 핵심 결정

### 4.1 `StoreService` 분할

현재 `StoreService`는 한 클래스에 손님용과 점주용 메서드가 섞여 있다. 둘로 나눈다.

| 신규 위치                                 | 메서드                                         |
|--------------------------------------|---------------------------------------------|
| `store/customer/application/StoreViewService` | `getStoreById`, `generateQrImage`           |
| `store/management/application/StoreService`   | `getMyStore`, `updateStoreInfo`, `updateStoreStatus` |

- `toResponse(Store)` 매핑 헬퍼는 둘 다 단순하므로 각 서비스가 각자 보유한다 (공유 추상 만들지 않음).
- 의존성: `StoreViewService`는 `StoreRepository`, `QrCodeGenerator`, `app.base-url` 사용.
  `StoreService`(점주)는 `StoreRepository`, `ApplicationEventPublisher` 사용.

### 4.2 공유 DTO(`StoreResponse`) 위치

`StoreResponse`는 손님(`getStoreById`)과 점주(`getMyStore`/`update*`)가 모두 반환한다.
어느 액터에도 치우치지 않도록 **`store/application/dto/`(애그리거트 중립 위치)** 에 유지한다.
액터 전용 DTO만 `customer/dto`·`management/dto`로 옮긴다.

### 4.3 단일 액터 애그리거트(`owner`)는 분리하지 않음

`owner`는 점주만 사용하므로 customer/management 분리가 무의미하다. 현 구조를 유지한다.

### 4.4 패키지명 `customer` / `management`

점주 액터 컨텍스트를 `owner`로 부르면 `owner` 애그리거트와 혼동되므로 `management`를 채택한다.

### 4.5 교차 애그리거트 의존 (현행 유지)

- `StoreController`(손님)는 `WaitingService`(손님)와 `WaitingStatusResponse`를 주입/사용해
  `GET /api/stores/{storeId}/waitings/status`를 제공한다. 이동 후에는
  `store/customer` → `waiting/customer`를 참조한다 (presentation이 두 애그리거트의
  application을 조립하는 것은 허용).

---

## 5. 작업 순서 (무행동 변경 보장)

1. **waiting 이동**: `customer/`·`management/` 서브패키지 생성 → 서비스·컨트롤러·DTO 이동,
   `package` 선언과 `import` 갱신.
2. **store 이동 & 분할**: 서브패키지 생성 → `StoreService`를 `StoreViewService`(customer)와
   `StoreService`(management)로 분할, `StoreSettingsService`·`OwnerStoreController` 이동,
   액터 전용 DTO 이동, `StoreResponse`는 중립 위치 유지.
3. **테스트 이동**: 대응 테스트의 패키지를 동일 구조로 이동
   (`StoreControllerTest`, `OwnerStoreControllerTest`, `WaitingControllerTest`,
   `OwnerWaitingControllerTest`, `WaitingServiceTest`, `WaitingManagementServiceTest`,
   `StoreServiceTest` 등). `StoreServiceTest`는 분할에 맞춰 손님/점주 테스트로 정리.
4. **검증**: `./gradlew build` (전체 테스트) 그린 확인. SonarLint/컴파일 경고 없음 확인.
5. **커밋** (한글 메시지).

---

## 6. 검증 기준 (Acceptance)

- [ ] `./gradlew build` 전체 통과 (기존 테스트 동작 동일).
- [ ] 모든 API 경로·HTTP 상태·응답 바디가 리팩토링 전과 동일.
- [ ] `domain/`·`infrastructure/` 패키지 내용물 변경 없음.
- [ ] `waiting`·`store`의 `application`·`presentation`이 `customer/`·`management/`로 분리됨.
- [ ] `owner`·`shared` 구조 변경 없음.
- [ ] 액터 전용 DTO는 서브패키지에, 공유 DTO(`StoreResponse`)는 중립 위치에 존재.

---

## 7. 범위 밖 (별도 스펙)

- **프론트엔드 테스트 도입** (Vitest/RTL) — 성격이 달라 이 작업 완료 후 별도 브레인스토밍·스펙으로 진행.
- 기능 추가/변경 일체 (Phase 2 잔여 구멍, Phase 3 신규 기능 등).
