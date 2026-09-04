package com.qrwait.api.waiting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaitingEntryTest {

  @Test
  void belongsTo_같은_매장이면_true() {
    UUID storeId = UUID.randomUUID();
    WaitingEntry entry = WaitingEntry.restore(
        UUID.randomUUID(), storeId, "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    assertThat(entry.belongsTo(storeId)).isTrue();
  }

  @Test
  void belongsTo_다른_매장이면_false() {
    WaitingEntry entry = WaitingEntry.restore(
        UUID.randomUUID(), UUID.randomUUID(), "010-1111-0001", 2, 1,
        WaitingStatus.WAITING, LocalDateTime.now(),
        LocalDate.now(), null, null, null);

    assertThat(entry.belongsTo(UUID.randomUUID())).isFalse();
  }

  private WaitingEntry waiting(WaitingStatus status, LocalDateTime calledAt) {
    return WaitingEntry.restore(
        UUID.randomUUID(), UUID.randomUUID(), "010-1111-0001", 2, 1,
        status, LocalDateTime.of(2026, 9, 1, 12, 0),
        LocalDate.of(2026, 9, 1), calledAt, null, null);
  }

  @Test
  void create_는_전달받은_영업일을_보존한다() {
    WaitingEntry entry = WaitingEntry.create(
        UUID.randomUUID(), "010-1111-0001", 2, 1, LocalDate.of(2026, 9, 1));

    assertThat(entry.getBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(entry.getCalledAt()).isNull();
    assertThat(entry.getEnteredAt()).isNull();
    assertThat(entry.getPhoneHash()).isNull();
  }

  @Test
  void call_은_호출_시각을_기록한다() {
    WaitingEntry called = waiting(WaitingStatus.WAITING, null).call();

    assertThat(called.getStatus()).isEqualTo(WaitingStatus.CALLED);
    assertThat(called.getCalledAt()).isNotNull();
  }

  @Test
  void enter_는_입장_시각을_기록한다() {
    WaitingEntry entered = waiting(WaitingStatus.CALLED, LocalDateTime.now()).enter();

    assertThat(entered.getStatus()).isEqualTo(WaitingStatus.ENTERED);
    assertThat(entered.getEnteredAt()).isNotNull();
  }

  @Test
  void postpone_CALLED를_WAITING으로_되돌리고_호출시각을_지운다() {
    WaitingEntry postponed = waiting(WaitingStatus.CALLED, LocalDateTime.now()).postpone();

    assertThat(postponed.getStatus()).isEqualTo(WaitingStatus.WAITING);
    assertThat(postponed.getCalledAt()).isNull();
  }

  @Test
  void postpone_번호와_순서는_유지된다() {
    WaitingEntry before = waiting(WaitingStatus.CALLED, LocalDateTime.now());

    WaitingEntry after = before.postpone();

    assertThat(after.getWaitingNumber()).isEqualTo(before.getWaitingNumber());
    assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
  }

  @Test
  void postpone_CALLED가_아니면_예외() {
    WaitingEntry entry = waiting(WaitingStatus.WAITING, null);

    assertThatThrownBy(entry::postpone)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CALLED");
  }

  @Test
  void isGraceExpired_유예_경과_후_true() {
    LocalDateTime calledAt = LocalDateTime.of(2026, 9, 1, 12, 0);
    WaitingEntry entry = waiting(WaitingStatus.CALLED, calledAt);

    assertThat(entry.isGraceExpired(calledAt.plusMinutes(5).plusSeconds(1), 5)).isTrue();
  }

  @Test
  void isGraceExpired_유예_정각에는_false() {
    LocalDateTime calledAt = LocalDateTime.of(2026, 9, 1, 12, 0);
    WaitingEntry entry = waiting(WaitingStatus.CALLED, calledAt);

    assertThat(entry.isGraceExpired(calledAt.plusMinutes(5), 5)).isFalse();
  }

  @Test
  void isGraceExpired_CALLED가_아니면_항상_false() {
    WaitingEntry entry = waiting(WaitingStatus.WAITING, null);

    assertThat(entry.isGraceExpired(LocalDateTime.now(), 5)).isFalse();
  }

  @Test
  void pseudonymize_뒤_4자리와_해시만_남긴다() {
    WaitingEntry entry = waiting(WaitingStatus.ENTERED, LocalDateTime.now());

    WaitingEntry result = entry.pseudonymize("abc123");

    assertThat(result.getPhoneNumber()).isEqualTo("0001");
    assertThat(result.getPhoneHash()).isEqualTo("abc123");
    assertThat(result.isPseudonymized()).isTrue();
  }

  @Test
  void pseudonymize_통계에_쓰이는_필드는_보존한다() {
    WaitingEntry entry = waiting(WaitingStatus.ENTERED, LocalDateTime.now());

    WaitingEntry result = entry.pseudonymize("abc123");

    assertThat(result.getWaitingNumber()).isEqualTo(entry.getWaitingNumber());
    assertThat(result.getPartySize()).isEqualTo(entry.getPartySize());
    assertThat(result.getStatus()).isEqualTo(entry.getStatus());
    assertThat(result.getBusinessDate()).isEqualTo(entry.getBusinessDate());
  }

  @Test
  void pseudonymize_두_번_해도_4자리가_더_잘리지_않는다() {
    WaitingEntry once = waiting(WaitingStatus.ENTERED, LocalDateTime.now()).pseudonymize("h1");

    WaitingEntry twice = once.pseudonymize("h2");

    assertThat(twice.getPhoneNumber()).isEqualTo("0001");
  }

  @Test
  void call_가명처리된_항목이면_예외() {
    WaitingEntry entry = waiting(WaitingStatus.WAITING, null).pseudonymize("abc123");

    assertThatThrownBy(entry::call)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("가명처리");
  }
}
