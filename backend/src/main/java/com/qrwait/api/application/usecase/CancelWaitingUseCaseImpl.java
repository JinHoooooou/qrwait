package com.qrwait.api.application.usecase;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.repository.WaitingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelWaitingUseCaseImpl implements CancelWaitingUseCase {

    private final WaitingRepository waitingRepository;

    @Override
    @Transactional
    public void execute(UUID waitingId) {
        // 1. 웨이팅 조회
        WaitingEntry entry = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingNotFoundException(waitingId));

        // 2. 도메인 메서드로 취소 (잘못된 상태 전이 시 IllegalStateException)
        entry.cancel();

        // 3. 변경된 상태 저장
        waitingRepository.save(entry);

        // 4. SSE 브로드캐스트 트리거 — Phase 3에서 연결 예정
    }
}
