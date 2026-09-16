# M5/5E-2 — checklist.md

## M5 완료 조건 담당 근거

| 완료 조건(milestone-5.md) | 5E-2 근거 |
| --- | --- |
| model release/checksum/feature schema 응답 | `serving/runtime.py::build_derived_release`(D-2F-2 DERIVED 채움 규약) + `serving/wire.py::map_kernel_result`(release CopyFrom + schema echo) — `tests/serving/test_runtime.py`·`tests/serving/test_wire.py` |
| request validation | `serving/prediction.py::_validate`(닫힌 순서 ①⑴~⑸) — `tests/serving/test_prediction.py` 단계별 test |
| deadline/cancellation/status 매핑 | `context.is_active()` 확인(⑹, ADR 0010 D-2) — `test_step6_inactive_context_returns_without_computing` |
| Kotlin 소비자 규칙과의 정합 | `tests/serving/test_kotlin_rules_parity.py`(D-5E2-7) |
| 출하 정책으로 서빙 불가 사실의 회귀 보호 | `tests/app/test_server_prediction.py::test_shipped_policy_without_agency_sample_threshold_stays_not_ready`(S-12b, D-5D2-3) |

## D-5E2-1~10 이행

| ID | 이행 |
| --- | --- |
| D-5E2-1 | `serving/runtime.py::PredictionRuntime`(frozen) — 조립 근이 preload 성공 시 한 번 만든다(`app/server.py::_prediction_runtime`). `wire.py::_map_success`가 `release.CopyFrom(release)` + `feature_schema_version` 한 필드만 덮어써 promoted 와 같은 객체 계보를 유지한다(`test_release_satisfies_latest_promoted_selector`가 실측) |
| D-5E2-2 | `release_id = "distribution/" + policy.version` — proto 주석 규약을 따랐다(2F testdata 예시값과 다름, 운영자 확인 대상이었으나 계약 고정 문면 자체가 이미 「운영자 확인 — 대안 (b)」로 갈림길을 열어뒀고 이 구현은 (a) 원안을 취했다. 대안 (b)로 바꾸려면 `release_id` 접두만 유지한 채 값 재생성이면 되므로 되돌리기 쉽다) |
| D-5E2-3 | `code_version`은 `build_derived_release`의 호출자 인자 — `app/server.py::_prediction_runtime`이 `config.code_version`(env `ML_ENGINE_CODE_VERSION`, D-5E-7)을 넘긴다. 정적 `pyproject.version`은 쓰지 않는다 |
| D-5E2-4 | `runtime.py::derived_release_checksum` — `dataclasses.fields(policy)` 기계 열거 + canonical JSON(5C-2 `policy_checksum`과 같은 규칙). `test_checksum_changes_when_a_single_field_changes`가 `scenario_z`·`assessment_agency_sample_threshold`·`version` 세 필드 각각의 변경을 확인, `test_checksum_covers_tuple_fields`가 튜플 성분 변경도 확인(손 목록이 아님을 실측) |
| D-5E2-5 | `wire.py::_format_fraction = format(value, "f")` — `test_wire.py::test_format_fraction_round_trips_kotlin_normalized_forms`(4 case) + `test_kotlin_rules_parity.py::test_fraction_rules_accept_all_decimal_fields`(실 응답의 모든 decimal 필드) |
| D-5E2-6 | `wire.py::_check_invariants` — sample_size==0·후보수≠3·training_row_count≠0·비유한 Decimal 넷 다 `MappingRejected`. `prediction.py::CalculateOptimalBid`가 `RuntimeError`로 올린다(`test_step7_mapping_rejected_raises`) |
| D-5E2-7 | `test_kotlin_rules_parity.py` — 다섯 규칙 미러, 실 servicer 응답(2F testdata + SUPPORTED schema echo + latest_promoted) 대상 |
| D-5E2-8 | `runtime.py`의 `_DERIVED_FEATURE_SCHEMA_VERSION = next(iter(SUPPORTED_FEATURE_SCHEMAS))` — 그 앞에 `len(...) != 1` assertion 을 둬 지원 집합이 둘 이상이 되면 이 상수 선택이 조용히 잘못된 값을 고르는 대신 즉시 실패하게 했다(값 지어내기 금지의 방어적 구현) |
| D-5E2-9 | `prediction.py::_validate_selector` — `exact_release`는 `release_id`·`artifact_checksum` 둘 다 일치해야 통과(`test_step3b_exact_release_mismatch_is_unsupported_release`·`test_step3b_exact_release_matching_current_release_passes`) |
| D-5E2-10 | `readiness`는 gate 실물(`_READINESS_TO_WIRE`), `promoted`는 `Readiness.READY`이고 `runtime is not None`일 때만(`test_get_model_metadata_promoted_is_set_when_ready`·`..._unset_when_not_ready`) |

## 우회 (1)~(8) ↔ test 대응표

| # | 우회 | 대응 test |
| --- | --- | --- |
| (1) | selector 미설정 → `INVALID_REQUEST` | `test_step3a_unset_selector_is_invalid_request` |
| (2) | `exact_release`에 다른 checksum → `UNSUPPORTED_RELEASE` | `test_step3b_exact_release_mismatch_is_unsupported_release` |
| (3) | `feature_schema_version = "bidvector.ml.v1"` → `UNSUPPORTED_SCHEMA`(별칭 없음) | `test_step2_unsupported_schema_is_unsupported_schema` |
| (4) | NOT_READY 인데 promoted 를 읽어 `latest_promoted` 호출 | `test_get_model_metadata_promoted_is_unset_when_not_ready`(promoted 자체가 없다) + `test_step5_runtime_none_is_model_not_ready`(그 상태에서 호출하면 `MODEL_NOT_READY`) |
| (5) | 정책 값 하나 바뀐 재배포 → checksum 변경 → 옛 `exact_release`가 `RELEASE_MISMATCH` | `test_runtime.py::test_checksum_changes_when_a_single_field_changes` + `test_step3b_exact_release_mismatch_is_unsupported_release`(같은 기제) |
| (6) | 엔진이 `sample_size 0` Success 를 내는 변이 | `test_wire.py::test_sample_size_zero_success_is_mapping_rejected` + `test_prediction.py::test_step7_mapping_rejected_raises` |
| (7) | Decimal `1E-7` 형태 | `test_wire.py::test_format_fraction_round_trips_kotlin_normalized_forms["0.0000001"]` |
| (8) | `objective = UNSPECIFIED`(proto3 기본값) | `test_step4a_unspecified_objective_is_invalid_request` |

## (2b) 값 획득 축 — 실측(2026-09-16, commands.md 「(2b)」 절)

- `PredictionRuntime` 생성 — production 호출자 `app/server.py:201`(`_prediction_runtime`) 하나.
- `BidPredictionServicer(runtime=)` 주입 — production 호출자 `app/server.py:224`(`_build_servicers`) 하나.
- `serve_bid_rates` 계수 — production 호출자 `serving/prediction.py:227`(`CalculateOptimalBid`) 하나. test 는 `monkeypatch.setattr("ml_engine.serving.prediction.serve_bid_rates", ...)`로 대체해 호출 횟수를 센다(공개 표면 증가 0, `object` 커널 없음 — `serve_bid_rates`는 모듈 함수라 주입 자리 자체가 없다).
- `serving.wire.map_kernel_result`·`MappingRejected` — top-level `serving` 재수출에 없다(scope (2b) 표가 `.wire.` 접두로 이미 구분). `serving.prediction`만 호출한다.

## 새 public 표면과 그것이 밖에 허락하는 것

| 표면 | 허락하는 것 |
| --- | --- |
| `ml_engine.serving.PredictionRuntime` | 정책·release 스냅샷 읽기 전용 보유 — 생성은 조립 근만(실측), 필드 재대입 불가(frozen). `release`는 protobuf 메시지라 **참조를 얻으면 mutate 가능**하지만, 이 slice 의 모든 소비 지점(`GetModelMetadata.promoted`·`wire.map_kernel_result`)이 `CopyFrom`만 쓰고 원본을 반환하지 않아 실질적 변조 표면이 없다(설계 노트) |
| `ml_engine.serving.build_derived_release(policy, code_version)` | 정책에서 release 를 만드는 순수 함수 — 결과가 쓴 값을 나르지 않는다(호출은 조립 근 하나) |
| `ml_engine.serving.wire.map_kernel_result`(비재수출, `.wire.` 하위) | `KernelResult`를 wire 로 옮기는 순수 함수 — `serving.prediction`만 소비 |
| `ml_engine.serving.wire.MappingRejected` | 결과 타입(읽기 전용 데이터) |
| `BidPredictionServicer(gate, schemas, runtime)` | 생성자 세 번째 인자로 `PredictionRuntime | None` — 호출자 하나(`app/server.py`) |
| `status.ValidationDetailCode` 신설 5값 + `ReadinessDetailCode.SERVER_NOT_READY` | 어휘 읽기(닫힌 집합) |

**fix round 1(verifier r1·code-reviewer 수정) 추가 표면 — 없음.** `_validate`(3번째
인자 `gate` 추가)·`_compute_and_map`(R-M2 리팩터 신설)·`_first_candidate_rate_
out_of_range`(H-1 신설)는 전부 밑줄 접두 모듈 내부 함수이고 `serving/__init__.py`
는 이번 라운드에 변경이 없다(diff 로 확인, `commands.md` 「(2b)」 참고).

## 미달/과잉 판정(설계 검토 (3))

- **미달 후보였던 것**: `Readiness.LOADING`을 wire 에 내는 경로 — `ReadinessGate`는 `from_preload`에서 곧바로 READY/NOT_READY 만 내고 `LOADING` 상태를 생산하는 production 경로가 없다(gate 실물 확인, `readiness.py` 전수 읽음). `_READINESS_TO_WIRE`엔 `LOADING` 매핑 항목을 **뒀다**(값 지어내기가 아니라 존재하는 wire enum 값을 빠짐없이 다루기 위함) — 이 경로는 현재 시스템에서 도달 불가능하지만, gate 가 장차 `LOADING`을 내게 되면 매핑이 이미 있어 조용한 기본값(예: NOT_READY 로 잘못 접히는 것)을 막는다. 알려진 제한 1로 등재.
- **verifier r1 L-2 — 도달 불가 매핑이 하나 더 있었다**: `wire.py::_INTERVAL_SOURCE_TO_WIRE`의 `TIME_HOLDOUT_RESIDUAL`은 `ml-engine/src` 전역에 생산자가 0(실측, grep)이고, `CROSS_VALIDATION_RESIDUAL`은 `inference/predict.py`(GBM 경로)에만 있는데 `inference/engine.py`(이 slice 가 소비하는 유일 진입점)는 그 모듈을 import 하지 않는다(분포 단독, ADR 0001 D-6). `Readiness.LOADING`과 같은 처분 — 매핑은 남기되(존재하는 wire enum 값을 빠짐없이 다룬다) 도달 불가임을 여기 명시한다. `_SEGMENT_SUPPORT_TO_WIRE`·`_UNMEASURABLE_REASON_TO_WIRE`는 셋 다 생산자가 있어 대상이 아니다.
- **과잉 후보 확인**: `DistributionRelease.method`를 wire 에 싣지 않았다(축 없음, D-2F-5 확인) — `wire.py`가 `Success`에서 오는 값만 옮기고 `runtime.release`는 정책+code_version 에서만 온다. GBM/ARTIFACT 분기 코드는 두지 않았다(생산 경로 없음, `engine.py`가 분포 단독임을 재확인).

## 알려진 제한

1. **`Readiness.LOADING`이 wire 로 나가는 경로는 매핑만 있고 실측 test 가 없다** — 현재 `ReadinessGate`가 그 상태를 생산하지 않는다(구조적으로 도달 불가, 위 판정 참고). gate 가 `LOADING`을 생산하게 되면(예: 비동기 preload) 매핑 test 를 그 slice 에서 추가해야 한다.
2. **출하 `policy/inference-v1.yaml`로는 서빙이 되지 않는다** — `assessment.agency_sample_threshold` 미선언(D-5D2-3, `OPEN-5D2-POLICY-VALUES` 값 미정). `test_server_prediction.py`가 이 사실을 test 로 고정했다(알려진 제한이지 결함이 아니다 — 값 승인이 선행돼야 한다).
3. **`OPEN-5E2-FEATURE-SCHEMA-PARITY`(운영자 확인 대기)** — Kotlin `ML_CALL_POLICY.featureSchemaVersion = "bidvector.ml.v1"`이 Python `SUPPORTED_FEATURE_SCHEMAS = {"award-rate-features-v2"}`와 다르다. 실 배포에서 Kotlin 이 그 값을 그대로 보내면 이 slice 의 servicer 는 계약대로 `UNSUPPORTED_SCHEMA`를 낸다(방어하지 않는다고 scope 가 명시한 대로 — 옳은 동작이다, 그러나 배포가 그대로면 실서빙이 전부 실패한다).
4. **GBM/ARTIFACT release 서빙 경로 없음** — `OPEN-5C2-SERVING-PATH-PARITY`·`OPEN-5C2-UNLEARNED-GUARD`는 대상 부재로 이 slice 에서 처분하지 못한다(5D-2 운영자 결정 (b) 「분포 단독」 승계) — GBM 서빙 slice 가 생기면 그때 다시 연다.
5. **교차 언어 실서버 통합 부재** — `OPEN-5E2-CROSSLANG-REAL-SERVER`(6C). 이 slice 는 Kotlin 규칙의 Python 미러(`test_kotlin_rules_parity.py`)까지다.
6. **`derived_release_checksum`와 5C-2 `policy_checksum`이 같은 규칙을 각자 구현** — 공용 헬퍼로 뽑지 않았다(layers 계약상 `serving`이 `evaluation`을 import 할 수 없다, `reuse.md` 참고). 규칙이 갈리면(예: 한쪽만 고쳐지면) 두 checksum 계보가 어긋날 수 있다 — 다음에 이 규칙을 또 복제할 slice 가 생기면 `registry`나 `features`처럼 두 layer 아래 공용 자리를 만드는 것을 검토해야 한다.

## OPEN 처분

| OPEN | 처분 |
| --- | --- |
| `OPEN-5D2-RELEASE-FOR-DISTRIBUTION` | **종결** — `runtime.py::build_derived_release`가 `DistributionRelease`(엔진 내부 타입)를 소비하지 않고 정책+code_version 에서 독립적으로 wire `ModelRelease`를 만든다(D-5E2-1) |
| `OPEN-5D2-INTERVAL-SOURCE-WIRE` | **종결** — `IntervalSource.POSTERIOR_PREDICTIVE → INTERVAL_SOURCE_POSTERIOR_PREDICTIVE` 직접 매핑(proto 가 이미 그 값을 가짐, 2F 가 먼저 배포) |
| `OPEN-5D-DIAGNOSTICS-WIRE` | **종결** — `Diagnostics` wire 메시지가 이미 `shrinkage_weight`·`excluded_observations`를 가짐(2F additive), `_map_diagnostics`가 여섯 필드 전부 옮긴다 |
| `OPEN-5C2-SERVING-PATH-PARITY`·`OPEN-5C2-UNLEARNED-GUARD` | 대상 부재로 유지(알려진 제한 4) — GBM 서빙 slice 로 이월 |
| `OPEN-5E-YAML-LOADER-INFERENCE` | 유지(5D-3 뒤 `inference/policy.py` 후속, 이 slice out_of_scope) |
| `OPEN-5D2-POLICY-VALUES` | 유지(운영자 (c), 알려진 제한 2) |
| `OPEN-5E2-FEATURE-SCHEMA-PARITY`(신설) | 미해결 — 운영자 결정 대기(알려진 제한 3) |
| `OPEN-5E2-CROSSLANG-REAL-SERVER`(신설) | 미해결 — 6C 이월(알려진 제한 5) |
| `OPEN-5E2-CANDIDATE-RATE-UPPER`(신설, verifier r1 H-1) | 미해결 — 엔진 clamp 상한(출하 정책 `scenario.clamp_max = 1.4`)이 계약 축(`Candidate.bid_rate ≤ 1`, D-2B-8·D-2F-4)과 충돌할 수 있다. 이 slice 는 fail-closed(`MappingRejected`)까지만 하고 처분은 운영자 결정 대기 — 선택지 (a) 정책 `scenario.clamp_max`를 1 이하로 낮춘다(`policy/inference-v1.yaml`, out_of_scope — 5D-2/정책 값 소관) (b) 계약(`prediction.proto` `Candidate.bid_rate`)의 상한 자체를 넓힌다(2F 재개정, Kotlin `ParsedSuccessFields.toRateOrNull`도 함께 바꿔야 한다) (c) 상한 초과를 새 `Unmeasurable` 사유로 접는다(도메인 결과로 재분류 — 지금처럼 엔진 결함으로 볼지, 표현 가능한 결과로 볼지의 판단이 선행돼야 한다) |
| `OPEN-2C-FAILURE-CODES`·`OPEN-5C-REJECT-ACCOUNTING`·`OPEN-5E-*` 나머지 | 5E-2 무변경 |

## 계약 불일치

- scope.md acceptance_commands S-9 가 가리키는 `tools/check_python_version.py`가
  저장소에 존재하지 않는다(commands.md 참고). 값을 지어내거나 도구를 새로 만들지
  않고 N/A 로 기록, 팀장 보고.
