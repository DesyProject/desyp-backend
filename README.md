# desyp-backend

사전 등록과 이벤트 당일 응모를 위한 Java 21 / Spring Boot 멀티모듈 프로젝트다.

## 사용자 흐름

1. Google·네이버 소셜 로그인 후 이메일 사전 등록과 추천 코드 입력
2. 코드 주인에게 추천 성공 1점, 코드 사용자에게 최초 보너스 1점
3. **총점 = 실제 추천 인원 수 + 코드 사용 보너스(최대 1점)** 기준으로 상품 후보 조회
4. 이벤트 시작 1~2시간 전에 알림 이메일 발송

다계정 추가 방지는 제외한다. 동일 계정·정규화 이메일 중복 등록, 자기추천, 등록 후 추천인 변경은 막는다.

## 구현 상태

| 영역 | 현재 상태 |
| --- | --- |
| 사전 등록 | Google 로그인 정보 기반 이메일·동의·추천 관계 저장 |
| 추천 점수 | 양쪽 점수, 최초 보너스, 내 점수·관리자 공동 순위 조회 구현 |
| 네이버 로그인 | 예정 |
| 이메일 알림 | AWS SES + EventBridge Scheduler 예정, 발송 시각 미확정 |
| 이벤트 응모 | 서비스 모듈과 설계 지침, 구현 예정 |
| 상품 지급 | 관리자 후보 조회까지 구현, 최종 선정·지급은 후속 정책 |

## 모듈

- `common`: 공통 응답과 예외
- `notification-service`: 사전 등록, 추천 점수/순위, 향후 알림 발송
- `event-entry-service`: 이벤트 당일 응모

개발 규칙은 [CLAUDE.md](CLAUDE.md), API·환경 변수는 [notification-service/README.md](notification-service/README.md)를 참고한다.

## 빌드와 테스트

```sh
./gradlew build
```

알림 서비스 실행에는 PostgreSQL과 Google OAuth 환경 설정이 필요하다. 테스트는 외부 계정 없이 H2 PostgreSQL 모드로 실행한다.

## Graphify

`AGENTS.md`에 탐색 규칙이 있다. 최초 `/graphify .`, 코드 변경 후 `graphify update .`, 문서 의미 변경 후 `/graphify . --update`로 갱신한다.
`graphify-out/`은 로컬 분석 결과이므로 Git에 포함하지 않는다.
