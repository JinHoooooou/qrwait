package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.DailySummaryResponse;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDailySummaryUseCaseImplTest {

  private final UUID storeId = UUID.randomUUID();
  @Mock
  private WaitingRepository waitingRepository;
  private GetDailySummaryUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetDailySummaryUseCaseImpl(waitingRepository);
  }

  @Test
  void execute_일별_통계_집계() {
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.WAITING), any())).willReturn(3L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.CALLED), any())).willReturn(1L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.ENTERED), any())).willReturn(5L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.NO_SHOW), any())).willReturn(2L);
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), eq(WaitingStatus.CANCELLED), any())).willReturn(1L);

    DailySummaryResponse response = useCase.execute(storeId);

    assertThat(response.totalRegistered()).isEqualTo(12); // 3+1+5+2+1
    assertThat(response.totalEntered()).isEqualTo(5);
    assertThat(response.totalNoShow()).isEqualTo(2);
    assertThat(response.totalCancelled()).isEqualTo(1);
    assertThat(response.currentWaiting()).isEqualTo(4); // 3+1
  }

  @Test
  void execute_데이터_없을_때_모두_0() {
    given(waitingRepository.countByStoreIdAndStatusAndDate(eq(storeId), any(), any())).willReturn(0L);

    DailySummaryResponse response = useCase.execute(storeId);

    assertThat(response.totalRegistered()).isZero();
    assertThat(response.currentWaiting()).isZero();
  }
}
