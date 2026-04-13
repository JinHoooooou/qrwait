package com.qrwait.api.store.domain.event;

import com.qrwait.api.store.domain.StoreStatus;
import java.util.UUID;

public record StoreStatusChangedEvent(UUID storeId, StoreStatus status) {

}
