# QR Wait — Frontend

QR Wait의 웹 클라이언트. **손님용 웨이팅 화면**과 **점주용 대시보드**를 하나의 SPA로 제공합니다.
전체 서비스 개요와 실행 방법은 [루트 README](../README.md)를 참고하세요.

## 스택

React 19 · Vite 8 · TypeScript 5.9 · React Router 7 · Zustand 5 · axios

## 실행

```bash
npm install
npm run dev        # http://localhost:5173
```

`/api` 요청은 Vite dev server가 백엔드로 프록시합니다. 대상 주소는 `.env.development`의 `VITE_API_TARGET`(기본 `http://localhost:8080`)이며, `vite.config.ts`가 이 값을 읽습니다. 백엔드가 먼저 떠 있어야 합니다.

| 명령 | 설명 |
|------|------|
| `npm run dev` | 개발 서버 (HMR) |
| `npm run build` | `tsc -b` 타입체크 후 `dist/` 프로덕션 번들 |
| `npm run preview` | 빌드 결과 로컬 미리보기 |
| `npm run lint` | ESLint |

## 라우팅

`src/App.tsx`가 라우트를 정의합니다.

### 손님 (비로그인)

| 경로 | 화면 |
|------|------|
| `/wait?storeId={storeId}` | QR 스캔 진입점 — 웨이팅 등록 폼 |
| `/waiting/:waitingId` | 등록 완료 확인 |
| `/waiting/:waitingId/status` | 실시간 대기 순서 (SSE) |
| `/waiting/:waitingId/called` | 호출됨 — 입장 안내 |
| `/waiting/:waitingId/cancel` | 웨이팅 취소 |

### 점주 (JWT 인증)

`/owner/*` 중 로그인이 필요한 경로는 `components/PrivateRoute.tsx`로 감쌉니다. `/`는 로그인 여부에 따라 대시보드 또는 로그인으로 리다이렉트합니다.

| 경로 | 화면 | 인증 |
|------|------|-----|
| `/owner/login`, `/owner/signup` | 로그인 / 회원가입 | — |
| `/owner/onboarding` | 최초 설정 가이드 | ✅ |
| `/owner/dashboard` | 실시간 대기 목록 · 호출/입장/노쇼 처리 | ✅ |
| `/owner/settings` | 매장 정보 · 테이블 수 · 회전시간 · 알림 임계값 | ✅ |
| `/owner/qr-print` | QR 코드 출력 | ✅ |
| `/owner/history` | 당일 웨이팅 이력 | ✅ |

## 구조

```
src/
├── api/          axios 클라이언트
│   ├── client.ts       손님용 (인증 없음, 에러 메시지 정규화)
│   ├── ownerClient.ts  점주용 (Bearer 주입 + 401 자동 갱신)
│   ├── waiting.ts      웨이팅 API
│   └── owner.ts        인증·매장·대시보드 API
├── store/        Zustand
│   ├── waitingStore.ts 손님 웨이팅 상태
│   └── ownerStore.ts   점주 인증 상태 (accessToken · ownerId · storeId)
├── hooks/
│   └── useWaitingSse.ts  SSE 구독 (재연결 최대 3회, 3초 간격)
├── utils/
│   └── session.ts        localStorage 웨이팅 세션 (`qrwait_session`)
├── pages/        화면 13개
└── components/   Button · ErrorMessage · LoadingSpinner · PrivateRoute
```

## 알아둘 동작

- **인증 부트스트랩** — 앱 최초 로드 시 `App.tsx`가 `POST /api/auth/refresh`를 호출해 세션을 복원합니다. 완료 전까지는 아무것도 렌더하지 않아 로그인 화면이 깜빡이지 않습니다. Access Token은 메모리(Zustand)에만 두고, Refresh Token은 HttpOnly 쿠키로 서버가 관리합니다.
- **손님 세션 유지** — 웨이팅 정보는 `localStorage`(`qrwait_session`)에 저장되어 새로고침 후에도 복원됩니다.
- **토큰 자동 갱신** — `ownerClient`가 401을 받으면 `/api/auth/refresh`로 토큰을 갱신하고 원래 요청을 재시도합니다. 갱신이 진행 중일 때 들어온 요청은 큐(`failedQueue`)에 쌓아 두었다가 새 토큰으로 한 번에 재개하므로, 동시 요청이 갱신을 여러 번 호출하지 않습니다. 갱신마저 실패하면 상태를 비우고 로그인으로 보냅니다.
- **실시간 갱신 — 손님** — `useWaitingSse`가 `EventSource`로 `/api/waitings/:id/stream`을 구독합니다. `waiting-updated`는 순서 갱신, `waiting-called`는 자신의 `waitingId`와 일치할 때만 호출 화면으로 전환합니다. 연결 실패 시 3초 간격으로 3회까지 재시도합니다.
- **실시간 갱신 — 점주** — `EventSource`는 `Authorization` 헤더를 실을 수 없으므로, 대시보드는 `fetch` + `ReadableStream` 리더로 `/api/owner/stores/me/dashboard/stream`을 읽고 SSE 프레임을 직접 파싱합니다 (`DashboardPage.tsx`). 손님 쪽과 구현이 다른 것은 이 제약 때문입니다.
- **점주 대시보드 배너** — SMS 발송 실패(`sms-send-failed`) 시 상단에 배너를 띄워 직접 연락을 유도합니다.

## 배포

`Dockerfile`이 멀티스테이지로 빌드하고 Nginx로 서빙합니다. `nginx.conf`가 SPA 폴백(`try_files … /index.html`)과 `/api` 프록시를 담당하며, SSE를 위해 `proxy_buffering off` + `proxy_read_timeout 1800s`(백엔드 SSE 타임아웃 30분과 일치)를 설정합니다.

프로덕션 환경변수는 `.env.production.example`을 `.env.production`으로 복사해 채웁니다. (`.env.production`은 `.gitignore` 대상)

## 알려진 공백

- **테스트 하네스 없음.** 테스트 러너가 아직 도입되지 않았습니다 — `docs/superpowers/specs/2026-08-03-testcontainers-integration-design.md`에서 명시적으로 범위 밖으로 둔 항목입니다. `useWaitingSse`의 재연결 로직과 `session.ts` 복원 경로가 우선 대상입니다.
