# 백엔드 액터별 서브패키지 분리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `waiting`·`store` 애그리거트의 application/presentation을 손님(`customer/`)·점주(`management/`) 서브패키지로 분리해 가독성을 높인다. 동작 변경 없음.

**Architecture:** 순수 리팩토링. `domain/`·`infrastructure/`는 공유 커널로 그대로 두고, 액터별 서비스·컨트롤러·전용 DTO만 서브패키지로 이동한다. `StoreService`는 손님용(`StoreViewService`)과 점주용으로 분할한다. 컴포넌트 스캔 베이스(`com.qrwait.api`)는 그대로라 빈 탐색은 영향 없으며, 매 태스크 끝에서 전체 빌드가 그린이면 회귀가 없다는 뜻이다.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, JUnit 5, Mockito, Gradle.

## Global Constraints

- 컴포넌트 스캔 베이스 패키지는 `com.qrwait.api`. 모든 이동은 이 하위에서만 한다 (빈 탐색 유지).
- **동작 변경 금지**: API 경로·HTTP 상태·요청/응답 스키마·비즈니스 로직 동일. 코드 이동·리네임·분할만 한다.
- 계층 규칙 준수 (`backend/CLAUDE.md`): domain은 어떤 계층도 import 안 함, presentation은 application만 의존.
- `domain/`·`infrastructure/` 패키지 내용물은 변경하지 않는다.
- 검증 명령은 항상 `backend/` 디렉터리에서 `./gradlew build` (컴파일 + 전체 테스트).
- 커밋 메시지는 한글.
- 액터 컨텍스트 패키지명은 `customer` / `management`.

## File Structure (이동 매핑 총괄)

**waiting** (Task 1·2)
| 현재 | 이동 후 |
|---|---|
| `waiting/application/WaitingService.java` | `waiting/customer/application/WaitingService.java` |
| `waiting/presentation/WaitingController.java` | `waiting/customer/presentation/WaitingController.java` |
| `waiting/application/dto/RegisterWaitingRequest.java` | `waiting/customer/dto/RegisterWaitingRequest.java` |
| `waiting/application/dto/RegisterWaitingResponse.java` | `waiting/customer/dto/RegisterWaitingResponse.java` |
| `waiting/application/dto/MyWaitingStatusResponse.java` | `waiting/customer/dto/MyWaitingStatusResponse.java` |
| `waiting/application/dto/WaitingStatusResponse.java` | `waiting/customer/dto/WaitingStatusResponse.java` |
| `waiting/application/WaitingManagementService.java` | `waiting/management/application/WaitingManagementService.java` |
| `waiting/presentation/OwnerWaitingController.java` | `waiting/management/presentation/OwnerWaitingController.java` |
| `waiting/application/dto/OwnerWaitingResponse.java` | `waiting/management/dto/OwnerWaitingResponse.java` |
| `waiting/application/dto/DailySummaryResponse.java` | `waiting/management/dto/DailySummaryResponse.java` |
| `waiting/application/dto/TodayWaitingResponse.java` | `waiting/management/dto/TodayWaitingResponse.java` |

**store** (Task 3·4)
| 현재 | 이동 후 |
|---|---|
| `store/application/StoreService.java` (손님 메서드 분리) | `store/customer/application/StoreViewService.java` (신규) |
| `store/presentation/StoreController.java` | `store/customer/presentation/StoreController.java` |
| `store/application/StoreService.java` (점주 메서드만 남김) | `store/management/application/StoreService.java` |
| `store/application/StoreSettingsService.java` | `store/management/application/StoreSettingsService.java` |
| `store/presentation/OwnerStoreController.java` | `store/management/presentation/OwnerStoreController.java` |
| `store/application/dto/StoreSettingsResponse.java` | `store/management/dto/StoreSettingsResponse.java` |
| `store/application/dto/UpdateStoreInfoRequest.java` | `store/management/dto/UpdateStoreInfoRequest.java` |
| `store/application/dto/UpdateStoreSettingsRequest.java` | `store/management/dto/UpdateStoreSettingsRequest.java` |
| `store/application/dto/UpdateStoreStatusRequest.java` | `store/management/dto/UpdateStoreStatusRequest.java` |
| `store/application/dto/StoreResponse.java` | **이동 안 함** (공유 DTO, 중립 위치 유지) |

**변경 없음:** `waiting/domain`, `waiting/infrastructure`, `store/domain`, `store/infrastructure`, `owner/**`, `shared/**`.

---

### Task 1: waiting 손님(customer) 서브패키지 이동

**Files:**
- Move: `WaitingService.java`, `WaitingController.java`, `RegisterWaitingRequest/Response.java`, `MyWaitingStatusResponse.java`, `WaitingStatusResponse.java` (위 매핑표 waiting customer 행)
- Modify (import 갱신, 이동 안 함): `store/presentation/StoreController.java`, `shared/sse/SsePublisher.java`
- Test move: `WaitingServiceTest.java` → `waiting/customer/application/`, `WaitingControllerTest.java` → `waiting/customer/presentation/`
- Test modify (import 갱신): `store/presentation/StoreControllerTest.java`, `store/presentation/OwnerStoreControllerTest.java`(있다면 customer DTO 참조), `waiting/presentation/OwnerWaitingControllerTest.java`(있다면 customer DTO 참조)

**Interfaces:**
- Produces:
  - `com.qrwait.api.waiting.customer.application.WaitingService` — `register(UUID, RegisterWaitingRequest): RegisterWaitingResponse`, `getStatus(UUID): MyWaitingStatusResponse`, `cancel(UUID): void`, `getStoreWaitingStatus(UUID): WaitingStatusResponse`
  - `com.qrwait.api.waiting.customer.dto.WaitingStatusResponse` (record)
  - 나머지 customer DTO는 `com.qrwait.api.waiting.customer.dto.*`

- [ ] **Step 1: 새 디렉터리 생성 후 customer 클래스 이동**

`backend/` 기준으로 실행:
```bash
cd backend/src/main/java/com/qrwait/api/waiting
mkdir -p customer/application customer/presentation customer/dto
git mv application/WaitingService.java customer/application/WaitingService.java
git mv presentation/WaitingController.java customer/presentation/WaitingController.java
git mv application/dto/RegisterWaitingRequest.java customer/dto/RegisterWaitingRequest.java
git mv application/dto/RegisterWaitingResponse.java customer/dto/RegisterWaitingResponse.java
git mv application/dto/MyWaitingStatusResponse.java customer/dto/MyWaitingStatusResponse.java
git mv application/dto/WaitingStatusResponse.java customer/dto/WaitingStatusResponse.java
```

- [ ] **Step 2: 이동한 파일들의 `package` 선언과 내부 import 갱신**

각 파일의 `package` 줄을 새 위치로 바꾼다:
- `WaitingService.java`: `package com.qrwait.api.waiting.customer.application;` + DTO import를 `com.qrwait.api.waiting.customer.dto.*`로
- `WaitingController.java`: `package com.qrwait.api.waiting.customer.presentation;` + `WaitingService`/DTO import를 customer 경로로
- 4개 DTO: `package com.qrwait.api.waiting.customer.dto;`

(domain·store domain import는 그대로 유지 — 공유 커널은 안 움직임)

- [ ] **Step 3: 외부 참조자 import 갱신**

- `shared/sse/SsePublisher.java`: `import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;` → `import com.qrwait.api.waiting.customer.dto.WaitingStatusResponse;`
- `store/presentation/StoreController.java`:
  - `import com.qrwait.api.waiting.application.WaitingService;` → `...waiting.customer.application.WaitingService;`
  - `import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;` → `...waiting.customer.dto.WaitingStatusResponse;`

- [ ] **Step 4: 테스트 이동 및 import 갱신**

```bash
cd backend/src/test/java/com/qrwait/api/waiting
mkdir -p customer/application customer/presentation
git mv application/WaitingServiceTest.java customer/application/WaitingServiceTest.java
git mv presentation/WaitingControllerTest.java customer/presentation/WaitingControllerTest.java
```
- 두 테스트의 `package`를 `...waiting.customer.application` / `...waiting.customer.presentation`로 바꾸고, `WaitingService`·customer DTO import를 customer 경로로 갱신.
- `store/presentation/StoreControllerTest.java`: `import com.qrwait.api.waiting.application.WaitingService;` → `...waiting.customer.application.WaitingService;`
- `OwnerWaitingControllerTest.java`에 customer DTO(예: `WaitingStatusResponse`) import가 있으면 customer 경로로 갱신 (없으면 생략).

- [ ] **Step 5: 빌드 + 전체 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS. (실패 시 누락된 import 경로를 컴파일 에러 메시지로 추적해 수정)

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor: waiting 손님 코드를 customer 서브패키지로 분리"
```

---

### Task 2: waiting 점주(management) 서브패키지 이동

**Files:**
- Move: `WaitingManagementService.java`, `OwnerWaitingController.java`, `OwnerWaitingResponse.java`, `DailySummaryResponse.java`, `TodayWaitingResponse.java` (매핑표 waiting management 행)
- Test move: `WaitingManagementServiceTest.java` → `waiting/management/application/`, `OwnerWaitingControllerTest.java` → `waiting/management/presentation/`

**Interfaces:**
- Consumes: (Task 1과 무관 — 점주 코드는 customer 클래스를 참조하지 않음)
- Produces:
  - `com.qrwait.api.waiting.management.application.WaitingManagementService` — `getWaitingList(UUID): List<OwnerWaitingResponse>`, `getDailySummary(UUID): DailySummaryResponse`, `call/enter/noShow(UUID, UUID): void`, `getTodayWaitings(UUID): List<TodayWaitingResponse>`, `subscribeOwnerDashboard(UUID): SseEmitter`
  - management DTO는 `com.qrwait.api.waiting.management.dto.*`

- [ ] **Step 1: 새 디렉터리 생성 후 management 클래스 이동**

```bash
cd backend/src/main/java/com/qrwait/api/waiting
mkdir -p management/application management/presentation management/dto
git mv application/WaitingManagementService.java management/application/WaitingManagementService.java
git mv presentation/OwnerWaitingController.java management/presentation/OwnerWaitingController.java
git mv application/dto/OwnerWaitingResponse.java management/dto/OwnerWaitingResponse.java
git mv application/dto/DailySummaryResponse.java management/dto/DailySummaryResponse.java
git mv application/dto/TodayWaitingResponse.java management/dto/TodayWaitingResponse.java
```

- [ ] **Step 2: `package` 선언과 import 갱신**

- `WaitingManagementService.java`: `package com.qrwait.api.waiting.management.application;` + DTO import를 `...waiting.management.dto.*`로. (`SsePublisher`, store domain, waiting domain import는 그대로)
- `OwnerWaitingController.java`: `package com.qrwait.api.waiting.management.presentation;` + `WaitingManagementService`/DTO import를 management 경로로
- 3개 DTO: `package com.qrwait.api.waiting.management.dto;`
- `DailySummaryResponse.java`는 `DailySummary` 도메인 VO를 import한다(`...waiting.domain.DailySummary`) — 이 import는 그대로 유지.

- [ ] **Step 3: 빈 폴더 정리**

이제 비었을 `waiting/application/dto`, `waiting/application`, `waiting/presentation` 폴더가 남아 있으면 삭제 (git은 빈 디렉터리를 추적하지 않으므로 OS 폴더만 정리).

- [ ] **Step 4: 테스트 이동 및 import 갱신**

```bash
cd backend/src/test/java/com/qrwait/api/waiting
mkdir -p management/application management/presentation
git mv application/WaitingManagementServiceTest.java management/application/WaitingManagementServiceTest.java
git mv presentation/OwnerWaitingControllerTest.java management/presentation/OwnerWaitingControllerTest.java
```
- 두 테스트의 `package`를 management 경로로 바꾸고, `WaitingManagementService`·management DTO import를 management 경로로 갱신.

- [ ] **Step 5: 빌드 + 전체 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 전체 PASS.

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor: waiting 점주 코드를 management 서브패키지로 분리"
```

---

### Task 3: store 손님(customer) 분리 — StoreService 분할 + StoreViewService 신설

**Files:**
- Create: `store/customer/application/StoreViewService.java`, `store/customer/application/StoreViewServiceTest.java`
- Move: `store/presentation/StoreController.java` → `store/customer/presentation/StoreController.java`
- Modify: `store/application/StoreService.java` (손님 메서드 제거 + `QrCodeGenerator`/`baseUrl` 의존 제거), `store/application/StoreServiceTest.java` (getStoreById 테스트 제거 + 생성자 변경)

**Interfaces:**
- Consumes: `com.qrwait.api.waiting.customer.application.WaitingService` (Task 1), `com.qrwait.api.waiting.customer.dto.WaitingStatusResponse` (Task 1)
- Produces:
  - `com.qrwait.api.store.customer.application.StoreViewService` — `getStoreById(UUID): StoreResponse`, `generateQrImage(UUID): byte[]`
  - 점주용 `com.qrwait.api.store.application.StoreService`는 `getMyStore`, `updateStoreInfo`, `updateStoreStatus`만 남고 생성자는 `(StoreRepository, ApplicationEventPublisher)`.

- [ ] **Step 1: `StoreViewService` 신설 (손님 메서드 이관)**

`backend/src/main/java/com/qrwait/api/store/customer/application/StoreViewService.java` 생성:
```java
package com.qrwait.api.store.customer.application;

import com.qrwait.api.shared.qr.QrCodeGenerator;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreViewService {

  private final StoreRepository storeRepository;
  private final QrCodeGenerator qrCodeGenerator;

  @Value("${app.base-url}")
  private String baseUrl;

  @Transactional(readOnly = true)
  public StoreResponse getStoreById(UUID storeId) {
    Store store = storeRepository.getById(storeId);
    return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getStatus());
  }

  public byte[] generateQrImage(UUID storeId) {
    storeRepository.getById(storeId);
    String qrUrl = baseUrl + "/wait?storeId=" + storeId;
    return qrCodeGenerator.generate(qrUrl);
  }
}
```

- [ ] **Step 2: 점주용 `StoreService`에서 손님 메서드·의존 제거**

`store/application/StoreService.java`를 다음과 같이 수정 — `getStoreById`, `generateQrImage`, `QrCodeGenerator` 필드, `baseUrl`, 관련 import 제거:
```java
package com.qrwait.api.store.application;

import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.store.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.event.StoreStatusChangedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {

  private final StoreRepository storeRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public StoreResponse getMyStore(UUID ownerId) {
    return toResponse(storeRepository.getByOwnerId(ownerId));
  }

  @Transactional
  public StoreResponse updateStoreInfo(UUID ownerId, UpdateStoreInfoRequest request) {
    Store store = storeRepository.getByOwnerId(ownerId);
    Store updated = storeRepository.save(store.updateInfo(request.getName(), request.getAddress()));
    return toResponse(updated);
  }

  @Transactional
  public StoreResponse updateStoreStatus(UUID ownerId, UpdateStoreStatusRequest request) {
    Store store = storeRepository.getByOwnerId(ownerId);
    Store updated = storeRepository.save(store.changeStatus(request.getStatus()));
    eventPublisher.publishEvent(new StoreStatusChangedEvent(updated.getId(), updated.getStatus()));
    return toResponse(updated);
  }

  private StoreResponse toResponse(Store store) {
    return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getStatus());
  }
}
```
> 이 단계에서 `StoreService`는 아직 `store/application/`에 둔다 (Task 4에서 management로 이동).

- [ ] **Step 3: `StoreController`를 customer로 이동하고 `StoreViewService` 사용**

```bash
cd backend/src/main/java/com/qrwait/api/store
mkdir -p customer/application customer/presentation
git mv presentation/StoreController.java customer/presentation/StoreController.java
```
`StoreController.java` 수정:
- `package com.qrwait.api.store.customer.presentation;`
- `import com.qrwait.api.store.application.StoreService;` → `import com.qrwait.api.store.customer.application.StoreViewService;`
- 필드 `private final StoreService storeService;` → `private final StoreViewService storeViewService;`
- 본문 `storeService.getStoreById(...)`/`storeService.generateQrImage(...)` → `storeViewService....`
- `WaitingService`/`WaitingStatusResponse` import는 이미 Task 1에서 customer 경로 (`...waiting.customer.application` / `...waiting.customer.dto`); 그대로 유지.
- `StoreResponse` import (`...store.application.dto.StoreResponse`) 그대로 유지 (공유 DTO).

- [ ] **Step 4: `StoreServiceTest`에서 손님 테스트 분리**

`store/application/StoreServiceTest.java` 수정:
- `getStoreById_정상_조회`, `getStoreById_존재하지않는_매장_예외발생` 두 테스트 제거.
- `QrCodeGenerator` mock 필드 및 import 제거.
- `setUp()`을 `storeService = new StoreService(storeRepository, eventPublisher);` 로, `ReflectionTestUtils.setField(..., "baseUrl", ...)` 줄 제거.
- (이 테스트 파일은 Task 4에서 management로 이동)

신규 `store/customer/application/StoreViewServiceTest.java` 생성:
```java
package com.qrwait.api.store.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.shared.qr.QrCodeGenerator;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreViewServiceTest {

  private final UUID storeId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  @Mock
  StoreRepository storeRepository;
  @Mock
  QrCodeGenerator qrCodeGenerator;

  StoreViewService storeViewService;

  @BeforeEach
  void setUp() {
    storeViewService = new StoreViewService(storeRepository, qrCodeGenerator);
    ReflectionTestUtils.setField(storeViewService, "baseUrl", "http://localhost:5173");
  }

  @Test
  void getStoreById_정상_조회() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.getById(storeId)).willReturn(store);

    StoreResponse response = storeViewService.getStoreById(storeId);

    assertThat(response.storeId()).isEqualTo(storeId);
  }

  @Test
  void getStoreById_존재하지않는_매장_예외발생() {
    given(storeRepository.getById(storeId)).willThrow(new StoreNotFoundException(storeId));

    assertThatThrownBy(() -> storeViewService.getStoreById(storeId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void generateQrImage_정상_생성() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.getById(storeId)).willReturn(store);
    given(qrCodeGenerator.generate("http://localhost:5173/wait?storeId=" + storeId))
        .willReturn(new byte[]{1, 2, 3});

    byte[] image = storeViewService.generateQrImage(storeId);

    assertThat(image).hasSize(3);
  }
}
```

- [ ] **Step 5: `StoreControllerTest` 이동 및 mock 교체**

```bash
cd backend/src/test/java/com/qrwait/api/store
mkdir -p customer/presentation
git mv presentation/StoreControllerTest.java customer/presentation/StoreControllerTest.java
```
`StoreControllerTest.java` 수정:
- `package com.qrwait.api.store.customer.presentation;`
- `import com.qrwait.api.store.application.StoreService;` → `import com.qrwait.api.store.customer.application.StoreViewService;`
- `@MockitoBean StoreService storeService;` → `@MockitoBean StoreViewService storeViewService;`
- 본문 `given(storeService.getStoreById(...))` → `given(storeViewService.getStoreById(...))`
- `import com.qrwait.api.waiting.application.WaitingService;` → `...waiting.customer.application.WaitingService;` (Task 1에서 이미 갱신했다면 유지)
- `StoreResponse` import 그대로.

- [ ] **Step 6: 빌드 + 전체 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 전체 PASS.

- [ ] **Step 7: 커밋**

```bash
git add -A
git commit -m "refactor: StoreService를 손님용 StoreViewService로 분할하고 customer 서브패키지 분리"
```

---

### Task 4: store 점주(management) 서브패키지 이동

**Files:**
- Move: `store/application/StoreService.java` → `store/management/application/StoreService.java`, `store/application/StoreSettingsService.java` → `store/management/application/StoreSettingsService.java`, `store/presentation/OwnerStoreController.java` → `store/management/presentation/OwnerStoreController.java`
- Move DTO: `StoreSettingsResponse.java`, `UpdateStoreInfoRequest.java`, `UpdateStoreSettingsRequest.java`, `UpdateStoreStatusRequest.java` → `store/management/dto/`
- Test move: `StoreServiceTest.java` → `store/management/application/`, `StoreSettingsServiceTest.java` → `store/management/application/`, `OwnerStoreControllerTest.java` → `store/management/presentation/`
- Keep: `store/application/dto/StoreResponse.java` (이동 안 함)

**Interfaces:**
- Consumes: (없음 — customer/waiting 코드를 참조하지 않음)
- Produces:
  - `com.qrwait.api.store.management.application.StoreService` — `getMyStore`, `updateStoreInfo`, `updateStoreStatus`
  - `com.qrwait.api.store.management.application.StoreSettingsService` — `getSettings(UUID)`, `updateSettings(UUID, UpdateStoreSettingsRequest)`
  - management DTO는 `com.qrwait.api.store.management.dto.*`

- [ ] **Step 1: management 클래스 이동**

```bash
cd backend/src/main/java/com/qrwait/api/store
mkdir -p management/application management/presentation management/dto
git mv application/StoreService.java management/application/StoreService.java
git mv application/StoreSettingsService.java management/application/StoreSettingsService.java
git mv presentation/OwnerStoreController.java management/presentation/OwnerStoreController.java
git mv application/dto/StoreSettingsResponse.java management/dto/StoreSettingsResponse.java
git mv application/dto/UpdateStoreInfoRequest.java management/dto/UpdateStoreInfoRequest.java
git mv application/dto/UpdateStoreSettingsRequest.java management/dto/UpdateStoreSettingsRequest.java
git mv application/dto/UpdateStoreStatusRequest.java management/dto/UpdateStoreStatusRequest.java
```

- [ ] **Step 2: `package` 선언 및 import 갱신**

- `StoreService.java`: `package com.qrwait.api.store.management.application;` + `UpdateStoreInfoRequest`/`UpdateStoreStatusRequest` import를 `...store.management.dto.*`로. `StoreResponse` import는 `...store.application.dto.StoreResponse` 유지.
- `StoreSettingsService.java`: `package com.qrwait.api.store.management.application;` + `StoreSettingsResponse`/`UpdateStoreSettingsRequest` import를 `...store.management.dto.*`로.
- `OwnerStoreController.java`: `package com.qrwait.api.store.management.presentation;` + `StoreService`/`StoreSettingsService` import를 `...store.management.application.*`로, `StoreSettingsResponse`/`UpdateStore*Request` import를 `...store.management.dto.*`로. `StoreResponse` import는 `...store.application.dto.StoreResponse` 유지.
- 4개 DTO: `package com.qrwait.api.store.management.dto;`

- [ ] **Step 3: 빈 폴더 정리**

`store/application` (StoreResponse가 있는 `application/dto`는 남김), `store/presentation`이 비었으면 OS 폴더 정리. `store/application/dto`는 `StoreResponse.java`가 남아 유지.

- [ ] **Step 4: 테스트 이동 및 import 갱신**

```bash
cd backend/src/test/java/com/qrwait/api/store
mkdir -p management/application management/presentation
git mv application/StoreServiceTest.java management/application/StoreServiceTest.java
git mv application/StoreSettingsServiceTest.java management/application/StoreSettingsServiceTest.java
git mv presentation/OwnerStoreControllerTest.java management/presentation/OwnerStoreControllerTest.java
```
- `StoreServiceTest.java`: `package com.qrwait.api.store.management.application;` + `UpdateStore*Request` import를 `...store.management.dto.*`로. (`StoreResponse` 유지)
- `StoreSettingsServiceTest.java`: `package com.qrwait.api.store.management.application;` + `StoreSettingsResponse`/`UpdateStoreSettingsRequest` import를 management 경로로.
- `OwnerStoreControllerTest.java`: `package com.qrwait.api.store.management.presentation;` + `StoreService`/`StoreSettingsService`/management DTO import를 management 경로로.

- [ ] **Step 5: 빌드 + 전체 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 전체 PASS.

- [ ] **Step 6: 최종 구조 점검**

다음을 확인:
- `store/application/dto/`에는 `StoreResponse.java`만 남아 있다.
- `store/customer/`, `store/management/`, `waiting/customer/`, `waiting/management/`가 application/presentation/dto로 채워져 있다.
- `owner/**`, `shared/**`, `*/domain/**`, `*/infrastructure/**`는 변경되지 않았다 (`git log --oneline`로 이번 4개 커밋 외 domain/infra 변경 없음 확인).

- [ ] **Step 7: 커밋**

```bash
git add -A
git commit -m "refactor: store 점주 코드를 management 서브패키지로 분리"
```

---

## Self-Review (작성자 체크 결과)

- **Spec coverage:** 스펙 §3.1(waiting)→Task 1·2, §3.2(store)→Task 3·4, §4.1(StoreService 분할)→Task 3 Step 1·2, §4.2(공유 DTO 유지)→매핑표/Task 4 Step 2·3, §4.3(owner 미분리)→이동 매핑에서 제외, §4.5(교차 의존)→Task 1 Step 3·Task 3 Step 3. 검증 기준 §6→각 Task의 빌드 Step + Task 4 Step 6. 누락 없음.
- **Placeholder scan:** "TODO/적절히 처리" 류 없음. 실제 코드·명령·경로 명시.
- **Type consistency:** `StoreViewService`(생성자 `(StoreRepository, QrCodeGenerator)` + `baseUrl`)가 Task 3 Step 1·4·5에서 일관. 점주 `StoreService` 생성자 `(StoreRepository, ApplicationEventPublisher)`가 Step 2·4에서 일관. waiting customer/management 패키지 경로가 Task 1·2·3 전반에서 일관.

## 비고: 알려진 사전 의존 (범위 밖)

`shared/sse/SsePublisher`가 `WaitingStatusResponse`(손님 DTO)를 직접 참조하는 구조적 냄새가 있으나, 이는 리팩토링 이전부터 존재한다. 본 작업은 import 경로만 갱신하고 구조 개선은 별도 과제로 둔다.
