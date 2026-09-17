# desyp-backend

사전 등록·추천 이벤트와 이벤트 당일 응모를 위한 Java 21 / Spring Boot 멀티모듈 백엔드다.

## 제품 정책

1. 사용자는 Google 또는 네이버 소셜 로그인 후 이메일을 사전 등록한다.
2. B가 A의 추천 코드로 등록하면 A는 추천 성공 1점, B는 최초 코드 사용 보너스 1점을 받는다.
3. **추천 점수 = 실제 추천 인원 수 + 코드 사용 보너스(최대 1점)** 이다.
4. 최고점자가 여러 명이면 최고점 동률자 중 최종 1등 한 명을 무작위로 추첨한다.
5. 공개 결과에는 개인정보를 마스킹한 1등 한 명만 표시하고, 관리자는 연락 가능한 정보를 조회한다.
6. 운영자가 당첨자에게 직접 연락해 5만 원 이하 상품을 확인하고 발송한다. 상품 선택 API는 만들지 않는다.
7. 메인 이벤트 시작 1시간 전에 사전 등록자에게 이메일을 발송한다.

다계정 추가 방지는 제외한다. 동일 소셜 계정·정규화 이메일 중복, 자기추천, 등록 후 추천인 변경은 차단한다.

### 상품 목록

이벤트 페이지에는 선택 입력 없이 다음 상품을 안내용으로 표시한다.

- 올리브영
- 메가MGC커피
- 스타벅스
- 다이소
- 문화상품권
- 투썸플레이스
- 배달의민족
- 이마트·신세계상품권
- 쿠팡

판매 여부와 금액은 운영자가 구매 전에 확인하며, 모든 상품은 5만 원 이하여야 한다.

## 일정과 화면 동작

- 서버 운영 시작: **2026년 10월 초**
- 공개 메인 이벤트: 서버 운영 시작 **2~4주 뒤**
- 결과 팝업: 바깥 클릭이나 Escape로 닫히지 않고, 사용자가 `OK`를 눌러야 닫힌 뒤 다음 화면으로 이동
- 이벤트 당일 당첨 방식: 고정 순번과 랜덤 방식 중 아직 미정. 추천 점수 동률자 추첨과는 별개

## 구현 상태

| 영역 | 상태 |
| --- | --- |
| Google·네이버 로그인 | 구현 완료 |
| 이메일 사전 등록 | 구현 완료 |
| 추천 점수·공동 순위 | 구현 완료 |
| SES 알림 | 관리자 수동 트리거 구현 완료 |
| 1시간 전 자동 발송 | EventBridge Scheduler 미구현 |
| 최고점 동률 추첨·결과 공개 | 미구현 |
| 이벤트 당일 응모 | 설계 단계 |
| 정적 이벤트 페이지 | 비공개 S3 + CloudFront OAC 사용 확정 |

## 프로젝트 구성

- `common`: 공통 응답과 예외
- `notification-service`: 사전 등록, 추천 점수·순위, 이메일 발송
- `event-entry-service`: 이벤트 당일 응모. 현재 코드 없음

개발 규칙과 상세 설계는 [CLAUDE.md](CLAUDE.md), 에이전트 작업 규칙은 [AGENTS.md](AGENTS.md)를 참고한다.

## 실행

Java 21과 PostgreSQL을 준비하고 다음 환경 변수를 설정한다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
- `SES_SENDER_EMAIL`
- `SPRING_PROFILES_ACTIVE=oauth`

Google·네이버 Redirect URI는 `{서비스 외부 주소}/login/oauth2/code/{google|naver}`다. 운영에서는 HTTPS와 Secure 세션 쿠키를 사용하고, 로컬 HTTP에서만 `SESSION_COOKIE_SECURE=false`를 쓴다.

```sh
./gradlew :notification-service:bootRun
```

Swagger UI는 `http://localhost:8080/swagger-ui/index.html`에서 확인한다.

## API 흐름

1. `GET /oauth2/authorization/google` 또는 `/oauth2/authorization/naver`로 로그인
2. `GET /api/csrf` 응답으로 CSRF 헤더 구성
3. 같은 세션으로 API 호출

### 사전 등록

`POST /api/subscribers`

```json
{"email":"로그인 이메일","ageConfirmed":true,"privacyConsented":true,"referralCode":null}
```

성공 시 `201`과 가입 ID·추천 코드를 반환한다. 추천 코드는 선택 사항이며 등록 후 변경할 수 없다.

### 내 추천 점수

`GET /api/referrals/me`

로그인 계정의 추천 인원, 보너스, 총점을 반환한다.

### 관리자 추천 순위

`GET /api/admin/referrals/ranking?maxRank=10`

`ADMIN_GOOGLE_SUBS` 허용 목록의 관리자만 사용할 수 있다. 공동 순위는 `1, 1, 3` 방식이며 경계의 동점자를 모두 반환한다. 현재는 실시간 순위 조회만 제공한다.

### 이벤트 알림 메일

`POST /api/admin/mail/event-start`

관리자가 아직 알림을 받지 않은 등록자에게 SES 메일을 발송한다. 성공한 등록자만 `notified_at`을 기록해 재호출 시 중복을 막는다. 운영에서는 EventBridge Scheduler가 메인 이벤트 시작 1시간 전에 이 동작을 실행하도록 추가해야 한다.

## 빌드와 테스트

```sh
./gradlew build
```

테스트는 외부 계정 없이 H2 PostgreSQL 모드로 실행한다. 실제 PostgreSQL, Google·네이버 로그인, SES 연결은 운영 환경에서 별도로 검증해야 한다.
