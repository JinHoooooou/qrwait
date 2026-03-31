package com.qrwait.api.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class Store {

    private final UUID id;
    private final String name;
    private final String qrCode;
    private final LocalDateTime createdAt;

    private Store(UUID id, String name, String qrCode, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.qrCode = qrCode;
        this.createdAt = createdAt;
    }

    /** 신규 매장 생성 — qrCode는 내부에서 UUID v4로 자동 생성 */
    public static Store create(String name) {
        return new Store(
                UUID.randomUUID(),
                name,
                UUID.randomUUID().toString(),
                LocalDateTime.now()
        );
    }

    /** 영속 계층에서 복원할 때 사용 */
    public static Store restore(UUID id, String name, String qrCode, LocalDateTime createdAt) {
        return new Store(id, name, qrCode, createdAt);
    }

}
