package com.qrwait.api.domain.model;

import java.util.UUID;

public class WaitingNotFoundException extends RuntimeException {

    public WaitingNotFoundException(UUID waitingId) {
        super("웨이팅을 찾을 수 없습니다. waitingId=" + waitingId);
    }
}
