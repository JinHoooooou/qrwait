package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  // TODO 1-5: owner_id, address, status 컬럼 추가 후 아래 필드 활성화
  // @Column(name = "owner_id", columnDefinition = "uuid")
  // private UUID ownerId;
  // @Column(length = 255)
  // private String address;
  // @Column(nullable = false, length = 20)
  // private String status;

  private StoreJpaEntity(UUID id, String name, LocalDateTime createdAt) {
    this.id = id;
    this.name = name;
    this.createdAt = createdAt;
  }

  public static StoreJpaEntity from(Store store) {
    return new StoreJpaEntity(store.getId(), store.getName(), store.getCreatedAt());
  }

  public Store toDomain() {
    // TODO 1-5: DB 컬럼 추가 후 ownerId, address, status를 실제 값으로 교체
    return Store.restore(id, null, name, null, StoreStatus.OPEN, createdAt);
  }
}
