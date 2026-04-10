package com.qrwait.api.owner.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.qrwait.api.owner.domain.Owner;
import com.qrwait.api.owner.domain.OwnerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FlywayAutoConfiguration.class, OwnerRepositoryImpl.class})
class OwnerRepositoryImplTest {

  @Autowired
  private OwnerRepository ownerRepository;

  @Test
  void findByEmail_존재하는_이메일_정상조회() {
    Owner owner = Owner.create("test@qrwait.com", "hashed-password");
    ownerRepository.save(owner);

    Optional<Owner> result = ownerRepository.findByEmail("test@qrwait.com");

    assertThat(result).isPresent();
    assertThat(result.get().getEmail()).isEqualTo("test@qrwait.com");
    assertThat(result.get().getPasswordHash()).isEqualTo("hashed-password");
  }

  @Test
  void findByEmail_존재하지않는_이메일_empty반환() {
    Optional<Owner> result = ownerRepository.findByEmail("notfound@qrwait.com");

    assertThat(result).isEmpty();
  }
}
