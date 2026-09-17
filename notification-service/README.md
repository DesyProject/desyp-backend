# 이메일 사전 등록

Google OIDC 또는 네이버 OAuth2 로그인 → CSRF 토큰 조회 → 사전 등록 순서로 사용한다.

## 실행

Java 21과 PostgreSQL을 준비하고 다음 환경 변수를 설정한다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: 예 `jdbc:postgresql://localhost:5432/desyp`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
- `SES_SENDER_EMAIL`: SES에서 인증된 발신 이메일 주소
- `SPRING_PROFILES_ACTIVE=oauth`

Google 콘솔·네이버 개발자센터에 `{서비스 외부 주소}/login/oauth2/code/{google|naver}`를 Redirect URI로 등록한다.
운영은 HTTPS + 세션 쿠키 Secure 기본값을 유지하고, 로컬 HTTP 개발에서만 `SESSION_COOKIE_SECURE=false`를 쓴다.

```sh
./gradlew :notification-service:bootRun
```

Flyway가 스키마를 생성/검증하며, `SPRING_PROFILES_ACTIVE=oauth` 없이도 뜨지만 실제 로그인은 안 된다.
API 문서: `http://localhost:8080/swagger-ui/index.html` (로그인 없이 접근 가능).

## API

1. `GET /oauth2/authorization/google`(또는 `naver`)로 로그인
2. `GET /api/csrf` 응답의 `headerName`/`token`으로 CSRF 헤더 구성
3. 같은 세션으로 아래 API 호출

### `POST /api/subscribers` — 사전 등록

```json
{"email": "인증된 이메일과 동일해야 함", "ageConfirmed": true, "privacyConsented": true, "referralCode": null}
```

201 성공 시 `{"data":{"id":1,"inviteToken":"..."}}`. 400(입력/동의 오류), 401(미로그인), 403(CSRF), 409(중복/자기추천/동시등록)를 반환한다.
추천 코드는 선택이며 등록 후 변경 불가. 이메일 정규화(소문자화, `+` 별칭 제거, Gmail 점 제거, googlemail.com 통합) 후 중복을 검사한다.

### `GET /api/referrals/me` — 내 점수

로그인 세션 기준으로만 조회(다른 사람 ID 지정 불가). 가입 전이면 404.

```json
{"data":{"subscriberId":2,"inviteToken":"...","referralCount":1,"referralBonus":1,"totalScore":2}}
```

### `GET /api/admin/referrals/ranking?maxRank=10` — 관리자 순위

`ADMIN_GOOGLE_SUBS`(쉼표구분 Google subject)에 없으면 전부 거부. 공동 순위(`1,1,3`)라 응답 건수가 `maxRank`보다 많을 수 있다.
실시간 조회일 뿐 수상자 확정·지급은 하지 않는다.

상품 정책은 최고 추천 점수 동률자 중 최종 당첨자 1명을 무작위로 뽑는 방식이다. 운영자가 당첨자에게 직접 연락하면 당첨자는 5만 원 이하의 올리브영, 메가MGC커피, 스타벅스, 다이소, 문화상품권, 투썸플레이스, 배달의민족, 이마트·신세계상품권, 쿠팡 기프티콘 중 하나를 선택한다. 상품 선택용 사용자 API는 만들지 않는다. 현재 API에는 점수 집계 마감, 순위 스냅샷과 무작위 추첨 기능이 없다.

최종 결과 API를 추가할 때는 1등 한 명만 반환한다. 공개 결과에는 마스킹된 표시명만 포함하고 관리자 결과에만 연락 가능한 원본 정보를 포함한다. 상품 목록은 프런트엔드가 선택 입력 없이 안내용으로 표시한다.

### `POST /api/admin/mail/event-start` — 이벤트 알림 메일

관리자 전용. 아직 알림 안 받은(`notified_at IS NULL`) 구독자에게만 SES로 발송하고 성공한 사람만 표시해 재호출해도 중복 발송하지 않는다.
운영 발송 시점은 **메인 이벤트 시작 1시간 전**으로 확정했다.
EventBridge Scheduler 자동 트리거는 아직 없어 현재는 수동 호출해야 한다. 자동화 시 이 API 또는 동일 서비스 로직을 정확히 1시간 전에 한 번 실행한다.

## 운영 일정

- 2026년 10월 초: 서버 운영 시작
- 서버 운영 시작 2~4주 뒤: 공개 메인 이벤트 오픈

위 상대 일정은 확정이다. EventBridge 일정에는 공개 메인 이벤트의 최종 시작 시각에서 정확히 1시간을 뺀 시각을 사용한다.

## 검증

```sh
./gradlew :notification-service:test
```

실제 Google/네이버 로그인, 운영 PostgreSQL, SES 연결은 별도 환경에서 검증해야 한다.
