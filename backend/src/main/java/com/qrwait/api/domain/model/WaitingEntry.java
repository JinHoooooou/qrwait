package com.qrwait.api.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class WaitingEntry {

    private final UUID id;
    private final UUID storeId;
    private final String visitorName;
    private final int partySize;
    private final int waitingNumber;
    private WaitingStatus status;
    private final LocalDateTime createdAt;

    private WaitingEntry(UUID id, UUID storeId, String visitorName, int partySize,
                         int waitingNumber, WaitingStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.storeId = storeId;
        this.visitorName = visitorName;
        this.partySize = partySize;
        this.waitingNumber = waitingNumber;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** 신규 웨이팅 등록 */
    public static WaitingEntry create(UUID storeId, String visitorName, int partySize, int waitingNumber) {
        return new WaitingEntry(
                UUID.randomUUID(),
                storeId,
                visitorName,
                partySize,
                waitingNumber,
                WaitingStatus.WAITING,
                LocalDateTime.now()
        );
    }

    /** 영속 계층에서 복원 */
    public static WaitingEntry restore(UUID id, UUID storeId, String visitorName, int partySize,
                                       int waitingNumber, WaitingStatus status, LocalDateTime createdAt) {
        return new WaitingEntry(id, storeId, visitorName, partySize, waitingNumber, status, createdAt);
    }

    // ===== 도메인 상태 전이 =====

    /** WAITING → CALLED */
    public void call() {
        if (status != WaitingStatus.WAITING) {
            throw new IllegalStateException(
                    "call() 은 WAITING 상태에서만 가능합니다. 현재 상태: " + status);
        }
        this.status = WaitingStatus.CALLED;
    }

    /** CALLED → ENTERED */
    public void enter() {
        if (status != WaitingStatus.CALLED) {
            throw new IllegalStateException(
                    "enter() 는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
        }
        this.status = WaitingStatus.ENTERED;
    }

    /** WAITING | CALLED → CANCELLED */
    public void cancel() {
        if (status != WaitingStatus.WAITING && status != WaitingStatus.CALLED) {
            throw new IllegalStateException(
                    "cancel() 은 WAITING 또는 CALLED 상태에서만 가능합니다. 현재 상태: " + status);
        }
        this.status = WaitingStatus.CANCELLED;
    }
}
