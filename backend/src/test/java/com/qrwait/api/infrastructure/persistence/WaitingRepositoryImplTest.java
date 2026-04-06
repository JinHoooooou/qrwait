package com.qrwait.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.domain.model.Store;
import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingStatus;
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
  private StoreRepositoryImpl storeRepository;

  @Autowired
  private WaitingRepositoryImpl waitingRepository;

  private Store savedStore;

  @BeforeEach
  void setUp() {
    savedStore = storeRepository.save(Store.create(null, "테스트 식당", null));
  }

  @Test
  void findByStoreIdAndStatus_returnsMatchingEntries() {
    // given
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "손님A", 2, 1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "손님B", 3, 2));

    // when
    List<WaitingEntry> result = waitingRepository.findByStoreIdAndStatus(savedStore.getId(), WaitingStatus.WAITING);

    // then
    assertThat(result).hasSize(2);
    assertThat(result).allMatch(e -> e.getStatus() == WaitingStatus.WAITING);
  }

  @Test
  void countByStoreIdAndStatus_returnsCorrectCount() {
    // given
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "손님A", 2, 1));
    waitingRepository.save(WaitingEntry.create(savedStore.getId(), "손님B", 3, 2));

    // when
    int count = waitingRepository.countByStoreIdAndStatus(savedStore.getId(), WaitingStatus.WAITING);

    // then
    assertThat(count).isEqualTo(2);
  }
}
