package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.OwnerWaitingResponse;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetOwnerWaitingListUseCaseImplTest {

  private final UUID storeId = UUID.randomUUID();
  @Mock
  private WaitingRepository waitingRepository;
  private GetOwnerWaitingListUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetOwnerWaitingListUseCaseImpl(waitingRepository);
  }

  @Test
  void execute_대기_목록_반환() {
    WaitingEntry waiting = WaitingEntry.restore(UUID.randomUUID(), storeId, "김철수", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now().minusMinutes(10));
    WaitingEntry called = WaitingEntry.restore(UUID.randomUUID(), storeId, "이영희", 3, 2,
        WaitingStatus.CALLED, LocalDateTime.now().minusMinutes(5));
    given(waitingRepository.findActiveByStoreId(storeId)).willReturn(List.of(waiting, called));

    List<OwnerWaitingResponse> result = useCase.execute(storeId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).visitorName()).isEqualTo("김철수");
    assertThat(result.get(0).status()).isEqualTo(WaitingStatus.WAITING);
    assertThat(result.get(0).elapsedMinutes()).isGreaterThanOrEqualTo(10);
    assertThat(result.get(1).visitorName()).isEqualTo("이영희");
    assertThat(result.get(1).status()).isEqualTo(WaitingStatus.CALLED);
  }

  @Test
  void execute_활성_대기_없음_빈_목록_반환() {
    given(waitingRepository.findActiveByStoreId(storeId)).willReturn(List.of());

    List<OwnerWaitingResponse> result = useCase.execute(storeId);

    assertThat(result).isEmpty();
  }
}
