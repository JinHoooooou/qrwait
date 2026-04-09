package com.qrwait.api.store.domain;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

  Optional<Store> findById(UUID id);

  Optional<Store> findByOwnerId(UUID ownerId);

  Store save(Store store);
}
