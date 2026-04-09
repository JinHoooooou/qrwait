package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.DailySummaryResponse;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDailySummaryUseCaseImpl implements GetDailySummaryUseCase {

  private final WaitingRepository waitingRepository;

  @Override
  @Transactional(readOnly = true)
  public DailySummaryResponse execute(UUID storeId) {
    LocalDate today = LocalDate.now();

    long totalRegistered = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.WAITING, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CALLED, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.ENTERED, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.NO_SHOW, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CANCELLED, today);

    long totalEntered = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.ENTERED, today);
    long totalNoShow = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.NO_SHOW, today);
    long totalCancelled = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CANCELLED, today);
    long currentWaiting = waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.WAITING, today)
        + waitingRepository.countByStoreIdAndStatusAndDate(storeId, WaitingStatus.CALLED, today);

    return new DailySummaryResponse(totalRegistered, totalEntered, totalNoShow, totalCancelled, currentWaiting);
  }
}
