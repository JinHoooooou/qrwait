package com.qrwait.api.waiting.infrastructure;

import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingStatus;
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
@Table(name = "waiting_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingEntryJpaEntity {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "store_id", nullable = false, columnDefinition = "uuid")
  private UUID storeId;

  @Column(name = "phone_number", nullable = false, length = 20)
  private String phoneNumber;

  @Column(name = "party_size", nullable = false)
  private int partySize;

  @Column(name = "waiting_number", nullable = false)
  private int waitingNumber;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  private WaitingEntryJpaEntity(UUID id, UUID storeId, String phoneNumber, int partySize,
      int waitingNumber, String status, LocalDateTime createdAt) {
    this.id = id;
    this.storeId = storeId;
    this.phoneNumber = phoneNumber;
    this.partySize = partySize;
    this.waitingNumber = waitingNumber;
    this.status = status;
    this.createdAt = createdAt;
  }

  public static WaitingEntryJpaEntity from(WaitingEntry entry) {
    return new WaitingEntryJpaEntity(
        entry.getId(),
        entry.getStoreId(),
        entry.getPhoneNumber(),
        entry.getPartySize(),
        entry.getWaitingNumber(),
        entry.getStatus().name(),
        entry.getCreatedAt()
    );
  }

  public WaitingEntry toDomain() {
    return WaitingEntry.restore(
        id,
        storeId,
        phoneNumber,
        partySize,
        waitingNumber,
        WaitingStatus.valueOf(status),
        createdAt
    );
  }
}
