# M5/5D 정책 값 — `OPEN-5D-POLICY-VALUES`(**사용자 승인 2026-09-12, 착수 시**)

> **지위: 승인.** 전부 legacy-behavior(`ed4b06c` 모듈 상수·`app/core/config.py`), 5D 가 지어낸 수치 없음. 정본은 이 문서 —
> 실물은 `ml-engine/policy/inference-v1.yaml`(평탄 키, `PolicyScalar` 제약), 로더 `load_inference_policy` 가 `known_keys` 전수·값 불변식으로
> 대조하고 test 가 이 표와 YAML 을 대조한다. 값을 바꾸려면 이 문서 → YAML → test 순.

| 키(평탄) | 값 | legacy 좌표 |
| --- | --- | --- |
| `version` | `inference-v1` | — (`Candidate.weight_policy_version` 으로 응답에 실림) |
| `scenario.z` | `1.2816` | `app/ai/predictors/scenario_spec.py` `SCENARIO_INTERVAL_Z` |
| `scenario.conservative.weight` / `scenario.base.weight` / `scenario.aggressive.weight` | `0.24` / `0.52` / `0.24` | `CANDIDATE_SCENARIOS`(합 1 불변식) |
| `scenario.conservative.z_sign` / `scenario.base.z_sign` / `scenario.aggressive.z_sign` | `-1` / `0` / `1` | 같은 곳 |
| `scenario.clamp_min` / `scenario.clamp_max` | `0.7` / `1.4` | `app/ai/predictors/historical/statistics.py` `clamp_bid_rate` |
| `scenario.bid_rate_digits` | `4` | `_BID_RATE_DIGITS` |
| `assessment.agency_prior_strength` / `assessment.category_prior_strength` | `12.0` / `40.0` | `app/domain/assessment_shrinkage.py:45-46` — **사정률 축**(5B 낙찰률 축 κ 와 별도 선언, 재사용 금지) |
| `assessment.min_predictive_std` | `0.002` | `MIN_PREDICTIVE_STD` |
| `assessment.min_samples_for_variance` | `2` | `MIN_SAMPLES_FOR_VARIANCE` |
| `assessment.plausible_min` / `assessment.plausible_max` | `0.8` / `1.2` | `app/core/constants.py:414-415` |
| `reserve.draw_count` / `reserve.expected_price_count` | `4` / `15` | `app/domain/reserve_draw_distribution.py:33,36` |
| `reserve.min_reserve_records` | `8` | 5A 표 #28 |
| `bid_ratio.min_samples` | `3` | 5A 표 #29 |
| `bid_ratio.plausible_min` / `bid_ratio.plausible_max` | `0.5` / `1.5` | `app/ai/predictors/distribution_extraction.py:47-48` |
| `gbm.min_category_rows` | `40` | 5A 표 #31(하한 1 클램프는 코드 불변식 D-5D-3) |
| `maturity.window_days` | `7` | `app/domain/settlement_maturity.py:51` |

## 이 slice 가 값을 갖지 않는 것
합성 `confidence` 계수(이식 안 함) · seed·num_threads(5C reproducibility) · 5A 표의 guardrail·가격 15행(Kotlin) · embargo 임계(`OPEN-SET-06`, Kotlin).

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-12 착수 | 표 등재·승인 | 운영자 「승인」(D-5D-8 legacy 값) |
