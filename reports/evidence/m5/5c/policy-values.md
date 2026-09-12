# M5/5C-1 정책 값 — `OPEN-5C-POLICY-VALUES`(**승인 대기 2026-09-12, 착수 시**)

> **지위: 승인 대기.** 실물은 `ml-engine/policy/training-v1.yaml`(평탄 키, `PolicyScalar` 제약), 로더 `load_training_policy` 가 `known_keys` 전수·값 불변식으로
> 대조하고 test 가 이 표와 YAML 을 대조한다. 값을 바꾸려면 이 문서 → YAML → test 순. 하이퍼파라미터는 여기 없다 — D-5C-2 로 코드 선언 `TrainingSpec`(아래 §2, 변경은 새 version).

## §1 정책 값(YAML)

| 키(평탄) | 값 | 출처 | 층 |
| --- | --- | --- | --- |
| `version` | `training-v1` | — | — |
| `min_training_rows` | `500` | legacy `app/core/config.py` `PRICE_PREDICTION_AWARD_RATE_GBM_MIN_TRAINING_ROWS` 기본값 | **legacy-declared(미소비)** — 저장소 전체에서 선언 한 줄 외 참조 0(조사 01 §8-2). 「학습을 시작할 자격」이라는 주석의 의도만 있고 거는 코드가 없었다. 5D 표의 `legacy-behavior` 와 다른 층이라 따로 표기한다 |

κ 둘(`agency_prior_strength 12.0`·`category_prior_strength 40.0`, 낙찰률 축)은 새 키가 아니라 5B `SHIPPED_ENCODING_POLICY` 재사용(5B `policy-values.md` §1 승인 값) — 5D 의 사정률 축 κ 와 혼용하지 않는다.

## §2 `TrainingSpec` `award-rate-gbm-training-v1`(코드 선언, D-5C-2 — 값 무변경 이식, 승인은 「이식 값 확인」)

| 키 | 값 | legacy 좌표(`ed4b06c`) |
| --- | --- | --- |
| `objective` / `metric` | `regression` / `rmse` | `app/services/ml_training/award_rate_gbm.py` `LIGHTGBM_PARAMS` |
| `learning_rate` / `num_leaves` / `min_data_in_leaf` | `0.05` / `31` / `40` | 같은 곳 |
| `feature_fraction` / `bagging_fraction` / `bagging_freq` / `lambda_l2` | `0.9` / `0.9` / `1` / `1.0` | 같은 곳 |
| `verbosity` / `deterministic` / `force_row_wise` / `num_threads` | `-1` / `true` / `true` / `4` | 같은 곳 — `num_threads` 고정은 재현성 사유(같은 스레드 수 전제)와 자원 사유(한 호스트 공유)가 legacy 주석에 섞여 있다. 5C-1 은 **재현성 사유만** 승계하고 자원 사유는 spec 문서에 적지 않는다 |
| `num_boost_round` | `400` | `BOOSTING_ROUNDS` — early stopping 없음 |
| `encoding_folds` | `5` | `DEFAULT_ENCODING_FOLDS` |
| `seed` | `20260812` | `DEFAULT_TRAINING_SEED` |
| `min_residual_std` | `0.002` | `MIN_RESIDUAL_STD` — 5D `scenario` 가 `std == 0` 을 거부하지 않는 근거(5D 설계 검토 (14)) |

## §3 5A 표 대조(`reports/evidence/m5/5a/policy-values.md` — 이 slice 는 그 파일을 편집하지 않는다, `OPEN-5C-5A-TABLE-REASSIGN`)

| 5A # | 이름 | 5A 배정 | 실측(조사 01 §8-1) | 정정 제안 |
| --- | --- | --- | --- | --- |
| 8 | `BUSINESS_GROUP_CALIBRATION_ENABLED` | 5C(환경/토글) | `app/ai/guardrail_core.py` 만 — 업무 판정(guardrail) 축 | **Kotlin**(guardrail, M4 소관) |
| 31 | `PRICE_PREDICTION_AWARD_RATE_GBM_MIN_CATEGORY_ROWS` | 5C → 5D 「5C·5D」(D-5D-9) | 서빙 가용성 가드만 소비, 학습기는 읽지 않음 | **5D 만** |
| 32 | `PRICE_PREDICTION_BACKTEST_MIN_TRAINING_SAMPLES` | 5C | B 계보(`dataset_quality`·`comparison`) 소비 | **미이식**(D-5C-1) — 표에 「B 계보, 이식 없음」 |
| 33 | `PRICE_PREDICTION_BACKTEST_HOLDOUT_SIZE` | 5C | B 계보 소비 | **미이식**(D-5C-1) |
| (표 밖) | `PRICE_PREDICTION_AWARD_RATE_GBM_FEED_ORIGIN_ONLY` = `True` | 「5C 가 만나면 등재」 | A 계보 표본 정의(`award_rate_dataset.py`) — **dataset 생성 측** 값 | 5C-1 은 값을 소비하지 않고 `DatasetManifestV1.feed_origin_only` 로 **기록만**(ML-11.4 F2). 값의 소유는 dataset 생성(Kotlin/M6) |
| (표 밖) | `PRICE_PREDICTION_AWARD_RATE_GBM_MIN_TRAINING_ROWS` = `500` | 「5C 가 만나면 등재」 | 죽은 설정 | 위 §1 `min_training_rows` |
| (표 밖) | `GROUP_CALIBRATION_MIN_SAMPLES` = `100` | 「5C 가 만나면 등재」 | B 계보 승격 preflight | **미이식**(D-5C-1) |

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-12 착수 | 표 등재(승인 대기) | D-5C-7 · D-5C-2 |
| 2026-09-12 구현 확인 | `policy/training-v1.yaml`(§1)·`TrainingSpec` 등록표 `award-rate-gbm-training-v1`(§2, `spec.py`)가 이 표 값과 일치함을 `test_policy.py::test_shipped_policy_file_matches_policy_values_md`·`test_spec.py::test_shipped_spec_matches_policy_values_md`로 test 고정. §3 5A 표 대조는 값 변경 없음(정정 제안만, `OPEN-5C-5A-TABLE-REASSIGN`) | 구현 evidence — `reports/evidence/m5/5c/checklist.md` |
