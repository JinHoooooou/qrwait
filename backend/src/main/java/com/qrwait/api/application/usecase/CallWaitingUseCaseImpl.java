package com.qrwait.api.application.usecase;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.StoreNotFoundException;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.repository.StoreRepository;
import com.qrwait.api.domain.repository.WaitingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CallWaitingUseCaseImpl implements CallWaitingUseCase {

  private final WaitingRepository waitingRepository;
  private final StoreRepository storeRepository;

  @Override
  @Transactional
  public void execute(UUID ownerId, UUID waitingId) {
    WaitingEntry entry = waitingRepository.findById(waitingId)
        .orElseThrow(() -> new WaitingNotFoundException(waitingId));

    Store store = storeRepository.findById(entry.getStoreId())
        .orElseThrow(() -> new StoreNotFoundException(entry.getStoreId().toString()));

    if (!store.getOwnerId().equals(ownerId)) {
      throw new StoreNotFoundException(entry.getStoreId().toString());
    }

    entry.call();
    waitingRepository.save(entry);

    // TODO: Phase 4에서 SSE 추가 — 해당 손님 채널에 'called' 이벤트 발송
  }
}
