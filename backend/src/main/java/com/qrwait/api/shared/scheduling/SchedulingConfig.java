package com.qrwait.api.shared.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 인프라를 활성화한다. {@code test} 프로필에서는 등록하지 않는다 — 공유
 * Testcontainers 컨테이너를 쓰는 통합 테스트가 실행 중 정각을 넘기면 배치(예: 전화번호
 * 가명처리)가 실제로 발화해 다른 테스트가 커밋해 둔 데이터를 오염시킬 수 있기 때문이다.
 * {@code @Scheduled} 메서드를 가진 서비스 빈 자체는 그대로 등록되므로, 테스트가 그 메서드를
 * 직접 호출하는 것은 영향받지 않는다.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {

}
