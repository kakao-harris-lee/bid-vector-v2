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
