package com.qrwait.api.store.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreSettingsTest {

  private StoreSettings settings(int tableCount, int avgTurnoverMinutes, LocalTime openTime) {
    return StoreSettings.restore(
        UUID.randomUUID(), UUID.randomUUID(), tableCount, avgTurnoverMinutes,
        openTime, null, 10, true, 5);
  }

  @Test
  void businessDateOf_영업_시작_이전이면_전날() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 4, 59));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void businessDateOf_영업_시작_정각이면_당일() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 5, 0));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 2));
  }

  @Test
  void businessDateOf_자정_직후는_전날_영업일() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 0, 10));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void businessDateOf_영업중_정오는_당일() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    LocalDate result = s.businessDateOf(LocalDateTime.of(2026, 9, 2, 12, 0));

    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 2));
  }

  @Test
  void calculateEstimatedWait_절사되지_않는다() {
    // 30분 / 7테이블 = 4.28분. 곱셈을 먼저 하지 않으면 4분으로 잘려 10팀에 40분이 된다.
    StoreSettings s = settings(7, 30, LocalTime.of(5, 0));

    assertThat(s.calculateEstimatedWait(10)).isEqualTo(43);
  }

  @Test
  void calculateEstimatedWait_테이블이_회전시간보다_많아도_0이_아니다() {
    // 기존 공식은 30/40 = 0 이 되어 항상 0분을 반환했다.
    StoreSettings s = settings(40, 30, LocalTime.of(5, 0));

    assertThat(s.calculateEstimatedWait(20)).isEqualTo(15);
  }

  @Test
  void calculateEstimatedWait_앞에_아무도_없으면_0() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThat(s.calculateEstimatedWait(0)).isZero();
  }

  @Test
  void update_테이블_수가_0이면_예외() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThatThrownBy(() -> s.update(0, 30, null, null, 10, true, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("테이블 수");
  }

  @Test
  void update_유예_시간이_음수면_예외() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThatThrownBy(() -> s.update(5, 30, LocalTime.of(9, 0), null, 10, true, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("유예");
  }

  @Test
  void update_영업_시작_시각이_null이면_예외() {
    StoreSettings s = settings(5, 30, LocalTime.of(5, 0));

    assertThatThrownBy(() -> s.update(5, 30, null, null, 10, true, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("영업 시작 시각");
  }

  @Test
  void createDefault_기본값은_05시_5분() {
    StoreSettings s = StoreSettings.createDefault(UUID.randomUUID());

    assertThat(s.getOpenTime()).isEqualTo(LocalTime.of(5, 0));
    assertThat(s.getCallGraceMinutes()).isEqualTo(5);
  }
}
