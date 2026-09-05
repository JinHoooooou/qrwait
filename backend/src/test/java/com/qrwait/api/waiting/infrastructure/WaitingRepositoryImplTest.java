package com.qrwait.api.waiting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.infrastructure.StoreRepositoryImpl;
import com.qrwait.api.support.IntegrationTestSupport;
import com.qrwait.api.waiting.domain.DailySummary;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StoreRepositoryImpl.class, WaitingRepositoryImpl.class})
@ActiveProfiles("test")
class WaitingRepositoryImplTest extends IntegrationTestSupport {

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private WaitingRepository waitingRepository;

  private Store savedStore;

  @BeforeEach
  void setUp() {
    savedStore = storeRepository.save(Store.create(null, "테스트 식당", null));
  }

  @Test
  void findByStoreIdAndStatus_returnsMatchingEntries() {
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1, LocalDate.now()));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2, LocalDate.now()));

    List<WaitingEntry> result = waitingRepository
        .findByStoreIdAndStatus(savedStore.getId(), LocalDate.now(), WaitingStatus.WAITING);

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(e -> e.getStatus() == WaitingStatus.WAITING);
  }

  @Test
  void countByStoreIdAndStatus_returnsCorrectCount() {
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1, LocalDate.now()));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2, LocalDate.now()));

    int count = waitingRepository.countByStoreIdAndStatus(savedStore.getId(), LocalDate.now(), WaitingStatus.WAITING);

    assertThat(count).isEqualTo(2);
  }

  @Test
  void countByStatusForStoreAndBusinessDate_상태별_집계() {
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-0000-0001", 2, 1, LocalDate.now()));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-0000-0002", 2, 2, LocalDate.now()));

    var counts = waitingRepository.countByStatusForStoreAndBusinessDate(savedStore.getId(), LocalDate.now());

    assertThat(counts.get(WaitingStatus.WAITING)).isEqualTo(2L);
    assertThat(DailySummary.from(counts).getTotalRegistered()).isEqualTo(2L);
  }

  @Test
  void findActiveByStoreId_excludesOtherBusinessDates() {
    LocalDate today = LocalDate.of(2026, 9, 2);
    LocalDate yesterday = LocalDate.of(2026, 9, 1);
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1, yesterday));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 2, 1, today));

    List<WaitingEntry> result = waitingRepository.findActiveByStoreId(savedStore.getId(), today);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getBusinessDate()).isEqualTo(today);
  }

  @Test
  void findNextWaitingNumber_countsPerBusinessDate() {
    LocalDate day1 = LocalDate.of(2026, 9, 1);
    LocalDate day2 = LocalDate.of(2026, 9, 2);
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1, day1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 2, 2, day1));

    assertThat(waitingRepository.findNextWaitingNumber(savedStore.getId(), day1)).isEqualTo(3);
    assertThat(waitingRepository.findNextWaitingNumber(savedStore.getId(), day2)).isEqualTo(1);
  }
}
