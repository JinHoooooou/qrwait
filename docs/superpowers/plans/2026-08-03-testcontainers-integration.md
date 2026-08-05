# Testcontainers 통합 테스트 자립화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드 통합 테스트가 수동 docker 인프라 없이 Testcontainers로 PostgreSQL·Redis를 스스로 띄워 `./gradlew test` 한 번에 통과하도록 만든다.

**Architecture:** `IntegrationTestSupport` 추상 클래스가 PostgreSQL·Redis 컨테이너를 static 싱글톤으로 1회 기동하고 `@DynamicPropertySource`로 접속 정보를 주입한다. 인프라가 필요한 테스트 6개 클래스가 이 베이스를 `extends`한다. `application-test.yml`은 하드코딩 접속 정보를 제거하고 Flyway 실제 실행 + `ddl-auto: validate`로 전환한다.

**Tech Stack:** Java 21, Spring Boot 3.5, Testcontainers (PostgreSQL 15, Redis 7-alpine), Flyway, JUnit 5, Gradle.

## Global Constraints

- Testcontainers 버전은 명시하지 않는다 — Spring Boot 3.5 BOM(`io.spring.dependency-management`)이 관리한다.
- 컨테이너 이미지는 운영과 동일하게 `postgres:15`, `redis:7-alpine`.
- 인프라 의존 테스트는 정확히 6개 클래스: `ApiApplicationTests`, `OwnerRepositoryImplTest`, `StoreRepositoryImplTest`, `StoreSettingsRepositoryImplTest`, `WaitingRepositoryImplTest`, `RefreshTokenRepositoryTest`. 그 외 테스트는 손대지 않는다.
- 각 테스트 클래스의 기존 애노테이션(`@DataJpaTest`/`@DataRedisTest`/`@SpringBootTest`/`@Import(...)`/`@ActiveProfiles("test")`/`@AutoConfigureTestDatabase(replace = NONE)`)은 **그대로 유지**하고 `extends IntegrationTestSupport`만 추가한다.
- `docker-compose.dev.yml`은 수정/삭제하지 않는다 (bootRun 로컬 앱 실행용).
- `@DynamicPropertySource` 메서드는 `static`이어야 한다.
- 테스트 실행에는 도커 데몬이 켜져 있어야 한다 (전제).
- 검증 명령은 `backend/` 디렉터리에서 `./gradlew` 로 실행한다.

---

### Task 1: Testcontainers 의존성 추가 + 베이스 클래스 작성

**Files:**
- Modify: `backend/build.gradle:42-44` (test 의존성 블록)
- Create: `backend/src/test/java/com/qrwait/api/support/IntegrationTestSupport.java`

**Interfaces:**
- Consumes: (없음)
- Produces: `com.qrwait.api.support.IntegrationTestSupport` — 인프라 테스트가 상속할 추상 클래스. static 필드 `POSTGRES`(`PostgreSQLContainer<?>`), `REDIS`(`GenericContainer<?>`)를 기동하고 `spring.datasource.url/username/password`, `spring.data.redis.host/port` 프로퍼티를 `@DynamicPropertySource`로 주입한다.

- [ ] **Step 1: build.gradle에 Testcontainers 의존성 추가**

`backend/build.gradle`의 test 의존성 3줄(기존 `testImplementation ... starter-test`, `mockwebserver`, `testRuntimeOnly ... junit-platform-launcher` 근처)에 아래를 추가한다:

```groovy
	testImplementation 'org.springframework.boot:spring-boot-testcontainers'
	testImplementation 'org.testcontainers:junit-jupiter'
	testImplementation 'org.testcontainers:postgresql'
```

- [ ] **Step 2: 의존성 해석 확인**

Run: `cd backend && ./gradlew dependencies --configuration testRuntimeClasspath -q | grep -i testcontainers`
Expected: `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`, `org.testcontainers:jdbc` 등이 버전과 함께 출력됨 (BOM이 버전 채움).

- [ ] **Step 3: IntegrationTestSupport 작성**

`backend/src/test/java/com/qrwait/api/support/IntegrationTestSupport.java`:

```java
package com.qrwait.api.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

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

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileTestJava -q`
Expected: BUILD SUCCESSFUL (컴파일 에러 없음).

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle backend/src/test/java/com/qrwait/api/support/IntegrationTestSupport.java
git commit -m "test: Testcontainers 의존성 및 IntegrationTestSupport 베이스 추가"
```

---

### Task 2: application-test.yml을 Testcontainers + Flyway 기반으로 전환

**Files:**
- Modify: `backend/src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: `IntegrationTestSupport`가 주입하는 `spring.datasource.*`, `spring.data.redis.*` 프로퍼티.
- Produces: Flyway가 활성화되고(`spring.flyway.enabled: true`) Hibernate가 `validate` 모드로 동작하는 테스트 프로파일. 하드코딩 접속 정보 없음.

이 태스크는 Task 3에서 6개 클래스가 베이스를 상속한 뒤에야 완전히 검증되므로, 여기서는 설정 변경 + 컴파일/설정 로드까지만 확인한다.

- [ ] **Step 1: application-test.yml 교체**

`backend/src/test/resources/application-test.yml` 전체를 아래로 교체한다. `spring.datasource`의 host/username/password와 `spring.data.redis`의 host/port를 **제거**(컨테이너가 주입), `flyway.enabled: true`, `ddl-auto: validate`로 변경:

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate      # Flyway가 스키마 소유, Hibernate는 엔티티↔스키마 일치 검증만
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true             # 운영과 동일하게 V1__init.sql 실제 실행

cors:
  allowed-origins: http://localhost:5173

app:
  base-url: http://localhost:5173

jwt:
  secret: dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=
  access-expiry: 3600
  refresh-expiry: 604800
```

- [ ] **Step 2: 하드코딩 접속 정보 제거 확인**

Run: `cd backend && grep -nE "5434|6379|CHANGEME|localhost:5432" src/test/resources/application-test.yml || echo "CLEAN"`
Expected: `CLEAN` (하드코딩된 DB/Redis 접속 정보가 남아있지 않음).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/resources/application-test.yml
git commit -m "test: application-test.yml을 Testcontainers 주입 + Flyway 실행으로 전환"
```

---

### Task 3: 인프라 의존 테스트 6개를 IntegrationTestSupport 상속으로 전환

**Files:**
- Modify: `backend/src/test/java/com/qrwait/api/ApiApplicationTests.java`
- Modify: `backend/src/test/java/com/qrwait/api/owner/infrastructure/OwnerRepositoryImplTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/store/infrastructure/StoreRepositoryImplTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/store/infrastructure/StoreSettingsRepositoryImplTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/waiting/infrastructure/WaitingRepositoryImplTest.java`
- Modify: `backend/src/test/java/com/qrwait/api/shared/redis/RefreshTokenRepositoryTest.java`

**Interfaces:**
- Consumes: `com.qrwait.api.support.IntegrationTestSupport` (Task 1).
- Produces: (없음 — 최종 검증 대상)

각 클래스에 `import com.qrwait.api.support.IntegrationTestSupport;`를 추가하고 `class X` 선언을 `class X extends IntegrationTestSupport`로 바꾼다. 기존 애노테이션·필드·테스트 메서드는 변경하지 않는다.

- [ ] **Step 1: ApiApplicationTests 수정**

import에 `import com.qrwait.api.support.IntegrationTestSupport;` 추가, 선언을 다음으로 변경:

```java
class ApiApplicationTests extends IntegrationTestSupport {
```

- [ ] **Step 2: OwnerRepositoryImplTest 수정**

import 추가 후:

```java
class OwnerRepositoryImplTest extends IntegrationTestSupport {
```

- [ ] **Step 3: StoreRepositoryImplTest 수정**

import 추가 후:

```java
class StoreRepositoryImplTest extends IntegrationTestSupport {
```

- [ ] **Step 4: StoreSettingsRepositoryImplTest 수정**

import 추가 후:

```java
class StoreSettingsRepositoryImplTest extends IntegrationTestSupport {
```

- [ ] **Step 5: WaitingRepositoryImplTest 수정**

import 추가 후:

```java
class WaitingRepositoryImplTest extends IntegrationTestSupport {
```

- [ ] **Step 6: RefreshTokenRepositoryTest 수정**

import 추가 후:

```java
class RefreshTokenRepositoryTest extends IntegrationTestSupport {
```

- [ ] **Step 7: 수동 인프라를 내린 상태에서 전체 테스트 실행 (핵심 검증)**

먼저 수동 인프라를 내려 자립성을 검증한다 (도커 데몬은 켜둠):

Run: `cd "C:/Users/jinho/IdeaProjects/qrwait" && docker compose -f docker-compose.dev.yml down`
그다음: `cd backend && ./gradlew test --console=plain`

Expected: BUILD SUCCESSFUL, `127 tests completed, 0 failed`. 수동 인프라가 없어도 Testcontainers가 컨테이너를 띄워 전부 통과.

> 만약 `ddl-auto: validate`로 인해 `SchemaManagementException`(V1 스키마↔엔티티 불일치)이 발생하면, 이는 스펙 §6에 명시된 예상 리스크다. **임의로 수정하지 말고** 어떤 컬럼/테이블이 불일치인지 오류 메시지를 기록해 사용자에게 보고한다. (완화책으로 `ddl-auto: none` 전환은 사용자 승인 후에만.)

- [ ] **Step 8: Commit**

```bash
git add backend/src/test/java
git commit -m "test: 인프라 의존 테스트 6개를 IntegrationTestSupport 상속으로 전환"
```

---

## Self-Review

**1. Spec coverage:**
- §4-1 의존성 → Task 1 Step 1 ✓
- §4-2 IntegrationTestSupport → Task 1 Step 3 ✓
- §4-3 application-test.yml (하드코딩 제거 / flyway.enabled / ddl-auto validate) → Task 2 ✓
- §4-4 테스트 6개 extends → Task 3 ✓
- §5 시드 충돌 없음 → 브레인스토밍에서 검증 완료, 코드 변경 불필요 ✓
- §6 validate 리스크 → Task 3 Step 7 주의문에 반영 ✓
- §7 검증(수동 인프라 down 후 통과) → Task 3 Step 7 ✓
- 비목표(CI/프론트/compose 유지) → Global Constraints에 반영 ✓

**2. Placeholder scan:** "TBD"/"적절히"/"나중에" 등 없음. 모든 코드 단계에 실제 코드 블록 포함. ✓

**3. Type consistency:** `IntegrationTestSupport`, static 필드 `POSTGRES`/`REDIS`, 프로퍼티 키(`spring.datasource.url/username/password`, `spring.data.redis.host/port`)가 Task 1 정의와 Task 2/3 소비에서 일치. ✓
