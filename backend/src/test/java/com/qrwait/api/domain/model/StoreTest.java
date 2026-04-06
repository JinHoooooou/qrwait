package com.qrwait.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoreTest {

  @Test
  void changeStatus_새_객체_반환_원본_불변() {
    Store original = Store.create(null, "테스트 식당", null);
    assertThat(original.getStatus()).isEqualTo(StoreStatus.OPEN);

    Store changed = original.changeStatus(StoreStatus.BREAK);

    assertThat(changed.getStatus()).isEqualTo(StoreStatus.BREAK);
    assertThat(original.getStatus()).isEqualTo(StoreStatus.OPEN); // 원본 불변 확인
  }

  @Test
  void changeStatus_모든_상태_전이_가능() {
    Store store = Store.create(null, "테스트 식당", null);

    assertThat(store.changeStatus(StoreStatus.FULL).getStatus()).isEqualTo(StoreStatus.FULL);
    assertThat(store.changeStatus(StoreStatus.BREAK).getStatus()).isEqualTo(StoreStatus.BREAK);
    assertThat(store.changeStatus(StoreStatus.CLOSED).getStatus()).isEqualTo(StoreStatus.CLOSED);
    assertThat(store.changeStatus(StoreStatus.OPEN).getStatus()).isEqualTo(StoreStatus.OPEN);
  }

  @Test
  void changeStatus_필드_보존() {
    Store original = Store.create(null, "테스트 식당", "서울시 강남구");

    Store changed = original.changeStatus(StoreStatus.CLOSED);

    assertThat(changed.getId()).isEqualTo(original.getId());
    assertThat(changed.getName()).isEqualTo(original.getName());
    assertThat(changed.getAddress()).isEqualTo(original.getAddress());
    assertThat(changed.getCreatedAt()).isEqualTo(original.getCreatedAt());
  }

  @Test
  void calculateEstimatedWait_정상_계산() {
    // avgTurnoverMinutes=30, tableCount=5 → 팀당 6분
    StoreSettings settings = StoreSettings.createDefault(null); // tableCount=5, avgTurnoverMinutes=30

    assertThat(settings.calculateEstimatedWait(3)).isEqualTo(18); // 30/5*3 = 18
    assertThat(settings.calculateEstimatedWait(0)).isEqualTo(0);
    assertThat(settings.calculateEstimatedWait(1)).isEqualTo(6);  // 30/5*1 = 6
  }
}
