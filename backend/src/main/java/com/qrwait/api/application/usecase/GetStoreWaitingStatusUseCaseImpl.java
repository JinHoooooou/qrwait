package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStoreWaitingStatusUseCaseImpl implements GetStoreWaitingStatusUseCase {

    private final WaitingRepository waitingRepository;

    @Override
    @Transactional(readOnly = true)
    public WaitingStatusResponse execute(UUID storeId) {
        int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
        int estimatedWaitMinutes = totalWaiting * 5;

        return new WaitingStatusResponse(totalWaiting, totalWaiting, estimatedWaitMinutes);
    }
}
