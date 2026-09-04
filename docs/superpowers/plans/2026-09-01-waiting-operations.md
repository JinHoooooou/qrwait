# 현장 웨이팅 운영 완성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 대기번호를 영업일 단위로 채번하고, 호출 이후 운영(유예·미루기)을 갖추고, 예상 대기시간을 바로잡고, 영업일이 지난 전화번호를 가명처리한다.

**Architecture:** 영업일(`business_date`)을 등록 시점에 확정해 컬럼으로 저장하고, 채번·통계·가명처리가 모두 이 값을 기준으로 동작한다. `UNIQUE (store_id, business_date, waiting_number)` 제약이 채번 경합을 DB에서 닫고, 애플리케이션은 제약 위반 시 트랜잭션 밖에서 재시도한다. 호출 유예는 상태가 아니라 `called_at`에서 파생되는 값이라 감시 스케줄러가 필요 없다.

**Tech Stack:** Java 21 · Spring Boot 3.5 (JPA · `@Scheduled` · `@TransactionalEventListener`) · PostgreSQL 15 · Flyway · JUnit5 · AssertJ · Mockito · Testcontainers · React 19 + TypeScript

**Spec:** [`docs/superpowers/specs/2026-09-01-waiting-operations-design.md`](../specs/2026-09-01-waiting-operations-design.md)

## Global Constraints

- 백엔드 아키텍처 규칙은 [`backend/CLAUDE.md`](../../../backend/CLAUDE.md)를 따른다. 도메인 계층은 Spring/JPA를 import하지 않는다.
- 도메인 객체는 불변이다. 상태 변경은 새 인스턴스를 반환한다. 잘못된 전이는 `IllegalStateException`.
- `@Query`(JPQL)는 자바 텍스트 블록으로 작성하고, root 키워드를 river 정렬(우측 정렬)한다.
- 커밋 메시지는 한글로 작성한다.
- 백엔드 테스트 실행: `cd backend && ./gradlew test --tests "<FQCN>"`. 전체는 `./gradlew test`.
- 도메인 테스트 메서드명은 한글 스네이크(`postpone_CALLED가_아니면_예외`), 인프라 테스트는 영문 카멜(`findNextWaitingNumber_returnsOnePerBusinessDate`)을 따른다 (기존 관례).
- **개인정보 용어:** 뒤 4자리와 해시가 남으므로 "파기"가 아니라 **"가명처리"** 다. 문서·주석·UI 문구에서 "파기"를 쓰지 않는다.
- **마이그레이션 분할:** 스펙은 `V2` 하나로 기술했으나, 태스크 경계에서 빌드가 항상 초록이어야 하므로 `V2`(store_settings)와 `V3`(waiting_entries)로 나눈다.
- 스펙 §14의 완료 기준 11개가 최종 검수 항목이다.

---

## File Structure

**생성**
| 파일 | 책임 |
|---|---|
| `backend/src/main/resources/db/migration/V2__store_settings_business_day.sql` | 영업일 시작 시각·호출 유예 컬럼 |
| `backend/src/main/resources/db/migration/V3__waiting_business_date.sql` | `business_date`·`called_at`·`entered_at`·`phone_hash` + 유니크 제약 |
| `backend/src/main/resources/db/seed/R__dev_seed.sql` | 로컬 전용 개발 시드 (멱등) |
| `backend/.../shared/privacy/PhoneHasher.java` | HMAC-SHA256 산출 (비밀키 주입) |
| `backend/.../shared/privacy/PhoneNumberMasker.java` | 표시용 마스킹 (`****-5678`) |
| `backend/.../waiting/customer/application/WaitingRegistrar.java` | 등록 1회 시도의 트랜잭션 경계 |
| `backend/.../waiting/domain/WaitingNumberConflictException.java` | 채번 재시도 소진 |
| `backend/.../waiting/domain/event/WaitingPostponedEvent.java` | 미루기 이벤트 |
| `backend/.../waiting/management/application/WaitingRetentionService.java` | 가명처리 배치 |

**주요 수정**
| 파일 | 책임 변화 |
|---|---|
| `store/domain/StoreSettings.java` | 영업일 경계와 유예 시간을 아는 객체가 된다 |
| `waiting/domain/WaitingEntry.java` | 시각 3개 + 해시를 갖고, 미루기·가명처리 전이를 안다 |
| `waiting/domain/WaitingRepository.java` | 모든 조회가 `businessDate`를 받는다 |
| `waiting/customer/application/WaitingService.java` | 트랜잭션을 소유하지 않고 재시도만 담당 |

---

## Task 0: Flyway 정상화

스키마를 바꾸기 전에 스키마 출처를 하나로 만든다. 지금은 Flyway가 테스트에서만 돌고 로컬은 `ddl-auto: update`라, 이 상태로 마이그레이션을 올리면 로컬과 테스트 스키마가 확실히 갈라진다.

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/main/resources/db/seed/R__dev_seed.sql`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: Flyway가 모든 프로필에서 스키마를 소유하고, `ddl-auto`는 `validate`만 한다. 이후 모든 태스크는 마이그레이션 파일로 스키마를 바꾼다.

- [ ] **Step 1: 개발 시드용 BCrypt 해시 생성**

`V1__init.sql`의 `dev-password-hash`는 BCrypt 형식이 아니라 로그인이 불가능하다. 실제 해시를 만든다. 임시 테스트를 작성한다:

```java
// backend/src/test/java/com/qrwait/api/support/GenerateDevPasswordHash.java
package com.qrwait.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GenerateDevPasswordHash {

  @Test
  void print() {
    System.out.println("DEV_HASH=" + new BCryptPasswordEncoder().encode("devpassword"));
  }
}
```

- [ ] **Step 2: 해시 출력 확인**

Run: `cd backend && ./gradlew test --tests "*.GenerateDevPasswordHash" -i | grep DEV_HASH`
Expected: `DEV_HASH=$2a$10$...` 형태의 60자 문자열이 출력된다. 이 값을 복사해 둔다.

- [ ] **Step 3: 임시 테스트 삭제**

```bash
rm backend/src/test/java/com/qrwait/api/support/GenerateDevPasswordHash.java
```

- [ ] **Step 4: `V1__init.sql`에서 시드 블록 제거**

파일 하단의 아래 블록을 통째로 삭제한다 (주석 구분선 포함):

```sql
-- =============================================
-- 개발 환경 전용 시드 데이터
-- =============================================
INSERT INTO owners (id, email, password_hash, created_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'dev@qrwait.com', 'dev-password-hash', now());

INSERT INTO stores (id, owner_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', '맛있는 한식당', now()),
       ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000', '행복한 분식집', now()),
       ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000000', '즐거운 카페', now());
```

- [ ] **Step 5: 로컬 전용 시드 파일 생성**

`backend/src/main/resources/db/seed/R__dev_seed.sql`. `<STEP_2_HASH>`를 Step 2에서 복사한 값으로 치환한다. Repeatable 마이그레이션은 체크섬이 바뀔 때마다 재실행되므로 반드시 멱등해야 한다.

```sql
-- 로컬 개발 전용 시드. application-local.yml 의 flyway.locations 에서만 로드된다.
-- Repeatable 마이그레이션이므로 재실행되어도 안전하도록 ON CONFLICT DO NOTHING 을 붙인다.
-- 계정: dev@qrwait.com / devpassword

INSERT INTO owners (id, email, password_hash, created_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'dev@qrwait.com', '<STEP_2_HASH>', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO stores (id, owner_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', '맛있는 한식당', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO store_settings (id, store_id)
VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;
```

> 매장을 3개에서 1개로 줄인다. 나머지 둘은 점주 1명당 매장 1개인 현재 도메인(`getByOwnerId`가 단일 매장을 가정)과 맞지 않아 혼란만 준다. `store_settings`를 함께 시드해야 예상 대기시간이 기본값 폴백으로 빠지지 않는다.

- [ ] **Step 6: `application.yml`에서 Flyway 전역 차단 해제**

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
```

(기존 `enabled: false`를 `true`로 바꾼다)

- [ ] **Step 7: `application-local.yml`을 Flyway + validate로 전환**

아래 블록을

```yaml
  # 로컬 개발: Flyway 대신 JPA 엔티티 기준으로 스키마 자동 생성/갱신
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: update
```

이렇게 바꾼다:

```yaml
  # 로컬 개발: Flyway 가 스키마를 소유하고, Hibernate 는 엔티티↔스키마 일치만 검증한다.
  # 개발 시드는 로컬에서만 db/seed 를 추가로 로드해 적용한다.
  flyway:
    locations: classpath:db/migration,classpath:db/seed
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 8: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: 127개 전부 PASS. `IntegrationTestSupport`는 기본 위치(`classpath:db/migration`)만 마이그레이션하므로 시드는 실행되지 않으며, 시드 UUID를 참조하는 테스트는 존재하지 않는다.

> **실패 시:** `ddl-auto: validate`가 `V1__init.sql`과 JPA 엔티티의 불일치(컬럼 nullability·타입)를 처음으로 드러낼 수 있다. 이는 스펙 §13이 예고한 리스크다. 엔티티 쪽을 스키마에 맞추고, 판단이 필요한 불일치는 보고한다.

- [ ] **Step 9: 로컬 기동 확인**

Run: `docker compose -f docker-compose.dev.yml up -d && cd backend && ./gradlew bootRun`
Expected: 정상 부팅. 로그에 Flyway가 V1 적용과 `R__dev_seed` 실행을 남긴다.

> 기존 로컬 DB에 `ddl-auto: update`가 만든 스키마가 있으면 Flyway 베이스라인 충돌이 난다. 그 경우 `docker compose -f docker-compose.dev.yml down -v` 로 볼륨을 지우고 다시 올린다.

- [ ] **Step 10: 커밋**

```bash
git add backend/src/main/resources/
git commit -m "chore: Flyway를 모든 프로필에서 스키마 소유자로 전환

로컬도 ddl-auto: validate 로 바꿔 테스트와 스키마 출처를 일치시킨다.
개발 시드는 V1 에서 분리해 로컬 프로필에서만 로드하는 db/seed 로 옮기고,
로그인 불가였던 password_hash 를 실제 BCrypt 해시로 교체했다."
```

---

## Task 1: StoreSettings — 영업일 경계와 유예 시간

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__store_settings_business_day.sql`
- Modify: `backend/.../store/domain/StoreSettings.java`
- Modify: `backend/.../store/infrastructure/StoreSettingsJpaEntity.java`
- Modify: `backend/.../store/management/dto/UpdateStoreSettingsRequest.java`
- Modify: `backend/.../store/management/dto/StoreSettingsResponse.java`
- Modify: `backend/.../store/management/application/StoreSettingsService.java`
- Test: `backend/src/test/java/com/qrwait/api/store/domain/StoreSettingsTest.java` (신규)

**Interfaces:**
- Consumes: Task 0의 Flyway 파이프라인
- Produces:
  - `StoreSettings.businessDateOf(LocalDateTime at) -> LocalDate`
  - `StoreSettings.getCallGraceMinutes() -> int`
  - `StoreSettings.calculateEstimatedWait(int aheadCount) -> int` (교정됨)
  - `StoreSettings.restore(UUID, UUID, int, int, LocalTime, LocalTime, int, boolean, LocalTime businessDayStart, int callGraceMinutes)`
  - `StoreSettings.update(int, int, LocalTime, LocalTime, int, boolean, LocalTime, int)`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/qrwait/api/store/domain/StoreSettingsTest.java`

```java
package com.qrwait.api.store.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreSettingsTest {

  private StoreSettings settings(int tableCount, int avgTurnoverMinutes, LocalTime businessDayStart) {
    return StoreSettings.restore(
        UUID.randomUUID(), UUID.randomUUID(), tableCount, avgTurnoverMinutes,
        null, null, 10, true, businessDayStart, 5);
  }

  @Test
  void businessDateOf_영업일_시작_이전이면_전날() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 4, 59));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void businessDateOf_영업일_시작_정각이면_당일() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 5, 0));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 2));
  }

  @Test
  void businessDateOf_자정_직후는_전날_영업일() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 0, 10));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void businessDateOf_영업중_정오는_당일() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 12, 0));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 2));
  }

  @Test
  void calculateEstimatedWait_절사되지_않는다() {
    // 30분 / 7테이블 = 4.28분. 곱셈을 먼저 하지 않으면 4분으로 잘려 10팀에 40분이 된다.
    StoreSettings s = settings(7, 30, LocalTime.of(5, 0));

    assertThat(s.calculateEstimatedWait(10)).isEqualTo(43);
  }

  @Test
  void calculateEstimatedWait_테이블이_회전시간보다_많아도_0이_아니다() {
    // 기존 공식은 30/40 = 0 이 되어 항상 0분을 반환했다.
    StoreSettings s = settings(40, 30, LocalTime.of(5, 0));

    assertThat(s.calculateEstimatedWait(20)).isEqualTo(15);
  }

  @Test
  void calculateEstimatedWait_앞에_아무도_없으면_0() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThat(s.calculateEstimatedWait(0)).isZero();
  }

  @Test
  void update_테이블_수가_0이면_예외() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThatThrownBy(() -> s.update(0, 30, null, null, 10, true, LocalTime.of(5, 0), 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("테이블 수");
  }

  @Test
  void update_유예_시간이_음수면_예외() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThatThrownBy(() -> s.update(5, 30, null, null, 10, true, LocalTime.of(5, 0), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("유예");
  }

  @Test
  void createDefault_기본값은_05시_5분() {
    StoreSettings s = StoreSettings.createDefault(UUID.randomUUID());

    assertThat(s.getBusinessDayStart()).isEqualTo(LocalTime.of(5, 0));
    assertThat(s.getCallGraceMinutes()).isEqualTo(5);
  }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew test --tests "*.StoreSettingsTest"`
Expected: 컴파일 실패 — `restore(...)` 인자 개수 불일치, `getBusinessDayStart()` 없음.

- [ ] **Step 3: `StoreSettings` 구현**

필드 두 개를 추가하고 생성자·팩토리·`update`를 확장한다. 기존 필드 선언 아래에 추가:

```java
  private final LocalTime businessDayStart;
  private final int callGraceMinutes;
```

기본값 상수 추가:

```java
  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);
  private static final int DEFAULT_CALL_GRACE_MINUTES = 5;
```

생성자에 두 인자를 추가하고, 팩토리를 이렇게 바꾼다:

```java
  public static StoreSettings createDefault(UUID storeId) {
    return new StoreSettings(UUID.randomUUID(), storeId, DEFAULT_TABLE_COUNT,
        DEFAULT_AVG_TURNOVER_MINUTES, null, null, DEFAULT_ALERT_THRESHOLD, DEFAULT_ALERT_ENABLED,
        DEFAULT_BUSINESS_DAY_START, DEFAULT_CALL_GRACE_MINUTES);
  }

  public static StoreSettings restore(UUID id, UUID storeId, int tableCount,
      int avgTurnoverMinutes, LocalTime openTime, LocalTime closeTime,
      int alertThreshold, boolean alertEnabled,
      LocalTime businessDayStart, int callGraceMinutes) {
    return new StoreSettings(id, storeId, tableCount, avgTurnoverMinutes,
        openTime, closeTime, alertThreshold, alertEnabled, businessDayStart, callGraceMinutes);
  }

  public StoreSettings update(int tableCount, int avgTurnoverMinutes, LocalTime openTime,
      LocalTime closeTime, int alertThreshold, boolean alertEnabled,
      LocalTime businessDayStart, int callGraceMinutes) {
    if (tableCount < 1 || tableCount > 100) {
      throw new IllegalArgumentException("테이블 수는 1~100 이어야 합니다. 입력: " + tableCount);
    }
    if (callGraceMinutes < 0 || callGraceMinutes > 60) {
      throw new IllegalArgumentException("호출 유예 시간은 0~60분이어야 합니다. 입력: " + callGraceMinutes);
    }
    return new StoreSettings(id, storeId, tableCount, avgTurnoverMinutes,
        openTime, closeTime, alertThreshold, alertEnabled, businessDayStart, callGraceMinutes);
  }
```

도메인 메서드 두 개:

```java
  /**
   * 이 시각이 속한 영업일. 영업일 시작 시각 이전이면 전날로 귀속된다.
   */
  public LocalDate businessDateOf(LocalDateTime at) {
    return at.toLocalTime().isBefore(businessDayStart)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }

  /**
   * 앞선 팀 수 기준 예상 대기시간(분). 곱셈을 먼저 해 정수 나눗셈 절사를 피한다.
   */
  public int calculateEstimatedWait(int aheadCount) {
    return Math.toIntExact(Math.round((double) avgTurnoverMinutes * aheadCount / tableCount));
  }
```

`java.time.LocalDate` import를 추가한다.

- [ ] **Step 4: 테스트 통과 확인**

> ⚠️ **먼저 Step 6·7을 끝낸 뒤 이 스텝을 실행한다.** Step 3에서 `restore()`가 8→10 인자, `update()`가 6→8 인자로 바뀌므로 `StoreSettingsJpaEntity`(Step 6)와 `StoreSettingsService`(Step 7)를 고치기 전에는 **소스 세트 전체가 컴파일되지 않아** 이 테스트를 돌릴 수 없다. 순서는 3 → 5 → 6 → 7 → 4 → 8 이 된다.

Run: `cd backend && ./gradlew test --tests "*.StoreSettingsTest"`
Expected: 10개 PASS.

- [ ] **Step 5: 마이그레이션 작성**

`backend/src/main/resources/db/migration/V2__store_settings_business_day.sql`

```sql
-- 영업일 경계와 호출 유예 시간. 채번·통계·가명처리가 공통으로 쓰는 기준이다.
ALTER TABLE store_settings
  ADD COLUMN business_day_start TIME NOT NULL DEFAULT '05:00',
  ADD COLUMN call_grace_minutes INT  NOT NULL DEFAULT 5;
```

- [ ] **Step 6: JPA 엔티티 매핑 추가**

`StoreSettingsJpaEntity`에 필드·생성자 인자·`from`·`toDomain`을 확장한다.

```java
  @Column(name = "business_day_start", nullable = false)
  private LocalTime businessDayStart;

  @Column(name = "call_grace_minutes", nullable = false)
  private int callGraceMinutes;
```

`from(...)`의 마지막에 `settings.getBusinessDayStart(), settings.getCallGraceMinutes()`를, `toDomain()`의 `restore(...)` 마지막에 `businessDayStart, callGraceMinutes`를 추가한다.

- [ ] **Step 7: 요청/응답 DTO와 서비스 확장**

`UpdateStoreSettingsRequest`에 추가:

```java
  private LocalTime businessDayStart;

  @Min(0)
  @Max(60)
  private int callGraceMinutes;
```

`StoreSettingsService.updateSettings`의 `settings.update(...)` 호출에 두 인자를 추가한다:

```java
    StoreSettings updated = settings.update(
        request.getTableCount(),
        request.getAvgTurnoverMinutes(),
        request.getOpenTime(),
        request.getCloseTime(),
        request.getAlertThreshold(),
        request.isAlertEnabled(),
        request.getBusinessDayStart(),
        request.getCallGraceMinutes()
    );
```

`StoreSettingsResponse`는 필드 두 개를 추가하고, 잘못 조립되어 있던 안내 문구도 실제 공식에 맞게 고친다:

```java
public record StoreSettingsResponse(
    int tableCount,
    int avgTurnoverMinutes,
    LocalTime openTime,
    LocalTime closeTime,
    int alertThreshold,
    boolean alertEnabled,
    LocalTime businessDayStart,
    int callGraceMinutes,
    String estimatedWaitFormulaExample
) {

  public static StoreSettingsResponse from(StoreSettings settings) {
    String formula = "앞선 팀 수 × " + settings.getAvgTurnoverMinutes()
        + "분 ÷ " + settings.getTableCount() + "테이블";
    return new StoreSettingsResponse(
        settings.getTableCount(),
        settings.getAvgTurnoverMinutes(),
        settings.getOpenTime(),
        settings.getCloseTime(),
        settings.getAlertThreshold(),
        settings.isAlertEnabled(),
        settings.getBusinessDayStart(),
        settings.getCallGraceMinutes(),
        formula
    );
  }
}
```

- [ ] **Step 8: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: 전부 PASS. 기존 `StoreSettingsServiceTest`·`StoreSettingsRepositoryImplTest`가 `restore`/`update` 시그니처 변경으로 컴파일 실패하면, 호출부에 `LocalTime.of(5, 0), 5`를 추가해 고친다.

- [ ] **Step 9: 커밋**

```bash
git add backend/
git commit -m "feat: StoreSettings에 영업일 시작 시각과 호출 유예 시간 추가

businessDateOf() 로 영업일 귀속을 판정한다. 새벽 영업분이 전날로 묶인다.
예상 대기시간 공식의 정수 나눗셈 절사를 교정한다. 곱셈을 먼저 해
테이블 수가 회전시간보다 커도 0분이 되지 않는다."
```

---

## Task 2: WaitingEntry — 영업일·시각·해시 필드와 영속 매핑

가장 결합이 큰 태스크다. `create`/`restore` 시그니처가 바뀌므로 JPA 엔티티와 호출부를 함께 고쳐야 빌드가 초록으로 유지된다. 동작 변화는 없고 새 컬럼이 채워지기 시작하는 것까지가 이 태스크의 범위다.

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__waiting_business_date.sql`
- Modify: `backend/.../waiting/domain/WaitingEntry.java`
- Modify: `backend/.../waiting/infrastructure/WaitingEntryJpaEntity.java`
- Modify: `backend/.../waiting/customer/application/WaitingService.java`
- Test: `backend/src/test/java/com/qrwait/api/waiting/domain/WaitingEntryTest.java`

**Interfaces:**
- Consumes: `StoreSettings.businessDateOf(...)` (Task 1)
- Produces:
  - `WaitingEntry.create(UUID storeId, String phoneNumber, int partySize, int waitingNumber, LocalDate businessDate)`
  - `WaitingEntry.restore(UUID, UUID, String, int, int, WaitingStatus, LocalDateTime createdAt, LocalDate businessDate, LocalDateTime calledAt, LocalDateTime enteredAt, String phoneHash)`
  - getter: `getBusinessDate()`, `getCalledAt()`, `getEnteredAt()`, `getPhoneHash()`
  - `call()`이 `calledAt`을, `enter()`가 `enteredAt`을 기록한다

- [ ] **Step 1: 실패하는 테스트 추가**

`WaitingEntryTest`에 헬퍼와 테스트를 추가한다. 기존 두 테스트의 `restore(...)` 호출도 새 시그니처로 고친다.

```java
  private WaitingEntry waiting(WaitingStatus status, LocalDateTime calledAt) {
    return WaitingEntry.restore(
        UUID.randomUUID(), UUID.randomUUID(), "010-1111-0001", 2, 1,
        status, LocalDateTime.of(2026, 9, 1, 12, 0),
        LocalDate.of(2026, 9, 1), calledAt, null, null);
  }

  @Test
  void create_는_전달받은_영업일을_보존한다() {
    WaitingEntry entry = WaitingEntry.create(
        UUID.randomUUID(), "010-1111-0001", 2, 1, LocalDate.of(2026, 9, 1));

    assertThat(entry.getBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(entry.getCalledAt()).isNull();
    assertThat(entry.getEnteredAt()).isNull();
    assertThat(entry.getPhoneHash()).isNull();
  }

  @Test
  void call_은_호출_시각을_기록한다() {
    WaitingEntry called = waiting(WaitingStatus.WAITING, null).call();

    assertThat(called.getStatus()).isEqualTo(WaitingStatus.CALLED);
    assertThat(called.getCalledAt()).isNotNull();
  }

  @Test
  void enter_는_입장_시각을_기록한다() {
    WaitingEntry entered = waiting(WaitingStatus.CALLED, LocalDateTime.now()).enter();

    assertThat(entered.getStatus()).isEqualTo(WaitingStatus.ENTERED);
    assertThat(entered.getEnteredAt()).isNotNull();
  }
```

`java.time.LocalDate` import를 추가한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew test --tests "*.WaitingEntryTest"`
Expected: 컴파일 실패 — `create`/`restore` 인자 개수 불일치.

- [ ] **Step 3: `WaitingEntry` 확장**

필드 네 개를 추가한다:

```java
  private final LocalDate businessDate;
  private final LocalDateTime calledAt;
  private final LocalDateTime enteredAt;
  private final String phoneHash;
```

생성자를 전 필드로 확장하고, 팩토리와 전이 메서드를 바꾼다. 전이가 늘어 인자가 길어지므로 **내부 복사 헬퍼**를 두어 반복을 줄인다:

```java
  private WaitingEntry with(WaitingStatus newStatus, LocalDateTime newCalledAt,
      LocalDateTime newEnteredAt, String newPhoneNumber, String newPhoneHash) {
    return new WaitingEntry(id, storeId, newPhoneNumber, partySize, waitingNumber,
        newStatus, createdAt, businessDate, newCalledAt, newEnteredAt, newPhoneHash);
  }

  public static WaitingEntry create(UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, LocalDate businessDate) {
    return new WaitingEntry(UUID.randomUUID(), storeId, phoneNumber, partySize, waitingNumber,
        WaitingStatus.WAITING, LocalDateTime.now(), businessDate, null, null, null);
  }

  public static WaitingEntry restore(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, WaitingStatus status, LocalDateTime createdAt,
      LocalDate businessDate, LocalDateTime calledAt, LocalDateTime enteredAt, String phoneHash) {
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber,
        status, createdAt, businessDate, calledAt, enteredAt, phoneHash);
  }
```

기존 전이 메서드를 헬퍼 기반으로 바꾼다. `call()`과 `enter()`만 시각을 기록한다:

```java
  public WaitingEntry call() {
    if (status != WaitingStatus.WAITING) {
      throw new IllegalStateException(
          "call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.CALLED, LocalDateTime.now(), enteredAt, phoneNumber, phoneHash);
  }

  public WaitingEntry enter() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "enter() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.ENTERED, calledAt, LocalDateTime.now(), phoneNumber, phoneHash);
  }

  public WaitingEntry cancel() {
    if (status != WaitingStatus.WAITING && status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "cancel() 은 WAITING 또는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.CANCELLED, calledAt, enteredAt, phoneNumber, phoneHash);
  }

  public WaitingEntry noShow() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "noShow() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.NO_SHOW, calledAt, enteredAt, phoneNumber, phoneHash);
  }
```

`java.time.LocalDate` import를 추가한다.

- [ ] **Step 4: 마이그레이션 작성**

`backend/src/main/resources/db/migration/V3__waiting_business_date.sql`

```sql
-- 영업일 기준 채번·통계·가명처리를 위한 컬럼과 제약.
ALTER TABLE waiting_entries
  ADD COLUMN business_date DATE,
  ADD COLUMN called_at     TIMESTAMP,
  ADD COLUMN entered_at    TIMESTAMP,
  ADD COLUMN phone_hash    VARCHAR(64);

-- 기존 행 backfill. 실서비스 데이터는 없으나 로컬/개발 DB를 위한 안전장치.
-- 기본 영업일 시작(05:00)을 가정한다.
UPDATE waiting_entries
   SET business_date = (created_at - INTERVAL '5 hours')::date
 WHERE business_date IS NULL;

ALTER TABLE waiting_entries ALTER COLUMN business_date SET NOT NULL;

-- phone_number 는 NOT NULL 을 유지한다. 가명처리 후에도 뒤 4자리가 들어간다.
ALTER TABLE waiting_entries
  ADD CONSTRAINT uq_waiting_store_business_date_number
  UNIQUE (store_id, business_date, waiting_number);
```

- [ ] **Step 5: JPA 엔티티 매핑 추가**

`WaitingEntryJpaEntity`에 필드를 추가하고 생성자·`from`·`toDomain`을 확장한다.

```java
  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "called_at")
  private LocalDateTime calledAt;

  @Column(name = "entered_at")
  private LocalDateTime enteredAt;

  @Column(name = "phone_hash", length = 64)
  private String phoneHash;
```

`from(entry)`에 `entry.getBusinessDate(), entry.getCalledAt(), entry.getEnteredAt(), entry.getPhoneHash()`를, `toDomain()`의 `restore(...)`에 `businessDate, calledAt, enteredAt, phoneHash`를 추가한다. `java.time.LocalDate` import 추가.

- [ ] **Step 6: `WaitingService.register`가 영업일을 확정하도록 수정**

`register` 안에서 설정을 한 번 로드해 영업일과 예상 대기시간에 함께 쓴다. 기존 `estimatedWaitMinutes(...)` 헬퍼는 남기되, 등록 경로는 로드한 설정을 재사용한다.

```java
  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);

  // register(...) 안, store 검증 직후
  Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(storeId);
  LocalDate businessDate = settings
      .map(s -> s.businessDateOf(LocalDateTime.now()))
      .orElseGet(() -> defaultBusinessDate(LocalDateTime.now()));

  int waitingNumber = waitingRepository.findNextWaitingNumber(storeId);

  WaitingEntry entry = WaitingEntry.create(
      storeId, request.getPhoneNumber(), request.getPartySize(), waitingNumber, businessDate);
```

그리고 폴백 헬퍼:

```java
  /** 매장 설정이 없을 때의 영업일 기준. StoreSettings 기본값(05:00)과 같아야 한다. */
  private LocalDate defaultBusinessDate(LocalDateTime at) {
    return at.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }
```

`java.time.LocalDate`, `java.time.LocalDateTime`, `java.time.LocalTime`, `java.util.Optional`, `com.qrwait.api.store.domain.StoreSettings` import를 추가한다.

- [ ] **Step 7: 테스트 실행 및 호출부 정리**

Run: `cd backend && ./gradlew test`
Expected: `WaitingEntry.create`/`restore`를 쓰는 모든 테스트가 컴파일 실패한다. 아래 파일들의 호출을 새 시그니처로 고친다:
- `WaitingRepositoryImplTest` — `create(storeId, phone, party, number)` → `create(storeId, phone, party, number, LocalDate.now())`
- `WaitingServiceTest`, `WaitingManagementServiceTest` — `restore(...)`에 `LocalDate.now(), null, null, null` 추가
- `SsePublisherTest`, `OwnerWaitingControllerTest` 등 `WaitingEntry`를 만드는 곳 전부

수정 후 다시 실행해 전부 PASS를 확인한다.

- [ ] **Step 8: 커밋**

```bash
git add backend/
git commit -m "feat: WaitingEntry에 영업일·호출시각·입장시각·해시 필드 추가

business_date 를 등록 시점에 확정해 저장하고 (store_id, business_date,
waiting_number) 유니크 제약을 건다. call()/enter() 가 각각 시각을 기록해
실측 대기시간 수집이 시작된다."
```

---

## Task 3: 영업일 기준 채번 + 동시성 재시도

**Files:**
- Modify: `backend/.../waiting/domain/WaitingRepository.java`
- Modify: `backend/.../waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `backend/.../waiting/infrastructure/WaitingRepositoryImpl.java`
- Create: `backend/.../waiting/customer/application/WaitingRegistrar.java`
- Create: `backend/.../waiting/domain/WaitingNumberConflictException.java`
- Modify: `backend/.../waiting/customer/application/WaitingService.java`
- Modify: `backend/.../shared/web/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`
- Test: `backend/src/test/java/com/qrwait/api/waiting/customer/application/WaitingRegistrationConcurrencyTest.java` (신규)

**Interfaces:**
- Consumes: Task 2의 `business_date` 컬럼과 유니크 제약
- Produces:
  - `WaitingRepository.findNextWaitingNumber(UUID storeId, LocalDate businessDate) -> int`
  - `WaitingRegistrar.registerOnce(UUID storeId, RegisterWaitingRequest request) -> RegisterWaitingResponse` (`@Transactional`)
  - `WaitingService.register(...)`는 트랜잭션 없이 재시도만 담당

- [ ] **Step 1: 실패하는 통합 테스트 작성 (채번)**

`WaitingRepositoryImplTest`에 추가:

```java
  @Test
  void findNextWaitingNumber_countsPerBusinessDate() {
    LocalDate day1 = LocalDate.of(2026, 9, 1);
    LocalDate day2 = LocalDate.of(2026, 9, 2);
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1, day1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 2, 2, day1));

    assertThat(waitingRepository.findNextWaitingNumber(savedStore.getId(), day1)).isEqualTo(3);
    assertThat(waitingRepository.findNextWaitingNumber(savedStore.getId(), day2)).isEqualTo(1);
  }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest"`
Expected: 컴파일 실패 — `findNextWaitingNumber(UUID, LocalDate)` 없음.

- [ ] **Step 3: 리포지토리 계층 구현**

`WaitingEntryJpaRepository`의 기존 `findMaxWaitingNumberByStoreId`를 영업일 조건이 있는 쿼리로 교체한다:

```java
  @Query("""
      SELECT COALESCE(MAX(w.waitingNumber), 0)
        FROM WaitingEntryJpaEntity w
       WHERE w.storeId = :storeId
         AND w.businessDate = :businessDate
      """)
  int findMaxWaitingNumberByStoreIdAndBusinessDate(@Param("storeId") UUID storeId,
      @Param("businessDate") LocalDate businessDate);
```

포트(`WaitingRepository`) 시그니처를 `int findNextWaitingNumber(UUID storeId, LocalDate businessDate);`로 바꾸고, `WaitingRepositoryImpl`을 맞춘다:

```java
  @Override
  public int findNextWaitingNumber(UUID storeId, LocalDate businessDate) {
    return waitingEntryJpaRepository
        .findMaxWaitingNumberByStoreIdAndBusinessDate(storeId, businessDate) + 1;
  }
```

- [ ] **Step 4: 테스트 통과 확인**

> ⚠️ **먼저 Step 5·6·7을 끝낸 뒤 이 스텝을 실행한다.** Step 3에서 포트가 `findNextWaitingNumber(UUID)` → `(UUID, LocalDate)`로 바뀌는데, Task 2가 남긴 `WaitingService`의 1-인자 호출은 Step 7에서야 사라진다. 그 전에는 컴파일되지 않는다. 순서는 3 → 5 → 6 → 7 → 4 → 8 이 된다.

Run: `cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 5: 예외 타입 추가**

`backend/.../waiting/domain/WaitingNumberConflictException.java`

```java
package com.qrwait.api.waiting.domain;

import java.util.UUID;

public class WaitingNumberConflictException extends RuntimeException {

  public WaitingNumberConflictException(UUID storeId) {
    super("대기번호 채번에 반복 실패했습니다. storeId=" + storeId);
  }
}
```

`GlobalExceptionHandler`에 매핑을 추가한다 (기존 핸들러들과 같은 형식):

```java
  @ExceptionHandler(WaitingNumberConflictException.class)
  public ResponseEntity<ErrorResponse> handleWaitingNumberConflict(WaitingNumberConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("WAITING_NUMBER_CONFLICT", e.getMessage()));
  }
```

- [ ] **Step 6: 트랜잭션 경계를 분리한 `WaitingRegistrar` 생성**

> **왜 별도 빈인가:** 유니크 제약 위반은 트랜잭션을 rollback-only로 오염시키므로 **같은 트랜잭션 안에서 재시도하면 반드시 실패한다.** 매 시도가 새 트랜잭션이어야 한다. 같은 빈 안에서 `this.registerOnce()`를 부르면 프록시를 우회해 `@Transactional`이 적용되지 않으므로, 트랜잭션 단위를 별도 빈으로 뺀다.

`backend/.../waiting/customer/application/WaitingRegistrar.java` — 기존 `WaitingService.register`의 본문을 그대로 옮긴다 (Task 2에서 수정한 영업일 확정 로직 포함).

```java
package com.qrwait.api.waiting.customer.application;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.store.domain.StoreStatus;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingResponse;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import com.qrwait.api.waiting.domain.event.WaitingRegisteredEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 1회 시도의 트랜잭션 경계. 채번 경합 재시도는 트랜잭션 밖(WaitingService)에서 이뤄져야
 * 하므로, 트랜잭션 단위를 별도 빈으로 분리한다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRegistrar {

  private static final int DEFAULT_MINUTES_PER_PERSON = 5;
  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);

  private final WaitingRepository waitingRepository;
  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public RegisterWaitingResponse registerOnce(UUID storeId, RegisterWaitingRequest request) {
    Store store = storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
    if (store.getStatus() != StoreStatus.OPEN) {
      throw new StoreNotAvailableException(store.getStatus());
    }

    Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(storeId);
    LocalDateTime now = LocalDateTime.now();
    LocalDate businessDate = settings
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> defaultBusinessDate(now));

    int waitingNumber = waitingRepository.findNextWaitingNumber(storeId, businessDate);

    WaitingEntry saved = waitingRepository.save(WaitingEntry.create(
        storeId, request.getPhoneNumber(), request.getPartySize(), waitingNumber, businessDate));

    int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
    int estimatedWaitMinutes = settings
        .map(s -> s.calculateEstimatedWait(totalWaiting))
        .orElse(totalWaiting * DEFAULT_MINUTES_PER_PERSON);

    eventPublisher.publishEvent(new WaitingRegisteredEvent(storeId));

    // 신규 등록자는 대기열 맨 뒤이므로 currentRank == totalWaiting (의도된 동일 값)
    return new RegisterWaitingResponse(
        saved.getId(), waitingNumber, totalWaiting, totalWaiting, estimatedWaitMinutes);
  }

  /** 매장 설정이 없을 때의 영업일 기준. StoreSettings 기본값(05:00)과 같아야 한다. */
  private LocalDate defaultBusinessDate(LocalDateTime at) {
    return at.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
        ? at.toLocalDate().minusDays(1)
        : at.toLocalDate();
  }
}
```

- [ ] **Step 7: `WaitingService.register`를 재시도 루프로 교체**

`WaitingService`에서 기존 `register` 본문을 지우고 아래로 바꾼다. `@Transactional`을 **붙이지 않는다.** 나머지 메서드(`getStatus`·`cancel`·`getStoreWaitingStatus`)와 `storeRepository` 의존은 그대로 둔다.

```java
  private static final int MAX_REGISTER_ATTEMPTS = 3;

  private final WaitingRegistrar waitingRegistrar;   // 생성자 주입 필드에 추가

  /**
   * 채번 경합은 유니크 제약 위반으로 드러난다. 위반된 트랜잭션은 rollback-only 이므로
   * 매 시도를 새 트랜잭션으로 돌린다.
   */
  public RegisterWaitingResponse register(UUID storeId, RegisterWaitingRequest request) {
    for (int attempt = 1; attempt <= MAX_REGISTER_ATTEMPTS; attempt++) {
      try {
        return waitingRegistrar.registerOnce(storeId, request);
      } catch (DataIntegrityViolationException e) {
        if (attempt == MAX_REGISTER_ATTEMPTS) {
          throw new WaitingNumberConflictException(storeId);
        }
      }
    }
    throw new WaitingNumberConflictException(storeId);
  }
```

`org.springframework.dao.DataIntegrityViolationException`과 `WaitingNumberConflictException` import를 추가한다. 더 이상 쓰이지 않는 import(`StoreNotAvailableException`, `WaitingRegisteredEvent` 등)는 정리한다.

- [ ] **Step 8: 동시성 통합 테스트 작성**

`backend/src/test/java/com/qrwait/api/waiting/customer/application/WaitingRegistrationConcurrencyTest.java`

```java
package com.qrwait.api.waiting.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.support.IntegrationTestSupport;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingRegistrationConcurrencyTest extends IntegrationTestSupport {

  private static final int CONCURRENCY = 10;

  @Autowired
  private WaitingService waitingService;

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private StoreSettingsRepository storeSettingsRepository;

  private UUID storeId;

  @BeforeEach
  void setUp() {
    Store store = storeRepository.save(Store.create(UUID.randomUUID(), "동시성 테스트 식당", null));
    storeId = store.getId();
    storeSettingsRepository.save(StoreSettings.createDefault(storeId));
  }

  @Test
  void 동시_등록에도_대기번호가_중복되지_않는다() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
    try {
      List<Callable<RegisterWaitingResponse>> tasks = IntStream.range(0, CONCURRENCY)
          .mapToObj(i -> (Callable<RegisterWaitingResponse>) () -> {
            RegisterWaitingRequest request = new RegisterWaitingRequest();
            request.setPhoneNumber("010-1111-%04d".formatted(i));
            request.setPartySize(2);
            return waitingService.register(storeId, request);
          })
          .toList();

      List<Integer> numbers = pool.invokeAll(tasks).stream()
          .map(WaitingRegistrationConcurrencyTest::get)
          .map(RegisterWaitingResponse::waitingNumber)
          .sorted()
          .toList();

      assertThat(numbers).doesNotHaveDuplicates();
      assertThat(numbers).containsExactlyElementsOf(
          IntStream.rangeClosed(1, CONCURRENCY).boxed().toList());
    } finally {
      pool.shutdown();
    }
  }

  private static <T> T get(Future<T> future) {
    try {
      return future.get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
```

> `RegisterWaitingRequest`는 `@Getter @Setter @NoArgsConstructor`이므로 setter로 조립한다.

- [ ] **Step 9: 동시성 테스트 실행**

Run: `cd backend && ./gradlew test --tests "*.WaitingRegistrationConcurrencyTest"`
Expected: PASS. 1~10번이 중복 없이 발급된다.

> **실패 시:** 재시도가 3회로 부족할 수 있다. 로그에 `DataIntegrityViolationException`이 반복되면 `MAX_REGISTER_ATTEMPTS`를 10으로 올린다. 그래도 실패하면 재시도가 트랜잭션 안에서 돌고 있다는 뜻이므로 Step 6~7의 빈 분리를 다시 확인한다.

- [ ] **Step 10: 전체 테스트 + 커밋**

Run: `cd backend && ./gradlew test`
Expected: 전부 PASS (`WaitingServiceTest`가 `WaitingRegistrar` 목을 요구하면 목 주입을 추가한다).

```bash
git add backend/
git commit -m "feat: 대기번호를 영업일 단위로 채번하고 경합을 재시도로 해소

MAX(waiting_number)+1 을 영업일로 한정해 매일 1번부터 다시 시작한다.
동시 등록은 유니크 제약이 거부하며, 오염된 트랜잭션에서는 재시도할 수 없으므로
등록 1회 시도를 WaitingRegistrar 로 분리해 매 시도를 새 트랜잭션으로 돌린다."
```

---

## Task 4: 통계·대기 목록을 영업일 기준으로 전환

지난 영업일에 등록하고 오지 않은 손님이 오늘 대시보드에 남는 결함을 함께 닫는다.

**Files:**
- Modify: `backend/.../waiting/domain/WaitingRepository.java`
- Modify: `backend/.../waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `backend/.../waiting/infrastructure/WaitingRepositoryImpl.java`
- Modify: `backend/.../waiting/management/application/WaitingManagementService.java`
- Modify: `backend/.../waiting/management/dto/TodayWaitingResponse.java`
- Test: `backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`

**Interfaces:**
- Consumes: Task 2의 `business_date`, Task 1의 `businessDateOf`
- Produces:
  - `WaitingRepository.findActiveByStoreId(UUID storeId, LocalDate businessDate)`
  - `WaitingRepository.findAllByStoreIdAndBusinessDate(UUID storeId, LocalDate businessDate)`
  - `WaitingRepository.countByStatusForStoreAndBusinessDate(UUID storeId, LocalDate businessDate)`
  - `TodayWaitingResponse`에 `waitedMinutes` 필드

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`WaitingRepositoryImplTest`에 추가:

```java
  @Test
  void findActiveByStoreId_excludesOtherBusinessDates() {
    LocalDate today = LocalDate.of(2026, 9, 2);
    LocalDate yesterday = LocalDate.of(2026, 9, 1);
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1, yesterday));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 2, 1, today));

    List<WaitingEntry> result = waitingRepository.findActiveByStoreId(savedStore.getId(), today);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getBusinessDate()).isEqualTo(today);
  }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest"`
Expected: 컴파일 실패 — `findActiveByStoreId(UUID, LocalDate)` 없음.

- [ ] **Step 3: JPA 쿼리를 영업일 기준으로 교체**

`WaitingEntryJpaRepository`에서 시각 범위 쿼리 두 개를 영업일 등호 쿼리로 바꾸고, 활성 조회에 영업일 조건을 더한다.

```java
  List<WaitingEntryJpaEntity> findByStoreIdAndBusinessDateAndStatusInOrderByCreatedAtAsc(
      UUID storeId, LocalDate businessDate, List<String> statuses);

  @Query("""
        SELECT w
          FROM WaitingEntryJpaEntity w
         WHERE w.storeId = :storeId
           AND w.businessDate = :businessDate
      ORDER BY w.waitingNumber DESC
      """)
  List<WaitingEntryJpaEntity> findAllByStoreIdAndBusinessDate(@Param("storeId") UUID storeId,
      @Param("businessDate") LocalDate businessDate);

  @Query("""
        SELECT w.status AS status, COUNT(w) AS count
          FROM WaitingEntryJpaEntity w
         WHERE w.storeId = :storeId
           AND w.businessDate = :businessDate
      GROUP BY w.status
      """)
  List<StatusCount> countByStatusGrouped(@Param("storeId") UUID storeId,
      @Param("businessDate") LocalDate businessDate);
```

기존 `findByStoreIdAndStatusInOrderByCreatedAtAsc`와 `findAllByStoreIdBetween`은 삭제한다.

- [ ] **Step 4: 포트와 구현체 수정**

`WaitingRepository`:

```java
  List<WaitingEntry> findActiveByStoreId(UUID storeId, LocalDate businessDate);

  List<WaitingEntry> findAllByStoreIdAndBusinessDate(UUID storeId, LocalDate businessDate);

  Map<WaitingStatus, Long> countByStatusForStoreAndBusinessDate(UUID storeId, LocalDate businessDate);
```

(기존 `findAllByStoreIdAndDate`·`countByStatusForStoreAndDate`는 대체된다)

`WaitingRepositoryImpl`:

```java
  @Override
  public List<WaitingEntry> findActiveByStoreId(UUID storeId, LocalDate businessDate) {
    List<String> activeStatuses = List.of(WaitingStatus.WAITING.name(), WaitingStatus.CALLED.name());
    return waitingEntryJpaRepository
        .findByStoreIdAndBusinessDateAndStatusInOrderByCreatedAtAsc(storeId, businessDate, activeStatuses)
        .stream()
        .map(WaitingEntryJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<WaitingEntry> findAllByStoreIdAndBusinessDate(UUID storeId, LocalDate businessDate) {
    return waitingEntryJpaRepository.findAllByStoreIdAndBusinessDate(storeId, businessDate)
        .stream()
        .map(WaitingEntryJpaEntity::toDomain)
        .toList();
  }

  @Override
  public Map<WaitingStatus, Long> countByStatusForStoreAndBusinessDate(UUID storeId, LocalDate businessDate) {
    Map<WaitingStatus, Long> result = new EnumMap<>(WaitingStatus.class);
    for (WaitingEntryJpaRepository.StatusCount row :
        waitingEntryJpaRepository.countByStatusGrouped(storeId, businessDate)) {
      result.put(WaitingStatus.valueOf(row.getStatus()), row.getCount());
    }
    return result;
  }
```

`java.time.LocalDateTime` import가 더 이상 필요 없으면 정리한다.

- [ ] **Step 5: 테스트 통과 확인**

> ⚠️ **먼저 Step 6·7을 끝낸 뒤 이 스텝을 실행한다.** Step 4에서 포트의 `findActiveByStoreId`·`findAllByStoreIdAndDate`·`countByStatusForStoreAndDate` 시그니처가 바뀌므로, 호출부인 `WaitingManagementService`(Step 7)와 `WaitingService`·`WaitingRegistrar`·`SsePublisher`(Step 6)를 고치기 전에는 컴파일되지 않는다. 순서는 3 → 4 → 6 → 7 → 5 → 8 → 9 가 된다.

Run: `cd backend && ./gradlew test --tests "*.WaitingRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 6: 손님 쪽 집계·순번도 영업일로 한정**

목록만 한정하면 손님 화면의 "앞에 N팀"과 대기 인원 수에는 지난 영업일 잔류분이 계속 잡힌다. 상태 기반 조회 두 개도 영업일을 받아야 한다.

`WaitingEntryJpaRepository` — 기존 두 메서드를 교체한다:

```java
  List<WaitingEntryJpaEntity> findByStoreIdAndBusinessDateAndStatus(
      UUID storeId, LocalDate businessDate, String status);

  int countByStoreIdAndBusinessDateAndStatus(UUID storeId, LocalDate businessDate, String status);
```

포트(`WaitingRepository`)와 `WaitingRepositoryImpl`을 같은 형태로 바꾼다:

```java
  List<WaitingEntry> findByStoreIdAndStatus(UUID storeId, LocalDate businessDate, WaitingStatus status);

  int countByStoreIdAndStatus(UUID storeId, LocalDate businessDate, WaitingStatus status);
```

호출부 세 곳을 고친다:

1. **`WaitingRegistrar.registerOnce`** — 이미 `businessDate`를 갖고 있으므로 그대로 넘긴다.
   ```java
   int totalWaiting = waitingRepository.countByStoreIdAndStatus(
       storeId, businessDate, WaitingStatus.WAITING);
   ```

2. **`WaitingService.getStatus`** — 순번 계산의 기준을 해당 웨이팅의 영업일로 맞춘다. 같은 영업일 안에서만 앞뒤를 따지는 것이 옳다.
   ```java
   List<WaitingEntry> waitingList = waitingRepository
       .findByStoreIdAndStatus(entry.getStoreId(), entry.getBusinessDate(), WaitingStatus.WAITING);
   ```
   `WaitingService.getStoreWaitingStatus`는 매장의 현재 영업일을 계산해 넘긴다 (Task 2에서 추가한 `defaultBusinessDate` 폴백을 재사용).

3. **`SsePublisher.buildStoreStatus`** — 이미 `storeSettingsRepository`를 갖고 있으므로 영업일을 계산해 쓴다.
   ```java
   private WaitingStatusResponse buildStoreStatus(UUID storeId) {
     Optional<StoreSettings> settings = storeSettingsRepository.findByStoreId(storeId);
     LocalDateTime now = LocalDateTime.now();
     LocalDate businessDate = settings
         .map(s -> s.businessDateOf(now))
         .orElseGet(() -> now.toLocalTime().isBefore(LocalTime.of(5, 0))
             ? now.toLocalDate().minusDays(1)
             : now.toLocalDate());

     int total = waitingRepository.countByStoreIdAndStatus(storeId, businessDate, WaitingStatus.WAITING);
     int estimated = settings
         .map(s -> s.calculateEstimatedWait(total))
         .orElse(total * 5);
     return new WaitingStatusResponse(total, total, estimated);
   }
   ```

- [ ] **Step 7: `WaitingManagementService`가 영업일을 해석하도록 수정**

매장의 현재 영업일을 구하는 헬퍼를 추가하고, 세 조회가 이를 쓰게 한다.

```java
  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);

  private final StoreSettingsRepository storeSettingsRepository;   // 생성자 주입 추가

  /** 매장의 현재 영업일. 설정이 없으면 기본값(05:00) 기준으로 계산한다. */
  private LocalDate currentBusinessDate(UUID storeId) {
    LocalDateTime now = LocalDateTime.now();
    return storeSettingsRepository.findByStoreId(storeId)
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> now.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate());
  }
```

`getWaitingList`·`getDailySummary`·`getTodayWaitings`에서 `LocalDate.now()` 대신 이 헬퍼를 쓰고, 새 리포지토리 메서드를 호출하도록 바꾼다:

```java
  @Transactional(readOnly = true)
  public List<OwnerWaitingResponse> getWaitingList(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    return waitingRepository.findActiveByStoreId(storeId, currentBusinessDate(storeId)).stream()
        .map(this::toOwnerWaitingResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public DailySummaryResponse getDailySummary(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    DailySummary summary = DailySummary.from(
        waitingRepository.countByStatusForStoreAndBusinessDate(storeId, currentBusinessDate(storeId))
    );
    return DailySummaryResponse.from(summary);
  }
```

`getTodayWaitings`는 `findAllByStoreIdAndBusinessDate(storeId, currentBusinessDate(storeId))`를 호출한다.

- [ ] **Step 8: `TodayWaitingResponse`에 실측 대기시간 추가**

```java
package com.qrwait.api.waiting.management.dto;

import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record TodayWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    LocalDateTime createdAt,
    Long waitedMinutes
) {

  /** 등록 → 입장까지 실제로 걸린 시간. 입장하지 않은 건은 null. */
  public static TodayWaitingResponse from(WaitingEntry entry) {
    Long waited = entry.getEnteredAt() == null
        ? null
        : ChronoUnit.MINUTES.between(entry.getCreatedAt(), entry.getEnteredAt());
    return new TodayWaitingResponse(
        entry.getId(),
        entry.getWaitingNumber(),
        entry.getPhoneNumber(),
        entry.getPartySize(),
        entry.getStatus(),
        entry.getCreatedAt(),
        waited
    );
  }
}
```

`WaitingManagementService.getTodayWaitings`의 인라인 매핑을 `.map(TodayWaitingResponse::from)`으로 교체한다.

- [ ] **Step 9: 전체 테스트 + 커밋**

Run: `cd backend && ./gradlew test`
Expected: 전부 PASS. 아래 두 가지가 대표적인 수정 지점이다.
- `WaitingManagementServiceTest`·`SsePublisherTest`에 `StoreSettingsRepository` 목을 추가하고, `findByStoreId`가 `Optional.of(StoreSettings.createDefault(storeId))`를 반환하도록 스텁한다.
- `WaitingServiceTest`·`WaitingRepositoryImplTest`의 `findByStoreIdAndStatus`·`countByStoreIdAndStatus` 호출에 `businessDate` 인자를 추가한다.

```bash
git add backend/
git commit -m "feat: 통계와 대기 목록을 영업일 기준으로 조회

지난 영업일에 등록하고 오지 않은 손님이 오늘 대시보드에 남던 결함을 닫는다.
목록뿐 아니라 대기 인원 집계와 순번 계산도 영업일로 한정해, 손님 화면의
'앞에 N팀'에 어제 잔류분이 잡히지 않게 한다. 통계에는 그대로 남는다.
이력 응답에 등록→입장 실측 대기시간(waitedMinutes)을 추가한다."
```

---

## Task 5: 호출 유예와 미루기

**Files:**
- Modify: `backend/.../waiting/domain/WaitingEntry.java`
- Create: `backend/.../waiting/domain/event/WaitingPostponedEvent.java`
- Modify: `backend/.../waiting/management/application/WaitingManagementService.java`
- Modify: `backend/.../waiting/management/presentation/OwnerWaitingController.java`
- Modify: `backend/.../waiting/management/dto/OwnerWaitingResponse.java`
- Modify: `backend/.../waiting/customer/dto/MyWaitingStatusResponse.java`
- Modify: `backend/.../waiting/customer/application/WaitingService.java`
- Modify: `backend/.../shared/sse/SsePublisher.java`
- Modify: `backend/.../shared/sse/SseEventListener.java`
- Test: `backend/src/test/java/com/qrwait/api/waiting/domain/WaitingEntryTest.java`
- Test: `backend/src/test/java/com/qrwait/api/waiting/management/presentation/OwnerWaitingControllerTest.java`

**Interfaces:**
- Consumes: `WaitingEntry.getCalledAt()` (Task 2), `StoreSettings.getCallGraceMinutes()` (Task 1)
- Produces:
  - `WaitingEntry.postpone() -> WaitingEntry`
  - `WaitingEntry.isGraceExpired(LocalDateTime now, int graceMinutes) -> boolean`
  - `WaitingManagementService.postpone(UUID ownerId, UUID waitingId)`
  - `POST /api/owner/waitings/{waitingId}/postpone`
  - SSE 이벤트 `waiting-postponed`
  - `OwnerWaitingResponse.graceDeadline`, `MyWaitingStatusResponse.graceDeadline`

- [ ] **Step 1: 실패하는 도메인 테스트 추가**

`WaitingEntryTest`에 추가:

```java
  @Test
  void postpone_CALLED를_WAITING으로_되돌리고_호출시각을_지운다() {
    WaitingEntry postponed = waiting(WaitingStatus.CALLED, LocalDateTime.now()).postpone();

    assertThat(postponed.getStatus()).isEqualTo(WaitingStatus.WAITING);
    assertThat(postponed.getCalledAt()).isNull();
  }

  @Test
  void postpone_번호와_순서는_유지된다() {
    WaitingEntry before = waiting(WaitingStatus.CALLED, LocalDateTime.now());

    WaitingEntry after = before.postpone();

    assertThat(after.getWaitingNumber()).isEqualTo(before.getWaitingNumber());
    assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
  }

  @Test
  void postpone_CALLED가_아니면_예외() {
    WaitingEntry entry = waiting(WaitingStatus.WAITING, null);

    assertThatThrownBy(entry::postpone)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CALLED");
  }

  @Test
  void isGraceExpired_유예_경과_후_true() {
    LocalDateTime calledAt = LocalDateTime.of(2026, 9, 1, 12, 0);
    WaitingEntry entry = waiting(WaitingStatus.CALLED, calledAt);

    assertThat(entry.isGraceExpired(calledAt.plusMinutes(5).plusSeconds(1), 5)).isTrue();
  }

  @Test
  void isGraceExpired_유예_정각에는_false() {
    LocalDateTime calledAt = LocalDateTime.of(2026, 9, 1, 12, 0);
    WaitingEntry entry = waiting(WaitingStatus.CALLED, calledAt);

    assertThat(entry.isGraceExpired(calledAt.plusMinutes(5), 5)).isFalse();
  }

  @Test
  void isGraceExpired_CALLED가_아니면_항상_false() {
    WaitingEntry entry = waiting(WaitingStatus.WAITING, null);

    assertThat(entry.isGraceExpired(LocalDateTime.now(), 5)).isFalse();
  }
```

`assertThatThrownBy` static import를 추가한다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew test --tests "*.WaitingEntryTest"`
Expected: 컴파일 실패 — `postpone()`, `isGraceExpired(...)` 없음.

- [ ] **Step 3: 도메인 메서드 구현**

`WaitingEntry`에 추가:

```java
  /**
   * CALLED → WAITING. 호출을 취소하고 유예 타이머를 끈다. 번호와 순서는 그대로이므로
   * 다른 손님에게 영향이 없고, 점주는 다음 팀을 호출하면 된다.
   */
  public WaitingEntry postpone() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "postpone() 은 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return with(WaitingStatus.WAITING, null, enteredAt, phoneNumber, phoneHash);
  }

  /**
   * 호출 유예가 지났는가. 상태가 아니라 calledAt 에서 파생되는 값이므로 감시 스케줄러가 필요 없다.
   */
  public boolean isGraceExpired(LocalDateTime now, int graceMinutes) {
    return status == WaitingStatus.CALLED
        && calledAt != null
        && calledAt.plusMinutes(graceMinutes).isBefore(now);
  }
```

- [ ] **Step 4: 도메인 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*.WaitingEntryTest"`
Expected: 전부 PASS.

- [ ] **Step 5: 이벤트와 SSE 발행 경로 추가**

`backend/.../waiting/domain/event/WaitingPostponedEvent.java`

```java
package com.qrwait.api.waiting.domain.event;

import java.util.UUID;

public record WaitingPostponedEvent(UUID storeId, UUID waitingId) {

}
```

`SsePublisher`에 추가 (`broadcastCalled` 바로 아래):

```java
  /**
   * 미루기(호출 취소) 시 호출. 호출 화면에 있던 손님이 대기 화면으로 돌아가도록 알린다.
   * 클라이언트가 자신의 waitingId 와 비교해 처리한다.
   */
  public void broadcastPostponed(UUID storeId, UUID waitingId) {
    registry.broadcast(storeId, "waiting-postponed", Map.of("waitingId", waitingId));
  }
```

`SseEventListener`에 추가 — 손님 화면 복귀와 점주 목록 갱신을 함께 처리한다:

```java
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWaitingPostponed(WaitingPostponedEvent event) {
    ssePublisher.broadcastPostponed(event.storeId(), event.waitingId());
    ssePublisher.broadcastUpdate(event.storeId());
  }
```

- [ ] **Step 6: 애플리케이션 서비스에 `postpone` 추가**

`WaitingManagementService`에 추가 (기존 `enter`/`noShow`와 같은 형태):

```java
  @Transactional
  public void postpone(UUID ownerId, UUID waitingId) {
    WaitingEntry postponed = loadOwnedEntry(ownerId, waitingId).entry().postpone();
    waitingRepository.save(postponed);
    eventPublisher.publishEvent(
        new WaitingPostponedEvent(postponed.getStoreId(), postponed.getId()));
  }
```

- [ ] **Step 7: 컨트롤러 엔드포인트 추가**

`OwnerWaitingController`의 `noShowWaiting` 아래에 추가 (기존 세 액션과 동일한 형태):

```java
  @PostMapping("/waitings/{waitingId}/postpone")
  public ResponseEntity<Void> postponeWaiting(
      @AuthenticationPrincipal UUID ownerId,
      @PathVariable UUID waitingId) {
    waitingManagementService.postpone(ownerId, waitingId);
    return ResponseEntity.noContent().build();
  }
```

- [ ] **Step 8: 응답 DTO에 `graceDeadline` 추가**

`OwnerWaitingResponse`:

```java
public record OwnerWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    long elapsedMinutes,
    LocalDateTime graceDeadline
) {

}
```

`WaitingManagementService.toOwnerWaitingResponse`를 유예 시간을 받도록 바꾸고, `getWaitingList`가 설정을 한 번만 읽어 넘기게 한다:

```java
  @Transactional(readOnly = true)
  public List<OwnerWaitingResponse> getWaitingList(UUID ownerId) {
    UUID storeId = storeRepository.getByOwnerId(ownerId).getId();
    int graceMinutes = storeSettingsRepository.findByStoreId(storeId)
        .map(StoreSettings::getCallGraceMinutes)
        .orElse(DEFAULT_CALL_GRACE_MINUTES);
    return waitingRepository.findActiveByStoreId(storeId, currentBusinessDate(storeId)).stream()
        .map(entry -> toOwnerWaitingResponse(entry, graceMinutes))
        .toList();
  }

  private OwnerWaitingResponse toOwnerWaitingResponse(WaitingEntry entry, int graceMinutes) {
    long elapsedMinutes = ChronoUnit.MINUTES.between(entry.getCreatedAt(), LocalDateTime.now());
    LocalDateTime graceDeadline = entry.getCalledAt() == null
        ? null
        : entry.getCalledAt().plusMinutes(graceMinutes);
    return new OwnerWaitingResponse(
        entry.getId(), entry.getWaitingNumber(), entry.getPhoneNumber(),
        entry.getPartySize(), entry.getStatus(), elapsedMinutes, graceDeadline);
  }
```

`private static final int DEFAULT_CALL_GRACE_MINUTES = 5;` 상수를 추가한다.

`MyWaitingStatusResponse`:

```java
public record MyWaitingStatusResponse(
    int currentRank,
    int totalWaiting,
    int estimatedWaitMinutes,
    WaitingStatus status,
    LocalDateTime graceDeadline
) {

}
```

`WaitingService.getStatus`에서 값을 채운다 — 이미 설정을 읽고 있으므로 재사용한다:

```java
    int graceMinutes = storeSettingsRepository.findByStoreId(entry.getStoreId())
        .map(StoreSettings::getCallGraceMinutes)
        .orElse(DEFAULT_CALL_GRACE_MINUTES);
    LocalDateTime graceDeadline = entry.getCalledAt() == null
        ? null
        : entry.getCalledAt().plusMinutes(graceMinutes);

    return new MyWaitingStatusResponse(
        currentRank, totalWaiting, estimatedWaitMinutes, entry.getStatus(), graceDeadline);
```

- [ ] **Step 9: 컨트롤러 테스트 추가**

`OwnerWaitingControllerTest`에 기존 `call`/`enter` 테스트와 같은 형식으로 추가한다:

```java
  @Test
  void postponeWaiting_204를_반환한다() throws Exception {
    UUID waitingId = UUID.randomUUID();

    mockMvc.perform(post("/api/owner/waitings/{waitingId}/postpone", waitingId)
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(waitingManagementService).postpone(any(), eq(waitingId));
  }
```

> 기존 테스트가 인증 주체를 어떻게 주입하는지 확인해 동일하게 맞춘다. `call`/`enter` 테스트의 설정을 그대로 복사해 쓴다.

- [ ] **Step 10: 전체 테스트 + 커밋**

Run: `cd backend && ./gradlew test`
Expected: 전부 PASS. DTO 필드가 늘어 컴파일이 깨지는 테스트는 새 인자(`null`)를 채워 고친다.

```bash
git add backend/
git commit -m "feat: 호출 유예와 미루기 추가

호출 시 calledAt 을 기록하고 graceDeadline 을 응답에 실어, 만료 강조는
클라이언트 타이머가 하도록 한다. 감시 스케줄러가 필요 없고 재기동에도 안전하다.
미루기는 CALLED → WAITING 호출 취소로 번호와 순서를 유지하며,
waiting-postponed 이벤트로 손님 화면을 대기 화면으로 되돌린다."
```

---

## Task 6: 전화번호 마스킹과 가명처리 배치

**Files:**
- Create: `backend/.../shared/privacy/PhoneNumberMasker.java`
- Create: `backend/.../shared/privacy/PhoneHasher.java`
- Create: `backend/.../waiting/management/application/WaitingRetentionService.java`
- Modify: `backend/.../waiting/domain/WaitingEntry.java`
- Modify: `backend/.../waiting/domain/WaitingRepository.java`
- Modify: `backend/.../waiting/infrastructure/WaitingEntryJpaRepository.java`
- Modify: `backend/.../waiting/infrastructure/WaitingRepositoryImpl.java`
- Modify: `backend/.../store/domain/StoreRepository.java`
- Modify: `backend/.../store/infrastructure/StoreRepositoryImpl.java`
- Modify: `backend/.../waiting/management/application/WaitingManagementService.java`
- Modify: `backend/.../waiting/management/dto/TodayWaitingResponse.java`
- Modify: `backend/.../ApiApplication.java`
- Modify: `backend/src/main/resources/application.yml`, `application-local.yml`
- Modify: `.env.example`, `docker-compose.yml`
- Test: `backend/src/test/java/com/qrwait/api/shared/privacy/PhoneHasherTest.java` (신규)
- Test: `backend/src/test/java/com/qrwait/api/shared/privacy/PhoneNumberMaskerTest.java` (신규)
- Test: `backend/src/test/java/com/qrwait/api/waiting/domain/WaitingEntryTest.java`

**Interfaces:**
- Consumes: `WaitingEntry.getPhoneHash()` (Task 2), `StoreSettings.businessDateOf` (Task 1)
- Produces:
  - `PhoneNumberMasker.mask(String) -> String`
  - `PhoneHasher.hash(String phoneNumber) -> String`
  - `WaitingEntry.pseudonymize(String phoneHash) -> WaitingEntry`
  - `WaitingEntry.isPseudonymized() -> boolean`
  - `StoreRepository.findAll() -> List<Store>`
  - `WaitingRepository.findPseudonymizationTargets(UUID storeId, LocalDate currentBusinessDate) -> List<WaitingEntry>`

- [ ] **Step 1: 실패하는 마스킹·해시 테스트 작성**

`backend/src/test/java/com/qrwait/api/shared/privacy/PhoneNumberMaskerTest.java`

```java
package com.qrwait.api.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumberMaskerTest {

  @Test
  void 전체_번호는_뒤_4자리만_남긴다() {
    assertThat(PhoneNumberMasker.mask("010-1234-5678")).isEqualTo("****-5678");
  }

  @Test
  void 이미_가명처리된_뒤_4자리도_같은_형태로_렌더된다() {
    // 가명처리 전후로 화면 표시가 같아야 한다.
    assertThat(PhoneNumberMasker.mask("5678")).isEqualTo("****-5678");
  }

  @Test
  void 값이_없거나_짧으면_별표만() {
    assertThat(PhoneNumberMasker.mask(null)).isEqualTo("****");
    assertThat(PhoneNumberMasker.mask("12")).isEqualTo("****");
  }
}
```

`backend/src/test/java/com/qrwait/api/shared/privacy/PhoneHasherTest.java`

```java
package com.qrwait.api.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneHasherTest {

  private static final String SECRET = "test-phone-hash-secret-value";

  @Test
  void 같은_번호는_같은_해시를_만든다() {
    PhoneHasher hasher = new PhoneHasher(SECRET);

    assertThat(hasher.hash("010-1234-5678")).isEqualTo(hasher.hash("010-1234-5678"));
  }

  @Test
  void 다른_번호는_다른_해시를_만든다() {
    PhoneHasher hasher = new PhoneHasher(SECRET);

    assertThat(hasher.hash("010-1234-5678")).isNotEqualTo(hasher.hash("010-1234-5679"));
  }

  @Test
  void 키가_다르면_같은_번호도_다른_해시가_된다() {
    // 키를 모르면 대조표를 만들 수 없다는 것이 HMAC 을 쓰는 이유다.
    assertThat(new PhoneHasher(SECRET).hash("010-1234-5678"))
        .isNotEqualTo(new PhoneHasher("another-secret").hash("010-1234-5678"));
  }

  @Test
  void 해시는_64자_hex다() {
    assertThat(new PhoneHasher(SECRET).hash("010-1234-5678"))
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew test --tests "*.PhoneHasherTest" --tests "*.PhoneNumberMaskerTest"`
Expected: 컴파일 실패 — 두 클래스 없음.

- [ ] **Step 3: `PhoneNumberMasker` 구현**

```java
package com.qrwait.api.shared.privacy;

/**
 * 표시용 전화번호 마스킹. 저장값이 전체 번호든 가명처리된 뒤 4자리든 출력이 같으므로,
 * 해당 행이 가명처리되었는지가 화면에 드러나지 않는다.
 */
public final class PhoneNumberMasker {

  private static final int VISIBLE_DIGITS = 4;

  private PhoneNumberMasker() {
  }

  public static String mask(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.length() < VISIBLE_DIGITS) {
      return "****";
    }
    return "****-" + phoneNumber.substring(phoneNumber.length() - VISIBLE_DIGITS);
  }
}
```

- [ ] **Step 4: `PhoneHasher` 구현**

```java
package com.qrwait.api.shared.privacy;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 전화번호의 HMAC-SHA256 해시. 반복 노쇼 손님을 식별하기 위한 가명 식별자다.
 *
 * <p>솔트 없는 단순 해시를 쓰지 않는 이유는 전화번호의 경우의 수가 10^8 밖에 되지 않아
 * 전수 대입으로 즉시 역산되기 때문이다. 비밀키를 쓰면 키를 모르는 쪽에서는 대조표를 만들 수 없다.
 *
 * <p>키를 교체하면 기존 해시와 매칭이 끊긴다.
 */
@Component
public class PhoneHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  public PhoneHasher(@Value("${privacy.phone-hash-secret}") String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("privacy.phone-hash-secret 이 설정되지 않았습니다.");
    }
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  public String hash(String phoneNumber) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      byte[] digest = mac.doFinal(phoneNumber.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append("%02x".formatted(b));
      }
      return hex.toString();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("전화번호 해시 산출에 실패했습니다.", e);
    }
  }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*.PhoneHasherTest" --tests "*.PhoneNumberMaskerTest"`
Expected: 7개 PASS.

- [ ] **Step 6: 설정에 비밀키 추가**

`application.yml`:

```yaml
privacy:
  phone-hash-secret: ${PHONE_HASH_SECRET}
```

`application-local.yml` (JWT와 같은 방식으로 개발용 고정값):

```yaml
privacy:
  phone-hash-secret: local-dev-phone-hash-secret-not-for-prod
```

`backend/src/test/resources/application-test.yml`:

```yaml
privacy:
  phone-hash-secret: test-phone-hash-secret
```

`.env.example`에 추가:

```
# 전화번호 가명처리용 HMAC 비밀키 — openssl rand -base64 32 으로 생성
# 주의: 교체하면 기존 해시와 매칭이 끊겨 반복 노쇼 이력이 단절된다.
PHONE_HASH_SECRET=
```

`docker-compose.yml`의 backend `environment`에 추가:

```yaml
      PHONE_HASH_SECRET: ${PHONE_HASH_SECRET}
```

- [ ] **Step 7: 도메인 가명처리 테스트 추가**

`WaitingEntryTest`에 추가:

```java
  @Test
  void pseudonymize_뒤_4자리와_해시만_남긴다() {
    WaitingEntry entry = waiting(WaitingStatus.ENTERED, LocalDateTime.now());

    WaitingEntry result = entry.pseudonymize("abc123");

    assertThat(result.getPhoneNumber()).isEqualTo("0001");
    assertThat(result.getPhoneHash()).isEqualTo("abc123");
    assertThat(result.isPseudonymized()).isTrue();
  }

  @Test
  void pseudonymize_통계에_쓰이는_필드는_보존한다() {
    WaitingEntry entry = waiting(WaitingStatus.ENTERED, LocalDateTime.now());

    WaitingEntry result = entry.pseudonymize("abc123");

    assertThat(result.getWaitingNumber()).isEqualTo(entry.getWaitingNumber());
    assertThat(result.getPartySize()).isEqualTo(entry.getPartySize());
    assertThat(result.getStatus()).isEqualTo(entry.getStatus());
    assertThat(result.getBusinessDate()).isEqualTo(entry.getBusinessDate());
  }

  @Test
  void pseudonymize_두_번_해도_4자리가_더_잘리지_않는다() {
    WaitingEntry once = waiting(WaitingStatus.ENTERED, LocalDateTime.now()).pseudonymize("h1");

    WaitingEntry twice = once.pseudonymize("h2");

    assertThat(twice.getPhoneNumber()).isEqualTo("0001");
  }

  @Test
  void call_가명처리된_항목이면_예외() {
    WaitingEntry entry = waiting(WaitingStatus.WAITING, null).pseudonymize("abc123");

    assertThatThrownBy(entry::call)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("가명처리");
  }
```

- [ ] **Step 8: 도메인 메서드 구현**

`WaitingEntry`에 추가:

```java
  private static final int RETAINED_DIGITS = 4;

  /**
   * 가명처리 — 전체 번호를 뒤 4자리로 줄이고 해시를 심는다. 통계에 쓰이는 필드는 보존한다.
   * 해시 산출은 비밀키가 필요한 인프라 관심사이므로 계산된 값을 인자로 받는다.
   * 이미 가명처리된 항목에 다시 적용해도 뒤 4자리가 더 잘리지 않는다.
   */
  public WaitingEntry pseudonymize(String phoneHash) {
    String retained = phoneNumber.length() <= RETAINED_DIGITS
        ? phoneNumber
        : phoneNumber.substring(phoneNumber.length() - RETAINED_DIGITS);
    return with(status, calledAt, enteredAt, retained, phoneHash);
  }

  public boolean isPseudonymized() {
    return phoneHash != null;
  }
```

`call()`에 가드를 추가한다 (상태 검사 앞):

```java
  public WaitingEntry call() {
    if (isPseudonymized()) {
      throw new IllegalStateException("가명처리된 웨이팅은 호출할 수 없습니다. 전체 전화번호가 없습니다.");
    }
    if (status != WaitingStatus.WAITING) {
      ...
```

- [ ] **Step 9: 도메인 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*.WaitingEntryTest"`
Expected: 전부 PASS.

- [ ] **Step 10: 조회 포트 확장**

`StoreRepository`에 `List<Store> findAll();`을 추가하고 `StoreRepositoryImpl`에 구현한다:

```java
  @Override
  public List<Store> findAll() {
    return storeJpaRepository.findAll().stream()
        .map(StoreJpaEntity::toDomain)
        .toList();
  }
```

`WaitingEntryJpaRepository`에 대상 조회를 추가한다:

```java
  @Query("""
      SELECT w
        FROM WaitingEntryJpaEntity w
       WHERE w.storeId = :storeId
         AND w.businessDate < :currentBusinessDate
         AND w.phoneHash IS NULL
      """)
  List<WaitingEntryJpaEntity> findPseudonymizationTargets(@Param("storeId") UUID storeId,
      @Param("currentBusinessDate") LocalDate currentBusinessDate);
```

`WaitingRepository` 포트와 `WaitingRepositoryImpl`에 대응 메서드를 추가한다 (다른 조회와 같은 매핑 형태).

- [ ] **Step 11: 가명처리 배치 서비스 작성**

`backend/.../waiting/management/application/WaitingRetentionService.java`

```java
package com.qrwait.api.waiting.management.application;

import com.qrwait.api.shared.privacy.PhoneHasher;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영업일이 지난 웨이팅의 전화번호를 가명처리한다. 전체 번호를 뒤 4자리로 줄이고 HMAC 해시를 심는다.
 *
 * <p>매장마다 영업일 경계가 달라 몰아서 돌릴 공통 기준 시각이 없으므로 매시 정각에 순회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRetentionService {

  private static final LocalTime DEFAULT_BUSINESS_DAY_START = LocalTime.of(5, 0);

  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final WaitingRepository waitingRepository;
  private final PhoneHasher phoneHasher;

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void pseudonymizeExpired() {
    LocalDateTime now = LocalDateTime.now();
    int total = 0;
    for (Store store : storeRepository.findAll()) {
      total += pseudonymizeStore(store.getId(), currentBusinessDate(store.getId(), now));
    }
    if (total > 0) {
      log.info("전화번호 가명처리 완료 — {}건", total);
    }
  }

  private int pseudonymizeStore(java.util.UUID storeId, LocalDate currentBusinessDate) {
    List<WaitingEntry> targets =
        waitingRepository.findPseudonymizationTargets(storeId, currentBusinessDate);
    for (WaitingEntry entry : targets) {
      waitingRepository.save(entry.pseudonymize(phoneHasher.hash(entry.getPhoneNumber())));
    }
    return targets.size();
  }

  private LocalDate currentBusinessDate(java.util.UUID storeId, LocalDateTime now) {
    return storeSettingsRepository.findByStoreId(storeId)
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> now.toLocalTime().isBefore(DEFAULT_BUSINESS_DAY_START)
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate());
  }
}
```

`ApiApplication`에 `@EnableScheduling`을 추가한다:

```java
@EnableScheduling
@SpringBootApplication
public class ApiApplication {
```

`org.springframework.scheduling.annotation.EnableScheduling` import 추가.

- [ ] **Step 12: 배치 통합 테스트 작성**

`backend/src/test/java/com/qrwait/api/waiting/management/application/WaitingRetentionServiceTest.java`

```java
package com.qrwait.api.waiting.management.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.support.IntegrationTestSupport;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingRetentionServiceTest extends IntegrationTestSupport {

  @Autowired
  private WaitingRetentionService retentionService;

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private StoreSettingsRepository storeSettingsRepository;

  @Autowired
  private WaitingRepository waitingRepository;

  private UUID storeId;

  @BeforeEach
  void setUp() {
    Store store = storeRepository.save(Store.create(UUID.randomUUID(), "가명처리 테스트 식당", null));
    storeId = store.getId();
    storeSettingsRepository.save(StoreSettings.createDefault(storeId));
  }

  @Test
  void 지난_영업일_건만_가명처리하고_당일_건은_보존한다() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    WaitingEntry old = waitingRepository.save(
        WaitingEntry.create(storeId, "010-1234-5678", 2, 1, yesterday));
    WaitingEntry today = waitingRepository.save(
        WaitingEntry.create(storeId, "010-9999-8888", 2, 1, LocalDate.now()));

    retentionService.pseudonymizeExpired();

    WaitingEntry reloadedOld = waitingRepository.findById(old.getId()).orElseThrow();
    WaitingEntry reloadedToday = waitingRepository.findById(today.getId()).orElseThrow();

    assertThat(reloadedOld.getPhoneNumber()).isEqualTo("5678");
    assertThat(reloadedOld.getPhoneHash()).isNotNull();
    assertThat(reloadedToday.getPhoneNumber()).isEqualTo("010-9999-8888");
    assertThat(reloadedToday.getPhoneHash()).isNull();
  }

  @Test
  void 두_번_실행해도_결과가_같다() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    WaitingEntry old = waitingRepository.save(
        WaitingEntry.create(storeId, "010-1234-5678", 2, 1, yesterday));

    retentionService.pseudonymizeExpired();
    retentionService.pseudonymizeExpired();

    assertThat(waitingRepository.findById(old.getId()).orElseThrow().getPhoneNumber())
        .isEqualTo("5678");
  }
}
```

> 테스트가 `LocalDate.now()`를 쓰므로 자정~05:00 사이에 돌리면 영업일 경계 때문에 결과가 달라진다. 실패하면 `LocalDate.now().minusDays(2)`로 여유를 둔다.

- [ ] **Step 13: 마스킹을 응답 DTO에 적용**

`WaitingManagementService.toOwnerWaitingResponse`의 `entry.getPhoneNumber()`를 `PhoneNumberMasker.mask(entry.getPhoneNumber())`로 바꾼다.
`TodayWaitingResponse.from`의 `entry.getPhoneNumber()`도 동일하게 바꾼다.

> SMS 발송 실패 배너는 `SsePublisher.notifyOwnerSmsFailed`가 전체 번호를 따로 보내므로 영향받지 않는다.

- [ ] **Step 14: 전체 테스트 + 커밋**

Run: `cd backend && ./gradlew test`
Expected: 전부 PASS. 전화번호 전체를 기대하던 컨트롤러/서비스 테스트가 있으면 마스킹된 형태로 기대값을 고친다.

```bash
git add backend/ .env.example docker-compose.yml
git commit -m "feat: 영업일이 지난 전화번호를 가명처리

전체 번호를 뒤 4자리로 줄이고 HMAC-SHA256 해시를 남긴다. 뒤 4자리는 점주가
눈으로 대조하는 용도, 해시는 반복 노쇼 손님의 시스템 식별 용도다.
전화번호는 경우의 수가 10^8 뿐이라 솔트 없는 해시는 즉시 역산되므로
비밀키 기반 HMAC 을 쓴다.
대시보드 표시는 뒤 4자리 기준으로 통일해 가명처리 전후가 동일하게 보인다."
```

---

## Task 7: 프론트 — 미루기·유예 카운트다운·SSE

> 프론트엔드에는 테스트 러너가 없다(스펙 §11, 2026-08-03 스펙에서 범위 밖으로 둔 항목). 각 단계는 수동 검증으로 확인한다.

**Files:**
- Modify: `frontend/src/api/owner.ts`
- Modify: `frontend/src/api/waiting.ts`
- Modify: `frontend/src/hooks/useWaitingSse.ts`
- Modify: `frontend/src/pages/DashboardPage.tsx`
- Modify: `frontend/src/pages/WaitingCalledPage.tsx`
- Modify: `frontend/src/pages/WaitingStatusPage.tsx`

**Interfaces:**
- Consumes: `POST /api/owner/waitings/{id}/postpone`, `graceDeadline` 필드, `waiting-postponed` 이벤트 (Task 5)
- Produces: 점주가 미루기를 쓸 수 있고, 유예 만료가 화면에 드러난다

- [ ] **Step 1: API 클라이언트에 `postpone` 추가**

`frontend/src/api/owner.ts`에 기존 `callWaiting`/`enterWaiting`과 같은 형태로 추가한다:

```ts
export const postponeWaiting = (waitingId: string) =>
    ownerClient.post(`/owner/waitings/${waitingId}/postpone`)
```

`OwnerWaiting` 타입에 `graceDeadline: string | null`을 추가한다. `TodayWaiting` 타입에 `waitedMinutes: number | null`을 추가한다.

- [ ] **Step 2: 손님 상태 타입에 `graceDeadline` 추가**

`frontend/src/api/waiting.ts`의 `MyWaitingStatus` 타입에 `graceDeadline: string | null`을 추가한다.

- [ ] **Step 3: SSE 훅에 `waiting-postponed` 핸들러 추가**

`useWaitingSse.ts`의 `UseWaitingSseOptions`에 `onPostponed?: () => void`를 추가하고, ref 패턴을 기존과 동일하게 따른다:

```ts
  const onPostponedRef = useRef(options.onPostponed)
  onPostponedRef.current = options.onPostponed
```

`connect()` 안, `waiting-called` 핸들러 아래에 추가한다:

```ts
      es.addEventListener('waiting-postponed', (e) => {
        if (unmounted) return
        try {
          const data = JSON.parse((e as MessageEvent).data)
          if (data.waitingId === waitingId) onPostponedRef.current?.()
        } catch {
          // payload 파싱 실패 시 무시
        }
      })
```

- [ ] **Step 4: 호출 화면이 미루기에 반응하도록 수정**

`WaitingCalledPage.tsx`에서 `useWaitingSse`에 `onPostponed`를 넘겨 대기 화면으로 되돌린다:

```ts
  onPostponed: () => navigate(`/waiting/${waitingId}/status`, {replace: true}),
```

- [ ] **Step 5: 호출 화면에 유예 카운트다운 추가**

`WaitingCalledPage.tsx`에 남은 시간을 초 단위로 갱신하는 상태를 둔다. `graceDeadline`은 절대 시각이라 화면에 다시 들어와도 정확하다.

```tsx
const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null)

useEffect(() => {
  if (!graceDeadline) return
  const tick = () => {
    const diff = Math.floor((new Date(graceDeadline).getTime() - Date.now()) / 1000)
    setRemainingSeconds(diff > 0 ? diff : 0)
  }
  tick()
  const timer = setInterval(tick, 1000)
  return () => clearInterval(timer)
}, [graceDeadline])
```

표시부:

```tsx
{remainingSeconds !== null && (
    <p>
      {remainingSeconds > 0
          ? `${Math.floor(remainingSeconds / 60)}분 ${remainingSeconds % 60}초 안에 입장해 주세요`
          : '입장 시간이 지났습니다. 매장에 문의해 주세요.'}
    </p>
)}
```

- [ ] **Step 6: 대시보드에 [미루기] 버튼 활성화**

`DashboardPage.tsx`에서 `disabled`로 두었던 미루기 버튼을 살린다. 기존 호출/입장/노쇼 버튼의 핸들러 패턴을 그대로 따른다:

```tsx
const handlePostpone = async (waitingId: string) => {
  await postponeWaiting(waitingId)
  await Promise.all([fetchWaitingList(), fetchSummary()])
}
```

버튼은 `status === 'CALLED'`일 때만 활성화한다.

- [ ] **Step 7: 대시보드에 유예 만료 강조 추가**

목록 렌더에서 `graceDeadline`이 지난 항목을 시각적으로 구분한다. 1초마다 갱신되는 `now` 상태를 하나 두면 재렌더가 걸린다:

```tsx
const [now, setNow] = useState(() => Date.now())
useEffect(() => {
  const timer = setInterval(() => setNow(Date.now()), 1000)
  return () => clearInterval(timer)
}, [])

const isGraceExpired = (w: OwnerWaiting) =>
    w.graceDeadline !== null && new Date(w.graceDeadline).getTime() < now
```

만료된 행에 배경색과 `⏰ 유예 시간 초과` 라벨을 붙인다. **상태는 바꾸지 않는다** — 판단은 점주 몫이다.

- [ ] **Step 8: 수동 검증**

```bash
docker compose -f docker-compose.dev.yml up -d
cd backend && ./gradlew bootRun    # 별도 터미널
cd frontend && npm run dev
```

확인 항목:
1. 손님 등록 → 점주 대시보드에서 [호출] → 손님 화면이 호출 화면으로 바뀌고 카운트다운이 돈다.
2. 유예 시간이 지나면 대시보드에서 해당 행이 강조되지만 **상태는 CALLED 그대로**다.
3. [미루기] → 손님 화면이 대기 화면으로 돌아가고, 대기번호와 순서가 그대로다.
4. 대시보드 목록의 전화번호가 `****-5678`로 보인다.

- [ ] **Step 9: 빌드 확인 후 커밋**

Run: `cd frontend && npm run build`
Expected: 타입 오류 없이 빌드 성공.

```bash
git add frontend/
git commit -m "feat: 대시보드 미루기 버튼과 호출 유예 카운트다운 추가

graceDeadline 이 절대 시각이라 화면에 다시 들어와도 남은 시간이 정확하다.
유예가 지난 행은 강조만 하고 상태는 바꾸지 않는다. 판단은 점주 몫이다.
waiting-postponed 를 받으면 손님 화면이 대기 화면으로 돌아간다."
```

---

## Task 8: 프론트 — 설정 화면·이력·동의 문구

**Files:**
- Modify: `frontend/src/pages/StoreSettingsPage.tsx`
- Modify: `frontend/src/pages/HistoryPage.tsx`
- Modify: `frontend/src/pages/LandingPage.tsx`
- Modify: `frontend/src/api/owner.ts`

**Interfaces:**
- Consumes: `businessDayStart`·`callGraceMinutes` (Task 1), `waitedMinutes` (Task 4)
- Produces: 점주가 영업일과 유예를 설정할 수 있고, 손님이 실제 보관 방식을 고지받는다

- [ ] **Step 1: 설정 타입 확장**

`frontend/src/api/owner.ts`의 `StoreSettings` 타입과 업데이트 요청 타입에 추가한다:

```ts
  businessDayStart: string   // "05:00:00"
  callGraceMinutes: number
```

- [ ] **Step 2: 설정 화면에 입력 추가**

`StoreSettingsPage.tsx`에 기존 입력들과 같은 형태로 두 항목을 추가한다.

```tsx
<label>
  영업일 시작 시각
  <input
      type="time"
      value={businessDayStart.slice(0, 5)}
      onChange={(e) => setBusinessDayStart(`${e.target.value}:00`)}
  />
  <small>이 시각을 기준으로 대기번호가 1번부터 다시 시작합니다. 새벽 영업분은 전날로 집계됩니다.</small>
</label>

<label>
  호출 유예 시간 (분)
  <input
      type="number"
      min={0}
      max={60}
      value={callGraceMinutes}
      onChange={(e) => setCallGraceMinutes(Number(e.target.value))}
  />
  <small>호출 후 이 시간이 지나면 대기 목록에서 강조 표시됩니다. 자동으로 취소되지는 않습니다.</small>
</label>
```

저장 요청 payload에 두 값을 포함시킨다.

- [ ] **Step 3: 이력 화면에 실측 대기시간 열 추가**

`HistoryPage.tsx`의 테이블에 열을 하나 추가한다:

```tsx
<td>{w.waitedMinutes !== null ? `${w.waitedMinutes}분` : '-'}</td>
```

헤더에 `실제 대기시간`을 추가한다. 입장하지 않은 건은 `-`로 표시된다.

- [ ] **Step 4: 개인정보 동의 문구 교체**

`LandingPage.tsx`의 동의 라벨 문구를 실제 보관 방식과 일치시킨다. 기존:

```tsx
<span style={styles.consentText}>
  전화번호는 웨이팅 호출 알림 목적으로만 사용됩니다.
</span>
```

이렇게 바꾼다:

```tsx
<span style={styles.consentText}>
  전화번호는 웨이팅 호출 알림에 사용합니다. 영업일이 끝나면 전체 번호는 폐기하고,
  뒤 4자리와 식별할 수 없는 형태로 변환한 값만 이력 관리 및 반복 노쇼 방지를 위해 보관합니다.
</span>
```

> 이 문구가 없으면 가명처리된 값을 보관할 근거가 없다. 반드시 함께 배포해야 한다.

- [ ] **Step 5: 수동 검증**

1. `/owner/settings`에서 영업일 시작 시각과 유예 시간을 저장하고 새로고침 → 값이 유지된다.
2. 유예 시간을 1분으로 바꾸고 호출 → 1분 뒤 대시보드에서 강조된다.
3. `/owner/history`에서 입장 처리된 건에 실제 대기시간이 표시된다.
4. `/wait?storeId=...`에서 동의 문구가 새 내용으로 보인다.

- [ ] **Step 6: 빌드 확인 후 커밋**

Run: `cd frontend && npm run build`
Expected: 타입 오류 없이 빌드 성공.

```bash
git add frontend/
git commit -m "feat: 영업일·유예 설정 화면과 실측 대기시간 표시 추가

개인정보 동의 문구를 실제 보관 방식(뒤 4자리 + 변환값)에 맞게 교체한다.
이 문구 없이는 가명처리된 값을 보관할 근거가 없다."
```

---

## Task 9: 최종 검수

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: 전부 PASS.

- [ ] **Step 2: 로컬 스택 기동**

```bash
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d
cd backend && ./gradlew bootRun
```

Expected: 정상 부팅. `flyway_schema_history`에 V1·V2·V3와 `R__dev_seed`가 기록된다.

- [ ] **Step 3: 스펙 §14 완료 기준 11개 확인**

`docs/superpowers/specs/2026-09-01-waiting-operations-design.md`의 §14를 열어 항목별로 확인한다. 특히 자동 테스트로 덮이지 않는 것들:

- (4) 영업일 시작 시각을 현재 시각 직전으로 바꾸면 다음 등록의 대기번호가 1번이 되고, 일일 통계도 같은 시점에 초기화된다.
- (7) 테이블 40개·회전 30분으로 설정해도 예상 대기시간이 0분이 아니다.
- (9) 가명처리 전후로 대시보드의 번호 표시가 동일하다.
- (10) 지난 영업일 미방문 손님이 오늘 목록에 없다.
- (11) 동의 문구가 실제 보관 방식과 일치한다.

- [ ] **Step 4: 결과 보고**

확인되지 않은 항목이 있으면 그대로 보고한다. 통과한 것만 통과했다고 말한다.

---

## Self-Review

**1. Spec coverage**

| 스펙 섹션 | 담당 태스크 |
|---|---|
| §4-1 StoreSettings (영업일·유예·공식 교정) | Task 1 |
| §4-2 WaitingEntry 필드 | Task 2 (기본 4개), Task 6 (`pseudonymize`/`isPseudonymized`) |
| §4-3 상태 전이 (`postpone`, `call()` 가드) | Task 5, Task 6 |
| §5-1 Flyway 정상화 | Task 0 |
| §5-2 스키마 | Task 1 (V2), Task 2 (V3) |
| §6 채번 + 재시도 | Task 3 |
| §7 API·DTO, 대기 목록 영업일 한정 | Task 4 (목록·집계·순번), Task 5, Task 6 (마스킹) |
| §8 SSE `waiting-postponed` | Task 5 |
| §9 가명처리 (해시·배치·동의 문구) | Task 6, Task 8 (문구) |
| §10 실측 수집 | Task 2 (`enteredAt`), Task 4 (`waitedMinutes`) |
| §11 프론트 | Task 7, Task 8 |
| §12 테스트 전략 | 각 태스크에 분산, Task 9에서 종합 |
| §14 완료 기준 | Task 9 |

누락 없음.

**2. Placeholder scan** — `<STEP_2_HASH>`는 Step 2에서 생성한 값을 채우도록 절차가 명시된 의도적 자리표시자다. 그 외 미해결 자리표시자 없음.

**3. Type consistency** — `businessDateOf(LocalDateTime) -> LocalDate`, `calculateEstimatedWait(int) -> int`, `pseudonymize(String) -> WaitingEntry`, `isGraceExpired(LocalDateTime, int) -> boolean`, `findNextWaitingNumber(UUID, LocalDate) -> int`, `PhoneNumberMasker.mask(String) -> String`, `PhoneHasher.hash(String) -> String` — 태스크 전반에서 이름과 시그니처가 일치한다. `WaitingEntry.with(...)`는 Task 2에서 도입해 Task 5·6이 그대로 사용한다.
