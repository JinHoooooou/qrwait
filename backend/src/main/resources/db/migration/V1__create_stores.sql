CREATE TABLE stores
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100)        NOT NULL,
    qr_code    VARCHAR(36) UNIQUE  NOT NULL,
    created_at TIMESTAMP           DEFAULT now()
);
