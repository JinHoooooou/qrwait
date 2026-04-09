package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.repository.StoreRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetMyStoreUseCaseImpl implements GetMyStoreUseCase {

  private final StoreRepository storeRepository;

  @Override
  @Transactional(readOnly = true)
  public StoreResponse execute(UUID ownerId) {
    Store store = storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
    return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getStatus());
  }
}
