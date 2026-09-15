# M2/2F commands.md

정본은 명령과 exit code, 핵심 결과 한 줄이다. 출력 전문은 재현 명령으로 대신한다.
base `c669a71affabed165c1afbd8b11c8245b81a3a5a`, head는 리뷰 시점의 `git rev-parse HEAD`
— 값을 박지 않는다(2A verifier r1 F-1 관례).

## S-2 — buf lint/build(proto 편집 직후)

- cmd: `(cd contracts && buf lint && buf build)`
- exit: 0(둘 다)
- 핵심 결과: lint 위반 0, build 성공(`CompetitionSample` 필드 8·9, `Diagnostics` 필드
  3~6, `IntervalSource` 값 3, `ModelRelease.release_kind`+`ReleaseKind` enum 포함).

## S-9 — 현 승인 태그 대비 breaking 0(proto 편집 직후, 태그 신설 전 정본 증거)

- cmd: `(cd contracts && buf breaking --against '../.git#tag=contracts/v1-approved-2026-09-10,subdir=contracts')`
- exit: 0
- 핵심 결과: finding 0 — 다섯 additive(표본 축 둘·diagnostics 넷·enum 값·release_kind
  필드+enum) 전부 호환 변경으로만 잡힘.

## testdata 재생성(재현 절차) — `contracts/testdata/prediction/`

- cmd: (cwd `contracts/testdata/prediction`) 6개 파일에 대해
  `buf convert ../.. --type bidvector.ml.v1.<Type> --from <name>.json --to <name>.binpb`
  (`CalculateOptimalBidRequest`·`CalculateOptimalBidResponse`×4·`GetModelMetadataResponse`)
- exit: 0(6/6)
- 핵심 결과: 기존 5쌍 갱신(표본 축 값 1·missing 1, diagnostics 넷, release_kind ARTIFACT) +
  신설 1쌍(`calculate_optimal_bid_response_success_posterior_predictive` — release_kind
  DERIVED·interval_source POSTERIOR_PREDICTIVE·`dataset_id=""`·
  `training_row_count=0`).

## testdata round-trip 자체 검증 — JSON → binpb → JSON 의미 동등

- cmd: 6개 전건에 대해 `buf convert ../.. --type bidvector.ml.v1.<Type> --from
  <name>.binpb --to -#format=json | jq -S .` 후 원본 `jq -S .`와 `diff`
- exit: 0(6/6 의미 동등)
- 핵심 결과: `calculate_optimal_bid_request`·두 `unmeasurable`·`get_model_metadata_response`
  는 완전 일치. `calculate_optimal_bid_response_success`·`..._posterior_predictive`는
  proto3 기본값(`agencySampleBelowThreshold: false`·`excludedObservations: 0`·
  `trainingRowCount: 0`·`datasetId: ""`)이 canonical JSON 출력에서 생략됨 — 2E·2B 실측과
  같은 표준 동작(결함 아님). `calculate_optimal_bid_response_failure_unsupported_schema`도
  기존부터 있던 `retryable: false` 생략(2F 무관, 사전 존재).

## S-3 — breaking mutation 스윕(양성 대조, 신설 파일 없음 — 2E F-1 재발 없음)

- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: 11/11 mutation 잡힘(`[잡힘] field-delete`~`optional-removal`), 호환 변경
  넷(필드·enum 값·RPC·메시지 추가) 양성 대조 통과. `breaking-mutations.sh` 무편집(digest
  ②-d 예측대로 — mutation 은 내용 앵커 sed, 필드 추가로 안 밀림).

## GREEN — Kotlin 계약 test(신설 + 기존 전건)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*Contract*'`
- exit: 0
- 핵심 결과: `PredictionContractTest` 43/43(기존 34 + 신설 DERIVED round-trip 1 + import
  정리), `PredictionAdditiveContractTest`(신설) 9/9 — diagnostics 보존·POSTERIOR_PREDICTIVE
  파싱/round-trip·표본 축 왕복·release_kind ARTIFACT/DERIVED 통과·UNSPECIFIED 거부·
  kind↔접두 불일치 거부 둘.

- cmd: `./gradlew --no-daemon :adapters:test :workflow:test`
- exit: 0
- 핵심 결과: adapters+workflow 합계 706 tests, 0 failures, 0 skipped(기존 test 전건 —
  `SuccessShapeFailClosedTest`·`ReleaseCheckTest`·`GrpcBidPredictionGatewayTest`·
  `EmbeddingContractTest`·`EmbeddingShapeFailClosedTest` 포함 — `hasNonBlankRelease`
  무변경·`testModelRelease()` 기본 ARTIFACT 로 회귀 없음 확인).

## S-4 — `:adapters:test --tests '*…Contract*'`(scope.md 축어)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*Contract*'`
- exit: 0(위 GREEN 명령과 동일 — 재기재)

## S-5 — Python 계약 test

- cmd: `(cd ml-engine && uv run python -m pytest tests/test_prediction_contract.py tests/test_contract_roundtrip.py -q)`
- exit: 0
- 핵심 결과: 54 passed — `test_prediction_contract.py` 45(base 35 + 신설 10: diagnostics·
  posterior_predictive interval_source·derived training_row_count·표본 축·release_kind
  넷·round-trip) + `test_contract_roundtrip.py` 9(무변경). 실측 대조:
  rollback 임시 clone에서 base 상태 `test_prediction_contract.py` 단독 실행 —
  `35 passed`(아래 「rollback 실측」).

## S-6 — 교차 언어 socket 스모크

- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(proto 형태 변경에도 기존 RPC 배선 무영향).

## S-7 — gateExecutionGate(adapters·workflow)

- cmd: `./gradlew --no-daemon :adapters:test :workflow:test` (선행) 후
  `./gradlew --no-daemon :adapters:gateExecutionGate :workflow:gateExecutionGate`
- exit: 0(둘 다)
- 핵심 결과: `gate.tests.adapters`에 신설 등재한 `PredictionAdditiveContractTest` 포함
  전 class 실행 확인 — 단독 `--tests` 필터와 함께 부르면 다른 등재 class 가 「실행되지
  않았다」로 실패하므로(gateExecutionGate 는 전건 실행을 요구) 전체 `test` 선행이 필요
  (실측 실패→원인 확인→정정 절차).

## S-8 — qualityBaseline

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 생성.

## 커밋 순서 — proto+testdata → Kotlin → Python → gate 등재 → 문서

각 커밋 뒤 clean-tree 로 다음 커밋 대상만 남았음을 확인(아래 「clean-tree 게이트」).

## S-1 — `--no-build-cache --no-daemon clean check`(전 모듈, 현 태그 대비)

- cmd: `./gradlew --no-build-cache --no-daemon clean check` (1차, proto+testdata 만 커밋한
  시점 — 나머지 미커밋)
- exit: 1
- 핵심 결과: **두 건** — (a) `:adapters:sizeGate` FAILED,
  `PredictionContractTest.kt` 566줄(500줄 한도 초과) → 신설 단언을
  `PredictionAdditiveContractTest.kt`로 분리(size ratchet, 2E `EmbeddingTestdataCanonicalTest`
  분리와 같은 사유). (b) `:contractGate` FAILED, 「작업 트리가 비어있지 않다(ml-contract,
  contracts)」 — `contracts/**` 미커밋 편집이 남아 있었다(`clean check`는 커밋된 트리를
  전제). 두 건 다 실 결함이 아니라 미완료 커밋 상태의 산출물 — 파일 분리 + 커밋 완료 뒤
  재실행.

- cmd: `./gradlew --no-build-cache --no-daemon clean check` (2차, 파일 분리 커밋 + gate
  등재 + 문서 커밋 후, 전부 커밋된 상태)
- exit: 1
- 핵심 결과: `:adapters:ktlintMainSourceSetCheck` FAILED — `ReleaseShapeValidation.kt`·
  `ResponseMapping.kt`의 `when` 분기 8건이 `standard:when-entry-bracing`·
  `standard:blank-line-between-when-conditions` 위반(멀티라인 분기는 중괄호로 감싸야
  함). `ktlintMainSourceSetFormat` 자동 교정, 동작 변화 없음(별도 커밋).

- cmd: `./gradlew --no-build-cache --no-daemon clean check` (3차, ktlint 교정 커밋 후)
- exit: (재실행 진행 중 — 아래 「S-1 3차 결과」에 기재)

## S-1 3차 결과

- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 41s`, 346 actionable tasks(322 executed, 24 up-to-date).
  `:contractGate`·`:adapters:sizeGate`를 직접 재호출해 개별 확인(`./gradlew --no-daemon
  :contractGate :adapters:sizeGate` — 둘 다 `BUILD SUCCESSFUL`, `:adapters:sizeGate
  UP-TO-DATE`·`:contractGate` 실행). ktlint·detekt·ArchUnit·cpdCheck·kover 포함 전 모듈
  `:check` 통과.

## S-0 — 격리 worktree 전건(clean check)

- cmd: `git worktree add --detach <scratchpad>/s0-2f-worktree 693bc5a19b9de8850e887c24e6a1fba4342f20ea
  && (cd <dir> && ./gradlew --no-build-cache --no-daemon clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 59s`, 355 actionable tasks(355 executed — 캐시 없이
  전건). 현 승인 태그(`contracts/v1-approved-2026-09-10`)가 아직 유효해 `contractGate`
  포함 정상 실행(종결 승인 커밋에서만 태그 부재로 미실행).
- cmd(정리): `git worktree remove --force <scratchpad>/s0-2f-worktree`
- exit: 0
- 핵심 결과: `git worktree list`에 잔여 없음, 디렉터리 자체도 삭제 확인(`ls` exit 1 —
  「No such file or directory」).

## 누출 검사

- cmd: `grep -rniE -f config/quality/leak-patterns.txt contracts/proto contracts/testdata adapters/src workflow/src ml-engine/tests/test_prediction_contract.py reports/evidence/m2/2f --exclude=scope.md`
- exit: 0(매치 있음 — 판독)
- 핵심 결과: 매치 전부 이 slice 의 diff 밖(koneps `ServiceKeyTest`·extraction LLM 토큰
  test·persistence 테스트 컨테이너 비밀번호·notification `RouteKeyTest` — 다른 slice 가
  만든 기존 secret-스캔 test fixture, developer 구간 무매치). in_scope 파일(proto·
  testdata·`RequestMapping.kt`·`ParsedSuccessFields.kt`·`ReleaseShapeValidation.kt`·
  `ResponseMapping.kt`·`BidPredictionOutcome.kt`·`BidPredictionRequest.kt`·
  `PredictionContractTest.kt`·`PredictionAdditiveContractTest.kt`·`MlTestFixtures.kt`·
  `test_prediction_contract.py`) 자체에는 매치 0.

## clean-tree 게이트 — in_scope 경로 개별 인자

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: evidence 파일(이 문서·checklist.md·rollback.md, 커밋 전)만 나타남 —
  나머지는 이미 커밋됨.

## rollback 실측(임시 clone)

정본은 `rollback.md`. `git clone --no-hardlinks <repo> <scratch>/rollback-test-clone-2f`
→ `git checkout m2-2f/2026-09-15` → `git restore --source=c669a71... --staged --worktree
-- <rollback.md 23경로>`.

- exit: 0
- 핵심 결과: `git status --porcelain` — D 3(신규 파일)·M 20(편집 파일), 23경로 전부 반영.
- cmd: `git diff c669a71... -- <편집 파일 20개>` (파이프 `wc -l`)
- exit: 0
- 핵심 결과: 0줄(작업 트리가 base와 바이트 단위로 동일).
- cmd: `(cd contracts && buf lint && buf build)`
- exit: 0(둘 다) — 다섯 additive 사라진 6개 proto 파일로 계약 자족.
- cmd: `./gradlew --no-daemon :adapters:compileTestKotlin :workflow:compileTestKotlin`
- exit: 0.
- cmd: `./gradlew --no-daemon :adapters:test :workflow:test`
- exit: 0(BUILD SUCCESSFUL).
- cmd: `(cd ml-engine && uv sync --extra dev && uv run python -m pytest
  tests/test_prediction_contract.py -q)`
- exit: 0 — **35 passed**(2F 신설 10건 없이 base 상태로 정확히 축소 — 위 S-5 「실측
  대조」의 근거).
