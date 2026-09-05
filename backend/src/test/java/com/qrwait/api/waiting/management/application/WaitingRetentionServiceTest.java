package com.qrwait.api.waiting.management.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.support.IntegrationTestSupport;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingRetentionServiceTest extends IntegrationTestSupport {

  @Autowired
  private WaitingRetentionService retentionService;

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private StoreSettingsRepository storeSettingsRepository;

  @Autowired
  private WaitingRepository waitingRepository;

  private UUID storeId;

  @BeforeEach
  void setUp() {
    Store store = storeRepository.save(Store.create(null, "가명처리 테스트 식당", null));
    storeId = store.getId();
    storeSettingsRepository.save(StoreSettings.createDefault(storeId));
  }

  @Test
  void 지난_영업일_건만_가명처리하고_당일_건은_보존한다() {
    LocalDate businessToday = StoreSettings.createDefault(storeId).businessDateOf(LocalDateTime.now());
    LocalDate yesterday = businessToday.minusDays(1);
    WaitingEntry old = waitingRepository.save(
        WaitingEntry.create(storeId, "010-1234-5678", 2, 1, yesterday));
    WaitingEntry today = waitingRepository.save(
        WaitingEntry.create(storeId, "010-9999-8888", 2, 1, businessToday));

    retentionService.pseudonymizeExpired();

    WaitingEntry reloadedOld = waitingRepository.findById(old.getId()).orElseThrow();
    WaitingEntry reloadedToday = waitingRepository.findById(today.getId()).orElseThrow();

    assertThat(reloadedOld.getPhoneNumber()).isEqualTo("5678");
    assertThat(reloadedOld.getPhoneHash()).isNotNull();
    assertThat(reloadedToday.getPhoneNumber()).isEqualTo("010-9999-8888");
    assertThat(reloadedToday.getPhoneHash()).isNull();
  }

  @Test
  void 두_번_실행해도_결과가_같다() {
    LocalDate businessToday = StoreSettings.createDefault(storeId).businessDateOf(LocalDateTime.now());
    LocalDate yesterday = businessToday.minusDays(1);
    WaitingEntry old = waitingRepository.save(
        WaitingEntry.create(storeId, "010-1234-5678", 2, 1, yesterday));

    retentionService.pseudonymizeExpired();
    retentionService.pseudonymizeExpired();

    assertThat(waitingRepository.findById(old.getId()).orElseThrow().getPhoneNumber())
        .isEqualTo("5678");
  }
}
