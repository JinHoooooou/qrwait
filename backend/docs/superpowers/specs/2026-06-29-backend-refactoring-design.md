# 백엔드 코드 품질 리팩토링 설계

- 작성일: 2026-06-29
- 범위: `backend/` 전반적 코드 품질 정리 (동작 보존)
- 원칙: [`backend/CLAUDE.md`](../../../CLAUDE.md)의 Domain-first / YAGNI 준수. 모든 변경은 **동작을 보존**하며 기존 테스트(도메인/서비스/Controller)가 안전망이다.

## 목표

구조 자체는 아키텍처 가이드를 잘 따르고 있으므로 대규모 재설계가 아니라, 중복 제거와 도메인 강화를 통한 품질 정리를 한다. 외부 API 동작·응답 스펙은 바꾸지 않는다.

## 비범위 (Non-goals)

- 새 기능 추가, API 변경.
- `OwnerService.signUp`의 오케스트레이션 구조 변경(회원가입 시 Owner+Store+StoreSettings 동시 생성은 application service 책임으로 적절 → 유지).
- `calculateEstimatedWait`의 정수 나눗셈 동작 변경(기존 동작 보존).
- `RegisterWaitingResponse` / `getStoreWaitingStatus`의 `currentRank == totalWaiting` 값(신규 등록자는 맨 뒤라 의도된 값 → 유지, 주석으로만 명확화).

---

## T1 — 웨이팅 소유권 검증 도메인화 + 중복 템플릿 제거

### 현재 문제
`WaitingManagementService`의 `call`/`enter`/`noShow`가 다음 절차를 거의 그대로 반복한다.
1. `waitingRepository.findById` → 없으면 `WaitingNotFoundException`
2. 점주의 매장 조회 (`storeRepository.findByOwnerId(...).orElseThrow`)
3. `store.getId().equals(entry.getStoreId())` 비교 → 불일치 시 `StoreNotFoundException`
4. 상태전이 → 저장 → 이벤트 발행

또한 `call()`은 `com.qrwait.api.store.domain.Store`를 풀네임으로 참조한다.

### 변경
- **도메인 메서드 추가** — `WaitingEntry.belongsTo(UUID storeId)`:
  ```java
  public boolean belongsTo(UUID storeId) {
    return this.storeId.equals(storeId);
  }
  ```
  (도메인은 순수 유지 — 예외는 던지지 않고 boolean만 반환. `StoreNotFoundException`(store 애그리거트 예외)은 서비스가 던진다.)
- **공통 로드 헬퍼 추출** — 소유권 검증까지 끝낸 `Store`+`WaitingEntry`를 함께 반환:
  ```java
  private record OwnedEntry(Store store, WaitingEntry entry) {}

  private OwnedEntry loadOwnedEntry(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));
    Store store = storeRepository.getByOwnerId(ownerId); // T2 default 메서드
    if (!entry.belongsTo(store.getId())) {
      throw new StoreNotFoundException("ownerId=" + ownerId);
    }
    return new OwnedEntry(store, entry);
  }
  ```
- `call`/`enter`/`noShow`는 헬퍼를 호출해 상태전이·저장·이벤트만 담당. `call`은 `store.getName()`을 그대로 사용(중복 조회 제거).
- `Store`를 정식 import 하여 풀네임 제거.

### 영향
- 동작 동일. `call`은 기존에 store를 1회 조회(+resolveStoreId 없음)였고, 변경 후에도 1회 조회.
- `enter`/`noShow`는 기존 `resolveStoreId`(매장 1회 조회)와 동일하게 매장 1회 조회.

### 테스트
- 도메인 단위 테스트: `WaitingEntry.belongsTo` true/false (신규 추가, 신규 `WaitingEntryTest`).
- 기존 `WaitingManagementServiceTest` 그대로 통과해야 함(동작 보존). T2 적용으로 스텁 대상 변경 필요(아래 T2 참고).

---

## T2 — `resolveStoreId(ownerId)` / `findByX().orElseThrow` 중복 제거

### 현재 문제
- `resolveStoreId(ownerId)` 동일 private 메서드가 `WaitingManagementService`, `StoreSettingsService`에 복붙.
- `StoreService`, `OwnerService`도 `storeRepository.findByOwnerId(...).orElseThrow(StoreNotFoundException)` / `findById(...).orElseThrow(...)`를 매번 인라인.

### 변경
포트 인터페이스 `StoreRepository`에 default 메서드를 추가해 "없으면 예외" 규칙을 한 곳으로 모은다(예: 같은 패키지의 `StoreNotFoundException` 사용).
```java
default Store getByOwnerId(UUID ownerId) {
  return findByOwnerId(ownerId)
      .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
}

default Store getById(UUID storeId) {
  return findById(storeId)
      .orElseThrow(() -> new StoreNotFoundException(storeId));
}
```
호출부 변경:
- `WaitingManagementService.resolveStoreId` 제거 → 필요한 곳은 `storeRepository.getByOwnerId(ownerId).getId()` 또는 T1 헬퍼 경유.
- `StoreSettingsService.resolveStoreId` 제거 → 동일.
- `StoreService`의 `findByOwnerId(...).orElseThrow` 3곳, `findById(...).orElseThrow` 2곳 → `getByOwnerId` / `getById`.
- `OwnerService.login`의 `findByOwnerId(...).orElseThrow` → `getByOwnerId`.

### ⚠️ 테스트 영향 (중요)
서비스 테스트는 `StoreRepository`를 Mockito mock으로 사용한다. Mockito는 default 메서드를 **실제 실행하지 않고** 기본값(null)을 반환하므로, 호출부가 `getByOwnerId`/`getById`로 바뀌면 `findByOwnerId`/`findById`만 스텁한 기존 테스트가 깨진다.
→ 해당 서비스 테스트들의 스텁 대상을 새 메서드(`getByOwnerId`/`getById`)로 변경한다. (대상: `WaitingManagementServiceTest`, `StoreSettingsServiceTest`, `StoreServiceTest`, `OwnerServiceTest` 중 해당 호출이 있는 케이스.)
→ `StoreRepositoryImplTest`(실제 구현 테스트)는 default 메서드를 자동 상속하므로 영향 없음. 필요 시 `getByOwnerId`/`getById`의 throw 경로 테스트만 보강.

---

## T3 — 추정 대기시간 fallback 통일

### 현재 문제
`WaitingService`에 동일 패턴이 3회 반복되고 `* 5` 매직넘버가 흩어져 있다.
```java
storeSettingsRepository.findByStoreId(storeId)
    .map(settings -> settings.calculateEstimatedWait(n))
    .orElse(n * 5);
```
(`register`는 n=totalWaiting, `getStatus`는 n=ahead, `getStoreWaitingStatus`는 n=totalWaiting)

### 변경
private 헬퍼로 일원화 + 상수화. 동작 보존(설정 없을 때 `ahead * 5분`).
```java
private static final int DEFAULT_MINUTES_PER_PERSON = 5;

private int estimatedWaitMinutes(UUID storeId, int ahead) {
  return storeSettingsRepository.findByStoreId(storeId)
      .map(settings -> settings.calculateEstimatedWait(ahead))
      .orElse(ahead * DEFAULT_MINUTES_PER_PERSON);
}
```
3개 호출부를 이 헬퍼로 대체.

### 테스트
- 기존 `WaitingServiceTest` 동작 보존으로 통과. fallback 경로(설정 없음)와 정상 경로가 이미 커버되는지 확인하고, 비면 보강.

---

## T4 — 일일 집계 N+1 제거 + `DailySummary` 도메인 도출 (T5 사소 정리 포함)

### 현재 문제
`WaitingManagementService.getDailySummary`가 `countByStoreIdAndStatusAndDate`를 **상태당 1회씩 총 7번** 호출하고, 집계 산수(합산)가 서비스에 고여 있다. 가이드의 "새 도메인 도출" 케이스.

집계 규칙(보존):
- `totalRegistered` = 모든 상태 합 (WAITING+CALLED+ENTERED+NO_SHOW+CANCELLED)
- `totalEntered` = ENTERED
- `totalNoShow` = NO_SHOW
- `totalCancelled` = CANCELLED
- `currentWaiting` = WAITING + CALLED

### 변경
1. **포트 추가** — `WaitingRepository`:
   ```java
   Map<WaitingStatus, Long> countByStatusForStoreAndDate(UUID storeId, LocalDate date);
   ```
2. **JpaRepository** — GROUP BY 1쿼리:
   ```java
   @Query("SELECT w.status, COUNT(w) FROM WaitingEntryJpaEntity w " +
          "WHERE w.storeId = :storeId AND w.createdAt >= :startOfDay AND w.createdAt < :endOfDay " +
          "GROUP BY w.status")
   List<Object[]> countByStatusForStoreAndDate(@Param("storeId") UUID storeId,
       @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);
   ```
   (status는 String 저장 → RepositoryImpl에서 `WaitingStatus.valueOf`로 매핑. 비어있는 상태는 0으로 보정.)
3. **RepositoryImpl** — `Object[]` → `Map<WaitingStatus, Long>` 변환(없는 상태는 0L 채움), 시작/끝 시각 계산은 기존 패턴 재사용.
4. **도메인 VO 신설** — `waiting/domain/DailySummary`:
   ```java
   public class DailySummary {
     private final long totalRegistered;
     private final long totalEntered;
     private final long totalNoShow;
     private final long totalCancelled;
     private final long currentWaiting;
     // @Getter

     public static DailySummary from(Map<WaitingStatus, Long> counts) {
       long get(...) // 없으면 0
       totalRegistered = 전체 합;
       currentWaiting  = WAITING + CALLED;
       ...
     }
   }
   ```
   집계 산수는 전부 이 도메인이 책임진다.
5. **서비스** — `getDailySummary`:
   ```java
   UUID storeId = ...getByOwnerId(ownerId).getId();
   DailySummary summary = DailySummary.from(
       waitingRepository.countByStatusForStoreAndDate(storeId, LocalDate.now()));
   return new DailySummaryResponse(... summary getters ...);
   ```
   (또는 `DailySummaryResponse.from(summary)` 정적 팩토리 추가 — 다른 DTO들이 `from`을 쓰므로 일관.)
6. **레거시 정리** — 다른 곳에서 더는 쓰지 않으면 `countByStoreIdAndStatusAndDate`(포트/구현/JPA) 제거 검토. 사용처가 남아 있으면 유지.

### T5 사소 정리 (이 단계에 포함)
- `StoreSettings.createDefault`의 매직넘버 5/30/10 → 명명 상수(`DEFAULT_TABLE_COUNT` 등).
- `RegisterWaitingResponse(... totalWaiting, totalWaiting ...)` / `getStoreWaitingStatus`의 중복 값: **동작 유지**, `currentRank == totalWaiting`임을 알리는 인라인 주석만 추가.

### 테스트
- 도메인 단위 테스트 신설: `DailySummaryTest` — 빈 맵/부분 맵/전체 맵에 대한 집계 검증.
- 인프라 테스트: `WaitingRepositoryImplTest`에 `countByStatusForStoreAndDate` 케이스 추가(날짜 경계 포함).
- 서비스 테스트: `getDailySummary`가 새 포트 1회 호출로 바뀌므로 스텁/검증 갱신.

---

## 작업 순서 (가이드 §7 Domain-first 체크리스트 적용)

1. **T1 도메인** — `WaitingEntry.belongsTo` + 도메인 테스트.
2. **T2 포트** — `StoreRepository.getByOwnerId/getById` default 메서드.
3. **T4 도메인/포트/인프라** — `DailySummary` VO + `countByStatusForStoreAndDate`(포트→JPA→Impl) + 도메인/인프라 테스트.
4. **서비스 리팩토링** — `WaitingManagementService`(T1 헬퍼+T4), `WaitingService`(T3 헬퍼), `StoreService`/`StoreSettingsService`/`OwnerService`(T2 적용).
5. **T5 사소 정리** — 상수화, 주석.
6. **서비스 테스트 스텁 갱신**(T2 Mockito 이슈) 및 전체 `./gradlew test` 그린 확인.

## 검증

- 각 단계 후 `./gradlew test` 그린 유지(동작 보존 증거).
- 외부 응답 DTO 필드/의미 불변 확인.
