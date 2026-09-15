# M2/2F rollback.md

정본은 **in_scope 경로 한정 복원**이다(range revert 금지 — 2026-09-04 관례). 하네스 경로
(`CLAUDE.md`·`.claude/**`)는 대상이 아니다(이 slice에서 변경 없음, scope.md 「하네스 레인
변경」 절 — 착수 시 없음, 이후로도 없음).

**`milestone-2.md`·`reports/evidence/m2/2f/scope.md`는 되돌리지 않는다** — 2E 전례와 같은
경계(rollback은 기능 산출물을 대상으로 하고, 착수 계약·설계 문서는 「이 slice가 시도됐다」
기록으로 남긴다). 둘 다 팀장의 착수 계약 고정 커밋(`7b4a9ef`)만 건드렸다(`git show --stat
7b4a9ef` — 이 두 파일뿐, 구현 레인 커밋과 겹침 없음). base..HEAD 안에서 아래 23경로는
**이 slice만 만졌다**(단일 레인 브랜치, 동시 병행 레인 없음 — 확인: `git log
--oneline c669a71..HEAD`가 팀장 착수 커밋 1 + 구현 레인 커밋 6뿐). 그래서 hunk 격리 없이
base 상태로 직접 복원해도 안전하다.

## 되돌리는 것

- 신규 파일(`--source=<base>`에 없음 → 복원 시 삭제):
  - `adapters/src/test/kotlin/bidvector/adapters/contract/PredictionAdditiveContractTest.kt`
  - `contracts/testdata/prediction/calculate_optimal_bid_response_success_posterior_predictive.binpb`
  - `contracts/testdata/prediction/calculate_optimal_bid_response_success_posterior_predictive.json`
- 편집 파일(`--source=<base>`가 있음 → base 상태로 복원):
  - `adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt`
  - `adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseShapeValidation.kt`
  - `adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt`
  - `adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt`
  - `adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt`
  - `adapters/src/test/kotlin/bidvector/adapters/ml/MlTestFixtures.kt`
  - `config/quality/gate-tests.properties`(`PredictionAdditiveContractTest` 행·M2/2F 주석
    블록 제거)
  - `contracts/proto/bidvector/ml/v1/features.proto`(`CompetitionSample` 필드 8·9·
    D-2B-8 축 한정 주석 제거)
  - `contracts/proto/bidvector/ml/v1/prediction.proto`(`Diagnostics` 필드 3~6·
    `IntervalSource` 값 3·`ReleaseKind`+`ModelRelease.release_kind`·`Weight` KDoc 확장·
    `Candidate.bid_rate` 주석 제거)
  - `contracts/testdata/prediction/calculate_optimal_bid_request.{json,binpb}`
  - `contracts/testdata/prediction/calculate_optimal_bid_response_success.{json,binpb}`
  - `contracts/testdata/prediction/get_model_metadata_response.{json,binpb}`
  - `docs/discovery/capability-map.md`(다섯 OPEN wire 측 닫힘 표기·`OPEN-2F-DICT-INTERVAL-SOURCE`
    신설 행 제거)
  - `ml-engine/tests/test_prediction_contract.py`(10건 신설 test·헬퍼 둘 제거)
  - `reports/evidence/m2/2b/scope.md`(갱신 이력 절 제거)
  - `workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt`
    (`IntervalSource.PosteriorPredictive`·`ReleaseKind`·`ModelReleaseRef.kind` 제거)
  - `workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt`
    (`CompetitionSample.agencyId`·`categoryCode` 제거)

## 명령

```bash
BASE=c669a71affabed165c1afbd8b11c8245b81a3a5a
git restore --source="$BASE" --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/contract/PredictionAdditiveContractTest.kt \
  contracts/testdata/prediction/calculate_optimal_bid_response_success_posterior_predictive.binpb \
  contracts/testdata/prediction/calculate_optimal_bid_response_success_posterior_predictive.json \
  adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt \
  adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlTestFixtures.kt \
  config/quality/gate-tests.properties \
  contracts/proto/bidvector/ml/v1/features.proto \
  contracts/proto/bidvector/ml/v1/prediction.proto \
  contracts/testdata/prediction/calculate_optimal_bid_request.json \
  contracts/testdata/prediction/calculate_optimal_bid_request.binpb \
  contracts/testdata/prediction/calculate_optimal_bid_response_success.json \
  contracts/testdata/prediction/calculate_optimal_bid_response_success.binpb \
  contracts/testdata/prediction/get_model_metadata_response.json \
  contracts/testdata/prediction/get_model_metadata_response.binpb \
  docs/discovery/capability-map.md \
  ml-engine/tests/test_prediction_contract.py \
  reports/evidence/m2/2b/scope.md \
  workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt \
  workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt
```

`--source`에 없는 경로(신규 파일 3종)는 `git restore`가 작업 트리에서 삭제한다 — 별도
`git rm`이 필요 없다(2026-09-04 관례).

## 확인 지점

① `git status --porcelain -- <위 23개 경로>`는 **빈 출력이 아니다**(staged 상태로 D/M이
   남는다, 2B·2E rollback.md 관례와 같은 사실) — 복원 확인은 아래 ②~⑥으로 한다.
② `git diff "$BASE" -- <편집 파일 20개>`가 빈 출력(작업 트리 내용이 base와 완전히 같음).
③ `(cd contracts && buf lint && buf build)` exit 0 — 다섯 additive 없이도 계약 자족
   (필드 8·9·`Diagnostics` 필드 3~6·`IntervalSource` 값 3·`ReleaseKind` 사라짐).
④ **되돌린 트리의 compile** — `./gradlew --no-daemon :adapters:compileTestKotlin
   :workflow:compileTestKotlin` exit 0(`PredictionContractTest.kt`·`MlTestFixtures.kt`의
   2F 참조가 남아 컴파일이 깨지지 않는지 확인).
⑤ **되돌린 트리의 test** — `./gradlew --no-daemon :adapters:test :workflow:test` exit 0
   (2F 이전 상태로 정상 축소 — `SuccessShapeFailClosedTest`·`ReleaseCheckTest`·
   `EmbeddingContractTest` 등 기존 test 전건 회귀 없음) + `(cd ml-engine && uv sync
   --frozen --all-extras && uv run python -m pytest tests/test_prediction_contract.py -q)`
   exit 0(10건 신설 test 없이 base 35건만 남음 — `uv run` 만으로는 dev 의존(`pytest`)이 fresh
   clone 에 없어 `No module named pytest`가 난다, `--extra dev`만으로도 부족해 `--frozen
   --all-extras`로 정정).
⑥ **되돌린 트리의 게이트**(2026-09-12 규격 필수 항목, verifier r1 F-2) — `git restore
   --staged --worktree`는 **staged 된 D/M을 남긴다**. 그 상태로 바로 `clean check`를 돌리면
   `contractGate`(작업 트리가 비어있어야 한다는 전제)·`leakPatternGate`(2F 자신의 evidence
   3파일은 이 rollback 대상이 아니라 여전히 존재 — 그 자체는 F-1 수정 뒤 깨끗하지만,
   staged 상태 자체가 `contractGate`의 "작업 트리가 비어있지 않다" 판정을 촉발한다)가
   **절차 잔여**로 붉어진다(실제 되돌림 실패가 아니라 확인 순서 문제). 그래서 ⑥은
   `git add -A && git commit -m "rollback probe"`(**임시 clone 안에서만** — 실 저장소에는
   커밋하지 않는다, 이 커밋은 `clean check` 전제인 "커밋된 트리"를 임시로 흉내 내는 것일
   뿐이다)로 staged 를 커밋한 뒤 `./gradlew --no-build-cache --no-daemon clean check`
   exit 0을 확인한다.

## 예상 복구 시간

1분 미만(파일 삭제·`git restore` 한 번) + compile/test 확인 시 약 2~3분(캐시 활용 시) +
⑥ 전건 게이트 확인 시 추가 1~2분(캐시 활용 시).

## 실측(임시 clone)

`commands.md` 「rollback 실측(임시 clone)」 참고 — 위 명령을 `git clone --no-hardlinks`
임시 clone에서 그대로 실행해 exit 0·대상 경로 삭제/복원·확인 지점 ②~⑥(diff 빈 출력·
buf lint/build·compileTestKotlin·test·전건 게이트)을 전부 확인한다.

**라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 목록은
`git diff --name-status "$BASE"..HEAD`에서 기계적으로 낸다(milestone-2.md·2f/scope.md
제외).
