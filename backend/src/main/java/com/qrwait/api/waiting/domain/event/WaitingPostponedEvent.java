package com.qrwait.api.waiting.domain.event;

import java.util.UUID;

public record WaitingPostponedEvent(UUID storeId, UUID waitingId) {

}
