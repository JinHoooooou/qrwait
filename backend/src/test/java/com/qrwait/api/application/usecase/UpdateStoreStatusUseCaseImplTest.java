package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.StoreStatus;
import com.qrwait.api.domain.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UpdateStoreStatusUseCaseImplTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  @Mock
  private StoreRepository storeRepository;
  private UpdateStoreStatusUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateStoreStatusUseCaseImpl(storeRepository);
  }

  @Test
  void execute_정상_상태_변경() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    Store updated = store.changeStatus(StoreStatus.CLOSED);
    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));
    given(storeRepository.save(any())).willReturn(updated);

    UpdateStoreStatusRequest request = createRequest(StoreStatus.CLOSED);
    StoreResponse response = useCase.execute(ownerId, storeId, request);

    assertThat(response.status()).isEqualTo(StoreStatus.CLOSED);
  }

  @Test
  void execute_소유권_불일치_예외발생() {
    UUID otherOwnerId = UUID.randomUUID();
    Store store = Store.restore(storeId, otherOwnerId, "남의 매장", "부산", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

    assertThatThrownBy(() -> useCase.execute(ownerId, storeId, createRequest(StoreStatus.CLOSED)))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void execute_존재하지않는_매장_예외발생() {
    given(storeRepository.findById(storeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(ownerId, storeId, createRequest(StoreStatus.CLOSED)))
        .isInstanceOf(StoreNotFoundException.class);
  }

  private UpdateStoreStatusRequest createRequest(StoreStatus status) {
    UpdateStoreStatusRequest request = new UpdateStoreStatusRequest();
    ReflectionTestUtils.setField(request, "status", status);
    return request;
  }
}
