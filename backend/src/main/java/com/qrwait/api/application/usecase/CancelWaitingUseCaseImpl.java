package com.qrwait.api.application.usecase;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.repository.WaitingRepository;
import com.qrwait.api.infrastructure.sse.WaitingSseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelWaitingUseCaseImpl implements CancelWaitingUseCase {

    private final WaitingRepository waitingRepository;
    private final WaitingSseService waitingSseService;

    @Override
    @Transactional
    public void execute(UUID waitingId) {
        WaitingEntry entry = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingNotFoundException(waitingId));

        entry.cancel();
        waitingRepository.save(entry);

        waitingSseService.broadcastUpdate(entry.getStoreId());
    }
}
