package com.qrwait.api.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Store {

  private final UUID id;
  private final String name;
  private final LocalDateTime createdAt;

  private Store(UUID id, String name, LocalDateTime createdAt) {
    this.id = id;
    this.name = name;
    this.createdAt = createdAt;
  }

  public static Store create(String name) {
    return new Store(UUID.randomUUID(), name, LocalDateTime.now());
  }

  /**
   * 영속 계층에서 복원할 때 사용
   */
  public static Store restore(UUID id, String name, LocalDateTime createdAt) {
    return new Store(id, name, createdAt);
  }

}
