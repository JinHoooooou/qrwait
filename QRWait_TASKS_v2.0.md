# TASKS — QR 웨이팅 서비스 Phase 2 (점주 기능)

| 항목    | 내용                                                                  |
|-------|---------------------------------------------------------------------|
| 문서 유형 | Task Checklist                                                      |
| 버전    | v2.0.0                                                              |
| 상태    | Draft                                                               |
| 작성일   | 2026년 04월 03일                                                       |
| 연관 문서 | [PRD v2.0](./QRWait_PRD_v2.0.md) · [TRD v2.0](./QRWait_TRD_v2.0.md) |

> **문서 목적:** PRD/TRD v2.0을 기반으로 에이전트(또는 개발자)가 순서대로 실행할 수 있도록 점주 기능 구현 태스크를 체크리스트 형태로 명세합니다.
> Phase 1 구현 코드(backend/, frontend/)는 이미 존재하므로 **덮어쓰지 않고 확장**하는 방향으로 진행합니다.

---

## 진행 현황 요약

| 섹션                              | 태스크 수  | 예상 소요시간      |
|---------------------------------|--------|--------------|
| PHASE 0. 사전 준비                  | 4      | 45m          |
| PHASE 1. 백엔드 — 도메인 & DB 확장      | 9      | 2h 30m       |
| PHASE 2. 백엔드 — 인증 (JWT)         | 8      | 2h 30m       |
| PHASE 3. 백엔드 — 점주 UseCase & API | 12     | 4h 00m       |
| PHASE 4. 백엔드 — 대시보드 SSE 확장      | 4      | 1h 30m       |
| PHASE 5. 프론트엔드 — 점주 페이지 구현      | 9      | 4h 00m       |
| PHASE 6. 연동 & 통합 테스트            | 5      | 1h 30m       |
| **합계**                          | **51** | **~16h 45m** |

---

## PHASE 0. 사전 준비

> 예상 소요시간: **45m**

### 0-1. 의존성 추가 (build.gradle)

> ⏱ 15m

- [x] `spring-boot-starter-security` 추가
- [x] `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.x) 추가
- [x] `application.yml`에 JWT 설정 추가
  ```yaml
  jwt:
    secret: ${JWT_SECRET}
    access-expiry: 3600      # 1시간 (초)
    refresh-expiry: 604800   # 7일 (초)
  ```

### 0-2. 패키지 구조 확장 생성

> ⏱ 10m | 선행: 0-1

- [x] `presentation/security/` 패키지 생성
- [x] `domain/model/` — `Owner.java`, `StoreSettings.java` 파일 생성 위치 확인
- [x] `domain/repository/` — `OwnerRepository.java`, `StoreSettingsRepository.java` 파일 생성 위치 확인
- [x] `application/usecase/` — 신규 UseCase 파일 생성 위치 확인
- [x] `infrastructure/persistence/` — 신규 JPA Entity 파일 생성 위치 확인

### 0-3. Flyway 마이그레이션 버전 확인

> ⏱ 10m | 선행: 0-1

- [x] 기존 마이그레이션 파일 버전 확인 (V1 ~ V4)
- [x] 마이그레이션 전략 변경: 팀원이 1명이므로 V1 단일 파일로 통합
  - V2~V4 삭제, `V1__init.sql`에 stores + waiting_entries + 시드 데이터 통합
  - 이후 스키마 변경은 V1 업데이트 방식으로 진행 (운영 배포 직전 확정)
  - **1-5 태스크**: V5~V7 생성 대신 V1에 owners, store_settings, stores 변경 추가로 수정

### 0-4. Spring Security 기본 설정 (임시 전체 허용)

> ⏱ 10m | 선행: 0-1

- [x] `SecurityConfig.java` 생성 — 초기에는 모든 경로 허용으로 설정 (JWT Filter 구현 후 단계적으로 적용)
- [x] 기존 손님 API가 Security 추가로 401을 반환하지 않는지 확인
  - `@WebMvcTest`가 `SecurityConfig`를 자동 로드하지 않는 문제 → 두 Controller 테스트에 `@Import(SecurityConfig.class)` 추가로 해결

---

## PHASE 1. 백엔드 — 도메인 & DB 확장

> 예상 소요시간: **2h 30m**

### 1-1. Owner 도메인 모델 구현

> ⏱ 20m | 선행: 0-2

- [x] `domain/model/Owner.java` 생성
    - 필드: `id (UUID)`, `email (String)`, `passwordHash (String)`, `createdAt (LocalDateTime)`
    - 순수 Java 객체 (JPA 어노테이션 없음)
    - 팩토리 메서드: `create(email, passwordHash)`
    - 복원 메서드: `restore(id, email, passwordHash, createdAt)`

### 1-2. StoreSettings 도메인 모델 구현

> ⏱ 20m | 선행: 0-2

- [x] `domain/model/StoreSettings.java` 생성
    - 필드: `id (UUID)`, `storeId (UUID)`, `tableCount (int)`, `avgTurnoverMinutes (int)`, `openTime (LocalTime)`, `closeTime (LocalTime)`,
      `alertThreshold (int)`, `alertEnabled (boolean)`
  - 팩토리 메서드: `createDefault(storeId)` — 기본값으로 생성 (tableCount=5, avgTurnoverMinutes=30, alertThreshold=10, alertEnabled=true)
    - 복원 메서드: `restore(...)`
    - 예상 대기시간 계산 메서드: `calculateEstimatedWait(int aheadCount)` → `avgTurnoverMinutes / tableCount * aheadCount`

### 1-3. Store 도메인 모델 변경

> ⏱ 15m | 선행: 0-2

- [x] `domain/model/StoreStatus.java` Enum 생성
    - 값: `OPEN`, `BREAK`, `FULL`, `CLOSED`
- [x] `domain/model/Store.java` 수정
    - `ownerId (UUID)`, `address (String)`, `status (StoreStatus)` 필드 추가
    - `create(ownerId, name, address)` 팩토리 메서드 수정
  - 도메인 메서드: `changeStatus(StoreStatus newStatus)` 추가 — 불변 설계, 새 Store 객체 반환
  - 상태 전이 제한 없음: CLOSED는 당일 운영 상태이므로 다음 날 OPEN 전환 허용. 상태 전이 제한은 UseCase 레벨에서 처리
  - `StoreJpaEntity`, `CreateStoreUseCaseImpl`, 테스트 4개 — 시그니처 변경에 맞게 수정 (ownerId/address는 1-5 DB 컬럼 추가 전까지 null)

### 1-4. Repository 인터페이스 추가

> ⏱ 15m | 선행: 1-1, 1-2

- [x] `domain/repository/OwnerRepository.java` 생성
    - `save(Owner owner): Owner`
    - `findByEmail(String email): Optional<Owner>`
    - `findById(UUID id): Optional<Owner>`
- [x] `domain/repository/StoreSettingsRepository.java` 생성
    - `save(StoreSettings settings): StoreSettings`
    - `findByStoreId(UUID storeId): Optional<StoreSettings>`
- [x] `domain/repository/StoreRepository.java` 수정
    - `findByOwnerId(UUID ownerId): Optional<Store>` 추가
  - `StoreRepositoryImpl.findByOwnerId()` — 1-5 DB 컬럼 추가 전까지 UnsupportedOperationException

### 1-5. Flyway 마이그레이션 작성

> ⏱ 25m | 선행: 0-3

- [x] V1__init.sql에 통합 (V5~V7 별도 파일 대신)
  - `owners` 테이블 추가
  - `stores` 테이블에 `owner_id`, `address`, `status` 컬럼 추가
  - `store_settings` 테이블 추가
  - 시드 데이터: 개발용 더미 owner(UUID all-zero) + 기존 stores에 owner_id 연결
  - `StoreJpaEntity` TODO 해소 — owner_id, address, status 컬럼 활성화
  - `StoreRepositoryImpl.findByOwnerId()` 실제 구현 완료
- [x] 마이그레이션 실행 후 테이블 생성 확인 (owners, stores, store_settings, waiting_entries)

### 1-6. JPA Entity 추가/수정

> ⏱ 25m | 선행: 1-4, 1-5

- [x] `infrastructure/persistence/OwnerJpaEntity.java` 생성
    - `toDomain()`, `from(Owner)` 변환 메서드
- [x] `infrastructure/persistence/StoreSettingsJpaEntity.java` 생성
    - `toDomain()`, `from(StoreSettings)` 변환 메서드
- [x] `infrastructure/persistence/StoreJpaEntity.java` 수정
  - `ownerId`, `address`, `status` 컬럼 매핑 추가 (1-5에서 완료)
  - `toDomain()`, `from(Store)` 변환 메서드 수정 (1-5에서 완료)

### 1-7. JpaRepository 추가/수정

> ⏱ 15m | 선행: 1-6

- [x] `infrastructure/persistence/OwnerJpaRepository.java` 생성
    - `findByEmail(String email)` 쿼리 메서드
- [x] `infrastructure/persistence/StoreSettingsJpaRepository.java` 생성
    - `findByStoreId(UUID storeId)` 쿼리 메서드
- [x] `infrastructure/persistence/StoreJpaRepository.java` 수정
  - `findByOwnerId(UUID ownerId)` 쿼리 메서드 추가 (1-5에서 완료)

### 1-8. Repository 구현체 추가/수정

> ⏱ 20m | 선행: 1-4, 1-7

- [x] `infrastructure/persistence/OwnerRepositoryImpl.java` 생성
- [x] `infrastructure/persistence/StoreSettingsRepositoryImpl.java` 생성
- [x] `infrastructure/persistence/StoreRepositoryImpl.java` 수정 — `findByOwnerId` 구현 추가 (1-5에서 완료)

### 1-9. 도메인/Repository 단위 테스트

> ⏱ 20m | 선행: 1-8

- [x] `OwnerRepositoryImplTest` 작성 (`@DataJpaTest`)
    - `findByEmail` 정상 조회 / 존재하지 않는 이메일 테스트
- [x] `StoreSettingsRepositoryImplTest` 작성
    - `findByStoreId` 정상 조회 테스트
- [x] `StoreTest` 작성 (순수 단위 테스트)
  - `changeStatus()` 새 객체 반환 및 원본 불변 검증, 모든 상태 전이 가능 확인, 필드 보존 확인
    - `StoreSettings.calculateEstimatedWait()` 계산 결과 검증

---

## PHASE 2. 백엔드 — 인증 (JWT)

> 예상 소요시간: **2h 30m**

### 2-1. JwtTokenProvider 구현

> ⏱ 30m | 선행: 0-1

- [x] `presentation/security/JwtTokenProvider.java` 생성
    - Access Token 생성: `generateAccessToken(UUID ownerId): String`
    - Refresh Token 생성: `generateRefreshToken(UUID ownerId): String`
    - 토큰 검증: `validateToken(String token): boolean`
    - 토큰에서 ownerId 추출: `extractOwnerId(String token): UUID`
    - `@Value`로 secret, expiry 주입

### 2-2. RefreshTokenRepository 구현 (Redis)

> ⏱ 20m | 선행: 0-1

- [x] `infrastructure/redis/RefreshTokenRepository.java` 생성
    - `save(UUID ownerId, String refreshToken, long expirySeconds)`
    - `findByOwnerId(UUID ownerId): Optional<String>`
    - `delete(UUID ownerId)`
    - Redis key 형식: `refresh:token:{ownerId}`

### 2-3. SignUpOwnerUseCase 구현

> ⏱ 25m | 선행: 1-4, 2-1

- [x] `application/dto/SignUpRequest.java` 생성
    - 필드: `email`, `password`, `storeName`, `address`
    - `@Email`, `@NotBlank`, `@Size(min=8)` 검증 어노테이션
- [x] `application/dto/SignUpResponse.java` 생성
    - 필드: `ownerId`, `storeId`, `qrUrl`
- [x] `application/usecase/SignUpOwnerUseCase.java` 인터페이스 정의
- [x] `application/usecase/SignUpOwnerUseCaseImpl.java` 구현
    - 이메일 중복 검증
    - BCrypt로 비밀번호 해싱
    - `Owner` 생성 및 저장
    - `Store` 생성 및 저장 (ownerId 연결)
    - `StoreSettings` 기본값으로 생성 및 저장
    - `qrUrl` 조립 후 반환

### 2-4. LoginOwnerUseCase 구현

> ⏱ 20m | 선행: 1-4, 2-1, 2-2

- [x] `application/dto/LoginRequest.java` 생성
- [x] `application/dto/LoginResponse.java` 생성
  - 필드: `accessToken`, `refreshToken`, `ownerId`, `storeId` (컨트롤러가 refreshToken을 HttpOnly Cookie로 설정)
- [x] `application/usecase/LoginOwnerUseCase.java` 인터페이스 정의
- [x] `application/usecase/LoginOwnerUseCaseImpl.java` 구현
    - 이메일로 Owner 조회 (없으면 `InvalidCredentialsException`)
    - BCrypt로 비밀번호 검증
    - Access Token, Refresh Token 생성
    - Refresh Token → Redis 저장
  - Refresh Token → HttpOnly Cookie 설정 (컨트롤러 2-6에서 처리)
    - Access Token → 응답 바디 반환

### 2-5. LogoutUseCase / RefreshTokenUseCase 구현

> ⏱ 20m | 선행: 2-2, 2-4

- [x] `LogoutOwnerUseCaseImpl` 구현 — Redis에서 Refresh Token 삭제
- [x] `RefreshTokenUseCaseImpl` 구현
  - HttpOnly Cookie에서 Refresh Token 추출 (컨트롤러에서 추출 후 String으로 전달)
  - Redis에서 유효성 검증 (토큰 존재 여부 + 저장값 일치 여부)
    - 새 Access Token 발급

### 2-6. JwtAuthFilter 구현

> ⏱ 20m | 선행: 2-1

- [x] `presentation/security/JwtAuthFilter.java` 생성 (`OncePerRequestFilter` 상속)
    - `Authorization: Bearer {token}` 헤더 파싱
    - `JwtTokenProvider.validateToken()` 검증
  - 검증 성공 시 `SecurityContextHolder`에 인증 정보 설정 (`ROLE_OWNER`, principal=ownerId)
    - 토큰 없거나 유효하지 않으면 다음 필터로 통과 (익명 요청 허용)

### 2-7. SecurityConfig 완성

> ⏱ 20m | 선행: 2-6

- [x] `SecurityConfig.java` 완성
    - `/api/auth/**`, `/api/stores/**`, `/api/waitings/**` → 인증 없이 허용 (손님 API)
  - `/api/owner/**` → JWT 인증 필수 (`hasRole("OWNER")`)
    - `JwtAuthFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록
    - CSRF 비활성화 (REST API)
    - Session stateless 설정

### 2-8. AuthController 구현 및 테스트

> ⏱ 25m | 선행: 2-3, 2-4, 2-5

- [x] `presentation/controller/AuthController.java` 생성
    - `POST /api/auth/signup`
  - `POST /api/auth/login` — refreshToken → HttpOnly Cookie, accessToken → 응답 바디
  - `POST /api/auth/logout` — Redis 삭제 + 쿠키 만료
  - `POST /api/auth/refresh` — 쿠키에서 refreshToken 추출 후 새 accessToken 발급
- [x] `GlobalExceptionHandler` 수정 — `InvalidCredentialsException` → 401, `DuplicateEmailException` → 409 처리
- [x] `AuthControllerTest` 작성 (`@WebMvcTest`)
    - 회원가입 성공 → 201 + storeId, qrUrl 반환 검증
    - 중복 이메일 회원가입 → 409 반환 검증
  - 로그인 성공 → 200 + accessToken 반환 검증, HttpOnly Cookie 설정 확인
    - 잘못된 비밀번호 → 401 반환 검증

---

## PHASE 3. 백엔드 — 점주 UseCase & API

> 예상 소요시간: **4h 00m**

### 3-1. DTO 정의 (점주 운영)

> ⏱ 20m | 선행: 1-2, 1-3

- [x] `application/dto/StoreResponse.java` 수정 — `address`, `status` 필드 추가
- [x] `application/dto/StoreSettingsResponse.java` 생성
    - 필드: `tableCount`, `avgTurnoverMinutes`, `openTime`, `closeTime`, `alertThreshold`, `alertEnabled`, `estimatedWaitFormulaExample`
- [x] `application/dto/UpdateStoreSettingsRequest.java` 생성
    - 필드: `tableCount`, `avgTurnoverMinutes`, `openTime`, `closeTime`, `alertThreshold`, `alertEnabled`
    - `@Min`, `@Max` 검증 어노테이션
- [x] `application/dto/UpdateStoreStatusRequest.java` 생성
    - 필드: `status (StoreStatus)`
- [x] `application/dto/OwnerWaitingResponse.java` 생성 (점주 대기 목록용)
    - 필드: `waitingId`, `waitingNumber`, `visitorName`, `partySize`, `status`, `elapsedMinutes`
- [x] `application/dto/DailySummaryResponse.java` 생성
    - 필드: `totalRegistered`, `totalEntered`, `totalNoShow`, `totalCancelled`, `currentWaiting`

### 3-2. GetMyStoreUseCase 구현

> ⏱ 15m | 선행: 1-4, 3-1

- [x] `GetMyStoreUseCase` 인터페이스 + Impl 구현
    - ownerId로 Store 조회 (없으면 `StoreNotFoundException`)
    - `StoreResponse` 반환

### 3-3. UpdateStoreSettingsUseCase 구현

> ⏱ 20m | 선행: 1-4, 3-1

- [x] `UpdateStoreSettingsUseCase` 인터페이스 + Impl 구현
    - storeId 소유권 검증 (ownerId 매칭)
    - `StoreSettings` 조회 후 필드 업데이트
    - 저장 후 `StoreSettingsResponse` 반환

### 3-4. UpdateStoreStatusUseCase 구현

> ⏱ 20m | 선행: 1-3, 1-4

- [x] `UpdateStoreStatusUseCase` 인터페이스 + Impl 구현
    - storeId 소유권 검증
    - `store.changeStatus(newStatus)` 호출
    - 저장 후 SSE 브로드캐스트 트리거 — 손님 화면에 상태 변경 이벤트 전송
    - Phase 4에서 SSE 연결 후 완성 (주석으로 표시)

### 3-5. GetOwnerWaitingListUseCase 구현

> ⏱ 20m | 선행: 1-4, 3-1

- [x] `GetOwnerWaitingListUseCase` 인터페이스 + Impl 구현
    - storeId의 WAITING / CALLED 상태 목록 조회
    - 각 항목에 `elapsedMinutes` 계산 (`now - createdAt`)
    - `List<OwnerWaitingResponse>` 반환

### 3-6. GetDailySummaryUseCase 구현

> ⏱ 15m | 선행: 1-4, 3-1

- [x] `GetDailySummaryUseCase` 인터페이스 + Impl 구현
    - 오늘 날짜 기준으로 storeId의 상태별 건수 집계
    - `WaitingRepository`에 `countByStoreIdAndStatusAndDate()` 메서드 추가
    - `DailySummaryResponse` 반환

### 3-7. ProcessWaitingUseCase 구현 (호출/입장/노쇼 통합)

> ⏱ 30m | 선행: 1-4

- [ ] `CallWaitingUseCase` 인터페이스 + Impl 구현
    - storeId 소유권 검증
    - `entry.call()` 호출 (WAITING → CALLED)
    - 저장 후 해당 손님 SSE 채널에 `called` 이벤트 발송
- [ ] `EnterWaitingUseCase` 인터페이스 + Impl 구현 (기존 시뮬레이션용 대체)
    - storeId 소유권 검증
    - `entry.enter()` 호출 (CALLED → ENTERED)
    - 저장 후 매장 전체 SSE 브로드캐스트
- [ ] `NoShowWaitingUseCase` 인터페이스 + Impl 구현
    - storeId 소유권 검증
    - `entry.cancel()` 호출 (→ CANCELLED)
    - 저장 후 매장 전체 SSE 브로드캐스트

### 3-8. OwnerController 구현

> ⏱ 30m | 선행: 3-2 ~ 3-7

- [ ] `presentation/controller/OwnerController.java` 생성
    - `GET /api/owner/stores/me` — 내 매장 정보 조회
    - `PUT /api/owner/stores/me` — 매장 정보 수정 (이름, 주소)
    - `GET /api/owner/stores/me/settings` — 매장 설정 조회
    - `PUT /api/owner/stores/me/settings` — 매장 설정 수정
    - `PUT /api/owner/stores/me/status` — 매장 상태 변경
    - `GET /api/owner/stores/me/waitings` — 현재 대기 목록
    - `GET /api/owner/stores/me/waitings/summary` — 오늘 통계
    - `POST /api/owner/waitings/{waitingId}/call` — 손님 호출
    - `POST /api/owner/waitings/{waitingId}/enter` — 입장 처리
    - `POST /api/owner/waitings/{waitingId}/noshow` — 노쇼 처리
- [ ] 모든 엔드포인트에서 `SecurityContextHolder`에서 ownerId 추출하는 헬퍼 메서드 구현

### 3-9. RegisterWaitingUseCase 수정 — 매장 상태 검증 추가

> ⏱ 15m | 선행: 1-3

- [ ] `RegisterWaitingUseCaseImpl` 수정
    - 매장 조회 후 `store.getStatus() != OPEN`이면 `StoreNotAvailableException` 발생
- [ ] `GlobalExceptionHandler`에 `StoreNotAvailableException` → 409 처리 추가
- [ ] `GetWaitingStatusUseCaseImpl` 수정
    - 예상 대기시간 계산을 `StoreSettings.calculateEstimatedWait()` 메서드로 교체
    - `StoreSettingsRepository.findByStoreId()` 조회 추가

### 3-10. UseCase 단위 테스트

> ⏱ 40m | 선행: 3-2 ~ 3-7

- [ ] `SignUpOwnerUseCaseImplTest` — 정상 가입, 중복 이메일 예외
- [ ] `LoginOwnerUseCaseImplTest` — 정상 로그인, 잘못된 비밀번호 예외
- [ ] `UpdateStoreStatusUseCaseImplTest` — 상태 전이 성공, 소유권 검증 실패
- [ ] `ProcessWaitingUseCaseTest` — 호출/입장/노쇼 각 케이스 + 소유권 검증 실패

### 3-11. OwnerController 통합 테스트

> ⏱ 30m | 선행: 3-8

- [ ] `OwnerControllerTest` 작성 (`@WebMvcTest`)
    - JWT 없이 접근 → 401 검증
    - 타 점주 매장 접근 → 403 검증
    - 매장 상태 변경 성공 → 200 검증
    - 대기 목록 조회 성공 → 200 + 응답 바디 검증

### 3-12. 기존 시뮬레이션 엔드포인트 제거

> ⏱ 10m | 선행: 3-7

- [ ] `WaitingController`에서 `PUT /api/waitings/{waitingId}/enter` (시뮬레이션용) 제거
- [ ] `EnterWaitingUseCaseImpl` (기존 시뮬레이션용) 제거
- [ ] 관련 테스트 정리

---

## PHASE 4. 백엔드 — 대시보드 SSE 확장

> 예상 소요시간: **1h 30m**

### 4-1. SseEmitterRegistry 확장 (점주용 채널 추가)

> ⏱ 20m | 선행: 0-2

- [ ] `SseEmitterRegistry.java` 수정
    - 점주용 `Map<UUID, SseEmitter> ownerEmitters` 추가 (storeId → 점주 Emitter)
    - `registerOwner(UUID storeId, SseEmitter emitter)` 메서드 추가
    - `removeOwner(UUID storeId)` 메서드 추가
    - `broadcastToOwner(UUID storeId, String eventName, Object data)` 메서드 추가

### 4-2. WaitingSseService 확장

> ⏱ 25m | 선행: 4-1

- [ ] `WaitingSseService.java` 수정
    - `subscribeOwner(UUID storeId): SseEmitter` 메서드 추가
        - 기존 연결 있으면 교체
        - 초기 이벤트로 현재 대기 목록 전송
    - `broadcastUpdate()` 수정 — 매장 전체 손님 SSE + 점주 SSE 모두 전송
    - 손님 신규 등록 시 점주에게 `waiting-registered` 이벤트 발송
    - 대기자 수 임계값 초과 시 점주에게 `alert-threshold-reached` 이벤트 발송

### 4-3. OwnerController에 SSE 엔드포인트 추가

> ⏱ 15m | 선행: 4-2

- [ ] `GET /api/owner/stores/me/dashboard/stream` 엔드포인트 추가
    - `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
    - JWT 검증 후 `subscribeOwner()` 호출

### 4-4. 매장 상태 변경 시 손님 SSE 연결

> ⏱ 10m | 선행: 3-4, 4-2

- [ ] `UpdateStoreStatusUseCaseImpl`에서 `WaitingSseService.broadcastStoreStatus()` 호출 연결
    - 손님 화면에 `store-status-changed` 이벤트 전송

---

## PHASE 5. 프론트엔드 — 점주 페이지 구현

> 예상 소요시간: **4h 00m**

### 5-1. 점주 인증 상태 관리 (Zustand + axios 인터셉터)

> ⏱ 30m | 선행: Phase 2 완료

- [x] `src/store/ownerStore.ts` 생성
    - 상태: `ownerId`, `storeId`, `accessToken`
  - 액션: `setAuth()`, `setAccessToken()`, `clearAuth()`
- [x] `src/api/ownerClient.ts` 생성 (점주 전용 axios instance)
    - `Authorization: Bearer {accessToken}` 헤더 자동 첨부
    - 401 응답 시 `/api/auth/refresh` 자동 재시도 인터셉터
    - refresh 실패 시 `/owner/login` 리다이렉트
  - 동시 401 요청 처리를 위한 failedQueue 패턴 적용
- [x] `src/api/owner.ts` 생성 — 점주 API 함수 정의 (signUp, login, logout, refreshToken)
- [x] 앱 초기 로드 시 `/api/auth/refresh` 호출하여 Access Token 복구 (App.tsx)

### 5-2. 점주 전용 라우트 설정

> ⏱ 15m | 선행: 5-1

- [x] `App.tsx`에 점주 라우트 추가
    - `/owner/signup` → `OwnerSignupPage`
    - `/owner/login` → `OwnerLoginPage`
    - `/owner/onboarding` → `OnboardingPage`
    - `/owner/dashboard` → `DashboardPage`
    - `/owner/settings` → `StoreSettingsPage`
- [x] `PrivateRoute` 컴포넌트 구현 — 미인증 시 `/owner/login`으로 리다이렉트
- [x] 루트(`/`) 접근 시 리다이렉트 처리
  - 로그인 상태 → `/owner/dashboard`
  - 비로그인 상태 → `/owner/login`
- [x] 기존 `OwnerPage.tsx` 파일 제거

### 5-3. OwnerSignupPage 구현

> ⏱ 30m | 선행: 5-2

- [x] 이메일, 비밀번호, 비밀번호 확인, 매장명, 주소 입력 폼
- [x] 클라이언트 유효성 검증 (이메일 형식, 비밀번호 8자 이상, 비밀번호 일치)
- [x] `POST /api/auth/signup` 호출
- [x] 성공 시 자동 로그인 후 `OnboardingPage`로 이동

### 5-4. OwnerLoginPage 구현

> ⏱ 20m | 선행: 5-2

- [x] 이메일, 비밀번호 입력 폼
- [x] `POST /api/auth/login` 호출
- [x] 성공 시 Access Token → ownerStore 저장, `DashboardPage`로 이동
- [x] 회원가입 페이지 링크

### 5-5. OnboardingPage 구현

> ⏱ 25m | 선행: 5-3

- [x] 단계별 가이드 UI (3단계)
    - 1단계: QR 코드 확인 및 PNG 다운로드 버튼
  - 2단계: 테이블 수 / 팀당 평균 이용시간 초기 설정 (`PUT /api/owner/stores/me/settings` — Phase 3 완료 후 동작)
    - 3단계: 대시보드 사용법 안내
- [x] 완료 시 `DashboardPage`로 이동

### 5-6. DashboardPage 구현 (핵심)

> ⏱ 70m | 선행: 5-1, 5-2

- [ ] 상단: 매장 상태 토글 버튼 (운영 중 / 브레이크타임 / 만석 / 영업 종료)
- [ ] 오늘 통계 요약 카드 (등록 / 입장 / 노쇼 / 취소 건수)
- [ ] 실시간 대기 목록 테이블
    - 컬럼: 대기번호, 이름, 인원, 대기 경과시간
    - 각 행에 호출 / 입장 처리 / 노쇼 버튼
    - 처리 전 확인 다이얼로그 표시
- [ ] SSE 연결 구현
    - `GET /api/owner/stores/me/dashboard/stream`
    - `waiting-registered` 이벤트 → 목록 갱신
    - `waiting-updated` 이벤트 → 목록 갱신
    - `alert-threshold-reached` 이벤트 → 브라우저 알림 표시
    - 연결 끊김 시 자동 재연결 (최대 3회)
- [ ] 로그아웃 버튼 → `POST /api/auth/logout` 호출 → `/owner/login` 이동

### 5-7. StoreSettingsPage 구현

> ⏱ 30m | 선행: 5-1

- [ ] 현재 설정 조회 (`GET /api/owner/stores/me/settings`)
- [ ] 테이블 수, 팀당 평균 이용시간, 영업 시작/종료 시간 수정 폼
- [ ] 알림 설정 (임계값, ON/OFF 토글)
- [ ] QR 코드 이미지 표시 + PNG 다운로드 버튼
- [ ] 저장 (`PUT /api/owner/stores/me/settings`) 성공 시 토스트 알림

### 5-8. 손님 LandingPage 수정 — 매장 상태 반영

> ⏱ 20m | 선행: 5-1

- [ ] 매장 상태가 OPEN이 아닌 경우 웨이팅 등록 폼 대신 상태 메시지 표시
    - BREAK: "현재 브레이크타임입니다"
    - FULL: "현재 만석입니다"
    - CLOSED: "오늘 영업이 종료되었습니다"
- [ ] SSE로 매장 상태 변경 시 실시간으로 메시지 전환

### 5-9. 브라우저 푸시 알림 설정

> ⏱ 20m | 선행: 5-6

- [ ] 대시보드 최초 접속 시 브라우저 알림 권한 요청
- [ ] `alert-threshold-reached` SSE 이벤트 수신 시 `Notification API`로 OS 알림 발송
- [ ] 알림 권한 거부 시 대시보드 내 배너로 대체 표시

---

## PHASE 6. 연동 & 통합 테스트

> 예상 소요시간: **1h 30m**

### 6-1. 점주 전체 플로우 통합 테스트

> ⏱ 30m | 선행: Phase 5 완료

- [ ] 회원가입 → 온보딩 → 대시보드 접속 전체 플로우 수동 테스트
- [ ] 손님 QR 스캔 → 웨이팅 등록 → 점주 대시보드에 실시간 반영 확인
- [ ] 점주 입장 처리 → 손님 화면 순서 갱신 확인
- [ ] 점주 노쇼 처리 → 대기열 갱신 확인

### 6-2. 예외 시나리오 테스트

> ⏱ 20m | 선행: 6-1

- [ ] 점주가 브레이크타임 설정 후 손님이 QR 스캔 → 등록 불가 메시지 표시 확인
- [ ] Access Token 만료 후 자동 재발급 동작 확인
- [ ] 타 점주 매장 API 직접 호출 → 403 확인
- [ ] 대기자 수 임계값 초과 시 점주 알림 발송 확인

### 6-3. 예상 대기시간 계산 검증

> ⏱ 15m | 선행: 6-1

- [ ] 테이블 수 5개, 평균 이용시간 30분, 앞 대기 3팀 → 손님 화면에 18분 표시 확인
- [ ] 점주가 설정 변경 후 손님 화면 예상 대기시간 즉시 반영 확인

### 6-4. 보안 검증

> ⏱ 15m | 선행: Phase 3 완료

- [ ] JWT 없이 `/api/owner/**` 접근 → 401 반환 확인
- [ ] 만료된 Access Token으로 접근 → 401 반환 확인
- [ ] 손님 API (`/api/stores/**`, `/api/waitings/**`) JWT 없이 정상 동작 확인

### 6-5. TASKS 문서 체크박스 최종 업데이트

> ⏱ 10m | 선행: Phase 6 완료

- [ ] 완료된 모든 태스크 체크박스 확인
- [ ] 미완료 항목 및 이슈 사항 별도 기록
- [ ] CLAUDE.md 진행 상태 업데이트
