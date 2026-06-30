package com.qrwait.api.store.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qrwait.api.shared.qr.QrCodeGenerator;
import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreViewServiceTest {

  private final UUID storeId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  @Mock
  StoreRepository storeRepository;
  @Mock
  QrCodeGenerator qrCodeGenerator;

  StoreViewService storeViewService;

  @BeforeEach
  void setUp() {
    storeViewService = new StoreViewService(storeRepository, qrCodeGenerator);
    ReflectionTestUtils.setField(storeViewService, "baseUrl", "http://localhost:5173");
  }

  @Test
  void getStoreById_정상_조회() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.getById(storeId)).willReturn(store);

    StoreResponse response = storeViewService.getStoreById(storeId);

    assertThat(response.storeId()).isEqualTo(storeId);
  }

  @Test
  void getStoreById_존재하지않는_매장_예외발생() {
    given(storeRepository.getById(storeId)).willThrow(new StoreNotFoundException(storeId));

    assertThatThrownBy(() -> storeViewService.getStoreById(storeId))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void generateQrImage_정상_생성() {
    Store store = Store.restore(storeId, ownerId, "테스트 매장", "서울", StoreStatus.OPEN, LocalDateTime.now());
    given(storeRepository.getById(storeId)).willReturn(store);
    given(qrCodeGenerator.generate("http://localhost:5173/wait?storeId=" + storeId))
        .willReturn(new byte[]{1, 2, 3});

    byte[] image = storeViewService.generateQrImage(storeId);

    assertThat(image).hasSize(3);
  }
}
