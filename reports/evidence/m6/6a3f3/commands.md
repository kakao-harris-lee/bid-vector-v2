# M6/6A-3+6F-3 commands

acceptance는 `.github/workflows/ci.yml`의 `check`·`container` job 명령 그대로(구현 레인이
착수 시 원문 대조). 전부 버릴 worktree(`git worktree add --detach <scratch> <sha>`)에서,
`check`는 캐시 우회(`--rerun-tasks`) 1회 포함. **라운드 이력을 남기지 않는다 — 최종 명령·
exit만 적는다**(evidence-pack 규율). 실측 HEAD는 `0dd74d19`(검토 라운드 1 수정 마지막 산출물
커밋 — 이후 커밋은 evidence 전용).

## check job — 정본(GREEN)

- cmd: `./gradlew --no-daemon check --rerun-tasks`(버릴 worktree `ci-final`, HEAD `0dd74d19`)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 346 actionable tasks executed(캐시 전부 우회), 4m50s. 컴파일·
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

이 검토 라운드는 `docker/`·`ml-engine/`·`docker/compose.yaml` 어느 것도 만지지 않았다 —
`git diff --name-only febad567..0dd74d19 -- docker/ ml-engine/ docker/compose.yaml` 빈 출력으로
확인(cmd 그대로, exit 0). container job의 최초 GREEN 결과(이미지 빌드·위생 게이트·compose
healthy·`RealServerIntegrationTest`)는 착수 라운드에서 이미 확인됐고 이 라운드가 그 경로를
건드리지 않았으므로 그대로 유효하다 — 「이 slice의 배선 변경이 부팅에 닿는다」는 확인은 아래
`ProductionAssemblyAuthAuditTest`·`EvaluationDryRunE2ETest`(production 조립 실제 부팅)가 진다.

## acceptance ①~⑧ — production 조립 E2E

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.http.EvaluationDryRunE2ETest'`
- exit: 0
- 핵심 결과: 6 tests, 0 failed — ① 후보없음 200/빈배열(키 집합 등식 포함) ② 상한없음 409
  ③ 음수 400 ④ 무인증 401 ⑤⑥⑦ 표본 1건 → candidateCount 1·reviewNoticeIds 1(결정적)·outbox
  전후 등식·wouldNotifyNoticeIds==bidNowNoticeIds(엄격 캐스트) ⑧ 제목·기관명 있는 표본 공고
  → 원시 응답 문자열에 원문 없음(D-6A3-22).

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.http.EvaluationDryRunBidNowE2ETest'`
- exit: 0
- 핵심 결과: BidNow fake ML(profile 격리) → wouldNotifyNoticeIds 비어있지 않음==bidNowNoticeIds,
  outbox 전후 등식(D-6A3-17(a) 거동 다리).

- cmd: `./gradlew --no-daemon :app:test`(전체, HEAD `0dd74d19`)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(app 모듈 전체 test — http·wiring·architecture·conformance 포함,
  위 check job 실측에 포함된 것과 같은 실행).

## 검토 라운드 1 — 게이트 술어 변경에 대한 변이 실측(전부 버릴 worktree, 적용→RED 확인→worktree 폐기로 원복)

verifier r1(HIGH 4·MEDIUM 4)·code-reviewer(MEDIUM 1)·migration-reviewer(위반 1·권고 2)의
표적을 수정한 게이트마다, 판정이 지목한 변이를 새 게이트 위에 재현해 RED를 확인했다. 수정
전 GREEN(판정 대상 SHA `ff69cdd2`에서의 관측)은 `_workspace/m6-6a3f3/10_verifier_report.md`·
`11_code_review.md`·`12_migration_review.md`가 정본이다 — 여기는 **수정 후** 재현만 적는다.

| # | 변이 | 표적 게이트 | cmd | exit |
|---|---|---|---|---|
| M1 | `app.wiring`에 outbox로 쓰는 `TeeNotificationPort : NotificationRequestPort` 신설(`OutboxPort` 참조 포함) | `ArchitectureGateTest`(D-6A3-17(a)①③) | `:app:test --tests 'bidvector.app.architecture.ArchitectureGateTest'` | 1(RED) |
| M4 | 루트 패키지 `bidvector.app`에 `GrpcBidPredictionGateway` 참조 신설 | `ArchitectureGateTest`(D-6A3-17(b)) | 위와 같음(같은 실행에 포함) | 1(RED) |
| M5 | `app.http`에 `CandidateSourcePort`를 직접 부르는 `EvaluationShortcut` 신설 | `ArchitectureGateTest`(D-6A3-17(c)) | 위와 같음(같은 실행에 포함) | 1(RED) |
| M3 | `EvaluationDryRunResponse.from`의 `wouldNotifyNoticeIds`를 `emptyList()`로 고정 | `EvaluationDryRunBidNowE2ETest` | `:app:test --tests 'bidvector.app.http.EvaluationDryRunBidNowE2ETest'` | 1(RED, `Set should not be empty`) |
| M2 | `EvaluationDryRunFactory`가 use case에 `strategies = pinnedStrategies` 대신 `strategies = strategyRepository`(delegate 그대로)를 넘김 | `EvaluationDryRunFactoryTest`(evaluate() 까지 도는 새 계수 test) | `:app:test --tests 'bidvector.app.wiring.EvaluationDryRunFactoryTest'` | 1(RED, `expected:<1> but was:<2>`) |
| M8 | `GlobalErrorHandler`의 `@ExceptionHandler(CandidateCapExceededException::class)` 삭제 | `OpenApiDryRunContractTest`(409 CANDIDATE_CAP_EXCEEDED HTTP test) | `:app:test --tests 'bidvector.app.http.OpenApiDryRunContractTest'`(sizeGate 분리 후 재실측) | 1(RED, `expected:<409> but was:<500>`) |
| M7b | OpenAPI에서 `/api/evaluation-dry-runs`의 `409` 응답 선언 삭제 | `OpenApiDryRunContractTest`(각 path 상태 코드 선언 집합) | `:app:test --tests 'bidvector.app.http.OpenApiDryRunContractTest'`(sizeGate 분리 후 재실측) | 1(RED, `expected:<["200", "400", "401", "409", "500"]> but was:<["200", "400", "401", "500"]>`) |
| — | `Sql.UPSERT_STRATEGY`의 `max_active_bids = EXCLUDED.max_active_bids,` 줄 삭제 | `StrategyRowMaxActiveBidsTest`(두 번 저장) | `:adapters:test --tests 'bidvector.adapters.strategy.StrategyRowMaxActiveBidsTest'` | 1(RED, `expected:<3> but was:<7>`) |
| — | V16 `CHECK (max_active_bids > 0)` → `CHECK (max_active_bids >= 0)`로 완화 | `StrategyRowMaxActiveBidsTest`(0 직접 INSERT) | 위와 같음 | 1(RED, "no exception was thrown") |
| — | D-6A3-14 컬럼·CHECK 추가분(`max_active_bids`)을 기대 목록에서 되돌림 | `CleanMigrationColumnTest`·`CleanMigrationCheckTest` | `:adapters:test --tests 'bidvector.adapters.persistence.CleanMigrationColumnTest' --tests 'bidvector.adapters.persistence.CleanMigrationCheckTest'` | 1(RED, 둘 다 FAILED — D-6A3-14 절 참고) |

모든 변이는 `git worktree add --detach <scratch> <sha>`로 만든 버릴 worktree에서 적용했고,
확인 뒤 `git worktree remove --force`로 폐기했다(원본 트리에 영향 없음 — 각 worktree의
`git diff --numstat`으로 변이 적용 자체를 먼저 확인했다).

## 검토 라운드 1 착수 이전(전 라운드) 변이 실측 — 유지되는 다섯

착수 라운드가 실측한 다섯 변이(assemble* 새 호출자·전략 값 지어냄 등)는 이번 라운드가 그
경로를 바꾸지 않아 그대로 유효하다 — 원 실측은 `_workspace/m6-6a3f3/10_verifier_report.md`
「재현」 절 및 이전 checklist 갱신 이력(git log) 참고. 이번 라운드는 **새로 바뀐 게이트**의
변이만 위 표로 추가 기록한다(중복 재실행 없음).

## 게이트·보안 스캔

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0(빈 출력) — clean-tree 통과. 양성 대조: 임시 줄 추가 → `git status`가 `M` 보고 →
  비파괴 절삭 → 재확인 diff 0줄(착수 라운드에서 이미 실측, 이번 라운드도 매 커밋 직전
  `git diff --cached --name-status`로 in_scope 대조).

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m6/6a3f3/`
- exit: 0(매치 있음)
- 핵심 결과: 전부 false positive로 육안 확인 — Testcontainers 고정 fixture 문구(운영 값
  아님), OpenAPI 보안 스키마 선언 키워드, 편집 필드 토큰 파싱 식별자. 실 비밀값 0건. 이번
  라운드가 추가한 비밀값류 문자열 없음(신규 코드는 도메인 예외 메시지·ArchUnit 규칙 설명뿐).

## 롤백 실측(별도 문서)

절차·①~⑥ 실측은 `rollback.md` 참고(실측 HEAD는 이 문서와 같은 `0dd74d19`).
