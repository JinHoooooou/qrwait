# QR Wait — QA 테스트 케이스

> 지금까지 개발된 기능을 수동으로 검증하기 위한 QA 시나리오 모음.
> 코드(컨트롤러·도메인·DTO·프론트 페이지) 기준으로 작성됨.
> 표기: ✅ 정상 케이스 · ⚠️ 예외/검증 케이스 · 🔁 실시간(SSE) 케이스

---

## 0. 사전 준비 / 테스트 환경

| 항목          | 값                                                                                         |
|-------------|-------------------------------------------------------------------------------------------|
| 백엔드         | `cd backend && ./gradlew bootRun` (PostgreSQL + Redis 필요)                                 |
| 프론트엔드       | `cd frontend && npm run dev`                                                              |
| 손님 진입 URL   | `/wait?storeId={storeId}`                                                                 |
| 점주 진입 URL   | `/owner/login`, `/owner/signup`                                                           |
| 개발 시드 매장 ID | `00000000-0000-0000-0000-000000000001` (맛있는 한식당)<br>`...002` (행복한 분식집), `...003` (즐거운 카페) |

> ⚠️ 로컬은 JPA `ddl-auto`로 스키마를 관리하므로 Flyway 시드(`V1__init.sql`)가 적용되지 않을 수 있다.
> 시드 매장이 없으면 점주 회원가입(TC-AUTH-01)으로 매장을 먼저 만들고, 응답의 `storeId`를 손님 테스트에 사용한다.

### 공통 에러 응답 포맷

```json
{
  "code": "STRING_CODE",
  "message": "설명"
}
```

| 코드                          | HTTP | 발생 상황                      |
|-----------------------------|------|----------------------------|
| `INVALID_REQUEST`           | 400  | Bean Validation 실패         |
| `INVALID_CREDENTIALS`       | 401  | 이메일/비밀번호 불일치               |
| `DUPLICATE_EMAIL`           | 409  | 이미 가입된 이메일                 |
| `STORE_NOT_FOUND`           | 404  | 매장 없음 / 타 점주 리소스 접근(존재 은닉) |
| `WAITING_NOT_FOUND`         | 404  | 웨이팅 없음 / 종료된 웨이팅 조회        |
| `STORE_NOT_AVAILABLE`       | 409  | 매장이 OPEN이 아닐 때 등록 시도       |
| `INVALID_STATUS_TRANSITION` | 409  | 허용되지 않은 상태 전이              |
| `QR_GENERATION_FAILED`      | 500  | QR 생성 실패                   |

---

## 1. 점주 인증 (회원가입 / 로그인 / 토큰)

`POST /api/auth/signup` · `POST /api/auth/login` · `POST /api/auth/logout` · `POST /api/auth/refresh`

| ID            | 시나리오                | 전제조건            | 단계                                                    | 예상결과                                                                                                       | 비고                                   |
|---------------|---------------------|-----------------|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------|--------------------------------------|
| TC-AUTH-01 ✅  | 회원가입 성공             | 미가입 이메일         | `/owner/signup`에서 이메일·비밀번호(8자↑)·비밀번호확인·매장명·주소 입력 후 가입 | 201, 응답에 `ownerId`·`storeId`·`qrUrl`. 자동 로그인 후 `/owner/onboarding` 이동                                      | Store(OPEN)·StoreSettings(기본값) 동시 생성 |
| TC-AUTH-02 ⚠️ | 중복 이메일 가입           | 동일 이메일 이미 가입됨   | 같은 이메일로 재가입 시도                                        | 409 `DUPLICATE_EMAIL`                                                                                      |                                      |
| TC-AUTH-03 ⚠️ | 비밀번호 8자 미만          | -               | 비밀번호 7자 이하 입력                                         | 400 `INVALID_REQUEST` (프론트에서도 사전 차단)                                                                       | `@Size(min=8)`                       |
| TC-AUTH-04 ⚠️ | 이메일 형식 오류           | -               | `abc@`, `abc` 등 입력                                    | 400 `INVALID_REQUEST`                                                                                      | `@Email`                             |
| TC-AUTH-05 ⚠️ | 비밀번호 확인 불일치         | -               | 비밀번호 / 확인 값 다르게 입력                                    | 프론트 폼에서 가입 차단 (요청 전송 안 됨)                                                                                  | 클라이언트 검증                             |
| TC-AUTH-06 ✅  | 로그인 성공              | 가입된 계정          | `/owner/login`에서 이메일·비밀번호 입력                          | 200, 응답 바디에 `accessToken`. `refresh_token` HttpOnly 쿠키(Path=`/api/auth/refresh`) 설정. `/owner/dashboard` 이동 |                                      |
| TC-AUTH-07 ⚠️ | 잘못된 비밀번호            | 가입된 계정          | 틀린 비밀번호로 로그인                                          | 401 `INVALID_CREDENTIALS`                                                                                  |                                      |
| TC-AUTH-08 ⚠️ | 미가입 이메일 로그인         | -               | 존재하지 않는 이메일로 로그인                                      | 401 `INVALID_CREDENTIALS` (이메일 존재 은닉)                                                                      |                                      |
| TC-AUTH-09 ✅  | 토큰 갱신               | 로그인 상태(쿠키 보유)   | `POST /api/auth/refresh` 호출                           | 200, 새 `accessToken` 반환                                                                                    | 앱 최초 로드 시 자동 호출(App.tsx)             |
| TC-AUTH-10 ⚠️ | 무효 Refresh로 갱신      | 로그아웃/만료 상태      | 쿠키 없거나 위조된 토큰으로 refresh                               | 갱신 실패 → 비로그인 처리                                                                                            | Redis 저장값과 불일치                       |
| TC-AUTH-11 ✅  | 로그아웃                | 로그인 상태          | 대시보드에서 로그아웃                                           | 204, Redis Refresh 삭제 + 쿠키 만료. `/owner/login` 이동                                                           |                                      |
| TC-AUTH-12 🔁 | Access Token 자동 재발급 | 로그인 후 Access 만료 | 만료된 토큰으로 `/api/owner/**` 요청                           | 401 수신 → 인터셉터가 refresh 후 원요청 재시도 → 정상 응답                                                                   | `ownerClient.ts` failedQueue         |
| TC-AUTH-13 ⚠️ | 재발급도 실패             | refresh도 만료     | Access·Refresh 모두 만료 상태로 요청                           | `/owner/login`으로 리다이렉트                                                                                     |                                      |

---

## 2. 매장 정보 / 상태 / 설정 관리 (점주)

`GET·PUT /api/owner/stores/me` · `PUT /api/owner/stores/me/status` · `GET·PUT /api/owner/stores/me/settings`

| ID             | 시나리오          | 전제조건 | 단계                                     | 예상결과                                                 | 비고                   |
|----------------|---------------|------|----------------------------------------|------------------------------------------------------|----------------------|
| TC-STORE-01 ✅  | 내 매장 조회       | 로그인  | `GET /api/owner/stores/me`             | 200, `name`·`address`·`status` 포함                    |                      |
| TC-STORE-02 ✅  | 매장 정보 수정      | 로그인  | 매장명·주소 변경 후 `PUT /api/owner/stores/me` | 200, 변경값 반영                                          |                      |
| TC-STORE-03 ✅  | 매장 상태 변경      | 로그인  | 대시보드 상태 토글(운영중/브레이크/만석/영업종료)           | 200, status 변경. 손님 화면에 `store-status-changed` SSE 전송 | 4가지 상태 전이 모두 허용      |
| TC-STORE-04 ✅  | 설정 조회         | 로그인  | `GET /api/owner/stores/me/settings`    | 200, 테이블수·평균이용시간·영업시간·임계값·알림여부·계산식 예시                |                      |
| TC-STORE-05 ✅  | 설정 수정         | 로그인  | 테이블수·평균이용시간·영업시간·알림 변경 후 저장            | 200, 토스트 알림(3초 후 사라짐)                                |                      |
| TC-STORE-06 ⚠️ | 테이블 수 범위 위반   | 로그인  | `tableCount` 0 또는 101                  | 400 `INVALID_REQUEST`                                | `@Min(1) @Max(100)`  |
| TC-STORE-07 ⚠️ | 평균 이용시간 범위 위반 | 로그인  | `avgTurnoverMinutes` 4 또는 121          | 400 `INVALID_REQUEST`                                | `@Min(5) @Max(120)`  |
| TC-STORE-08 ⚠️ | 알림 임계값 범위 위반  | 로그인  | `alertThreshold` 0 또는 101              | 400 `INVALID_REQUEST`                                | `@Min(1) @Max(100)`  |
| TC-STORE-09 ✅  | QR 이미지 조회     | -    | `GET /api/stores/{storeId}/qr`         | 200, `image/png` 바이너리                                | 설정/온보딩 화면에서 PNG 다운로드 |
| TC-STORE-10 ⚠️ | 없는 매장 조회      | -    | `GET /api/stores/{랜덤UUID}`             | 404 `STORE_NOT_FOUND`                                |                      |

---

## 3. 손님 웨이팅 등록

`POST /api/stores/{storeId}/waitings`

| ID           | 시나리오                    | 전제조건         | 단계                                           | 예상결과                                                                                           | 비고                                   |
|--------------|-------------------------|--------------|----------------------------------------------|------------------------------------------------------------------------------------------------|--------------------------------------|
| TC-REG-01 ✅  | 등록 성공                   | 매장 OPEN      | `/wait?storeId=...`에서 전화번호·인원 입력, 동의 체크 후 등록 | 201, `waitingId`·`waitingNumber`·`currentRank`·`totalWaiting`·`estimatedWaitMinutes`. 상태페이지 이동 | 신규 등록자 rank == totalWaiting          |
| TC-REG-02 ⚠️ | 동의 미체크                  | 매장 OPEN      | 동의 체크 안 함                                    | 등록 버튼 비활성, 전송 안 됨                                                                              | 프론트 검증                               |
| TC-REG-03 ⚠️ | 전화번호 형식 오류              | 매장 OPEN      | `01012345678`, `010-123-456` 등               | 400 `INVALID_REQUEST` ("010-XXXX-XXXX")                                                        | `@Pattern`. 프론트는 자동 하이픈 포맷           |
| TC-REG-04 ⚠️ | 인원 범위 위반                | 매장 OPEN      | partySize 0 또는 11                            | 400 `INVALID_REQUEST`                                                                          | `@Min(1) @Max(10)`. 프론트 스테퍼는 1~10 제한 |
| TC-REG-05 ⚠️ | 매장 BREAK/FULL/CLOSED 등록 | 매장 비OPEN     | 등록 시도                                        | 409 `STORE_NOT_AVAILABLE`. 손님 화면엔 폼 대신 상태 메시지 표시                                               | BREAK="현재 브레이크타임입니다." 등              |
| TC-REG-06 ⚠️ | 없는 매장 등록                | -            | `storeId`에 랜덤 UUID                           | 404 `STORE_NOT_FOUND`                                                                          |                                      |
| TC-REG-07 ⚠️ | storeId 누락 진입           | -            | `/wait`에 storeId 없이 진입                       | "유효하지 않은 QR 코드입니다." 표시                                                                         |                                      |
| TC-REG-08 ✅  | 대기번호 순차 증가              | 매장 OPEN      | 연속 2건 등록                                     | waitingNumber가 직전 +1                                                                           | `findNextWaitingNumber`              |
| TC-REG-09 🔁 | 등록 시 점주 알림              | 점주 대시보드 구독 중 | 손님 등록 발생                                     | 점주 화면에 `waiting-registered` → 목록 갱신                                                            |                                      |
| TC-REG-10 ✅ | 영업일 기준 대기번호 리셋          | 전날 마감 대기 존재    | 매장 설정 `businessDayStart` 시각 이후 첫 등록          | waitingNumber가 1부터 다시 시작 (전날 번호와 무관)                                                        | `businessDateOf` 05:00 기본값          |

---

## 4. 손님 웨이팅 상태 조회 / 취소

`GET /api/waitings/{id}` · `DELETE /api/waitings/{id}` · `GET /api/waitings/{id}/stream`

| ID            | 시나리오           | 전제조건                      | 단계                                  | 예상결과                                                                           | 비고                        |
|---------------|----------------|---------------------------|-------------------------------------|--------------------------------------------------------------------------------|---------------------------|
| TC-WAIT-01 ✅  | 상태 조회          | WAITING 상태                | 상태페이지 진입 / `GET /api/waitings/{id}` | 200, `currentRank`·`totalWaiting`·`estimatedWaitMinutes`. "앞 대기 팀 = rank-1" 표시 |                           |
| TC-WAIT-02 🔁 | SSE 연결         | 상태페이지                     | 페이지 진입 후 배지 확인                      | "● 실시간 업데이트 중"(녹색) 표시                                                          | `connected`               |
| TC-WAIT-03 🔁 | 앞 팀 입장 시 순서 갱신 | 상태페이지 구독 중                | 점주가 앞 팀 입장 처리                       | `waiting-updated` 수신 → 재조회 → 순서 감소                                             |                           |
| TC-WAIT-04 🔁 | 호출 모달          | 상태페이지 구독 중                | 점주가 내 웨이팅 호출                        | `called` 수신 → "입장해 주세요!" 모달                                                    |                           |
| TC-WAIT-05 🔁 | SSE 재연결        | 상태페이지                     | 네트워크 끊김 유발                          | 최대 3회 재연결 시도(3초 간격), 실패 시 "● 연결 오류"(빨강)                                        | `MAX_RETRIES=3`           |
| TC-WAIT-06 ✅  | 취소 성공          | WAITING 또는 CALLED         | 취소 페이지에서 취소 확정 / `DELETE`           | 204, CANCELLED 전이. `waiting-updated` 브로드캐스트                                    |                           |
| TC-WAIT-07 ⚠️ | 종료된 웨이팅 조회     | ENTERED/CANCELLED/NO_SHOW | `GET /api/waitings/{id}`            | 404 `WAITING_NOT_FOUND` → "웨이팅이 종료되었습니다" 화면 + 세션 삭제                            | 활성(WAITING/CALLED)만 조회 허용 |
| TC-WAIT-08 ⚠️ | 없는 웨이팅 조회      | -                         | 랜덤 UUID 조회                          | 404 `WAITING_NOT_FOUND`                                                        |                           |
| TC-WAIT-09 ⚠️ | 이미 입장/취소건 재취소  | ENTERED/CANCELLED         | `DELETE /api/waitings/{id}`         | 409 `INVALID_STATUS_TRANSITION`                                                | cancel은 WAITING/CALLED만   |
| TC-WAIT-10 ✅  | 세션 자동 복귀       | 등록 후 재방문                  | `/wait` 재진입                         | 진행 중 세션 있으면 상태페이지로 자동 이동                                                       | `getWaitingSession`       |
| TC-WAIT-11 ✅  | 일시 오류 처리       | 상태페이지                     | 서버 5xx/네트워크 오류                      | "서버에 연결할 수 없습니다" + 새로고침 버튼 (세션 유지)                                             | 404와 구분 처리                |
| TC-WAIT-12 ✅  | 호출 유예 카운트다운     | CALLED 상태                 | 점주 호출 후 상태페이지 확인                    | `graceDeadline`까지 남은 시간 카운트다운 표시                                                     | `callGraceMinutes` 반영         |

---

## 5. 점주 대기 관리 (호출 / 입장 / 노쇼)

`GET /api/owner/stores/me/waitings` · `POST /api/owner/waitings/{id}/call|enter|noshow|postpone`

| ID           | 시나리오                | 전제조건             | 단계                                        | 예상결과                                           | 비고              |
|--------------|---------------------|------------------|-------------------------------------------|------------------------------------------------|-----------------|
| TC-MNG-01 ✅  | 대기 목록 조회            | 로그인              | `GET /api/owner/stores/me/waitings`       | 200, WAITING/CALLED 항목 + `elapsedMinutes`(경과분) |                 |
| TC-MNG-02 ✅  | 호출 (WAITING→CALLED) | 대상 WAITING       | 대기카드 "호출" → 확인 다이얼로그 → 확정                 | 204, CALLED 전이. 손님에 `called` 발송                | 카드 버튼 상태별 표시    |
| TC-MNG-03 ✅  | 입장 (CALLED→ENTERED) | 대상 CALLED        | "입장" → 확인 → 확정                            | 204, ENTERED 전이. `waiting-updated` 브로드캐스트      | 목록에서 사라짐        |
| TC-MNG-04 ✅  | 노쇼 (CALLED→NO_SHOW) | 대상 CALLED        | "노쇼" → 확인 → 확정                            | 204, NO_SHOW 전이. `waiting-updated` 브로드캐스트      |                 |
| TC-MNG-05 ⚠️ | WAITING 입장 시도       | 대상 WAITING       | `POST .../enter` 직접 호출                    | 409 `INVALID_STATUS_TRANSITION`                | enter는 CALLED만  |
| TC-MNG-06 ⚠️ | WAITING 노쇼 시도       | 대상 WAITING       | `POST .../noshow`                         | 409 `INVALID_STATUS_TRANSITION`                | noShow는 CALLED만 |
| TC-MNG-07 ⚠️ | CALLED 재호출          | 대상 CALLED        | `POST .../call`                           | 409 `INVALID_STATUS_TRANSITION`                | call은 WAITING만  |
| TC-MNG-08 ⚠️ | 없는 웨이팅 처리           | -                | 랜덤 UUID로 call/enter/noshow                | 404 `WAITING_NOT_FOUND`                        |                 |
| TC-MNG-09 ⚠️ | 타 매장 웨이팅 처리         | 다른 점주의 waitingId | call/enter/noshow 시도                      | 404 `STORE_NOT_FOUND` (소유권 은닉)                 | `belongsTo` 검증  |
| TC-MNG-10 ✅  | 오늘 이력 조회            | 로그인              | `GET /api/owner/stores/me/waitings/today` | 200, 오늘 전체 이력(모든 상태)                           | HistoryPage     |
| TC-MNG-11 ✅  | 미루기 (CALLED→WAITING) | 대상 CALLED        | "미루기" → 확인 → 확정                        | 204, WAITING 전이(번호 유지). 손님 화면은 호출 모달 닫고 대기 화면으로 복귀 | `waiting-postponed` SSE |
| TC-MNG-12 ✅  | 전화번호 마스킹            | 대기 목록 존재         | 대기 목록 조회                                | `phoneNumber`가 `****-XXXX` 형식(뒤 4자리만 노출)         | `PhoneNumberMasker`  |

---

## 6. 대시보드 통계 / SSE / 알림

`GET /api/owner/stores/me/waitings/summary` · `GET /api/owner/stores/me/dashboard/stream`

| ID            | 시나리오        | 전제조건           | 단계          | 예상결과                                              | 비고               |
|---------------|-------------|----------------|-------------|---------------------------------------------------|------------------|
| TC-DASH-01 ✅  | 오늘 통계 조회    | 로그인            | summary 조회  | 200, 등록/입장/노쇼/취소/현재대기 건수                          | 오늘 날짜 기준 집계      |
| TC-DASH-02 ✅  | 통계 정확성      | 등록3·입장1·노쇼1 처리 | summary 재조회 | 등록=3, 입장=1, 노쇼=1, 현재대기=1 반영                       |                  |
| TC-DASH-03 🔁 | 대시보드 SSE 구독 | 로그인            | 대시보드 진입     | `dashboard/stream` 연결, 초기 대기목록 수신                 |                  |
| TC-DASH-04 🔁 | 실시간 목록 갱신   | 대시보드 구독 중      | 손님 등록/취소 발생 | `waiting-registered`/`waiting-updated` → 목록 자동 갱신 |                  |
| TC-DASH-05 🔁 | 임계값 초과 알림   | 알림 ON, 임계값 N   | 대기팀이 임계값 초과 | `alert-threshold-reached` → 브라우저 OS 알림            | Notification API |
| TC-DASH-06 ⚠️ | 알림 권한 거부    | 알림 권한 거부       | 임계값 초과 발생   | 대시보드 내 배너로 대체 표시(닫기 버튼)                           |                  |
| TC-DASH-07 🔁 | SSE 자동 재연결  | 대시보드 구독 중      | 연결 끊김       | 최대 3회 재연결 시도                                      |                  |

---

## 7. 예상 대기시간 계산

공식: `round(avgTurnoverMinutes × 앞팀수 / tableCount)` (곱셈을 먼저 해 정수 나눗셈 절사를 피하고, 결과를 반올림)

| ID          | 시나리오        | 전제조건              | 단계                 | 예상결과                           | 비고                           |
|-------------|-------------|-------------------|--------------------|--------------------------------|------------------------------|
| TC-EST-01 ✅ | 기본 계산       | 테이블5·평균30분        | 앞 대기 3팀인 손님 상태조회   | 30×3/5 = round(18) = **18분**   | TASKS 6-3                    |
| TC-EST-02 ✅ | 앞 0팀        | 테이블5·평균30분        | 첫 손님 상태조회          | 0분                             |                              |
| TC-EST-03 ✅ | 설정 변경 즉시 반영 | 손님 대기 중           | 점주가 평균이용시간/테이블수 변경 | 손님 상태페이지 새로고침 시 변경된 값으로 재계산    |                              |
| TC-EST-04 ✅ | 설정 없을 때 폴백  | StoreSettings 미존재 | 상태조회               | 앞팀수 × 5분(기본)                   | `DEFAULT_MINUTES_PER_PERSON` |
| TC-EST-05 ✅ | 곱셈 우선·반올림 확인 | 테이블3·평균10분        | 앞 2팀               | 10×2/3 = round(6.67) = **7분**  | 연산 순서 주의                     |

---

## 8. 보안 / 권한

| ID           | 시나리오                | 전제조건      | 단계                                          | 예상결과                                            | 비고             |
|--------------|---------------------|-----------|---------------------------------------------|-------------------------------------------------|----------------|
| TC-SEC-01 ⚠️ | JWT 없이 점주 API       | 비로그인      | 토큰 없이 `/api/owner/**` 호출                    | 403 (인증 없음)                                     | TASKS 3-11/6-4 |
| TC-SEC-02 ⚠️ | 만료 토큰 접근            | 만료 Access | 만료 토큰으로 `/api/owner/**`                     | 401                                             |                |
| TC-SEC-03 ✅  | 손님 API는 무인증         | -         | JWT 없이 `/api/stores/**`, `/api/waitings/**` | 정상 동작                                           |                |
| TC-SEC-04 ⚠️ | 타 점주 매장 접근          | 점주 A 로그인  | A 토큰으로 B 매장 리소스 접근                          | 404 `STORE_NOT_FOUND` (존재 은닉, 403 아님)           | 의도된 보안 패턴      |
| TC-SEC-05 ✅  | PrivateRoute 가드     | 비로그인      | `/owner/dashboard` 직접 접근                    | `/owner/login`으로 리다이렉트                          |                |
| TC-SEC-06 ✅  | 루트 리다이렉트            | -         | `/` 접근                                      | 로그인 시 `/owner/dashboard`, 비로그인 시 `/owner/login` |                |
| TC-SEC-07 ✅  | Refresh 쿠키 HttpOnly | 로그인       | 브라우저 JS로 쿠키 접근 시도                           | `document.cookie`에 refresh_token 미노출            | HttpOnly       |

---

## 9. E2E 통합 시나리오

| ID        | 시나리오              | 단계                                        | 예상결과                             |
|-----------|-------------------|-------------------------------------------|----------------------------------|
| TC-E2E-01 | 점주 온보딩 전체 플로우     | 회원가입 → 온보딩(QR 다운로드·초기설정) → 대시보드           | 각 단계 정상 진행, 대시보드 진입              |
| TC-E2E-02 | 손님 등록 → 점주 실시간 반영 | (브라우저2개) 손님 QR 등록 → 점주 대시보드 확인            | 점주 목록에 신규 대기 즉시 표시               |
| TC-E2E-03 | 호출 → 입장 전체 플로우    | 점주 호출 → 손님 모달 표시 → 점주 입장 처리 → 손님 순서 갱신/종료 | 양쪽 화면 실시간 동기화                    |
| TC-E2E-04 | 노쇼 처리             | 점주 호출 → 노쇼 처리                             | 대기열에서 제거, 통계 노쇼 +1               |
| TC-E2E-05 | 브레이크타임 차단         | 점주 BREAK 설정 → 손님 QR 스캔                    | 손님 화면 "현재 브레이크타임입니다." (등록 폼 미표시) |
| TC-E2E-06 | 토큰 만료 자동 복구       | Access 만료 상태로 대시보드 조작                     | 자동 refresh 후 끊김 없이 동작            |
| TC-E2E-07 | 예상 대기시간 검증        | 테이블5·평균30분, 손님 3팀 등록 후 4번째 조회             | 앞 3팀 → 약 18분 표시                  |

---

## 부록. 상태 전이 매트릭스

| 현재\동작     | call()   | enter()   | noShow()  | cancel()    |
|-----------|----------|-----------|-----------|-------------|
| WAITING   | → CALLED | ❌ 409     | ❌ 409     | → CANCELLED |
| CALLED    | ❌ 409    | → ENTERED | → NO_SHOW | → CANCELLED |
| ENTERED   | ❌ 409    | ❌ 409     | ❌ 409     | ❌ 409       |
| NO_SHOW   | ❌ 409    | ❌ 409     | ❌ 409     | ❌ 409       |
| CANCELLED | ❌ 409    | ❌ 409     | ❌ 409     | ❌ 409       |

> ❌ 409 = `INVALID_STATUS_TRANSITION` (`IllegalStateException` → 409)

## 부록. 매장 상태(StoreStatus)별 손님 화면

| status | 손님 등록   | 화면 메시지            |
|--------|---------|-------------------|
| OPEN   | 가능      | 웨이팅 등록 폼          |
| BREAK  | 불가(409) | "현재 브레이크타임입니다."   |
| FULL   | 불가(409) | "현재 만석입니다."       |
| CLOSED | 불가(409) | "오늘 영업이 종료되었습니다." |
