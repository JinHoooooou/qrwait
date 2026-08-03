# Testcontainers 통합 테스트 자립화 — 설계

| 항목 | 내용 |
|---|---|
| 문서 유형 | Design Spec |
| 작성일 | 2026-08-03 |
| 상태 | Approved |
| 범위 | 백엔드 통합 테스트를 로컬 인프라(수동 docker) 의존에서 Testcontainers 자립 실행으로 전환 |

---

## 1. 배경 / 문제

- `./gradlew test` 는 인프라가 안 떠 있으면 6개 클래스(16개 테스트)가 실패한다.
  실패 원인은 코드 버그가 아니라 **테스트가 `application-test.yml`의 하드코딩된 `localhost:5434`(PostgreSQL)·`localhost:6379`(Redis)에 의존**하기 때문이다.
- 즉 개발자가 `docker compose -f docker-compose.dev.yml up -d` 를 **수동으로 기억해 띄워야만** 테스트가 통과한다. 다른 개발자 PC나 CI 러너에서는 그냥 실패한다.
- 목표: **도커만 떠 있으면(데몬), 사람이 특정 compose를 수동 기동하지 않아도 `./gradlew test` 한 방에 통과**하도록 만든다. 테스트가 필요한 PostgreSQL·Redis를 스스로 일회용 컨테이너로 띄운다.

### 비목표 (Out of Scope)

- CI 파이프라인 구성(GitHub Actions)은 이번 범위 아님 (사용자 결정).
- 프론트엔드 테스트 하네스도 이번 범위 아님.
- `docker-compose.dev.yml` 은 **유지**한다. 이건 `bootRun`(로컬 앱 실행)용이며 테스트와 무관하다.

---

## 2. 왜 H2가 아니라 진짜 PostgreSQL(Testcontainers)인가

이 프로젝트는 PostgreSQL에 묶여 있어 H2로 대체하면 충실도가 깨진다.

- 운영 스키마를 Flyway `V1__init.sql`(Postgres 문법: `gen_random_uuid()`, `UUID`, `TIME` 등)로 관리
- `PostgreSQLDialect` 명시, `UUID` 타입 사용
- `@Query` JPQL + projection 인터페이스 기반 집계
- **Redis는 인메모리 대체제가 부적절** — RefreshToken TTL 만료 등 실제 Redis 동작 필요

→ "운영과 동일한 진짜 PostgreSQL 15 + Redis 7을 일회용 컨테이너로" 쓰는 Testcontainers가 정답.

---

## 3. 선택한 접근 — 싱글톤 static 컨테이너 + 추상 베이스 클래스

대안으로 `@ServiceConnection` 빈 기반 `@TestConfiguration`(B안)도 검토했으나, 슬라이스 테스트(`@DataJpaTest`/`@DataRedisTest`)마다 컨텍스트가 달라 컨테이너가 여러 번 기동될 수 있고 Redis는 `@ServiceConnection(name=...)` 매핑이 필요해 미묘함이 늘어난다. 이 프로젝트 규모에서는 **A안(싱글톤 static + `@DynamicPropertySource`)** 이 단순하고 예측 가능하다.

- 컨테이너는 JVM 테스트런 전체에서 **딱 한 번** 기동(static 싱글톤). Ryuk가 JVM 종료 시 정리.
- 인프라가 필요한 테스트 6개 클래스가 베이스를 `extends` 만 하면 된다. 기존 슬라이스 애노테이션은 그대로 둔다.

---

## 4. 구성 요소

### 4-1. 의존성 (`backend/build.gradle`, test 스코프)

```groovy
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
```

- 버전은 Spring Boot 3.5 BOM(`io.spring.dependency-management`)이 관리하므로 명시하지 않는다.
- Redis는 전용 모듈 없이 `org.testcontainers`의 `GenericContainer`(junit-jupiter에 포함)로 처리한다.

### 4-2. 신규 `IntegrationTestSupport` (test 소스)

- 위치: `backend/src/test/java/com/qrwait/api/support/IntegrationTestSupport.java`
- 책임: PostgreSQL·Redis 컨테이너를 static 으로 기동하고, 접속 정보를 `@DynamicPropertySource`로 스프링에 주입.

```java
package com.qrwait.api.support;

public abstract class IntegrationTestSupport {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15");

  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }
}
```

- static 컨테이너는 명시적으로 stop 하지 않는다(싱글톤 재사용). 정리는 Ryuk에 위임.
- 슬라이스 테스트가 둘 중 하나만 써도 두 컨테이너가 다 뜨는 건 허용한다(1회성 비용, 단순함 우선).

### 4-3. `application-test.yml` 변경

- **제거**: `spring.datasource.url/username/password`(localhost:5434, CHANGEME), `spring.data.redis.host/port`(localhost:6379) — 이제 `@DynamicPropertySource`가 주입.
- **변경**: `spring.flyway.enabled: false → true` — 운영과 동일하게 `V1__init.sql` 실제 실행.
- **변경**: `spring.jpa.hibernate.ddl-auto: create-drop → validate` — Flyway가 스키마를 소유하고, Hibernate는 엔티티↔스키마 일치만 검증(추가 충실도).
- **유지**: `jwt.*`, `cors.allowed-origins`, `app.base-url`, dialect(자동 감지되나 유지 무방).

### 4-4. 테스트 6개 클래스 — `extends IntegrationTestSupport` 추가

인프라 의존 테스트(정확히 이 6개):

| 클래스 | 슬라이스 | 필요 인프라 |
|---|---|---|
| `ApiApplicationTests` | `@SpringBootTest` | PostgreSQL + Redis |
| `owner.infrastructure.OwnerRepositoryImplTest` | `@DataJpaTest` | PostgreSQL |
| `store.infrastructure.StoreRepositoryImplTest` | `@DataJpaTest` | PostgreSQL |
| `store.infrastructure.StoreSettingsRepositoryImplTest` | `@DataJpaTest` | PostgreSQL |
| `waiting.infrastructure.WaitingRepositoryImplTest` | `@DataJpaTest` | PostgreSQL |
| `shared.redis.RefreshTokenRepositoryTest` | `@DataRedisTest` | Redis |

- 각 클래스에 `extends IntegrationTestSupport` 만 추가. 기존 `@DataJpaTest`/`@DataRedisTest`/`@Import(FlywayAutoConfiguration.class, ...)`/`@ActiveProfiles("test")`/`@AutoConfigureTestDatabase(replace = NONE)` 는 그대로 유지.
- 나머지 테스트(순수 단위, `@WebMvcTest`, MockWebServer, Mockito)는 인프라가 없으므로 손대지 않는다.

---

## 5. 데이터 정합성 — 시드 충돌 없음(검증 완료)

`V1__init.sql`은 owner 1명(`00000000-...-0000`, email `dev@qrwait.com`)과 store 3개(고정 UUID)를 시드하며, `waiting_entries`·`store_settings`는 시드하지 않는다.

- `@DataJpaTest`는 트랜잭션 롤백이 기본이라 각 테스트가 넣은 데이터는 사라지고, Flyway 시드는 컨텍스트 기동 시 1회 적재되어 유지된다.
- 기존 테스트들은 **자기가 저장한 특정 ID/랜덤 UUID로만 조회**하고, 전역 `findAll`/전역 카운트 단언이 없다. 이메일도 `test@qrwait.com`/`notfound@qrwait.com`을 써 시드의 `dev@qrwait.com`과 유니크 충돌이 없다.
- 따라서 Flyway 시드가 있어도 기존 단언은 깨지지 않는다.

---

## 6. 리스크

- **`ddl-auto: validate` 가 스키마 불일치를 잡을 수 있음.** `V1__init.sql`과 JPA 엔티티가 미세하게 어긋난 지점(컬럼 nullability/타입 등)이 있으면 컨텍스트 로드가 실패한다. 이는 이 작업이 노리는 **운영 충실도**의 결과이지만, 스키마/엔티티 수정이 딸려올 수 있다. 발생 시 사용자에게 즉시 보고한다. (완화책: `validate` → `none`으로 낮추면 검증은 생략되고 Flyway 스키마로 그대로 실행됨.)
- **도커 데몬 필요.** 테스트 머신에 도커가 떠 있어야 한다(방금 확인됨). 이 전제는 유지된다.

---

## 7. 검증 (완료 기준)

1. `docker compose -f docker-compose.dev.yml down` 으로 **수동 인프라를 내린 상태**에서(단, 도커 데몬은 켜둠) `./gradlew test` 실행.
2. **127개 전부 통과, 실패 0** 확인 — 즉 수동 인프라 없이도 자립 통과.
3. `application-test.yml`에 `localhost:5434`/`6379`/`CHANGEME` 하드코딩이 남아있지 않음 확인.
