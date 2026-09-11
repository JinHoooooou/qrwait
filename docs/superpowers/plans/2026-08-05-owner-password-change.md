# 로그인 상태 비밀번호 변경 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인한 점주가 현재 비밀번호 확인 후 새 비밀번호로 변경하고, 성공 시 기존 세션(refresh token)이 무효화되는 `POST /api/auth/password` 기능을 추가한다.

**Architecture:** owner 애그리거트에 4계층으로 추가한다. 도메인(`Owner`)은 불변 패턴으로 새 인스턴스를 반환하는 `changePassword` 메서드를 갖고, 비밀번호 매칭/인코딩은 기존 login처럼 어플리케이션 서비스(`OwnerService`)가 `PasswordEncoder`로 수행한다. 컨트롤러는 인증된 점주(`@AuthenticationPrincipal`)만 접근하며, 성공 시 refresh token을 삭제하고 쿠키를 비운다.

**Tech Stack:** Java 21 · Spring Boot 3.5 · Spring Security · JPA · Redis · JUnit5 · Mockito · AssertJ · MockMvc

## Global Constraints

- 백엔드 아키텍처 규칙은 `backend/CLAUDE.md`를 따른다: domain은 순수 자바(Spring/JPA import 금지), 비즈니스 규칙은 도메인 우선, 어플리케이션 서비스는 얇게, HTTP 상태 매핑은 오직 `shared/web/GlobalExceptionHandler`에서.
- 모든 명령은 `backend/` 디렉터리에서 실행한다 (`cd backend`).
- 요청 DTO는 이 코드베이스 관례(클래스 + `@Getter @NoArgsConstructor` + 검증 애너테이션)를 따른다. record 아님.
- 새 비밀번호 규칙: `@NotBlank @Size(min = 8)` (가입 규칙과 동일) + 현재 비밀번호와 달라야 함.
- 도메인 예외는 `owner/domain/`에 두고, HTTP 매핑은 `GlobalExceptionHandler`에만 추가한다.

---

### Task 1: `Owner.changePassword` 도메인 메서드

**Files:**
- Modify: `backend/src/main/java/com/qrwait/api/owner/domain/Owner.java`
- Test: `backend/src/test/java/com/qrwait/api/owner/domain/OwnerTest.java` (신규)

**Interfaces:**
- Consumes: (없음)
- Produces: `Owner Owner.changePassword(String newPasswordHash)` — `id`/`email`/`createdAt`은 보존하고 `passwordHash`만 교체한 **새 Owner 인스턴스**를 반환한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/qrwait/api/owner/domain/OwnerTest.java` 생성:

```java
package com.qrwait.api.owner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerTest {

  @Test
  void changePassword_새_해시로_새_Owner를_반환하고_나머지_필드는_보존한다() {
    UUID id = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.now();
    Owner owner = Owner.restore(id, "owner@test.com", "old-hash", createdAt);

    Owner changed = owner.changePassword("new-hash");

    assertThat(changed.getPasswordHash()).isEqualTo("new-hash");
    assertThat(changed.getId()).isEqualTo(id);
    assertThat(changed.getEmail()).isEqualTo("owner@test.com");
    assertThat(changed.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void changePassword_원본_Owner는_변경되지_않는다() {
    Owner owner = Owner.restore(UUID.randomUUID(), "owner@test.com", "old-hash", LocalDateTime.now());

    owner.changePassword("new-hash");

    assertThat(owner.getPasswordHash()).isEqualTo("old-hash");
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.owner.domain.OwnerTest"`
Expected: 컴파일 실패 — `changePassword` 메서드 없음.

- [ ] **Step 3: 도메인 메서드 구현**

`Owner.java`의 `restore(...)` 아래에 추가:

```java
  public Owner changePassword(String newPasswordHash) {
    return new Owner(id, email, newPasswordHash, createdAt);
  }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.owner.domain.OwnerTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/owner/domain/Owner.java \
        backend/src/test/java/com/qrwait/api/owner/domain/OwnerTest.java
git commit -m "feat: Owner.changePassword 도메인 메서드 추가"
```

---

### Task 2: `OwnerService.changePassword` (예외 · DTO · 서비스 로직)

**Files:**
- Create: `backend/src/main/java/com/qrwait/api/owner/domain/SamePasswordException.java`
- Create: `backend/src/main/java/com/qrwait/api/owner/application/dto/ChangePasswordRequest.java`
- Modify: `backend/src/main/java/com/qrwait/api/owner/application/OwnerService.java`
- Test: `backend/src/test/java/com/qrwait/api/owner/application/OwnerServiceTest.java` (기존에 테스트 추가)

**Interfaces:**
- Consumes: `Owner.changePassword(String)` (Task 1), 기존 `OwnerRepository.findById(UUID)`, `OwnerRepository.save(Owner)`, `RefreshTokenRepository.delete(UUID)`, `PasswordEncoder`.
- Produces:
  - `SamePasswordException extends RuntimeException` — 메시지 `"새 비밀번호는 현재 비밀번호와 달라야 합니다."`
  - `ChangePasswordRequest` — `String getCurrentPassword()`, `String getNewPassword()` (검증: currentPassword `@NotBlank`, newPassword `@NotBlank @Size(min = 8)`)
  - `void OwnerService.changePassword(UUID ownerId, ChangePasswordRequest request)` — `@Transactional`. 현재 비번 불일치 시 `InvalidCredentialsException`, 새 비번이 현재와 같으면 `SamePasswordException`, 성공 시 새 해시 저장 + `refreshTokenRepository.delete(ownerId)`.

- [ ] **Step 1: 예외 클래스 생성**

`backend/src/main/java/com/qrwait/api/owner/domain/SamePasswordException.java`:

```java
package com.qrwait.api.owner.domain;

public class SamePasswordException extends RuntimeException {

  public SamePasswordException() {
    super("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
  }
}
```

- [ ] **Step 2: 요청 DTO 생성**

`backend/src/main/java/com/qrwait/api/owner/application/dto/ChangePasswordRequest.java` (`LoginRequest` 관례를 그대로 따른다):

```java
package com.qrwait.api.owner.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequest {

  @NotBlank
  private String currentPassword;

  @NotBlank
  @Size(min = 8)
  private String newPassword;
}
```

- [ ] **Step 3: 실패하는 서비스 테스트 작성**

`OwnerServiceTest.java`에 import 추가 후 (`ChangePasswordRequest`, `SamePasswordException`), `// ===== refresh =====` 블록 아래·`private SignUpRequest ...` 헬퍼 위에 테스트와 헬퍼를 추가한다. (헬퍼는 기존 `createLoginRequest`와 같은 `ReflectionTestUtils` 방식.)

```java
  // ===== changePassword =====

  @Test
  void changePassword_정상변경_새해시저장_refreshToken삭제() {
    String encoded = passwordEncoder.encode("oldPassword1");
    Owner owner = Owner.restore(ownerId, "owner@test.com", encoded, LocalDateTime.now());
    given(ownerRepository.findById(ownerId)).willReturn(Optional.of(owner));

    ownerService.changePassword(ownerId, createChangePasswordRequest("oldPassword1", "newPassword1"));

    ArgumentCaptor<Owner> captor = ArgumentCaptor.forClass(Owner.class);
    verify(ownerRepository).save(captor.capture());
    assertThat(passwordEncoder.matches("newPassword1", captor.getValue().getPasswordHash())).isTrue();
    verify(refreshTokenRepository).delete(ownerId);
  }

  @Test
  void changePassword_현재비번_불일치_예외발생() {
    Owner owner = Owner.restore(ownerId, "owner@test.com", passwordEncoder.encode("correct1"), LocalDateTime.now());
    given(ownerRepository.findById(ownerId)).willReturn(Optional.of(owner));

    assertThatThrownBy(() ->
        ownerService.changePassword(ownerId, createChangePasswordRequest("wrongPassword", "newPassword1")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(ownerRepository, never()).save(any());
    verify(refreshTokenRepository, never()).delete(any());
  }

  @Test
  void changePassword_새비번이_현재와동일_예외발생() {
    String encoded = passwordEncoder.encode("samePassword1");
    Owner owner = Owner.restore(ownerId, "owner@test.com", encoded, LocalDateTime.now());
    given(ownerRepository.findById(ownerId)).willReturn(Optional.of(owner));

    assertThatThrownBy(() ->
        ownerService.changePassword(ownerId, createChangePasswordRequest("samePassword1", "samePassword1")))
        .isInstanceOf(SamePasswordException.class);

    verify(ownerRepository, never()).save(any());
    verify(refreshTokenRepository, never()).delete(any());
  }
```

그리고 `createLoginRequest` 헬퍼 아래에 추가:

```java
  private ChangePasswordRequest createChangePasswordRequest(String current, String newPassword) {
    ChangePasswordRequest request = new ChangePasswordRequest();
    ReflectionTestUtils.setField(request, "currentPassword", current);
    ReflectionTestUtils.setField(request, "newPassword", newPassword);
    return request;
  }
```

필요한 import (파일 상단):

```java
import com.qrwait.api.owner.application.dto.ChangePasswordRequest;
import com.qrwait.api.owner.domain.SamePasswordException;
import org.mockito.ArgumentCaptor;
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.owner.application.OwnerServiceTest"`
Expected: 컴파일 실패 — `OwnerService.changePassword` 없음.

- [ ] **Step 5: 서비스 메서드 구현**

`OwnerService.java`의 `refresh(...)` 아래에 추가하고, 필요한 import(`SamePasswordException`, `ChangePasswordRequest`)를 더한다:

```java
  @Transactional
  public void changePassword(UUID ownerId, ChangePasswordRequest request) {
    Owner owner = ownerRepository.findById(ownerId)
        .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(request.getCurrentPassword(), owner.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    if (passwordEncoder.matches(request.getNewPassword(), owner.getPasswordHash())) {
      throw new SamePasswordException();
    }

    String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
    ownerRepository.save(owner.changePassword(newPasswordHash));
    refreshTokenRepository.delete(ownerId);
  }
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.owner.application.OwnerServiceTest"`
Expected: PASS (기존 + 신규 3개)

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/owner/domain/SamePasswordException.java \
        backend/src/main/java/com/qrwait/api/owner/application/dto/ChangePasswordRequest.java \
        backend/src/main/java/com/qrwait/api/owner/application/OwnerService.java \
        backend/src/test/java/com/qrwait/api/owner/application/OwnerServiceTest.java
git commit -m "feat: OwnerService.changePassword 및 SamePasswordException 추가"
```

---

### Task 3: 엔드포인트 · 보안 · 예외 매핑

**Files:**
- Modify: `backend/src/main/java/com/qrwait/api/owner/presentation/AuthController.java`
- Modify: `backend/src/main/java/com/qrwait/api/shared/security/SecurityConfig.java`
- Modify: `backend/src/main/java/com/qrwait/api/shared/web/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/qrwait/api/owner/presentation/AuthControllerTest.java` (기존에 테스트 추가)

**Interfaces:**
- Consumes: `OwnerService.changePassword(UUID, ChangePasswordRequest)` (Task 2), `SamePasswordException` (Task 2), 기존 `AuthController.clearRefreshTokenCookie(...)`, 기존 `AuthController.REFRESH_TOKEN_COOKIE`.
- Produces: `POST /api/auth/password` — 204 No Content + refresh_token 삭제 쿠키. 미인증 시 401, 새 비번 == 현재 시 400 `SAME_PASSWORD`, 현재 비번 오류 시 401 `INVALID_CREDENTIALS`, 새 비번 8자 미만 시 400 `INVALID_REQUEST`.

> **보안 주의:** `SecurityConfig`에서 `/api/auth/**`가 `permitAll()`이므로, 새 규칙을 그 줄보다 **먼저** 추가하지 않으면 미인증 요청에서 `@AuthenticationPrincipal ownerId`가 null이 된다. 반드시 순서를 지킨다.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`AuthControllerTest.java`에 아래 테스트들을 추가한다. 인증 요청은 기존 `JwtAuthFilter` 흐름대로 `jwtTokenProvider`를 스텁하고 `Authorization: Bearer` 헤더를 보낸다.

```java
  @Test
  void 비밀번호변경_성공_204반환_refreshToken쿠키삭제() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);

    Map<String, String> request = Map.of("currentPassword", "oldPassword1", "newPassword", "newPassword1");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(AuthController.REFRESH_TOKEN_COOKIE, 0));

    verify(ownerService).changePassword(eq(ownerId), any());
  }

  @Test
  void 비밀번호변경_미인증_401반환() throws Exception {
    Map<String, String> request = Map.of("currentPassword", "oldPassword1", "newPassword", "newPassword1");

    mockMvc.perform(post("/api/auth/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 비밀번호변경_현재비번오류_401반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);
    org.mockito.BDDMockito.willThrow(new InvalidCredentialsException())
        .given(ownerService).changePassword(eq(ownerId), any());

    Map<String, String> request = Map.of("currentPassword", "wrongPassword", "newPassword", "newPassword1");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void 비밀번호변경_새비번이_현재와동일_400반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);
    org.mockito.BDDMockito.willThrow(new SamePasswordException())
        .given(ownerService).changePassword(eq(ownerId), any());

    Map<String, String> request = Map.of("currentPassword", "samePassword1", "newPassword", "samePassword1");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
  }

  @Test
  void 비밀번호변경_새비번_8자미만_400반환() throws Exception {
    UUID ownerId = UUID.randomUUID();
    given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
    given(jwtTokenProvider.extractOwnerId("valid-token")).willReturn(ownerId);

    Map<String, String> request = Map.of("currentPassword", "oldPassword1", "newPassword", "short");

    mockMvc.perform(post("/api/auth/password")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
```

필요한 import (파일 상단):

```java
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import com.qrwait.api.owner.domain.SamePasswordException;
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.owner.presentation.AuthControllerTest"`
Expected: 실패 — 엔드포인트 없음(404/미구현) 및 미인증 테스트가 401 대신 통과 못 함.

- [ ] **Step 3: 예외 → HTTP 매핑 추가**

`GlobalExceptionHandler.java`에 import `com.qrwait.api.owner.domain.SamePasswordException` 추가 후, `handleInvalidCredentials` 아래에:

```java
  @ExceptionHandler(SamePasswordException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleSamePassword(SamePasswordException e) {
    return ErrorResponse.of("SAME_PASSWORD", e.getMessage());
  }
```

- [ ] **Step 4: 보안 규칙 추가**

`SecurityConfig.java`의 `authorizeHttpRequests`에서 `/api/auth/**` permitAll **위에** POST password 규칙을 넣고, `org.springframework.http.HttpMethod` import를 추가한다:

```java
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/auth/password").hasRole("OWNER")
            .requestMatchers("/api/auth/**", "/api/stores/**", "/api/waitings/**").permitAll()
            .requestMatchers("/api/owner/**").hasRole("OWNER")
            .anyRequest().authenticated())
```

- [ ] **Step 5: 컨트롤러 엔드포인트 구현**

`AuthController.java`의 `refresh(...)` 아래에 추가하고, 필요한 import(`ChangePasswordRequest`, `org.springframework.web.bind.annotation.AuthenticationPrincipal`는 이미 있음 — `@AuthenticationPrincipal`, `@Valid`, `@RequestBody`는 기존 import 재사용):

```java
  @PostMapping("/password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal UUID ownerId,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletResponse httpResponse
  ) {
    ownerService.changePassword(ownerId, request);
    clearRefreshTokenCookie(httpResponse);
    return ResponseEntity.noContent().build();
  }
```

import 추가:

```java
import com.qrwait.api.owner.application.dto.ChangePasswordRequest;
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.qrwait.api.owner.presentation.AuthControllerTest"`
Expected: PASS (기존 + 신규 5개)

- [ ] **Step 7: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: 전체 PASS (회귀 없음)

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/qrwait/api/owner/presentation/AuthController.java \
        backend/src/main/java/com/qrwait/api/shared/security/SecurityConfig.java \
        backend/src/main/java/com/qrwait/api/shared/web/GlobalExceptionHandler.java \
        backend/src/test/java/com/qrwait/api/owner/presentation/AuthControllerTest.java
git commit -m "feat: POST /api/auth/password 비밀번호 변경 엔드포인트 추가"
```

---

## Self-Review

**Spec coverage:**
- 엔드포인트 `POST /api/auth/password` + 204 + 쿠키삭제 → Task 3 ✅
- `Owner.changePassword` 불변 도메인 메서드 → Task 1 ✅
- `SamePasswordException` → Task 2 ✅
- `OwnerService.changePassword`(현재비번 검증 · 동일비번 거부 · 인코딩 · 저장 · refresh 삭제) → Task 2 ✅
- `ChangePasswordRequest`(NotBlank + Size min 8) → Task 2 ✅
- 예외 매핑 3종(INVALID_REQUEST 400 / INVALID_CREDENTIALS 401 / SAME_PASSWORD 400) → Task 3 ✅
- 테스트: 도메인/서비스/컨트롤러 → Task 1/2/3 ✅
- (스펙 밖 보강) `/api/auth/**` permitAll 때문에 필요한 SecurityConfig 인증 규칙 → Task 3 ✅

**Placeholder scan:** TODO/TBD 없음. 모든 코드 스텝에 실제 코드 포함.

**Type consistency:** `changePassword(String)` (Task 1) → `owner.changePassword(newPasswordHash)` (Task 2) 일치. `OwnerService.changePassword(UUID, ChangePasswordRequest)` (Task 2) → 컨트롤러/테스트 호출부 (Task 3) 일치. `SamePasswordException()` 무인자 생성자 일관.
