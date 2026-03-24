# QR Wait — QR 웨이팅 서비스

앱 설치 없이 QR 스캔만으로 웨이팅 등록 및 실시간 순서 확인이 가능한 웹 기반 서비스입니다.

## 프로젝트 구조

```
qr-wait/
├── backend/        # Spring Boot 3.x API 서버
├── frontend/       # React 18 + Vite SPA
├── docker-compose.dev.yml   # 로컬 개발 환경 (PostgreSQL + Redis)
├── docker-compose.yml       # 프로덕션 배포
└── README.md
```

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Data Redis |
| Frontend | React 18, Vite 5.x, TypeScript, Zustand |
| Database | PostgreSQL 15 |
| Cache / Realtime | Redis 7, Spring SseEmitter |
| 인프라 | Docker, Docker Compose |

## 로컬 실행 방법

### 1. 개발 환경 인프라 실행

```bash
docker compose -f docker-compose.dev.yml up -d
```

PostgreSQL (`localhost:5432`) 및 Redis (`localhost:6379`)가 실행됩니다.

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

API 서버가 `http://localhost:8080`에서 실행됩니다.

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

개발 서버가 `http://localhost:5173`에서 실행됩니다. `/api` 요청은 백엔드로 프록시됩니다.

## 주요 기능 (MVP)

- QR 스캔 후 브라우저에서 웨이팅 등록 (앱 설치 불필요)
- 웨이팅 번호 발급 및 실시간 대기 순서 확인 (SSE)
- 페이지 새로고침 시에도 웨이팅 정보 유지

## 관련 문서

- [PRD (제품 요구사항)](./QRWait_PRD_v1.0.md)
- [TRD (기술 요구사항)](./QRWait_TRD_v1.0.md)
- [TASKS (구현 체크리스트)](./QRWait_TASKS_v1.0.md)
