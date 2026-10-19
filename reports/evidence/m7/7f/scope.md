# M7/7F-local — 로컬 패키징: web 정적 번들 + 리버스 프록시 + 로컬 TLS (착수 계약, 2026-10-16, 팀장)

- **근거**: `milestone-7.md` 7F 의 **로컬 절반**. 운영자 지시 2026-10-16 「병렬로 진행 가능한 M7 작업」이 7F 로컬 패키징을 골랐다.
  도메인·운영 host(R-3)가 정해지기 전이므로 **로컬에서 재현 가능한 부분만** 한다. `milestone-7.md` 「선행 조건」의 **운영자 예외**로 연다.
- **base**: main `77850903` · worktree `bid-vector-v2-m7-7f` · 브랜치 `m7-7f/2026-10-16`.
- **외부 effect 0**: Let's Encrypt·DNS·운영 host 에 닿지 않는다. TLS 는 로컬 CA(프록시의 내부 발급) 또는 실행 시 생성하는 자체 서명으로 한다.
  운영 host 반입과 public DNS 전환은 별도 승인 사항(7F 본편).

## 산출물

1. **web 이미지**: `web/` 빌드 산출(`dist/`)을 싣는 정적 서버 이미지 Dockerfile(`docker/` 아래). 이미지 위생은 6C 기준(고정 태그·
   non-root·금지 패키지 0)을 따른다.
2. **리버스 프록시**: compose 에 프록시 서비스 하나. `/` → web 정적, `/api/**` → app. 프록시 하나만 host 포트를 연다(app·db·ml 은 내부망).
   보안 헤더(HSTS 는 로컬 TLS 에서의 처리를 적는다 · `X-Content-Type-Options` · `Referrer-Policy` · CSP 는 web 번들이 실제로 쓰는 출처에 맞춘
   최소). 도메인은 환경변수(기본 `localhost`) — 운영 도메인 값은 저장소에 넣지 않는다.
3. **compose 확장**: 기존 `docker/compose.yaml`(6C)의 서비스·볼륨·이름(`name: bidvector-v2`)을 깨지 않고 web·proxy 를 더한다. app 의 host
   포트 노출이 바뀌면 그 영향(기존 runbook·test·`tools/*.sh`)을 전수하고 같은 slice 에서 맞춘다.
4. **스모크**: `tools/` 아래 스크립트 하나가 compose 를 올리고 프록시 https 로 ① web 셸이 뜨고 ② `/api/**` 가 app 에 닿아 인증 없는 요청이
   401 을 받는지(기존 필터) 확인하고 내린다. CI 의 `container` job 에 넣을지는 비용과 함께 판단해 적는다(넣으면 ci.yml 명령과 one-command
   축어 일치).
5. **runbook**: 로컬 실행·인증서 신뢰 방법·알려진 제한(운영 TLS 아님)을 `docs/runbooks/` 의 기존 6C 문서에 절로 더한다.

## 설계 검토 (Phase 2.5, 팀장 — 경량)

- **(0) 위협 모델**: 방어 — 내부 서비스(app·db·ml)가 host 에 직접 노출되는 것 · 프록시 경로 규칙의 틈(`/api` 밖의 app 경로 노출, 경로
  정규화 우회 `/api/../actuator`) · 관리 포트 노출 · 이미지 위생 퇴행. 방어하지 않음 — 운영 TLS·인증서 수명(7F 본편) · DDoS.
- **(2) 우회**: ① host 에서 app·db 포트 직접 접속 → 연결 거부 ② `/actuator/**` 등 관리 경로를 프록시로 → 차단 ③ 경로 정규화 우회 → 차단
  ④ http 평문 → https 리다이렉트 또는 거부(하나로) ⑤ web 이미지가 root 로 실행 → 실패. 각각을 스모크 또는 test 로 잰다.

## in_scope / out_scope

- in: `docker/**`(새 Dockerfile·프록시 설정·compose) · `tools/` 의 새 스모크 스크립트 · `docs/runbooks/` 의 6C 문서 절 · 필요하면
  `.github/workflows/ci.yml` 의 `container` job(계약 갱신과 함께) · `reports/evidence/m7/7f/**`.
- out: `web/src/**` · Kotlin 소스 · 마이그레이션 · OpenAPI · 운영 도메인 값.

## acceptance

`ci.yml` 의 `container` job 명령 그대로 + 새 스모크(로컬). `check` job 은 `tools/one-command-check.sh` 를 바꾸면 돈다. `web` job 은 `web/` 입력
변경 0 이면 생략.

## rollback

`git restore --source=77850903 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일 삭제. 실측은 마지막 산출물 커밋에서 ①~⑥
(⑥ 게이트에 `container` job 포함). 로컬 compose 를 내린 뒤 볼륨·네트워크 잔여 0 을 확인한다(`down -v` 가 다른 프로젝트 볼륨을 건드리지
않음 — 6B-2 의 이름 충돌 교훈).

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. Codex 없음.

## 호스트 빌드

docker build·compose up·Gradle 은 모두 `flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다(레인 넷 병렬). 점검 셋은 별도 호출로 먼저.
00:30~03:00(6G 수집 창)에는 무거운 작업을 시작하지 않는다 — **6G 수집이 쓰는 개발 DB 컨테이너(`bid-vector-v2-dev`)와 그 포트를 건드리지
않는다**(새 compose 프로젝트의 포트·이름이 겹치지 않음을 먼저 확인).

## 계약 갱신 이력

**D-7F-1 (2026-10-16, 팀장 — 구현 보고 수령).**
- **CI 편입 승인**: 스모크를 `container` job 의 마지막 step(S-26)으로 둔다. `tools/one-command-check.sh` 는 바꾸지 않았다(축어 대상은
  check·ml-engine·web 세 job).
- **경로 정정**: runbook 은 `docs/runbooks/` 가 아니라 실재하는 `docs/runbook/`(`m6-6e-operations.md` §9).
- **compose 는 덧쓰기 파일**(`docker/compose.local-tls.yaml`)로 확장한다. 기본 파일과 기존 step 은 바꾸지 않는다.
- **미달 하나를 이 slice 에서 닫는다**: 산출물 1 은 「6C 기준(고정 태그·non-root·금지 패키지 0)」을 요구한다. web·proxy 이미지는 아직 금지
  패키지·취약점 판정을 받지 않는다. 그래서 `config/quality/` 의 이미지 kind 별 정책 파일(기존 app·ml-serving 의 kind 정책과 같은 형태)을
  in_scope 에 더하고, 두 이미지를 기존 위생·취약점 스크립트에 태운다. 게이트 설정이므로 verifier 표적 재검증 대상이다. 허용·예외 줄마다
  근거를 적는다.
**D-7F-2 (2026-10-16, 운영자 승인 — 취약점 예외 첫 등재).**
- 베이스를 `caddy:2.11.4` → `2.11.7`(현재 최신)로 올렸다. 차단 후보는 21 → 4 로 줄었다.
- 남은 4건은 모두 HIGH 이고 caddy 바이너리 안에 있다:
  - Go stdlib(빌드 toolchain 1.26.8) 셋: CVE-2026-78667 · CVE-2026-78669 · CVE-2026-97031
  - `golang.org/x/net` 0.59.0 하나: CVE-2026-78669
- 우리가 그 바이너리를 빌드하지 않으므로 이미지 쪽 조치는 베이스 상향뿐이고, 이미 최신이다.
- **운영자 결정**: `config/quality/vuln-allowlist.properties` 에 4건 × kind 둘(web·proxy) = **8줄**을 등재한다.
  - 만료는 **2026-12-15**(정책 상한 90일보다 짧다).
  - 사유: 「caddy 바이너리를 우리가 빌드하지 않는다 — 상류가 수정된 Go toolchain·모듈로 재빌드한 릴리스를 내면 베이스 상향으로 해소된다」.
  - 이 등재로 허용 목록의 「등재 0」 상태가 바뀐다.
  - 쓸모없어진 등재는 게이트가 붉히므로, 상류가 먼저 고치면 그 자리에서 치운다.
- 직접 빌드(xcaddy)와 전역 `scan.pkg-types` 축소는 택하지 않았다.

**D-7F-3 (2026-10-19, 팀장 — 검토 r1 수령: verifier not-ready · code-reviewer APPROVE).**
- **in_scope 명시 등재(cr M-6)**: `tools/image-hygiene-check.sh` · `config/quality/**`. D-7F-1 이 정책 파일 자리만 말하고 스크립트는
  이름으로 들지 않았다 — 이번 판에 경로로 등재한다. 기존 kind(`jvm-app`) 판정 코드의 helper 치환은 verifier r1 이 base↔HEAD 바이트
  동일 출력·같은 종료 코드(세 조합)로 보존을 실측했다. 이 파일을 다시 고치면 같은 등식을 재실측한다.
- **수정 라운드 r1 에서 닫는 것**
  - **F-1(high) — web 경유 관리 경로**: 구조로 닫는다. 덧쓰기 compose 에서 네트워크를 갈라 web 이 app 에 닿지 못하게 한다(proxy 만 두
    네트워크). 스모크는 「web 컨테이너에서 app 이 해석·접속되지 않는다」를 양성 대조(같은 탐침이 proxy 에서는 닿는다)와 함께 단언하고,
    web 설정에도 (A3) 과 같은 상류 집합 단언을 건다. verifier 의 변이(web 에 `/healthz` → app reverse_proxy)가 RED 여야 한다.
  - **F-2(medium) — 키 재료 탐색 범위**: proxy 위생의 키·인증서 재료 부재를 `/etc/caddy` 한 자리가 아니라 Caddy 의 데이터·설정 자리
    (`/data`·`/config` 포함)까지 — 가능하면 이미지 파일시스템 전체로. 음성 대조: `/data/caddy/pki/...` 에 구운 키 → exit 1.
  - **헤더(cr M-2·M-3 · vr F-3)**: `/api/**` 응답도 잰다 · 이름마다 정확히 1개 · `Server` 부재 · 값은 등식(CSP 는 완화 어휘 금지
    구성 또는 전체 등식, Referrer-Policy·X-Frame-Options 값).
  - **cr M-1**: `(healthy)` 괄호 포함 술어로 — 기존 `ci.yml` S-23 의 같은 형태도 같은 커밋에서(ci.yml container job 은 in_scope).
  - **cr M-4**: (F) 는 응답 코드와 본문을 쌍으로 판정.
  - **cr M-5**: 이미지 빌드 정의를 compose 한 자리로 — CI S-21c·S-21d 를 compose build 로 바꾸고, 게이트가 판정한 이미지 ID 와 스모크가
    실행한 이미지 ID 의 등식을 단언한다.
  - **vr F-4**: 우회 ④ 는 계약이 「리다이렉트 또는 거부(하나로)」라 적었다 — 지금 구현이 고른 하나만 통과하도록 스모크를 좁힌다.
  - **low 중 바로 닫는 것**: cr L-1=vr F-6(정책 주석 순서) · cr L-2(관리 포트 등식) · cr L-3(`no-new-privileges` 단언) · cr L-8(문면) ·
    vr F-7(`root:root` 포함).
- **알려진 제한으로 등재(닫지 않음)**: vr F-5(web 등재 하나의 만료가 app·ml-serving 스캔까지 붉힌다 — runbook 에 문면) · cr L-4(XFF,
  앱이 forwarded 헤더를 읽지 않아 오늘 영향 0 → 7F 본편 반입 조건) · cr L-5(HTTP/3 광고) · cr L-6(번들 인라인 env 경계) · cr L-7(캐시
  키 전제). 7D 검토에서 나온 「`/api/session` 유량 제한」은 7F 본편 반입 조건 후보로만 적는다(이 slice 범위 밖).
- 게이트 술어를 바꾸므로 수정 뒤 verifier 표적 재검증 r2. rollback ①~⑥ 은 새 마지막 산출물 커밋에서 다시 잰다.

**D-7F-4 (2026-10-19, 팀장 — 수정 r1 수령 · 동결).** 판정 대상은 마지막 산출물 `9f4e3385` · evidence `67d1cae1` 다.
- **in_scope 추가를 수용한다**: `tools/local-tls-build.sh` · `tools/local-tls-env.sh`. cr M-5 를 닫으면서 빌드 진입점과 환경 해석을 한 자리로
  모은 파일이다. rollback 복원 목록은 15 → 17 이 됐다.
- **D-7F-3 의 cr M-5 문면 정정**: 「S-21c·S-21d 를 compose build 로」는 실제로 **두 step 이 진입점 하나(S-21c)로 합쳐지고 S-21d 가 없어진**
  것으로 읽는다. 그래서 container job 의 run step 은 22 다(base 16 대비 +6).
- **레인이 보고한 관찰 둘을 기록한다**: ⓐ 「헤더 이름당 1개」 축은 오늘 아무것도 운동시키지 않는다 — app 이 그 네 이름을 보내지 않아
  `defer` 를 떼도 초록이다. 회귀 가드로만 둔다. ⓑ 저작 중 키 재료 축의 이름이 스캔 어휘와 겹쳐 개명했고(`9f4e3385`), evidence 한 줄의 축어
  인용으로 게이트가 붉어져 교정했다(`67d1cae1`). 이력은 되쓰지 않았다.
- 검토: verifier 표적 r2(F-1·F-2·헤더·M-5 이미지 ID 등식·F-4·jvm-app 보존·rollback 17) + code-reviewer 표적 r2.

**D-7F-5 (2026-10-19, 팀장 — 검토 r2 수령: verifier not-ready · code-reviewer APPROVE).** 재작업 2/5.
- r1 지적 일곱은 닫혔다(vr r2 실측). container job 의 base run step 은 **16** 이다(17 은 중간 상태였다).
- **수정 r2 에서 닫는 것**
  - **vr R2-H1(high) — 매달린 step id**: S-22e2·S-22e3 의 `if:` 가 없어진 id(`build_web`·`build_proxy`)를 가리킨다. 그래서 web·proxy 취약점
    게이트가 매 CI 실행에서 건너뛰어지고 SBOM 도 올라가지 않는다. 조건을 현재 id 로 고친다. 재발은 **구조로** 막는다 — job 안의 `if:` 가
    가리키는 모든 `steps.<id>` 가 그 job 에 정의된 id 인지 재는 검사를 CI 에 둔다(새 도구를 들이지 않는 작은 검사를 우선). 매달린 id 를 심은
    변이가 RED 여야 한다. evidence 의 「S-22e2 exit 0」은 `if:` 를 평가하지 않는 재생에서 나왔으므로, 재생 하네스가 `if:` 를 평가하게 고치고
    「건너뛴 step 0」을 함께 적는다.
  - **cr R2-1 · R2-2 · R2-3 · R2-4 · R2-5(=vr R2-L2) — 키 재료 검사**: 제외 접두를 `/etc/ssl/certs/` 로 좁힌다. 확장자 열거에 더해 PEM 머리 줄
    **내용** 탐지를 한 겹 둔다. 경로 일치는 끝 고정으로, 제외 접두는 리터럴 비교로 한다. 제외 목록은 선언된 집합과의 **등식**으로 잠가 표적
    제외(`/data/` 하나 추가)도 붉게 한다. 음성 대조: `/etc/ssl/private/` 의 키 → 1 · 확장자 없는 PEM 키 → 1 · `/data/` 제외 추가 → 1.
  - **low**: vr R2-L1(runbook 에 「입력을 고쳤으면 `local-tls-build.sh` 를 먼저」) · cr R2-6(「게이트가 본 바이트 = 실행 바이트」 전제를 코드에) ·
    R2-7(인자 처리) · R2-8(도구 preflight) · R2-9(jq 대입 실패 문면) · R2-10(`edge` 를 `internal: true` 로 — 스모크 초록일 때만, 아니면 제한).
  - **runbook 한 줄(cr 권고)**: 망 분리가 닫은 것은 web 경로뿐이다. proxy→app 관리 포트의 방어는 설정층 둘((A3) 상류 등식 · (E) 404)이다.
- 수정 뒤 verifier 표적 r3(R2-H1 구조 검사 · 키 재료 · rollback) + code-reviewer 표적 r3.

**D-7F-6 (2026-10-19, 팀장 — 수정 r2 수령 · 동결).** 판정 대상은 마지막 산출물 `d492ca9e` · evidence `3f2df512` 다.
- **in_scope 추가를 수용한다**: `tools/workflow-step-refs-check.sh`(S-20z). R2-H1 의 재발을 구조로 막는 검사다. 복원 목록은 17 → 18 이다.
- container job 은 `if:` 를 평가하는 재생으로 ran 23 · skipped 0 이다(S-20z 추가로 22 → 23, base 16 대비 +7).
- **자리 후속 둘(이 slice 밖)**: ⓐ 워크플로 참조 검사는 워크플로 전체를 보므로 `check` job(= `one-command-check.sh` 축어 경로)이 더
  맞는 자리다. 그 두 파일이 in_scope 밖이라 이번에는 `container` job 맨 앞에 두었다. ⓑ R2-6 은 `tools/vuln-scan-check.sh` 가 기록하는
  이미지 ID 를 쓰는 편이 더 정확하다. 그 파일도 in_scope 밖이라 빌드 기록 방식으로 닫았다.
- **레인이 잡은 사고 하나를 기록한다**: 내용 탐지 첫 판의 `find -size -1M` 을 BusyBox 가 거부해 탐침 전체가 조용히 0건이었다. 종료 코드로는
  가려지지 않아 「훑은 파일 수」를 양성 대조로 세게 했다.

**D-7F-7 (2026-10-19, 팀장 — 검토 r3 수령: verifier ready-for-review · code-reviewer APPROVE).** 재작업 3/5. 남은 medium 넷은 모두 게이트
술어라서 승인 전에 닫는다(마지막 배치).
- **키 재료 — 읽지 못한 파일(vr R3-M1)**: 내용 탐지가 이미지의 실행 사용자로 돌아서 root 소유 0600 키를 읽지 못한다. 읽기 오류는
  삼켜지고, 「훑은 파일 수」는 목록만 세므로 잡지 못한다. 탐침은 root 로 돌리고, 그래도 읽지 못한 파일이 있으면 **판정 불가(exit 2)** 다.
  양성 대조는 「목록 수」가 아니라 「실제로 읽은 수」로 센다. 음성 대조: 0600 키(확장자 없음 · `/etc/ssl/certs/` 아래) → 1.
- **워크플로 참조 검사 — 텍스트 술어(vr R3-M2 · cr R3-1 · R3-2 · R3-3)**: 표기만 바꾸면 매달린 참조가 지나간다(블록 스칼라 `if:` · 색인형
  `steps['id']` · `run:`/`env:` 안의 `${{ steps.x… }}` · 끝 점 없는 참조). 또 `run:` 본문 안의 `id:` 한 줄이 가짜 id 를 만든다. **새 의존 없이
  저장소 도구 체인에 이미 있는 YAML 파서**로 job 별 step id 를 정의 자리에서만 걷고, 모든 문자열 값에서 두 표기를 찾는다. 그런 파서가 CI
  러너에 보장되지 않으면 fail-closed 로 간다 — 지원하지 않는 표기는 exit 2, id 는 step 항목 자리에서만 센다. 위 표기 변형 각각이 RED 여야 한다.
- **low 중 닫는 것**: cr R3-4(하한 근거 실측치) · R3-5(이미지 부재 분기를 대조 앞으로, 문면을 사실대로) · R3-6(내용 탐지 앵커 제거 — 값으로
  박힌 키) · R3-7(내용 축 양성 대조는 자기 모집단의 하한) · R3-8(제외 집합 불일치는 정책 오류 exit 2) · R3-9(정책 둘의 제외 목록도 서로 등식).
- **알려진 제한으로 등재**: vr R3-L1(크기 상한을 넘는 확장자 없는 키 — 실행 파일과 가를 기준이 없다) · vr R3-L2(`needs.<없는 job>` 은
  S-20z 범위 밖 — 오늘 `needs` 없음) · cr R3-10(이미지당 세 번 훑음 — 성능 기록만).
- 수정 뒤 verifier 표적 r4. 판정이 high·medium 0 이면 PR 로 간다.

**D-7F-8 (2026-10-20, 팀장 — 마지막 배치 수령 · 동결).** 판정 대상은 마지막 산출물 `ee7f6fda` · evidence `5487dbe3` 다.
- **참조 검사는 fail-closed 갈래로 갔다**: 저장소의 YAML 파서(`pyyaml`)는 `ml-engine` job 의 uv 환경에만 있고, 이 검사가 도는 `container`
  job 에는 없다. 새 의존 없이(awk) 가되, 읽지 못하는 표기(블록 스칼라 `if:` · 흐름 매핑 step)는 통과가 아니라 exit 2 다. id 는 step 항목
  자리에서만 세고, 참조는 job 안의 모든 줄에서 찾는다.
- **레인이 찾은 결함 하나를 기록한다**: `docker image inspect --format` 은 없는 이미지에 빈 줄을 찍고 실패한다. 그래서 `… || echo none` 이
  개행 섞인 값을 냈고, r1 이 둔 「태그 없음」 가지가 한 번도 타지 않았다(R3-5 수정에서 함께 닫혔다).
- 자리 후속(D-7F-6 ⓐ)은 그대로다. 제외 목록이 게이트 상수 + 정책 둘 세 자리에 사는 구조도 유지한다 — 등식으로 잠겨 있고, 한 자리로 모으려면
  정책 로더 변경이 필요하다(이 slice 밖).
- 검토: verifier 표적 r4 + code-reviewer 표적 r4. 둘 다 high·medium 0 이면 PR.

**D-7F-9 (2026-10-20, 팀장 — 검토 r4 수령: verifier ready-for-review · code-reviewer APPROVE).** 재작업 **4/5** — 이번 배치 뒤에도 medium
이 남으면 계속 돌리지 않고 운영자에게 PR 로 갈지 판단을 묻는다.
- **닫는 것(작은 배치)**
  - **vr R4-M1 = cr R4-1**: 이름 축의 파일 목록도 root 로 걷는다. 그래도 `find` 의 권한 오류가 나면 판정 불가(exit 2)다. 음성 대조: `/root/.cert/client.p12`
    (0600 · 디렉터리 700) → 1.
  - **vr R4-M2 + cr R4-2**: 참조 검사가 읽지 못하는 표기를 0 으로 통과시키는 자리 셋 — YAML 앵커·별칭(따옴표 밖의 `&`·`*`) · `jobs:` 아래
    들여쓰기 2 의 줄 중 job 머리 패턴이 아닌 것(따옴표 친 job 키 등) · 동적 색인(`steps[` 뒤가 리터럴 문자열이 아닌 것). 셋 다 exit 2.
  - **cr R4-3**: DANGLING 문면에 행 번호를 싣는다(수집 범위는 좁히지 않는다). **R4-4**: runbook §9.5 의 「세 번」을 실측 수로. **R4-5**: 내용 축
    매처의 양성 대조(매 실행 알려진 표본 하나가 적중하는지). **R4-7**: 「읽지 못한 파일」 가지의 주석을 실제 역할대로. **R4-8**: step 항목 들여쓰기를
    `steps:` 키 기준으로.
- **알려진 제한으로 등재**: cr R4-6(내용 축의 파일당 프로세스 수 — 성능 기록). vr R4-M2(a)는 Actions 의 앵커 지원 여부를 실측하지 않았지만,
  지원 여부와 무관하게 exit 2 로 막으므로 판정에 영향이 없다.
- 배치 뒤 verifier 표적 r5 + code-reviewer 표적 r5.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
