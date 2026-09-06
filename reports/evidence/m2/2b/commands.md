# M2/2B commands.md

정본은 명령과 exit code, 핵심 결과 한 줄이다. 출력 전문은 재현 명령으로 대신한다.
base `55ecdcc`(2A 종결·2B 착수 커밋), head는 리뷰 시점의 HEAD — 구체 SHA 를 여기 박지
않는다(2A verifier r1 F-1 관례). Phase 3 구현 커밋 목록은 진행 순서로 `checklist.md`
「커밋 목록」이 갖는다.

## §4b 스모크(선행) — `ml-contract` 가 새 `.proto`(service 포함)를 실제로 생성하는가

- cmd: `./gradlew --project-dir ml-contract build`
- exit: 0
- 핵심 결과: `generateProto`가 `BidPredictionServiceGrpc.java`(grpc-java)와
  `PredictionGrpcKt.kt`(`BidPredictionServiceCoroutineImplBase`·`CoroutineStub` 포함,
  grpc-kotlin)를 실제로 생성. 컴파일·jar 생성 성공(2A는 `.proto`에 `service`가 없어
  두 플러그인이 무출력이었다 — 이번이 실제 생성의 첫 실측).

## S-2 — buf lint/build

- cmd: `(cd contracts && buf lint && buf build)`
- exit: 0
- 핵심 결과: lint 위반 0, build 성공. `common.proto`·`error.proto` 무편집(D-2B-1~4 전부
  기존 어휘 재사용 — `BidRateOrigin`이 이미 2A에 있어 「2A 파일 편집」이 발생하지 않았다).

## testdata 생성(재현 절차) — `contracts/testdata/prediction/`

- cmd: JSON 원본 6개를 손으로 작성(값·enum·oneof는 `docs/discovery/data-dictionary.md`
  §6.5·`_workspace/m2-prep/01_scout_ml_interface.md` 조사 결과 기반) 후
  `buf convert contracts --type bidvector.ml.v1.<Type> --from <name>.json --to <name>.binpb`
  를 6개 타입(`CalculateOptimalBidRequest`·`CalculateOptimalBidResponse`×4·
  `GetModelMetadataResponse`)에 대해 실행
- exit: 0(6/6)
- 핵심 결과: canonical `.binpb` 6개 + JSON 원본 6개 커밋(scope.md 「canonical 바이트 +
  사람이 읽는 JSON 원본」).

## testdata round-trip 자체 검증 — JSON → binpb → JSON 의미 동등

- cmd: `buf convert contracts --type bidvector.ml.v1.<Type> --from <name>.binpb --to -#format=json`
  후 원본 JSON과 `jq -S` 정렬 비교(6개 전건)
- exit: 0(6/6 의미 동등)
- 핵심 결과: 5개는 완전 일치, 1개(`ApplicationFailure`)는 `retryable: false`(bool 기본값)가
  canonical JSON 출력에서 생략됨 — proto3 기본값 필드 elision(표준 동작, 결함 아님). 와이어
  바이트 자체는 두 경로(JSON→binpb, binpb→JSON→binpb) 모두 동일.

## S-3 — round-trip + fake servicer consumer test(Kotlin)

- cmd: `./gradlew :adapters:test --tests '*Prediction*Contract*'`
- exit: 0
- 핵심 결과: `PredictionContractTest` 27/27 통과(in-process 실제 RPC 2건 포함 —
  `BidPredictionServiceCoroutineImplBase` fake 위에서 `calculateOptimalBid`·
  `getModelMetadata` 호출).
- **버그 실측**: 최초 구현에서 이 두 test가 `= runBlocking { ... }`(식 본문)이었고 마지막
  문장이 kotest `shouldBe`(수신자 반환)라 함수 추론 반환 타입이 `Unit`이 아니게 됨 →
  JUnit Jupiter가 조용히 discover하지 않음(`tests="25"`, 2건 누락 — 가짜 초록). 블록
  본문(`{ }`)으로 교정 후 `tests="27"` 재확인.

## S-4 — round-trip + fake servicer provider test(Python)

- cmd: `(cd ml-engine && python -m pytest tests/test_prediction_contract.py tests/test_contract_roundtrip.py -q)`
- exit: 0
- 핵심 결과: 37/37 통과(신규 28 + 2A 9). 가상환경 `ml-engine/.venv`(grpcio 1.83.1,
  protobuf 7.36.1, pytest 9.1.1 — 2A와 동일 실측 해석 버전, 신규 의존 추가 없음).
  `conftest.py`(2A, scope.md in_scope 밖)는 무편집 — 이 파일 안 독립 module fixture로
  `features.proto`·`prediction.proto`(+`grpc_python_out`)까지 생성.

## S-1 — 기존 게이트 전건(ktlint·detekt·ArchUnit·moduleDependencyGate 등)

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0(1차 시도는 detekt `MaxLineLength` 3건으로 FAILED — 수동 개행 후 재실행 exit 0.
  2차는 ktlint 10건 위반 — `./gradlew :adapters:ktlintTestSourceSetFormat`으로 자동
  정리 후 재실행 exit 0)
- 핵심 결과: 322 actionable tasks, BUILD SUCCESSFUL(재현 시 최종 상태 기준).

## S-5 — quality baseline

- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 갱신.

## S-0 — clean worktree 전건

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: 331 actionable tasks, 전부 executed(캐시 없음), BUILD SUCCESSFUL. worktree는
  `git worktree remove --force`로 제거, 잔여 디렉터리 없음 확인(`ls` → No such file).

## 변이 실측(≥5, 계약이 실제로 우회를 막는지 — Kotlin·Python 양쪽 test로 실측)

1. **후보가 2개면 불변식 위반** — `Success.candidates`에서 인덱스 2를 제거한 사본에
   `hasExactlyThreeOrderedCandidates`(Kotlin)/`_has_three_ordered_candidates`(Python)가
   `false`. 정상 3후보 사본은 `true`(대조).
2. **`Unmeasurable` 두 사유가 합쳐지지 않는다** — `UNTRAINED_SEGMENT`와
   `INSUFFICIENT_SAMPLES` testdata를 각각 파싱해 `reason` 비교 —
   양쪽 test에서 `!=`가 성립.
3. **release 불일치는 거부** — `Success.release`의 `release_id`를 다른 값으로 바꾼 사본에
   `releaseSatisfiesSelector`/`_release_satisfies_selector`(exact_release·
   latest_promoted 양쪽 selector)가 `false`. 원본은 `true`(대조).
4. **`fraction = "87.995"`(percent 관용, H-11)는 거부** — `isValidBidRateFraction`/
   `_is_valid_bid_rate_fraction`이 `false`. `"1.0000"`(경계, 포함)은 `true`,
   `"1.0001"`(경계 밖)은 `false`.
5. **`sample_size = 0`인 Success는 불변식 위반** — `Uncertainty.sample_size`를 0으로
   덮어쓴 사본에 `isAcceptableSuccess`/`_is_acceptable_success`가 `false`.
6. **`FeatureInputs`의 fact 하나라도 oneof 미설정이면 거부** — `base_amount`·
   `category_code`·`agency_id`·`base_amount_provenance_label` 네 fact 각각을 개별로
   지운 사본 4종 전부 `isAcceptableFeatureInputs`/`_is_acceptable_feature_inputs`가
   `false`(Kotlin 4건 + Python 4건, 8건 전부 실측).
7. **`OptimizationObjective`·`CandidateLabel`의 `UNSPECIFIED`·정의 밖 정수는 거부** —
   Kotlin은 `UNRECOGNIZED`(closed-ish 처리) 실측, Python은 proto3 open enum이라 정의
   밖 정수(99·77)가 파싱은 통과하되 알려진 값 집합(`.values()`/`.items()`) 밖임을
   실측 — 언어별 표현 차이를 각자의 test가 문서화한다.

## secret 스캔

- cmd: `git diff 55ecdcc..HEAD --name-only -- contracts/ ml-engine/ adapters/ config/ | xargs grep -lniE '(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))'`
- exit: 1(grep 매치 없음 = 정상)
- 핵심 결과: 변경 파일 전건 매치 0건.

## 하네스 레인 변경

- cmd: `git log --oneline 55ecdcc..HEAD -- CLAUDE.md .claude/`
- exit: 0
- 핵심 결과: 출력 없음 — 없음.

`55ecdcc..HEAD` range에는 이 slice 산출물 외에 세션 모델의 M3/M4/M5 준비 문서 리뷰 반영
커밋(`8cc7e19` — "docs(m3/m4/m5-prep): 리뷰 r1 반영")이 섞여 있다 — 이 slice와 무관한
병행 레인(다른 마일스톤 준비, 세션 모델 단독 저작)이며 in_scope 밖이다. 2B 자신의 커밋
5개: `cb5c612`(features.proto·prediction.proto) · `07af74f`(PredictionContractTest +
testdata) · `fd98ccf`(test_prediction_contract.py) · `2ce838b`(ktlint/detekt 정리).

## clean-tree 게이트(경로 개별 인자 + 양성 대조)

- cmd: `git status --porcelain contracts/proto/bidvector/ml/v1/prediction.proto contracts/proto/bidvector/ml/v1/features.proto contracts/testdata/prediction/calculate_optimal_bid_request.binpb contracts/testdata/prediction/calculate_optimal_bid_request.json contracts/testdata/prediction/calculate_optimal_bid_response_success.binpb contracts/testdata/prediction/calculate_optimal_bid_response_success.json contracts/testdata/prediction/calculate_optimal_bid_response_unmeasurable_untrained_segment.binpb contracts/testdata/prediction/calculate_optimal_bid_response_unmeasurable_untrained_segment.json contracts/testdata/prediction/calculate_optimal_bid_response_unmeasurable_insufficient_samples.binpb contracts/testdata/prediction/calculate_optimal_bid_response_unmeasurable_insufficient_samples.json contracts/testdata/prediction/calculate_optimal_bid_response_failure_unsupported_schema.binpb contracts/testdata/prediction/calculate_optimal_bid_response_failure_unsupported_schema.json contracts/testdata/prediction/get_model_metadata_response.binpb contracts/testdata/prediction/get_model_metadata_response.json adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt ml-engine/tests/test_prediction_contract.py config/quality/gate-tests.properties`
- exit: 0
- 핵심 결과: 빈 출력(전건 커밋됨). `reports/evidence/m2/2b/**` 자체는 이 시점에도 계속
  편집 중이라 목록에서 제외. `milestone-2.md`는 이 slice에서 편집하지 않았다(착수
  문단은 55ecdcc가 이미 기록).
- 양성 대조: `contracts/proto/bidvector/ml/v1/prediction.proto`에 한 줄 추가 →
  `git status --porcelain`이 `M`을 냄 → `git checkout -- <파일>`로 원복 → 다시 빈 출력.

## rollback 실측(임시 clone)

- cmd: 아래 `rollback.md`의 명령 그대로, 임시 clone에서 실행.
- exit: 0
- 핵심 결과: `contracts/proto/bidvector/ml/v1/prediction.proto`·`features.proto`·
  `contracts/testdata/prediction/`·`adapters/src/test/kotlin/.../PredictionContractTest.kt`·
  `ml-engine/tests/test_prediction_contract.py` 삭제, `config/quality/gate-tests.properties`
  는 2A 상태로 복원(`git diff <base>`가 빈 출력). 롤백 뒤 `(cd contracts && buf lint && buf build)`
  통과(2A 상태로 정상 복귀 — `service` 없는 계약).
