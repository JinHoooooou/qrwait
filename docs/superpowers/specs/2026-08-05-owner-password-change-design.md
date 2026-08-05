# 로그인 상태 비밀번호 변경 설계

> 상태: 확정 (2026-08-05)
> 대상: `backend/` · 애그리거트: `owner`
> 참고: 백엔드 아키텍처 규칙은 [`backend/CLAUDE.md`](../../../backend/CLAUDE.md)를 따른다.

## 배경

점주 인증에는 회원가입 / 로그인 / 로그아웃 / 토큰 갱신은 있으나 비밀번호를 바꿀 수단이 없다.
이메일 발송 인프라가 아직 없어 "비밀번호 재설정(이메일)"은 인프라 결정이 딸리므로,
발송 채널이 필요 없는 **로그인 상태에서의 비밀번호 변경**을 먼저 추가한다.

## 결정 요약

- 현재 비밀번호 확인 **필수**.
- 새 비밀번호는 **8자 이상**(가입 규칙과 동일) **이며 현재 비밀번호와 달라야** 한다.
- 변경 성공 시 **기존 refresh token 무효화** → 다른 기기는 재로그인 필요, 현재 세션도 쿠키를 비워 재로그인으로 유도.
- 엔드포인트는 기존 auth 액션(logout/refresh)과 동일하게 **POST**.

## 엔드포인트

```
POST /api/auth/password        (인증 필요)

Request  { "currentPassword": "...", "newPassword": "..." }
Response 204 No Content
         Set-Cookie: refresh_token 삭제 (clearRefreshTokenCookie 재사용)
```

인증 주체는 기존 logout처럼 `@AuthenticationPrincipal UUID ownerId`로 추출한다.
성공 시 서버가 Redis의 refresh token을 삭제하고 쿠키도 비우므로 현재 세션은 자연히 로그아웃되어
프론트가 재로그인 화면으로 이동한다.

## 계층별 변경

### domain — `Owner` (owner/domain/)

불변 패턴을 유지하며 새 인스턴스를 반환하는 상태 전이 메서드를 추가한다
(`WaitingEntry.call()`과 동일한 방식).

```java
public Owner changePassword(String newPasswordHash) {
  return new Owner(id, email, newPasswordHash, createdAt);
}
```

### domain — `SamePasswordException` (owner/domain/, 신규)

새 비밀번호가 현재와 같을 때 던진다. `InvalidCredentialsException`과 같은 위치·패턴의 도메인 예외.

### application — `OwnerService.changePassword(UUID ownerId, ChangePasswordRequest request)`

`@Transactional`. 흐름 (login과 동일하게 `PasswordEncoder`를 어플리케이션 계층에서 사용):

1. `ownerRepository`로 Owner 로드.
2. `passwordEncoder.matches(current, owner.getPasswordHash())` 실패 → `InvalidCredentialsException` (재사용).
3. `passwordEncoder.matches(new, owner.getPasswordHash())` 참(= 현재와 동일) → `SamePasswordException`.
4. `passwordEncoder.encode(new)` → `owner.changePassword(hash)` → `ownerRepository.save(...)`.
5. `refreshTokenRepository.delete(ownerId)` — 세션 무효화.

> **"새 비번 == 현재 비번" 검증을 왜 도메인이 아닌 어플리케이션 서비스에 두는가**
> BCrypt는 매번 다른 해시를 생성하므로 저장된 해시 비교로는 동일성을 판단할 수 없고,
> 반드시 `PasswordEncoder.matches(raw, hash)`가 필요하다. `PasswordEncoder`는 기존에도
> login/signUp에서 어플리케이션 계층이 다루므로, 이 규칙도 login의 `matches`와 동일하게
> 어플리케이션 서비스에 둔다.

### application/dto — `ChangePasswordRequest` (record)

```java
record ChangePasswordRequest(
  @NotBlank String currentPassword,
  @NotBlank @Size(min = 8) String newPassword
) {}
```

`newPassword`의 8자 규칙은 `SignUpRequest`와 동일하게 유지한다.

### presentation — `AuthController.changePassword(...)`

서비스 호출 → `clearRefreshTokenCookie(httpResponse)` → `204 No Content`.

## 예외 → HTTP 매핑 (`shared/web/GlobalExceptionHandler`)

| 상황 | 예외 | 상태 | 코드 |
|---|---|---|---|
| 새 비번 8자 미만 / 누락 | `MethodArgumentNotValidException` (기존) | 400 | `INVALID_REQUEST` |
| 현재 비번 틀림 | `InvalidCredentialsException` (기존) | 401 | `INVALID_CREDENTIALS` |
| 새 비번 == 현재 비번 | `SamePasswordException` (신규) | 400 | `SAME_PASSWORD` |

## 테스트

- **도메인 (`Owner`)**: `changePassword`가 새 해시로 새 Owner를 반환하고 `id`/`email`/`createdAt`은 보존한다.
- **서비스 (`OwnerService`)**:
  - 정상 변경 시 저장된 해시가 새 비번과 매칭되고 `refreshTokenRepository.delete(ownerId)`가 호출된다.
  - 현재 비번 오류 시 `InvalidCredentialsException`.
  - 새 비번이 현재와 같으면 `SamePasswordException`, 저장/삭제가 일어나지 않는다.
- **통합 (Controller)**: 204 + refresh_token 삭제 쿠키(Max-Age 0) 응답, 각 에러 상태코드 검증.

## 범위 밖 (YAGNI)

- 이메일 기반 비밀번호 재설정(발송 인프라 필요) — 별도 스펙.
- 비밀번호 복잡도 강화(대소문자/숫자/특수문자) — 도입 시 가입 규칙과 함께 변경해야 하므로 이번 범위에서 제외.
