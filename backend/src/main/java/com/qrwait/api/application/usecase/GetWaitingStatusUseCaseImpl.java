package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.WaitingStatusResponse;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetWaitingStatusUseCaseImpl implements GetWaitingStatusUseCase {

    private final WaitingRepository waitingRepository;

    @Override
    @Transactional(readOnly = true)
    public WaitingStatusResponse execute(UUID waitingId) {
        // 1. 웨이팅 조회 (없으면 예외)
        WaitingEntry entry = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingNotFoundException(waitingId));

        // 취소/입장 완료된 웨이팅은 대기 순서 없음
        if (entry.getStatus() != WaitingStatus.WAITING && entry.getStatus() != WaitingStatus.CALLED) {
            throw new WaitingNotFoundException(waitingId);
        }

        // 2. 앞 팀 수 계산 → currentRank 산출
        List<WaitingEntry> waitingList = waitingRepository
                .findByStoreIdAndStatus(entry.getStoreId(), WaitingStatus.WAITING);

        long ahead = waitingList.stream()
                .filter(e -> e.getWaitingNumber() < entry.getWaitingNumber())
                .count();

        int currentRank = (int) ahead + 1;
        int totalWaiting = waitingList.size();

        // 3. 예상 대기시간 산출 (앞 팀 수 × 평균 5분)
        int estimatedWaitMinutes = (int) ahead * 5;

        return new WaitingStatusResponse(currentRank, totalWaiting, estimatedWaitMinutes);
    }
}
