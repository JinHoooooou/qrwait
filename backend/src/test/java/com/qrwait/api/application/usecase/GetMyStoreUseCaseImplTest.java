package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.application.dto.StoreResponse;
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

@ExtendWith(MockitoExtension.class)
class GetMyStoreUseCaseImplTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();
  @Mock
  private StoreRepository storeRepository;
  private GetMyStoreUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetMyStoreUseCaseImpl(storeRepository);
  }

  @Test
  void execute_정상_매장_조회() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울시 강남구", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));

    StoreResponse response = useCase.execute(ownerId);

    assertThat(response.storeId()).isEqualTo(storeId);
    assertThat(response.name()).isEqualTo("테스트 매장");
    assertThat(response.address()).isEqualTo("서울시 강남구");
    assertThat(response.status()).isEqualTo(StoreStatus.OPEN);
  }

  @Test
  void execute_매장_없음_예외발생() {
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(ownerId))
        .isInstanceOf(StoreNotFoundException.class);
  }
}
