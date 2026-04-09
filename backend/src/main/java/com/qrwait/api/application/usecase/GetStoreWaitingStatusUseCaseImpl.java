package com.qrwait.api.application.usecase;

import com.qrwait.api.waiting.application.dto.WaitingStatusResponse;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
