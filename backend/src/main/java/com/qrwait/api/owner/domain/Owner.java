package com.qrwait.api.owner.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Owner {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final LocalDateTime createdAt;

  private Owner(UUID id, String email, String passwordHash, LocalDateTime createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public static Owner create(String email, String passwordHash) {
    return new Owner(UUID.randomUUID(), email, passwordHash, LocalDateTime.now());
  }

  public static Owner restore(UUID id, String email, String passwordHash, LocalDateTime createdAt) {
    return new Owner(id, email, passwordHash, createdAt);
  }
}
