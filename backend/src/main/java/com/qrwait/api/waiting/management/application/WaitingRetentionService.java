package com.qrwait.api.waiting.management.application;

import com.qrwait.api.shared.privacy.PhoneHasher;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영업일이 지난 웨이팅의 전화번호를 가명처리한다. 전체 번호를 뒤 4자리로 줄이고 HMAC 해시를 심는다.
 *
 * <p>매장마다 영업일 경계가 달라 몰아서 돌릴 공통 기준 시각이 없으므로 매시 정각에 순회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRetentionService {

  private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(5, 0);

  /**
   * 한 번의 배치 실행이 매장당 가명처리할 최대 건수. 배포 직후 첫 실행처럼 밀린 이력이 많을 때
   * 하나의 트랜잭션이 전체를 처리하지 않도록 상한을 둔다. 배치는 멱등하고 매시간 다시 돌므로
   * 잔량은 다음 실행에서 마저 처리된다.
   */
  private static final int BATCH_LIMIT = 500;

  private final StoreRepository storeRepository;
  private final StoreSettingsRepository storeSettingsRepository;
  private final WaitingRepository waitingRepository;
  private final PhoneHasher phoneHasher;

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void pseudonymizeExpired() {
    LocalDateTime now = LocalDateTime.now();
    int total = 0;
    for (Store store : storeRepository.findAll()) {
      total += pseudonymizeStore(store.getId(), currentBusinessDate(store.getId(), now));
    }
    if (total > 0) {
      log.info("전화번호 가명처리 완료 — {}건", total);
    }
  }

  private int pseudonymizeStore(UUID storeId, LocalDate currentBusinessDate) {
    List<WaitingEntry> targets =
        waitingRepository.findPseudonymizationTargets(storeId, currentBusinessDate, BATCH_LIMIT);
    for (WaitingEntry entry : targets) {
      waitingRepository.save(entry.pseudonymize(phoneHasher.hash(entry.getPhoneNumber())));
    }
    return targets.size();
  }

  private LocalDate currentBusinessDate(UUID storeId, LocalDateTime now) {
    return storeSettingsRepository.findByStoreId(storeId)
        .map(s -> s.businessDateOf(now))
        .orElseGet(() -> now.toLocalTime().isBefore(DEFAULT_OPEN_TIME)
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate());
  }
}
