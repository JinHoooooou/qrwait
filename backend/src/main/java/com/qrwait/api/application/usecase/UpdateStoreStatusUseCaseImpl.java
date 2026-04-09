package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.repository.StoreRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStoreStatusUseCaseImpl implements UpdateStoreStatusUseCase {

  private final StoreRepository storeRepository;

  @Override
  @Transactional
  public StoreResponse execute(UUID ownerId, UUID storeId, UpdateStoreStatusRequest request) {
    Store store = storeRepository.findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId.toString()));

    if (!store.getOwnerId().equals(ownerId)) {
      throw new StoreNotFoundException(storeId.toString());
    }

    Store updated = storeRepository.save(store.changeStatus(request.getStatus()));

    // TODO: Phase 4에서 SSE 브로드캐스트 추가 — 손님 화면에 매장 상태 변경 이벤트 전송

    return new StoreResponse(updated.getId(), updated.getName(), updated.getAddress(), updated.getStatus());
  }
}
