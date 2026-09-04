package com.qrwait.api.waiting.management.dto;

import com.qrwait.api.shared.privacy.PhoneNumberMasker;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record TodayWaitingResponse(
    UUID waitingId,
    int waitingNumber,
    String phoneNumber,
    int partySize,
    WaitingStatus status,
    LocalDateTime createdAt,
    Long waitedMinutes
) {

  /** 등록 → 입장까지 실제로 걸린 시간. 입장하지 않은 건은 null. */
  public static TodayWaitingResponse from(WaitingEntry entry) {
    Long waited = entry.getEnteredAt() == null
        ? null
        : ChronoUnit.MINUTES.between(entry.getCreatedAt(), entry.getEnteredAt());
    return new TodayWaitingResponse(
        entry.getId(),
        entry.getWaitingNumber(),
        PhoneNumberMasker.mask(entry.getPhoneNumber()),
        entry.getPartySize(),
        entry.getStatus(),
        entry.getCreatedAt(),
        waited
    );
  }
}
