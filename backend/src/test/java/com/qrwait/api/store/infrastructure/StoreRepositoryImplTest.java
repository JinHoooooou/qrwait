package com.qrwait.api.store.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qrwait.api.owner.domain.Owner;
import com.qrwait.api.owner.domain.OwnerRepository;
import com.qrwait.api.owner.infrastructure.OwnerRepositoryImpl;
import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.support.IntegrationTestSupport;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StoreRepositoryImpl.class, OwnerRepositoryImpl.class})
@ActiveProfiles("test")
class StoreRepositoryImplTest extends IntegrationTestSupport {

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private OwnerRepository ownerRepository;

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
    Owner owner = ownerRepository.save(Owner.create("owner@qrwait.com", "hashed"));
    Store saved = storeRepository.save(Store.create(owner.getId(), "가게", "주소"));

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
