# desyp-backend

사전 등록·추천 이벤트와 이벤트 당일 응모를 위한 Java 21 / Spring Boot 멀티모듈 백엔드다.

## 제품 정책

1. 사용자는 네이버 소셜 로그인 후 이메일과 휴대전화번호 제공에 동의하고, 만 14세 이상 확인·개인정보 수집·오픈 알림 수신에 모두 동의해야 사전 등록할 수 있다.
2. B가 사전 등록 시 A의 추천 코드를 입력하면 A는 추천 성공 1점, B는 추천 코드 사용 보너스 1점을 받는다.
3. **추천 점수 = 실제 추천 인원 수 + 추천 코드 사용 보너스(최대 1점)** 이다.
4. 최고점자가 여러 명이면 최고점 동률자 중 최종 1등 한 명을 무작위로 추첨한다.
5. 공개 결과에는 개인정보를 마스킹한 1등 한 명만 표시하고, 관리자는 당첨자 연락용 이메일을 조회한다.
6. 운영자가 당첨자에게 직접 연락해 5만 원 이하 상품을 확인하고 발송한다. 상품 선택 API는 만들지 않는다.
7. 메인 이벤트 시작 1시간 전에 사전 등록자에게 이메일을 발송한다.

사전 등록 인원으로 메인 이벤트 참여 규모를 예측하기 위해 로그인 제공자를 네이버로 제한한다. 네이버 계정이 달라도 정규화된 휴대전화번호가 같으면 한 사람으로 보고 중복 등록을 차단한다. 동일 네이버 계정·정규화 이메일 중복, 자기추천, 등록 후 추천인 변경도 차단한다. 추천에는 이메일 같은 개인정보 대신 무작위 추천 코드를 사용한다.

휴대전화번호는 중복 참여 확인에만 쓰고 연락에는 사용하지 않는다. 사용자에게도 그렇게 고지하므로 관리자 API를 포함한 어떤 응답에도 내려보내지 않는다. 당첨·이벤트 안내는 이메일로만 한다.

사전 등록은 **2026-10-14T23:59:59+09:00**(메인 이벤트 하루 전)에 마감한다. 개인정보는 이벤트 종료 후 30일 이내 파기한다.

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

### 브라우저 고유값 (DevOps 요청, 미구현)

메인 이벤트 페이지는 브라우저별 고유값을 만들어 `localStorage`에 저장한다. 이 값은 사용자가 응모 버튼을 누를 때마다 보내는 응모 요청에만 함께 보내고, 로그인 등 다른 API 요청에는 보내지 않는다. 요청 추적과 트래픽 분석용이다.

```js
const KEY = 'desyp_client_id';
function getClientId() {
  try {
    let id = localStorage.getItem(KEY);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(KEY, id);
    }
    return id;
  } catch {
    // 시크릿 모드·저장소 차단 시 페이지 수명 동안만 유지
    return (window.__desypClientId ??= crypto.randomUUID());
  }
}

// 응모 버튼 클릭마다 호출
entryButton.addEventListener('click', () =>
  fetch(entryUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Client-Id': getClientId() },
    credentials: 'include',
    body: JSON.stringify({ instaId }),
  }));
```

- 헤더 이름은 `X-Client-Id`, 값은 UUID v4다. 서버는 UUID 형식이 아니면 무시하고 요청은 계속 처리한다.
- API는 별도 도메인이므로 CORS `Access-Control-Allow-Headers`에 `X-Client-Id`를 추가해야 한다.
- 사용자가 지우거나 위조할 수 있는 값이다. 인증, 중복 응모 차단, 매크로 방어, 당첨 판단에 쓰지 않는다. 1초 요청 간격 제한은 계속 서버가 Instagram ID 기준으로 적용한다.
- 저장소를 지우거나 브라우저·기기가 바뀌면 새 값이 생기므로 사용자 수와 일치하지 않는다.
- Instagram ID와 함께 저장하면 개인 식별에 쓰일 수 있으므로 저장·보관 기간과 개인정보 처리방침 반영 여부를 구현 전에 정한다.

## 구현 상태

| 영역 | 상태 |
| --- | --- |
| 네이버 단일 로그인 | 구현 완료 |
| 휴대전화번호 저장·중복 차단 | 구현 완료 |
| 이메일 사전 등록 | 구현 완료 |
| 추천 점수·공동 순위 | 구현 완료 |
| SES 알림 | 관리자 수동 트리거 구현 완료 |
| 1시간 전 자동 발송 | EventBridge Scheduler 미구현 |
| 최고점 동률 추첨·결과 공개 | 미구현 |
| 이벤트 당일 응모 | 설계 단계 |
| 브라우저 고유값(`X-Client-Id`) | 설계 단계 |
| 사전 등록 페이지 | Vercel 배포 ([desyp-event](https://github.com/DesyProject/desyp-event)) |
| 메인 이벤트 페이지 | 비공개 S3 + CloudFront OAC 사용 확정 |
| 사전 등록 마감(410) | 구현 완료 |
| 세션 공유 | Spring Session JDBC(PostgreSQL) 구현 완료 |
| 과도한 요청 차단(429) | Nginx 설정 작성(`deploy/nginx.conf`), 운영 적용 전 |
| EC2 배포 | 설정 작성(`deploy/`, `.github/workflows/deploy.yml`), 서버 미구축 |
| 이벤트 종료 후 30일 내 파기 | 미구현 |

## 프로젝트 구성

- `common`: 공통 응답과 예외
- `notification-service`: 사전 등록, 추천 점수·순위, 이메일 발송
- `event-entry-service`: 이벤트 당일 응모. 현재 코드 없음

개발 규칙과 상세 설계는 [CLAUDE.md](CLAUDE.md), 에이전트 작업 규칙은 [AGENTS.md](AGENTS.md)를 참고한다.

## 실행

Java 21과 PostgreSQL을 준비하고 다음 환경 변수를 설정한다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
- `NAVER_REDIRECT_URI`: 운영 `https://api.desyp.site/auth/naver/callback`, 로컬 `http://localhost:8080/auth/naver/callback`. 네이버 개발자센터 Callback URL과 같아야 한다
- `SES_SENDER_EMAIL`, `AWS_REGION`: SES 클라이언트 생성에 리전이 필요하다. 로컬도 `ap-northeast-2` 등 값을 넣어야 서버가 뜬다
- `SPRING_PROFILES_ACTIVE=oauth`. 로컬은 `oauth,local`이며 `local` 프로필이 CORS·`return_to` Origin `http://localhost:5173`, 로컬 redirect-uri, 비Secure 세션 쿠키를 설정한다
- `FRONTEND_ORIGIN`: 기본 `https://www.desyp.site`. 로컬 프런트는 `http://localhost:5173`
- `REGISTRATION_END_AT`: 기본 `2026-10-14T23:59:59+09:00`

네이버 Callback URL은 `{백엔드 외부 주소}/auth/naver/callback`(예: `https://api.desyp.site/auth/naver/callback`), 서비스 URL은 `https://www.desyp.site`다. 백엔드는 프런트와 같은 사이트인 `api.desyp.site` 같은 하위 도메인에 두어 `SameSite=Lax` 세션 쿠키를 쓴다. 네이버 개발자센터에서 이메일과 휴대전화번호 제공 권한을 신청·활성화해야 한다. 운영에서는 HTTPS와 Secure 세션 쿠키를 사용하고, 로컬 HTTP에서만 `SESSION_COOKIE_SECURE=false`를 쓴다.

```sh
./gradlew :notification-service:bootRun
```

Swagger UI는 `http://localhost:8080/swagger-ui/index.html`에서 확인한다. 운영 서버는 systemd 설정에서 API 문서를 끈다.

## EC2 배포

`notification-service`는 EC2 한 대에서 `Nginx(HTTPS·429) → Spring Boot(127.0.0.1:8080)`로 운영한다. 설정 파일은 `deploy/`에 있다.

### 서버 초기 설정 (한 번만)

Amazon Linux 2023, t3.micro(메모리 1GB) 한 대에 PostgreSQL을 함께 설치하는 기준이다.

1. 탄력적 IP를 인스턴스에 연결한다. 자동 할당 공인 IP는 중지 후 시작하면 바뀐다.
2. 보안 그룹은 80·443을 공개하고 22는 배포에 필요한 범위로 제한한다. 8080과 5432는 열지 않는다.
3. SES 발송 권한(`ses:SendEmail`)이 있는 IAM 역할을 인스턴스에 연결한다. 액세스 키는 서버에 두지 않는다.
4. 메모리가 1GB라 스왑 2GB를 추가한다.
   ```sh
   sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap defaults 0 0' | sudo tee -a /etc/fstab
   ```
5. 패키지와 사용자를 준비한다.
   ```sh
   sudo dnf install -y java-21-amazon-corretto-headless nginx certbot postgresql18-server
   sudo useradd --system --home /opt/desyp --shell /sbin/nologin desyp
   sudo install -d -o desyp -g desyp /opt/desyp
   ```
6. PostgreSQL을 비밀번호 인증으로 초기화하고 DB를 만든다. 기본 초기화는 localhost 접속을 비밀번호 없이 허용하므로 옵션을 지정한다. PostgreSQL은 기본값대로 localhost에서만 접속을 받는다.
   ```sh
   sudo PGSETUP_INITDB_OPTIONS="--auth-host=scram-sha-256 --auth-local=peer" postgresql-setup --initdb
   sudo grep -vE '^\s*(#|$)' /var/lib/pgsql/data/pg_hba.conf   # host 줄이 scram-sha-256인지 확인
   sudo systemctl enable --now postgresql
   sudo -u postgres psql -c "CREATE ROLE desyp LOGIN PASSWORD '<DB 비밀번호>'"
   sudo -u postgres psql -c "CREATE DATABASE desyp OWNER desyp"
   ```
   테이블은 앱 기동 시 Flyway가 만든다.
7. `deploy/notification.env.example`을 참고해 `/etc/desyp/notification.env`를 만들고 `chmod 600`한다. `DB_PASSWORD`는 6번에서 정한 값이다.
8. `api.desyp.site` DNS를 탄력적 IP로 연결한 뒤 인증서를 받는다. 갱신 때도 같은 훅이 쓰인다.
   ```sh
   sudo certbot certonly --standalone -d api.desyp.site \
     --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx"
   ```
9. 로컬에서 `scp -r deploy <EC2_USER>@<탄력적 IP>:~/`로 설정 파일을 올린 뒤 복사하고 서비스를 켠다. jar는 첫 자동 배포가 올린다.
   ```sh
   sudo cp deploy/nginx.conf /etc/nginx/conf.d/desyp-api.conf
   sudo cp deploy/desyp-notification.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable nginx desyp-notification && sudo systemctl start nginx
   ```
10. 배포 사용자가 비밀번호 없이 `sudo install`, `sudo cp`, `sudo systemctl restart desyp-notification`, `sudo journalctl`을 실행할 수 있게 한다.
11. 백업은 Amazon Data Lifecycle Manager로 EBS 볼륨 일일 스냅샷을 만든다. 스냅샷에도 개인정보가 있으므로 보관은 7일로 두고, 이벤트 종료 후 30일 내 파기 대상에 포함한다.

### 자동 배포

`main`에 push되면 `.github/workflows/deploy.yml`이 빌드·테스트 후 jar를 올리고 서비스를 재시작한다. 비로그인 `/api/me`가 `401`을 반환하면 기동 완료로 본다. 저장소 Secrets에 다음 값을 등록한다.

| Secret | 값 |
| --- | --- |
| `EC2_HOST` | EC2 공인 IP 또는 도메인 |
| `EC2_USER` | SSH 사용자(예: `ec2-user`) |
| `EC2_SSH_KEY` | 배포 전용 SSH 개인 키 |
| `EC2_KNOWN_HOSTS` | `ssh-keyscan <EC2_HOST>` 결과. 호스트 키를 고정한다 |

직전 jar는 `/opt/desyp/notification-service.jar.prev`에 남는다. 롤백은 이 파일을 `notification-service.jar`로 복사한 뒤 서비스를 재시작한다.

## API 흐름

사전 등록 페이지는 아래 세 API만 호출하고 모든 요청에 `credentials: 'include'`를 쓴다. CORS는 `FRONTEND_ORIGIN`만 허용하고 `Allow-Credentials: true`다. 오류 응답의 `message`는 프런트가 모달에 그대로 표시한다.

### 네이버 로그인

`GET /auth/naver/login?return_to=<URL>`

브라우저가 이동하는 주소다. `return_to`는 `FRONTEND_ORIGIN`과 scheme·host·port가 같을 때만 허용하고, 아니면 프런트 루트로 돌려보낸다. `state` 검증은 Spring Security가 한다. 로그인에 실패하면 `return_to`에 `?login=error&reason=<이유>`를 붙인다.

| reason | 의미 |
| --- | --- |
| `no_email` | 이메일 제공 거부 |
| `no_phone` | 휴대전화번호 제공 거부 또는 휴대전화 형식 아님 |
| `cancelled` | 사용자 취소 |
| `failed` | 그 밖의 실패 |

### 로그인 상태

`GET /api/me`

로그인 상태면 `200 {"emailMasked":"des***@naver.com","registered":false}`, 아니면 `401`이다. 프런트 계약에 맞춰 `ApiResponse`로 감싸지 않는다. 휴대전화번호는 내려보내지 않는다.

### 사전 등록

`POST /api/pre-registrations` (`Content-Type: application/json`만 허용)

```json
{"ageConfirmed":true,"agreePrivacy":true,"agreeMarketing":true,"referralCode":"7K2QM9XA"}
```

이메일과 휴대전화번호는 요청 본문이 아니라 네이버 프로필에서만 가져온다. `referralCode`는 선택이며 기존 등록자의 추천 코드와 일치해야 한다. 추천 코드는 혼동 문자(0·O·1·I·L)를 뺀 대문자·숫자 8자리이고, 앞뒤 공백과 소문자 입력은 정규화한다. 등록 성공 응답과 내 추천 점수 응답은 본인의 `referralCode`를 반환한다.

| 코드 | 의미 |
| --- | --- |
| `201` | 등록 완료 |
| `400` | 동의 누락, 요청 형식 오류 |
| `401` | 로그인 필요 또는 세션 만료 |
| `404` | 유효하지 않은 추천 코드 |
| `409` | 이미 등록한 네이버 계정·이메일·휴대전화번호 |
| `410` | 마감 이후 요청 |
| `415` | JSON이 아닌 요청 |

이 API는 CSRF 토큰을 요구하지 않는다. `SameSite=Lax` 세션 쿠키, JSON 전용 요청, 프런트 Origin만 허용하는 CORS 사전 요청으로 교차 사이트 요청을 막는다.

### 관리자 API

관리자 API는 CSRF 토큰이 필요하다. `GET /api/csrf` 응답으로 CSRF 헤더를 구성한 뒤 같은 세션으로 호출한다.

### 내 추천 점수

`GET /api/referrals/me`

로그인 계정의 추천 인원, 보너스, 총점을 반환한다.

### 관리자 추천 순위

`GET /api/admin/referrals/ranking?maxRank=10`

`ADMIN_NAVER_ACCOUNT_IDS` 허용 목록의 관리자만 사용할 수 있다. 응답에는 연락용 이메일이 포함되고 휴대전화번호는 포함되지 않는다. 공동 순위는 `1, 1, 3` 방식이며 경계의 동점자를 모두 반환한다. 현재는 실시간 순위 조회만 제공한다.

### 이벤트 알림 메일

`POST /api/admin/mail/event-start`

관리자가 호출하면 오픈 알림 수신에 동의했고 아직 알림을 받지 않은 등록자에게 SES 메일을 발송한다. 성공한 등록자만 `notified_at`을 기록해 재호출 시 중복을 막는다. 운영에서는 EventBridge Scheduler가 메인 이벤트 시작 1시간 전에 이 동작을 실행하도록 추가해야 한다.

## 빌드와 테스트

```sh
./gradlew build
```

테스트는 외부 계정 없이 H2 PostgreSQL 모드로 실행한다. 실제 PostgreSQL, 네이버 로그인 프로필 권한, SES 연결은 운영 환경에서 별도로 검증해야 한다.
