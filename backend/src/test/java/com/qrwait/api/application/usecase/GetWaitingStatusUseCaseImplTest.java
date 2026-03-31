package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetWaitingStatusUseCaseImplTest {

  @Mock
  WaitingRepository waitingRepository;
  @InjectMocks
  GetWaitingStatusUseCaseImpl useCase;

  @Test
  void execute_currentRank_정확성_검증() {
    // given — 대기열: 1번, 2번, 3번(조회 대상)
    UUID storeId = UUID.randomUUID();
    UUID waitingId = UUID.randomUUID();

    WaitingEntry target = WaitingEntry.restore(
        waitingId, storeId, "홍길동", 2, 3, WaitingStatus.WAITING, LocalDateTime.now()
    );

    List<WaitingEntry> waitingList = List.of(
        WaitingEntry.restore(UUID.randomUUID(), storeId, "손님A", 2, 1, WaitingStatus.WAITING, LocalDateTime.now()),
        WaitingEntry.restore(UUID.randomUUID(), storeId, "손님B", 2, 2, WaitingStatus.WAITING, LocalDateTime.now()),
        target
    );

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(target));
    given(waitingRepository.findByStoreIdAndStatus(storeId, WaitingStatus.WAITING)).willReturn(waitingList);

    // when
    WaitingStatusResponse response = useCase.execute(waitingId);

    // then
    assertThat(response.currentRank()).isEqualTo(3);       // 앞에 2팀
    assertThat(response.totalWaiting()).isEqualTo(3);
    assertThat(response.estimatedWaitMinutes()).isEqualTo(10); // 앞 2팀 × 5분
  }

  @Test
  void execute_취소된_웨이팅_조회시_예외발생() {
    // given
    UUID waitingId = UUID.randomUUID();
    WaitingEntry cancelled = WaitingEntry.restore(
        waitingId, UUID.randomUUID(), "손님", 2, 1, WaitingStatus.CANCELLED, LocalDateTime.now());

    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(cancelled));

    // when & then
    assertThatThrownBy(() -> useCase.execute(waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }
}
