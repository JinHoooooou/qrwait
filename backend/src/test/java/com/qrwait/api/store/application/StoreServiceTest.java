package com.qrwait.api.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.store.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreStatus;
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
class StoreServiceTest {

  private final UUID ownerId = UUID.randomUUID();
  private final UUID storeId = UUID.randomUUID();

  @Mock
  StoreRepository storeRepository;

  StoreService storeService;

  @BeforeEach
  void setUp() {
    storeService = new StoreService(storeRepository);
    ReflectionTestUtils.setField(storeService, "baseUrl", "http://localhost:5173");
  }

  // ===== getMyStore =====

  @Test
  void getMyStore_정상_조회() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울시 강남구", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));

    StoreResponse response = storeService.getMyStore(ownerId);

    assertThat(response.storeId()).isEqualTo(storeId);
    assertThat(response.name()).isEqualTo("테스트 매장");
    assertThat(response.status()).isEqualTo(StoreStatus.OPEN);
  }

  @Test
  void getMyStore_매장_없음_예외발생() {
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> storeService.getMyStore(ownerId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  // ===== updateStoreStatus =====

  @Test
  void updateStoreStatus_정상_상태_변경() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    Store updated = store.changeStatus(StoreStatus.CLOSED);
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
    given(storeRepository.save(any())).willReturn(updated);

    UpdateStoreStatusRequest request = new UpdateStoreStatusRequest();
    ReflectionTestUtils.setField(request, "status", StoreStatus.CLOSED);

    StoreResponse response = storeService.updateStoreStatus(ownerId, request);

    assertThat(response.status()).isEqualTo(StoreStatus.CLOSED);
  }

  @Test
  void updateStoreStatus_매장_없음_예외발생() {
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.empty());

    UpdateStoreStatusRequest request = new UpdateStoreStatusRequest();
    ReflectionTestUtils.setField(request, "status", StoreStatus.CLOSED);

    assertThatThrownBy(() -> storeService.updateStoreStatus(ownerId, request))
        .isInstanceOf(StoreNotFoundException.class);
  }

  // ===== updateStoreInfo =====

  @Test
  void updateStoreInfo_정상_수정() {
    Store store = Store.restore(storeId, ownerId, "기존 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    Store updated = store.updateInfo("수정 매장", "부산");
    given(storeRepository.findByOwnerId(ownerId)).willReturn(Optional.of(store));
    given(storeRepository.save(any())).willReturn(updated);

    UpdateStoreInfoRequest request = new UpdateStoreInfoRequest();
    ReflectionTestUtils.setField(request, "name", "수정 매장");
    ReflectionTestUtils.setField(request, "address", "부산");

    StoreResponse response = storeService.updateStoreInfo(ownerId, request);

    assertThat(response.name()).isEqualTo("수정 매장");
    assertThat(response.address()).isEqualTo("부산");
  }

  // ===== getStoreById =====

  @Test
  void getStoreById_정상_조회() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

    StoreResponse response = storeService.getStoreById(storeId);

    assertThat(response.storeId()).isEqualTo(storeId);
  }

  @Test
  void getStoreById_존재하지않는_매장_예외발생() {
    given(storeRepository.findById(storeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> storeService.getStoreById(storeId))
        .isInstanceOf(StoreNotFoundException.class);
  }
}
