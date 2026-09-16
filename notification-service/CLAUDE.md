# notification-service

desyp의 상시 알림 트랙. 이벤트 사전 등록, 추천인 시스템, 메일 발송 담당.
(공통 규칙은 저장소 루트 CLAUDE.md 참고 — 여기는 이 서비스 전용 내용만)

## 목표 규모

- 2개월간 상시 가동, 트래픽은 낮음 (이벤트 트랙과 트래픽 특성이 다름 — 혼동 주의)

## 기술 스택

- 배포 대상: AWS Lambda
- PostgreSQL (관리형, 무료 티어)
- Google OAuth2 (`openid`, `email` 스코프만)

## 패키지 구조

```
com.desyp.notification
├── subscriber
├── auth
├── referral
├── mail
└── global          (이 서비스 전용: OAuth 시큐리티 설정 등)
```

`BaseErrorCode`, `BusinessException`, `ApiResponse`는 이 서비스에 만들지 않는다 — `common` 모듈(`com.desyp.common`) 것을 가져다 쓴다.

## 데이터 모델 — subscribers 테이블

```sql
CREATE TABLE subscribers (
                             id               BIGSERIAL PRIMARY KEY,
                             google_sub       TEXT NOT NULL UNIQUE,
                             email            TEXT NOT NULL,
                             email_normalized TEXT NOT NULL UNIQUE,
                             referrer_id      BIGINT REFERENCES subscribers(id),
                             invite_token     TEXT NOT NULL UNIQUE,
                             age_confirmed    BOOLEAN NOT NULL,
                             consent_at       TIMESTAMPTZ NOT NULL,
                             created_at       TIMESTAMPTZ DEFAULT now()
);
```

- 이메일 정규화 필수: 소문자화 → gmail 계열 점(`.`) 제거 → `+` 이후 절삭
- `referrer_id` 자기참조 — 자기추천/순환추천은 애플리케이션 레벨에서 차단

## 이 서비스만의 커밋 scope

| Scope | 적용 범위 |
| --- | --- |
| `auth` | Google OAuth 로그인, 토큰 |
| `subscriber` | 구독자 등록, 프로필, 연령·개인정보 동의 |
| `referral` | 추천인 관계, 추천 전환 집계 |
| `mail` | 발송 배치, 바운스 처리 |

## 동시성 / 보안 — 확정 필요

- 구독자 등록 동시 폭주 시 커넥션 풀 고갈 여부 검증 필요
- 추천 카운트 집계: 원자적 UPDATE vs 별도 집계 테이블 검토
- Google OAuth: state 파라미터 검증, PKCE 적용 여부 확인