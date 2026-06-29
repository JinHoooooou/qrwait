# Backend Architecture Guide (AI 작업 규칙)

> 이 파일은 **백엔드(`backend/`) 작업 전에 읽고 그대로 적용**하는 아키텍처 규칙이다.
> 코드를 추가·변경하기 전에 이 문서의 계층 규칙과 위치 규칙을 먼저 확인한다.
> 스택: Java 21 · Spring Boot 3.5 · JPA · Redis · PostgreSQL · Flyway

---

## 0. 핵심 철학 — Domain-first

요구사항이 추가·변경되면 **항상 도메인부터** 생각한다.
**서비스는 적게, 얇게. 도메인은 풍부하게.**

**로직을 둘 곳 — 우선순위대로 시도한다:**

1. **도메인 객체**에 메서드로 넣는다. (1순위, 대부분 여기서 끝나야 함)
2. 객체에 안 들어가면 → **새로운 도메인을 도출**할 수 없는지 먼저 고민한다.
3. 그래도 안 되는 *연산* 로직만 → **도메인 서비스**에 둔다.
4. 어플리케이션 서비스는 **오케스트레이션만** 한다 (비즈니스 규칙 금지).

> **도메인 서비스는 "새 도메인 발굴에 실패했을 때의 마지막 수단"이다.**
> `PriceManager` 같은 서비스를 만들기 전에, 그 책임을 가진 새 도메인이 없는지 반드시 자문한다.

> **단, 풍부함을 억지로 만들지 않는다 (YAGNI).**
> 도메인이 본질적으로 단순하면 — 단순 상태 전이, 인가(소유권) 검사, 단순 조회·집계 등 — **그대로 둔다.**
> 새 도메인·도메인 서비스는 **실제 복잡도가 있을 때만** 도출한다.
> 빈 껍데기 도메인이나 `OwnershipPolicy` 같은 가짜 추상을 만드는 것 자체가 오버엔지니어링이다.
> "도메인 우선"은 **복잡도를 도메인에 담으라**는 뜻이지, **없는 복잡도를 지어내라**는 뜻이 아니다.

---

## 1. 의존 방향

```
Presentation (Controller)
    │ 호출
    ▼
Application (Application Service)
    │              ╲ 호출
    │ 저장·외부연동    ▼
    │ (port 경유)   Domain (Entity / Domain Service / Port)
    │                 │ 저장 (port 경유)
    ▼                 ▼
Infrastructure (RepositoryImpl, JpaRepository, 외부 연동)
```

- **흐름:** Application도 Domain(도메인 서비스)도, 저장소·외부연동이 필요하면 둘 다 **Infrastructure로 흐른다.** 단 직접 부르지 않고 domain이 소유한 **port 인터페이스**를 통한다.
- **컴파일 의존 방향은 반대다:** `presentation → application → domain ← infrastructure`. Infrastructure가 domain의 port를 **구현**한다 (DIP). 그래서 흐름은 →Infra이지만 import는
  Infra→domain.
- **domain은 어떤 계층도 import 하지 않는다.** (Spring/JPA 어노테이션도 금지) — port 인터페이스만 소유한다.

---

## 2. 패키지 구조 & 계층 책임

패키지는 **애그리거트(도메인)별**로 나뉘고, 각 애그리거트가 4계층을 가진다.

```
com.qrwait.api
├── {aggregate}/                 (owner, store, waiting …)
│   ├── domain/                  엔티티 · VO · 도메인 서비스 · port(Repository 인터페이스) · event · 도메인 예외
│   ├── application/             어플리케이션 서비스 · dto/
│   ├── infrastructure/          JpaEntity · JpaRepository · RepositoryImpl · 외부 연동
│   └── presentation/            Controller
└── shared/                      web(GlobalExceptionHandler 등) · security · sse · sms · redis · qr
```

| 계층                 | 둔다                                              | 의존 가능            | 금지                              |
|--------------------|-------------------------------------------------|------------------|---------------------------------|
| **domain**         | 엔티티, VO, 도메인 서비스, port, event, 도메인 예외           | (없음 — 순수 자바)     | Spring/JPA import, 다른 계층 import |
| **application**    | 어플리케이션 서비스, DTO                                 | domain (port 포함) | 도메인 객체/JpaEntity를 그대로 외부 노출     |
| **infrastructure** | JpaEntity, JpaRepository, RepositoryImpl, 외부 연동 | domain           | JpaEntity를 다른 계층으로 누출           |
| **presentation**   | Controller                                      | application      | Repository/JpaRepository 직접 호출  |

---

## 3. 세 가지 — 도메인 / 도메인 서비스 / 어플리케이션 서비스

| 구분             | 책임                                                        | 예시                                           |
|----------------|-----------------------------------------------------------|----------------------------------------------|
| **도메인**        | 비즈니스 로직 수행, 도메인 역할 수행, 다른 도메인과 협력                         | `WaitingEntry`, `Store`                      |
| **도메인 서비스**    | 객체로 표현하기 애매한 비즈니스 **'연산'**, 도메인 협력 중재                     | (예: `PriceManager` — 단 아래 예시처럼 새 도메인 도출이 우선) |
| **어플리케이션 서비스** | 어플리케이션 **'연산'** — 저장소에서 도메인 로드 → 도메인 서비스 실행 → 도메인 실행 → 저장 | `WaitingManagementService`                   |

**판단 가이드 — "이 로직 어디에 둘까?"**

```
비즈니스 규칙/상태 변화인가?
 ├─ 한 도메인 객체로 표현 가능 → 그 도메인의 메서드     (1순위)
 ├─ 표현 애매 → 새 도메인을 도출할 수 있나?
 │     ├─ 가능 → 새 도메인 생성                       (2순위)
 │     └─ 불가능한 '연산'만 → 도메인 서비스            (3순위, 마지막)
 └─ 비즈니스 규칙이 아니라 '흐름 조립'(로드/저장/트랜잭션/이벤트 발행)
       → 어플리케이션 서비스
```

**워크드 예시 — "도메인 서비스"보다 "새 도메인"** *(가상 도메인 — 이 프로젝트와 무관, 원리 설명용)*

`Product / Coupon / User` 가 있고 결제가를 이렇게 계산한다고 하자:
`결제가 = 상품가 − (상품가 × 쿠폰 할인율) − 사용자 마일리지`

- ❌ **트랜잭션 스크립트:** `ProductService.calculatePrice()`가 쿠폰 탐색·마일리지 차감을 직접 계산 → 비즈니스 로직이 서비스에 고임.
- 🤔 "로직은 도메인으로" → 그런데 이 계산을 `Product`·`Coupon`·`User` **어디에 넣어도 애매**하다.
- 🥈 `PriceManager` **도메인 서비스**로 뺄 수 있다. 동작은 하지만 **차선**이다.
- 🥇 **계산을 책임지는 새 도메인 `Cashier`를 도출한다.** `Cashier.checkout(product, coupon, user)`가 계산을 수행 → 가장 도메인 중심적.

이때 `ProductService`(어플리케이션 서비스)는 **도메인·도메인 서비스의 파사드**일 뿐이다: 저장소에서 `Product/Coupon/User` 로드 → `Cashier` 실행 → 저장/반환. 비즈니스 규칙은 갖지 않는다.

> 새 로직을 짤 때 **항상** 두 가지를 먼저 묻는다: ① 기존 도메인 객체에 못 넣나? ② 새 도메인으로 못 만드나? — 둘 다 "아니오"일 때만 도메인 서비스를 만든다.

---

## 4. 계층별 Do / Don't

### domain (엔티티 · VO)

- ✅ 순수 자바. 불변(immutable) 지향.
- ✅ 생성은 팩토리 `create(...)`, 영속 복원은 `restore(...)`.
- ✅ 상태 전이는 도메인 메서드로. 잘못된 전이는 `IllegalStateException`.
  ```java
  public WaitingEntry call() {
    if (status != WaitingStatus.WAITING)
      throw new IllegalStateException("call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.CALLED, createdAt);
  }
  ```
- ✅ getter는 Lombok `@Getter`만. 도메인 메서드는 직접 구현.
- ❌ Spring/JPA 어노테이션, 다른 계층 타입 import 금지.

### domain service

- ✅ 도메인 협력 중재와 *연산*만 처리. port(Repository 인터페이스)에만 의존.
- ✅ Spring 빈으로 등록할 땐 `@Component` 사용 가능.
- ❌ 영속/트랜잭션 경계/DTO 변환 금지. **만들기 전에 "새 도메인 없나?" 먼저 자문.**

### application service

- ✅ `@Service @RequiredArgsConstructor`. **얇게** 유지.
- ✅ 흐름: 도메인 로드 → 도메인(또는 도메인 서비스) 실행 → 저장 → 이벤트 발행.
- ✅ 트랜잭션 경계 소유: 쓰기 `@Transactional`, 읽기 `@Transactional(readOnly = true)`.
- ✅ 입출력은 DTO(record). 도메인 객체를 Controller로 그대로 내보내지 않는다.
- ❌ 비즈니스 규칙을 여기 두지 말 것 — 도메인으로 내린다.

### infrastructure

- ✅ `RepositoryImpl`이 domain의 port를 구현하고 `JpaRepository`에 위임.
- ✅ `JpaEntity`는 `from(domain)` / `toDomain()` 매핑 책임만.
  ```java
  @Override
  public WaitingEntry save(WaitingEntry entry) {
    return waitingEntryJpaRepository.save(WaitingEntryJpaEntity.from(entry)).toDomain();
  }
  ```
- ✅ **`@Query`(JPQL)는 자바 텍스트 블록(`"""`)으로 작성**한다. 문자열 `+` 연결 금지.
- ✅ **쿼리 포맷은 SQL Style Guide(Simon Holywell)의 river 정렬**을 따른다: root 키워드(`SELECT`/`FROM`/`WHERE`/`AND`/`OR`/`GROUP BY`/`ORDER BY` 등)의 **오른쪽 끝을 같은 문자
  경계에 맞춰 우측 정렬**한다. river 폭은 그 쿼리에서 **가장 긴 키워드 기준**이라 `GROUP BY`/`ORDER BY`가 있으면 8, 없으면 6이 된다.
  ```java
  @Query("""
        SELECT w.status AS status, COUNT(w) AS count
          FROM WaitingEntryJpaEntity w
         WHERE w.storeId = :storeId
           AND w.createdAt >= :startOfDay
           AND w.createdAt < :endOfDay
      GROUP BY w.status
      """)
  List<StatusCount> countByStatusGrouped(...);
  ```
- ✅ **다중 컬럼 집계 결과는 `Object[]` 대신 projection 인터페이스**로 받는다. SELECT 별칭(`AS xxx`)을 getter 이름과 일치시켜 비검사 캐스팅을 없앤다. projection 인터페이스는 해당 `JpaRepository` 안에
  중첩으로 둔다.
- ❌ `JpaEntity`를 application/presentation으로 누출 금지.

### presentation (Controller)

- ✅ 어플리케이션 서비스만 의존. 입출력은 DTO.
- ✅ 인증 주체(`ownerId`)는 `SecurityContext`에서 추출.
- ❌ Repository/JpaRepository 직접 호출 금지. 비즈니스 로직 금지.

---

## 5. 횡단 패턴 (필수 준수)

- **예외 → HTTP 매핑:** 도메인 예외는 각 `{aggregate}/domain/`에 둔다. HTTP 상태 매핑은 **오직** `shared/web/GlobalExceptionHandler`에서 `ErrorResponse.of(code, message)`로
  한다. Controller/Service에서 상태코드를 다루지 않는다.
- **소유권 검증:** 다른 매장 리소스 접근은 `StoreNotFoundException`(404)으로 처리해 **리소스 존재 자체를 숨긴다.** (403 아님 — 의도된 보안 패턴)
- **부수효과(외부 연동):** 상태 변경 후 `ApplicationEventPublisher`로 도메인 이벤트(`event/XxxEvent`)를 발행한다. SMS 발송 등 외부 연동은
  `@TransactionalEventListener(phase = AFTER_COMMIT)` 리스너에서 처리한다 (커밋 후 실행).
- **실시간 전송:** 클라이언트(손님/점주) 푸시는 항상 `SsePublisher`를 경유한다. Service가 SseEmitter를 직접 다루지 않는다.

---

## 6. 네이밍 & 위치 규칙

| 종류                   | 이름                                   | 위치                       |
|----------------------|--------------------------------------|--------------------------|
| 엔티티/VO               | `Store`, `WaitingEntry`              | `{agg}/domain/`          |
| 도메인 서비스              | `XxxCalculator`, `XxxPolicy` 등 역할 기반 | `{agg}/domain/`          |
| 포트(Repository 인터페이스) | `XxxRepository`                      | `{agg}/domain/`          |
| 도메인 이벤트              | `XxxEvent`                           | `{agg}/domain/event/`    |
| 도메인 예외               | `XxxException`                       | `{agg}/domain/`          |
| 어플리케이션 서비스           | `XxxService`                         | `{agg}/application/`     |
| DTO                  | `XxxRequest`, `XxxResponse` (record) | `{agg}/application/dto/` |
| JPA 엔티티              | `XxxJpaEntity`                       | `{agg}/infrastructure/`  |
| Spring Data 리포지토리    | `XxxJpaRepository`                   | `{agg}/infrastructure/`  |
| 포트 구현체               | `XxxRepositoryImpl`                  | `{agg}/infrastructure/`  |
| Controller           | `XxxController`                      | `{agg}/presentation/`    |

---

## 7. 새 기능 추가 순서 (Domain-first 체크리스트)

1. **도메인 모델과 규칙을 먼저 만든다** (엔티티/VO + 상태 전이 메서드). 새 도메인이 필요한지 먼저 검토.
2. 필요한 **port(Repository 인터페이스)** 를 `domain/`에 정의.
3. **infrastructure** 구현: `JpaEntity`(from/toDomain), `JpaRepository`, `RepositoryImpl`.
4. (정말 필요할 때만) **도메인 서비스** 추가.
5. **얇은 어플리케이션 서비스** 작성 — 로드/실행/저장/이벤트만.
6. **DTO**(record) 정의.
7. **Controller** 추가 — 서비스 호출 + DTO 변환만.
8. 도메인 예외 + `GlobalExceptionHandler` 매핑.
9. **테스트**: 도메인 단위 테스트 우선, 서비스/Controller 테스트 보강.

---

## 8. 불확실할 때

- 새 코드는 **기존 코드의 같은 계층 패턴을 그대로 따른다.**
- 규칙으로 판단이 안 서면 임의로 새 패턴을 만들지 말고 **사용자에게 확인한다.**
