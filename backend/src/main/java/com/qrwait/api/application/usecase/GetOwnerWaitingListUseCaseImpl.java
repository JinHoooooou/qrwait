package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.OwnerWaitingResponse;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOwnerWaitingListUseCaseImpl implements GetOwnerWaitingListUseCase {

  private final WaitingRepository waitingRepository;

  @Override
  @Transactional(readOnly = true)
  public List<OwnerWaitingResponse> execute(UUID storeId) {
    return waitingRepository.findActiveByStoreId(storeId).stream()
        .map(this::toResponse)
        .toList();
  }

  private OwnerWaitingResponse toResponse(WaitingEntry entry) {
    long elapsedMinutes = ChronoUnit.MINUTES.between(entry.getCreatedAt(), LocalDateTime.now());
    return new OwnerWaitingResponse(
        entry.getId(),
        entry.getWaitingNumber(),
        entry.getVisitorName(),
        entry.getPartySize(),
        entry.getStatus(),
        elapsedMinutes
    );
  }
}
