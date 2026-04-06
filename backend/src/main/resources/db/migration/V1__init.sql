CREATE TABLE stores
(
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name       VARCHAR(100) NOT NULL,
  created_at TIMESTAMP        DEFAULT now()
);

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

-- 개발 환경 전용 시드 데이터
INSERT INTO stores (id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', '맛있는 한식당', now()),
       ('00000000-0000-0000-0000-000000000002', '행복한 분식집', now()),
       ('00000000-0000-0000-0000-000000000003', '즐거운 카페', now());
