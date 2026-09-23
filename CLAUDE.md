# desyp 개발 지침

@README.md
@AGENTS.md

제품 정책, 일정, 상품 목록과 API 사용법의 기준 문서는 루트 `README.md`다. 이 문서는 코드와 인프라 구현 규칙만 다룬다. 계획을 구현 완료로 표현하지 않는다.

## 서비스 구성

| 모듈 | 책임 | 배포 목표 |
| --- | --- | --- |
| `common` | 공통 응답과 예외 | 라이브러리 |
| `notification-service` | 사전 등록, 추천 점수·순위, 메일 | AWS Lambda |
| `event-entry-service` | 이벤트 당일 응모 | k3s |

Java 21, Spring Boot 4.1.1, Gradle Groovy를 사용한다. Spring Boot 4.x 기준으로 `jakarta.*` 패키지를 사용한다.

## 공통 코드 규칙

- 생성자 주입만 사용한다.
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@Builder`만 허용한다. `@Data`와 Entity `@Setter`는 금지한다.
- 트랜잭션 경계는 Service 계층에 둔다.
- Entity는 의미 있는 메서드로 상태를 변경한다.
- 도메인 오류는 `BusinessException`과 `<Domain>ErrorCode`로 표현한다.
- 로그는 SLF4J 파라미터 치환을 사용한다.
- 이메일, 추천 코드, 소셜 식별자, Instagram ID 원문은 로그에 남기지 않는다.
- 외부 입력은 DTO 검증 후 Service 도메인 규칙으로 검증한다.
- Secret을 코드나 저장소 설정에 넣지 않고 HTTPS를 강제한다.

이름은 `Controller`, `Service`, `Repository` 접미사를 사용한다. Entity는 단수형, DTO는 `record`와 `Request`/`Response` 접미사를 기본으로 한다.

`common`에는 두 서비스에서 실제로 공유하는 코드만 둔다. 현재 공통 대상은 `BaseErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `ApiResponse`다.

## Git 규칙

커밋 형식은 `<type>(<scope>): <description>`이다. type은 `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `perf`, `chore`, `revert` 중 하나를 사용한다.

- `main`: 배포
- `develop`: 통합
- 기능 브랜치: `feat/`, `fix/`, `refactor/`, `chore/`
- 긴급 수정: `hotfix/`

PR은 최소 한 명이 리뷰하고 Checkstyle·SpotBugs를 함께 사용한다.

## notification-service

### 책임과 구현 상태

- `subscriber`: 사전 등록, 동의, 중복 검증, 추천 관계와 보너스 저장
- `auth`: 네이버 로그인 시작·콜백 리다이렉트, 계정·프로필 확인, 관리자 허용 목록
- `referral`: 내 점수와 관리자 공동 순위 조회
- `mail`: 관리자 트리거 기반 SES 발송과 `notified_at` 중복 방지
- `global`: 세션, CSRF, CORS, 보안 설정

사전 등록·마감·점수·순위·관리자 메일 트리거는 구현됐다. 최고점 동률 추첨, 결과 스냅샷, 공개 결과, EventBridge Scheduler, 429 스로틀링, 30일 파기는 미구현이다.

### 데이터 규칙

적용된 Flyway 마이그레이션은 수정하지 않고 새 버전을 추가한다.

- 신규 인증과 등록은 네이버만 허용한다. `GOOGLE` enum 값은 기존 V1~V3 데이터 호환용이며 신규 등록에 사용하지 않는다.
- `(provider, provider_account_id)`, `email_normalized`, `phone_number`는 각각 고유해야 한다.
- `phone_number`는 네이버 `mobile` 프로필 값에서 숫자 형식으로 정규화하며 요청 본문으로 받지 않는다. 중복 확인에만 쓰고 어떤 API 응답에도 포함하지 않는다.
- 추천인은 `referrerEmail`을 정규화해 `email_normalized`로 찾는다. `invite_token`은 기존 스키마 호환용이며 추천에 쓰지 않는다.
- 동의 3종(`age_confirmed`, `privacy_agreed`, `marketing_agreed`)은 모두 필수이며 `consent_at`에 동의 시각을 기록한다. 알림 메일은 `marketing_agreed`인 등록자에게만 보낸다.
- `referrer_id`는 생성 후 변경하지 않으며 자기 자신을 가리킬 수 없다.
- `referral_bonus`는 추천인이 있으면 1, 없으면 0이며 요청 값으로 받지 않는다.
- 실제 추천 인원은 `referrer_id` 관계를 조회 시 `COUNT`한다. 누적 카운터를 별도로 저장하지 않는다.
- 순위는 추천 인원과 보너스를 한 SQL에서 계산하고 `1, 1, 3` 공동 순위를 사용한다.
- 이메일은 소문자화, `+` 별칭 제거, Gmail 점 제거, `googlemail.com` 통합 후 중복을 검사한다.
- 메일 성공 시에만 `notified_at`을 기록한다.

내 점수는 로그인 계정으로만 조회한다. 관리자 API는 `ADMIN_NAVER_ACCOUNT_IDS`가 비어 있으면 전부 거부한다. 이메일이 포함된 순위 응답은 당첨자 연락을 위한 관리자 전용이다.

세션은 Spring Session JDBC로 PostgreSQL `SPRING_SESSION` 테이블(V7)에 저장해 Lambda 인스턴스 간에 공유한다. 세션 쿠키 속성(`HttpOnly`, `SameSite=Lax`, `Secure`)은 Boot 속성이 내장 서버에서만 적용되므로 `SecurityConfig`의 `CookieSerializer`에서 지정한다.

`return_to`는 `desyp.frontend.origin`과 scheme·host·port가 같을 때만 허용한다. `/api/pre-registrations`는 프런트가 CSRF 토큰을 받지 않으므로 CSRF 예외이며 JSON 전용·SameSite=Lax·CORS로 보호한다. 관리자 API는 CSRF 토큰을 유지한다. 이메일·휴대전화번호·네이버 식별자는 로그에 남기지 않는다.

### 배포 전 확인

- Spring Session JDBC의 만료 세션 정리(`cleanup-cron`, 1분 주기)는 Lambda가 요청 사이에 멈추면 실행되지 않는다. 만료 세션은 조회 시 거부되지만 행이 쌓이므로 별도 정리 작업을 정한다
- API Gateway·WAF의 사전 등록 요청 스로틀링(`429`)
- `server.forward-headers-strategy=framework`로 `X-Forwarded-*`를 신뢰하므로 애플리케이션은 API Gateway를 거쳐서만 접근 가능해야 한다. 첫 배포 후 네이버 redirect-uri가 `https://api.desyp.site/auth/naver/callback`으로 나가는지 확인한다
- 이벤트 종료 후 30일 내 개인정보 파기
- EventBridge Scheduler의 정확한 이벤트 시작 시각과 1시간 전 실행
- 네이버 개발자센터의 이메일·휴대전화번호 제공 권한과 실제 OAuth 응답
- 기존 Google 가입 데이터의 운영 전 정리 여부. V5는 데이터 손실을 피하기 위해 기존 행의 `phone_number`를 NULL로 유지한다.
- 실제 PostgreSQL과 SES 연결
- 메일 실패 재시도와 발송 이력
- 추천 집계 마감, 감사 가능한 동률 추첨과 결과 스냅샷

## event-entry-service

현재 소스 코드가 없는 설계 단계다. 사전 등록 추천 점수와 이벤트 시작 메일은 `notification-service` 책임이며, 이벤트 응모에서 추천 보너스를 다시 지급하지 않는다.

### 확정 사항

- 목표 규모는 DAU 10,000명, 10분간 1,000 QPS다.
- Java 21 Virtual Thread, k3s, Redis, PostgreSQL을 사용한다.
- 인당 응모 횟수는 제한하지 않는다.
- 동일 Instagram ID의 요청 간격을 서버에서 최소 1초로 제한한다.
- 클라이언트 지연은 매크로 방어 수단으로 인정하지 않는다.
- 접수 번호는 당첨 정책이 확정되기 전까지 내부 감사 식별자로만 사용한다.
- 결과 팝업은 `OK`로만 닫으며, 실패나 결과 대기 상태에도 안내 문구와 `OK`를 제공한다.

### 설계 후보와 미정 사항

- Redis 원자 연산으로 rate limit 확인, 접수 번호 증가, Stream 기록을 한 번에 처리한다.
- Stream을 PostgreSQL에 비동기 배치 적재하는 기존 후보 구조는 부하 테스트 후 확정한다.
- 고정 순번과 랜덤 중 당첨 방식을 정한 후에만 당첨 데이터 모델과 문구를 고정한다.
- 정책 확정 전에는 `1번째`, `N번째`, `10,000번째` 같은 순번을 당첨 조건으로 구현하지 않는다.

응모 레코드 후보 필드는 `seq`, `insta_id`, `submitted_at`, `token_hash`, `ip_hash`, `created_at`이다. `insta_id`에는 UNIQUE 제약을 두지 않는다.

## 프런트엔드와 인프라

- 사전 등록 페이지만 Vercel에 배포한다([desyp-event](https://github.com/DesyProject/desyp-event)). 메인 이벤트 정적 페이지는 비공개 S3 버킷과 CloudFront OAC로 제공한다.
- S3 Block Public Access를 유지하고 CloudFront만 읽을 수 있게 한다.
- 정적 파일명에는 콘텐츠 해시를 쓰고 `index.html` TTL은 짧게 둔다.
- 로그인·응모 API는 별도 도메인으로 분리하고 정적 캐시에 넣지 않는다.
- 서버 렌더링이 필요해지면 S3 정적 배포 결정을 다시 검토한다.
- API 진입점의 WAF와 rate limit은 정적 CDN 설정과 분리한다.

관측성은 OpenTelemetry와 Prometheus·Loki·Tempo·Grafana를 사용한다. Lambda 메트릭은 OTel Collector로 전송하며 Collector 위치는 배포 전에 정한다.

## Graphify

코드 구조 질문은 `graphify-out/graph.json`이 있으면 `graphify query`로 먼저 탐색한다. 코드 변경 후 `graphify update .`, 문서 의미 변경 후 `/graphify . --update`를 사용한다. `graphify-out/`은 Git에 포함하지 않는다.
