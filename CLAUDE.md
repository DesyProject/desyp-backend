# desyp — 대규모 인프라 대응 프로젝트 (모노레포)

이 저장소는 두 개의 독립 서비스로 구성된다. 각 서비스 폴더 안의 CLAUDE.md에 서비스별 상세 규칙이 있다 — 여기는 두 서비스에 공통으로 적용되는 규칙만 담는다.

## 서비스 구성

| 서비스 | 역할 | 트래픽 특성 | 배포 대상 |
| --- | --- | --- | --- |
| `notification-service/` | 사전 등록, 추천 점수·순위, 메일 발송 (상시 트랙) | 2개월간 낮고 꾸준함 | AWS Lambda |
| `event-entry-service/` | 이벤트 당일 응모 처리 (이벤트 트랙) | DAU 10,000 / 순간 QPS 1,000 | k3s |

## 현재 제품 흐름과 구현 범위

1. Google·네이버 소셜 로그인으로 사전 등록한다.
2. A의 코드로 B가 사전 등록을 완료하면 A는 추천 성공 1점, B는 최초 코드 사용 보너스 1점을 얻는다.
3. 최종 순위 점수는 실제 추천 인원 수 + 코드 사용 보너스(최대 1점)다. 상품 기준 명칭은 **최고 추천 점수**로 통일한다.
4. 최고 추천 점수 동률자가 여러 명이면 그 동률 집단에서 최종 당첨자 1명을 무작위 추첨한다.
5. 운영자가 당첨자에게 직접 연락하고, 당첨자는 5만 원 이하의 지정 기프티콘 중 하나를 선택한다. 선택 브랜드는 올리브영, 메가MGC커피, 스타벅스, 다이소, 문화상품권, 투썸플레이스, 배달의민족, 이마트·신세계상품권, 쿠팡이다. 이벤트 페이지에는 이 목록을 안내용으로 표시하되 상품 선택용 사용자 API나 화면은 만들지 않는다.
6. 이벤트 시작 **1시간 전**에 등록자에게 이메일을 발송한다. SES 발송은 관리자 API로 트리거하도록 구현했고, EventBridge Scheduler 자동 트리거는 아직 미구현이다.
7. 다계정 추가 방지는 구현하지 않는다. 동일 계정·정규화 이메일 중복, 자기추천, 등록 후 추천인 변경만 차단한다.
8. 2026년 10월 초 서버 운영을 시작하고, 공개 메인 이벤트는 그로부터 2~4주 뒤에 연다.
9. 이벤트 결과/순번 팝업은 사용자가 `OK`를 눌러야만 닫히고 다음 화면으로 이동한다.
10. 이벤트 당일 응모에서 고정 순번과 랜덤 방식 중 어떤 방식으로 당첨/순번을 결정할지는 미확정이다. 이는 추천 점수 동률자 추첨 정책과 별개다.
11. 추천 상품 결과에는 최종 1등 한 명만 표시한다. 공개 응답은 마스킹된 표시명을 사용하고 관리자 응답만 연락 가능한 원본 정보를 포함한다.

현재 구현은 사전 등록, 양쪽 점수 반영, 내 점수 조회, 관리자 공동 순위 조회, 관리자 트리거 기반 메일 발송까지다.
추천 최고점 동률자 추첨, 운영자의 당첨자 연락·상품 지급, EventBridge Scheduler 자동 배포는 구현 완료로 표현하지 않는다.
세부 API·실행 방법은 `notification-service/README.md`, 도메인 규칙은 `notification-service/CLAUDE.md`를 따른다.

## 프론트엔드 전송 구조 — 확정

- 이벤트 페이지가 정적 빌드 가능한 전제에서 **S3 + CloudFront**를 사용한다.
- S3 버킷은 공개하지 않고 Block Public Access를 유지하며 CloudFront OAC만 읽을 수 있게 한다.
- HTML·JavaScript·CSS·이미지는 CloudFront에서 캐시하고 파일명에 콘텐츠 해시를 사용한다. `index.html`은 짧은 TTL로 배포 전환을 빠르게 한다.
- 응모·로그인 등 동적 API는 별도 API 도메인으로 분리하고 CloudFront 정적 캐시에 넣지 않는다.
- HTTPS를 강제한다. 필요 시 WAF/rate limit은 API 진입점에 별도로 적용한다.
- 서버 렌더링이 필수가 되는 경우에는 S3 정적 호스팅 결정을 재검토한다.

## 팀 구성

- DevOps A / DevOps B (2명): 인프라, CI/CD, 관측성, 부하 테스트
- Backend (1명): 두 서비스 도메인 로직
- Frontend (1명): 알림 신청 페이지, 응모 페이지, 마케팅 실행

## 공통 기술 스택

- Java 21, Spring Boot 4.1.1, Gradle-Groovy (모노레포 멀티모듈: `common`, `notification-service`, `event-entry-service`)
- 두 서비스 모두 Java로 통일 (Go 검토했으나 팀 내 경험 부재 + 일정 리스크로 제외, Java 21 Virtual Thread로 고동시성 대응)
- Spring Boot 4.x는 Jakarta EE 11 / Servlet 6.1 베이스라인 — `jakarta.*` 패키지 사용

## common 모듈

두 서비스에서 완전히 동일하게 쓰는 코드만 여기 둔다 (서비스별로 다른 설정은 각 서비스의 `global`에 남긴다).

```
com.desyp.common
├── exception   (BaseErrorCode, BusinessException, GlobalExceptionHandler)
└── response    (ApiResponse)
```

## 공통 코드 규칙

- 생성자 주입만 사용 (`@RequiredArgsConstructor`)
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@Builder`만. `@Data`, Entity의 `@Setter` 금지
- 트랜잭션 경계는 Service 계층에만
- Entity 상태 변경은 의미 있는 메서드로 (setter 직접 호출 금지)
- 예외는 `BusinessException` + 도메인별 `ErrorCode` Enum
- 로그는 SLF4J 파라미터 치환 방식, `System.out.println` 금지
- **개인정보(이메일 원문, invite_token, google_sub, Instagram ID 원문) 로그 절대 금지**

## 공통 네이밍 규칙

- Controller/Service/Repository: `<Domain><Layer>` 형식
- Entity: 단수형, `Entity` 접미사 미사용
- DTO: `record` 기본, Request/Response 접미사로 방향 명시
- ErrorCode: `<Domain>ErrorCode implements BaseErrorCode`

## 공통 커밋 컨벤션

```
<type>(<scope>): <description>
```

- type: `feat` `fix` `refactor` `test` `docs` `build` `ci` `perf` `chore` `revert`
- description: 한글, 명사형 종결, 마침표 없음, 50자 이내 권장
- scope 목록은 서비스마다 다름 — 각 서비스 CLAUDE.md 참고

## 공통 브랜치 컨벤션

- `main`(배포) / `develop`(통합)
- `feat/`, `fix/`, `refactor/`, `chore/`는 develop에서 분기, `hotfix/`는 main에서 분기 후 양쪽 반영
- 영문 소문자 + 하이픈

## 공통 코드리뷰

- PR 필수 리뷰(최소 1인 승인) — 4인 팀 구성 완료로 확정
- 자동 정적분석(Checkstyle/SpotBugs) 병행

## 공통 관측성

- Prometheus(메트릭) + Loki(로그) + Tempo(트레이스), Grafana 통합 시각화
- 계측 표준: OpenTelemetry (OTLP)
- AWS Lambda는 pull 방식 스크레이핑 불가 → OTel Collector로 push 구조 필요 (DevOps와 호스팅 위치·경로 확정 필요)

## 공통 보안 원칙

- 모든 외부 입력은 Request DTO 검증 → Service 도메인 규칙 검증 순서
- Secret은 코드/설정 파일에 하드코딩 금지
- HTTPS 강제

## Graphify

코드 구조 질문은 `graphify-out/graph.json`이 있으면 `graphify query "질문"`으로 먼저 탐색하고 근거 소스를 확인한다.
코드 변경 후 `graphify update .`, 문서까지 갱신할 때는 `/graphify . --update`를 사용한다.
그래프와 캐시는 로컬 전용이며 Git에 포함하지 않는다.
