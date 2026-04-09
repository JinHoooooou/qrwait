package com.qrwait.api.application.usecase;

import com.qrwait.api.application.dto.RegisterWaitingRequest;
import com.qrwait.api.application.dto.RegisterWaitingResponse;
import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotAvailableException;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.StoreStatus;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterWaitingUseCaseImpl implements RegisterWaitingUseCase {

    private final StoreRepository storeRepository;
    private final WaitingRepository waitingRepository;

    @Override
    @Transactional
    public RegisterWaitingResponse execute(UUID storeId, RegisterWaitingRequest request) {
      // 1. 매장 존재 여부 및 상태 검증
      Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
      if (store.getStatus() != StoreStatus.OPEN) {
        throw new StoreNotAvailableException(store.getStatus());
      }

        // 2. 다음 웨이팅 번호 계산
        int waitingNumber = waitingRepository.findNextWaitingNumber(storeId);

        // 3. WaitingEntry 생성 및 저장
        WaitingEntry entry = WaitingEntry.create(
                storeId, request.getVisitorName(), request.getPartySize(), waitingNumber);
        WaitingEntry saved = waitingRepository.save(entry);

        // 4. 현재 대기 순서 및 예상 대기시간 산출 (대기팀 수 × 평균 5분)
        int totalWaiting = waitingRepository.countByStoreIdAndStatus(storeId, WaitingStatus.WAITING);
        int estimatedWaitMinutes = totalWaiting * 5;

        // 5. waitingToken 생성 (UUID v4)
        String waitingToken = UUID.randomUUID().toString();

        return new RegisterWaitingResponse(
                saved.getId(),
                waitingNumber,
                totalWaiting,   // currentRank = 전체 대기 중 내 순서 (마지막에 등록됐으므로 totalWaiting과 동일)
                totalWaiting,
                estimatedWaitMinutes,
                waitingToken
        );
    }
}
