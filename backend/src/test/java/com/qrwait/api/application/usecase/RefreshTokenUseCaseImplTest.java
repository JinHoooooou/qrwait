package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.domain.model.InvalidCredentialsException;
import com.qrwait.api.infrastructure.redis.RefreshTokenRepository;
import com.qrwait.api.presentation.security.JwtTokenProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseImplTest {

  private final UUID ownerId = UUID.randomUUID();
  @Mock
  private JwtTokenProvider jwtTokenProvider;
  @Mock
  private RefreshTokenRepository refreshTokenRepository;
  @InjectMocks
  private RefreshTokenUseCaseImpl useCase;

  @Test
  void execute_유효한_토큰_새_AccessToken_발급() {
    String refreshToken = "valid-refresh-token";
    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(refreshToken)).willReturn(ownerId);
    given(refreshTokenRepository.findByOwnerId(ownerId)).willReturn(Optional.of(refreshToken));
    given(jwtTokenProvider.generateAccessToken(ownerId)).willReturn("new-access-token");

    String newAccessToken = useCase.execute(refreshToken);

    assertThat(newAccessToken).isEqualTo("new-access-token");
  }

  @Test
  void execute_유효하지않은_토큰_예외발생() {
    given(jwtTokenProvider.validateToken("bad-token")).willReturn(false);

    assertThatThrownBy(() -> useCase.execute("bad-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void execute_Redis에_없는_토큰_예외발생() {
    String refreshToken = "valid-refresh-token";
    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(refreshToken)).willReturn(ownerId);
    given(refreshTokenRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(refreshToken))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void execute_Redis_저장값과_불일치_예외발생() {
    String refreshToken = "valid-refresh-token";
    given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
    given(jwtTokenProvider.extractOwnerId(refreshToken)).willReturn(ownerId);
    given(refreshTokenRepository.findByOwnerId(ownerId)).willReturn(Optional.of("different-token"));

    assertThatThrownBy(() -> useCase.execute(refreshToken))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
