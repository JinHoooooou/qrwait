package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Owner;
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
@Table(name = "owners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerJpaEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  private OwnerJpaEntity(UUID id, String email, String passwordHash, LocalDateTime createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public static OwnerJpaEntity from(Owner owner) {
    return new OwnerJpaEntity(owner.getId(), owner.getEmail(), owner.getPasswordHash(), owner.getCreatedAt());
  }

  public Owner toDomain() {
    return Owner.restore(id, email, passwordHash, createdAt);
  }
}
