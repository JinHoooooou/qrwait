package com.qrwait.api.infrastructure.persistence;

import com.qrwait.api.domain.model.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "qr_code", unique = true, nullable = false, length = 36)
    private String qrCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private StoreJpaEntity(UUID id, String name, String qrCode, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.qrCode = qrCode;
        this.createdAt = createdAt;
    }

    public static StoreJpaEntity from(Store store) {
        return new StoreJpaEntity(store.getId(), store.getName(), store.getQrCode(), store.getCreatedAt());
    }

    public Store toDomain() {
        return Store.restore(id, name, qrCode, createdAt);
    }
}
