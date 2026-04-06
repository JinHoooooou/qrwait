package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreJpaEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "owner_id", columnDefinition = "uuid")
  private UUID ownerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 255)
  private String address;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StoreStatus status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  private StoreJpaEntity(UUID id, UUID ownerId, String name, String address,
      StoreStatus status, LocalDateTime createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.name = name;
    this.address = address;
    this.status = status;
    this.createdAt = createdAt;
  }

  public static StoreJpaEntity from(Store store) {
    return new StoreJpaEntity(store.getId(), store.getOwnerId(), store.getName(),
        store.getAddress(), store.getStatus(), store.getCreatedAt());
  }

  public Store toDomain() {
    return Store.restore(id, ownerId, name, address, status, createdAt);
  }
}
