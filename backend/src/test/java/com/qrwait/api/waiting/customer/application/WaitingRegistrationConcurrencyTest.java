package com.qrwait.api.waiting.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;

import com.qrwait.api.store.domain.Store;
import com.qrwait.api.store.domain.StoreRepository;
import com.qrwait.api.store.domain.StoreSettings;
import com.qrwait.api.store.domain.StoreSettingsRepository;
import com.qrwait.api.support.IntegrationTestSupport;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingRequest;
import com.qrwait.api.waiting.customer.dto.RegisterWaitingResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class WaitingRegistrationConcurrencyTest extends IntegrationTestSupport {

  private static final int CONCURRENCY = 10;

  @Autowired
  private WaitingService waitingService;

  @Autowired
  private StoreRepository storeRepository;

  @Autowired
  private StoreSettingsRepository storeSettingsRepository;

  // 재시도가 실제로 발생했는지 — 즉 열 스레드가 정말로 겹쳐 채번 경합을 만들었는지 — 를
  // 호출 횟수로 직접 단언하기 위한 스파이. 실제 구현은 그대로 위임한다.
  @MockitoSpyBean
  private WaitingRegistrar waitingRegistrar;

  private UUID storeId;

  @BeforeEach
  void setUp() {
    // owner_id는 FK(owners.id)를 참조하므로, 존재하지 않는 임의 UUID 대신 다른 통합 테스트와 동일하게
    // null(소유자 없음)로 저장한다.
    Store store = storeRepository.save(Store.create(null, "동시성 테스트 식당", null));
    storeId = store.getId();
    storeSettingsRepository.save(StoreSettings.createDefault(storeId));
  }

  @Test
  void 동시_등록에도_대기번호가_중복되지_않는다() throws Exception {
    // CyclicBarrier로 스타팅 게이트를 만든다. pool.invokeAll()만으로는 스레드가 게으르게 시작되고
    // 첫 작업이 Hibernate·커넥션 풀 워밍업을 떠안아, 열 번의 register가 실제로 겹친다는 보장이 없다.
    // 모든 스레드가 이 배리어에 도달할 때까지 등록 호출 직전에서 대기시켜 진짜 동시 경합을 강제한다.
    CyclicBarrier startGate = new CyclicBarrier(CONCURRENCY);
    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
    try {
      List<Callable<RegisterWaitingResponse>> tasks = IntStream.range(0, CONCURRENCY)
          .mapToObj(i -> (Callable<RegisterWaitingResponse>) () -> {
            RegisterWaitingRequest request = new RegisterWaitingRequest();
            request.setPhoneNumber("010-1111-%04d".formatted(i));
            request.setPartySize(2);
            startGate.await();
            return waitingService.register(storeId, request);
          })
          .toList();

      List<Integer> numbers = pool.invokeAll(tasks).stream()
          .map(WaitingRegistrationConcurrencyTest::get)
          .map(RegisterWaitingResponse::waitingNumber)
          .sorted()
          .toList();

      assertThat(numbers).containsExactlyElementsOf(
          IntStream.rangeClosed(1, CONCURRENCY).boxed().toList());

      // 재시도가 "동작한다"와 "충돌이 아예 없었다"를 구분한다: 직렬화되어 경합이 한 번도 없었다면
      // registerOnce 호출 횟수는 정확히 CONCURRENCY와 같다. CONCURRENCY보다 많다는 것은 최소 한 번
      // 이상의 유니크 제약 위반과 재시도가 실제로 일어났다는 뜻이다.
      then(waitingRegistrar).should(atLeast(CONCURRENCY + 1))
          .registerOnce(eq(storeId), any(RegisterWaitingRequest.class));
    } finally {
      pool.shutdown();
    }
  }

  private static <T> T get(Future<T> future) {
    try {
      return future.get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
