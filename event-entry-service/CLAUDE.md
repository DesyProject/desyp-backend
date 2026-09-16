# event-entry-service

desyp의 이벤트 트랙. 10/31 이벤트 당일 응모 처리 담당 (1등/N번째 순번, 랜덤 추첨, 추천인 집계용 토큰 매칭).
(공통 규칙은 저장소 루트 CLAUDE.md 참고 — 여기는 이 서비스 전용 내용만)

## 사전 등록 추천과의 구분

사전 등록의 양쪽 추천 점수 및 최고점 순위는 `notification-service`에서 처리한다.
A 코드로 B가 사전 등록 완료 시 A 추천 1점 + B 최초 코드 사용 보너스 1점이며, 총점은 추천 인원 수 + 보너스(최대 1점)다.
이 서비스의 이벤트 응모 순번·랜덤 추첨·추천 토큰 매칭으로 사전 등록 보너스를 다시 부여하지 않는다.
이벤트 시작 1~2시간 전 SES 알림 발송도 notification-service의 후속 범위다.

## 목표 규모

- DAU 10,000명, 목표 처리량 1,000 QPS (10분 지속)
- 원래 Go 검토됐으나 팀 내 Go 경험 부재 + 일정 리스크로 **Java 21 + Virtual Thread**로 결정

## 패키지 구조

```
com.desyp.event
├── entry
├── ratelimit
├── rank
└── global          (이 서비스 전용: Redis 커넥션, 보안 설정 등)
```

`BaseErrorCode`, `BusinessException`, `ApiResponse`는 이 서비스에 만들지 않는다 — `common` 모듈(`com.desyp.common`) 것을 가져다 쓴다.

## 기술 스택

- 배포 대상: k3s (스팟 인스턴스, HPA)
- Redis (순번 발급, rate limit, Stream 영속화 큐)
- PostgreSQL (파드, 최종 저장)

## 응모 정책 — 확정된 것

- **인당 응모 횟수 무제한 허용.** 이유: DAU 10,000명으로 지속 QPS 1,000(10분)을 채우려면 인당 평균 60회 이상 필요 — 1인 1회 제한으로는 물리적으로 목표 QPS 도달 불가
- 대신 **매크로 방지는 서버 사이드 rate limit으로만** 방어한다. 클라이언트 딜레이(예: 1초 sleep)는 스크립트로 우회 가능해 방어 수단으로 인정하지 않는다
- Rate limit 규칙: 동일 Instagram ID 기준 **최소 요청 간격 1초** (Redis에 마지막 요청 timestamp 저장 후 서버가 강제)

## 데이터 모델 — entries 테이블

```sql
CREATE TABLE entries (
  seq          BIGINT PRIMARY KEY,
  insta_id     TEXT NOT NULL,        -- UNIQUE 제약 없음 (무제한 응모 허용)
  submitted_at TIMESTAMPTZ NOT NULL,
  token_hash   CHAR(64),             -- 추천 전환 집계용 (notification-service invite_token 해시)
  ip_hash      CHAR(64),
  created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_entries_insta_id ON entries (insta_id);  -- rate limit 조회 및 통계용
CREATE INDEX idx_entries_token ON entries (token_hash);
```

## Redis 설계 — 순번 발급 + rate limit

기존 v0.2 "인당 1회 차단" Lua 스크립트를 "속도 제한" 방식으로 교체:

```lua
-- KEYS[1] = seq:{e}          (순번 카운터)
-- KEYS[2] = stream:{e}       (영속화 큐)
-- KEYS[3] = lastReq:{e}      (hash: instaId -> 마지막 요청 timestamp)
-- ARGV[1] = instaId
-- ARGV[2] = tokenHash ('' 허용)
-- ARGV[3] = 현재시각(ms)
-- ARGV[4] = 최소 간격(ms, 기본 1000)
-- 반환: 양수 = 발급된 순번 / -1 = rate limit 거부

local last = redis.call('HGET', KEYS[3], ARGV[1])
if last and (tonumber(ARGV[3]) - tonumber(last)) < tonumber(ARGV[4]) then
    return -1
end

local n = redis.call('INCR', KEYS[1])
redis.call('HSET', KEYS[3], ARGV[1], ARGV[3])
redis.call('XADD', KEYS[2], '*', 'seq', n, 'id', ARGV[1], 'tk', ARGV[2])
return n
```

- 당첨 판정: 애플리케이션에서 `n == 1`, `n == 10000` 비교 (기존 방식 유지)
- **"10,000번째"는 이제 10,000번째 참여자가 아니라 10,000번째 클릭이다.** 사전 공지 문구에 이 차이를 명확히 반영할 것

## 아직 결정 안 됨 — 재검토 필요

- **Redis+Stream 비동기 배치 → PostgreSQL 적재 구조**를 그대로 쓸지, 다른 구조로 갈지 미확정 (v0.2 검증 이력 있음: 15,000 요청, 순번 중복·누락 0건 — 재사용 유력 후보)
- 무제한 응모로 총 요청량이 커진 만큼, DB 비동기 적재 배치 크기·주기 재산정 필요
- 인당 무제한 응모가 §10 목표 수치("10,000명 목표") 산정 로직에 미치는 영향 재검토 필요

## 이 서비스만의 커밋 scope

| Scope | 적용 범위 |
| --- | --- |
| `entry` | 응모 처리, 순번 발급 |
| `ratelimit` | 매크로 방지, 속도 제한 |
| `rank` | 당첨 판정, 랜덤 추첨 |