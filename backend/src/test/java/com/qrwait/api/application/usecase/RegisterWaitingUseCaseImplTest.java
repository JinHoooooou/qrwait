package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.RegisterWaitingRequest;
import com.qrwait.api.application.dto.RegisterWaitingResponse;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterWaitingUseCaseImplTest {

  @Mock
  StoreRepository storeRepository;
  @Mock
  WaitingRepository waitingRepository;
  @InjectMocks
  RegisterWaitingUseCaseImpl useCase;

  private RegisterWaitingRequest request;
  private UUID storeId;

  @BeforeEach
  void setUp() {
    storeId = UUID.randomUUID();
    request = new RegisterWaitingRequest();
    request.setVisitorName("홍길동");
    request.setPartySize(2);
  }

  @Test
  void execute_정상등록_waitingNumber와_currentRank_반환() {
    // given
    given(storeRepository.findById(storeId))
        .willReturn(Optional.of(Store.create(null, "테스트 식당", null)));
    given(waitingRepository.findNextWaitingNumber(storeId)).willReturn(3);
    given(waitingRepository.save(any(WaitingEntry.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(2);

    // when
    RegisterWaitingResponse response = useCase.execute(storeId, request);

    // then
    assertThat(response.waitingNumber()).isEqualTo(3);
    assertThat(response.currentRank()).isEqualTo(2);
    assertThat(response.totalWaiting()).isEqualTo(2);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(10);
    assertThat(response.waitingToken()).isNotBlank();
  }

  @Test
  void execute_존재하지않는_storeId_예외발생() {
    // given
    given(storeRepository.findById(storeId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> useCase.execute(storeId, request))
        .isInstanceOf(StoreNotFoundException.class);
  }
}
