# 이메일 사전 등록

Google OIDC 또는 네이버 OAuth2 로그인 → CSRF 토큰 조회 → 사전 등록 순서로 사용한다.

## 실행

Java 21과 PostgreSQL을 준비한다. 다음 환경 변수를 설정한다.

- `DB_URL`: 예: `jdbc:postgresql://localhost:5432/desyp`
- `DB_USERNAME`, `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
- `SPRING_PROFILES_ACTIVE=oauth`

Google 콘솔에 `{서비스 외부 주소}/login/oauth2/code/google`을, 네이버 개발자센터에 `{서비스 외부 주소}/login/oauth2/code/naver`를 Redirect URI로 등록한다.
운영은 HTTPS로 제공하고 세션 쿠키의 Secure 기본값을 유지한다. 로컬 HTTP 개발에서만 `SESSION_COOKIE_SECURE=false`를 사용한다.
프록시 환경에서는 신뢰할 프록시와 외부 HTTPS 주소 전달 설정을 배포 계층에서 구성해야 한다.

```sh
./gradlew :notification-service:bootRun
```

Flyway가 `subscribers` 테이블을 생성하고 Hibernate가 스키마를 검증한다. 이미 수동 생성한 테이블이 있는 DB는 자동 baseline하지 않으므로 별도 마이그레이션 계획이 필요하다.
OAuth 프로필 없이도 테스트할 수 있지만 실제 Google 로그인과 등록은 사용할 수 없다.

## API

1. 브라우저에서 `GET /oauth2/authorization/google`로 로그인한다.
2. 로그인 후 `GET /api/csrf` 응답의 `data.headerName`, `data.token`을 읽는다.
3. 같은 세션 쿠키와 해당 CSRF 헤더를 포함해 `POST /api/subscribers`를 호출한다.

```json
{
  "email": "Google에서 인증한 이메일",
  "ageConfirmed": true,
  "privacyConsented": true,
  "referralCode": null
}
```

`email`은 인증된 소셜 로그인 이메일과 대소문자를 제외하고 일치해야 한다. 제공자 계정 식별자는 요청에서 받지 않는다.
`ageConfirmed`와 `privacyConsented`는 모두 true여야 하며 동의 시각을 서버에서 저장한다.
추천 코드는 선택 사항이다. 신규 등록 시 기존 구독자만 추천인으로 지정할 수 있고 자기추천은 차단한다. 기존 추천 관계를 수정하는 API는 없다.

성공은 HTTP 201, 응답은 `{"success":true,"data":{"id":1,"inviteToken":"발급된 UUID"},"message":null}`이다.
입력/동의/추천 오류는 400, 로그인 누락은 401, CSRF 실패는 403, 중복 또는 동시 등록 충돌은 409이다.
인증 없이 CSRF 토큰도 생략한 요청은 보안 필터 순서에 따라 403이 반환될 수 있다.
이메일 정규화는 소문자화, `+` 별칭 절삭, Gmail 점 제거 및 googlemail.com 통합을 적용한다.

SES 메일 발송, 예약 스케줄, 실제 소셜 자격 증명 발급, Lambda 배포는 후속 작업이다.
현재 인증은 서버 세션을 사용한다. Lambda/여러 인스턴스 배포 전 세션 공유 방식은 별도로 확정해야 한다.

## 추천 점수

A의 코드로 B가 등록을 완료하면 A의 추천 인원 +1, B의 코드 사용 보너스 +1이다.
B의 코드로 C가 등록하면 B는 추천 1 + 보너스 1 = 2점이고, A와 C는 각각 1점이다.
코드 없이 등록하면 보너스는 0이다. 실패/중복 등록으로는 점수를 추가하지 않는다.

- 실제 추천 인원: 저장된 추천 관계를 실시간 COUNT
- 코드 사용 보너스: 가입 시 `referral_bonus`에 0 또는 1 저장
- 총점: 추천 인원 + 보너스
- 상품 선정 기준: **최고 추천 점수**

### 내 점수

Google 로그인 세션으로 `GET /api/referrals/me`를 호출한다. 가입 전이면 404다.

```json
{"success":true,"data":{"subscriberId":2,"inviteToken":"본인 추천 코드","referralCount":1,"referralBonus":1,"totalScore":2},"message":null}
```

### 관리자 순위

`ADMIN_GOOGLE_SUBS`에 허용할 관리자 Google subject를 쉼표로 구분해 설정한다. 이메일이 아닌 계정 고유값이며 로그에 출력하지 않는다.
설정하지 않으면 관리자 API는 모두 거부된다. 로그인된 일반 사용자도 접근할 수 없다.

`GET /api/admin/referrals/ranking?maxRank=10`

- `maxRank`: 1~100, 기본 10. 잘못된 값은 400.
- 총점 내림차순 공동 순위 `1, 1, 3`을 사용한다. 순위 경계의 동점자는 모두 포함하므로 응답 건수가 maxRank보다 많을 수 있다.
- 동일 점수의 표시 순서는 가입자 ID 순서이며, 상품 우선권을 의미하지 않는다.
- 이메일을 포함한 관리자 전용 응답이다. 추천 코드는 포함하지 않는다.
- 실시간 조회이며 수상자 확정·상품 지급은 수행하지 않는다. 집계 마감 시각과 동점 상품 정책은 운영에서 정해야 한다.

```json
{"success":true,"data":[{"rank":1,"subscriberId":2,"email":"b@example.com","referralCount":1,"referralBonus":1,"totalScore":2}],"message":null}
```

### DB 업그레이드

Flyway V2는 보너스 컬럼과 제약을 추가하고, 기존 가입자 중 추천인이 있는 사람에게 보너스 1점을 적용한다.
추천 관계와 보너스는 가입 후 변경할 수 없으며, 별도 점수 증가 API는 없다.
다계정 추가 방지는 적용하지 않는다. 동일 계정·정규화 이메일 중복과 자기추천은 차단한다.

## 예정된 전체 흐름

Google·네이버 로그인 → 사전 등록/추천 연결 → 내 점수/관리자 최고점 조회 → 이벤트 시작 1~2시간 전 SES 이메일 발송.
현재 Google·네이버 등록과 추천 점수 조회까지 구현했다. SES/EventBridge Scheduler는 아직 구현하지 않았다.
정확한 행사 시각과 발송 간격(1시간 또는 2시간)은 확정되지 않았다.

## 검증

```sh
./gradlew :notification-service:test :notification-service:bootJar
```

HTTP/보안 필터부터 서비스, Flyway, H2(PostgreSQL 모드) 저장까지 통합 테스트한다.
정상 등록, 동의·이메일 검증, 중복/자기추천, 양쪽 점수, 보너스 중복 방지, 공동 순위, 관리자 권한, 동시 추천, CSRF와 기존 데이터 마이그레이션을 검사한다.
실제 Google 로그인 및 운영 PostgreSQL 연결은 자격 증명과 실행 환경에서 별도 검증해야 한다.
