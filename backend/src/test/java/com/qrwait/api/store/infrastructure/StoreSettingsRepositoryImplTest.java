package com.qrwait.api.store.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, StoreRepositoryImpl.class, StoreSettingsRepositoryImpl.class})
class StoreSettingsRepositoryImplTest {

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private StoreSettingsRepository storeSettingsRepository;

  @Test
  void findByStoreId_정상조회() {
    Store store = storeRepository.save(Store.create(null, "테스트 식당", null));
    StoreSettings settings = StoreSettings.createDefault(store.getId());
    storeSettingsRepository.save(settings);

    Optional<StoreSettings> result = storeSettingsRepository.findByStoreId(store.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getStoreId()).isEqualTo(store.getId());
    assertThat(result.get().getTableCount()).isEqualTo(5);
    assertThat(result.get().getAvgTurnoverMinutes()).isEqualTo(30);
    assertThat(result.get().getAlertThreshold()).isEqualTo(10);
    assertThat(result.get().isAlertEnabled()).isTrue();
  }
}
