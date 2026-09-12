# M5/5D — checklist.md

## verifier r1 반영(2026-09-12, 코드 커밋 `145847c`)

M-1(`LoadedArtifact`가 `_VerifiedBytes` 필수 인자)·M-2(정책 불변식 `clamp_min>0`·가중치
`≥0`·`min_predictive_std>0`)·M-4(`JsonScalar`→재귀 `JsonValue`, `warn_unreachable=true`)·
L-1(D-5D-10, `ROUND_HALF_UP`)·L-2(`NO_GLOBAL_SAMPLES` 신설)·L-3(registry 층 `residual_std`
비유한 즉시 거부) 전부 반영. M-3(golden 통합)은 이 라운드에서 제외(팀장 지시 — golden
corpus 승인·브랜치 병합 뒤 별도). L-4(S-5/S-8 마커 분리)는 commands.md 반영, 코드 변경
없음(마커는 이미 등록돼 있었다).

## verifier r2 반영(2026-09-12, 코드 커밋 `4ad2de7`)

**F-1(medium)** — M-2 의 `clamp_min>0` 검사가 quantize **전** 값만 봐서 `quantize(
clamp_min, bid_rate_digits) == 0`인 정책(재현: `digits 1`+`clamp_min 0.04`, `digits 4`+
`clamp_min 0.00001`)을 통과시켰다. `scenario.py`의 quantize 로직을 `rounding.py::
quantize_bid_rate`(단일 출처)로 뽑아 `policy.py`가 **같은** 규칙으로 `quantize(clamp_min,
bid_rate_digits) > 0`을 검사하도록 고쳤다 — 정책과 커널이 각자 quantize 를 구현해
반올림 규칙이 갈리는 사고를 원천 차단. **F-2(장부)** — 아래 (2b) 표·알려진 제한 갱신.
F-3·F-4(장부, scope.md acceptance_commands 문면·「TypedDict」 서술)는 팀장 계약 갱신
(`b9a4e4b`)이 이미 처리 — 이 문서는 코드 레인 몫(F-1·F-2)만 반영한다.

## golden 통합(M-3, 2026-09-12, 코드 커밋 `3a40bc3`)

승인·병합된 curator golden(`fixtures/manifest.yaml` domain: ml-kernel, `ml-kernel-001~014`,
병합 커밋 `5ef6eb8`)을 `tests/inference/golden/{_adapter.py,test_kernel_golden.py}`가
소비한다. 대응 규칙은 어댑터에만 있다 — production 코드에 golden 전용 표면 없음(정책
변환은 `dataclasses.replace`로 `InferencePolicy` 일부 필드만 override, 값은 case 가
선언한 synthetic 값 그대로). case 별 결과는 commands.md 참고.

**13/14 구현, `ml-kernel-011` 은 명시 skip**(`reason="OPEN-5D-DISTRIBUTION-ENGINE — 5D-2
가 소비"`) — GBM 예측 경로는 K5 를 쓰지 않아 `Diagnostics.shrinkage_weight`의 생산자가
없다(알려진 제한 8·9와 같은 갈래). 팀장 지시대로 부분 단언(예: `resolve_assessment_
posterior`만 별도 확인)은 하지 않았다 — case 의 `verified_paths`가 요구하는 것은
「응답에 실린다」는 사실 전체이고, 그 절반만 확인하고 초록을 내면 조용한 통과가 된다.

**골든 검증이 실제 동작 차이를 하나 발견했다** — `ml-kernel-008`(singular-and-non-finite-
input) 의 `degenerate-variance` probe(표본 5 개가 전부 같은 값, `draw_count=4`)는
`Unmeasurable(INSUFFICIENT_SAMPLES, DEGENERATE_VARIANCE)`를 요구하는데, 이식 당시 코드는
legacy 그대로 `(mean, 0.0)`을 조용히 냈다(legacy 는 이 축을 애초에 구별하지 않았다). 승인된
golden case 가 milestone-5.md 5D 항목이 이름 든 "singular input" 처리를 구체화한 것으로
읽어 `reserve_draw.py`에 이 검사를 추가했다(`n == draw_count`는 조합이 하나뿐이라 예외 —
legacy 조기 반환 그대로 계승). 신규 D-5D-11 로 등재.

## D-5D-1~11 충족 근거

| ID | 결정 | 충족 근거 |
| --- | --- | --- |
| D-5D-1 | 커널 내부 float, 경계에서 Decimal 한 번(bid_rate scale=정책 자릿수, weight 는 quantize 없음) | `rounding.py::quantize_bid_rate`(verifier r2 F-1 이후 `scenario.py`에서 분리된 단일 출처, `Decimal(str(x)).quantize(...)`, `ROUND_HALF_UP`) · `weight=Decimal(str(x))`(quantize 없음) · `results.py::Candidate.__post_init__`가 둘 다 `Decimal` 타입을 강제 · `test_scenario.py::test_bid_rate_is_decimal_with_scale_preserved`·`test_weight_is_decimal_without_quantize` |
| D-5D-2 | `UnmeasurableReason` wire 3값 미러(추가 없음), 세분 사유는 `UnmeasurableDetail`(verifier r1 L-2 이후 7값 — `NO_GLOBAL_SAMPLES` 신설) | `results.py::UnmeasurableReason`(3값만) · `UnmeasurableDetail`(닫힌 StrEnum, K5 global 표본 0 은 `NO_GLOBAL_SAMPLES` — K6 추첨 축 `TOO_FEW_DRAWS`와 분리) · 전 모듈이 `(reason, detail)` 조합으로 반환 · `test_assessment.py::test_global_level_sample_count_below_one_is_unmeasurable` |
| D-5D-3 | 미학습 가드 임계는 정책, 하한 1 클램프는 코드 불변식(`max(1, ·)`), 정책 0 을 줘도 1 | `predict.py::segment_availability`의 `max(1, policy.gbm_min_category_rows)` · `policy.py`는 0 을 거부하지 않고 음수만 거부(`test_policy.py::test_gbm_min_category_rows_zero_is_not_rejected_by_policy`·`test_gbm_min_category_rows_negative_is_rejected`) |
| D-5D-4 | seed·threads 는 5C 소유, manifest 로 받아 대조만 · predict 는 fake booster 구조 test · manifest 수치 필드는 유한값만(verifier r1 L-3) | `registry/artifact.py::Reproducibility`(seed·num_threads·deterministic 셋 다 필수) · `_parse_reproducibility`가 타입만 검증(값 재현성 판단은 5C) · `_parse_scalars`가 `residual_std` 비유한(`NaN`/`Inf`) 즉시 거부 · `test_predict.py`(fake booster NaN/inf/음수) · `test_artifact.py::test_non_finite_residual_std_is_rejected` · 실 LightGBM 결정성 test 는 `OPEN-5D-REAL-BOOSTER`(미착수, 알려진 제한) |
| D-5D-5 | K5 provenance 게이트 신설 — `CleanAssessmentSample`(admit_clean 유일 생성) 만 집계 함수가 받음 | `assessment.py::admit_clean`·`CleanAssessmentSample`·`aggregate_level_observation(samples: Sequence[CleanAssessmentSample])`(시그니처 자체가 비-clean 표본을 거부) · `test_assessment.py::test_admit_clean_only_passes_clean_provenance`·`test_admit_clean_excluded_count_is_not_silently_dropped` |
| D-5D-6 | ruff `select` 에 `BLE` 추가, 기존 코드 위반 0 | `pyproject.toml [tool.ruff.lint] select` 에 `"BLE"` · `uv run ruff check .` exit 0(위반 0, S-2) |
| D-5D-7 | `Diagnostics.shrinkage_weight`·`excluded_observations` 는 Python 결과 타입에만(wire 추가는 `OPEN-5D-DIAGNOSTICS-WIRE`) | `results.py::Diagnostics`(4필드, `training_row_count`·`segment_support`는 wire 2필드와 대응, 나머지 둘은 wire 에 없음) — proto 편집 없음(`contracts/proto/**` 무편집, in_scope 밖) |
| D-5D-8 | 정책 값(`inference-v1.yaml`) 전부 legacy-behavior, `policy-values.md` 표와 일치 · verifier r1 M-2 이후 불변식 보강(`clamp_min>0`·가중치 각각 `≥0`·`min_predictive_std>0`) · verifier r2 F-1 이후 `quantize(clamp_min, bid_rate_digits)>0` 추가 | `policy/inference-v1.yaml` · `test_policy.py::test_shipped_policy_file_loads_successfully`가 표 값 전건 대조 · `test_clamp_min_non_positive_is_rejected`·`test_negative_weight_is_rejected_even_when_sum_is_one`·`test_min_predictive_std_non_positive_is_rejected`·`test_clamp_min_quantizes_to_zero_is_rejected_digits_1`·`test_clamp_min_quantizes_to_zero_is_rejected_digits_4`·`test_shipped_clamp_min_survives_quantize_check`(회귀 없음) |
| D-5D-9 | 정책 표 배정 정정(#31 은 5C·5D 공동 소비, `gbm.min_category_rows`) | `policy.py`가 `gbm.min_category_rows` 를 소비(`_POSITIVE_THRESHOLD_KEYS` 밖, D-5D-3 별도 취급) — 표 갱신 자체는 팀장 문서 레인(`reports/evidence/m5/5a/policy-values.md`) 소관, 이 slice 는 소비 코드만 |
| D-5D-10(신설, verifier r1 L-1) | 경계 `Decimal` quantize 는 `ROUND_HALF_UP`(scale 4) — legacy `round()`(Python 기본 half-even) 와 갈리는 예는 의도된 갈림, legacy 출력은 정답이 아니다. verifier r2 F-1 이후 이 규칙이 `rounding.py`(단일 출처)로 분리돼 `scenario.py`와 `policy.py`가 공유 | `rounding.py::quantize_bid_rate`(verifier r1 판은 `scenario.py::_quantize_bid_rate`였고 F-1 수정으로 분리) · `test_scenario.py::test_quantize_rounds_half_up_not_half_even`(`Decimal("0.87465")` 동점에서 `ROUND_HALF_UP`→`0.8747`, `ROUND_HALF_EVEN`→`0.8746` 직접 대조) |
| D-5D-11(신설, golden M-3, `ml-kernel-008`) | K6 추첨 커널은 singular 입력(표본 전부 같은 값, 모분산 0)을 `n == draw_count`가 아닌 한 `Unmeasurable(INSUFFICIENT_SAMPLES, DEGENERATE_VARIANCE)`로 거부한다 — legacy 처럼 `(mean, 0.0)`을 조용히 내지 않는다 | `reserve_draw.py::_admitted_values`(`_validated_values` 뒤 `pvariance(validated) == 0.0 and len(validated) != draw_count` 검사) · `test_reserve_draw.py::test_all_same_values_is_degenerate_variance_when_n_not_equal_k`·`test_all_same_values_with_n_equals_k_is_not_degenerate` · golden `test_ml_kernel_008_reserve_draw_singular_inputs_never_fold_or_raise` |

## (2b) 값 획득 축 — 실측

| 표면 | 판정 | 실측 |
| --- | --- | --- |
| `KernelResult`(`Success`\|`Unmeasurable`)·`Candidate`·`Uncertainty`·`Diagnostics` frozen dataclass | 연다(직접 생성 — Python 가시성 한계) | `Candidate.__post_init__`이 `bid_rate>0`·타입은 강제하지만 생성 자체(정상 값으로)는 막지 못한다 — 5B `FeatureFacts`와 같은 한계, 알려진 제한 |
| `predict_bid_rates(...)` | 연다 — 유일 추론 진입점 | 시그니처가 `LoadedArtifact`(검증된)·`InferencePolicy`(검증된)만 받아 미검증 아티팩트/정책이 흘러들 경로가 없다(둘 다 각자의 `load_*`함수만 생성) |
| `load_artifact(raw, expected) -> LoadedArtifact \| ArtifactRejected` | **타입으로 닫되 완전하지 않다**(verifier r2 F-2 정정 — 이전 판 「닫는다」는 과장이었다) — `LoadedArtifact`는 `manifest`·`_VerifiedBytes` 둘 다 필수 인자라 정상 API 표면(공개 함수만 쓰는 호출부)에서는 `load_artifact`만이 유일한 생성 경로다. 그러나 `_VerifiedBytes`가 모듈 `_` 접두 관례일 뿐이라, 모듈 밖에서 `from ml_engine.registry.artifact import _VerifiedBytes`로 직접 import 해 `LoadedArtifact(m, _VerifiedBytes(b"x"))`처럼 2줄로 넘기면 **mypy clean·런타임 성공**한다(Python 가시성 한계, 알려진 제한 12 — `InferencePolicy` 잔여(제한 10)와 같은 갈래) | `test_artifact.py` 13건(성공 1 + 변조 11: checksum·schema·feature_names·feature_manifest_checksum·sample_scope 2종·malformed json·missing reproducibility·비유한 residual_std 3종 — 전부 `ArtifactRejected`, 객체 미생성) · `test_loaded_artifact_requires_verified_bytes_argument`(런타임 `TypeError`로 인자 누락 확인 — 이 test 는 「1 인자 생성이 막힌다」만 확인하고 「모듈 밖 `_VerifiedBytes` 우회」는 검증하지 않는다, verifier r2 실측) |
| `load_inference_policy(path) -> InferencePolicy \| PolicyRejected` | 연다(직접 `InferencePolicy(...)` 생성은 Python 한계, 알려진 제한 10) — 로더 경로는 값 필수·불변식 검증(verifier r1 M-2 보강: `clamp_min>0`·가중치 `≥0`·`min_predictive_std>0` · verifier r2 F-1 보강: `quantize(clamp_min, bid_rate_digits)>0`) | `test_policy.py` 27건(미지 키·타입 오류·불변식 위반 각각, M-2 신규 5건 + F-1 신규 3건 포함) |
| `admit_clean(...)` | 연다 — `CleanAssessmentSample` 유일 진입점(컨벤션, Python 한계) | `CleanAssessmentSample(1.0)` 직접 생성이 여전히 가능(우회 후보 (17), 알려진 제한) — `aggregate_level_observation`의 시그니처가 그 타입만 받으므로 **비-clean 표본은 admit_clean 을 거치지 않고는 집계에 닿을 수 없다**(값이 정수라서 통과하는 경로 없음, D-5D-5 핵심 실측) |
| `BoosterLike` Protocol | 연다 — test fake 주입 자리(고정 항목, 판정 함수는 주입 안 함) | `segment_availability`·`_predict_center`의 결과가 booster 출력을 그대로 나르지 않고 클램프·유한성 검사를 거친다(`test_predict.py::test_fake_booster_negative_output_is_clamped_not_rejected`·`test_fake_booster_non_finite_output_is_unmeasurable`) — 임의 float 이 응답에 직행하지 않음 실측 |
| `resolve_maturity`·`Observed`·`NoObservation` | `Observed.__post_init__`이 `opened_count>0` 강제 — **닫는다** | `test_maturity.py::test_observed_rejects_non_positive_opened_count` |

## 알려진 제한

1. **Python 가시성 한계** — `Candidate`·`Uncertainty`·`Diagnostics`·`CleanAssessmentSample` 등 frozen dataclass 의 직접 생성 자체는 타입 시스템이 막지 못한다(5B 와 같은 한계). 강제는 진입점 관례(`admit_clean`·`load_artifact`·`load_inference_policy`)와 test 뿐이다.
2. **`std == 0`은 통과한다**(설계 검토 (14), scope.md ⑥ 의도) — 후보 3 이 전부 같은 값으로 나는 것을 거부하지 않는다. `MIN_PREDICTIVE_STD`(K5)·`MIN_RESIDUAL_STD`(5C 학습)의 정책 바닥이 상류에서 `std==0` 자체를 사실상 막는다.
3. **golden test 는 13/14 만 구현됐다**(M-3 반영, 2026-09-12) — `ml-kernel-011`만 명시 skip(`OPEN-5D-DISTRIBUTION-ENGINE`, 알려진 제한 8·9). 나머지 13 건은 `tests/inference/golden/test_kernel_golden.py`가 실제로 검증한다.
4. **실 LightGBM booster 결정성 test 미작성** — `BoosterLike`는 fake 로만 검증했다. 실 booster 1건(스레드 수 무관 동일값)은 5C artifact 도착 후(`OPEN-5D-REAL-BOOSTER`).
5. **`ArtifactManifestV1`은 5C 의 writer 를 아직 인수하지 않았다** — 이 slice 는 read model 형태와 `load_artifact` 게이트만 고정했고, 5C 착수 계약이 이 형태로 write 하는지는 5C 쪽 verifier 대상이다.
6. **`predict.py`의 `Diagnostics.segment_support`는 항상 `DIRECT`다** — parent_category/global 폴백 세그먼트 판정은 5C·5E 인수(`_direct_diagnostics` 주석에 명시).
7. **`predict_bid_rates`의 `segment_rows`(가용성 게이트, 공종 학습 행 수)와 `sample_size`(§6.5 분산 임계, 그 추정을 뒷받침하는 표본 수)는 서로 다른 축의 별도 인자다** — legacy 는 이 둘을 각각 `category_training_rows`(가용성)·`agency_sample_count`(신뢰도)로 이미 분리해 썼다(digest §2). 두 값을 채우는 실제 파이프라인(구체적으로 무엇을 `sample_size`로 셀지 — agency 표본인지 다른 것인지)은 5E/서빙 오케스트레이션이 정한다 — 이 slice 는 그 구분이 있어야 한다는 계약(타입 시그니처)만 고정했다.
8. **K5 assessment·K6 reserve_draw·K7 maturity 는 `predict.py`(GBM 경로)에 배선되지 않았다** — legacy 에서도 이 셋은 별도 predictor(Phase 1 분포 엔진, `distribution.py`)가 소비하고 GBM(Phase 2, `award_rate_gbm.py`)은 소비하지 않는다(digest §2 확인). 이 slice 는 scope.md 가 요구한 대로 셋을 순수 커널 모듈로 이식했고(①), 각자 독립 test 로 검증했다 — 분포 엔진 자체의 오케스트레이션(legacy `distribution.py`의 `build_distribution_prediction` 상당)은 milestone-5.md 5D 다섯 항목에 명시되지 않았고 scope.md in_scope 파일 목록에도 그런 모듈이 없다(inference/ 아래 5개 파일 + predict.py 뿐). 필요해지면 별도 slice 로 다룬다.
9. **`Diagnostics.shrinkage_weight`는 `predict.py`(GBM) 경로에서 항상 `Decimal("0")`이다** — GBM 은 K5 계층 수축을 쓰지 않는다(#8 참조, legacy 도 그렇다). ML-04 「수축 가중치가 응답 근거에 실린다」는 K5 provenance 게이트(D-5D-5, `assessment.py`)가 값을 내는 자리이지, GBM 예측 경로가 아니다 — 분포 엔진 predictor 가 별도 slice 에서 조립되면 그때 실제 값이 흐른다.
10. **`InferencePolicy` 직접 생성은 로더 불변식을 우회한다**(Python 가시성 한계, #1 과 같은 갈래 — verifier r1 M-2 반영 후에도 남는 잔여) — `load_inference_policy`의 M-2 보강(`clamp_min>0`·가중치 `≥0`·`min_predictive_std>0` 등)은 **그 함수를 통과하는 경로**만 막는다. `InferencePolicy(scenario_clamp_min=Decimal("-1"), ...)`처럼 필드를 직접 채워 넣으면 검증을 거치지 않은 객체가 여전히 만들어진다 — 강제는 「YAML 로 정책을 배포한다」는 운영 관례와 test 뿐이다.
11. **(해소, M-3 통합 2026-09-12)** ~~golden test skip 기준이 verified_paths 미단언~~ — 13/14 case 가 case 별 `verified_paths`를 실제로 단언하는 test 로 교체됐다. `ml-kernel-011` 만 명시 skip(제한 8·9와 같은 사유) — 그 case 는 대응 불가로 남는다.
12. **`LoadedArtifact`도 `_VerifiedBytes` 모듈 밖 import 로 우회된다**(Python 가시성 한계, #1·#10 과 같은 갈래 — verifier r2 F-2) — `load_artifact(raw, expected)`만이 **정상 공개 API 경로**로는 유일한 생성 지점이다(`test_loaded_artifact_requires_verified_bytes_argument`가 1 인자 생성 실패를 확인). 그러나 `_VerifiedBytes`는 단일 밑줄 관례일 뿐이라, `from ml_engine.registry.artifact import _VerifiedBytes`로 직접 import 해 `LoadedArtifact(manifest, _VerifiedBytes(b"anything"))`처럼 2줄을 쓰면 checksum 검증 없이도 mypy clean·런타임 성공한다(verifier r2 실측). 강제는 「비공개 이름을 import 하지 않는다」는 관례와 코드 리뷰뿐이다.
13. **`ml-kernel-005` golden test 가 `_verify_checksum`·`_VerifiedBytes`(모듈 private)를 직접 import 한다** — curator 의 합성 아티팩트 바이트가 `ArtifactManifestV1` 전체 스키마(모든 필수 필드)를 만족하지 않아(case `not_covered`가 명시), 공개 `load_artifact()`를 그대로 호출하면 checksum 통과 여부와 무관하게 매니페스트 파싱 단계에서 항상 거부돼 case 가 겨누는 「checksum 게이트 단계」자체를 검증할 수 없다. white-box test 로 판단해 private 이름을 직접 가져왔다 — production 표면 확장은 아니다(#12 의 우회 통로가 test 안에서 의도적으로 쓰인 유일한 자리).
14. **leak 스캔이 "token" 오탐 11건을 낸다** — `_adapter.py`/`test_kernel_golden.py`의 `sign_token_to_int`·`provenance_from_token`·`_reserve_price_token` 등은 curator golden case 가 부호·provenance 를 나르는 문자열 리터럴("NEGATIVE"/"CLEAN" 등)을 가리키는 도메인 용어이지 비밀값이 아니다(`config/quality/leak-patterns.txt`의 bare `token` 패턴이 "sign token"/"provenance token"까지 잡는다). 값 자체(git diff)를 확인해 실제 비밀·자격증명이 없음을 확인했다.

## OPEN 갱신

- `OPEN-5D-POLICY-VALUES`: 착수 시 승인 값 그대로 출하(`inference-v1.yaml`) — 해소.
- `OPEN-5D-DIAGNOSTICS-WIRE`: 변경 없음(5E 전 M2 additive slice 2F 인수).
- `OPEN-5D-GOLDEN`: **13/14 해소**(M-3 통합, 2026-09-12) — `ml-kernel-001~010`·`012~014`를 golden test 가 실제로 소비한다. `ml-kernel-011`은 `OPEN-5D-DISTRIBUTION-ENGINE`으로 이관(그 slice 가 해소해야 완전히 닫힌다).
- `OPEN-5D-REAL-BOOSTER`: 변경 없음(5C artifact 도착 후).
- `OPEN-5D-DISTRIBUTION-ENGINE`(신설, 팀장 범위 판정 2026-09-12): K5·K6·K7 → 후보 3 을 잇는 분포 엔진 조립(legacy `distribution.py`)은 5D in_scope 에 없다 — ML-04 둘째 acceptance(수축 가중치가 응답 근거에)·golden 011(명시 skip, M-3 반영)·`segment_support` 폴백은 그 조립 slice 가 만족시킨다(알려진 제한 8·9·6). 처분은 운영자 결정 대기.
- `OPEN-5B-OBSERVATION-DOMAIN`·`OPEN-5B-FEATURES-FORBIDDEN`: 수령 유지, 5D 는 `features` 소비만(무변경).
