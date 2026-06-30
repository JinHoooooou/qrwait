package com.qrwait.api.store.application;

import com.qrwait.api.store.application.dto.StoreResponse;
import com.qrwait.api.store.application.dto.UpdateStoreInfoRequest;
import com.qrwait.api.store.application.dto.UpdateStoreStatusRequest;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.event.StoreStatusChangedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {

  private final StoreRepository storeRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public StoreResponse getMyStore(UUID ownerId) {
    return toResponse(storeRepository.getByOwnerId(ownerId));
  }

  @Transactional
  public StoreResponse updateStoreInfo(UUID ownerId, UpdateStoreInfoRequest request) {
    Store store = storeRepository.getByOwnerId(ownerId);
    Store updated = storeRepository.save(store.updateInfo(request.getName(), request.getAddress()));
    return toResponse(updated);
  }

  @Transactional
  public StoreResponse updateStoreStatus(UUID ownerId, UpdateStoreStatusRequest request) {
    Store store = storeRepository.getByOwnerId(ownerId);
    Store updated = storeRepository.save(store.changeStatus(request.getStatus()));
    eventPublisher.publishEvent(new StoreStatusChangedEvent(updated.getId(), updated.getStatus()));
    return toResponse(updated);
  }

  private StoreResponse toResponse(Store store) {
    return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getStatus());
  }
}
