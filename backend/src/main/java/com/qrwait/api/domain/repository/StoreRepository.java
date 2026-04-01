package com.qrwait.api.domain.repository;

import com.qrwait.api.domain.model.Store;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

  Optional<Store> findById(UUID id);

  Store save(Store store);
}
