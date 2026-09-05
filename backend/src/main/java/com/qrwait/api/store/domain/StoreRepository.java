package com.qrwait.api.store.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

  Optional<Store> findById(UUID id);

  Optional<Store> findByOwnerId(UUID ownerId);

  Store save(Store store);

  List<Store> findAll();

  default Store getByOwnerId(UUID ownerId) {
    return findByOwnerId(ownerId)
        .orElseThrow(() -> new StoreNotFoundException("ownerId=" + ownerId));
  }

  default Store getById(UUID storeId) {
    return findById(storeId)
        .orElseThrow(() -> new StoreNotFoundException(storeId));
  }
}
