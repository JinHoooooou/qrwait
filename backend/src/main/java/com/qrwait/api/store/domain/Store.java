package com.qrwait.api.store.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Store {

  private final UUID id;
  private final UUID ownerId;
  private final String name;
  private final String address;
  private final StoreStatus status;
  private final LocalDateTime createdAt;

  private Store(UUID id, UUID ownerId, String name, String address,
      StoreStatus status, LocalDateTime createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.name = name;
    this.address = address;
    this.status = status;
    this.createdAt = createdAt;
  }

  public static Store create(UUID ownerId, String name, String address) {
    return new Store(UUID.randomUUID(), ownerId, name, address, StoreStatus.OPEN, LocalDateTime.now());
  }

  public static Store restore(UUID id, UUID ownerId, String name, String address,
      StoreStatus status, LocalDateTime createdAt) {
    return new Store(id, ownerId, name, address, status, createdAt);
  }

  public Store changeStatus(StoreStatus newStatus) {
    return Store.restore(id, ownerId, name, address, newStatus, createdAt);
  }

  public Store updateInfo(String newName, String newAddress) {
    return Store.restore(id, ownerId, newName, newAddress, status, createdAt);
  }
}
