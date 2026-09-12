# M5/5D — checklist.md

## D-5D-1~9 충족 근거

| ID | 결정 | 충족 근거 |
| --- | --- | --- |
| D-5D-1 | 커널 내부 float, 경계에서 Decimal 한 번(bid_rate scale=정책 자릿수, weight 는 quantize 없음) | `scenario.py::_quantize_bid_rate`(`Decimal(str(x)).quantize(...)`, ROUND_HALF_EVEN) · `weight=Decimal(str(x))`(quantize 없음) · `results.py::Candidate.__post_init__`가 둘 다 `Decimal` 타입을 강제 · `test_scenario.py::test_bid_rate_is_decimal_with_scale_preserved`·`test_weight_is_decimal_without_quantize` |
| D-5D-2 | `UnmeasurableReason` wire 3값 미러(추가 없음), 세분 사유는 `UnmeasurableDetail` | `results.py::UnmeasurableReason`(INSUFFICIENT_SAMPLES·UNTRAINED_SEGMENT·FEATURE_ABSENT 3값만) · `UnmeasurableDetail`(닫힌 StrEnum 6값) · 전 모듈이 `(reason, detail)` 조합으로 반환 |
| D-5D-3 | 미학습 가드 임계는 정책, 하한 1 클램프는 코드 불변식(`max(1, ·)`), 정책 0 을 줘도 1 | `predict.py::segment_availability`의 `max(1, policy.gbm_min_category_rows)` · `policy.py`는 0 을 거부하지 않고 음수만 거부(`test_policy.py::test_gbm_min_category_rows_zero_is_not_rejected_by_policy`·`test_gbm_min_category_rows_negative_is_rejected`) |
| D-5D-4 | seed·threads 는 5C 소유, manifest 로 받아 대조만 · predict 는 fake booster 구조 test | `registry/artifact.py::Reproducibility`(seed·num_threads·deterministic 셋 다 필수) · `_parse_reproducibility`가 타입만 검증(값 재현성 판단은 5C) · `test_predict.py`(fake booster NaN/inf/음수) · 실 LightGBM 결정성 test 는 `OPEN-5D-REAL-BOOSTER`(미착수, 알려진 제한) |
| D-5D-5 | K5 provenance 게이트 신설 — `CleanAssessmentSample`(admit_clean 유일 생성) 만 집계 함수가 받음 | `assessment.py::admit_clean`·`CleanAssessmentSample`·`aggregate_level_observation(samples: Sequence[CleanAssessmentSample])`(시그니처 자체가 비-clean 표본을 거부) · `test_assessment.py::test_admit_clean_only_passes_clean_provenance`·`test_admit_clean_excluded_count_is_not_silently_dropped` |
| D-5D-6 | ruff `select` 에 `BLE` 추가, 기존 코드 위반 0 | `pyproject.toml [tool.ruff.lint] select` 에 `"BLE"` · `uv run ruff check .` exit 0(위반 0, S-2) |
| D-5D-7 | `Diagnostics.shrinkage_weight`·`excluded_observations` 는 Python 결과 타입에만(wire 추가는 `OPEN-5D-DIAGNOSTICS-WIRE`) | `results.py::Diagnostics`(4필드, `training_row_count`·`segment_support`는 wire 2필드와 대응, 나머지 둘은 wire 에 없음) — proto 편집 없음(`contracts/proto/**` 무편집, in_scope 밖) |
| D-5D-8 | 정책 값(`inference-v1.yaml`) 전부 legacy-behavior, `policy-values.md` 표와 일치 | `policy/inference-v1.yaml` · `test_policy.py::test_shipped_policy_file_loads_successfully`가 표 값 전건 대조 |
| D-5D-9 | 정책 표 배정 정정(#31 은 5C·5D 공동 소비, `gbm.min_category_rows`) | `policy.py`가 `gbm.min_category_rows` 를 소비(`_POSITIVE_THRESHOLD_KEYS` 밖, D-5D-3 별도 취급) — 표 갱신 자체는 팀장 문서 레인(`reports/evidence/m5/5a/policy-values.md`) 소관, 이 slice 는 소비 코드만 |

## (2b) 값 획득 축 — 실측

| 표면 | 판정 | 실측 |
| --- | --- | --- |
| `KernelResult`(`Success`\|`Unmeasurable`)·`Candidate`·`Uncertainty`·`Diagnostics` frozen dataclass | 연다(직접 생성 — Python 가시성 한계) | `Candidate.__post_init__`이 `bid_rate>0`·타입은 강제하지만 생성 자체(정상 값으로)는 막지 못한다 — 5B `FeatureFacts`와 같은 한계, 알려진 제한 |
| `predict_bid_rates(...)` | 연다 — 유일 추론 진입점 | 시그니처가 `LoadedArtifact`(검증된)·`InferencePolicy`(검증된)만 받아 미검증 아티팩트/정책이 흘러들 경로가 없다(둘 다 각자의 `load_*`함수만 생성) |
| `load_artifact(raw, expected) -> LoadedArtifact \| ArtifactRejected` | 연다 — `LoadedArtifact`는 이 함수만 만든다 | `test_artifact.py` 5건(checksum·schema·feature_names·feature_manifest_checksum·sample_scope 변조) 전부 `ArtifactRejected`, 객체 미생성 실측 |
| `load_inference_policy(path) -> InferencePolicy \| PolicyRejected` | 연다 — 값 필수, 기본값 없음 | `test_policy.py` 18건(미지 키·타입 오류·불변식 위반 각각) |
| `admit_clean(...)` | 연다 — `CleanAssessmentSample` 유일 진입점(컨벤션, Python 한계) | `CleanAssessmentSample(1.0)` 직접 생성이 여전히 가능(우회 후보 (17), 알려진 제한) — `aggregate_level_observation`의 시그니처가 그 타입만 받으므로 **비-clean 표본은 admit_clean 을 거치지 않고는 집계에 닿을 수 없다**(값이 정수라서 통과하는 경로 없음, D-5D-5 핵심 실측) |
| `BoosterLike` Protocol | 연다 — test fake 주입 자리(고정 항목, 판정 함수는 주입 안 함) | `segment_availability`·`_predict_center`의 결과가 booster 출력을 그대로 나르지 않고 클램프·유한성 검사를 거친다(`test_predict.py::test_fake_booster_negative_output_is_clamped_not_rejected`·`test_fake_booster_non_finite_output_is_unmeasurable`) — 임의 float 이 응답에 직행하지 않음 실측 |
| `resolve_maturity`·`Observed`·`NoObservation` | `Observed.__post_init__`이 `opened_count>0` 강제 — **닫는다** | `test_maturity.py::test_observed_rejects_non_positive_opened_count` |

## 알려진 제한

1. **Python 가시성 한계** — `Candidate`·`Uncertainty`·`Diagnostics`·`CleanAssessmentSample` 등 frozen dataclass 의 직접 생성 자체는 타입 시스템이 막지 못한다(5B 와 같은 한계). 강제는 진입점 관례(`admit_clean`·`load_artifact`·`load_inference_policy`)와 test 뿐이다.
2. **`std == 0`은 통과한다**(설계 검토 (14), scope.md ⑥ 의도) — 후보 3 이 전부 같은 값으로 나는 것을 거부하지 않는다. `MIN_PREDICTIVE_STD`(K5)·`MIN_RESIDUAL_STD`(5C 학습)의 정책 바닥이 상류에서 `std==0` 자체를 사실상 막는다.
3. **golden test 는 skip 상태다** — `tests/inference/golden/test_kernel_golden.py`가 `fixtures/expected/ml-kernel-*` 부재로 명시 skip(`OPEN-5D-GOLDEN`, curator 병행 레인 대기).
4. **실 LightGBM booster 결정성 test 미작성** — `BoosterLike`는 fake 로만 검증했다. 실 booster 1건(스레드 수 무관 동일값)은 5C artifact 도착 후(`OPEN-5D-REAL-BOOSTER`).
5. **`ArtifactManifestV1`은 5C 의 writer 를 아직 인수하지 않았다** — 이 slice 는 read model 형태와 `load_artifact` 게이트만 고정했고, 5C 착수 계약이 이 형태로 write 하는지는 5C 쪽 verifier 대상이다.
6. **`predict.py`의 `Diagnostics.segment_support`는 항상 `DIRECT`다** — parent_category/global 폴백 세그먼트 판정은 5C·5E 인수(`_direct_diagnostics` 주석에 명시).
7. **`predict_bid_rates`의 `segment_rows`(가용성 게이트, 공종 학습 행 수)와 `sample_size`(§6.5 분산 임계, 그 추정을 뒷받침하는 표본 수)는 서로 다른 축의 별도 인자다** — legacy 는 이 둘을 각각 `category_training_rows`(가용성)·`agency_sample_count`(신뢰도)로 이미 분리해 썼다(digest §2). 두 값을 채우는 실제 파이프라인(구체적으로 무엇을 `sample_size`로 셀지 — agency 표본인지 다른 것인지)은 5E/서빙 오케스트레이션이 정한다 — 이 slice 는 그 구분이 있어야 한다는 계약(타입 시그니처)만 고정했다.
8. **K5 assessment·K6 reserve_draw·K7 maturity 는 `predict.py`(GBM 경로)에 배선되지 않았다** — legacy 에서도 이 셋은 별도 predictor(Phase 1 분포 엔진, `distribution.py`)가 소비하고 GBM(Phase 2, `award_rate_gbm.py`)은 소비하지 않는다(digest §2 확인). 이 slice 는 scope.md 가 요구한 대로 셋을 순수 커널 모듈로 이식했고(①), 각자 독립 test 로 검증했다 — 분포 엔진 자체의 오케스트레이션(legacy `distribution.py`의 `build_distribution_prediction` 상당)은 milestone-5.md 5D 다섯 항목에 명시되지 않았고 scope.md in_scope 파일 목록에도 그런 모듈이 없다(inference/ 아래 5개 파일 + predict.py 뿐). 필요해지면 별도 slice 로 다룬다.
9. **`Diagnostics.shrinkage_weight`는 `predict.py`(GBM) 경로에서 항상 `Decimal("0")`이다** — GBM 은 K5 계층 수축을 쓰지 않는다(#8 참조, legacy 도 그렇다). ML-04 「수축 가중치가 응답 근거에 실린다」는 K5 provenance 게이트(D-5D-5, `assessment.py`)가 값을 내는 자리이지, GBM 예측 경로가 아니다 — 분포 엔진 predictor 가 별도 slice 에서 조립되면 그때 실제 값이 흐른다.

## OPEN 갱신

- `OPEN-5D-POLICY-VALUES`: 착수 시 승인 값 그대로 출하(`inference-v1.yaml`) — 해소.
- `OPEN-5D-DIAGNOSTICS-WIRE`: 변경 없음(5E 전 M2 additive slice 2F 인수).
- `OPEN-5D-GOLDEN`: 변경 없음(curator 병행 레인, `ml-kernel-*` 도착 대기).
- `OPEN-5D-REAL-BOOSTER`: 변경 없음(5C artifact 도착 후).
- `OPEN-5B-OBSERVATION-DOMAIN`·`OPEN-5B-FEATURES-FORBIDDEN`: 수령 유지, 5D 는 `features` 소비만(무변경).
