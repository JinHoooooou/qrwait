package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.StoreStatus;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallWaitingUseCaseImplTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  private final UUID waitingId = UUID.randomUUID();
  @Mock
  private WaitingRepository waitingRepository;
  @Mock
  private StoreRepository storeRepository;
  private CallWaitingUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new CallWaitingUseCaseImpl(waitingRepository, storeRepository);
  }

  @Test
  void execute_정상_호출처리() {
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "김철수", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
    given(waitingRepository.save(any())).willReturn(entry);

    useCase.execute(ownerId, waitingId);

    verify(waitingRepository).save(any());
  }

  @Test
  void execute_소유권_불일치_예외발생() {
    UUID otherOwnerId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(waitingId, storeId, "김철수", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());
    Store store = Store.restore(storeId, otherOwnerId, "남의 매장", "부산", StoreStatus.OPEN, LocalDateTime.now());
    given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

    assertThatThrownBy(() -> useCase.execute(ownerId, waitingId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void execute_존재하지않는_대기_예외발생() {
    given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(ownerId, waitingId))
        .isInstanceOf(WaitingNotFoundException.class);
  }
}
