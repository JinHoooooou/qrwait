# 백엔드 리팩토링 계획

| 항목    | 내용                          |
|-------|-----------------------------|
| 문서 유형 | Refactoring Plan            |
| 버전    | v1.0.0                      |
| 작성일   | 2026년 04월 09일               |
| 대상    | 백엔드 개발자                     |
| 목적    | 현재 레이어별 패키지 구조를 도메인별 구조로 전환 |

> **배경:** Phase 2 구현 완료 후 코드 규모가 커지면서 레이어별 패키지 구조의 한계가 드러났습니다. UseCase 폴더에 클래스가 30개 이상 쌓이고, 관련 코드가 여러 패키지에 분산되어 응집도가 낮아졌습니다. 이를 해결하기 위해 도메인별 패키지 구조로
> 전환합니다.

---

## 변경 사항 요약

| # | 항목            | 변경 전                                                             | 변경 후                               |
|---|---------------|------------------------------------------------------------------|------------------------------------|
| 1 | 패키지 구조        | 레이어별 (`application`, `domain`, `infrastructure`, `presentation`) | 도메인별 (`owner`, `store`, `waiting`) |
| 2 | UseCase 인터페이스 | 인터페이스 + 구현체 분리                                                   | 구현 클래스만 유지                         |
| 3 | UseCase 단위    | 클래스당 메서드 1개 (단일 책임)                                              | 도메인별 Service로 통합 (멀티 메서드)          |
| 4 | 도메인 서비스       | `domain/service/` 패키지 (현재 비어 있음)                                 | 제거 — 도메인 객체 내부 메서드로 흡수             |
| 5 | 도메인 간 참조      | ID 참조 유지                                                         | ID 참조 유지 (변경 없음)                   |

---

## 의사결정 근거

### 1. 도메인별 패키지 구조 (Vertical Slice)

레이어별 구조는 기능이 추가될수록 특정 폴더에 클래스가 집중되고, 하나의 기능을 이해하려면 여러 패키지를 오가야 합니다. 도메인별로 나누면 관련 코드가 한 패키지에 모여 응집도가 높아집니다.

도메인 경계는 다음 세 가지로 정합니다.

- `owner` — 점주 계정/인증 (회원가입, 로그인, 토큰)
- `store` — 매장 운영 (매장 정보, 설정, 상태 관리)
- `waiting` — 웨이팅 처리 (등록, 호출, 입장, 노쇼, 취소)

**도메인 간 의존 방향은 단방향으로만 허용합니다.**

```
waiting → store  (웨이팅 등록 시 매장 상태 조회)
store   → owner  (소유권 검증 시 ownerId 참조)
owner   → store  (가입 시 Store 생성, 단방향)
```

역방향 의존은 금지합니다. 예를 들어 `store`가 `waiting`을 참조하면 안 됩니다.

### 2. UseCase 인터페이스 제거

Controller가 UseCase 인터페이스에 의존하는 구조는 구현체 교체 가능성이 실질적으로 없는 상황에서 불필요한 추상화입니다. 파일 수가 2배로 늘어나고 코드 탐색 비용이 증가합니다. Mockito의 subclass mock으로 구체 클래스도 충분히 테스트
가능합니다.

### 3. 단일 UseCase → 도메인별 Service

단일 책임 UseCase는 UseCase 자체의 비즈니스 로직이 복잡할 때 빛을 발합니다. 현재 대부분의 UseCase는 단순 CRUD에 가까운 로직을 담고 있으며, Controller가 UseCase를 10개 이상 주입받아 오히려 가독성을 해칩니다. 도메인별
Service로 통합하면 Controller 의존성이 단순해집니다.

### 4. 도메인 서비스 제거

현재 `WaitingDomainService`는 `.gitkeep`만 있고 비어 있습니다. 도메인 로직이 이미 도메인 객체 안에 잘 캡슐화되어 있습니다 (`Store.changeStatus()`,
`StoreSettings.calculateEstimatedWait()`, `WaitingEntry.call()` 등). 별도 도메인 서비스 없이도 충분히 표현 가능하므로 제거합니다.

### 5. 도메인 간 Service 호출 방식

도메인 간 협력이 필요한 경우 (예: `WaitingManagementService`에서 ownerId → storeId 변환), **Application Service가 다른 도메인의 Service를 직접 호출**합니다.

```java
// waiting/application/WaitingManagementService.java
@RequiredArgsConstructor
public class WaitingManagementService {
    private final WaitingRepository waitingRepository;
    private final StoreService storeService;   // store 도메인 Service 주입

    public List<OwnerWaitingResponse> getWaitingList(UUID ownerId) {
        UUID storeId = storeService.getMyStore(ownerId).storeId();
        return waitingRepository.findActiveByStoreId(storeId)...
    }
}
```

Repository를 직접 넘나드는 것보다 Service를 통해 접근하므로, 각 도메인의 비즈니스 규칙(소유권 검증 등)이 Service 안에서 일관되게 유지됩니다. 단, 의존 방향은 반드시 `waiting → store` 단방향으로만 허용합니다.

### 6. 도메인 간 참조 방식 (ID 참조 유지)

Owner와 Store는 변경의 이유가 다릅니다. Owner의 관심사는 인증/계정이고, Store의 관심사는 운영/웨이팅입니다. 함께 변경되지 않으므로 서로 다른 애그리거트로 취급합니다. 다른 애그리거트는 객체를 직접 참조하지 않고 ID로만 참조합니다. 도메인 객체 간
협력이 필요한 경우 Application Service에서 각 Repository를 통해 조율합니다.

---

## 목표 패키지 구조

```
com.qrwait.api
├── owner/
│   ├── domain/
│   │   ├── Owner.java
│   │   ├── InvalidCredentialsException.java
│   │   ├── DuplicateEmailException.java
│   │   └── OwnerRepository.java
│   ├── application/
│   │   ├── OwnerService.java          # 회원가입, 로그인, 로그아웃, 토큰 갱신
│   │   └── dto/                       # SignUpRequest/Response, LoginRequest/Response, AccessTokenResponse
│   ├── infrastructure/
│   │   ├── OwnerJpaEntity.java
│   │   ├── OwnerJpaRepository.java
│   │   └── OwnerRepositoryImpl.java
│   └── presentation/
│       └── AuthController.java
│
├── store/
│   ├── domain/
│   │   ├── Store.java
│   │   ├── StoreSettings.java
│   │   ├── StoreStatus.java
│   │   ├── StoreNotFoundException.java
│   │   ├── StoreNotAvailableException.java
│   │   ├── StoreRepository.java
│   │   └── StoreSettingsRepository.java
│   ├── application/
│   │   ├── StoreService.java          # 매장 조회, 수정, 상태 변경
│   │   ├── StoreSettingsService.java  # 설정 조회, 수정
│   │   └── dto/                       # StoreResponse, StoreSettingsResponse, UpdateStoreStatusRequest 등
│   ├── infrastructure/
│   │   ├── StoreJpaEntity.java
│   │   ├── StoreJpaRepository.java
│   │   ├── StoreRepositoryImpl.java
│   │   ├── StoreSettingsJpaEntity.java
│   │   ├── StoreSettingsJpaRepository.java
│   │   └── StoreSettingsRepositoryImpl.java
│   └── presentation/
│       ├── StoreController.java       # 손님용 매장 조회, QR
│       └── OwnerStoreController.java  # 점주용 매장/설정/상태 관리
│
├── waiting/
│   ├── domain/
│   │   ├── WaitingEntry.java
│   │   ├── WaitingStatus.java
│   │   ├── WaitingNotFoundException.java
│   │   └── WaitingRepository.java
│   ├── application/
│   │   ├── WaitingService.java        # 등록, 취소, 상태 조회 (손님용)
│   │   ├── WaitingManagementService.java  # 호출, 입장, 노쇼, 목록/통계 (점주용)
│   │   └── dto/                       # RegisterWaitingRequest/Response, OwnerWaitingResponse 등
│   ├── infrastructure/
│   │   ├── WaitingEntryJpaEntity.java
│   │   ├── WaitingEntryJpaRepository.java
│   │   └── WaitingRepositoryImpl.java
│   └── presentation/
│       ├── WaitingController.java         # 손님용
│       └── OwnerWaitingController.java    # 점주용
│
└── shared/
    ├── security/
    │   ├── JwtTokenProvider.java
    │   ├── JwtAuthFilter.java
    │   └── SecurityConfig.java
    ├── sse/
    │   ├── SseEmitterRegistry.java
    │   └── WaitingSseService.java
    ├── redis/
    │   └── RefreshTokenRepository.java
    └── web/
        ├── WebConfig.java
        └── GlobalExceptionHandler.java
```

---

## Service 메서드 구성

### OwnerService

```java
// owner/application/OwnerService.java
public SignUpResponse signUp(SignUpRequest request)
public LoginResponse login(LoginRequest request)
public void logout(UUID ownerId)
public String refresh(String refreshToken)
```

### StoreService

```java
// store/application/StoreService.java
public StoreResponse getMyStore(UUID ownerId)
public StoreResponse updateStoreInfo(UUID ownerId, UpdateStoreInfoRequest request)
public StoreResponse updateStoreStatus(UUID ownerId, UpdateStoreStatusRequest request)
```

### StoreSettingsService

```java
// store/application/StoreSettingsService.java
public StoreSettingsResponse getSettings(UUID ownerId)
public StoreSettingsResponse updateSettings(UUID ownerId, UpdateStoreSettingsRequest request)
```

### WaitingService

```java
// waiting/application/WaitingService.java
public RegisterWaitingResponse register(UUID storeId, RegisterWaitingRequest request)
public WaitingStatusResponse getStatus(UUID waitingId)
public void cancel(UUID waitingId)
```

### WaitingManagementService

```java
// waiting/application/WaitingManagementService.java
public List<OwnerWaitingResponse> getWaitingList(UUID ownerId)
public DailySummaryResponse getDailySummary(UUID ownerId)
public void call(UUID ownerId, UUID waitingId)
public void enter(UUID ownerId, UUID waitingId)
public void noShow(UUID ownerId, UUID waitingId)
```

---

## 리팩토링 체크리스트

> 아래 순서대로 진행합니다. 각 단계 완료 후 전체 테스트를 실행하여 기존 동작이 유지되는지 확인합니다.

### STEP 1. `shared` 패키지 생성 및 공통 클래스 이동

- [x] `shared/security/` 생성 → `JwtTokenProvider`, `JwtAuthFilter`, `SecurityConfig` 이동
- [x] `shared/sse/` 생성 → `SseEmitterRegistry`, `WaitingSseService` 이동
- [x] `shared/redis/` 생성 → `RefreshTokenRepository` 이동
- [x] `shared/web/` 생성 → `WebConfig`, `GlobalExceptionHandler`, `ErrorResponse` 이동
- [x] 이동 후 import 경로 수정
- [x] 전체 테스트 실행 확인

**완료 후:**

- [x] 본 문서 STEP 1 체크박스 업데이트
- 커밋 메시지: `refactor: move shared infrastructure to shared package`

### STEP 2. `waiting` 도메인 패키지 구성

> 의존성이 가장 적은 도메인부터 시작합니다.

- [x] `waiting/domain/` 생성 → `WaitingEntry`, `WaitingStatus`, `WaitingNotFoundException`, `WaitingRepository` 이동
- [x] `waiting/infrastructure/` 생성 → `WaitingEntryJpaEntity`, `WaitingEntryJpaRepository`, `WaitingRepositoryImpl` 이동
- [x] `waiting/application/dto/` 생성 → 웨이팅 관련 DTO 이동 (`RegisterWaitingRequest/Response`, `WaitingStatusResponse`, `OwnerWaitingResponse`,
  `DailySummaryResponse`)
- [x] `WaitingService` 클래스 생성 — `RegisterWaitingUseCase`, `GetWaitingStatusUseCase`, `CancelWaitingUseCase` 통합
- [x] `WaitingManagementService` 클래스 생성 — `CallWaitingUseCase`, `EnterWaitingUseCase`, `NoShowWaitingUseCase`, `GetOwnerWaitingListUseCase`,
  `GetDailySummaryUseCase` 통합
- [x] 기존 UseCase 인터페이스 및 구현체 제거
- [x] `waiting/presentation/` 생성 → `WaitingController` 이동, `OwnerWaitingController` 분리 생성
- [x] 관련 테스트 클래스 이동 및 import 수정
- [x] 전체 테스트 실행 확인

**완료 후:**

- [x] 본 문서 STEP 2 체크박스 업데이트
- 커밋 메시지: `refactor: reorganize waiting domain into vertical slice package`

### STEP 3. `store` 도메인 패키지 구성

- [x] `store/domain/` 생성 → `Store`, `StoreSettings`, `StoreStatus`, `StoreNotFoundException`, `StoreNotAvailableException`, `StoreRepository`,
  `StoreSettingsRepository` 이동
- [x] `store/infrastructure/` 생성 → Store/StoreSettings 관련 JPA 클래스 이동
- [x] `store/application/dto/` 생성 → 매장 관련 DTO 이동 (`StoreResponse`, `StoreSettingsResponse`, `UpdateStoreInfoRequest`, `UpdateStoreSettingsRequest`,
  `UpdateStoreStatusRequest`)
- [x] `StoreService` 클래스 생성 — `GetMyStoreUseCase`, `UpdateStoreInfoUseCase`, `UpdateStoreStatusUseCase`, `GetStoreByIdUseCase`,
  `GenerateQrImageUseCase`, `GetStoreWaitingStatusUseCase` 통합
- [x] `StoreSettingsService` 클래스 생성 — `GetStoreSettingsUseCase`, `UpdateStoreSettingsUseCase` 통합
- [x] 기존 UseCase 인터페이스 및 구현체 제거
- [x] `store/presentation/` 생성 → `StoreController` 이동, `OwnerStoreController` 분리 생성
- [x] 관련 테스트 클래스 이동 및 import 수정
- [x] 전체 테스트 실행 확인

**완료 후:**

- [x] 본 문서 STEP 3 체크박스 업데이트
- 커밋 메시지: `refactor: reorganize store domain into vertical slice package`

### STEP 4. `owner` 도메인 패키지 구성

- [ ] `owner/domain/` 생성 → `Owner`, `InvalidCredentialsException`, `DuplicateEmailException`, `OwnerRepository` 이동
- [ ] `owner/infrastructure/` 생성 → `OwnerJpaEntity`, `OwnerJpaRepository`, `OwnerRepositoryImpl` 이동
- [ ] `owner/application/dto/` 생성 → 인증 관련 DTO 이동 (`SignUpRequest/Response`, `LoginRequest/Response`, `AccessTokenResponse`)
- [ ] `OwnerService` 클래스 생성 — `SignUpOwnerUseCase`, `LoginOwnerUseCase`, `LogoutOwnerUseCase`, `RefreshTokenUseCase` 통합
- [ ] 기존 UseCase 인터페이스 및 구현체 제거
- [ ] `owner/presentation/` 생성 → `AuthController` 이동
- [ ] 관련 테스트 클래스 이동 및 import 수정
- [ ] 전체 테스트 실행 확인

**완료 후:**

- [ ] 본 문서 STEP 4 체크박스 업데이트
- 커밋 메시지: `refactor: reorganize owner domain into vertical slice package`

### STEP 5. 기존 레이어별 패키지 제거

- [ ] `application/` 패키지 완전 제거 (비어 있는지 확인 후)
- [ ] `domain/` 패키지 완전 제거
- [ ] `infrastructure/` 패키지 완전 제거
- [ ] `presentation/` 패키지 완전 제거 (security는 shared로 이동 완료 확인)
- [ ] 빌드 성공 확인
- [ ] 전체 테스트 실행 확인

**완료 후:**

- [ ] 본 문서 STEP 5 체크박스 업데이트
- 커밋 메시지: `refactor: remove legacy layer-based package structure`

### STEP 6. `CreateStoreUseCase` 제거

> 현재 `CreateStoreUseCase`는 Phase 2에서 `SignUpOwnerUseCase`로 대체되었습니다. 시뮬레이션용으로만 남아 있어 제거합니다.

- [ ] `StoreController`에서 `POST /api/stores` 엔드포인트 제거
- [ ] `CreateStoreUseCase`, `CreateStoreUseCaseImpl`, `CreateStoreRequest/Response` 제거
- [ ] 관련 테스트 제거
- [ ] 전체 테스트 실행 확인

**완료 후:**

- [ ] 본 문서 STEP 6 체크박스 업데이트
- 커밋 메시지: `refactor: remove CreateStoreUseCase replaced by SignUpOwnerUseCase`

### STEP 7. 최종 검증

- [ ] 빌드 성공 확인 (`./gradlew build`)
- [ ] 전체 테스트 통과 확인 (`./gradlew test`)
- [ ] `BACKEND_DECISIONS.md`에 리팩토링 결정 사항 ADR 추가

**완료 후:**

- [ ] 본 문서 STEP 7 체크박스 업데이트
- 커밋 메시지: `refactor: complete vertical slice architecture migration`

---

## 주의사항

**도메인 간 의존 방향을 반드시 지킵니다.**
`waiting → store`, `store → owner` 단방향만 허용합니다. 역방향 참조가 생기면 설계를 다시 검토합니다.

**각 STEP 완료 후 테스트를 실행합니다.**
한 번에 모든 패키지를 옮기지 않고 STEP 단위로 이동 후 확인합니다. 테스트가 깨지면 해당 STEP 내에서 해결합니다.

**테스트 클래스도 같이 이동합니다.**
`src/test/`의 패키지 구조도 `src/main/`과 동일하게 맞춥니다.
