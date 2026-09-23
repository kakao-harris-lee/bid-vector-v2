# M6/6A-3+6F-3 commands

acceptance는 `.github/workflows/ci.yml`의 `check`·`container` job 명령 그대로(구현 레인이
착수 시 원문 대조). 전부 버릴 worktree(`git worktree add --detach <scratch> <sha>`)에서,
`check`는 캐시 우회(`--rerun-tasks`) 1회 포함. 실측 HEAD는 `535a48ce`(이후 커밋은 evidence
전용).

## check job — 1차 실측(RED, 수정)

## 2026-09-23T18:19Z
- cmd: `./gradlew --no-daemon check --rerun-tasks` (worktree `ci-check`, HEAD `1c52a182`)
- exit: 1
- 핵심 결과: `:app:sizeGate`(dependencies{} 첫 블록 55줄, 한도 50) · `:app:detekt`
  (`EvaluationWiring.kt`의 `operatorProfilePort` 빈 메서드, MaxLineLength) 둘 다 실패.
  수정 커밋 `bb9fec5f`.

## check job — 2차 실측(RED, 수정)

## 2026-09-23T18:52Z
- cmd: `./gradlew --no-daemon check --rerun-tasks` (worktree `ci-check2`, HEAD `bb9fec5f`)
- exit: 1
- 핵심 결과: sizeGate·detekt 통과, `:app:ktlintMainSourceSetCheck` FAILED(
  `EvaluationDryRunFactory.kt`의 `MaxActiveBidsNotConfiguredException` 선언, "Expected single space
  before the super type"). `cpdCheckObserved`
  가 adapters·app·decision·procurement·qualification 다섯 모듈에서 "중복 코드 발견"을 보고했으나
  **이 slice와 무관한 기존 모듈에서도 발생**(정보성 task, 실 게이트 `cpdCheck`는 별도로 통과).
  수정: `./gradlew :app:ktlintFormat`(자동, 두 파일만 변경) → 커밋 `535a48ce`.

## check job — 3차 실측(GREEN, 정본)

## 2026-09-23T19:01Z
- cmd: `./gradlew --no-daemon check --rerun-tasks` (worktree `ci-check3`, HEAD `535a48ce`)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 346 actionable tasks executed(캐시 전부 우회) — 컴파일·ktlint·
  detekt·sizeGate·domainDependencyGate·architecture test·contractGate·compatibilitySmoke·모듈별
  test·gateExecutionGate 전부 포함.

## 2026-09-23T19:02Z
- cmd: `./gradlew --no-daemon qualityBaseline` (같은 worktree)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(측정 task, 게이트 아님, OPEN-ADR-06 입력).

## 2026-09-23T19:03Z
- cmd: `./tools/one-command-check.sh` (같은 worktree)
- exit: 0
- 핵심 결과: "one-command-check: 완료 — Kotlin 전건 + Python 전건 통과"(내부에서 `check`·
  `qualityBaseline`·ruff·mypy --strict·import-linter·pytest·design_ratchet·wheel 재수출까지
  ci.yml `check`+`ml-engine` 두 job 명령을 순서대로 재실행).

## container job — 정본(GREEN)

## 2026-09-23T19:05Z
- cmd: `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .`
- exit: 0
- 핵심 결과: 이미지 빌드 성공(sha256:1dd7d41a...).

## 2026-09-23T19:06Z
- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local`
- exit: 0
- 핵심 결과: "위생 게이트 통과" — non-root(uid 10001)·base label 일치·크기 336MB < 400MB 상한.

## 2026-09-23T19:06Z
- cmd: `docker compose -f docker/compose.yaml up -d`(+ 20회 폴링 healthy 대기)
- exit: 0
- 핵심 결과: ml-serving·postgres 둘 다 6초 안에 healthy 수렴.

## 2026-09-23T19:07Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 검증 통과.

## 2026-09-23T19:08Z
- cmd: `docker compose -f docker/compose.yaml down -v`
- exit: 0
- 핵심 결과: 컨테이너·볼륨·네트워크 전부 정리.

**scope.md 서술 정정 실측(D-6A3-13, 이미 계약 갱신에 반영)** — `container` job은 app 이미지가
아니라 ml-serving 이미지 + compose + `RealServerIntegrationTest`다(`docker/`에 ml-serving
Dockerfile만 존재, app용 Dockerfile 없음). 「이 slice의 배선 변경이 부팅에 닿는다」는 확인은
아래 `EvaluationDryRunE2ETest`(production 조립 실제 부팅)가 진다.

## acceptance ①~⑦ — production 조립 E2E

## 2026-09-23T18:35Z
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.http.EvaluationDryRunE2ETest'`
- exit: 0
- 핵심 결과: 5 tests, 0 failed — ① 후보없음 200/빈배열 ② 상한없음 409 ③ 음수 400 ④ 무인증 401
  ⑤⑥⑦ 표본 1건 → candidateCount 1·reviewNoticeIds 1(UnavailableMlAnalysis 결정적)·outbox
  전후 등식·wouldNotifyNoticeIds==bidNowNoticeIds.

## 2026-09-23T19:00Z
- cmd: `./gradlew --no-daemon :app:test`(전체, HEAD 535a48ce)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(app 모듈 전체 test — http·wiring·architecture·conformance 포함).

## 변이 실측(다섯, 전부 버릴 worktree, 순서대로 적용→RED 확인→비파괴 복원)

## 2026-09-23T18:41Z — 변이 1: dry-run 조립에 outbox 포트를 꽂는다
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.wiring.NotificationPortWiringGateTest'`
  (`EvaluationDryRunFactory.kt`에 `OutboxNotificationRequestPort::class.java` 클래스 리터럴 삽입)
- exit: 1
- 핵심 결과: 참조 집합이 `{RecordingNotificationRequestPort, OutboxNotificationRequestPort}` —
  기대(`{RecordingNotificationRequestPort}`)와 어긋나 RED.

## 2026-09-23T18:42Z — 변이 2: load 두 번
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.wiring.EvaluationDryRunFactoryTest'`
  (`forRequest`에 `strategyRepository.load()` 중복 호출 삽입)
- exit: 1
- 핵심 결과: `forRequest 는 전략을 정확히 한 번 읽는다` — expected 1 but was 2, RED.

## 2026-09-23T18:43Z — 변이 3: 상한 미설정 409 → 200(값 지어냄)
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.wiring.EvaluationDryRunFactoryTest'
  --tests 'bidvector.app.http.EvaluationDryRunControllerTest'`
  (`?: throw MaxActiveBidsNotConfiguredException()` → `?: 0`)
- exit: 1
- 핵심 결과: 2건 실패 — 팩토리 test는 예외 미발생, 컨트롤러 test는 409 기대에 500 관측(둘 다 RED,
  「200으로 조용히 통과」가 아니라 다른 실패로 드러남 — 값을 지어내면 더 아래(LadderInput)에서
  막힌다는 사실도 같이 실측).

## 2026-09-23T18:44Z — 변이 4: assemble* 새 호출자(app, 허용 목록 밖)
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.architecture.ArchitectureGateTest'`
  (`EvaluationDryRunController.kt`에 `assembleKeywordScopeText(null, null)` 최상위 호출 삽입)
- exit: 1
- 핵심 결과: ArchUnit 위반 — `Static Initializer <EvaluationDryRunControllerKt.<clinit>()> calls
  method <TextKt.assembleKeywordScopeText>` — RED.

## 2026-09-23T18:46Z — 변이 5: OpenAPI에서 필드 하나 제거
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.http.OpenApiContractTest'
  --tests 'bidvector.app.http.EvaluationDryRunE2ETest'`
  (`wouldNotifyNoticeIds`를 `EvaluationDryRunResponse` 스키마 `properties`·`required`에서 제거)
- exit: 0(1차) → **게이트 사각 발견** — 200 응답 키 집합 대조가 없어 초록. `4c2d041e`에서
  test 추가.
- 재실측(같은 변이, 수정된 test로): exit 1 — `평가 dry-run 200 응답의...` 키 집합 불일치로
  RED. 수정 후 원복 확인(diff 0줄).

## 게이트·보안 스캔

## 2026-09-23T18:59Z
- cmd: `git status --porcelain -- <in_scope 49경로 개별 인자>`
- exit: 0(빈 출력) — clean-tree 통과. 양성 대조: `EvaluationDryRunController.kt`에 임시 줄 추가 →
  `git status`가 `M` 보고 → `head -n -1`로 비파괴 절삭(`checkout --` 미사용) → 재확인 diff 0줄.

## 2026-09-23T18:59Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 49경로> reports/evidence/m6/6a3f3/`
- exit: 0(매치 있음)
- 핵심 결과: 전부 false positive로 육안 확인 — `EvaluationDryRunE2ETest`·`ProductionAssemblyAuthAuditTest`의
  Testcontainers 고정 fixture 문구(운영 값 아님), OpenAPI 보안 스키마 선언 키워드 하나,
  `StrategyEditExecutors`·`StrategyExecutors`·`EditSessionRestore`의 편집 필드 토큰 파싱 식별자
  (인증과 무관한 도메인 어휘). 실 비밀값 0건.

## 롤백 실측(별도 문서)

절차·①~⑥ 실측은 `rollback.md` 참고(같은 실측 HEAD `535a48ce`).
