package com.qrwait.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.domain.model.Store;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, StoreRepositoryImpl.class})
class StoreRepositoryImplTest {

  @Autowired
  private StoreRepositoryImpl storeRepository;

  @Test
  void findById_whenExists_returnsStore() {
    Store store = Store.create("테스트 식당");
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
}
