# Backend Architecture Decisions

백엔드 설계 과정에서 논의한 의사결정과 그 근거를 기록합니다.
각 항목은 "왜 그렇게 했는가"와 "어떤 대안을 고려했는가"를 담습니다.

> **읽는 법:** ADR은 시간순 기록이며, **뒤의 ADR이 앞의 ADR을 뒤집을 수 있습니다.**
> 각 항목의 `상태`를 반드시 확인하세요. `Superseded`인 ADR의 결론을 현행 컨벤션으로 착각하면 안 됩니다.

| ADR | 제목 | 상태 |
|-----|------|-----|
| ADR-001 | 도메인 모델 getter — Lombok @Getter 사용 | ✅ Accepted |
| ADR-002 | UseCase 인터페이스 분리 여부 | ❌ **Superseded by ADR-003** |
| ADR-003 | UseCase 폐기 — 애그리거트별 패키지 + 구체 Service | ✅ Accepted (현행) |

---

## ADR-001 · 도메인 모델 getter — Lombok @Getter 사용

**상태:** ✅ Accepted

**결정:** 도메인 모델(`Store`, `WaitingEntry`)의 getter는 수동 작성 대신 Lombok `@Getter` 적용

**배경:** 처음에는 명시적 getter를 직접 작성했으나, 이미 Lombok이 의존성에 포함되어 있어 불필요한 보일러플레이트라는 피드백

**대안:** 수동 getter, Java record (불변 객체라면 가능하나 WaitingEntry처럼 상태가 변하는 경우 부적합)

**결론:** `@Getter` 적용. 단, 도메인 메서드(`cancel()`, `call()` 등)는 Lombok 대상이 아니므로 직접 구현 유지

---

## ADR-002 · UseCase 인터페이스 분리 여부

**상태:** ❌ **Superseded by ADR-003 (2026-04-09)** — 아래 결론은 **더 이상 유효하지 않습니다.**
현행 코드에 `UseCase` 타입은 존재하지 않습니다. 이 항목은 당시 판단을 남기기 위해 보존합니다.

**결정:** UseCase를 인터페이스 + 구현체로 분리 (`RegisterWaitingUseCase` + `RegisterWaitingUseCaseImpl`)

**배경:** Controller가 Application 계층에 의존할 때, 인터페이스 없이 구체 클래스를 직접 사용하는 방식과의 비교 논의

**찬성 (당시 방식):**
- 계층 경계를 코드로 명시적으로 표현 ("나는 계약에만 의존한다")
- `@WebMvcTest`에서 `@MockBean UseCase` 시 경계 의도가 명확

**반대 (구체 클래스 방식):**
- 구현체를 실제로 갈아끼울 일이 거의 없음
- Mockito subclass mock maker로 구체 클래스도 mock 가능
- 파일 수 2배 증가, 보일러플레이트

**결론(당시):** 인터페이스 분리 유지.

> ⚠️ 당시 결론의 근거로 "학습 목적"을 들었으나, 이 프로젝트는 실제 매장 투입을 목표로 하는 프로덕션 지향 제품입니다(루트 `CLAUDE.md` 참조). 그 전제 자체가 잘못되었고, 결론도 ADR-003에서 뒤집혔습니다.

---

## ADR-003 · UseCase 폐기 — 애그리거트별 패키지 + 구체 Service

**상태:** ✅ Accepted (현행 컨벤션)

**결정:** UseCase 인터페이스/구현체 쌍을 전부 제거하고, **애그리거트별 4계층 패키지** 안에 **구체 클래스 애플리케이션 서비스**(`XxxService`)를 둔다.

**배경:** ADR-002는 레이어별 패키지(`application/usecase/`, `domain/model/` …) 구조를 전제로 한 결정이었다. 이후 패키지를 **애그리거트(도메인) 단위**로 재구성하면서(`owner/`, `store/`, `waiting/` 각각이 `domain·application·infrastructure·presentation`을 가짐) 전제가 무너졌다.

- 관련 커밋: `26211be`(shared 분리) → `bd4f897`(waiting) → `fb84d79`(store) → `91d4e4b`(owner) → **`eadfd34`**(레이어별 레거시 패키지 및 잔여 UseCase 제거, 2026-04-09)
- 이후 `2026-06-30` 스펙에서 액터별 서브패키지(`customer/`, `management/`)로 한 번 더 분리

**대안:**
- ADR-002 유지(인터페이스 존치) — 애그리거트별 구조에서는 파일 수만 2배가 되고, 계층 경계는 이미 **패키지 구조와 port 인터페이스(DIP)** 로 표현되므로 중복이다.
- 유스케이스 1개 = 클래스 1개 — 오케스트레이션이 얇아 클래스가 과분해된다.

**결론:** 애그리거트별 패키지에서는 계층 경계가 이미 구조로 드러나므로, 애플리케이션 계층의 추가 인터페이스는 순수 보일러플레이트다.

- 애플리케이션 서비스는 `{agg}/application/XxxService` 또는 `{agg}/{actor}/application/XxxService` 의 **구체 클래스**로 둔다.
- 테스트는 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`의 `mock-maker-subclass` 설정으로 구체 클래스를 그대로 목킹한다.
- **인터페이스를 두는 곳은 오직 port다** — `{agg}/domain/XxxRepository`와 외부 연동 포트(`shared/sms/SmsClient` 등). 이건 DIP상 필수이므로 유지한다.

> 즉 "인터페이스를 쓰지 않는다"가 아니라, **의존 역전이 필요한 경계(port)에만 쓴다.** 현행 규칙은 `backend/CLAUDE.md` §2를 따른다.

---

<!-- 새로운 논의가 생기면 아래 양식으로 추가. 기존 ADR을 뒤집을 때는 그 ADR의 '상태'를 Superseded by ADR-NNN 으로 바꾼다. -->

## ADR-NNN · 제목

**상태:**

**결정:**

**배경:**

**대안:**

**결론:**
