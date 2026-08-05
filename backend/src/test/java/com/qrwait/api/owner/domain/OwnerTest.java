package com.qrwait.api.owner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerTest {

  @Test
  void changePassword_새_해시로_새_Owner를_반환하고_나머지_필드는_보존한다() {
    UUID id = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.now();
    Owner owner = Owner.restore(id, "owner@test.com", "old-hash", createdAt);

    Owner changed = owner.changePassword("new-hash");

    assertThat(changed.getPasswordHash()).isEqualTo("new-hash");
    assertThat(changed.getId()).isEqualTo(id);
    assertThat(changed.getEmail()).isEqualTo("owner@test.com");
    assertThat(changed.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void changePassword_원본_Owner는_변경되지_않는다() {
    Owner owner = Owner.restore(UUID.randomUUID(), "owner@test.com", "old-hash", LocalDateTime.now());

    owner.changePassword("new-hash");

    assertThat(owner.getPasswordHash()).isEqualTo("old-hash");
  }
}
