package com.qrwait.api.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaitingEntryTest {

  @Test
  void belongsTo_같은_매장이면_true() {
    UUID storeId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(
        UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now());

    assertThat(entry.belongsTo(storeId)).isTrue();
  }

  @Test
  void belongsTo_다른_매장이면_false() {
    WaitingEntry entry = WaitingEntry.restore(
        UUID.randomUUID(), UUID.randomUUID(), "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now());

    assertThat(entry.belongsTo(UUID.randomUUID())).isFalse();
  }
}
