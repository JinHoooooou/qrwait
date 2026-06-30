package com.qrwait.api.store.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, StoreRepositoryImpl.class})
@ActiveProfiles("test")
class StoreRepositoryImplTest {

  @Autowired
  private StoreRepository storeRepository;

  @Test
  void findById_whenExists_returnsStore() {
    Store store = Store.create(null, "테스트 식당", null);
    Store saved = storeRepository.save(store);

    Optional<Store> result = storeRepository.findById(saved.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("테스트 식당");
    assertThat(result.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  void findById_whenNotExists_returnsEmpty() {
    Optional<Store> result = storeRepository.findById(UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  void getByOwnerId_매장_있으면_반환() {
    Store saved = storeRepository.save(Store.create(UUID.randomUUID(), "가게", "주소"));

    Store found = storeRepository.getByOwnerId(saved.getOwnerId());

    assertThat(found.getId()).isEqualTo(saved.getId());
  }

  @Test
  void getByOwnerId_매장_없으면_예외() {
    assertThatThrownBy(() -> storeRepository.getByOwnerId(UUID.randomUUID()))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  void getById_매장_없으면_예외() {
    assertThatThrownBy(() -> storeRepository.getById(UUID.randomUUID()))
        .isInstanceOf(StoreNotFoundException.class);
  }
}
