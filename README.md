# QR Wait — QR 웨이팅 서비스

앱 설치 없이 QR 스캔만으로 웨이팅 등록 및 실시간 순서 확인이 가능한 웹 기반 서비스입니다.

## 프로젝트 구조

```
qr-wait/
├── backend/                   # Spring Boot 3.x API 서버
├── frontend/                  # React 19 + Vite SPA
├── docker-compose.dev.yml     # 로컬 개발 환경 인프라 (PostgreSQL + Redis)
├── docker-compose.yml         # 전체 스택 배포 (backend + frontend + db + redis)
├── .env.example               # 환경변수 템플릿
└── README.md
```

## 기술 스택

| 구분               | 기술                                                           |
|------------------|--------------------------------------------------------------|
| Backend          | Java 21, Spring Boot 3.5, Spring Data JPA, Spring Data Redis |
| Frontend         | React 19, Vite, TypeScript, Zustand                          |
| Database         | PostgreSQL 15                                                |
| Cache            | Redis 7 (JWT Refresh Token 저장)                              |
| 실시간             | Spring SseEmitter (인메모리 레지스트리 · 단일 인스턴스 전제)      |
| 인프라              | Docker, Docker Compose, Nginx                                |

## 주요 기능

- 점주: 매장명 입력 → QR 코드 즉시 생성
- 사용자: QR 스캔 → 앱 설치 없이 웨이팅 등록
- 웨이팅 번호 발급 및 실시간 대기 순서 확인 (SSE)
- 페이지 새로고침 후에도 웨이팅 정보 유지 (localStorage)
- 웨이팅 취소 기능

---

## 로컬 개발 환경 실행

### 1. 인프라 기동 (PostgreSQL + Redis)

```bash
docker compose -f docker-compose.dev.yml up -d
```

> 기존 로컬 DB가 있다면 `docker compose -f docker-compose.dev.yml down -v` 로 초기화한 뒤 기동하세요 (Flyway가 스키마를 소유하도록 바뀌었습니다).

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun        # local 프로필 자동 적용
```

IntelliJ 사용 시: Run Configuration → Active profiles: `local`

API 서버: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

개발 서버: `http://localhost:5173` (`/api` 요청은 백엔드로 자동 프록시)

---

## 전체 스택 Docker 배포

### 1. 환경변수 파일 생성

```bash
cp .env.example .env
```

`.env` 파일을 열어 아래 값을 채우세요:

| 변수                     | 설명                  | 예시                    |
|------------------------|---------------------|-----------------------|
| `DB_NAME`              | PostgreSQL DB명      | `qrwait`              |
| `DB_USERNAME`          | DB 사용자명             | `qrwait`              |
| `DB_PASSWORD`          | DB 비밀번호             | `yourpassword`        |
| `CORS_ALLOWED_ORIGINS` | 프론트엔드 접근 도메인        | `http://192.168.0.10` |
| `APP_BASE_URL`         | QR 코드에 인코딩될 베이스 URL | `http://192.168.0.10` |
| `JWT_SECRET`           | JWT 서명 시크릿 (Base64, 32바이트 이상) | `openssl rand -base64 32` |
| `PHONE_HASH_SECRET`    | 전화번호 가명처리용 HMAC 비밀키 (Base64, 32바이트 이상) — 교체 시 기존 해시와 매칭이 끊겨 반복 노쇼 이력이 단절됨 | `openssl rand -base64 32` |
| `NHN_SMS_APP_KEY`        | NHN Cloud SMS 프로젝트 AppKey (선택)    | (콘솔에서 발급)          |
| `NHN_SMS_SECRET_KEY`     | NHN Cloud SMS SecretKey (선택)      | (콘솔에서 발급)          |
| `NHN_SMS_SENDER_NUMBER`  | 사전 등록된 발신번호, 하이픈 무관 (선택)          | `01099998888`         |

> `NHN_SMS_*` 세 값은 **선택 사항**입니다. 미설정 시 앱 구동은 정상 진행되며, 손님 호출 시점에 SMS 발송이 실패하고 점주 대시보드 상단에 "⚠️ SMS 발송 실패, 직접 연락해주세요" 배너가 표시됩니다.

### 2. 프로덕션 설정 파일 생성

```bash
cp backend/src/main/resources/application-prod.yml.example backend/src/main/resources/application-prod.yml
```

이 파일은 `.gitignore`에 포함되어 있어 커밋되지 않으며, `docker compose up --build` 시 이미지에 포함되어 `prod` 프로필로 로드됩니다. 최초 1회만 생성하면 됩니다.

### 3. 전체 스택 기동

```bash
docker compose up --build
```

서비스 접속: `http://localhost` (또는 같은 네트워크의 `http://{PC_IP}`)

### 4. 종료

```bash
docker compose down        # 컨테이너만 종료 (데이터 유지)
docker compose down -v     # 컨테이너 + 볼륨 삭제 (데이터 초기화)
```

---

## SMS 알림 (NHN Cloud)

손님이 화면을 닫아도 호출 알림을 받을 수 있도록, 점주가 호출 버튼을 누르는 시점에 NHN Cloud SMS로 손님 전화번호에 문자를 발송합니다.

- 트리거: 점주 호출 (`WAITING` → `CALLED`)
- 발송자: 사전 등록된 발신번호
- 실패 처리: 재시도 없이 점주 대시보드에 SSE 배너로 즉시 알림 (과금 중복 방지)

### 로컬 개발에서의 SMS 테스트

세 가지 방식이 있습니다. 목적에 따라 선택하세요.

#### 방식 A. 실 발송 검증 (본인 폰으로만)

실제 문자가 도착하는지 확인하고 싶을 때:

1. `NHN_SMS_APP_KEY` / `NHN_SMS_SECRET_KEY` / `NHN_SMS_SENDER_NUMBER` 세 값 세팅 후 백엔드 재기동.
2. 손님 등록 화면에서 **본인 폰번호 입력** → 점주 계정으로 로그인해 해당 웨이팅에 "호출" 클릭.
3. 폰에 SMS 도착 확인. NHN Cloud 콘솔의 "발송 결과 조회" 에서 상세 로그 확인 가능.

**주의**: 손님 폰번호는 입력값 그대로 발송되므로, 로컬 테스트 시 반드시 본인 번호만 사용하세요. 회당 SMS 단문 요금(약 9원)이 회사 계정에서 차감됩니다.

#### 방식 B. 실패 경로 검증 (배너 흐름 확인)

발송 실패 시 점주 대시보드 배너가 뜨는지 확인할 때:

1. `NHN_SMS_*` 환경변수를 **비워둔 채로** 백엔드 기동 (기본값).
2. 손님 등록 → 점주 "호출" 클릭.
3. 대시보드 상단에 `⚠️ #N번 손님 SMS 발송 실패. 직접 연락해주세요. (010-XXXX-XXXX)` 배너 표시 확인.

#### 방식 C. 자동화 테스트

`NhnCloudSmsClientTest` 가 MockWebServer 로 성공/실패 응답 시나리오를 검증합니다.

```bash
cd backend && ./gradlew test --tests "*.NhnCloudSmsClientTest"
```

### 관련 스펙

- 설계: [`docs/superpowers/specs/2026-07-03-sms-notification-design.md`](./docs/superpowers/specs/2026-07-03-sms-notification-design.md)
- 구현 플랜: [`docs/superpowers/plans/2026-07-03-sms-notification-implementation.md`](./docs/superpowers/plans/2026-07-03-sms-notification-implementation.md)

---

## 관련 문서

### 현행 문서

| 알고 싶은 것 | 볼 곳 |
|------------|------|
| 백엔드 아키텍처 규칙 (패키지 위치·계층 책임) | [`backend/CLAUDE.md`](./backend/CLAUDE.md) |
| 설계 의사결정 기록 (ADR) | [`backend/BACKEND_DECISIONS.md`](./backend/BACKEND_DECISIONS.md) |
| 기능별 설계 스펙 | [`docs/superpowers/specs/`](./docs/superpowers/specs/) — 파일명 앞 날짜가 최신인 것 |
| 기능별 구현 플랜 | [`docs/superpowers/plans/`](./docs/superpowers/plans/) |
| 수동 QA 시나리오 | [`docs/QA-test-cases.md`](./docs/QA-test-cases.md) |

### 아카이브 (역사 기록 — 구현 근거 아님)

> ⚠️ 아래 v2.0 문서는 **2026-04-03 시점 기록**이며 현행 구현과 여러 곳에서 어긋납니다.
> 참조하기 전에 **[`docs/archive/README.md`](./docs/archive/README.md)의 드리프트 목록을 먼저 확인하세요.**

- [PRD v2.0 (제품 요구사항)](./docs/archive/QRWait_PRD_v2.0.md)
- [TRD v2.0 (기술 요구사항)](./docs/archive/QRWait_TRD_v2.0.md)
- [TASKS v2.0 (구현 체크리스트)](./docs/archive/QRWait_TASKS_v2.0.md)
