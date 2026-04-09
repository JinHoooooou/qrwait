package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.StoreResponse;
import com.qrwait.api.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.repository.StoreRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStoreInfoUseCaseImpl implements UpdateStoreInfoUseCase {

  private final StoreRepository storeRepository;

  @Override
  @Transactional
  public StoreResponse execute(UUID ownerId, UpdateStoreInfoRequest request) {
    Store store = storeRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));

    Store updated = storeRepository.save(store.updateInfo(request.getName(), request.getAddress()));
    return new StoreResponse(updated.getId(), updated.getName(), updated.getAddress(), updated.getStatus());
  }
}
