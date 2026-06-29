# 백엔드 코드 품질 리팩토링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 동작을 보존하면서 백엔드의 중복을 제거하고 비즈니스 로직을 도메인으로 끌어올린다 (T1~T4 + 사소 정리).

**Architecture:** 가이드(`backend/CLAUDE.md`)의 Domain-first/YAGNI 원칙에 따라 ① 소유권 검증을 `WaitingEntry` 도메인 메서드로, ② `ownerId→Store` 조회를 `StoreRepository` 포트 default 메서드로 일원화, ③ 추정 대기시간 fallback을 서비스 헬퍼로 통일, ④ 일일 집계를 `DailySummary` 도메인 VO + GROUP BY 단일 쿼리로 도출한다. 기존 테스트가 동작 보존의 안전망이다.

**Tech Stack:** Java 21 · Spring Boot 3.5 · JPA · PostgreSQL · Flyway · JUnit5 · Mockito(BDD) · AssertJ · Gradle

## Global Constraints

- 빌드/테스트 실행은 `backend/` 디렉터리에서 `./gradlew` 사용.
- 도메인 계층은 순수 자바 — Spring/JPA import 및 다른 계층 import 금지 (`WaitingEntry`, `DailySummary`, `Store`, `StoreSettings`).
- getter는 Lombok `@Getter`만 사용. 도메인 생성=`create`/`createDefault`, 영속 복원=`restore`.
- 외부 응답 DTO(`*Response`)의 필드/의미는 변경하지 않는다.
- 소유권 위반은 `StoreNotFoundException`(404)으로 처리한다 (존재 은닉 — 의도된 보안 패턴).
- ⚠️ **Mockito 주의:** `StoreRepository`의 default 메서드(`getByOwnerId`/`getById`)는 mock에서 실제 본문이 실행되지 않는다. 서비스 단위 테스트는 `findByOwnerId`/`findById`가 아니라 **default 메서드 자체를 스텁**해야 한다 (정상은 `willReturn(store)`, 없음은 `willThrow(new StoreNotFoundException(...))`).
- 각 태스크는 작업·테스트 완료 후 **그 태스크의 변경만 작업 브랜치에 커밋**한다. 커밋 메시지는 한글로 작성하고 끝에 다음 줄을 포함한다: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. 최종 main 통합(squash/merge)은 사용자가 직접 한다.

---

## File Structure

생성:
- `src/test/java/com/qrwait/api/waiting/domain/WaitingEntryTest.java` — `belongsTo` 도메인 테스트
- `src/main/java/com/qrwait/api/waiting/domain/DailySummary.java` — 일일 집계 도메인 VO
- `src/test/java/com/qrwait/api/waiting/domain/DailySummaryTest.java` — 집계 단위 테스트

수정:
- `src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java` — `belongsTo` 추가
- `src/main/java/com/qrwait/api/store/domain/StoreRepository.java` — `getByOwnerId`/`getById` default 메서드
- `src/test/java/com/qrwait/api/store/infrastructure/StoreRepositoryImplTest.java` — default 메서드 통합 테스트
- `src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java` — `countByStatusForStoreAndDate` 포트
- `src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java` — GROUP BY 쿼리
- `src/main/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImpl.java` — Map 변환 구현
- `src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java` — 통합 테스트
- `src/main/java/com/qrwait/api/waiting/application/dto/DailySummaryResponse.java` — `from(DailySummary)` 팩토리
- `src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java` — T1 헬퍼 + T4 집계 + T2 적용
- `src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java` — 스텁 갱신
- `src/main/java/com/qrwait/api/waiting/application/WaitingService.java` — T3 헬퍼 + T5 주석
- `src/main/java/com/qrwait/api/store/application/StoreService.java` — T2 적용
- `src/test/java/com/qrwait/api/store/application/StoreServiceTest.java` — 스텁 갱신
- `src/main/java/com/qrwait/api/store/application/StoreSettingsService.java` — T2 적용
- `src/test/java/com/qrwait/api/store/application/StoreSettingsServiceTest.java` — 스텁 갱신
- `src/main/java/com/qrwait/api/owner/application/OwnerService.java` — T2 적용
- `src/test/java/com/qrwait/api/owner/application/OwnerServiceTest.java` — 스텁 갱신
- `src/main/java/com/qrwait/api/store/domain/StoreSettings.java` — 매직넘버 상수화

---

## Task 1: WaitingEntry.belongsTo (T1 도메인)

**Files:**
- Create: `src/test/java/com/qrwait/api/waiting/domain/WaitingEntryTest.java`
- Modify: `src/main/java/com/qrwait/api/waiting/domain/WaitingEntry.java`

**Interfaces:**
- Produces: `boolean WaitingEntry.belongsTo(UUID storeId)` — 엔트리의 `storeId`와 인자가 같으면 true.

- [ ] **Step 1: 실패하는 테스트 작성**

Create `src/test/java/com/qrwait/api/waiting/domain/WaitingEntryTest.java`:
```java
package com.qrwait.api.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaitingEntryTest {

  @Test
  void belongsTo_같은_매장이면_true() {
    UUID storeId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(
        UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now());

    assertThat(entry.belongsTo(storeId)).isTrue();
  }

  @Test
  void belongsTo_다른_매장이면_false() {
    WaitingEntry entry = WaitingEntry.restore(
        UUID.randomUUID(), UUID.randomUUID(), "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now());

    assertThat(entry.belongsTo(UUID.randomUUID())).isFalse();
  }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.domain.WaitingEntryTest"`
Expected: 컴파일 실패 — `cannot find symbol: method belongsTo(UUID)`

- [ ] **Step 3: 최소 구현 추가**

`WaitingEntry.java`의 `noShow()` 메서드 뒤(마지막 `}` 직전)에 추가:
```java

  public boolean belongsTo(UUID storeId) {
    return this.storeId.equals(storeId);
  }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.domain.WaitingEntryTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: WaitingEntry.belongsTo 소유권 검증 도메인 메서드 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 2: StoreRepository 포트 default 메서드 (T2)

**Files:**
- Modify: `src/main/java/com/qrwait/api/store/domain/StoreRepository.java`
- Test: `src/test/java/com/qrwait/api/store/infrastructure/StoreRepositoryImplTest.java`

**Interfaces:**
- Produces:
  - `Store StoreRepository.getByOwnerId(UUID ownerId)` — 없으면 `StoreNotFoundException("ownerId=" + ownerId)`.
  - `Store StoreRepository.getById(UUID storeId)` — 없으면 `StoreNotFoundException(storeId)`.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`StoreRepositoryImplTest.java`에 테스트 메서드 추가 (기존 `@DataJpaTest` 클래스 안, 마지막 `}` 직전). 기존 import에 다음이 없으면 추가: `import static org.assertj.core.api.Assertions.assertThatThrownBy;`, `import com.qrwait.api.store.domain.StoreNotFoundException;`, `import java.util.UUID;`.
```java
  @Test
  void getByOwnerId_매장_있으면_반환() {
    Store saved = storeRepository.save(Store.create(UUID.randomUUID(), "가게", "주소"));

    Store found = storeRepository.getByOwnerId(saved.getOwnerId());

    assertThat(found.getId()).isEqualTo(saved.getId());
  }

  @Test
  void getByOwnerId_매장_없으면_예외() {
    assertThatThrownBy(() -> storeRepository.getByOwnerId(UUID.randomUUID()))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void getById_매장_없으면_예외() {
    assertThatThrownBy(() -> storeRepository.getById(UUID.randomUUID()))
        .isInstanceOf(StoreNotFoundException.class);
  }
```
> 참고: `StoreRepositoryImplTest`에 `private StoreRepository storeRepository;` 필드가 없다면, 같은 패턴(`@Autowired StoreRepository storeRepository;`)을 추가한다. (기존 `WaitingRepositoryImplTest`가 동일하게 `StoreRepository`를 주입한다.)

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests "com.qrwait.api.store.infrastructure.StoreRepositoryImplTest"`
Expected: 컴파일 실패 — `cannot find symbol: method getByOwnerId / getById`

- [ ] **Step 3: 포트에 default 메서드 추가**

`StoreRepository.java` 전체를 다음으로 교체:
```java
package com.qrwait.api.store.domain;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

  Optional<Store> findById(UUID id);

  Optional<Store> findByOwnerId(UUID ownerId);

  Store save(Store store);

  default Store getByOwnerId(UUID ownerId) {
    return findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
  }

  default Store getById(UUID storeId) {
    return findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.qrwait.api.store.infrastructure.StoreRepositoryImplTest"`
Expected: PASS

- [ ] **Step 5: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: StoreRepository getByOwnerId/getById 포트 default 메서드 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 3: DailySummary 도메인 VO (T4 도메인)

**Files:**
- Create: `src/main/java/com/qrwait/api/waiting/domain/DailySummary.java`
- Create: `src/test/java/com/qrwait/api/waiting/domain/DailySummaryTest.java`

**Interfaces:**
- Produces: `DailySummary.from(Map<WaitingStatus, Long> counts)` → getter: `getTotalRegistered/getTotalEntered/getTotalNoShow/getTotalCancelled/getCurrentWaiting` (모두 `long`).
  - `totalRegistered` = 전체 상태 합, `currentWaiting` = WAITING+CALLED, 나머지는 해당 상태 값(없으면 0).

- [ ] **Step 1: 실패하는 테스트 작성**

Create `src/test/java/com/qrwait/api/waiting/domain/DailySummaryTest.java`:
```java
package com.qrwait.api.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySummaryTest {

  @Test
  void from_상태별_카운트를_집계한다() {
    DailySummary summary = DailySummary.from(Map.of(
        WaitingStatus.WAITING, 3L,
        WaitingStatus.CALLED, 1L,
        WaitingStatus.ENTERED, 5L,
        WaitingStatus.NO_SHOW, 2L,
        WaitingStatus.CANCELLED, 1L
    ));

    assertThat(summary.getTotalRegistered()).isEqualTo(12L);
    assertThat(summary.getTotalEntered()).isEqualTo(5L);
    assertThat(summary.getTotalNoShow()).isEqualTo(2L);
    assertThat(summary.getTotalCancelled()).isEqualTo(1L);
    assertThat(summary.getCurrentWaiting()).isEqualTo(4L);
  }

  @Test
  void from_빈_맵이면_모두_0() {
    DailySummary summary = DailySummary.from(Map.of());

    assertThat(summary.getTotalRegistered()).isZero();
    assertThat(summary.getCurrentWaiting()).isZero();
    assertThat(summary.getTotalEntered()).isZero();
  }

  @Test
  void from_일부_상태만_있어도_집계() {
    DailySummary summary = DailySummary.from(Map.of(WaitingStatus.WAITING, 2L));

    assertThat(summary.getTotalRegistered()).isEqualTo(2L);
    assertThat(summary.getCurrentWaiting()).isEqualTo(2L);
    assertThat(summary.getTotalEntered()).isZero();
  }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.domain.DailySummaryTest"`
Expected: 컴파일 실패 — `cannot find symbol: class DailySummary`

- [ ] **Step 3: 도메인 VO 구현**

Create `src/main/java/com/qrwait/api/waiting/domain/DailySummary.java`:
```java
package com.qrwait.api.waiting.domain;

import java.util.Map;
import lombok.Getter;

@Getter
public class DailySummary {

  private final long totalRegistered;
  private final long totalEntered;
  private final long totalNoShow;
  private final long totalCancelled;
  private final long currentWaiting;

  private DailySummary(long totalRegistered, long totalEntered, long totalNoShow,
      long totalCancelled, long currentWaiting) {
    this.totalRegistered = totalRegistered;
    this.totalEntered = totalEntered;
    this.totalNoShow = totalNoShow;
    this.totalCancelled = totalCancelled;
    this.currentWaiting = currentWaiting;
  }

  public static DailySummary from(Map<WaitingStatus, Long> counts) {
    long waiting = counts.getOrDefault(WaitingStatus.WAITING, 0L);
    long called = counts.getOrDefault(WaitingStatus.CALLED, 0L);
    long entered = counts.getOrDefault(WaitingStatus.ENTERED, 0L);
    long noShow = counts.getOrDefault(WaitingStatus.NO_SHOW, 0L);
    long cancelled = counts.getOrDefault(WaitingStatus.CANCELLED, 0L);

    return new DailySummary(
        waiting + called + entered + noShow + cancelled,
        entered,
        noShow,
        cancelled,
        waiting + called
    );
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.domain.DailySummaryTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: 일일 집계 DailySummary 도메인 VO 도출

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 4: 상태별 카운트 단일 쿼리 포트/인프라 (T4 인프라)

**Files:**
- Modify: `src/main/java/com/qrwait/api/waiting/domain/WaitingRepository.java`
- Modify: `src/main/java/com/qrwait/api/waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `src/main/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImpl.java`
- Test: `src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`

**Interfaces:**
- Produces: `Map<WaitingStatus, Long> WaitingRepository.countByStatusForStoreAndDate(UUID storeId, LocalDate date)` — 해당 매장의 그 날짜(00:00~익일 00:00) 상태별 건수. 건수 0인 상태는 맵에 없음.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`WaitingRepositoryImplTest.java`에 import 추가: `import com.qrwait.api.waiting.domain.DailySummary;`, `import java.time.LocalDate;`. 그리고 테스트 메서드 추가:
```java
  @Test
  void countByStatusForStoreAndDate_상태별_집계() {
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-0000-0001", 2, 1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-0000-0002", 2, 2));

    var counts = waitingRepository.countByStatusForStoreAndDate(savedStore.getId(), LocalDate.now());

    assertThat(counts.get(WaitingStatus.WAITING)).isEqualTo(2L);
    assertThat(DailySummary.from(counts).getTotalRegistered()).isEqualTo(2L);
  }
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.infrastructure.WaitingRepositoryImplTest"`
Expected: 컴파일 실패 — `cannot find symbol: method countByStatusForStoreAndDate`

- [ ] **Step 3: 포트에 메서드 추가**

`WaitingRepository.java`에 import 추가: `import java.util.Map;`. 인터페이스 본문에 메서드 추가:
```java
  Map<WaitingStatus, Long> countByStatusForStoreAndDate(UUID storeId, LocalDate date);
```

- [ ] **Step 4: JpaRepository에 GROUP BY 쿼리 추가**

`WaitingEntryJpaRepository.java`의 인터페이스 본문에 추가 (필요 import `java.util.List`는 이미 존재):
```java
  @Query("SELECT w.status, COUNT(w) FROM WaitingEntryJpaEntity w "
      + "WHERE w.storeId = :storeId AND w.createdAt >= :startOfDay AND w.createdAt < :endOfDay "
      + "GROUP BY w.status")
  List<Object[]> countByStatusGrouped(@Param("storeId") UUID storeId,
      @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);
```

- [ ] **Step 5: RepositoryImpl에 Map 변환 구현**

`WaitingRepositoryImpl.java`에 import 추가: `import java.util.EnumMap;`, `import java.util.Map;`. 마지막 `}` 직전에 메서드 추가:
```java
  @Override
  public Map<WaitingStatus, Long> countByStatusForStoreAndDate(UUID storeId, LocalDate date) {
    LocalDateTime startOfDay = date.atStartOfDay();
    LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
    Map<WaitingStatus, Long> result = new EnumMap<>(WaitingStatus.class);
    for (Object[] row : waitingEntryJpaRepository.countByStatusGrouped(storeId, startOfDay, endOfDay)) {
      result.put(WaitingStatus.valueOf((String) row[0]), (Long) row[1]);
    }
    return result;
  }
```
> 참고: `w.status`는 String으로 저장되므로 `row[0]`은 String이다 (기존 쿼리들이 `status.name()`을 넘기는 것과 일관).

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.infrastructure.WaitingRepositoryImplTest"`
Expected: PASS

- [ ] **Step 7: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: 상태별 카운트 단일 GROUP BY 쿼리 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 5: WaitingManagementService 리팩토링 (T1 헬퍼 + T4 집계 + T2 적용 + dead code 제거)

**Files:**
- Modify: `src/main/java/com/qrwait/api/waiting/application/dto/DailySummaryResponse.java`
- Modify: `src/main/java/com/qrwait/api/waiting/application/WaitingManagementService.java`
- Modify: `src/test/java/com/qrwait/api/waiting/application/WaitingManagementServiceTest.java`
- Modify (dead code 제거): `WaitingRepository.java`, `WaitingRepositoryImpl.java`, `WaitingEntryJpaRepository.java`

**Interfaces:**
- Consumes: `WaitingEntry.belongsTo` (Task 1), `StoreRepository.getByOwnerId` (Task 2), `DailySummary.from` (Task 3), `WaitingRepository.countByStatusForStoreAndDate` (Task 4).
- Produces: `DailySummaryResponse.from(DailySummary)` 정적 팩토리.

- [ ] **Step 1: 테스트 먼저 갱신(실패 상태로) — 스텁을 새 협력자로 교체**

`WaitingManagementServiceTest.java`를 다음과 같이 수정한다.

(a) import 추가: `import java.util.Map;`

(b) `getWaitingList`/`getTodayWaitings`/`call`/`enter`/`noShow` 정상 케이스의
`given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(Store.restore(... )));`
→ `given(storeRepository.getByOwnerId(ownerId)).willReturn(Store.restore(... ));` 로 모두 교체 (Optional 제거, 반환 타입 `Store`).

(c) 소유권 불일치 케이스(`call`/`enter`/`noShow`의 `otherStoreId`)도 동일하게
`given(storeRepository.getByOwnerId(ownerId)).willReturn(Store.restore(otherStoreId, ...));` 로 교체.

(d) `getTodayWaitings_매장_없음_예외발생`:
```java
    given(storeRepository.getByOwnerId(ownerId))
        .willThrow(new StoreNotFoundException("ownerId=" + ownerId));
```
(import `StoreNotFoundException`은 이미 존재. `Optional` 스텁 제거.)

(e) `getDailySummary_일별_통계_집계`의 5개 `countByStoreIdAndStatusAndDate` 스텁을 다음 1개로 교체:
```java
    given(waitingRepository.countByStatusForStoreAndDate(eq(storeId), any()))
        .willReturn(Map.of(
            WaitingStatus.WAITING, 3L,
            WaitingStatus.CALLED, 1L,
            WaitingStatus.ENTERED, 5L,
            WaitingStatus.NO_SHOW, 2L,
            WaitingStatus.CANCELLED, 1L));
```
그리고 store 스텁도 `getByOwnerId`로 교체. 단언(assert)은 그대로 둔다.

(f) `getDailySummary_데이터_없을_때_모두_0`의 count 스텁을:
```java
    given(waitingRepository.countByStatusForStoreAndDate(eq(storeId), any()))
        .willReturn(Map.of());
```
store 스텁도 `getByOwnerId`로 교체.

- [ ] **Step 2: 테스트 실패(컴파일 또는 단언) 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.application.WaitingManagementServiceTest"`
Expected: 실패 (서비스가 아직 옛 협력자 사용 → UnnecessaryStubbing/컴파일/NPE)

- [ ] **Step 3: DailySummaryResponse.from 팩토리 추가**

`DailySummaryResponse.java` 전체를 교체:
```java
package com.qrwait.api.waiting.application.dto;

import com.qrwait.api.waiting.domain.DailySummary;

public record DailySummaryResponse(
    long totalRegistered,
    long totalEntered,
    long totalNoShow,
    long totalCancelled,
    long currentWaiting
) {

  public static DailySummaryResponse from(DailySummary summary) {
    return new DailySummaryResponse(
        summary.getTotalRegistered(),
        summary.getTotalEntered(),
        summary.getTotalNoShow(),
        summary.getTotalCancelled(),
        summary.getCurrentWaiting()
    );
  }
}
```

- [ ] **Step 4: WaitingManagementService 리팩토링**

`WaitingManagementService.java`에서:

(a) import 정리 — 추가: `import com.qrwait.api.store.domain.Store;`, `import com.qrwait.api.waiting.domain.DailySummary;`. 제거: `import com.qrwait.api.waiting.application.dto.DailySummaryResponse;`는 유지(반환 타입). `LocalDate`는 유지. 사용 안 하게 되는 import(`WaitingStatus`가 getDailySummary에서만 쓰였다면 제거 — 단 다른 곳에서 쓰면 유지) 확인 후 정리.

(b) `getDailySummary`를 다음으로 교체:
```java
  @Transactional(readOnly = true)
  public DailySummaryResponse getDailySummary(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    DailySummary summary = DailySummary.from(
        waitingRepository.countByStatusForStoreAndDate(storeId, LocalDate.now()));
    return DailySummaryResponse.from(summary);
  }
```

(c) `resolveStoreId(...)` private 메서드를 **삭제**하고, 이를 호출하던 `getWaitingList`/`getTodayWaitings`/`subscribeOwnerDashboard`의 첫 줄을
`UUID storeId = storeRepository.getByOwnerId(ownerId).getId();` 로 교체.

(d) `call`/`enter`/`noShow`를 헬퍼 기반으로 교체하고, 클래스 맨 아래에 헬퍼+레코드를 추가:
```java
  @Transactional
  public void call(UUID ownerId, UUID waitingId) {
    OwnedEntry owned = loadOwnedEntry(ownerId, waitingId);
    WaitingEntry called = owned.entry().call();
    waitingRepository.save(called);
    eventPublisher.publishEvent(new WaitingCalledEvent(
        called.getStoreId(),
        waitingId,
        called.getPhoneNumber(),
        called.getWaitingNumber(),
        owned.store().getName()
    ));
  }

  @Transactional
  public void enter(UUID ownerId, UUID waitingId) {
    WaitingEntry entered = loadOwnedEntry(ownerId, waitingId).entry().enter();
    waitingRepository.save(entered);
    eventPublisher.publishEvent(new WaitingUpdatedEvent(entered.getStoreId()));
  }

  @Transactional
  public void noShow(UUID ownerId, UUID waitingId) {
    WaitingEntry noShowed = loadOwnedEntry(ownerId, waitingId).entry().noShow();
    waitingRepository.save(noShowed);
    eventPublisher.publishEvent(new WaitingUpdatedEvent(noShowed.getStoreId()));
  }
```
그리고 `toOwnerWaitingResponse` 아래(클래스 마지막)에 추가:
```java
  private OwnedEntry loadOwnedEntry(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));
    Store store = storeRepository.getByOwnerId(ownerId);
    if (!entry.belongsTo(store.getId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }
    return new OwnedEntry(store, entry);
  }

  private record OwnedEntry(Store store, WaitingEntry entry) {}
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "com.qrwait.api.waiting.application.WaitingManagementServiceTest"`
Expected: PASS (모든 케이스)

- [ ] **Step 6: dead code 제거 — `countByStoreIdAndStatusAndDate`**

이제 사용처가 없으므로 제거:
- `WaitingRepository.java`: `long countByStoreIdAndStatusAndDate(UUID storeId, WaitingStatus status, LocalDate date);` 줄 삭제.
- `WaitingRepositoryImpl.java`: `countByStoreIdAndStatusAndDate` `@Override` 메서드 전체 삭제.
- `WaitingEntryJpaRepository.java`: `countByStoreIdAndStatusAndDate` `@Query` 메서드 삭제.

- [ ] **Step 7: 전체 테스트로 회귀 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (잔여 참조 없음 — 컴파일/테스트 그린)

- [ ] **Step 8: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: WaitingManagementService 소유권 헬퍼 통합 및 일일 집계 단일 쿼리화

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 6: WaitingService 추정 대기시간 fallback 통일 (T3) + 의도된 값 주석 (T5)

**Files:**
- Modify: `src/main/java/com/qrwait/api/waiting/application/WaitingService.java`

**Interfaces:**
- (내부 전용) `private int estimatedWaitMinutes(UUID storeId, int ahead)` — 설정 있으면 `calculateEstimatedWait(ahead)`, 없으면 `ahead * DEFAULT_MINUTES_PER_PERSON`.

- [ ] **Step 1: 헬퍼/상수 추가 및 호출부 교체**

`WaitingService.java`에서:

(a) 클래스 필드 선언부 위쪽에 상수 추가:
```java
  private static final int DEFAULT_MINUTES_PER_PERSON = 5;
```

(b) 클래스 마지막 `}` 직전에 헬퍼 추가:
```java
  private int estimatedWaitMinutes(UUID storeId, int ahead) {
    return storeSettingsRepository.findByStoreId(storeId)
        .map(settings -> settings.calculateEstimatedWait(ahead))
        .orElse(ahead * DEFAULT_MINUTES_PER_PERSON);
  }
```

(c) `register`의 추정 계산 블록
```java
    int estimatedWaitMinutes = storeSettingsRepository.findByStoreId(storeId)
        .map(settings -> settings.calculateEstimatedWait(totalWaiting))
        .orElse(totalWaiting * 5);
```
→
```java
    int estimatedWaitMinutes = estimatedWaitMinutes(storeId, totalWaiting);
```
그리고 `return new RegisterWaitingResponse(...)` 위에 주석 추가:
```java
    // 신규 등록자는 대기열 맨 뒤이므로 currentRank == totalWaiting (의도된 동일 값)
```

(d) `getStatus`의 블록
```java
    int estimatedWaitMinutes = storeSettingsRepository.findByStoreId(entry.getStoreId())
        .map(settings -> settings.calculateEstimatedWait((int) ahead))
        .orElse((int) ahead * 5);
```
→
```java
    int estimatedWaitMinutes = estimatedWaitMinutes(entry.getStoreId(), (int) ahead);
```

(e) `getStoreWaitingStatus`의 블록
```java
    int estimatedWaitMinutes = storeSettingsRepository.findByStoreId(storeId)
        .map(settings -> settings.calculateEstimatedWait(totalWaiting))
        .orElse(totalWaiting * 5);
    return new WaitingStatusResponse(totalWaiting, totalWaiting, estimatedWaitMinutes);
```
→
```java
    int estimatedWaitMinutes = estimatedWaitMinutes(storeId, totalWaiting);
    // 매장 전체 상태 조회는 특정 손님이 없으므로 currentRank 자리에 totalWaiting을 그대로 둔다 (의도)
    return new WaitingStatusResponse(totalWaiting, totalWaiting, estimatedWaitMinutes);
```

- [ ] **Step 2: 테스트 통과 확인(동작 보존)**

Run: `./gradlew test --tests "com.qrwait.api.waiting.application.WaitingServiceTest"`
Expected: PASS (스텁 변경 불필요 — 여전히 `storeSettingsRepository.findByStoreId` 사용)

- [ ] **Step 3: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: WaitingService 추정 대기시간 계산 헬퍼로 통일

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 7: Store/StoreSettings/Owner 서비스에 포트 default 메서드 적용 (T2)

**Files:**
- Modify: `src/main/java/com/qrwait/api/store/application/StoreService.java`
- Modify: `src/test/java/com/qrwait/api/store/application/StoreServiceTest.java`
- Modify: `src/main/java/com/qrwait/api/store/application/StoreSettingsService.java`
- Modify: `src/test/java/com/qrwait/api/store/application/StoreSettingsServiceTest.java`
- Modify: `src/main/java/com/qrwait/api/owner/application/OwnerService.java`
- Modify: `src/test/java/com/qrwait/api/owner/application/OwnerServiceTest.java`

**Interfaces:**
- Consumes: `StoreRepository.getByOwnerId/getById` (Task 2).

- [ ] **Step 1: 서비스 호출부 교체**

`StoreService.java`:
- `getMyStore`/`updateStoreInfo`/`updateStoreStatus`의
  `Store store = storeRepository.findByOwnerId(ownerId).orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));`
  → `Store store = storeRepository.getByOwnerId(ownerId);`
- `getStoreById`의
  `Store store = storeRepository.findById(storeId).orElseThrow(() -> new StoreNotFoundException(storeId));`
  → `Store store = storeRepository.getById(storeId);`
- `generateQrImage`의
  `storeRepository.findById(storeId).orElseThrow(() -> new StoreNotFoundException(storeId));`
  → `storeRepository.getById(storeId);`

`StoreSettingsService.java`:
- `resolveStoreId(...)` private 메서드 삭제, `getSettings`/`updateSettings` 첫 줄
  `UUID storeId = resolveStoreId(ownerId);` → `UUID storeId = storeRepository.getByOwnerId(ownerId).getId();`

`OwnerService.java`:
- `login`의
  `Store store = storeRepository.findByOwnerId(owner.getId()).orElseThrow(() -> new StoreNotFoundException("ownerId=" + owner.getId()));`
  → `Store store = storeRepository.getByOwnerId(owner.getId());`

- [ ] **Step 2: 테스트 스텁 교체 (Mockito default 메서드 대응)**

`StoreServiceTest.java`:
- `findByOwnerId` 정상 스텁(라인 55/78/107) → `given(storeRepository.getByOwnerId(ownerId)).willReturn(store);`
- `findByOwnerId` empty 스텁(라인 66/92) → `given(storeRepository.getByOwnerId(ownerId)).willThrow(new StoreNotFoundException("ownerId=" + ownerId));`
- `findById` 정상 스텁(라인 125) → `given(storeRepository.getById(storeId)).willReturn(store);`
- `findById` empty 스텁(라인 134) → `given(storeRepository.getById(storeId)).willThrow(new StoreNotFoundException(storeId));`
- 미사용이 된 `import java.util.Optional;`은 다른 곳에서 안 쓰면 제거.

`StoreSettingsServiceTest.java`:
- 정상 스텁(라인 50/74) → `given(storeRepository.getByOwnerId(ownerId)).willReturn(store);`
- empty 스텁(라인 62) → `given(storeRepository.getByOwnerId(ownerId)).willThrow(new StoreNotFoundException("ownerId=" + ownerId));`
- `Optional` import는 `storeSettingsRepository.findByStoreId` 스텁에서 여전히 쓰이므로 유지.

`OwnerServiceTest.java`:
- 라인 111 `given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));`
  → `given(storeRepository.getByOwnerId(ownerId)).willReturn(store);`
  (단, 이 스텁의 변수명이 `ownerId`인지 `owner.getId()`인지 테스트 코드에 맞춘다 — 실제 서비스가 `owner.getId()`로 호출하므로 테스트가 동일 값을 stub 하는지 확인. `refreshTokenRepository.findByOwnerId`는 그대로 둔다.)

- [ ] **Step 3: 해당 테스트 통과 확인**

Run: `./gradlew test --tests "com.qrwait.api.store.application.*" --tests "com.qrwait.api.owner.application.OwnerServiceTest"`
Expected: PASS

- [ ] **Step 4: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: 매장 조회 중복을 StoreRepository 포트 default 메서드로 일원화

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 8: StoreSettings 매직넘버 상수화 (T5)

**Files:**
- Modify: `src/main/java/com/qrwait/api/store/domain/StoreSettings.java`

- [ ] **Step 1: 기본값 상수화**

`StoreSettings.java`에서 `createDefault` 위에 상수 추가:
```java
  private static final int DEFAULT_TABLE_COUNT = 5;
  private static final int DEFAULT_AVG_TURNOVER_MINUTES = 30;
  private static final int DEFAULT_ALERT_THRESHOLD = 10;
  private static final boolean DEFAULT_ALERT_ENABLED = true;
```
`createDefault`를 교체:
```java
  public static StoreSettings createDefault(UUID storeId) {
    return new StoreSettings(UUID.randomUUID(), storeId, DEFAULT_TABLE_COUNT,
        DEFAULT_AVG_TURNOVER_MINUTES, null, null, DEFAULT_ALERT_THRESHOLD, DEFAULT_ALERT_ENABLED);
  }
```

- [ ] **Step 2: 전체 테스트로 동작 보존 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (값 동일 — `StoreSettingsServiceTest`의 기본값 단언 5/30/true 그대로 통과)

- [ ] **Step 3: 커밋 (이 태스크의 변경만 스테이징 후 커밋)**

```
backend refactor: StoreSettings 기본값 매직넘버 상수화

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 최종 검증

- [ ] `./gradlew test` 전체 그린 — BUILD SUCCESSFUL
- [ ] `git status` 깔끔 (작업트리에 무관한 `application.yml` 외 잔여 없음 확인)
- [ ] 외부 응답 DTO 필드/의미 불변 재확인 (`RegisterWaitingResponse`, `WaitingStatusResponse`, `DailySummaryResponse`, `StoreResponse`, `StoreSettingsResponse`)

## 커밋 / TASKS 문서

- 각 태스크는 자기 변경을 작업 브랜치에 커밋한다(한글 메시지 + Co-Authored-By). **최종 main 통합(squash/merge)은 사용자가 직접 수행한다.**
- 작업 완료 후 TASKS 문서가 있으면 갱신하고, 한글 커밋 메시지 추천을 함께 제시한다.
