package com.qrwait.api.waiting.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public class WaitingEntry {

  private final UUID id;
  private final UUID storeId;
  private final String phoneNumber;
  private final int partySize;
  private final int waitingNumber;
  private final LocalDateTime createdAt;
  private final WaitingStatus status;

  private WaitingEntry(UUID id, UUID storeId, String phoneNumber, int partySize, int waitingNumber, WaitingStatus status, LocalDateTime createdAt) {
    this.id = id;
    this.storeId = storeId;
    this.phoneNumber = phoneNumber;
    this.partySize = partySize;
    this.waitingNumber = waitingNumber;
    this.status = status;
    this.createdAt = createdAt;
  }

  /**
   * 신규 웨이팅 등록
   */
  public static WaitingEntry create(UUID storeId, String phoneNumber, int partySize, int waitingNumber) {
    return new WaitingEntry(
        UUID.randomUUID(),
        storeId,
        phoneNumber,
        partySize,
        waitingNumber,
        WaitingStatus.WAITING,
        LocalDateTime.now()
    );
  }

  /**
   * 영속 계층에서 복원
   */
  public static WaitingEntry restore(
      UUID id, UUID storeId,
      String phoneNumber,
      int partySize,
      int waitingNumber,
      WaitingStatus status,
      LocalDateTime createdAt
  ) {
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, status, createdAt);
  }

  // ===== 도메인 상태 전이 =====

  /**
   * WAITING → CALLED
   */
  public WaitingEntry call() {
    if (status != WaitingStatus.WAITING) {
      throw new IllegalStateException(
          "call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.CALLED, createdAt);
  }

  /**
   * CALLED → ENTERED
   */
  public WaitingEntry enter() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "enter() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.ENTERED, createdAt);
  }

  /**
   * WAITING | CALLED → CANCELLED
   */
  public WaitingEntry cancel() {
    if (status != WaitingStatus.WAITING && status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "cancel() 은 WAITING 또는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.CANCELLED, createdAt);
  }

  /**
   * CALLED → NO_SHOW
   */
  public WaitingEntry noShow() {
    if (status != WaitingStatus.CALLED) {
      throw new IllegalStateException(
          "noShow() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
    }
    return new WaitingEntry(id, storeId, phoneNumber, partySize, waitingNumber, WaitingStatus.NO_SHOW, createdAt);
  }
}
