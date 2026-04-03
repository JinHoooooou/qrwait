# QR Wait — QR 웨이팅 서비스

앱 설치 없이 QR 스캔만으로 웨이팅 등록 및 실시간 순서 확인이 가능한 웹 기반 서비스입니다.

## 프로젝트 구조

```
qr-wait/
├── backend/                   # Spring Boot 3.x API 서버
├── frontend/                  # React 18 + Vite SPA
├── docker-compose.dev.yml     # 로컬 개발 환경 인프라 (PostgreSQL + Redis)
├── docker-compose.yml         # 전체 스택 배포 (backend + frontend + db + redis)
├── .env.example               # 환경변수 템플릿
└── README.md
```

## 기술 스택

| 구분               | 기술                                                           |
|------------------|--------------------------------------------------------------|
| Backend          | Java 21, Spring Boot 3.5, Spring Data JPA, Spring Data Redis |
| Frontend         | React 18, Vite, TypeScript, Zustand                          |
| Database         | PostgreSQL 15                                                |
| Cache / Realtime | Redis 7, Spring SseEmitter                                   |
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

### 2. 전체 스택 기동

```bash
docker compose up --build
```

서비스 접속: `http://localhost` (또는 같은 네트워크의 `http://{PC_IP}`)

### 3. 종료

```bash
docker compose down        # 컨테이너만 종료 (데이터 유지)
docker compose down -v     # 컨테이너 + 볼륨 삭제 (데이터 초기화)
```

---

## 관련 문서

- [PRD (제품 요구사항)](./QRWait_PRD_v1.0.md)
- [TRD (기술 요구사항)](./QRWait_TRD_v1.0.md)
- [TASKS (구현 체크리스트)](./QRWait_TASKS_v1.0.md)
