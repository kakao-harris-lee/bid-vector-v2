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

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
