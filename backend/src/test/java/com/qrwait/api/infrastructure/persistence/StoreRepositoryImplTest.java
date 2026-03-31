package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, StoreRepositoryImpl.class})
class StoreRepositoryImplTest {

    @Autowired
    private StoreRepositoryImpl storeRepository;

    @Test
    void findByQrCode_whenExists_returnsStore() {
        // given
        Store store = Store.create("테스트 식당");
        storeRepository.save(store);

        // when
        Optional<Store> result = storeRepository.findByQrCode(store.getQrCode());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("테스트 식당");
        assertThat(result.get().getQrCode()).isEqualTo(store.getQrCode());
    }

    @Test
    void findByQrCode_whenNotExists_returnsEmpty() {
        // when
        Optional<Store> result = storeRepository.findByQrCode("non-existent-qr");

        // then
        assertThat(result).isEmpty();
    }
}
