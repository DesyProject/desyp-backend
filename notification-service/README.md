# 이메일 사전 등록

Google OIDC 로그인 → CSRF 토큰 조회 → 사전 등록 순서로 사용한다.

## 실행

Java 21과 PostgreSQL을 준비한다. 다음 환경 변수를 설정한다.

- `DB_URL`: 예: `jdbc:postgresql://localhost:5432/desyp`
- `DB_USERNAME`, `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `SPRING_PROFILES_ACTIVE=oauth`

Google 콘솔에 `{서비스 외부 주소}/login/oauth2/code/google`을 Redirect URI로 등록한다.
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

`email`은 인증된 Google 이메일과 대소문자를 제외하고 일치해야 한다. `google_sub`는 요청에서 받지 않는다.
`ageConfirmed`와 `privacyConsented`는 모두 true여야 하며 동의 시각을 서버에서 저장한다.
추천 코드는 선택 사항이다. 신규 등록 시 기존 구독자만 추천인으로 지정할 수 있고 자기추천은 차단한다. 기존 추천 관계를 수정하는 API는 없다.

성공은 HTTP 201, 응답은 `{"success":true,"data":{"id":1,"inviteToken":"발급된 UUID"},"message":null}`이다.
입력/동의/추천 오류는 400, 로그인 누락은 401, CSRF 실패는 403, 중복 또는 동시 등록 충돌은 409이다.
인증 없이 CSRF 토큰도 생략한 요청은 보안 필터 순서에 따라 403이 반환될 수 있다.
이메일 정규화는 소문자화, `+` 별칭 절삭, Gmail 점 제거 및 googlemail.com 통합을 적용한다.

메일 발송, 추천 집계, 실제 Google 자격 증명 발급, Lambda 배포는 이 등록 API 범위에 포함하지 않는다.
현재 인증은 서버 세션을 사용한다. Lambda/여러 인스턴스 배포 전 세션 공유 방식은 별도로 확정해야 한다.

## 검증

```sh
./gradlew :notification-service:test :notification-service:bootJar
```

HTTP/보안 필터부터 서비스, Flyway, H2(PostgreSQL 모드) 저장까지 통합 테스트한다.
정상 등록, 동의·이메일 검증, 중복 계정/별칭, 추천인/자기추천, 동시 등록, CSRF, 다른 인증 제공자 차단을 검사한다.
실제 Google 로그인 및 운영 PostgreSQL 연결은 자격 증명과 실행 환경에서 별도 검증해야 한다.
