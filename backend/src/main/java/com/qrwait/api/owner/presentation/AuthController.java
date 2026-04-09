package com.qrwait.api.owner.presentation;

import com.qrwait.api.owner.application.OwnerService;
import com.qrwait.api.owner.application.dto.AccessTokenResponse;
import com.qrwait.api.owner.application.dto.LoginRequest;
import com.qrwait.api.owner.application.dto.LoginResponse;
import com.qrwait.api.owner.application.dto.SignUpRequest;
import com.qrwait.api.owner.application.dto.SignUpResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  static final String REFRESH_TOKEN_COOKIE = "refresh_token";

  private final OwnerService ownerService;

  @Value("${jwt.refresh-expiry}")
  private int refreshExpirySeconds;

  @PostMapping("/signup")
  public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
    SignUpResponse response = ownerService.signUp(request);
    URI location = URI.create("/api/owner/stores/" + response.storeId());
    return ResponseEntity.created(location).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse httpResponse) {
    LoginResponse response = ownerService.login(request);
    setRefreshTokenCookie(httpResponse, response.refreshToken());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal UUID ownerId, HttpServletResponse httpResponse) {
    ownerService.logout(ownerId);
    clearRefreshTokenCookie(httpResponse);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<AccessTokenResponse> refresh(@CookieValue(REFRESH_TOKEN_COOKIE) String refreshToken) {
    String newAccessToken = ownerService.refresh(refreshToken);
    return ResponseEntity.ok(new AccessTokenResponse(newAccessToken));
  }

  private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
    cookie.setHttpOnly(true);
    cookie.setPath("/api/auth/refresh");
    cookie.setMaxAge(refreshExpirySeconds);
    response.addCookie(cookie);
  }

  private void clearRefreshTokenCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
    cookie.setHttpOnly(true);
    cookie.setPath("/api/auth/refresh");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }
}
