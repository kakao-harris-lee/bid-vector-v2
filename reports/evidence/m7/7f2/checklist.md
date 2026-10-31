# M7/7F 본편 — 점검 체크리스트

## §0 Phase 1 설계 메모 (코드 0, 2026-10-22, 레인 w-7f2)

base main `bee7c36f`. 이 절은 설계만 담는다 — 저장소 코드·설정 변경은 팀장 승인 뒤 Phase 2 에서 한다.
근거 표기: 이 slice 가 **편집하지 않는** 파일은 `file:line`, Phase 2 에서 **편집할** 파일(`docker/*`·`tools/local-tls-*`·
runbook)은 낡는 좌표를 피해 인용문·절 제목으로 적는다(CLAUDE.md 「낡는 좌표 금지」).

### 0.1 출발 사실

| 사실 | 근거 |
|---|---|
| 7F 행의 범위: 도메인(R-3)·DNS·프록시+Let's Encrypt 자동 갱신·6C compose 확장(web+app+ml+db)·백업 리허설·헬스·로그·알림·비밀값 주입·runbook. **운영 host 반입과 public DNS 전환은 별도 승인** | `milestone-7.md:62` |
| 완료 조건: 「운영 host 에서 one-command 반입과 되돌림 리허설 증거(반입 자체는 별도 승인 뒤)」 | `milestone-7.md:119` |
| 별도 승인 목록에 **운영 DB 전환**·채널 발송 활성화·legacy 데이터 삭제가 있다 | `milestone-7.md:131` |
| R-3 = 「현 legacy 서버에 compose 로 반입 … 그 host 의 재사용」, 「서버 담당자 협의」 | `milestone-7.md:89` |
| 저장소는 운영 host 를 모른다 — 「개발 장비는 서버를 모른다 … 저장소에는 호스트·계정이 들어갈 자리가 없다」 | `docs/discovery/decisions-2026-09-07.md:14` |
| 7F-local 의 프록시는 `tls internal` · https 리스너 하나(평문 「거부」) · HSTS 미송신 · 상류 둘(web·app API 포트) · `/actuator*` 404 · 로그 헤더 삭제 | `docker/proxy.Caddyfile` 의 「`tls internal`」「`auto_https disable_redirects`」「HSTS 를 로컬에서 보내지 않는다」 블록 |
| 7F-local 의 덧쓰기는 publish 를 proxy 하나·루프백으로 줄이고 망을 `edge`/`default` 로 가른다. proxy 인증서는 볼륨 없음(컨테이너 수명) | `docker/compose.local-tls.yaml` 의 「우회 ①」「F-1」 주석 · `reports/evidence/m7/7f/checklist.md` 제한 5 |
| app·postgres 의 모든 값은 `${VAR:?}` — 파일·`.env` 에 값 0 | `docker/compose.yaml` app `environment` 주석 「값이 하나도 이 파일에 없다」 |
| ml-job 볼륨 백업은 「M7」로 미뤄져 있다 | `docker/compose.yaml` 「백업은 이 slice 가 들지 않는다」 · `docs/runbook/m6-6b4-ml-job-journal.md:90` |
| app 은 ml-serving 을 부르지 않는다(자리지킴, `depends_on` 없음) | `docker/compose.yaml` app 의 「ML 주소 변수도 두지 않는다(D-6A2a-6)」 |
| 세션 쿠키 `Secure` 기본 참, `bidvector.access.cookie-secure=false` 로만 내려간다 | `app/src/main/kotlin/bidvector/app/http/SessionCookieWriter.kt:17-18` · 7D 제한 6 `reports/evidence/m7/7d/checklist.md:366` |
| 로그인 열린 경로는 `POST /api/session` 하나 | `app/src/main/kotlin/bidvector/app/http/RequestAuthentication.kt:129` |
| app 코드에 forwarded·원격 주소 참조 0건(2026-10-22 재실측, `app/src`·`adapters/src` grep) | 7F-local 제한 11 `reports/evidence/m7/7f/checklist.md:183` 와 같은 결과 |
| web 번들의 env 입력은 `VITE_API_BASE_URL` 하나(기본 `/`) | `web/src/api/client.ts:32` |
| 백업·복원·리허설 도구가 있다(컨테이너 인자, 자격 값 무입력) | `tools/db-backup.sh:1-30` · `tools/db-rehearsal.sh:1-15` |
| caddy 취약점 allowlist 8줄 만료 2026-12-15, 만료 시 **네 스캔 전부** 붉어진다 | `reports/evidence/m7/7f/checklist.md:180-182`·`:220` |

**host 관찰(읽기 전용, 2026-10-22 15:22 KST, 판정 근거가 아니라 질문 근거)**: 이 개발 WSL host 에 legacy
`bid_vector_*` 컨테이너 7개가 「Exited 2 weeks ago」 상태로 남아 있다(legacy 중지 2026-10-03 과 시기가 맞다). 같은
host 의 0.0.0.0:80·0.0.0.0:443 은 **이미 다른 프로세스가 bind** 하고 있다(`ss -ltn`). 어느 프로세스인지는 다른
프로젝트 영역이라 보지 않았다.

### 0.2 R-3 의 host — 저장소 근거로 확정할 수 없다 (질문 Q-1)

저장소는 「서버를 모른다」고 명시한다(decisions:14). 한편 legacy 컨테이너가 **이 개발 host** 에 있었다는 관찰은
「legacy 서버 = 이 WSL」일 가능성을 보인다. 두 근거가 엇갈리므로 추측하지 않는다. 설계는 host 와 무관하게 쓰고,
host 가 정해지면 바뀌는 자리(아래 ★)만 질문에 매단다.

### 0.3 산출물별 설계

#### 1. 운영 프로필 compose — 덧쓰기 셋째 파일

- **구성**: `docker compose -f compose.yaml -f compose.local-tls.yaml -f compose.prod.yaml`. 로컬 프로필을 **그대로 깔고**
  그 위에 운영 차이만 얹는다 — 망 분리·publish 제거·`no-new-privileges`·상류 env 가 구조상 같은 정의를 공유하므로
  「7F-local 과의 구성 등식」이 복사가 아니라 상속이 된다. 기본 파일·로컬 덧쓰기의 바이트는 바꾸지 않는 것이 목표.
- **prod 덧쓰기가 바꾸는 것(만)**:
  - 이미지 태그: 모든 `image:` 를 `bidvector/<svc>:${BIDVECTOR_RELEASE_TAG:?}` 로. **`:local` 을 덮지 않는다**(다른 레인
    재생 태그 보호 — 계약 「검증·동결」). 태그는 git SHA 파생.
  - proxy publish: `0.0.0.0:443→컨테이너 8443`(★ 80 여부는 Q-2). 컨테이너 리스너는 비-root 그대로 고포트.
  - proxy 인증서 저장: named volume `bidvector-proxy-data:/data` (ACME 계정키·인증서 영속 — 로컬의 「잔여 0」과 반대 선택,
    재생성마다 재발급하면 LE 발급 한도에 닿는다).
  - proxy 설정 선택: 이미지에 site 파일 둘을 굽고 `command:` 로 운영 쪽을 고른다(아래 2).
  - `restart: unless-stopped`, 로그 드라이버 `json-file` 크기·개수 상한(값은 compose 선언, 매직값 아님).
  - ml-serving: ★ Q-5(app 이 부르지 않음 — 기본 미기동 profile 권고).
- **비밀값 주입**: host 의 env 파일 하나(저장소 밖, 예 `/etc/bidvector-v2/prod.env`, root 0600) → `--env-file`. 저장소에는
  **키 이름만 있는 템플릿**(`docker/prod.env.template`, 값 0)과, 「템플릿 키 집합 == 세 compose 파일이 요구하는 `${VAR:?}`
  집합」을 재는 등식 검사(구성이지 열거가 아니다). 반입 preflight 가 env 파일의 권한(0600)·저장소 밖 위치·
  `cookie-secure=false` 부재를 확인하고 하나라도 어긋나면 거부. compose 의 파일 마운트 방식은 app 쪽 설정 import 변경이
  필요해 이 slice 밖 — 대가(`docker inspect` 권한자에게 env 가 보인다)는 runbook 에 적는다.
- **도메인 값의 자리(계약의 판정 요청)**: **코드·이미지·compose 에 0. host env 파일 + runbook 문면에만** `bidvector.kr`.
  테스트는 예약 이름(`bidvector.test`)을 쓴다 — 실이름으로 로컬 테스트할 이유가 없고, 값을 저장소 코드에 넣으면
  도메인 교체가 코드 변경이 된다.

#### 2. ACME 자동 갱신

- **proxy 설정 분리**: 현재 `docker/proxy.Caddyfile` 의 공통부(로그 필터·헤더·`/actuator*` 404·라우팅)를 **공유 조각 하나**로
  옮기고, site 파일 둘이 그것을 `import` 한다 — 로컬(`tls internal`, HSTS 없음) / 운영(ACME, HSTS). **등식 검사**: 두 site 의
  `caddy adapt` JSON 에서 tls·HSTS·리스너 키를 뺀 나머지가 **같다**(구조 비교, 문자열 grep 아님). 로컬 쪽 adapt 결과는
  분리 전후 **동일**해야 한다(7F-local 스모크·게이트 보존의 측정).
- **운영 site 의 ACME**: `email {$BIDVECTOR_ACME_EMAIL}`(값은 env), CA 는 기본(LE) · `acme_ca`/`acme_ca_root` 를 env 로 바꿀 수
  있게 해 **테스트에서만** 로컬 ACME 로 돌린다. 챌린지 종류는 ★ Q-2. HTTP/3 끔(아래 3). HSTS 값은 Q-7.
- **실발급 없이 검증하는 범위 — 두 층**:
  1. 구성 lint: `caddy validate`·`caddy adapt` 를 운영 site 에 대해 매 실행(외부 호출 0, CI 가능).
  2. **로컬 ACME 테스트 서버(Pebble) 프로필**(`compose.acme-test.yaml`, 테스트 전용): Pebble + 그 DNS 대역을 compose 내부망에
    두고 proxy 를 Pebble 로 향하게 해 ⓐ 실제 ACME 주문→발급→Pebble 뿌리로 체인 검증 ⓑ 볼륨 영속(재생성 뒤 재발급 0) ⓒ 짧은
    유효기간으로 **갱신 실측** ⓓ Pebble 을 끈 상태에서 갱신 실패가 아래 감지 스크립트에 붉게 잡히는지. 외부 effect 는 Pebble
    이미지 pull 하나(레지스트리 다운로드 — caddy 이미지 pull 과 같은 성질). **이 층의 성립 여부(Pebble 의 유효기간 설정·
    TLS-ALPN-01 경로가 비-root 고포트에서 서는가)는 Phase 2 첫 탐침에서 판정**하고, 서지 않으면 lint 층만 남기고 제한으로 등재.
- **갱신 실패 감지**: `tools/prod-cert-check.sh` — 서빙 중인 인증서를 `--resolve 127.0.0.1` 로 읽어 남은 일수 < 임계(설정값,
  권고 21일 — Caddy 는 90일 인증서를 잔여 1/3 ≈30일에 갱신하므로 21일은 「9일째 갱신 실패 중」)면 exit 1, 판독 불가면 exit 2.
  주기 실행은 host 타이머(운영자 설치, runbook 문면). 알림 경로는 Q-4.

#### 3. 7F-local 이 넘긴 반입 조건 — 처분안

| 항목 | 처분 | 근거·방법 |
|---|---|---|
| cr L-4 XFF | **닫기** | 운영에서는 클라이언트가 보낸 XFF 를 덧붙이지 않고 proxy 가 **덮어쓴다**(접속 원격 주소로). 아래 유량 제한이 클라이언트 구분을 쓰는 순간 필요해진다. 측정: 위조 XFF 요청을 보내고 상류가 받은 값을 내부 echo 대역으로 확인(app 은 읽지 않으므로 대역 필요 — 탐침 방식은 Phase 2 에서 확정) |
| cr L-5 HTTP/3 광고 | **닫기** | 운영 site 는 `protocols h1 h2` 로 h3 를 끈다(UDP 443 을 열지 않으므로 광고할 이유가 없다). adapt JSON 으로 단언 |
| cr L-6 번들 인라인 env | **닫기(측정)** | 번들 입력 env 는 `VITE_API_BASE_URL` 하나(`web/src/api/client.ts:32`). web 빌드에 `VITE_` 접두 env 가 그 하나 밖에 들어가지 않음을 빌드 단계에서 단언하는 검사를 둔다. 위생 게이트 자체는 바꾸지 않는다 |
| cr L-7 캐시 키 전제 | **범위 밖 유지** | 위생 게이트 내부 성능·관례 사항이고 운영 반입과 무관. 7F-local 제한 14 그대로 |
| vr F-5 만료 결합 | **반입 조건 유지 + 날짜 결합 명시** | 운영 반입이 2026-12-15 이후면 그날 CI 네 스캔이 붉다. runbook 반입 절의 preflight 항목으로 「allowlist 만료일 > 오늘 + 14일」을 적는다(게이트 변경 없음) |
| 7D `/api/session` 유량 제한 | **★ Q-3** | 표준 caddy 에는 요청 수 제한 모듈이 없다(플러그인=자체 빌드, D-7F-2 가 택하지 않은 길). 플러그인 없이 가능한 것: `/api/session` 만 **별도 상류 전송의 동시 연결 상한**으로 보내 KDF 동시 실행 수를 묶는다(7D 가 남긴 축이 「CPU — 동시 로그인 N = KDF N」 `reports/evidence/m7/7d/checklist.md:369-377`). 클라이언트 단위 제한은 아니다 |
| 7D 제한 6 쿠키 Secure | **닫기** | 반입 preflight 가 env 파일의 `cookie-secure=false` 를 거부(위 1) |

#### 4. 백업·복원 리허설 · 헬스·로그·알림 · 반입/되돌림 스크립트

- **반입 방식**: 레지스트리가 없으므로 **번들**로 간다.
  - `tools/prod-bundle.sh`(개발 장비): 게이트를 통과한 이미지 ID 를 확인해 `docker save` + compose 파일 셋 + 템플릿 +
    manifest(git SHA·이미지 ID·파일 sha256) 묶음 하나. 태그는 `:<release>` 새로 붙임(`:local` 불변).
  - `tools/prod-deploy.sh`(host): **기본 dry-run**, `--apply` 없으면 상태 변경 0. preflight(compose ≥ v2.24 — `!override` 필요,
    7F-local 제한 9 / 포트 비어 있음 / env 파일 권한·키 집합 / 디스크 / manifest 해시) → **반입 직전 DB 백업 강제**
    (`db-backup.sh`) → 직전 release 기록 → `docker load` → `up -d` → 헬스 대기 → 사후 확인(https·인증서·`/actuator` 404).
  - `tools/prod-rollback.sh`: 기본 dry-run. 직전 release 태그로 `up -d`. **DB 는 이미지 되돌림으로 돌아오지 않는다**
    (forward-fix 정본, `docs/runbook/m6-6b2-backup-restore.md` §1) — 새 release 가 마이그레이션을 포함했으면 되돌림은
    「직전 백업 복원 + 그 사이 쓰기 손실」이고 스크립트가 그것을 판별해 **멈추고 묻는다**(자동 복원 없음).
- **리허설은 이 개발 host 에서, 외부 effect 0 으로**: 별도 프로젝트 이름(`-p bidvector-v2-prodrehearsal`)·루프백 고포트·
  Pebble ACME 로 bundle → deploy → (마이그레이션 없는 release 로) rollback → 재deploy 를 한 번 돈다. 「운영 host 에서의
  리허설」(완료 조건)은 승인 뒤 운영자 실행 몫으로 runbook 에 둔다. 6G 개발 DB(`bid-vector-v2-dev`)·포트는 건드리지 않는다.
- **백업 리허설**: 기존 `db-rehearsal.sh` 를 운영 프로필의 postgres 에 대해 재사용(새 구현 0). 더해 볼륨 둘(ml-jobs·proxy
  data)의 tar 백업·복원 등식 — 6B-4 가 M7 로 넘긴 축. 범위가 커지면 ml-jobs 는 Q-5 결과에 따라 뺀다.
- **헬스·로그**: `tools/prod-health.sh` — compose `(healthy)` 술어 · 외부 이름 https 왕복(`--resolve`) · 인증서 잔여일 ·
  디스크. 로그는 json-file 순환 상한 + proxy 접근 로그의 헤더 삭제 유지(7F-local (I) 축 그대로).
- **알림**: R-4 로 채널 발송이 꺼져 있다(`milestone-7.md:90` R-4). 기본안은 **종료 코드 + 로그 + runbook** 까지 — ★ Q-4.

#### 5. 운영 runbook

`docs/runbook/m6-6e-operations.md` 는 이미 1,090줄이라 **새 파일** `docs/runbook/m7-7f-production.md` 로 둔다(6E §9 에서 한 줄
링크). 절: 반입 절차(dry-run → 승인 → apply) · **DNS 전환 체크리스트(운영자 실행)** · 인증서 장애 대응(감지 → 원인 분기 →
LE staging 재시험 → 발급 한도 주의) · 되돌림(이미지 / DB 분기) · 백업 일정 · 알려진 제한. D-7F-13 이 미룬 runbook 반영(vr
R6-L1·L2)도 이때 6E §9.5 에 넣는다.

### 0.4 범위 밖

DNS 레코드 변경 · LE 실발급(staging 포함) · 운영 host 접속·반입·포트 개방 · 운영 DB 전환/개발 DB 데이터 이관
(`milestone-7.md:131`) · 채널 알림 발송(R-4) · app 레벨 유량 제한(Kotlin 코드, 필요하면 별 slice) · HSTS preload ·
CDN·정적 캐시 정책(7F-local 제한 7 유지) · caddy 자체 빌드 · 운영 host 의 수집(6G) 기동 · legacy 데이터 삭제 ·
`web/src/**`·Kotlin 소스·마이그레이션·OpenAPI.

### 0.5 운영자가 손으로 할 일 (저장소가 대신할 수 없다)

1. **host 확정과 접근**(Q-1) — 서버 담당자 협의, 접속 수단은 저장소 밖.
2. **등록기관에서**: `bidvector.kr` A 레코드(host 에 IPv6 가 있으면 AAAA, 없으면 AAAA 를 두지 않는다 — 있는데 닿지 않으면
   발급 실패), 전환 하루 전 TTL 을 300 으로 낮춤, 선택적으로 CAA(`letsencrypt.org`).
3. **포트**: 방화벽/보안그룹에서 inbound 443(★ Q-2 에 따라 80). host 가 WSL 이면 Windows 쪽 포트 포워딩·방화벽까지.
4. **ACME 연락 이메일** 하나(만료 경고 수신용).
5. **host env 파일 작성**(비밀값 — 저장소·채팅에 값 없음) · docker/compose v2.24+ 설치 확인 · 디스크 여유.
6. 번들 전송, 각 `--apply` 승인, 첫 발급은 **LE staging 으로 먼저**(runbook 순서) — 실패 반복 시 실 CA 발급 한도에 닿는다.
7. 인증서 확인 타이머 설치(runbook 문면).

### 0.6 질문 (팀장 → 필요하면 운영자)

- **Q-1 R-3 host**: 이 개발 WSL host 인가, 서버 담당자가 관리하는 별도 서버인가? 저장소는 「서버를 모른다」(decisions:14)
  인데 legacy 컨테이너는 이 host 에 있었다. **이 host 라면** 80/443 이 이미 다른 프로세스에 점유돼 있고(관찰), public 노출은
  Windows 포트 포워딩을 거친다 — 그 경우 설계의 publish·포트 preflight 를 바꿔야 한다.
- **Q-2 챌린지와 80 포트**: (a) 443 만, TLS-ALPN-01, 평문은 거부(7F-local 선택 유지) (b) 80 도 열어 HTTP→HTTPS 리다이렉트 +
  HTTP-01. 권고 **(b)** — 사용자가 `http://bidvector.kr` 을 치면 (a) 는 연결 실패로 보인다. (b) 면 프록시는 80 리스너가
  리다이렉트만 하고 다른 응답 0 임을 단언한다.
- **Q-3 `/api/session` 유량 제한**: (a) 플러그인 없는 동시 연결 상한(이 slice, KDF 동시성만 묶음) (b) 플러그인 자체 빌드
  (D-7F-2 번복) (c) app 레벨 별 Kotlin slice (d) 반입 조건으로 열어 둔 채 반입. 권고 **(a) 지금 + (c) 는 다수 사용자 전환 때**.
- **Q-4 알림**: 기본안(종료 코드 + 로그 + runbook, 외부 발송 0)으로 충분한가, 아니면 host 레벨 메일 등 하나를 열 것인가
  (외부 effect — 별도 승인).
- **Q-5 ml-serving**: app 이 부르지 않는다. 운영 프로필에서 기본 미기동(profile)으로 둘지, milestone 문면대로 띄울지. 권고
  미기동 + 이미지는 번들에 포함.
- **Q-6 운영 DB 초기 상태**: 빈 DB 로 반입하고 데이터 이관은 「운영 DB 전환」 별도 승인으로 두는 것이 맞는가.
- **Q-7 HSTS**: 권고 `max-age=86400`(includeSubDomains·preload 없음)로 시작해 안정 뒤 올린다. 값 확인.
- **Q-8 도메인 값**: 「코드·이미지 0, host env + runbook 문면만」 판정(0.3-1)을 승인하는가.

### 0.7 Phase 2 순서와 위험

순서(각 단계 커밋, 앞 단계 초록이 다음 조건):
1. proxy 설정 분리 + 로컬 adapt **분리 전후 동일** + 두 site 등식 검사 → 7F-local 스모크 재생 초록.
2. prod 덧쓰기 + env 템플릿 + 키 집합 등식 + adapt lint.
3. **Pebble 탐침**(성립 판정) → 서면 ACME 테스트 프로필·갱신·실패 감지 실측, 안 서면 제한 등재.
4. bundle/deploy/rollback(dry-run 기본) + 개발 host 리허설 1회.
5. 헬스·인증서 확인 스크립트, 볼륨 백업 등식.
6. runbook · CI 편입 판단(adapt lint·등식·dry-run 은 `container` job 후보, Pebble 은 비용 실측 뒤) · rollback.md(명령 도출) ·
   leakPatternGate · 판정 SHA.

위험:
- **R1** 설정 분리가 7F-local 게이트·스모크를 흔든다 → 1단계에서 adapt JSON 동일성으로 먼저 잠근다.
- **R2** Pebble 이 비-root 고포트·TLS-ALPN 조합에서 서지 않을 수 있다 → 탐침 먼저, 실패면 lint-only 로 정직하게 내린다.
- **R3** container job 30분 한도 — Pebble·리허설을 CI 에 넣으면 늘어난다. 소요 실측 뒤 판단.
- **R4** 이미지 되돌림 ≠ DB 되돌림 — 스크립트가 판별·정지, runbook 이 손실 창을 문면으로.
- **R5** allowlist 만료(2026-12-15)와 반입 날짜의 결합(F-5).
- **R6** 무거운 빌드는 호스트 직렬 규칙(pgrep·free·ps 별도 호출 → flock) 아래, 00:30~03:00 KST 금지. `:local` 태그 불변.

제안 in_scope(Phase 2 확정): `docker/proxy.Caddyfile`(분리) · 새 공유 조각·운영 site 파일 · `docker/proxy.Dockerfile` ·
`docker/compose.prod.yaml` · `docker/compose.acme-test.yaml` · `docker/prod.env.template` · `tools/prod-{bundle,deploy,rollback,
health,cert-check}.sh` · 키 집합·site 등식 검사 스크립트 · `tools/local-tls-smoke.sh`(분리 반영 시) · `docs/runbook/m7-7f-production.md`
· `docs/runbook/m6-6e-operations.md` §9 · 필요 시 `.github/workflows/ci.yml` `container` job · `config/quality/**`(Pebble 이미지를
게이트에 태울 경우) · `reports/evidence/m7/7f2/**`.

### 0.8 수정 설계 (D-7F2-1 — R-3 host = 이 WSL, host nginx 공존)

**host 사실(읽기 전용 실측, 2026-10-22 KST — `/etc/nginx` 쓰기·reload 0)**

| 사실 | 출처 |
|---|---|
| nginx 1.24.0(Ubuntu 패키지 `nginx`·`nginx-common` 1.24.0-2ubuntu7.18), systemd | `nginx -v` · `dpkg -l` |
| `sites-enabled` = `default`(80 `default_server`, 443 줄은 주석) · **기존 공개 site 파일 하나**(80 → 301, 443 `ssl` server 둘 — 하나는 루프백 상류로 프록시, 하나는 301). 내용은 저장소에 옮기지 않는다 | `/etc/nginx/sites-enabled/*` |
| **443 에 `default_server` 가 없다** → SNI 없음·모르는 이름의 443 요청은 **include 순서상 첫 443 server** 가 받는다(오늘 = 기존 site) | 같은 파일들 · `nginx.conf` 의 `include /etc/nginx/sites-enabled/*;` |
| 기존 site 의 인증서는 수동 발급 상용 인증서(경로 미기재), certbot 미설치, `/etc/letsencrypt` 없음 | 같은 파일 · `which certbot` |
| `stream` 은 **동적 모듈**로 빌드됐지만 모듈 파일이 설치돼 있지 않다(`/usr/lib/nginx/modules` 없음, `modules-enabled` 비어 있음). `http_realip`·`http_v2` 는 내장 | `nginx -V` |
| 메모리 22,016 MiB · swap 8,192 MiB · 16 코어. earlyoom 활성, `--prefer '^(java)$'`, `--avoid` 에 `postgres`·`caddy`·`nginx` | `free -m` · `/etc/default/earlyoom` |
| app 이미지에 JVM 메모리 플래그가 없다 → 힙 상한이 컨테이너 한도(없으면 host RAM)의 기본 비율로 정해진다 | `docker/app.Dockerfile`(`MaxRAM`·`Xmx`·`JAVA_TOOL_OPTIONS` grep 0) |

**설계 변경(두 안 공통)**: bidvector compose 는 0.0.0.0:80·443 을 **publish 하지 않는다**. proxy 하나만 **루프백 고포트**에
publish(7F-local 과 같은 모양, 포트 값만 운영 env). 0.3-1 의 「0.0.0.0:443 publish」를 철회한다.

#### (A) host nginx 가 TLS 종단 → 루프백 Caddy (권고)

- **host 쪽 변경**: 새 site 파일 하나(기존 site 파일 무변경). 80: `bidvector.kr` 의 `/.well-known/acme-challenge/` 만 webroot,
  나머지 301 https. 443: `bidvector.kr` 의 `ssl` server 가 `http://127.0.0.1:<proxy 포트>` 로 프록시. 인증서는 certbot(webroot,
  HTTP-01) — 설치·첫 발급·갱신 타이머·`deploy-hook` 의 `nginx` reload 는 운영자 수작업.
- **기존 site 무손상의 핵심 함정**: 443 에 `default_server` 가 없으므로 새 파일 이름이 기존 site 파일 이름 보다 **앞에** 정렬되면
  (`bidvector_kr` 이 그렇다) SNI 없는/모르는 이름의 443 요청 기본 대상이 기존 site → bidvector 로 **바뀐다**. 파일 이름을
  기존 site 뒤로 정렬되게 둔다(예 `zz-bidvector_kr`) — 기존 site 파일을 건드리지 않고 기존 기본 대상을 보존. 검증: 아래 nginx 하네스가
  「SNI 없음 → 기존 site 대역」 「기존 이름 → 기존 site 대역」을 변경 전후 동일로 단언.
- **7F-local 방어 중 Caddy 에 남는 것**: 상류 집합 둘·`/actuator*` 404·경로 정규화 우회 방어(접두 미절삭)·망 분리(web↛app)·
  접근 로그 헤더 삭제·보안 헤더(`defer` 덮어쓰기)·HSTS(Caddy 가 붙이고 nginx 는 통과). **nginx 로 옮겨 가는 것**: TLS·인증서·
  평문 처리(이제 「거부」가 아니라 80 → 301, Q-2 답). **새로 생기는 것**: nginx 가 자기 `Server` 헤더를 붙인다 —
  버전 표시 끄기 지시어로 버전만 지울 수 있고 이름은 남는다(제거 모듈 없음, 제한 등재). Caddy 의 TLS 는 운영 site 에서 꺼지고
  루프백 평문 리스너가 된다(구간이 host 루프백 안이라 수용, 대신 publish 가 `127.0.0.1` 임을 스모크가 단언).
- **L-4 XFF 신뢰 경계**: nginx 기본 관례(`$proxy_add_x_forwarded_for`)는 클라이언트가 보낸 값 뒤에 덧붙인다 → **쓰지 않는다**.
  nginx 가 `X-Forwarded-For $remote_addr`·`X-Real-IP $remote_addr` 로 **덮어쓰고**, Caddy 는 그 값을 신뢰하되 신뢰 출처를
  compose 의 host 쪽 게이트웨이 대역으로 한정(publish 가 루프백뿐이라 그 대역으로 들어오는 것은 host 프로세스뿐). 남는 경계:
  **host 위의 모든 프로세스**(WSL2 의 localhost 전달로 Windows 쪽 프로세스 포함)는 Caddy 에 직접 붙어 XFF 를 위조할 수 있다 —
  app 이 오늘 그 값을 읽지 않으므로 영향 0, 제한 등재.
- **Q-3 유량 제한**: (A) 에서는 nginx `limit_req`(키 `$binary_remote_addr`, `location = /api/session`)가 **클라이언트 단위**
  제한을 플러그인 없이 준다 — Caddy 동시 연결 상한((a))보다 7D 의 위협에 더 맞는다. 권고: nginx `limit_req` 를 주, Caddy 동시
  연결 상한을 보조(루프백 직접 접속 경로 대비). 존 선언은 site 파일 머리(http 문맥)에 둔다 — 존 이름은 기존 site 과 겹치지 않게
  접두를 단다.
- **ACME 산출물 재정의**: 계약 산출물 2 의 「프록시(Caddy)의 ACME」는 (A) 에서 「certbot + nginx」가 된다. 저장소가 드는 것은
  설정 조각·검증·runbook·`prod-cert-check.sh`(443 에서 `bidvector.kr` 인증서 잔여일 — 판독 자리가 nginx 로 바뀔 뿐)까지.
  Pebble 탐침은 「certbot 컨테이너 ↔ Pebble ↔ nginx 대역」으로 바꿔 Phase 2 에서 성립 여부를 판정한다.

#### (B) nginx `stream` SNI passthrough → Caddy 가 ACME 유지

- **침습도(높음)**: ⓐ `stream` 모듈 **패키지 설치**(`libnginx-mod-stream` 상당 — host 패키지 변경) + `load_module` ⓑ 443 을
  stream 이 쥐어야 하므로 **기존 site 의 `listen 443 ssl` 두 줄을 루프백 내부 포트로 옮겨야 한다**(기존 site 파일 수정) ⓒ 그러면
  기존 site 이 보는 클라이언트 주소가 127.0.0.1 이 된다 — 보존하려면 `proxy_protocol` + `set_real_ip_from` 을 기존 site 블록에 넣어야
  한다(추가 수정) ⓓ 모르는 SNI 의 기본 경로를 기존 site 으로 명시해야 오늘 거동이 유지된다.
- **챌린지**: TLS-ALPN-01 은 바이트 통과라 **동작한다**(ALPN `acme-tls/1` 이 그대로 Caddy 에 닿는다). HTTP-01 은 80 이 평문이라
  SNI 가 없어 stream 으로 가를 수 없다 → 80 은 nginx `http` 에 남고, `bidvector.kr` 의 challenge 경로만 Caddy 루프백 http 포트로
  프록시 + 나머지 301. 즉 (B) 도 80 에 nginx server 블록이 필요하다.
- **이점**: 7F-local 의 Caddy TLS·헤더·HSTS·ACME 가 그대로. **대가**: 운영자의 파일럿(기존 site)의 설정과 패키지를 바꾼다 —
  지시 「절대 깨면 안 됩니다」와 정면으로 부딪힌다.

#### 권고: (A)

기존 site 파일·패키지 무변경, 변경 단위가 「새 파일 하나 + `nginx -t` + reload」로 되돌림이 파일 삭제 + reload 다. (B) 의 이점(ACME 를
Caddy 가 쥠)은 기존 site 의 listen·클라이언트 주소·패키지를 바꾸는 대가에 비해 작다. (A) 에서 잃는 것은 「Caddy 가 TLS 를 쥔다」는
7F-local 의 구조 하나와 `Server: nginx` 이름 노출이다.

#### nginx 조각의 검증 방법 (host `/etc/nginx` 쓰기 0)

- 저장소에 `deploy/nginx/zz-bidvector_kr.conf`(템플릿, 도메인·포트는 치환 자리) 하나.
- **컨테이너 하네스**: host 와 같은 판(nginx 1.24, 다이제스트 고정) 컨테이너에 ⓐ host 의 `nginx.conf`·`sites-enabled/*`
  **읽기 전용 사본**(인증서 경로는 자체 서명 대역으로 치환 — 실 인증서 파일은 복사하지 않는다) ⓑ 새 조각을 넣고 `nginx -t`
  ⓒ 기존 site 상류 대역 + 루프백 Caddy(운영 프로필, `bidvector.test`)를 띄워 **변경 전후 동일**: SNI 없음 → 기존 site · 기존 이름
  → 기존 site · `www` → 301. **새 거동**: `bidvector.test` 80 → 301(challenge 경로 제외) · 443 → web 셸 200 · `/api/*` 401 ·
  `/actuator*` 404 · 위조 XFF 가 상류에 닿지 않음 · `/api/session` 버스트 → 429 · 헤더 등식.
- host 사본을 쓰는 이유: 실제 host 설정과의 결합(특히 default 대상)을 재려면 대역이 아니라 그 설정이어야 한다. 사본은
  실행 중 임시 디렉터리에만 두고 저장소·evidence 에 넣지 않는다(기존 site 설정은 우리 것이 아니다).
- runbook 운영자 절차: 파일 배치 → `sudo nginx -t` → 실패면 파일 제거로 끝 → 성공이면 `systemctl reload nginx` → 기존 site
  확인(위 동일 단언의 host 판) → bidvector 확인. 되돌림 = 파일 제거 + `nginx -t` + reload.

#### WSL 운영자 수작업 (runbook 에 추가)

Windows 쪽 inbound 80·443 을 WSL 로 넘기는 단계(포트 포워딩 또는 WSL 네트워킹 모드 설정 + Windows 방화벽)와 공유기/외부
방화벽 포워딩. 기존 site 이 이미 공개 중이므로 **그 경로가 이미 있을 가능성이 높다** — 운영자가 확인만 하면 되는지, 새로 여는지는
runbook 에서 「확인 → 없으면 설정」 순서로 둔다. WSL 재시작 시 IP 변동·포워딩 소실도 확인 항목.

#### 메모리 — 위험과 mem_limit 안

- **위험**: 운영 app 은 JVM 이고 이 host 의 earlyoom 은 **`java` 를 먼저 죽인다**(`--prefer '^(java)$'`). 개발 Gradle 빌드·다른
  세션의 큰 프로세스가 메모리를 밀어 올리면 **운영 app 이 첫 희생자**다(2026-09-24·09-30 사고 계열 — `~/.claude/CLAUDE.md`).
  `restart: unless-stopped` 가 복구하지만 그 사이 사이트가 502 다.
- **mem_limit 안(Phase 2 리허설에서 RSS 실측 뒤 확정)**: app 1.5 GiB + `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`(env, 이미지
  무변경) · postgres 1 GiB · proxy 128 MiB · web 64 MiB · ml-serving(기본 미기동) 1 GiB. 합 약 2.7 GiB(ml 제외).
- **운영자 판단 후보(이 slice 는 바꾸지 않는다)**: ⓐ earlyoom 대상에서 운영 app 을 가를 방법은 프로세스 이름뿐이라 `java`
  전체를 avoid 로 옮기면 Gradle 보호까지 꺼진다 — 권고하지 않는다 ⓑ 개발 빌드 쪽 한도(Gradle `-Xmx`·workers)를 낮추는 것이
  현실적 ⓒ 운영 반입 뒤 「무거운 빌드 전 확인 셋」에 운영 app 의 여유를 더하는 문면을 전역 지침에 넣을지.

#### 이전 질문 답의 반영

| Q | 반영 |
|---|---|
| Q-2 80 | (A): nginx 80 블록이 301 + certbot webroot challenge. Caddy 는 80 과 무관 |
| Q-3 | (a) Caddy 동시 연결 상한 지금 + **nginx `limit_req`**(주) 지금, (c) app 레벨은 후속 후보 |
| Q-4 | 종료 코드 + 로그 + runbook, 외부 발송 0 |
| Q-5 | ml-serving 은 profile 뒤(기본 미기동), 이미지는 번들 포함 |
| Q-6 | 빈 DB 반입. legacy `bid_vector_*` 컨테이너·볼륨은 읽지도 지우지도 않는다 — 반입 preflight 는 이름 교차만 본다(우리 프로젝트 이름 `bidvector-v2` 접두와 겹치지 않음) |
| Q-7 | HSTS `max-age=86400`, includeSubDomains·preload 없음 — Caddy 가 붙임 |
| Q-8 | 도메인은 host env 파일 + runbook 만, 테스트는 `bidvector.test` |

#### in_scope 변경(0.7 대비)

더함: `deploy/nginx/zz-bidvector_kr.conf`(템플릿) · nginx 하네스 스크립트(`tools/prod-nginx-check.sh`). 바뀜:
`docker/compose.acme-test.yaml` → certbot·Pebble 탐침용(성립 시). 운영 site 의 Caddy 는 ACME 대신 루프백 평문 리스너.

#### 이력 재작성 (D-7F2-3)

이 절의 첫 판에 있던 공존 서비스 식별자를 팀장 지시로 unpushed 이력에서 지웠다(재작성 뒤 base 이후 `git log -p` 계수 0).

## §1 Phase 2 — 구현과 측정 (D-7F2-2, 2026-10-22)

### 1.1 산출물

| 산출물 | 자리 | 무엇이 그것을 재는가 |
|---|---|---|
| 1 운영 compose | `docker/compose.prod.yaml`(셋째 덧쓰기, 프로젝트 `bidvector-v2-prod`) · `docker/prod.env.template` · `tools/prod-env.sh` | `tools/prod-env-keys-check.sh`(템플릿 == compose 참조 집합) · `tools/prod-proxy-sites-check.sh` (S3) |
| 2 TLS·인증서 (재정의: certbot + host nginx) | `deploy/nginx/zz-bidvector_kr.conf.template` · `tools/prod-nginx-render.sh`(`zz-` 강제 · `--http-only`) · `tools/prod-cert-check.sh` | `tools/prod-nginx-check.sh` N1~N9 |
| 2' proxy site 분리 | `docker/proxy.site.caddy`(공유 본문) · `docker/proxy.Caddyfile`(로컬) · `docker/proxy.prod.Caddyfile`(운영) · Dockerfile·dockerignore · 위생 정책(내용 하한 1→3) | `tools/prod-proxy-sites-check.sh` (S1)(S2) · 로컬 adapt JSON 분리 전후 바이트 동일 |
| 3 넘겨받은 조건 | 아래 1.4 | — |
| 4 반입·되돌림·헬스 | `tools/prod-release.sh` · `tools/prod-deploy.sh` · `tools/prod-rollback.sh` · `tools/prod-health.sh`(셋 다 기본 dry-run) | 개발 host 리허설(1.5) |
| 5 runbook | `docs/runbook/m7-7f-production.md`(신규) · 6E §9 연결 + 7F-local 이월 제한 19·20 | 절 안의 명령은 위 진입점뿐이다 |
| CI | `container` job 끝에 S-27(키 등식) · S-27b(site·compose 구조) · S-27c(nginx 하네스, 대역 host 설정) | `tools/workflow-step-refs-check.sh` 통과(job 4 · id 15 · 참조 18 · 매달린 참조 0) · container job step 24 → **27**(내 것 셋, 라벨 충돌 0) |

**설계에서 바뀐 것 둘**(구현 중 발견): ⓐ 프로젝트 이름을 `bidvector-v2-prod` 로 갈랐다 — 운영 host 가 개발 host 라 개발 레인의
`bidvector-v2` 스택 `down -v` 가 운영 DB 볼륨을 지울 수 있었다. 루프백 포트도 개발 스모크 기본값(18443)을 피해 runbook 권장
28443. ⓑ 반입 묶음은 tar 가 아니라 **release 태그 + manifest** 다 — 같은 기계라 이미지 운반이 없다(runbook 제한 6).

### 1.2 측정 결과

| 축 | 결과 |
|---|---|
| 로컬 site 보존 | 분리 전 `docker/proxy.Caddyfile`(base) 과 분리 뒤 이미지의 로컬 site 를 같은 env 로 adapt → JSON **바이트 동일**(1,562 B compact · sha256 앞 16자리 같음 · 들여쓰면 5,556 B). **양성 대조**: 공유 본문에서 보안 헤더 한 줄을 뺀 사본으로 adapt 하면 1,535 B 로 **달라진다** — 이 비교가 둔감하지 않다 |
| `prod-proxy-sites-check` | exit 0 — 하위 라우트 4 등식 · HSTS 핸들러 1 · 운영 TLS 앱 없음 · h1 · Host 일치 1 · 도메인 비면 `domain-not-set.invalid` · compose: 프로젝트 이름 · publish proxy 1 루프백 1 · 기본 서비스 4 · ml profile · mem_limit·restart 전부 · `:local` 0 · 망 분리 · **environment 항목 23 · 값 없는 통과 키 0** |
| `prod-env-keys-check` | exit 0 — 키 18 |
| `prod-nginx-check`(대역) | exit 0 — 불변 3표지 · 첫 발급 전 판 · N2~N9 |
| `prod-nginx-check`(host 설정 사본) | exit 0 — 인증서 지시어 경로 2 · 기존 이름 2 · **불변 5표지** · 첫 발급 전 판 · N2~N9. 사본은 종료 시 삭제(실행 뒤 임시 디렉터리 0) |
| 위생 게이트(release 안에서) | app·web·proxy·ml-serving 넷 exit 0 — proxy 는 새 정책(내용 하한 3 · 필수 셋) |
| 취약점 게이트 | **넷 다 exit 0**(release 안에서 실제로 돌았다 — 건너뛰지 않았다). proxy·web 을 따로도 쟀다: 차단후보 0 · 미등재 0 · allowlist 전체 0 · 분석 패키지 181(하한 126) · OS 선언 등식 `alpine/3.23.7` |
| caddy 바이너리 | web·proxy 두 이미지의 `/usr/bin/caddy` sha256 **같다**(이미지 안에서 같은 stage 로 빌드) |

`prod-proxy-sites-check` 의 첫 실행은 붉었다 — Caddy 가 같은 조건의 `header` 지시어 둘을 **한 라우트의 핸들러 둘**로 합쳐서
「HSTS 라우트를 뺀다」가 공유 본문의 헤더까지 뺐다. 비교 단위를 핸들러로 바꿨다(스크립트 주석에 실측 근거).

### 1.3 변이 표 — 바꿔치우기, 전부 사본에서 복원(복원 뒤 `cmp` 동일)

통제군(변이 전 원본)을 매 묶음 앞에 둔다 — 대역 하네스 exit 0 · 키 집합 exit 0 · `zz-` 이름 렌더 exit 0.

| 변이 | 바꾼 것 | 결과 |
|---|---|---|
| **M1 앞 정렬**(팀장 필수) | 하네스의 조각 파일 이름 `zz-bidvector_kr` → `bidvector_kr` | **대역 exit 1 · host 설정 사본 exit 1** — **N1** 「조각을 넣자 기존 응답이 바뀌었다 — 파일 이름 정렬 또는 listen 이 기존 site 의 기본 대상을 가져갔다」 + **N3** 「테스트 도메인이 SNI 없는 요청과 같은 인증서를 받았다」 |
| M2 XFF 덧붙이기 | 템플릿 `X-Forwarded-For $remote_addr` → `$proxy_add_x_forwarded_for` | exit 1 — **N9** 두 줄: 「nginx 가 위조 XFF/X-Real-IP 를 상류로 보냈다」 + 「XFF 가 접속 주소 하나가 아니다(위조 값이 앞에 남았다)」 |
| M3 유량 제한 제거 | `limit_req` 줄 삭제 | exit 1 — **N7** 「분포가 기대와 다르다: 200 ×10」 |
| M4 버전 노출 | 버전 표시 끄기 지시어를 켬 | exit 1 — **N4** 「Server 헤더가 이름만이 아니다(버전 노출?)」 |
| M5 리다이렉트 제거 | 80 의 301 → 상류 프록시 | exit 1 — **N2** 「80 리다이렉트가 기대와 다르다: 200」 |
| M6 X-Real-IP 덮어쓰기 제거 | `proxy_set_header X-Real-IP` 줄 삭제 | exit 1 — **N6** 「nginx 경유에서 위조 값이 상류에 닿았다」 + **N9** 「nginx 가 위조 값을 상류로 보냈다」(두 겹을 따로 잡는다) |
| K1~K4 키 집합 | 템플릿 키 하나 삭제 · 죽은 키 추가 · 템플릿에 값 · compose 에 `$NAME` 표기 | 1 · 1 · 1 · **2**(못 읽는 표기는 판정 불가) |
| R1 렌더 이름 | 출력 이름 `bidvector_kr` | exit 1 — 「zz- 로 시작해야 한다」 |
| **F5-A 만료 임박 등재** | allowlist 에 만료 7일 뒤 등재 한 줄(버릴 clone 에 커밋 — 깨끗한 트리 게이트가 앞에 있다) | release preflight **exit 1** — 「가장 이른 만료가 6일 뒤다(< 14)」 |
| **F5-B 먼 만료 등재** | 같은 자리에 만료 200일 뒤 등재 한 줄 | preflight 가 「**등재 1 · 가장 이른 만료까지 199일**」 — 세는 쪽도 산다 |

### 1.3b 고침마다 — 고치기 전(RED) ↔ 고친 뒤(GREEN)

되돌리기 어려운 경로(반입·되돌림)의 고침은 **실제 실패 경로**로 쟀다. 상태 파일을 손으로 맞춘 재현은
마이그레이션 비대칭 하나뿐이고 그 자리에 표시했다. 프로브는 레인 전용 프로젝트·포트이고 6G 개발 DB·
host nginx·`:local` 태그에 닿지 않는다.

| 고침 | 재현 | 고치기 전 | 고친 뒤 |
|---|---|---|---|
| **H-1** 백업 판정 | 볼륨은 남기고 `stop postgres` → 반입 | 「DB 백업 대상: 없음」 · 계획에 백업 줄 **0** · exit 0 — **첫 반입과 같은 그림** | DB 상태를 넷으로 가른다 · 멈춘 DB 를 올려 백업(디렉터리 0→1 · index 1줄) · apply exit 0 |
| **H-2** 실패 뒤 되돌림 | env 의 JVM 플래그를 깨 앱이 못 뜨게 한 **진짜 실패** | deploy 1 · `current`=`previous` · **떠 있는 것은 새 release** · 안내받은 되돌림이 **exit 2**(막다른 문면) | `pending` 이 남고 같은 안내 명령이 dry 0 / apply 0 으로 복귀 · 되돌린 뒤 `pending` 0 |
| **H-2** 우회 경로 | 마이그레이션을 실은 release 가 떠 있을 때 `deploy --release <옛것>`(manifest 한 줄 — 상태 편집) | **exit 0** — 가드 없음 | **exit 3** · 백업 경로 표시 |
| **M-1** 셸 덮어쓰기 | env 파일 38443 · 셸 `BIDVECTOR_PROXY_HTTPS_PORT=18443`(그리고 개발 env 조각을 source 한 셸) | preflight 가 **18443** 으로 통과 | **38443** — 셸이 지지 않는다 |
| **F5-C** 들여쓴 등재 | 앞 공백 두 칸 + 만료 7일 등재(버릴 clone 에 커밋) | 정본 게이트 판독 **1** · release 판독 **0** · 「등재 0 — 건너뛴다」 exit 0 | **exit 1** — 「가장 이른 만료가 6일 뒤」 |
| **F5-D** allowlist 파일 부재 | 그 파일을 지운다 | 조용히 「등재 0」 exit 0 | **exit 2** — 부재를 등재 0 으로 읽지 않는다 |
| **M-3** 게이트 실패 뒤 재시도 | 위생 정책의 내용 하한을 99 로 올려 반드시 실패시킨다 | 태그 **4개**가 남고 같은 태그 재시도가 「이미 있다」로 막힌다 | 태그 **0** · 재시도가 막히지 않고 · 원인을 고치자 같은 태그로 `OK` |
| **L-1** 값 없는 통과 키 | compose `environment` 에 `KEY:` 하나 | S-27·S-27b 둘 다 **0**(텍스트 술어라 못 본다) | S-27b **exit 1** — 서비스.키 를 이름으로 · 항목 23→24 · 통과 키 0→1 |
| **L-2** 링크 탈출 | 저장소 **밖** 링크가 저장소 **안** 파일을 가리킨다 | 옛 술어 통과(논리 경로만 본다) | **exit 1** — 링크를 푼 경로를 문면에 적는다 |
| **L-3** 80 의 버전 노출 |  80 블록의 **버전 표시 끄기 지시어**를 켠다 | 어느 게이트도 잡지 못한다(N4 는 443 만 잰다) | N2 **exit 1** — 「80 의 Server 헤더가 이름만이 아니다: `nginx/1.24.0`」 |
| D1 태그 옮김 | 리허설 중 `proxy:<rel1>` 을 다른 이미지로 | deploy exit 1 — 「ID 가 manifest 와 다르다」 |
| D2 env 권한 | env 파일 0644 | deploy exit 1 — 「0600 이 아니다」 |
| D3 마이그레이션을 넘는 되돌림 | 현재 release manifest 에 마이그레이션 한 줄 추가 | rollback **exit 3** — 멈춤 + 반입 직전 백업 경로 표시 |

**M2 의 첫 실측은 초록이었다** — 그때 하네스는 끝에서 끝(echo 가 Caddy 뒤)만 쟀고, Caddy 가 신뢰하지 않는 출처의 XFF 를 스스로
버려서 nginx 의 결함이 가려졌다. 그래서 nginx 의 출력 자체를 재는 N9(조각 상류를 루프백 echo 로 바꾼 판)를 더했고 M2 가 붉어졌다.
반대로 **M6 은 N6 과 N9 가 함께 잡는다** — Caddy 는 `X-Real-IP` 를 통과시키므로 그 헤더에서는 nginx 의 덮어쓰기가 유일한 겹이고
(N6), nginx 가 내보내는 값 자체도 따로 센다(N9). 겹이 하나뿐인 자리는 그 하나가 빠지면 끝까지 샌다(runbook 제한 3).

**하네스 결함 둘을 측정이 드러냈다**: 인증서 지시어 재작성이 두 번째 단계에서 자기 결과를 다시 바꿔 **같은 server 블록이 다른
인증서**를 내 N1 이 붉었다(멱등으로 고침 — 불변 단언이 실제로 민감하다는 양성 증거이기도 하다). host 설정에서는 sites-enabled 의
절대 경로 링크를 host 에서 그대로 따라가 기존 이름을 0 으로 셌다 — 「기존 이름 0 이면 판정 불가(exit 2)」 하한이 그것을 멈췄고,
링크를 사본 안 경로로 옮겨 읽게 고쳤다(host 원본은 `cp` 한 번 외에 읽지 않는다).

### 1.4 7F-local 이 넘긴 반입 조건 — 처분

| 항목 | 처분 | 측정 |
|---|---|---|
| cr L-4 XFF | **닫힘** | nginx 덮어쓰기(N9) + Caddy 가 신뢰하지 않는 XFF 를 버림(N6 직접 접속) — 두 겹 각각 측정 |
| cr L-5 h3 광고 | **닫힘(구조)** | 운영 site 는 평문 h1(S2 `protocols ["h1"]`) — TLS 리스너가 없어 h3 광고 자체가 없다. nginx 는 h3 를 켜지 않는다 |
| cr L-6 번들 인라인 env | **설계 처분 유지 · 검사 미구현** | Phase 1 안의 「빌드 단계 단언」을 만들지 않았다 — `web/` 빌드 입력은 이 slice 의 in_scope 밖이고 입력 env 는 여전히 하나(`VITE_API_BASE_URL`). 반입 조건으로 남긴다 |
| cr L-7 캐시 키 | 범위 밖 유지 | — |
| vr F-5 만료 결합 | **release preflight 로 닫힘 — 다만 지금은 잴 입력이 0 이다** | 가장 이른 allowlist 만료 < 14일이면 `prod-release.sh` 가 멈춘다. 판독은 **정본 게이트와 같은 파일을 같은 방식으로** 한다 — 경로를 정책의 `allowlist.file` 에서 읽고, 앞 공백·CR 을 지운 뒤 보고, **파일 부재는 「등재 0」이 아니라 판정 불가**다. 지금 저장소의 등재는 **0** 이라 이 검사가 볼 만료가 없다 — 「오늘 초록」은 방어의 증거가 아니고, 검사가 살아 있다는 것은 양성 대조 넷이 진다(1.3 F5-A~D). 등재 0 일 때도 한 줄을 남긴다 |
| 7D `/api/session` 유량 제한 | **닫힘(nginx)** | N7 — 분당 10 + 버스트 5, 버스트 10 중 6 통과·4 429, 다른 경로 무제한. Caddy 동시 연결 상한((a))은 **만들지 않았다**: nginx 가 클라이언트 단위로 닫았고, 남는 루프백 직접 경로는 host 프로세스뿐이라 runbook 제한 3·4 로 둔다 |
| 7D 제한 6 쿠키 Secure | **닫힘(구조) — 두 겹** | ① env 파일은 compose 보간에만 쓰이고 app 이 받는 env 는 compose 가 **이름으로 고른 것**뿐이다. ② 그 「이름으로 고른 것」이 값 없는 통과 키면 셸 env 가 그대로 실리는데, S-27b 가 해석된 구조에서 그런 키를 **0 으로 단언**한다(값 없는 통과 키 0 / 항목 23). 더해 운영 스크립트는 템플릿 키를 셸에서 비우고 시작한다 |

### 1.5 반입 리허설 (개발 host, 외부 effect 0)

프로젝트 `w7f2-prodrehearsal` · 루프백 28443 · env 파일은 scratch(0600, 자격 값은 실행마다 생성) · 상태 디렉터리 scratch.
release 출처는 `--source-tag`(기존 `:local` 이미지에 태그만 더한다). **`--accept-no-vuln-scan` 을 쓰지 않았다** — trivy 를 레인
임시 디렉터리에 CI 와 같은 절차로 두어(1.6 ③) 취약점 게이트가 release 안에서 실제로 돌았다.

| 단계 | 결과 |
|---|---|
| release rel1 dry-run → apply | 0 → 0 · **위생 넷 + 취약점 넷 = 8 건 OK**(건너뜀 0) · manifest(커밋 · 이미지 ID 넷 · 마이그레이션 **21**) |
| deploy rel1 dry-run → apply | 0 → 0 · 「데이터 볼륨 없음 · 반입 기록 없음 → **첫 반입**」 · 네 서비스 `(healthy)` · `prod-health` OK |
| **정지 상태에서 반입**(v H-1 변이) | `stop postgres` 로 볼륨만 남긴 뒤 release rel2 → deploy rel2: 0 → 0 · 「데이터 볼륨 있음 · **postgres 멈춰 있음** → 올려 백업」 · **멈춰 있던 postgres 를 올려 백업 생성**(`backups/index` 1줄) |
| **백업이 복원되는가**(CR OQ-1) | 그 백업을 새 컨테이너·새 DB 로 `db-restore.sh` → 다시 `db-backup.sh` → `--compare`: **복원 + manifest 등식 OK**. 복원 컨테이너는 레인 라벨로 만들고 끝에 지운다 |
| **헬스가 진짜로 실패하는 반입 → 되돌림**(v H-2 변이) | env 의 JVM 플래그를 깨 앱이 뜨지 못하게 한다 → deploy **exit 1** · `pending` 에 시도한 release · `current` 는 마지막 성공분 그대로 · 떠 있는 것은 시도한 쪽. 이어서 rollback dry 0 → apply 0 으로 복귀하고 `pending` 이 비워진다 |
| D1·D2·D3·D3b | deploy **1**(태그 옮김) · deploy **1**(env 0644) · rollback **3**(마이그레이션 넘음) · deploy `--release <옛것>` **3**(우회 경로도 막힌다) |
| 인증 GET 300건(8 병렬, 프록시 경유 `/api/strategy`) | **200 ×300** |
| 정리 | `down -v` 뒤 컨테이너·볼륨·망 **0·0·0** · 리허설 태그 0 · `:local` 태그 **전 4 줄 · 후 4 줄 · ID 동일** |

> `:local` 전후 비교에는 **계수의 양성 대조**를 먼저 건다(4 줄). 앞 라운드의 측정 하네스는 태그를 `:local$` 로
> 걸렀는데 출력이 이미지 ID 로 끝나 **아무것도 잡지 못했고**, 「전후 동일」이 빈 집합 둘의 비교로 공허하게
> 초록이었다. 이번 수치는 그 대조를 통과한 실제 비교다.

메모리 실측(`docker stats`, 기동 직후 → 부하 뒤): app 292 → 319MiB(상한 1.5GiB) · postgres 42 → 44MiB(1GiB) · proxy 11 → 15MiB
(128MiB) · web 11 → 11MiB(64MiB). **승인안을 그대로 확정**한다 — 빈 DB·짧은 부하의 수라 상한을 낮출 근거가 아니다(runbook §9).

비용 메모: 취약점 게이트는 실행마다 빈 샌드박스에서 돌아 **매 스캔이 DB(약 131MB)를 새로 받는다** — release 하나에
여덟 번이다. 게이트의 격리가 만든 값이고 이 slice 는 바꾸지 않는다.

### 1.6 막혔던 셋 — 전부 풀렸다 (재판정)

1. **취약점 게이트 — 풀렸다(이 slice 가 아니라 main 이 풀었다).** 막았던 것은 caddy 바이너리 안의 Go stdlib finding 이었고, 그때는
   상류가 고친 판을 내지 않아 베이스 상향으로 닫히지 않았다. 그 사이 main 이 **caddy 바이너리를 이미지 안에서 직접 빌드**하도록
   바꿔(런타임 베이스도 alpine 으로) 네 kind 의 allowlist 등재가 **전부 0** 이 됐다. 재측정: proxy·web 둘 다 **exit 0 · 차단후보 0 ·
   미등재 0**. 이 slice 는 allowlist 를 바꾸지 않았다 — 바꿀 것이 없다.
   > 그 대가로 vr F-5 의 만료 검사가 **잴 입력을 잃었다**. 1.4 의 그 행과 1.3 의 F5-A·F5-B 를 함께 본다.
2. **`:local` 태그 재생 — 풀렸다.** 막았던 전제(다른 레인이 그 태그를 쓰고 있다)가 더는 성립하지 않는다: 재개 시점에
   `bidvector/web:local` 과 `bidvector/proxy:local` 은 **아예 없었다**(앞 chore 레인이 다른 이름으로 남기고 갔다). 그 둘만 굽는 step
   하나(`tools/local-tls-build.sh`)를 돌려 **덮은 태그 0** 으로 재생했고, `app:local`·`ml-serving:local` 은 **이미지 ID 가 리허설
   전후 같다**(돌리지 않았다 — 그 둘을 굽는 step 둘은 건너뛰었다). 레인 전용 태그를 새로 만들 필요가 없어졌다.
3. **리허설의 취약점 게이트 — 풀렸다.** CI 는 trivy 를 액션이 아니라 **릴리스 바이너리 + 저장소에 핀한 sha256 대조**로 얻는다.
   같은 절차를 레인의 임시 디렉터리에 재현하고 그 호출의 PATH 에만 얹었다(**host 변경 0**, 네트워크 내려받기 1 — 이미지 pull 과
   같은 성질). 그래서 이번 리허설은 `--accept-no-vuln-scan` 을 **쓰지 않았고** release 안에서 네 이미지의 취약점 게이트가 실제로
   돌아 넷 다 통과했다. 운영 host 의 영구 설치 여부는 여전히 운영자 질문이다(runbook).

### 1.7 알려진 제한 (등재)

runbook `m7-7f-production.md` §10 이 정본이다: ① 응답의 `Server: nginx` 이름 노출(팀장 지시로 등재) ② nginx → proxy 루프백 평문
구간(팀장 지시로 등재) ③ 루프백 직접 접속 경로(유량 제한 밖 · `X-Real-IP` 통과) ④ 유량 제한은 클라이언트 주소 단위 ⑤ DB 를
넘는 되돌림 미자동화 ⑥ 태그 + manifest 반입(같은 기계 전제 · `prune -a` 위험) ⑦ certbot·LE 는 운영자 수작업에서 처음 실측
⑧ **시간 기반 알림은 cron 등록이 있어야 발화한다 — 이 반입 절차는 그 등록을 만들지 않는다**(팀장 결정 D-7F2-8: 그 러너를 담은
변경이 main 에 들어오면 다음 갱신에서 타이머 절을 더한다).
더해서 runbook §10 에 ⑦b(ml-serving 을 묶음에 싣는 대가 — 실패 표면 하나).
이 evidence 에만: ⑨ cr L-6 검사 미구현(1.4) ⑩ 하네스는 IPv4 만 잰다(조각의 80·443 이 이제 **둘 다 IPv4 만** 듣게
맞췄으므로 선언과 구조가 같다 — 다만 IPv6 리스너를 **더하는** 변이는 여전히 잡지 못한다. host 는 지금 80 만 IPv6 로
듣고 443 은 듣지 않는다) ⑪ 리허설의 app·ml-serving 이미지는 이 worktree 가 굽지 않았다(다른 레인이 남긴 `:local` 을
태그만 바꿔 썼다 — 그 둘을 굽는 step 을 건너뛴 결과다) ⑫ vr F-5 의 만료 검사는 지금 **잴 입력이 0** 이다(1.4·1.6 ①)
⑬ 백업 복원은 **반입 직전 백업 하나**로 쟀다(1.5 CR OQ-1) — 운영 규모의 데이터·장시간 축적분으로는 재지 않았다.

### 1.8 레인 교훈

- **두 겹 방어는 겹마다 따로 재야 한다** — 끝에서 끝 측정은 바깥 겹이 안쪽 결함을 가린다(M2 첫 실측 초록).
- **입력이 사라지면 검사는 조용히 통과한다.** 상류가 allowlist 를 전부 치운 날, 만료 검사는 조건문 안에서 통째로
  건너뛰어졌고 종료 코드는 그대로 0 이었다. 「통과」와 「볼 것이 없었다」가 출력에서 같은 모양이면, 그 검사가 죽은
  날을 아무도 모른다 — **두 경우 모두 한 줄을 남기고**, 살아 있음은 양성 대조로 보인다.
- **막힌 것은 상류가 풀기도 한다.** 재개 레인에서 가장 먼저 할 일은 「막혔던 이유가 아직 있는가」를 실측하는 것이다 —
  이번엔 셋 중 셋이 이미 사라져 있었고(상류 상향 · 태그 부재 · 설치 절차), 새 우회를 고안할 필요가 없었다.
- **실행 중 컨테이너는 「데이터가 있는가」의 답이 아니다.** 멈춘 스택은 볼륨에 데이터를 그대로 두고도 「첫 반입」과
  같은 그림을 낸다. 존재를 묻는 질문에는 존재를 재는 술어(볼륨)를 쓴다.
- **성공했을 때만 쓰는 기록은 성공한 세계만 설명한다.** 가장 급한 순간은 실패한 세계이고, 그때 되돌림에 필요한 것은
  「무엇이 떠 있는가」다 — 그것을 적지 않으면 되돌림은 자기가 돌아갈 자리를 모른다.
- **문면이 가리키는 명령은 그 상태에서 실제로 돌려 봐야 한다.** 「되돌림을 검토한다」는 안내가 정작 그 상태에서
  거부됐다 — 문면과 거동이 따로 자란 자리였다.
- **같은 파일을 두 곳에서 다르게 읽으면 한쪽은 반드시 틀린다.** 판독을 복제하지 말고 정본에서 빌린다.
- **빈 집합끼리의 비교는 초록이다.** 이 레인의 측정 하네스가 태그를 거르는 술어 하나를 틀려 아무것도 잡지 못했고,
  「전후 동일」이 빈 집합 둘의 비교로 공허하게 통과했다(앞 라운드 보고의 그 줄을 이번에 정정했다). **계수의 양성
  대조를 먼저 세우지 않으면 비교 자체가 거짓말을 한다.**
- **리허설 중에는 작업 트리를 건드리지 않는다.** release 는 커밋 하나에서 만들어야 하므로, 리허설 도중의 evidence
  편집이 그 게이트에 걸려 뒤 단계를 전부 무너뜨린다(이번에 한 번 겪고 다시 돌렸다).
- **`pgrep -f <패턴>` 대기 루프는 자기 명령줄에 그 패턴이 있어 영원히 자기를 찾는다** — 이 레인의 대기 루프 둘이 그래서
  끝나지 않았다(작업 도구로 정지). 대기는 flock 으로 한다.
