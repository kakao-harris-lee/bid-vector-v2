# M6/6A-2a — 명령과 종료 코드

정본: `scope.md` acceptance. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돌린다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다**(evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다).

변이·되돌림 실측은 전부 **버릴 worktree**에서 돌렸다. 아래 「변이」 절의 각 행은 적용 직후
`git diff --numstat` 으로 변이가 실제로 적용됐음을 확인한 뒤의 결과다.

## RED — 구현 전

- cmd: `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1
- 핵심 결과: 새 test 둘이 `lockManagementSurface`·`productionApplication`·actuator 좌표 부재로 컴파일 불가

## 구현 뒤 표적 test

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.ManagementSurfaceLockTest' --tests 'bidvector.app.management.ManagementHealthSurfaceTest'`
- exit: 1 → 0
- 핵심 결과: 첫 실행은 집계 health 본문이 키 둘(`status`+`groups`)이라 RED — Boot 4.1.1 이 세부
  설정과 무관하게 그룹 이름을 싣는다는 실측. 단언을 「키 하나」에서 **실측한 모양 고정**으로
  바꾼 뒤 12 test 전건 통과

- cmd: 같은 명령 + `--tests 'bidvector.app.http.ProductionAssemblyAuthAuditTest'`
- exit: 0
- 핵심 결과: 기존 production 조립 test 가 `productionApplication()` 공유 형태에서 그대로 초록

## 관리 표면 값 획득 축 실측 (계약 (2b) 요구)

- cmd: `ManagementSurfaceLockTest` 의 「잠금 전」 단언 — 환경변수·명령행 source 를 세운 뒤 잠금 적용 전 값 조회
- exit: 0
- 핵심 결과: **잠금 전에는 환경변수·명령행이 노출·세부를 넓힌다**(계약이 가정한
  `defaultProperties` 자리는 가장 낮은 우선순위다). `addFirst` 잠금 뒤에는 둘 다 못 이긴다 —
  잠금 전/후를 같은 test 가 함께 단언해 공허한 참을 막는다

## 이미지 · 위생 게이트

- cmd: `./gradlew --no-daemon :app:bootJar`
- exit: 0
- 핵심 결과: `app/build/libs/app.jar` 52.1MB(평범한 `app-plain.jar` 도 그대로 공존, D-6A1-28)

- cmd: `docker build -f docker/app.Dockerfile -t bidvector/app:local .`
- exit: 0
- 핵심 결과: layer 넷(`dependencies`/`spring-boot-loader`/`snapshot-dependencies`/`application`)으로
  풀려 `/application/{application.jar,lib}` 구성 · 이미지 339.4MB · `Config.User=bidvector`(10001)

- cmd: `./tools/image-hygiene-check.sh bidvector/app:local config/quality/image-hygiene-policy-app.properties`
- exit: 0
- 핵심 결과: 실프로세스 uid 10001 · 베이스 layer 접두 일치 · 금지 실행 파일 9 전건 부재 ·
  의존 layer 97 항목(하한 50) 중 금지 좌표 13 전건 0 · 339.4MB ≤ 450MB

- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local config/quality/image-hygiene-policy.properties`
- exit: 0
- 핵심 결과: 정책 인자화 뒤에도 python-serving 갈래가 그대로 선다(금지 import 5 전건 0) —
  이 개발 호스트의 기존 이미지는 336.1MB(상한 400MB)

- cmd: `docker build` 양성 대조 — `docker/app.Dockerfile` 에 제외 대상 경로 COPY 한 줄을 심어 빌드
- exit: 1
- 핵심 결과: `.dockerignore` 가 실제로 컨텍스트를 줄인다(그 경로를 "not found" 로 대며 실패).
  심은 줄은 사본 복원으로 비파괴 절삭(`checkout --` 을 쓰지 않았다)

- cmd: 위생 게이트 프로브 컨테이너 수명 실측(`docker run -d` → `docker inspect`/`docker top`)
- exit: 0
- 핵심 결과: TEST-NET-1 주소로 DB 를 가리키면 pid 1 이 t=10s 에도 살아 있고 uid 10001 ·
  `no-new-privileges` 아래 정상 기동 — (1) 의 프로세스 표집 창이 확보된다

## compose · 스모크

- cmd: `docker compose -f docker/compose.yaml config` (환경 변수 없이)
- exit: 1
- 핵심 결과: `app` 의 모든 값이 `${VAR:?}` 라 값 없이는 그 자리에서 실패한다

- cmd: 같은 명령(환경 변수 채운 뒤)
- exit: 0
- 핵심 결과: API 포트는 `host_ip: 127.0.0.1` 로만 publish · 관리 포트 publish 0 ·
  `no-new-privileges` · healthcheck 은 관리 포트 readiness · 수집·ML 변수 0

- cmd: `docker compose -f docker/compose.yaml up -d` + 세 서비스 healthy 수렴 대기
- exit: 0
- 핵심 결과: app·ml-serving·postgres 세 서비스가 세 번째 폴링(약 9초)에 healthy

- cmd: CI `container` job 의 스모크 블록을 로컬에서 그대로 실행
- exit: 0
- 핵심 결과: 프로브 둘 200·키 하나(`status`,UP) · 무인증 401 · 인증 `/api/strategy` 200 ·
  API 포트 actuator 네 경로가 무인증·인증 둘 다 401/404 · 관리 포트 health 외 열한 경로 404 ·
  앱 로그에 수집 시작 줄 0

- cmd: 자격 값 argv 노출 양성·음성 대조(요청 300회를 돌리며 프로세스 표와 `/proc/*/cmdline` 전수 조회)
- exit: 0
- 핵심 결과: `printf | curl --config -` 형태 **0회 관측**, 같은 값을 헤더 인자로 넘기는 형태
  **26회 관측**. 첫 측정은 조회기 자신의 인자에 값이 들어가 자기검출(50회)을 냈고, 패턴을 파일로
  넘기는 형태로 고쳐 다시 쟀다

- cmd: `docker compose -f docker/compose.yaml down -v`
- exit: 0
- 핵심 결과: 컨테이너·네트워크·볼륨 정리(이 호스트의 다른 프로젝트 컨테이너는 건드리지 않았다 —
  전용 project 이름으로 띄웠다)

## 변이 ①~⑦

| 변이 | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| ①a 관리 포트 기본값을 API 포트 기본값과 같게 | `:app:test`(표적 둘) | 1 | 「출하 기본값만으로 관리 포트가 API 포트와 분리된다」 FAILED (12 중 1) |
| ①b 분리 판정 가드 제거 | 같음 | 1 | 거부 단언 셋 FAILED (12 중 3) |
| ② 노출에 `env` 추가 | 같음 | 1 | 「관리 포트에는 health 말고 아무 endpoint 도 없다」 + 잠금 단언 FAILED (12 중 3) |
| ③ `show-details=always` | 같음 | 1 | 「명령행 인자로도 관리 표면을 넓힐 수 없다」 FAILED (12 중 1) |
| ④ readiness 에서 DB 제거 | 같음 | 1 | 「DB 가 사라지면 readiness 는 DOWN」 FAILED (12 중 1) |
| ⑤ Dockerfile `USER` 제거 | 앱 위생 게이트 | 1 | 위반 2 — `Config.User` 미지정 · 실프로세스 uid 0 |
| ⑥ test 전용 좌표를 의존 layer 에 실제로 넣기 | 앱 위생 게이트 | 1 | 위반 1 — 금지 좌표가 의존 layer 에 있다(항목수 97→98) |
| ⑦ JDK 베이스로 교체 | 앱 위생 게이트 | 1 | 위반 11 — 금지 실행 파일 9 전건 + 베이스 layer 체인 불일치 + 크기 상한(500.4MB > 450MB) |

**변이 ⑦ 이 게이트 사각을 하나 찾았다.** 첫 측정에서 위반이 10 이었고 `serialver` 만 빠졌다 —
JDK 베이스에 그 파일이 있는데 `--version` 을 거부해(exit 1) 실행 시도 축이 「부재」로 읽었다.
PATH 조회 축을 더하고(셸 부재는 판정 불가로 실패) 다시 재 11 건이 됐다. 이 시정은 별 커밋이다.

- cmd: 변이 없는 기준선 — `:app:test`(표적 둘)
- exit: 0
- 핵심 결과: 12 test 전건 통과(변이 RED 가 의미를 갖는 전제)

## `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` 실측

- cmd: Boot 기본 `excludeFilters` 둘을 명시로 되살린 트리에서 `:app:test`(표적 둘)
- exit: 0
- 핵심 결과: 되살려도 결과가 같다 — 거동 영향 0. 구조적 근거는 `checklist.md`(자동 구성 등재 파일
  부재 · `TypeExcludeFilter` 빈 0). 계약대로 **필터를 되살리지 않고** OPEN 을 6A-2b 로 넘긴다

## 경계 무편집 실측 (`out_of_scope`)

- cmd: `git diff --stat <base>..HEAD --` 로 네 경계 각각 조회 — 인증 필터 두 파일 · `openapi/` ·
  마이그레이션 디렉터리 둘 · `tools/one-command-check.sh`
- exit: 0
- 핵심 결과: **네 경계 모두 빈 출력**(diff 0). D-6A2a-1·4·9 문면 그대로

- cmd: 이미지 비밀값 표면 조회 — Dockerfile 의 `ENV`/`ARG` · 이미지 `Config.Env` · `docker history`
- exit: 0
- 핵심 결과: `ENV` 0 · `ARG` 하나(jar 의 공개 경로) · `Config.Env` 는 베이스 6 항목뿐 ·
  history 의 build 인자는 베이스 이미지 것 둘뿐 · `app` 에 `src/main/resources` 자체가 없다

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6a2a/`
- exit: 1
- 핵심 결과: 매치 없음 = 통과. 산출물 쪽 동일 스캔은 새 test 파일에서 2건 —
  둘 다 **Testcontainers API 자신의 멤버 이름**이고 기존 `ProductionAssemblyAuthAuditTest` 가
  같은 형태를 이미 갖는다. 루트 게이트의 scanRoot 는 `reports/evidence` 라 판정 대상 밖
- 육안 확인: Telegram id·사업자 정보 0(이 slice 는 그 축을 만들지 않는다)

## clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 산출물 경로 개별 인자>` (evidence 디렉터리 제외)
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조 1회** — in_scope 파일 하나에 줄을 심으면 ` M` 로 잡히고,
  심은 줄은 `head -n <원래 줄수>` 절삭으로 비파괴 복원했다(`checkout --` 을 쓰지 않았다)
- evidence 디렉터리 자신의 마지막 HEAD clean 상태는 **verifier 가 그 HEAD 에서 직접 잰다**
  (evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다)

## acceptance

`check` job 명령 그대로를 **버릴 worktree**에서 한 번씩. 두 번 돌렸다 — 첫 회차(`c4e09b94`)가
붉었고, 그것이 이 slice 의 유일한 회귀를 잡았다.

**첫 회차(`c4e09b94`)**

- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 1
- 핵심 결과: `OperatorAuthenticationTest` 4 test FAILED — actuator 좌표가 test 전용 조립에
  `RequestMappingHandlerMapping` 빈을 둘로 만들어 타입 주입이 `NoUniqueBeanDefinitionException`
  으로 깨졌다. **표적 test 만 돌렸을 때는 보이지 않았다.** 시정은 별 커밋(`checklist.md` 의
  「actuator 좌표가 만든 부수 효과」 절)

- cmd: `./tools/one-command-check.sh`
- exit: 1
- 핵심 결과: 같은 원인(첫 명령이 `check` 다)

**둘째 회차(`4facff09` — 마지막 산출물 커밋)**

- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 348 task 전건 실행(컴파일·ktlint·detekt·sizeGate·
  domainDependencyGate·architecture test·compatibilitySmoke·contractGate·leakPatternGate·
  gateExecutionGate 등)

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(게이트가 아니라 측정)

- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin 전건 + Python 전건 통과

**`container` job — ci.yml 의 step 을 그 파일에서 뽑아 같은 순서로 한 번**(버릴 worktree
`4facff09`). 러너 전용 step 셋(checkout·setup-java·setup-gradle)만 건너뛰고, `$GITHUB_ENV`
전파는 CI 러너의 동작을 흉내 냈다.

- cmd: `container` job 의 실행 step 열하나 전부
- exit: 전건 0 (`CONTAINER_JOB_FAILED=0`)
- 핵심 결과: DB·운영자 자격 값 생성 → ml-serving 이미지(새로 빌드) → 앱 배포물 → 앱 이미지 →
  위생 둘 통과 → 세 서비스 healthy → 스모크 통과 → 기존 S-24(실 Python 서버 통합) 통과 →
  볼륨까지 정리. 정리 뒤 이 slice 가 만든 컨테이너 0, 이 호스트의 다른 프로젝트 컨테이너
  36개는 무접촉

기준은 CI job 이 정본이고 PR CI 의 `container` 초록이 필요조건이다.

**새로 빌드한 ml-serving 이미지도 336.1MB 로 나왔다**(정책 상한 400MB) — 6C evidence 의
113.7MB 기록이 낡았다는 뜻이고, 이 slice 는 그 정책을 건드리지 않는다(`checklist.md` 알려진 제한).
