-- =============================================
-- owners
-- =============================================
CREATE TABLE owners
(
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255)        NOT NULL,
  created_at    TIMESTAMP        DEFAULT now()
);

-- =============================================
-- stores
-- =============================================
CREATE TABLE stores
(
  id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
  owner_id   UUID REFERENCES owners (id),
  name       VARCHAR(100) NOT NULL,
  address    VARCHAR(255),
  status     VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP            DEFAULT now()
);

CREATE INDEX idx_stores_owner ON stores (owner_id);

-- =============================================
-- waiting_entries
-- =============================================
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

-- =============================================
-- store_settings
-- =============================================
CREATE TABLE store_settings
(
  id                   UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
  store_id             UUID UNIQUE NOT NULL REFERENCES stores (id),
  table_count          INT         NOT NULL DEFAULT 5,
  avg_turnover_minutes INT         NOT NULL DEFAULT 30,
  open_time            TIME,
  close_time           TIME,
  alert_threshold      INT         NOT NULL DEFAULT 10,
  alert_enabled        BOOLEAN     NOT NULL DEFAULT true
);

-- =============================================
-- 개발 환경 전용 시드 데이터
-- =============================================
INSERT INTO owners (id, email, password_hash, created_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'dev@qrwait.com', 'dev-password-hash', now());

INSERT INTO stores (id, owner_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', '맛있는 한식당', now()),
       ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000', '행복한 분식집', now()),
       ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000000', '즐거운 카페', now());
