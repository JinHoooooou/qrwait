package com.qrwait.api.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.qrwait.api.domain.model.WaitingEntry;
import com.qrwait.api.domain.model.WaitingNotFoundException;
import com.qrwait.api.domain.model.WaitingStatus;
import com.qrwait.api.domain.repository.WaitingRepository;
import com.qrwait.api.shared.sse.WaitingSseService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelWaitingUseCaseImplTest {

    @Mock WaitingRepository waitingRepository;
  @Mock
  WaitingSseService waitingSseService;
    @InjectMocks CancelWaitingUseCaseImpl useCase;

    @Test
    void execute_정상취소_상태가_CANCELLED로_변경됨() {
        // given
        UUID waitingId = UUID.randomUUID();
        WaitingEntry entry = WaitingEntry.restore(
                waitingId, UUID.randomUUID(), "손님", 2, 1, WaitingStatus.WAITING, LocalDateTime.now());

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));
        given(waitingRepository.save(entry)).willReturn(entry);

        // when
        useCase.execute(waitingId);

        // then
        assertThat(entry.getStatus()).isEqualTo(WaitingStatus.CANCELLED);
        then(waitingRepository).should().save(entry);
    }

    @Test
    void execute_존재하지않는_waitingId_예외발생() {
        // given
        UUID waitingId = UUID.randomUUID();
        given(waitingRepository.findById(waitingId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> useCase.execute(waitingId))
                .isInstanceOf(WaitingNotFoundException.class);
    }

    @Test
    void execute_이미취소된_웨이팅_재취소시_예외발생() {
        // given
        UUID waitingId = UUID.randomUUID();
        WaitingEntry entry = WaitingEntry.restore(
                waitingId, UUID.randomUUID(), "손님", 2, 1, WaitingStatus.CANCELLED, LocalDateTime.now());

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(entry));

        // when & then
        assertThatThrownBy(() -> useCase.execute(waitingId))
                .isInstanceOf(IllegalStateException.class);
    }
}
