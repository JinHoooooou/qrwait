package com.qrwait.api.application.usecase;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.repository.WaitingRepository;
import com.qrwait.api.infrastructure.sse.WaitingSseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시뮬레이션용 입장 처리 UseCase. WAITING → CALLED → ENTERED 를 한 번에 처리한다. 점주 대시보드 구현 전 SSE 동작 검증 목적으로만 사용한다.
 */
@Service
@RequiredArgsConstructor
public class EnterWaitingUseCaseImpl {

  private final WaitingRepository waitingRepository;
  private final WaitingSseService waitingSseService;

  @Transactional
  public void execute(UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    entry.call();
    entry.enter();
    waitingRepository.save(entry);

    waitingSseService.broadcastUpdate(entry.getStoreId());
  }
}
