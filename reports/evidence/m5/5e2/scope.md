# Slice 계약 — M5 / 5E-2 · `CalculateOptimalBid` wire 매핑 · `promoted` · READY 전환 — **착수 계약 2026-09-16(사용자 지시 「병합 진행하고 5E 착수해」 의 후반 · 전건 2F PR #16 병합 충족 · 운영자 확인 대기: D-5E2-2·3·4·8 + `OPEN-5E2-FEATURE-SCHEMA-PARITY`·`OPEN-5D2-POLICY-VALUES`)**

> **5E-1(PR #18, `4b9fa21`)이 남긴 자리 하나를 채운다(D-5E-0·D-5E-3)** — 5D-2 분포 엔진 `serve_bid_rates` 의 결과 타입(`Success | Unmeasurable`)을 v1+2F wire 로 옮기고, `GetModelMetadata.promoted` 를 같은 DERIVED release 로 채우며, `readiness` 를 gate 실물(LOADING/READY/NOT_READY)에 연결한다. 엔진·정책·계약 파일은 편집하지 않는다.
> 레인: worktree `bid-vector-v2-m5e`, 브랜치 `m5-5e2/2026-09-16`, base `4b9fa21`(PR #18 머지 = origin/main, 2F·5E-1 실물). 병행 레인 **5D-3**(`bid-vector-v2-m4e`/`m5-5d3/2026-09-16`, PR #17 OPEN·CI 초록) — 겹침: `milestone-5.md` 하나(문서). 5D-3 은 `inference/{distribution,observations}.py`·`features/facts.py` 를 바꾸지만 `serve_bid_rates` 시그니처와 `results.py` 는 무변경 목표(5D-3 scope) → 5E-2 매핑 표면은 5D-3 과 독립. **PR #17 이 먼저 병합되면 rebase(문서 hunk 만)**, 5E-2 가 먼저면 5D-3 이 rebase.

```yaml
milestone: M5
slice: 5e2-calculate-optimal-bid-wire-mapping
base_sha: 4b9fa2166549ce9e6af9c5f2b09325f11bbbbdcf
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - ml-engine/src/ml_engine/serving/prediction.py           # BidPredictionServicer — CalculateOptimalBid 오버라이드(검증 순서 ①~⑦ 아래) · GetModelMetadata(readiness=gate 실물 매핑, promoted=DERIVED release when READY) · 생성자에 PredictionRuntime | None 주입(조립 근이 만든다)
  - ml-engine/src/ml_engine/serving/runtime.py              # 신규 — PredictionRuntime(frozen: policy: InferencePolicy · release: prediction_pb2.ModelRelease · supported_feature_schema_versions) + build_derived_release(policy, code_version, feature_schema_version_echo 는 요청별) — D-5E2-1~4
  - ml-engine/src/ml_engine/serving/wire.py                 # 신규 — KernelResult → CalculateOptimalBidResponse 순수 매핑(Success/Unmeasurable), Decimal → 정규형 문자열(D-5E2-5), 매핑 불변식 위반은 결과가 아니라 MappingRejected(D-5E2-6)
  - ml-engine/src/ml_engine/serving/status.py               # 닫힌 어휘 재추가 — 이 slice 가 실제로 생산하는 것만: MODEL_RELEASE_SELECTOR_UNSPECIFIED · FEATURE_SCHEMA_VERSION_UNSUPPORTED · RELEASE_MISMATCH · OBJECTIVE_UNSPECIFIED · OBJECTIVE_UNSUPPORTED · SERVER_NOT_READY(5E-1 기존) — 각 값에 생산 test
  - ml-engine/src/ml_engine/serving/__init__.py             # 재수출
  - ml-engine/src/ml_engine/app/server.py                   # preload 성공 시 PredictionRuntime 조립(정책·code_version·release), 실패 시 None — 5E-1 D-5E-7 env 7 그대로(추가 env 없음)
  - ml-engine/tests/serving/test_prediction.py              # 5E-1 test 갱신(UNIMPLEMENTED 단언 → 실물) + 검증 순서 ①~⑦ 각 1 + READY/NOT_READY 각
  - ml-engine/tests/serving/test_grpc.py                    # 5E-1 test — `BidPredictionServicer` 생성자 확장의 부수 편집 1줄(인자 `None` 추가, 검증 로직 무변경). 계약 갱신 (2)
  - ml-engine/tests/serving/test_runtime.py                 # DERIVED release 채움 규약·checksum 결정성(같은 정책 두 번 = 같은 값, 값 하나 바꾸면 다른 값)
  - ml-engine/tests/serving/test_wire.py                    # Success/Unmeasurable 매핑 · 후보 3 순서 · Decimal 정규형 경계(D-5E2-5) · 매핑 불변식 위반(sample_size 0 등) → MappingRejected
  - ml-engine/tests/serving/test_kotlin_rules_parity.py     # Kotlin 소비자 규칙 미러(D-5E2-7): ReleaseShapeValidation(prefix·비공백 넷·dataset_id 공백은 DERIVED 만) · FractionRules(BigDecimal.toPlainString 동일성) · ParsedSuccessFields(sample_size>0·후보 3·origin RECOMMENDED) · ReleaseCheck(latest_promoted ⇒ response.release == promoted)
  - ml-engine/tests/app/test_server_prediction.py           # S-12b 자동화 — 실 socket: 완성 case 정책으로 부팅 → GetModelMetadata READY+promoted → CalculateOptimalBid(testdata 요청 + schema 를 SUPPORTED 값으로 + latest_promoted) → Success 와 promoted 일치 · 미완성 출하 정책으로 부팅 → NOT_READY + MODEL_NOT_READY
  - milestone-5.md                                          # 5E 절 5E-2 착수 문단
  - reports/evidence/m5/5e2/**
out_of_scope:
  - `contracts/**`(proto·testdata — 2F 종결, 승인 태그 `contracts/v1-approved-2026-09-16`) · `tools/contract-crosslang-smoke.sh`·`tests/crosslang_smoke_server.py`(D-5E-10 승계, fake 서버 무편집)
  - `ml_engine/inference/**`(5D-3 레인 진행 중 — `serve_bid_rates`·`results.py` 는 소비만) · `policy/inference-v1.yaml`(`assessment.agency_sample_threshold` 출하 값은 `OPEN-5D2-POLICY-VALUES`, 운영자 (c)) · 5C·5D 파일 · `training/**`·`evaluation/**`
  - Kotlin 소스(`adapters/**`·`workflow/**`) — `ML_CALL_POLICY.featureSchemaVersion` 불일치는 `OPEN-5E2-FEATURE-SCHEMA-PARITY` 로 운영자 결정, 이 slice 는 Python 쪽 사실만 고정
  - GBM 서빙 경로(`predict_bid_rates` + artifact 승격 registry) — 5D-2 운영자 결정 (b) 「분포 단독」 승계, `RELEASE_KIND_ARTIFACT` 생산 경로 없음(알려진 제한)
  - 임베딩 실물(`OPEN-5E-EMBEDDING-MODEL`) · job 영속(`OPEN-5E-JOB-PERSISTENCE`) · `OPEN-5E-YAML-LOADER-INFERENCE`(로더 자체 정정은 5D-3 뒤 `inference/policy.py` 후속) · `OPEN-5E-JOB-QUEUE-BOUND`
acceptance_commands:
  - "(cd ml-engine && uv sync --extra serving --extra dev)"                                                            # S-0
  - "(cd ml-engine && uv run python -c 'import ml_engine.serving.prediction, ml_engine.serving.wire, ml_engine.serving.runtime')"  # S-1c 승계
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                                           # S-2
  - "(cd ml-engine && uv run mypy)"                                                                                    # S-3
  - "(cd ml-engine && uv run lint-imports)"                                                                            # S-4
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                                               # S-5 — S-12b 자동화 test 포함
  - "(cd ml-engine && uv run python tools/design_ratchet.py)"                                                          # S-6
  - "(cd ml-engine && uv run python tools/reuse_provenance.py)"                                                        # S-7
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
  - "./gradlew --no-daemon check"                                                                                      # S-10 — evidence 커밋마다 그 HEAD 에서(leakPatternGate)
  - "(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)"   # S-11 승계
  - "./tools/contract-crosslang-smoke.sh"                                                                              # S-12 승계(fake 서버, 무편집 — 5E-2 가 계약 표면을 안 바꿨음을 증명)
rollback: |
  **정본 `reports/evidence/m5/5e2/rollback.md`**(구현 레인). 신규 `serving/{runtime,wire}.py`·`tests/serving/{test_runtime,test_wire,test_kotlin_rules_parity}.py`·`tests/app/test_server_prediction.py` 삭제 + `serving/{prediction,status,__init__}.py`·`app/server.py`·`tests/serving/test_prediction.py` 를 base 로 restore(`git restore --source=4b9fa21 --staged --worktree --`, 개별 인자) · `milestone-5.md` 는 hunk 격리(5D-3 이 같은 파일을 만진다 — `git diff <sha>~1..<sha> -- milestone-5.md | git apply -R`, 인접 삽입이면 수동 해소 절차 명기) · 이 scope.md 자기 이력은 착수 커밋 기준 단일 역적용 + 「하네스 레인 변경」 절 재등재. 임시 clone 실측 ①~⑥(⑥ 은 `./gradlew --no-daemon check` 포함). 되돌린 트리 = 5E-1 종결 상태(`CalculateOptimalBid` UNIMPLEMENTED, readiness 항상 NOT_READY).
```

작성: 2026-09-16, 세션 모델 단독. 근거: `milestone-5.md` 5E 「model release/checksum/feature schema 응답」·「request validation」·「deadline/cancellation/status mapping」 · 5E-1 scope D-5E-0·D-5E-3(5E-2 가 채운다)·⑩ status 규율 · 2F D-2F-2(DERIVED 채움 규약)·D-2F-3(`POSTERIOR_PREDICTIVE`)·D-2F-4(축 한정)·D-2F-6(UNSPECIFIED 수신 거부) · 5D-2 D-5D2-1 (b)(분포 단독)·D-5D2-3(임계 OPEN)·D-5D2-5(`DistributionRelease` 는 wire 매핑 없음 — 5E 가 옮긴다) · ADR 0010 D-2(deadline)·D-3(status 3분)·D-4(retry) · 2A ④⑤⑥ · 2B ③⑥⑧ · Kotlin 실물 `adapters/ml/{ReleaseShapeValidation,ReleaseCheck,ResponseMapping,ParsedSuccessFields,FractionRules}.kt`(소비자 규칙의 정본은 Kotlin 코드다 — 이 계약은 그것을 Python test 로 미러한다).

---

## 착수 조사(세션 모델 실측, 2026-09-16)

- **엔진 표면**: `inference/engine.py::serve_bid_rates(request: CalculateOptimalBidRequest, policy: InferencePolicy) -> Success | Unmeasurable`. `DistributionRequest.from_proto` 는 **features 만** 검증한다(`FEATURE_ABSENT/ROW_REJECTED`) — envelope(`feature_schema_version`·selector)·`objective`·`deadline_policy_version` 은 **아무도 안 본다**. 5E-2 servicer 가 본다(아래 순서).
- **`Success`** = `candidates`(정확히 3, 순서 고정 — `__post_init__` 강제)·`fitness`·`uncertainty`(`sample_size`·`dispersion`·`estimate_margin`·`interval_source`)·`diagnostics`(여섯) — **`release` 없음**. `DistributionRelease(policy_version, code_version, method)` 는 내부 타입이고 **생산자가 없다**(grep 0). 그러므로 wire `ModelRelease` 는 엔진 결과가 아니라 **런타임(정책 + code_version)에서** 만든다 — 요청마다 같은 값.
- **Kotlin 소비자(정본)**: `ReleaseShapeValidation` — DERIVED 는 넷 비공백 + `release_id` 접두 `distribution/`, `dataset_id` 공백 허용; ARTIFACT 는 다섯 비공백 + 접두 금지; UNSPECIFIED 거부. `ReleaseCheck` — `latest_promoted` 면 **응답 release == `GetModelMetadata.promoted`**(id·checksum 둘 다), `exact_release` 면 요청 값과 일치, selector 미설정은 거부. `FractionRules` — `BigDecimal(s).toPlainString() == s`. `ParsedSuccessFields` — `sample_size == 0` 거부, 후보 3·순서·origin RECOMMENDED.
- **2F testdata** `calculate_optimal_bid_response_success_posterior_predictive.json` 은 `releaseId: "distribution/reserve-draw-distribution-v1"`·`codeVersion: "ml-engine-0.1.0"` — **예시값이지 규약이 아니다**(proto 주석이 규약: `distribution/<inference policy version>`; Kotlin 은 접두만 본다). 5E-2 는 proto 주석을 따른다(D-5E2-2).
- **교차 언어 불일치 둘(운영자 결정 필요)**: (a) Kotlin `ML_CALL_POLICY.featureSchemaVersion = "bidvector.ml.v1"`(4D §3, 사용자 승인 2026-09-10 — 「별도 schema 버전 축이 없어 패키지 식별자를 값으로」, 알려진 제한으로 등재돼 있었다) vs Python `SUPPORTED_FEATURE_SCHEMAS = {"award-rate-features-v2"}`(5B D-5B-1) → 실 서버는 Kotlin 의 실요청에 `UNSUPPORTED_SCHEMA` 를 낸다. 대체·별칭은 금지(2A ⑥, `fixtures/manifest.yaml` 「가까운 version 으로 대체하지 않는다」) → `OPEN-5E2-FEATURE-SCHEMA-PARITY`. (b) 출하 `policy/inference-v1.yaml` 에 `assessment.agency_sample_threshold` 가 없다(D-5D2-3 — 값은 운영자 (c) 5C 재학습 지표 뒤) → 로더 `PolicyRejected` → 출하 정책으로는 **영원히 NOT_READY**. 5E-2 는 이 사실을 바꾸지 않는다(정책 파일 out_of_scope) — test 는 완성 case 정책을 주입한다(5D-2·5D-3 관례).
- **5E-1 이 걷어낸 어휘**: `PREDICTION_UNIMPLEMENTED`·`MODEL_RELEASE_SELECTOR_UNSPECIFIED`·`FEATURE_SCHEMA_VERSION_UNSUPPORTED`(verifier r1 M-8/L-5 — 선언만 있어 제거). 5E-2 는 **생산하는 것만** 다시 넣는다(각 값에 test).
- **code_version 출처 충돌**: proto 주석은 「`code_version` = ml-engine 패키지 version」, 5E-1 D-5E-7 은 env `ML_ENGINE_CODE_VERSION`(5C-1 `CodeVersion`, 배포가 준다). 정적 `0.1.0` 은 코드를 식별하지 못한다 → D-5E2-3.
- **checksum 의 「정규화된 정책 YAML bytes」**: 파일 bytes 는 주석·공백에 흔들린다. 5C-2 `evaluation/policy.py::policy_checksum` 이 이미 「로드된 값의 canonical JSON(키 정렬·`(",", ":")`·`allow_nan=False`) sha256」 규칙을 세웠다 → 같은 규칙(D-5E2-4). 5C-1 `spec_checksum`·5B `canonical_json` 과 동일 계보.

## 이 slice 가 하는 일

| # | 항목 | 근거 |
| --- | --- | --- |
| ① | **요청 검증 순서(닫힌 어휘, `INVALID_REQUEST` 는 retryable=false)** — ⑴ envelope base(`request_id`·`correlation_id` 비공백, 5E-1 `envelope_violation`) ⑵ `feature_schema_version ∈ SUPPORTED_FEATURE_SCHEMAS` 아니면 `ApplicationFailure(UNSUPPORTED_SCHEMA, detail=FEATURE_SCHEMA_VERSION_UNSUPPORTED)` ⑶ selector 미설정 → `INVALID_REQUEST(MODEL_RELEASE_SELECTOR_UNSPECIFIED)`; `exact_release` 가 현 DERIVED release(id·checksum)와 다르면 `UNSUPPORTED_RELEASE(RELEASE_MISMATCH)`; `latest_promoted` 통과 ⑷ `objective` UNSPECIFIED → `INVALID_REQUEST(OBJECTIVE_UNSPECIFIED)`, `SCENARIO_TRIPLE` 외 → `INVALID_REQUEST(OBJECTIVE_UNSUPPORTED)` ⑸ runtime 없음(gate NOT_READY/LOADING) → `MODEL_NOT_READY(SERVER_NOT_READY, retryable=true)` — 5E-1 `TrainingJobServicer` 와 같은 값 ⑹ `context.is_active()` 거짓 → 계산 없이 반환(D-2) ⑺ `serve_bid_rates` 호출. 검증이 미준비보다 **앞**(설계 검토 우회 (20) 승계 — 검증 결함이 `MODEL_NOT_READY` 에 가려지지 않는다). `deadline_policy_version` 은 검증하지 않는다(서버가 쓰지 않음, 2A ④ 밖) | 2A ④⑥ · 2B ③ · ADR 0010 D-2·D-3 · 5E-1 ⑩ |
| ② | **결과 매핑**(`serving/wire.py`, 순수) — `Success` → `response.success`: 후보 3(`label`→enum·`bid_rate.fraction`·`origin=RECOMMENDED`·`weight.fraction`·`weight_policy_version`)·`fitness.score`·`uncertainty`(넷, `interval_source` → enum)·`release`(runtime 의 DERIVED release, `feature_schema_version` 은 **요청 값 에코**)·`diagnostics`(여섯, `training_row_count` 는 엔진 값 그대로 — DERIVED 는 0 이어야 하며 0 이 아니면 MappingRejected). `Unmeasurable` → `response.unmeasurable{reason→enum, detail_code=detail.value}`. 매핑은 값을 지어내지 않는다 — 엔진이 안 준 필드는 없다(2F 넷은 5D-2 가 이미 `Diagnostics` 에 둠) | 2B ⑤⑥ · D-2F-2·3 · D-5D2-5 |
| ③ | **DERIVED release**(`serving/runtime.py::build_derived_release`) — `release_id = "distribution/" + policy.version`(= `inference-v1`) · `artifact_checksum = "sha256:" + sha256(canonical JSON of InferencePolicy 값)` · `feature_schema_version` 은 요청별 에코(runtime 의 release 템플릿에는 SUPPORTED 유일 키를 넣고 `GetModelMetadata.promoted` 에도 그 값) · `code_version = env ML_ENGINE_CODE_VERSION` · `dataset_id = ""` · `release_kind = DERIVED`. **응답 release 와 `promoted` 는 같은 함수의 같은 입력**(Kotlin `ReleaseCheck` latest_promoted 가 둘을 대조한다) | D-2F-2 · Kotlin `ReleaseShapeValidation`·`ReleaseCheck` |
| ④ | **`GetModelMetadata`** — `readiness` = gate 스냅샷 매핑(LOADING→`READINESS_LOADING`, READY→`READY`, NOT_READY→`NOT_READY`) · `promoted` 는 READY 일 때만 설정(NOT_READY 면 미설정 — 지어내지 않는다) · `supported_feature_schema_versions` 5E-1 그대로 | 5E-1 ③ · D-5E-3 해제 |
| ⑤ | **Decimal 정규형**(D-5E2-5) — `format(value, "f")`(지수 표기 없음, scale 보존, 앞 `0`), 음수 0 없음(엔진 clamp 가 양수 보장 — test 로 재확인), Kotlin `BigDecimal(s).toPlainString() == s` 를 Python 에서 미러(`Decimal(s)` 왕복 + 문자열 동일성 + `E` 부재) | Kotlin `FractionRules` · 2B ⑧ |
| ⑥ | **매핑 불변식은 결과가 아니라 거부**(D-5E2-6) — `sample_size == 0` 인 `Success`·후보 수≠3·`training_row_count≠0`·비유한 Decimal 은 엔진 결함이므로 `MappingRejected`(결과 타입) → servicer 는 이를 **삼키지 않고** `RuntimeError` 로 올린다(gRPC `INTERNAL`/`UNKNOWN`, 5E-1 ⑩ BLE 규율) — Unmeasurable 로 위장하지 않는다(값 지어내기 금지) | 2B ⑥ 불변식 · ADR 0001 D-6 |
| ⑦ | **Kotlin 규칙 미러 test**(D-5E2-7) — 위 표의 Kotlin 다섯 규칙을 Python test 로 옮겨 실 servicer 응답(testdata 요청 + SUPPORTED schema + 완성 case 정책)에 적용. 미러의 정본은 Kotlin 코드이며 test docstring 이 파일 이름으로 가리킨다(줄 번호 아님) | 교차 언어 실서버 통합은 `OPEN-5E2-CROSSLANG-REAL-SERVER`(6C) |
| ⑧ | **S-12b 자동화**(`tests/app/test_server_prediction.py`, 실 socket) — 5E-1 의 수동 S-12b 를 test 로: 완성 case 정책 넷으로 `run()` 기동 → READY·promoted → `CalculateOptimalBid` Success → `promoted == success.release` → 출하 정책(임계 없음)으로 기동 → NOT_READY·`MODEL_NOT_READY` — 두 경로가 test 하나에 같이 있어 「출하 정책으로는 서빙이 안 된다」가 **문서가 아니라 test 로** 남는다 | 5E-1 알려진 제한 9 해소 · D-5D2-3 |

## 계약 고정 결정

| ID | 결정 | 근거 | 지위 |
| --- | --- | --- | --- |
| **D-5E2-1** | `ModelRelease` 는 엔진 결과가 아니라 **런타임 상수**(정책 + code_version) — `PredictionRuntime`(frozen) 을 조립 근이 preload 성공 시 한 번 만들고 servicer 에 주입, 요청마다 `feature_schema_version` 만 에코 | `Success` 에 release 없음 · `DistributionRelease` 생산자 0 · Kotlin latest_promoted 대조 | 계약 고정 |
| **D-5E2-2** | `release_id = "distribution/<InferencePolicy.version>"` = `distribution/inference-v1`. 2F testdata 의 `distribution/reserve-draw-distribution-v1` 은 예시(Kotlin 은 접두만 검사) — `DistributionRelease.method` 는 wire 에 싣지 않는다(축 없음, D-5D2-5) | proto 주석(규약) · D-2F-2 | **운영자 확인** — 대안 (b) `distribution/<method>-<policy version>` |
| **D-5E2-3** | `code_version = env ML_ENGINE_CODE_VERSION`(D-5E-7 단일 출처, 학습 artifact `CodeVersion` 과 같은 값) — proto 주석 「패키지 version」 은 배포가 그 값을 넣으면 충족, 정적 `pyproject.version` 은 쓰지 않는다(코드를 식별 못 함) | D-5E-7 · 5C-1 `CodeVersion` | **운영자 확인** — 대안 (b) `f"ml-engine-{importlib.metadata.version('ml-engine')}"` |
| **D-5E2-4** | `artifact_checksum = "sha256:" + hexdigest(canonical JSON of InferencePolicy 전 필드, 키 정렬·`(",", ":")`·`allow_nan=False`)` — 5C-2 `policy_checksum` 과 같은 규칙, 파일 bytes 가 아니다(주석·공백 무관 = 「정규화」의 실체). 정책 값 하나가 바뀌면 checksum 이 바뀐다(test) | 5C-2 `evaluation/policy.py::policy_checksum` · D-2F-2 | **운영자 확인** — 대안 (b) 파일 bytes 의 YAML 재직렬화 sha256 |
| **D-5E2-5** | Decimal 정규형 `format(d, "f")` + Python 측 `toPlainString` 미러 검사 | Kotlin `FractionRules` | 계약 고정 |
| **D-5E2-6** | 매핑 불변식 위반 = `MappingRejected` → servicer 가 예외로 올림(INTERNAL). Unmeasurable/ApplicationFailure 로 위장 금지 | 2B ⑥ · 5E-1 ⑩ | 계약 고정 |
| **D-5E2-7** | Kotlin 소비자 규칙 다섯을 Python test 로 미러 — 정본은 Kotlin, 미러가 어긋나면 Kotlin 이 옳다(test 가 Kotlin 파일 이름을 가리킨다) | 교차 언어 실서버 통합 부재 | 계약 고정 |
| **D-5E2-8** | `supported_feature_schema_versions` 와 ⑵ 검증의 집합은 5B `SUPPORTED_FEATURE_SCHEMAS`(= `award-rate-features-v2`) — 분포 엔진이 5B 피처 벡터를 쓰지 않아도 **wire 의 `FeatureInputs` 해석 규약을 나르는 축**으로 유지(5E-1 ③ 승계). Kotlin 송신값 `bidvector.ml.v1` 은 거부된다 — 별칭 없음 | 2A ⑥ · 5B D-5B-1 · 4D §3 알려진 제한 | **운영자 확인** — `OPEN-5E2-FEATURE-SCHEMA-PARITY` 와 함께 |
| **D-5E2-9** | `exact_release` 는 현 DERIVED release 와 id·checksum 이 **둘 다** 같아야 통과, 아니면 `UNSUPPORTED_RELEASE(RELEASE_MISMATCH, retryable=false)` — 다른 release 를 찾아 주지 않는다(release 는 하나뿐) | 2B ③ · Kotlin `ReleaseCheck` | 계약 고정 |
| **D-5E2-10** | `readiness` 는 gate 실물. READY 조건 = 정책 넷 로드 성공(5E-1 preload) — 임베딩 미가용은 별 축(`GetEmbeddingMetadata` 가 나름) | 5E-1 ② · D-5E-2 | 계약 고정 |

## 위협 모델 — 5E-2 고유 경계

**방어한다**: (a) 값 지어내기 — release·diagnostics 어느 필드도 엔진·정책·env 밖에서 만들지 않는다(D-5E2-1·6) (b) 미검증 요청이 엔진에 닿기 — ⑴~⑷ 가 엔진 호출 앞 (c) `promoted` 와 응답 release 의 불일치 — 같은 함수·같은 입력(D-5E2-1) (d) 비정규 decimal 이 Kotlin 에서 거부돼 정직한 답이 버려지기 — D-5E2-5 미러 test (e) 엔진 결함(sample_size 0)이 Unmeasurable 로 위장되기 — D-5E2-6 (f) 미준비 서버가 판정을 내기 — ⑸ 는 gate 실물, promoted 는 READY 에만.
**방어하지 않는다**: Kotlin 이 다른 schema 값을 보내는 배포 불일치(`OPEN-5E2-FEATURE-SCHEMA-PARITY` — 계약대로 `UNSUPPORTED_SCHEMA` 를 내는 것이 옳은 동작) · 출하 정책의 임계 부재로 READY 가 안 되는 것(`OPEN-5D2-POLICY-VALUES`) · 엔진 내부 수치의 옳음(5D-2·5D-3 golden 소관) · 실 Kotlin↔실 Python 통합(`OPEN-5E2-CROSSLANG-REAL-SERVER`).

**우회 후보(≥5)**: (1) selector 미설정 요청 → ⑶ `INVALID_REQUEST` (2) `exact_release` 에 다른 checksum → ⑶ `UNSUPPORTED_RELEASE` (3) `feature_schema_version = "bidvector.ml.v1"` → ⑵ `UNSUPPORTED_SCHEMA`(별칭 없음) (4) NOT_READY 인데 `GetModelMetadata.promoted` 를 읽어 latest_promoted 로 호출 → promoted 미설정이라 Kotlin 이 먼저 거부, 서버는 ⑸ `MODEL_NOT_READY` (5) 정책 값 하나 바뀐 재배포 → checksum 이 바뀌어 옛 `exact_release` 가 `RELEASE_MISMATCH`(의도) (6) 엔진이 `sample_size 0` Success 를 내는 변이 → `MappingRejected` → INTERNAL, Unmeasurable 아님 (7) Decimal `1E-7` 형태 → `format` 이 `0.0000001`, 미러 test 가 지수 부재 확인 (8) `objective = UNSPECIFIED`(proto3 기본값 — Kotlin 이 안 채우면 이것) → ⑷ `INVALID_REQUEST`.

## (2b) 값 획득 축 (Python)

| 표면 | 밖에 허락하는 것 | 판정 |
| --- | --- | --- |
| `serving.PredictionRuntime`(frozen) | 정책·release 스냅샷 읽기 — 조립 근만 만든다, 조립 근 밖에서 만들면 위조 release 로 servicer 를 띄울 수 있으나 그 주체는 이미 servicer 자체를 띄울 수 있다(5E-1 (2b) 「경계로 처리」 와 동치) | 경계로 처리(실측: 호출자 `app/server.py` 하나) |
| `serving.build_derived_release(policy, code_version)` | 정책에서 release 를 만든다 — 결과가 쓴 값을 나르지 않음(순수) | 닫는다 |
| `serving.wire.map_kernel_result(result, release, schema_echo)` | 순수 매핑, 결과 타입 | 닫는다 |
| `serving.wire.MappingRejected` | 결과 타입(읽기만) | 닫는다 |
| `BidPredictionServicer(gate, schemas, runtime: PredictionRuntime | None)` | 생성자 인자 확장 — 호출자 `app/server.py` 하나 | 경계로 처리(실측) |
| `status.ValidationDetailCode` 재추가 5값 | 어휘 읽기 | 닫는다 |

「`object` 커널을 세야 할 때」: `serve_bid_rates` 는 모듈 함수 — servicer 가 직접 부른다(주입 자리 없음). test 는 `monkeypatch` 로 `ml_engine.serving.prediction.serve_bid_rates` 를 대체해 호출 횟수·미호출(검증 실패 시 0회)을 센다 — 공개 표면을 늘리지 않는다.

## 하네스 레인 변경(리뷰 요청 시점마다 갱신 — `git log --oneline 4b9fa21..HEAD -- CLAUDE.md .claude/`)

없음(착수 시점). 등재되는 커밋은 slice 산출물이 아니며 in_scope 밖, 운영자 승인 하에 같은 range 에 있다.

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| **`OPEN-5E2-FEATURE-SCHEMA-PARITY`**(신설) | Kotlin `ML_CALL_POLICY.featureSchemaVersion = "bidvector.ml.v1"` vs Python `award-rate-features-v2`. 운영자 결정: **(a) 추천** Kotlin 값을 `award-rate-features-v2` 로(4D `policy-values.md` §3 갱신 + `MlCallPolicyData.kt` 한 줄, Kotlin 레인/6C) · (b) Python 이 패키지 식별자를 schema 로 받기(5B D-5B-1 위반 — 비추천) |
| **`OPEN-5E2-CROSSLANG-REAL-SERVER`**(신설) | 실 Kotlin gateway ↔ 실 Python 서버 통합 test 는 6C(컨테이너) — 5E-2 는 Kotlin 규칙 미러(D-5E2-7)까지 |
| `OPEN-5D2-POLICY-VALUES` | `assessment.agency_sample_threshold` 출하 값 — 운영자 (c) 유지. **값이 없으면 출하 정책으로 READY 불가**(⑧ test 가 그 사실을 고정). 운영자 결정 필요: 잠정값 지정(5D-3 golden 011 case 값 참고) 또는 5C 재학습 지표까지 NOT_READY 유지 |
| `OPEN-5D2-RELEASE-FOR-DISTRIBUTION`·`OPEN-5D2-INTERVAL-SOURCE-WIRE`·`OPEN-5D-DIAGNOSTICS-WIRE` | **이 slice 로 종결**(Python 매핑 실물) |
| `OPEN-5C2-SERVING-PATH-PARITY`·`OPEN-5C2-UNLEARNED-GUARD` | 서빙 경로가 분포 단독(GBM 미서빙)이라 **대상 부재 — 5E-2 알려진 제한 등재, GBM 서빙 slice(있다면) 로 이월** |
| `OPEN-5E-YAML-LOADER-INFERENCE` | 유지(5D-3 뒤 `inference/policy.py` 후속) |
| `OPEN-2C-FAILURE-CODES`·`OPEN-5C-REJECT-ACCOUNTING`·`OPEN-5E-*` 나머지 | 5E-2 무변경 |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 착수 | 초판 — D-5E2-1~10, OPEN 신설 둘 | 사용자 「5E 착수」 후반 · 착수 조사 |
| 2026-09-16 (2) 구현 완료 뒤 | S-9 를 5E-1 과 같은 인라인 python 버전 대조 명령으로 정정(초판이 존재하지 않는 `tools/check_python_version.py` 를 가리킴 — 팀장 계약 오류) · in_scope 에 `tests/serving/test_grpc.py` 추가 | 구현 보고 — 도구 부재 실측 · 생성자 시그니처 변경의 필연적 collateral |
