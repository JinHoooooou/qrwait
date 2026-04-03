# TASKS — QR 웨이팅 서비스 (MVP)

| 항목    | 내용                                                        |
|-------|-----------------------------------------------------------|
| 문서 유형 | Task Checklist                                            |
| 버전    | v1.0.0                                                    |
| 상태    | Draft                                                     |
| 작성일   | 2026년 03월 24일                                             |
| 연관 문서 | [PRD](./QRWait_PRD_v1.0.md) · [TRD](./QRWait_TRD_v1.0.md) |

> **문서 목적:** PRD/TRD를 기반으로 에이전트(또는 개발자)가 순서대로 실행할 수 있도록 구체적인 구현 태스크를 체크리스트 형태로 명세합니다.
> 각 태스크는 독립적으로 실행 가능한 단위로 분리되어 있으며, 선행 태스크가 있는 경우 명시합니다.

---

## 진행 현황 요약

| 섹션                          | 태스크 수  | 예상 소요시간      |
|-----------------------------|--------|--------------|
| PHASE 0. 프로젝트 셋업            | 6      | 1h 30m       |
| PHASE 1. 백엔드 — 도메인 & DB     | 9      | 3h 00m       |
| PHASE 2. 백엔드 — 애플리케이션 & API | 15     | 5h 00m       |
| PHASE 3. 백엔드 — 실시간 SSE      | 5      | 2h 00m       |
| PHASE 4. 프론트엔드 — 프로젝트 셋업    | 5      | 1h 00m       |
| PHASE 5. 프론트엔드 — 페이지 구현     | 9      | 4h 00m       |
| PHASE 6. 연동 & 통합 테스트        | 6      | 2h 10m       |
| PHASE 7. 배포 구성              | 5      | 1h 30m       |
| **합계**                      | **60** | **~20h 10m** |

---

## PHASE 0. 프로젝트 셋업

> 예상 소요시간: **1h 30m**

### 0-1. 모노레포 디렉토리 구조 생성

> ⏱ 10m

- [x] 하위 디렉토리 생성: `backend/`, `frontend/`
- [x] 루트 `README.md` 작성 (프로젝트 개요, 실행 방법 포함)

### 0-2. 백엔드 Spring Boot 프로젝트 초기화

> ⏱ 20m | 선행: 0-1

- [x] Spring Initializr로 프로젝트 생성
    - Group: `com.qrwait` / Artifact: `api`
    - Java 21, Spring Boot 3.3.x (실제 적용: 3.5.0 — start.spring.io 최소 지원 버전)
- [x] 의존성 추가: `Spring Web`, `Spring Data JPA`, `PostgreSQL Driver`, `Spring Data Redis`, `Lombok`, `Validation`
- [x] `backend/` 에 프로젝트 배치
- [x] `application.yml` 기본 설정 작성 (포트: 8080, 로컬 DB 연결 정보)

### 0-3. 백엔드 패키지 구조 생성 (클린 아키텍처)

> ⏱ 10m | 선행: 0-2

- [x] `domain/model/` 패키지 생성
- [x] `domain/repository/` 패키지 생성
- [x] `domain/service/` 패키지 생성
- [x] `application/usecase/` 패키지 생성
- [x] `application/dto/` 패키지 생성
- [x] `infrastructure/persistence/` 패키지 생성
- [x] `infrastructure/redis/` 패키지 생성
- [x] `infrastructure/sse/` 패키지 생성
- [x] `presentation/controller/` 패키지 생성
- [x] `presentation/advice/` 패키지 생성

### 0-4. 프론트엔드 React 프로젝트 초기화

> ⏱ 15m | 선행: 0-1

- [x] `npm create vite@latest frontend -- --template react-ts` 실행
- [x] 의존성 설치: `react-router-dom`, `axios`, `zustand`
- [x] 불필요한 보일러플레이트 파일 제거 (`App.css`, 기본 컴포넌트 등)
- [x] `vite.config.ts` 에 API 프록시 설정 (`/api` → `http://localhost:8080`)

### 0-5. 로컬 개발 환경 Docker Compose 구성

> ⏱ 20m | 선행: 0-1

- [x] 루트에 `docker-compose.dev.yml` 생성
- [x] PostgreSQL 15 서비스 정의 (포트: 5432, DB명: `qrwait`)
- [x] Redis 7 서비스 정의 (포트: 6379)
- [x] `docker-compose.dev.yml` 실행 후 PostgreSQL/Redis 연결 확인 (both healthy)

### 0-6. Git 초기화 및 .gitignore 설정

> ⏱ 15m | 선행: 0-1

- [x] 루트에 `git init`
- [x] `.gitignore` 설정 (Java 빌드 산출물, `node_modules`, `.env` 등)
- [x] 초기 커밋

---

## PHASE 1. 백엔드 — 도메인 & DB

> 예상 소요시간: **2h 30m**

### 1-1. Store 도메인 모델 구현

> ⏱ 20m | 선행: 0-3

- [x] `domain/model/Store.java` 생성
    - 필드: `id (UUID)`, `name (String)`, `createdAt (LocalDateTime)` — ~~qrCode 제거됨 (1-9 참고)~~
    - 순수 Java 객체 (JPA 어노테이션 없음)
- [x] 생성자, getter 구현 (private all-args 생성자 + public getter)
- [x] `Store` 팩토리 메서드 `create(name)` 구현
    - 추가: `restore(id, name, createdAt)` — 영속 계층 복원용 — ~~qrCode 파라미터 제거됨~~

### 1-2. WaitingEntry 도메인 모델 구현

> ⏱ 30m | 선행: 0-3

- [x] `domain/model/WaitingStatus.java` Enum 생성
    - 값: `WAITING`, `CALLED`, `ENTERED`, `CANCELLED`
- [x] `domain/model/WaitingEntry.java` 생성
    - 필드: `id (UUID)`, `storeId (UUID)`, `visitorName (String)`, `partySize (int)`, `waitingNumber (int)`, `status (WaitingStatus)`,
      `createdAt (LocalDateTime)`
- [x] 도메인 메서드 구현: `cancel()`, `enter()`, `call()`
    - 각 메서드에서 유효하지 않은 상태 전이 시 `IllegalStateException` 발생

### 1-3. Repository 인터페이스 정의

> ⏱ 15m | 선행: 1-1, 1-2

- [x] `domain/repository/StoreRepository.java` 인터페이스 생성
    - `findById(UUID id): Optional<Store>` — ~~findByQrCode 제거됨 (1-9 참고)~~
    - `save(Store store): Store`
- [x] `domain/repository/WaitingRepository.java` 인터페이스 생성
    - `save(WaitingEntry entry): WaitingEntry`
    - `findById(UUID id): Optional<WaitingEntry>`
    - `findByStoreIdAndStatus(UUID storeId, WaitingStatus status): List<WaitingEntry>`
    - `countByStoreIdAndStatus(UUID storeId, WaitingStatus status): int`
    - `findNextWaitingNumber(UUID storeId): int`

### 1-4. DB 마이그레이션 스크립트 작성 (Flyway 또는 schema.sql)

> ⏱ 20m | 선행: 0-2

- [x] `resources/db/migration/V1__create_stores.sql` 작성 (TRD DDL 기반)
- [x] `resources/db/migration/V2__create_waiting_entries.sql` 작성
    - `waiting_entries` 테이블 + 인덱스 `idx_waiting_store_status` 포함
- [x] `application.yml` 에 Flyway 설정 추가 (0-2에서 선행 작성, build.gradle에 flyway-core/flyway-database-postgresql 의존성 추가)
- [x] 마이그레이션 실행 후 테이블 생성 확인 (docker exec psql로 직접 검증)

### 1-5. JPA Entity 구현

> ⏱ 20m | 선행: 1-3, 1-4

- [x] `infrastructure/persistence/StoreJpaEntity.java` 생성
    - `@Entity`, `@Table(name = "stores")` 어노테이션
    - 도메인 `Store` ↔ JPA Entity 간 변환 메서드 (`toDomain()`, `from(Store)`)
- [x] `infrastructure/persistence/WaitingEntryJpaEntity.java` 생성
    - `@Entity`, `@Table(name = "waiting_entries")` 어노테이션
    - 도메인 `WaitingEntry` ↔ JPA Entity 간 변환 메서드

### 1-6. JpaRepository 인터페이스 생성

> ⏱ 15m | 선행: 1-5

- [x] `infrastructure/persistence/StoreJpaRepository.java` 생성 (`JpaRepository` 상속)
    - ~~`findByQrCode` 제거됨~~ — `JpaRepository` 기본 `findById` 사용 (1-9 참고)
- [x] `infrastructure/persistence/WaitingEntryJpaRepository.java` 생성
    - `findByStoreIdAndStatus`, `countByStoreIdAndStatus` 쿼리 메서드 정의
    - `findMaxWaitingNumberByStoreId` JPQL 쿼리 정의 (`COALESCE(MAX(...), 0)` — 첫 등록 시 0 반환)

### 1-7. Repository 구현체 작성

> ⏱ 20m | 선행: 1-3, 1-6

- [x] `infrastructure/persistence/StoreRepositoryImpl.java` 구현
    - `domain/repository/StoreRepository` 인터페이스 구현
    - `StoreJpaRepository` 주입하여 JPA 위임
- [x] `infrastructure/persistence/WaitingRepositoryImpl.java` 구현
    - `domain/repository/WaitingRepository` 인터페이스 구현

### 1-8. Repository 단위 테스트

> ⏱ 30m | 선행: 1-7

- [x] `StoreRepositoryImplTest` 작성 (`@DataJpaTest`)
    - `findById` 정상 조회 / 존재하지 않는 storeId 테스트 — ~~findByQrCode 제거됨 (1-9 참고)~~
- [x] `WaitingRepositoryImplTest` 작성
    - `findByStoreIdAndStatus` 목록 조회 테스트
    - `countByStoreIdAndStatus` 카운트 테스트

### 1-9. qrCode 필드 제거 리팩토링 (백엔드)

> ⏱ 30m | 선행: 1-1 ~ 1-8

> **배경:** QR 이미지에 storeId가 담긴 URL이 직접 인코딩되므로 별도 qrCode 식별자 불필요

- [x] `Store.java` — `qrCode` 필드 및 관련 생성자 파라미터 제거
- [x] `StoreJpaEntity.java` — `qrCode` 컬럼 매핑 제거, `toDomain()` / `from()` 수정
- [x] `StoreJpaRepository.java` — `findByQrCode()` 메서드 제거
- [x] `StoreRepository.java` — `findByQrCode()` 제거 (findById는 이미 JpaRepository에서 상속)
- [x] `StoreRepositoryImpl.java` — `findByQrCode()` 구현 제거
- [x] `GetStoreByQrCodeUseCase` / `Impl` → `GetStoreByIdUseCase` / `Impl` 로 rename, 조회 방식 변경
- [x] `StoreController.java` — `GET /api/stores/{qrCode}` → `GET /api/stores/{storeId}` (UUID 타입으로 변경)
- [x] `V4__remove_qr_code_column.sql` 작성: `ALTER TABLE stores DROP COLUMN qr_code;`
- [x] `V3__seed_test_stores.sql` — **수정 금지** (이미 적용된 마이그레이션은 변경 불가, 원본 그대로 유지)
- [x] `StoreRepositoryImplTest` 수정 — `findByQrCode` 테스트 → `findById` 테스트로 교체
- [x] `StoreControllerTest` 수정 — `/{qrCode}` → `/{storeId}` 경로 반영

---

## PHASE 2. 백엔드 — 애플리케이션 & API

> 예상 소요시간: **3h 30m**

### 2-1. Request/Response DTO 정의

> ⏱ 20m | 선행: 1-2

- [x] `application/dto/RegisterWaitingRequest.java` 생성
    - 필드: `visitorName (String)`, `partySize (int)`
    - `@NotBlank`, `@Min(1)`, `@Max(10)` 검증 어노테이션 추가
- [x] `application/dto/RegisterWaitingResponse.java` 생성
    - 필드: `waitingId`, `waitingNumber`, `currentRank`, `totalWaiting`, `estimatedWaitMinutes`, `waitingToken`
- [x] `application/dto/WaitingStatusResponse.java` 생성
    - 필드: `currentRank`, `totalWaiting`, `estimatedWaitMinutes`
- [x] `application/dto/StoreResponse.java` 생성
    - 필드: `storeId`, `name`

### 2-2. RegisterWaitingUseCase 구현

> ⏱ 30m | 선행: 1-3, 2-1

- [x] `application/usecase/RegisterWaitingUseCase.java` 인터페이스 정의
    - `execute(UUID storeId, RegisterWaitingRequest request): RegisterWaitingResponse`
- [x] `application/usecase/RegisterWaitingUseCaseImpl.java` 구현
    - 매장 존재 여부 검증 (`StoreNotFoundException` — `StoreRepository.findById` 추가)
    - 다음 웨이팅 번호 계산 (현재 최댓값 + 1)
    - `WaitingEntry` 생성 및 저장
    - 현재 대기 순서(rank) 계산 및 예상 대기시간 산출 (대기팀 수 × 평균 5분)
    - `waitingToken` 생성 (UUID v4)
    - `RegisterWaitingResponse` 반환

### 2-3. GetWaitingStatusUseCase 구현

> ⏱ 20m | 선행: 1-3, 2-1

- [x] `application/usecase/GetWaitingStatusUseCase.java` 인터페이스 정의
    - `execute(UUID waitingId): WaitingStatusResponse`
- [x] `application/usecase/GetWaitingStatusUseCaseImpl.java` 구현
    - 웨이팅 조회 (없으면 `WaitingNotFoundException` 발생)
    - CANCELLED/ENTERED 상태 조회 시 예외 처리
    - WAITING 상태인 앞 팀 수 계산 → currentRank 산출 (waitingNumber 비교)
    - 예상 대기시간 산출 (앞 팀 수 × 5분)

### 2-4. CancelWaitingUseCase 구현

> ⏱ 15m | 선행: 1-3

- [x] `application/usecase/CancelWaitingUseCase.java` 인터페이스 정의
    - `execute(UUID waitingId): void`
- [x] `application/usecase/CancelWaitingUseCaseImpl.java` 구현
    - 웨이팅 조회 후 `entry.cancel()` 호출
    - 저장 후 SSE 브로드캐스트 트리거 — Phase 3에서 연결 예정 (주석으로 표시)

### 2-5. StoreController 구현

> ⏱ 20m | 선행: 2-1

- [x] `presentation/controller/StoreController.java` 생성
    - `GET /api/stores/{storeId}` — storeId로 매장 조회, `StoreResponse` 반환 — ~~/{qrCode} → /{storeId} 변경 (1-9 참고)~~
    - `GET /api/stores/{storeId}/waitings/status` — 매장 전체 대기 현황 조회
    - 추가: `GetStoreByIdUseCase`, `GetStoreWaitingStatusUseCase` 생성 (계층 원칙 준수) — ~~GetStoreByQrCodeUseCase → GetStoreByIdUseCase rename~~

### 2-6. WaitingController 구현

> ⏱ 25m | 선행: 2-2, 2-3, 2-4

- [x] `presentation/controller/WaitingController.java` 생성
    - `POST /api/stores/{storeId}/waitings` — 웨이팅 등록 (`201 Created`, `Location` 헤더 포함)
    - `GET /api/waitings/{waitingId}` — 웨이팅 상세 조회
    - `DELETE /api/waitings/{waitingId}` — 웨이팅 취소 (`204 No Content`)
- [ ] SSE 엔드포인트는 Phase 3에서 추가 (`GET /api/waitings/{waitingId}/stream`)

### 2-7. GlobalExceptionHandler 구현

> ⏱ 20m | 선행: 2-5, 2-6

- [x] `presentation/advice/GlobalExceptionHandler.java` 생성 (`@RestControllerAdvice`)
- [x] `WaitingNotFoundException` → `404 Not Found` 처리
- [x] `StoreNotFoundException` → `404 Not Found` 처리
- [x] `@Valid` 검증 실패 (`MethodArgumentNotValidException`) → `400 Bad Request` 처리
- [x] 공통 에러 응답 포맷 정의: `{ "code": "...", "message": "..." }` (`ErrorResponse` record)
- [x] 추가: `IllegalStateException` → `409 Conflict` (잘못된 상태 전이 처리)

### 2-8. CORS 설정

> ⏱ 10m | 선행: 2-5

- [x] `WebMvcConfigurer` 구현 (`WebConfig.java`)
- [x] 개발 환경: `http://localhost:5173` 허용
- [x] `application.yml` 에 allowed-origins 프로퍼티로 분리 (`cors.allowed-origins`)

### 2-9. UseCase 단위 테스트

> ⏱ 40m | 선행: 2-2, 2-3, 2-4

- [x] `RegisterWaitingUseCaseImplTest` 작성 (Mockito)
    - 정상 등록 시 waitingNumber, currentRank 계산 검증
    - 존재하지 않는 storeId 입력 시 예외 발생 검증
- [x] `GetWaitingStatusUseCaseImplTest` 작성
    - currentRank 정확성 검증 (대기열 3번째 → rank=3, estimatedWait=10분)
    - 취소된 웨이팅 조회 시 예외 발생 검증
- [x] `CancelWaitingUseCaseImplTest` 작성
    - 정상 취소, 존재하지 않는 ID, 이미 취소된 웨이팅 재취소 3케이스

### 2-10. Controller 통합 테스트

> ⏱ 30m | 선행: 2-5, 2-6, 2-7

- [x] `StoreControllerTest` 작성 (`@WebMvcTest`)
    - 존재하는 QR코드 조회 성공 케이스
    - 존재하지 않는 QR코드 → 404 응답 검증
- [x] `WaitingControllerTest` 작성
    - 웨이팅 등록 성공 → 201 응답 + 응답 바디 필드 검증
    - `partySize = 0` 입력 → 400 응답 검증

### 2-11. 매장 등록 Request/Response DTO 정의

> ⏱ 15m | 선행: 1-1

- [x] `application/dto/CreateStoreRequest.java` 생성
    - 필드: `name (String)`
    - `@NotBlank(message = "매장 이름은 필수입니다.")` 검증 어노테이션 추가
- [x] `application/dto/CreateStoreResponse.java` 생성
    - 필드: `storeId (UUID)`, `name (String)`, `qrUrl (String)`

### 2-12. CreateStoreUseCase 구현

> ⏱ 20m | 선행: 1-3, 2-11

- [x] `application/usecase/CreateStoreUseCase.java` 인터페이스 정의
    - `execute(CreateStoreRequest request): CreateStoreResponse`
- [x] `application/usecase/CreateStoreUseCaseImpl.java` 구현
    - `Store.create(name)` 팩토리 메서드로 도메인 객체 생성
    - `StoreRepository.save()` 호출하여 영속화
    - `qrUrl` 조립: 설정값(base URL) + `/wait?storeId=` + `store.getId()`
    - `CreateStoreResponse` 반환

### 2-13. StoreController에 POST 엔드포인트 추가

> ⏱ 15m | 선행: 2-5, 2-12

- [x] `StoreController`에 `CreateStoreUseCase` 의존성 주입
- [x] `POST /api/stores` 엔드포인트 추가
    - `@Valid @RequestBody CreateStoreRequest` 파라미터
    - `201 Created` 응답 + `Location` 헤더 (`/api/stores/{storeId}`)
    - `CreateStoreResponse` 반환

### 2-14. 매장 등록 테스트 및 시드 데이터

> ⏱ 20m | 선행: 2-13

- [x] `CreateStoreUseCaseImplTest` 작성 (Mockito)
    - 정상 등록 시 storeId, name, qrUrl 반환 검증
    - name 미입력 시 검증 실패 확인
- [x] `StoreControllerTest`에 매장 등록 테스트 추가
    - `POST /api/stores` 성공 → 201 응답 + 응답 바디 필드 검증
    - name 누락 → 400 응답 검증
- [x] 시드 데이터 Flyway 스크립트 작성
    - `V3__seed_test_stores.sql` — 테스트용 매장 2~3건 INSERT
    - 개발 환경 전용 (`spring.flyway.locations`에 dev 프로필 분리 또는 주석 안내)

### 2-15. QR 코드 이미지 생성 API

> ⏱ 20m | 선행: 2-13

- [x] `GET /api/stores/{storeId}/qr` 엔드포인트 추가 (`StoreController`)
    - ZXing(`com.google.zxing`) 라이브러리로 QR PNG 이미지 생성
    - `ResponseEntity<byte[]>` 로 `image/png` Content-Type 반환
- [x] `build.gradle` 에 ZXing 의존성 추가 (`core`, `javase`)

---

## PHASE 3. 백엔드 — 실시간 SSE

> 예상 소요시간: **2h 00m**

### 3-1. SseEmitterRegistry 구현

> ⏱ 30m | 선행: 0-3

- [x] `infrastructure/sse/SseEmitterRegistry.java` 생성
    - 내부 자료구조: `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`
    - `register(UUID storeId, SseEmitter emitter)` 메서드 구현
    - `remove(UUID storeId, SseEmitter emitter)` 메서드 구현
    - `broadcast(UUID storeId, String eventName, Object data)` 메서드 구현
        - `emitter.send()` 실패 시 해당 Emitter 제거 처리

### 3-2. WaitingSseService 구현

> ⏱ 20m | 선행: 3-1

- [x] `infrastructure/sse/WaitingSseService.java` 생성
    - `subscribe(UUID storeId, UUID waitingId): SseEmitter` 메서드 구현
        - `SseEmitter` 생성 (timeout: 30분)
        - `onCompletion`, `onTimeout`, `onError` 콜백 등록 → Registry에서 제거
        - Registry에 등록 후 즉시 초기 이벤트(`waiting-update`) 전송
    - `broadcastUpdate(UUID storeId)` 메서드 구현
        - 해당 매장의 현재 대기 현황 조회 후 브로드캐스트

### 3-3. SSE 엔드포인트 추가 (WaitingController)

> ⏱ 15m | 선행: 2-6, 3-2

- [x] `WaitingController` 에 SSE 엔드포인트 추가
    - `GET /api/waitings/{waitingId}/stream`
    - `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
    - `WaitingSseService.subscribe()` 호출 후 `SseEmitter` 반환

### 3-4. 상태 변경 시 SSE 브로드캐스트 연결

> ⏱ 20m | 선행: 2-4, 3-2

- [x] `CancelWaitingUseCaseImpl` 에서 취소 완료 후 `WaitingSseService.broadcastUpdate()` 호출
- [x] (시뮬레이션용) `PUT /api/waitings/{waitingId}/enter` 임시 엔드포인트 추가
    - 입장 처리 후 `broadcastUpdate()` 호출 (Phase 2 점주 대시보드 구현 전 테스트용)

### 3-5. SSE 동작 통합 테스트

> ⏱ 35m | 선행: 3-3, 3-4

- [x] `SseEmitterRegistryTest` 작성
    - `broadcast` 시 등록된 모든 Emitter에 이벤트 전송 검증
    - 연결 해제된 Emitter 자동 제거 검증
- [x] Postman 또는 `curl` 로 SSE 스트림 수동 테스트
    - `curl -N http://localhost:8080/api/waitings/{id}/stream`
    - 다른 터미널에서 입장 처리 API 호출 후 이벤트 수신 확인

---

## PHASE 4. 프론트엔드 — 프로젝트 셋업

> 예상 소요시간: **1h 00m**

### 4-1. 라우팅 구성

> ⏱ 15m | 선행: 0-4

- [x] `react-router-dom` 으로 라우트 설정
    - `/wait` → `LandingPage`
    - `/waiting/:waitingId` → `WaitingConfirmPage`
    - `/waiting/:waitingId/status` → `WaitingStatusPage`
    - `/waiting/:waitingId/cancel` → `CancelPage`
- [x] 존재하지 않는 경로 → 404 페이지 추가

### 4-2. API 클라이언트 설정

> ⏱ 15m | 선행: 0-4

- [x] `src/api/client.ts` 생성 (axios instance)
    - baseURL: `/api`
    - 공통 에러 인터셉터 설정 (4xx/5xx 처리)
- [x] `src/api/waiting.ts` 생성
    - `getStore(storeId)`, `registerWaiting(storeId, body)`, `getWaiting(waitingId)`, `cancelWaiting(waitingId)` 함수 구현 — ~~qrCode → storeId 변경 (1-9 참고)~~

### 4-3. 전역 상태 설정 (Zustand)

> ⏱ 15m | 선행: 0-4

- [x] `src/store/waitingStore.ts` 생성
    - 상태: `waitingId`, `waitingNumber`, `waitingToken`, `currentRank`, `totalWaiting`, `estimatedWaitMinutes`
    - 액션: `setWaiting()`, `updateStatus()`, `clearWaiting()`

### 4-4. 공통 컴포넌트 구현

> ⏱ 15m | 선행: 4-1

- [x] `src/components/LoadingSpinner.tsx` 구현
- [x] `src/components/ErrorMessage.tsx` 구현
- [x] `src/components/Button.tsx` 구현 (primary / secondary variant)

---

## PHASE 5. 프론트엔드 — 페이지 구현

> 예상 소요시간: **3h 30m**

### 5-1. LandingPage 구현

> ⏱ 40m | 선행: 4-1, 4-2, 4-3

- [x] URL에서 `storeId` 쿼리 파라미터 파싱
- [x] 매장 정보 API 호출 (`GET /api/stores/{storeId}`) 및 매장명 표시
- [x] 웨이팅 등록 폼 구현
    - 이름 입력 필드 (최대 50자)
    - 인원수 선택 (1~10명, stepper UI)
    - '웨이팅 등록' 버튼
- [x] 중복 등록 방지: `localStorage` 에 `waitingToken` 존재 시 `WaitingStatusPage` 로 리다이렉트
- [x] 등록 성공 시 `WaitingConfirmPage` 로 이동

### 5-2. WaitingConfirmPage 구현

> ⏱ 25m | 선행: 4-1, 4-3

- [x] Zustand store에서 웨이팅 정보 표시
    - 웨이팅 번호, 현재 대기 순서, 예상 대기시간
- [x] '실시간 현황 보기' 버튼 → `WaitingStatusPage` 로 이동
- [x] '웨이팅 취소' 버튼 → `CancelPage` 로 이동

### 5-3. WaitingStatusPage 구현 (핵심)

> ⏱ 60m | 선행: 4-2, 4-3

- [x] 페이지 진입 시 `GET /api/waitings/{waitingId}` 호출하여 초기 상태 로드
- [x] SSE 연결 구현
  - `EventSource` 생성: `/api/waitings/${waitingId}/stream?storeId=${storeId}`
  - `waiting-update` 이벤트 수신 → API 재조회 → Zustand store 업데이트 → UI 갱신
    - `called` 이벤트 수신 → "입장해 주세요!" 모달 표시
    - 컴포넌트 언마운트 시 `eventSource.close()` 호출
- [x] 표시 UI 구현
    - 내 웨이팅 번호 (크게 표시)
    - 현재 대기 순서 (예: "현재 3번째")
    - 앞에 대기 중인 팀 수
    - 예상 대기시간
    - 연결 상태 인디케이터 (연결 중 / 실시간 업데이트 중)
- [x] 새로고침 시 session(localStorage)에서 storeId/waitingNumber 복원, API로 현황 재조회
- [x] '웨이팅 취소' 버튼 → `CancelPage` 로 이동
- [x] (prep) `session.ts` — `storeId`, `waitingNumber` 필드 추가
- [x] (prep) `waitingStore.ts` — `storeId` 상태 추가
- [x] (prep) `LandingPage.tsx` — 등록 시 `storeId` store/session에 저장

### 5-4. CancelPage 구현

> ⏱ 20m | 선행: 4-2, 4-3

- [x] 취소 확인 UI ("웨이팅을 취소하시겠습니까?")
- [x] '확인' 버튼 → `DELETE /api/waitings/{waitingId}` 호출
- [x] 취소 성공 시 `localStorage` 의 `waitingToken` 삭제 + Zustand store 초기화
- [x] 취소 완료 메시지 표시 후 루트(`/`) 로 이동
- [x] '돌아가기' 버튼 → `WaitingStatusPage` 로 이동

### 5-5. 모바일 반응형 스타일 적용

> ⏱ 30m | 선행: 5-1, 5-2, 5-3, 5-4

- [x] 전체 페이지 모바일 뷰포트 기준 레이아웃 (max-width: 480px) — 각 페이지 인라인 스타일로 적용됨
- [x] 터치 친화적 버튼 크기 (최소 44px height) — Button 컴포넌트 minHeight: 44px
- [x] 인원수 stepper 터치 영역 최적화 — 44×44px 고정
- [x] `index.css` 리셋: Vite 보일러플레이트 제거, `#root` 모바일 기준으로 정리
- [x] `index.html` viewport에 `maximum-scale=1.0` 추가 (iOS Safari 입력 시 자동 줌 방지)

### 5-6. 에러 상태 처리

> ⏱ 20m | 선행: 5-1, 5-2, 5-3

- [x] `LandingPage`: 유효하지 않은 `storeId` → 에러 메시지 표시 (5-1에서 구현됨)
- [x] `WaitingStatusPage`: SSE 연결 실패 시 자동 재연결 로직 추가 (최대 3회, 3초 간격)
- [x] `WaitingStatusPage`: 웨이팅이 이미 취소/입장 완료된 경우 안내 화면 표시 + session/store 초기화
- [x] 네트워크 오류 공통 처리 — axios 인터셉터에서 백엔드 `{ message }` 추출하여 `Error`로 래핑

### 5-7. localStorage 세션 관리

> ⏱ 15m | 선행: 5-1

- [x] `src/utils/session.ts` 유틸 생성 (5-3 prep에서 구현)
  - `saveWaitingSession(waitingId, waitingToken, storeId, waitingNumber)` 함수
  - `getWaitingSession(): { waitingId, waitingToken, storeId, waitingNumber } | null` 함수
    - `clearWaitingSession()` 함수
- [x] 앱 초기 진입 시 세션 확인 → 유효한 세션 있으면 `WaitingStatusPage` 로 리다이렉트 (5-1에서 구현)

### 5-8. 프론트엔드 테스트

> ⏱ 30m | 선행: 5-1, 5-2, 5-3, 5-4

- [x] `LandingPage` 렌더링 테스트 — 테스트/설계 팀 통합 테스트로 대체
- [x] `WaitingStatusPage` SSE 이벤트 수신 테스트 — 테스트/설계 팀 통합 테스트로 대체

### 5-9. OwnerPage 구현 (점주 QR 코드 생성 페이지)

> ⏱ 30m | 선행: 4-2, 2-15

- [x] `src/pages/OwnerPage.tsx` 구현
    - 매장명 입력 폼
    - 제출 시 `POST /api/stores` 호출 → `storeId` 획득
  - `<img src={getStoreQrUrl(storeId)}>` 로 QR 이미지 표시
  - QR 스캔 URL 및 매장 ID 안내 표시
- [x] `App.tsx` 에 `/owner` 라우트 추가
- [x] `src/api/waiting.ts` 에 `createStore`, `getStoreQrUrl` 함수 추가

---

## PHASE 6. 연동 & 통합 테스트

> 예상 소요시간: **2h 00m**

### 6-1. 백엔드-프론트엔드 Happy Path 통합 테스트

> ⏱ 30m | 선행: Phase 2, Phase 5 완료

- [x] 로컬 환경에서 전체 플로우 수동 테스트
    - QR URL 접속 → 매장 정보 표시 확인
    - 이름/인원수 입력 → 웨이팅 등록 완료 확인
    - 웨이팅 번호 및 현재 순서 표시 확인
- [x] 두 개 브라우저 탭으로 SSE 실시간 업데이트 확인
    - 탭 A: 웨이팅 등록 (1번 대기)
    - 탭 B: 웨이팅 등록 (2번 대기)
    - 임시 입장 처리 API 호출 → 탭 B의 순서가 1로 갱신되는지 확인

### 6-2. 예외 시나리오 테스트

> ⏱ 30m | 선행: 6-1

- [x] 존재하지 않는 QR코드로 접속 → 에러 메시지 표시 확인
- [x] 같은 기기에서 중복 등록 시도 → 기존 웨이팅 페이지로 리다이렉트 확인
- [x] 웨이팅 취소 후 해당 waitingId로 접속 → 안내 메시지 표시 확인
- [x] SSE 연결 중 서버 재시작 → 자동 재연결 동작 확인

### 6-3. 비기능 요구사항 검증

> ⏱ 30m | 선행: 6-1

- [x] 페이지 최초 로딩 시간 측정 (Chrome DevTools Lighthouse)
    - LCP ≤ 3초 목표 충족 여부 확인
- [x] SSE 이벤트 수신 지연 측정
    - 상태 변경 API 호출 후 클라이언트 UI 갱신까지 ≤ 1초 확인
- [x] 모바일 브라우저 (iOS Safari, Android Chrome) 에서 전체 플로우 확인

### 6-4. API 문서 자동화

> ⏱ 20m | 선행: Phase 2 완료

- [x] `springdoc-openapi` 의존성 추가
- [x] `application.yml` 에 Swagger UI 경로 설정
- [x] 주요 API에 `@Operation`, `@ApiResponse` 어노테이션 추가
- [x] `http://localhost:8080/swagger-ui.html` 접속 확인

### 6-5. 환경변수 및 설정 파일 정리

> ⏱ 10m | 선행: Phase 1~5 완료

- [ ] `application.yml` → `application-local.yml` / `application-prod.yml` 분리
- [ ] 민감 정보 (DB 비밀번호 등) `.env` 파일로 분리
- [ ] 프론트엔드 `.env.local` / `.env.production` 분리 (`VITE_API_BASE_URL`)

### 6-6. 통합 테스트 중 발견된 버그 수정 (프론트엔드)

> ⏱ 30m | 선행: 6-2

> **배경:** 6-2 예외 시나리오 테스트에서 발견된 두 가지 버그

#### Bug A — 취소된 웨이팅으로 직접 접속 시 "종료 안내" 화면 미표시 ✅ 수정 완료

- **파일:** `src/pages/WaitingStatusPage.tsx`
- **수정:** `initialized` 상태 추가. 리다이렉트 조건을 `!resolvedStoreId && initialized && !expired`로 강화.
  API 응답 전 리다이렉트 차단, `.finally()`에서 `setInitialized(true)` 호출

#### Bug B — 서버 일시 다운 시 세션 삭제로 재연결 불가 ✅ 수정 완료

- **파일:** `src/api/client.ts`, `src/pages/WaitingStatusPage.tsx`
- **수정:** 인터셉터에서 `err.status = error.response?.status` 보존.
  catch 분기: `404` → 세션 삭제 + expired 화면 / 그 외 → 세션 유지 + 에러 메시지 표시

---

## PHASE 7. 배포 구성

> 예상 소요시간: **1h 30m**

### 7-1. 백엔드 Dockerfile 작성

> ⏱ 15m | 선행: Phase 2 완료

- [ ] `backend/Dockerfile` 작성 (멀티 스테이지 빌드)
    - Build stage: `gradle build -x test`
    - Run stage: `eclipse-temurin:21-jre-alpine` 베이스 이미지
- [ ] `.dockerignore` 작성
- [ ] `docker build` 로 이미지 빌드 확인

### 7-2. 프론트엔드 Dockerfile 작성

> ⏱ 15m | 선행: Phase 5 완료

- [ ] `frontend/Dockerfile` 작성 (멀티 스테이지 빌드)
    - Build stage: `node:20-alpine` + `npm run build`
    - Run stage: `nginx:alpine` + 빌드 산출물 복사
- [ ] `frontend/nginx.conf` 작성
    - SPA 라우팅 처리 (`try_files $uri /index.html`)
    - `/api/` 요청 백엔드로 프록시 설정

### 7-3. 프로덕션 Docker Compose 작성

> ⏱ 20m | 선행: 7-1, 7-2

- [ ] 루트에 `docker-compose.yml` 작성 (TRD 배포 구성 기반)
    - `api`, `frontend`, `db`, `redis` 서비스 정의
    - 서비스 간 healthcheck 및 `depends_on` 설정
    - `postgres_data` 볼륨 설정
- [ ] `.env.example` 파일 작성 (필수 환경변수 목록)

### 7-4. 전체 스택 Docker Compose 기동 확인

> ⏱ 20m | 선행: 7-3

- [ ] `docker-compose up --build` 실행
- [ ] 각 서비스 로그 확인 (DB 마이그레이션 완료, API 서버 기동, Nginx 기동)
- [ ] `http://localhost` 접속 후 전체 플로우 동작 확인
- [ ] `docker-compose down -v` 후 재기동 시 데이터 초기화 여부 확인 (볼륨 설정 검증)

### 7-5. 루트 README.md 최종 업데이트

> ⏱ 20m | 선행: 7-4

- [ ] 프로젝트 소개 및 주요 기능 기술
- [ ] 로컬 개발 환경 실행 방법 (`docker-compose.dev.yml`)
- [ ] 프로덕션 배포 방법 (`docker-compose.yml`)
- [ ] 환경변수 설명 테이블 추가
- [ ] API 문서 접근 경로 안내 (Swagger UI)
