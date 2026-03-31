# Backend Architecture Decisions

백엔드 설계 과정에서 논의한 의사결정과 그 근거를 기록합니다.
각 항목은 "왜 그렇게 했는가"와 "어떤 대안을 고려했는가"를 담습니다.

---

## ADR-001 · 도메인 모델 getter — Lombok @Getter 사용

**결정:** 도메인 모델(`Store`, `WaitingEntry`)의 getter는 수동 작성 대신 Lombok `@Getter` 적용

**배경:** 처음에는 명시적 getter를 직접 작성했으나, 이미 Lombok이 의존성에 포함되어 있어 불필요한 보일러플레이트라는 피드백

**대안:** 수동 getter, Java record (불변 객체라면 가능하나 WaitingEntry처럼 상태가 변하는 경우 부적합)

**결론:** `@Getter` 적용. 단, 도메인 메서드(`cancel()`, `call()` 등)는 Lombok 대상이 아니므로 직접 구현 유지

---

## ADR-002 · UseCase 인터페이스 분리 여부

**결정:** UseCase를 인터페이스 + 구현체로 분리 (`RegisterWaitingUseCase` + `RegisterWaitingUseCaseImpl`)

**배경:** Controller가 Application 계층에 의존할 때, 인터페이스 없이 구체 클래스를 직접 사용하는 방식과의 비교 논의

**찬성 (현재 방식):**
- 계층 경계를 코드로 명시적으로 표현 ("나는 계약에만 의존한다")
- `@WebMvcTest`에서 `@MockBean UseCase` 시 경계 의도가 명확

**반대 (구체 클래스 방식):**
- 구현체를 실제로 갈아끼울 일이 거의 없음
- Mockito subclass mock maker로 구체 클래스도 mock 가능
- 파일 수 2배 증가, 보일러플레이트

**결론:** 이 프로젝트는 클린 아키텍처 학습 목적이 크므로 인터페이스 분리 유지.
실무 소규모 MVP라면 구체 클래스로도 충분하며, 팀 컨벤션에 따라 결정하는 것이 현실적

---

<!-- 새로운 논의가 생기면 아래 양식으로 추가 -->

## ADR-NNN · 제목

**결정:**

**배경:**

**대안:**

**결론:**
