package com.qrwait.api.waiting.domain.event;

import java.util.UUID;

public record WaitingCalledEvent(UUID storeId, UUID waitingId) {

}
