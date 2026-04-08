package com.qrwait.api.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

  @Mock
  private JwtTokenProvider jwtTokenProvider;

  private JwtAuthFilter jwtAuthFilter;

  @BeforeEach
  void setUp() {
    jwtAuthFilter = new JwtAuthFilter(jwtTokenProvider);
    SecurityContextHolder.clearContext();
  }

  @Test
  void 유효한_Bearer_토큰_인증_성공() throws Exception {
    UUID ownerId = UUID.randomUUID();
    String token = "valid-token";
    given(jwtTokenProvider.validateToken(token)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(token)).willReturn(ownerId);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);

    jwtAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isEqualTo(ownerId);
    assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
  }

  @Test
  void 유효하지않은_토큰_인증_미설정() throws Exception {
    String badToken = "bad-token";
    given(jwtTokenProvider.validateToken(badToken)).willReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + badToken);

    jwtAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void Authorization_헤더_없으면_인증_미설정() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();

    jwtAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
