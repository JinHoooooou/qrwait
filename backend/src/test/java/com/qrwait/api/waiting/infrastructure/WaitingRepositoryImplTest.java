package com.qrwait.api.waiting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.infrastructure.StoreRepositoryImpl;
import com.qrwait.api.waiting.domain.WaitingEntry;
import com.qrwait.api.waiting.domain.WaitingRepository;
import com.qrwait.api.waiting.domain.WaitingStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, StoreRepositoryImpl.class, WaitingRepositoryImpl.class})
class WaitingRepositoryImplTest {

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
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2));

    List<WaitingEntry> result = waitingRepository.findByStoreIdAndStatus(savedStore.getId(), WaitingStatus.WAITING);

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(e -> e.getStatus() == WaitingStatus.WAITING);
  }

  @Test
  void countByStoreIdAndStatus_returnsCorrectCount() {
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-1111-1111", 2, 1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "010-2222-2222", 3, 2));

    int count = waitingRepository.countByStoreIdAndStatus(savedStore.getId(), WaitingStatus.WAITING);

    assertThat(count).isEqualTo(2);
  }
}
