# M6/6A-3+6F-3 commands

acceptance는 `.github/workflows/ci.yml`의 `check`·`container` job 명령 그대로(구현 레인이
착수 시 원문 대조). 전부 버릴 worktree(`git worktree add --detach <scratch> <sha>`)에서,
`check`는 캐시 우회(`--rerun-tasks`) 1회 포함. **라운드 이력을 남기지 않는다 — 최종 명령·
exit만 적는다**(evidence-pack 규율, verifier r2 LR2-3 시정). 실측 HEAD는 `18fdd72e`(검토
라운드 2 수정 마지막 산출물 커밋 — 이후 커밋은 evidence 전용).

## check job — 정본(GREEN)

- cmd: `./gradlew --no-daemon check --rerun-tasks`(버릴 worktree, HEAD `18fdd72e`)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 346 actionable tasks executed(캐시 전부 우회), 4m5s. 컴파일·
  ktlint·detekt·sizeGate·domainDependencyGate·architecture test·contractGate·
  compatibilitySmoke·모듈별 test·gateExecutionGate 전부 포함.

- cmd: `./gradlew --no-daemon qualityBaseline`(같은 worktree)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(측정 task, 게이트 아님, OPEN-ADR-06 입력).

- cmd: `./tools/one-command-check.sh`(같은 worktree)
- exit: 0
- 핵심 결과: "one-command-check: 완료 — Kotlin 전건 + Python 전건 통과"(내부에서 `check`·
  `qualityBaseline`·ruff·mypy --strict·import-linter·pytest·design_ratchet·wheel 재수출까지
  ci.yml `check`+`ml-engine` 두 job 명령을 순서대로 재실행).

## container job — 재실행 불필요(재확인)

이 slice의 어떤 커밋도 `docker/`·`ml-engine/`·`docker/compose.yaml`을 만지지 않았다 —
`git diff --name-only febad567..18fdd72e -- docker/ ml-engine/ docker/compose.yaml` 빈 출력으로
확인(cmd 그대로, exit 0, 검토 라운드 2 포함 전체 range 재확인). container job의 최초 GREEN
결과(이미지 빌드·위생 게이트·compose healthy·`RealServerIntegrationTest`)는 착수 라운드에서
이미 확인됐고 이후 어떤 라운드도 그 경로를 건드리지 않았으므로 그대로 유효하다 — 「이 slice의
배선 변경이 부팅에 닿는다」는 확인은 아래 `ProductionAssemblyAuthAuditTest`·
`EvaluationDryRunE2ETest`(production 조립 실제 부팅)가 진다.

## acceptance ①~⑧ — production 조립 E2E(위 check job 실행에 포함, 같은 HEAD `18fdd72e`)

- `bidvector.app.http.EvaluationDryRunE2ETest` — 6 tests, 0 failed. ① 후보없음 200/빈배열
  (키 집합 등식 포함) ② 상한없음 409 ③ 음수 400 ④ 무인증 401 ⑤⑥⑦ 표본 1건 → candidateCount
  1·reviewNoticeIds 1(결정적)·outbox 전후 등식·wouldNotifyNoticeIds==bidNowNoticeIds(엄격
  캐스트) ⑧ 제목·기관명 있는 표본 공고 → 원시 응답 문자열에 원문 없음(D-6A3-22).
- `bidvector.app.http.EvaluationDryRunBidNowE2ETest` — 1 test, 0 failed. BidNow fake ML
  (profile 격리) → wouldNotifyNoticeIds 비어있지 않음==bidNowNoticeIds, outbox 전후 등식
  (D-6A3-17(a) 거동 다리, R2-M1 이 KDoc 에 적은 SAM 람다 폐쇄의 실측 근거이기도 하다).
- `bidvector.app.architecture.ArchitectureGateTest` — 11 tests, 0 failed(D-6A3-25 신규 test
  포함, production 0건).
- `bidvector.app.architecture.ArchitectureGateCatchesViolationsTest` — 36 tests, 0 failed
  (D-6A3-25 신규 fixture 2건 포함).

## D-6A3-25 — 우회 5 게이트 교체에 대한 변이 실측(TDD, 버릴 worktree, 적용→확인→worktree 폐기로 원복)

verifier r2 HIGH-R2-1 표적. **OLD 게이트(수정 전, HEAD `552f1e9a`) 위에서 먼저 재현**했다 —
N5·N6·PRIV 는 초록(버그 재현), M5(라운드 1 회귀 대조)는 그대로 RED.

| # | 변이 | cmd | exit(OLD 게이트) |
|---|---|---|---|
| M5 | `app.http`에 `CandidateSourcePort`를 직접 부르는 헬퍼(`MutM5HttpPortCaller`) 신설 | `:app:test --tests 'bidvector.app.architecture.*'` | 1(RED, 회귀 대조— 라운드 1부터 잡혀야 한다) |
| N6 | `app.http`에 `JdbcCandidateSource(...).openCandidates()`를 직접 부르는 헬퍼(`MutN6ConcreteAdapterCaller`) 신설(구체 어댑터 경유) | 위와 같음(같은 실행) | 0(GREEN — 버그: owner가 인터페이스 FQCN이 아니라서 못 잡음) |
| N5 | `app.wiring`에 `CandidateSourcePort`를 직접 부르는 헬퍼(`MutN5WiringShortcut`) 신설 | 위와 같음(같은 실행) | 0(GREEN — 버그: `app.http` 패키지 밖이라 못 잡음) |
| PRIV | `EvaluationDryRunFactory`에 `candidateSource.openCandidates()`로 후보를 직접 읽는 메서드 추가 | 위와 같음(같은 실행) | 0(GREEN — 버그: `app.wiring`은 옛 규칙의 대상 패키지가 아님) |

**새 게이트(D-6A3-25, `appPortCallsMustBeAllowedPairs`) 위에서 같은 네 변이를 재적용**했다 —
전부 RED, 되돌리면 전부 GREEN.

| # | cmd | exit(신규 게이트, 변이 적용) | exit(변이 원복) |
|---|---|---|---|
| M5·N6·N5·PRIV(같은 실행에 넷 동시 적용) | `:app:test --tests 'bidvector.app.architecture.*'` | 1(RED, 위반 4건 — `MutM5HttpPortCaller`·`MutN6ConcreteAdapterCaller`·`MutN5WiringShortcut`·`EvaluationDryRunFactory`가 각각 `CandidateSourcePort.openCandidates`를 허용 쌍 밖에서 호출한다고 보고) | 0(GREEN, 47 tests 0 failed) |

**최초 구현의 오탐 시정(과정 기록, checklist 아님 — 재현성 근거).** owner의 `isAssignableTo`만
보는 최초 구현은 생성자 호출(`EvaluationWiring`이 `JdbcCandidateSource(...)`를 **짓기만** 하는
`invokespecial`)과 포트에 없는 어댑터 전용 메서드(`RecordingNotificationRequestPort.
requested()`)까지 15건 오탐했다 — `JavaMethodCall`로 제한하고 "포트가 실제로 그 메서드를
선언하는가"까지 확인하도록 고쳐 production 0건으로 닫았다(같은 `:app:test --tests
'bidvector.app.architecture.*'` 실행, exit 0).

모든 변이는 `git worktree add --detach <scratch> <sha>`로 만든 버릴 worktree에서 적용했고,
확인 뒤 `git worktree remove --force`로 폐기했다(원본 트리에 영향 없음 — 각 worktree의
`git diff --numstat`으로 변이 적용 자체를 먼저 확인했다).

## 그 밖의 게이트 술어 변경에 대한 변이 실측(현재도 유효한 게이트 — 이 라운드가 바꾸지 않음)

검토 라운드 1이 수정한 게이트들에 대한 회귀 방지 변이다. 수정 전 GREEN(판정 대상 SHA
`ff69cdd2`에서의 관측)은 `_workspace/m6-6a3f3/10_verifier_report.md`·`11_code_review.md`·
`12_migration_review.md`가 정본이다 — 여기는 **수정 후 RED** 재현만 적는다(구 M5 행은 위
D-6A3-25 표로 옮겼다 — 그 표적 게이트가 이 라운드에서 대체됐다).

| # | 변이 | 표적 게이트 | cmd | exit |
|---|---|---|---|---|
| M1 | `app.wiring`에 outbox로 쓰는 `TeeNotificationPort : NotificationRequestPort` 신설(`OutboxPort` 참조 포함) | `ArchitectureGateTest`(D-6A3-17(a)①③) | `:app:test --tests 'bidvector.app.architecture.ArchitectureGateTest'` | 1(RED) |
| M4 | 루트 패키지 `bidvector.app`에 `GrpcBidPredictionGateway` 참조 신설 | `ArchitectureGateTest`(D-6A3-17(b)) | 위와 같음(같은 실행에 포함) | 1(RED) |
| M3 | `EvaluationDryRunResponse.from`의 `wouldNotifyNoticeIds`를 `emptyList()`로 고정 | `EvaluationDryRunBidNowE2ETest` | `:app:test --tests 'bidvector.app.http.EvaluationDryRunBidNowE2ETest'` | 1(RED, `Set should not be empty`) |
| M2 | `EvaluationDryRunFactory`가 use case에 `strategies = pinnedStrategies` 대신 `strategies = strategyRepository`(delegate 그대로)를 넘김 | `EvaluationDryRunFactoryTest`(evaluate() 까지 도는 새 계수 test) | `:app:test --tests 'bidvector.app.wiring.EvaluationDryRunFactoryTest'` | 1(RED, `expected:<1> but was:<2>`) |
| M8 | `GlobalErrorHandler`의 `@ExceptionHandler(CandidateCapExceededException::class)` 삭제 | `OpenApiDryRunContractTest`(409 CANDIDATE_CAP_EXCEEDED HTTP test) | `:app:test --tests 'bidvector.app.http.OpenApiDryRunContractTest'`(sizeGate 분리 후 재실측) | 1(RED, `expected:<409> but was:<500>`) |
| M7a | OpenAPI에서 `/api/evaluation-dry-runs`의 `409` 응답 선언 삭제 | `OpenApiDryRunContractTest`(각 path 상태 코드 선언 집합) | `:app:test --tests 'bidvector.app.http.OpenApiDryRunContractTest'`(sizeGate 분리 후 재실측) | 1(RED, `expected:<["200", "400", "401", "409", "500"]> but was:<["200", "400", "401", "500"]>`) |
| — | `Sql.UPSERT_STRATEGY`의 `max_active_bids = EXCLUDED.max_active_bids,` 줄 삭제 | `StrategyRowMaxActiveBidsTest`(두 번 저장) | `:adapters:test --tests 'bidvector.adapters.strategy.StrategyRowMaxActiveBidsTest'` | 1(RED, `expected:<3> but was:<7>`) |
| — | V16 `CHECK (max_active_bids > 0)` → `CHECK (max_active_bids >= 0)`로 완화 | `StrategyRowMaxActiveBidsTest`(0 직접 INSERT) | 위와 같음 | 1(RED, "no exception was thrown") |
| — | D-6A3-14 컬럼·CHECK 추가분(`max_active_bids`)을 기대 목록에서 되돌림 | `CleanMigrationColumnTest`·`CleanMigrationCheckTest` | `:adapters:test --tests 'bidvector.adapters.persistence.CleanMigrationColumnTest' --tests 'bidvector.adapters.persistence.CleanMigrationCheckTest'` | 1(RED, 둘 다 FAILED — D-6A3-14 절 참고) |

착수 라운드가 실측한 다섯 변이(assemble* 새 호출자·전략 값 지어냄 등)는 이 range 가 그 경로를
바꾸지 않아 그대로 유효하다 — `git log`(이 slice 커밋 이력)가 근거다.

## 게이트·보안 스캔

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`(HEAD `18fdd72e`)
- exit: 0(빈 출력) — clean-tree 통과. 양성 대조: 임시 줄 추가 → `git status`가 `M` 보고 →
  비파괴 절삭 → 재확인 diff 0줄(착수 라운드에서 이미 실측, 이후 매 커밋 직전
  `git diff --cached --name-status`로 in_scope 대조).

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m6/6a3f3/`
- exit: 0(매치 있음, 32건)
- 핵심 결과: 전부 false positive로 육안 확인 — 세 부류: E2E 의 Testcontainers 일회성 DB 접속 고정값(운영 값
  아님) · OpenAPI 인증 스키마 선언의 표준 키워드 · 편집 필드 토큰 파싱 함수 이름. 실 비밀값 0건(어휘를 축어로
  적지 않는다 — 하네스 규율, verifier r3 BLOCKER-R3-1). 이 라운드(D-6A3-25·R2-M1)가 추가한 코드에는 비밀값류
  문자열이 없다(도메인 예외 메시지·ArchUnit 규칙 설명·KDoc뿐 — 위 32건 목록에 이 라운드의 신규
  파일은 없다).

## 롤백 실측(별도 문서)

절차·①~⑥ 실측은 `rollback.md` 참고(실측 HEAD는 이 문서와 같은 `18fdd72e`).
