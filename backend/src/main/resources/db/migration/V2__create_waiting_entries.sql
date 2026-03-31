CREATE TABLE waiting_entries
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    store_id       UUID        NOT NULL REFERENCES stores (id),
    visitor_name   VARCHAR(50) NOT NULL,
    party_size     INT         NOT NULL CHECK (party_size BETWEEN 1 AND 10),
    waiting_number INT         NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at     TIMESTAMP            DEFAULT now()
);

CREATE INDEX idx_waiting_store_status ON waiting_entries (store_id, status);
