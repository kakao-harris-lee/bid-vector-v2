# M7/7F-local — 실행 명령과 종료 코드

**acceptance 정의**(계약 「acceptance」절)

- `.github/workflows/ci.yml` 의 **`container` job 명령 그대로** + 새 스모크. 이 slice 가 그 job 에
  스모크 step(S-26)을 더했으므로 **둘은 한 집합**이다 — job 전건을 돌린 것이 acceptance 다.
- `check` job 은 돌지 않는다 — `tools/one-command-check.sh` 를 **바꾸지 않았다**(그 스크립트가 축어로
  따르는 것은 `check`·`ml-engine`·`web` 세 job 이고 컨테이너 축인 이 job 은 그 안에 없다).
  **한 축만 예외다**: evidence 커밋은 `check` 안 `leakPatternGate` 의 입력(`reports/evidence/`)을
  건드린다. 그 축은 아래 비밀값 스캔 둘째 명령과 그 게이트 실행으로 따로 쟀고, 마지막 HEAD 의
  `check` 전건은 verifier 가 그 HEAD 에서 직접 잰다.
- `web` job 은 생략한다 — `web/` 입력 변경 0(계약 acceptance 절).
- job step 은 **파일에서 뽑아** 순서대로 돌렸다(손으로 옮겨 적으면 「명령 그대로」가 깨진다).
  `uses:` step(체크아웃·setup-java·setup-gradle·artifact 업로드)만 CI 전용으로 건너뛰었고,
  러너가 하는 `$GITHUB_ENV`·`$GITHUB_PATH` 흡수는 같은 형태로 재현했다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의 post-state 를
담을 수 없다. 그 한 칸은 verifier 와 PR 조치 코멘트가 갖는다.

---

## 2026-10-16 — 새 스모크 단독 (산출물 4)

- cmd: `./tools/local-tls-smoke.sh`
- exit: 0
- 핵심 결과: 검사 블록 전부 통과 — 셸·번들 자산·SPA fallback · `/api` 무인증 401·인증 200 ·
  우회 ①~⑤ · 자격 값 로그 0줄 · 정리 뒤 컨테이너·볼륨·네트워크 잔여 0(블록 목록은 스크립트의
  `_step` 문면이 정본이다)

## 2026-10-17 — `container` job 전건 (acceptance, D-7F-1·2 반영 뒤)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: DB 접속 값 생성(이 job 전용 일회성, 파일에 리터럴로 두지 않는다)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: 운영자 자격 값 생성(이 job 전용 일회성, 파일에 리터럴로 두지 않는다)

- cmd: `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .`
- exit: 0
- step: 이미지 빌드 (S-21)

- cmd: `./gradlew --no-daemon :app:bootJar`
- exit: 0
- step: 앱 배포물 빌드 (S-21a)

- cmd: `docker build -f docker/app.Dockerfile -t bidvector/app:local .`
- exit: 0
- step: 앱 이미지 빌드 (S-21b)

- cmd: `docker build -f docker/web.Dockerfile -t bidvector/web:local .`
- exit: 0
- step: web 이미지 빌드 (S-21c)

- cmd: `docker build -f docker/proxy.Dockerfile -t bidvector/proxy:local .`
- exit: 0
- step: 프록시 이미지 빌드 (S-21d)

- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local config/quality/image-hygiene-policy.properties`
- exit: 0
- step: 이미지 위생 게이트 — ml-serving (S-22a)

- cmd: `./tools/image-hygiene-check.sh bidvector/app:local config/quality/image-hygiene-policy-app.properties`
- exit: 0
- step: 이미지 위생 게이트 — 앱 (S-22b)

- cmd: `./tools/image-hygiene-check.sh bidvector/web:local config/quality/image-hygiene-policy-web.properties`
- exit: 0
- step: 이미지 위생 게이트 — web (S-22b2)

- cmd: `./tools/image-hygiene-check.sh bidvector/proxy:local config/quality/image-hygiene-policy-proxy.properties`
- exit: 0
- step: 이미지 위생 게이트 — 프록시 (S-22b3)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: 앱 이미지 거부 스모크 — 운반 이름공간을 조기에 거부한다 (S-22c)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: install trivy

- cmd: `./tools/vuln-scan-check.sh bidvector/ml-serving:local ml-serving config/quality/vuln-policy.properties`
- exit: 0
- step: 이미지 취약점 게이트 — ml-serving (S-22d)

- cmd: `./tools/vuln-scan-check.sh bidvector/app:local app config/quality/vuln-policy.properties`
- exit: 0
- step: 이미지 취약점 게이트 — 앱 (S-22e)

- cmd: `./tools/vuln-scan-check.sh bidvector/web:local web config/quality/vuln-policy.properties`
- exit: 0
- step: 이미지 취약점 게이트 — web (S-22e2)

- cmd: `./tools/vuln-scan-check.sh bidvector/proxy:local proxy config/quality/vuln-policy.properties`
- exit: 0
- step: 이미지 취약점 게이트 — 프록시 (S-22e3)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: 로컬 환경 기동 — app+ml-serving+postgres 가 healthy 로 수렴 (S-23)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: 컨테이너 스모크 — 준비 상태·인증 경계·health 격리·수집 꺼짐 (S-23b)

- cmd: job 의 인라인 블록 그대로
- exit: 0
- step: 백업·복원·되돌림 리허설 (S-23c)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true`
- exit: 0
- step: 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 (S-24, OPEN-5E2-CROSSLANG-REAL-SERVER)

- cmd: `docker compose -f docker/compose.yaml down -v`
- exit: 0
- step: 로컬 환경 정리 — 볼륨까지 (S-25)

- cmd: `./tools/local-tls-smoke.sh`
- exit: 0
- step: 로컬 패키징 스모크 — web 셸 · /api 401 · 우회 ①~⑤ (S-26)

- 핵심 결과: 열거한 **23 step 전부 exit 0**(러너 로그의 종말 줄 `ALL STEPS exit=0`). 앞 라운드의 17 에서 **step 여섯이 늘었다** — 이미지 빌드 둘(S-21c·S-21d) · 위생 둘(S-22b2·S-22b3) · 취약점 둘(S-22e2·S-22e3). 마지막 step 은 이 slice 가 더한 스모크(S-26)다.
- 소요: 이 장비에서 **3분 36초**(캐시가 전부 더운 상태 — 이미지·Gradle·trivy DB·npm layer). CI 의 소요는 이 수로 대신할 수 없다(러너는 차갑다) — job 의 `timeout-minutes: 30` 에 대한 여유는 첫 CI 실행의 실측으로 본다.

(step 열거는 `ci.yml` 의 `container` job 순서 그대로다 — `uses:` step 넷만 CI 전용으로 건너뛰었고, 러너가 하는 `$GITHUB_ENV`·`$GITHUB_PATH` 흡수는 재현했다)

## 2026-10-17 — D-7F-1·2: web·proxy 게이트와 음성 대조 다섯

**위생 게이트 둘**(베이스 상향 뒤 이미지로)

- cmd: `./tools/image-hygiene-check.sh bidvector/web:local config/quality/image-hygiene-policy-web.properties`
- exit: 0
- 핵심 결과: 금지-실행파일 8 · 필수-실행파일 1 · `/srv` 파일수 3(하한 2) · 필수내용 1 · 금지내용 5 ·
  `Config.User=bidvector` 실프로세스 uid [10001] · base-layer 접두 일치 · 크기 상한 이내
- cmd: 같은 명령, `bidvector/proxy:local` + `-proxy.properties`
- exit: 0
- 핵심 결과: 같은 축 · `/etc/caddy` 파일수 1(하한 1)

**취약점 게이트 둘**(allowlist 등재 뒤)

- cmd: `./tools/vuln-scan-check.sh bidvector/web:local web config/quality/vuln-policy.properties`
- exit: 0
- 핵심 결과: 차단 후보 4 · 판정단위 4 · 이 kind 적용 4 · **미등재 0**
- cmd: 같은 명령, `bidvector/proxy:local proxy`
- exit: 0
- 핵심 결과: 같은 수
- cmd: 같은 명령을 **trivy 가 PATH 에 없는 상태**로(의도치 않은 상태에서의 거동)
- exit: 2
- 핵심 결과: 「도구가 필요하다」로 **판정 불가 처리** — 도구 부재가 조용한 통과가 되지 않는다

**베이스 상향의 효과**(같은 술어로 전후 비교)

- cmd: 두 이미지의 SBOM·스캔을 핀한 trivy 로 생성·판독(게이트와 같은 술어: HIGH·CRITICAL +
  수정본 있음)
- exit: 0
- 핵심 결과: `caddy:2.11.4` 에서 차단 후보 **21**(stdlib 14 · grpc 3 · x/net 2 · x/crypto 1 ·
  x/text 1) → `caddy:2.11.7` 에서 **4**(stdlib 3 · x/net 1). SBOM 구성요소 182 · 분석 패키지 181 ·
  배포판 alpine/3.23.6 — 이 수들이 정책의 하한·배포판 선언의 근거다.

**음성 대조 다섯**(각각 사본에서 복원, `git checkout --` 미사용)

- cmd: web 런타임 stage 에 빌더의 `node` 를 복사(금지 패키지 투입) 후 위생 게이트
- exit: 1
- 핵심 결과: 「금지 실행 파일 'node' 이 PATH 에 있다」 + 크기 상한 초과 둘 다 위반으로 잡힘
- cmd: 위생 정책에서 `runtime.kind` 키 제거 후 위생 게이트
- exit: 2
- 핵심 결과: 「정책 키를 읽지 못했다」 — fail-closed
- cmd: `scan.kinds` 에서 `proxy` 제거 후 그 kind 로 취약점 게이트
- exit: 2
- 핵심 결과: 「kind 가 정책의 scan.kinds 에 없다」 — fail-closed
- cmd: allowlist 등재 하나 제거 후 취약점 게이트
- exit: 1
- 핵심 결과: 「미등재 finding 1건」(적용 3 · 미등재 1)
- cmd: allowlist 만료를 과거(2026-01-01)로 바꾼 뒤 취약점 게이트
- exit: 1
- 핵심 결과: 「등재가 만료됐다(2026-01-01 < 2026-10-16)」
- cmd: 어느 finding 과도 맞지 않는 가짜 등재를 넣고 취약점 게이트
- exit: 1
- 핵심 결과: 「등재가 차단 후보 어디에도 맞지 않는다(stale — 치워야 한다)」
- cmd: 변이마다 사본 복원 뒤 `diff -q <파일> <사본>` · `git status --porcelain`
- exit: 0
- 핵심 결과: 변이 잔여 0(allowlist 는 사본과 바이트 동일, 나머지는 numstat 빈 출력)

## 2026-10-16 — clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>` + 양성 대조(in_scope 파일 하나에 줄을
  심어 잡히는지 보고 **비파괴 절삭**으로 복원 — `checkout --` 을 쓰지 않는다)
- exit: — (아래 사유)
- 핵심 결과: **이 게이트는 evidence 커밋 뒤에 성립한다.** in_scope 에 evidence 경로가 들어 있어
  그 커밋 전에는 당연히 양성이고, 커밋 뒤의 결과는 evidence 가 자기 커밋의 post-state 를 담을 수
  없으므로 **verifier 와 PR 조치 코멘트가 정본**이다(같은 규율이 마지막 HEAD 의 게이트 결과에도
  적용된다). 저작 레인은 그 둘을 커밋 직후 실행해 보고에 싣는다.

## 2026-10-16 — 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m7/7f/`
- exit: 0
- 핵심 결과: 매치 3줄(2026-10-17 재측, 정책 파일 넷을 in_scope 에 더한 뒤에도 같다) — **전부
  환경변수 이름이고 값이 아니다**: ci.yml 의 한 줄은 base 에 이미 있던 것, 나머지 둘은 이 slice 가
  더한 runbook 예시와 스모크의 기본값 줄이다. 같은 이름은 기본 compose 파일에도 base 부터 있다 —
  저장소에 들어간 **값**은 포트·이미지 다이제스트·CVE 번호·`localhost` 뿐이다.
- cmd: 같은 명령을 **게이트의 스캔 축만**(`reports/evidence/m7/7f/`, 게이트는 `scope.md` 를 제외한다)
- exit: 1
- 핵심 결과: 매치 없음 — `leakPatternGate` 의 입력에 새 어휘가 없다. 패턴 어휘는 이 문서에 축어로
  적지 않는다(참조형 명령만 둔다).

## 2026-10-16 — `leakPatternGate`(evidence 를 읽는 유일한 게이트)

- cmd: `./gradlew --no-daemon leakPatternGate` — **되돌린 트리**(rollback.md 의 임시 clone)에서
- exit: 0
- 핵심 결과: `> Task :leakPatternGate` · `BUILD SUCCESSFUL` — 되돌리지 않는 evidence 가 게이트를
  붉히지 않는다(rollback ⑥ ⓐ).
- cmd: 같은 명령 — **이 worktree 의 evidence 내용**에서
- exit: 0
- 핵심 결과: 같은 판정. **이 커밋 자신의 post-state 에서의 결과는 여기 적을 수 없다**(적으려면 커밋이
  하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다) — 그 한 칸은 verifier 와 PR 조치 코멘트가 갖는다.

## 2026-10-16 — rollback 실측

- `rollback.md` 의 명령을 임시 clone 에서 **실제로** 돌렸다 — 종료 코드·D/M 수·diff 공백·④~⑥ 은 그
  문서에 있다(`실측 HEAD` 한 줄 포함).
