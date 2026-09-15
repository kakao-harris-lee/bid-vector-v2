# M5/5A 정책 값 — `OPEN-ML-05` 1차 분류 표 (D-M5-6, 운영자 승인 2026-09-11)

> **지위: 분류 표 승인(2026-09-11, 운영자 「추천안 대로 진행」 — D-M5-6 (a)).** 이 slice 는 **분류와
> 로드 자리**(`registry/policy.py` 가 versioned YAML 을 읽는 형태)만 만든다. **값 자체는 5A 산출물이
> 아니다** — 5C·5D 가 커널을 이식할 때 이 표의 「정책」 행을 YAML 로 옮기고 그 slice 의 policy-values 로
> 승인받는다. 「미분류」 넷은 이식 slice 가 근거를 찾아 재판정한다(`data-dictionary.md` §4.3.1).
> 출처: `_workspace/m5-prep/01_scout_ml_package.md` §(b) b-1(legacy `app/core/config.py`, commit `ed4b06c`).

| # | 이름 | legacy 기본값 | 분류 | 소비 예정 |
|---|---|---|---|---|
| 1 | `PREDICTION_DEFAULT_MINIMUM_BID_RATE` | `0.0` | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 2 | `PREDICTION_CATEGORY_MINIMUM_BID_RATES` | 5키 dict | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 3 | `PREDICTION_DEFAULT_MAXIMUM_BID_RATE` | `1.0` | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 4 | `PREDICTION_CATEGORY_MAXIMUM_BID_RATES` | 5키 dict | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 5 | `PREDICTION_FLOOR_SAFETY_MARGIN_RATE` | `0.001` | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 6 | `PREDICTION_CONSTRUCTION_SCENARIO_FLOOR_OFFSETS` | 3키 dict | 정책(근거 있음 — n=2,051 백분위) | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 7 | `BUSINESS_GROUP_CODE_PREFIXES` | 3키 dict | 정책(분류표) | 5B |
| 8 | `BUSINESS_GROUP_CALIBRATION_ENABLED` | `True` | 환경(토글) | 5C |
| 9 | `PREDICTION_GROUP_MINIMUM_BID_RATES` | 3키 dict | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 10 | `PREDICTION_GROUP_MAXIMUM_BID_RATES` | 3키 dict | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 11 | `PREDICTION_AGENCY_MINIMUM_BID_RATES` | 1키 dict | 정책(기관 고유) | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 12 | `PREDICTION_AGENCY_MAXIMUM_BID_RATES` | 1키 dict | 정책(기관 고유) | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 13 | `PREDICTION_AGENCY_BAND_ASSESSMENT_RATES` | 1키 dict | 정책(기관 고유, basis 결합 경고 동반) | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 14 | `PREDICTION_DEFAULT_BAND_ASSESSMENT_RATE` | `1.0` | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 15 | `PREDICTION_RESERVE_PRIOR_WEIGHT` | `0.2` | **미분류**(근거 주석 없음) | 5D 재판정 |
| 16 | `PREDICTION_RESERVE_PRIOR_FULL_CONFIDENCE_SAMPLES` | `8` | **미분류** | 5D 재판정 |
| 17 | `PREDICTION_HIGH_RATE_TAIL_ADJUSTMENT_ENABLED` | `True` | 환경(토글) | 5D |
| 18 | `PREDICTION_SMALL_BUDGET_HIGH_RATE_BUDGET_MAX` | `50_000_000.0` | 정책(금액 임계) | 5D |
| 19 | `PREDICTION_SMALL_BUDGET_HIGH_RATE_TARGET` | `0.93` | **미분류** | 5D 재판정 |
| 20 | `PREDICTION_SMALL_BUDGET_HIGH_RATE_MIN_RATE` | `0.925` | **미분류** | 5D 재판정 |
| 21 | `PREDICTION_BID_PRICE_GRANULARITY` | `10` | 정책(반올림 규칙) | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 22 | `PREDICTION_BID_PRICE_GRANULARITY_MIN_BUDGET` | `1_000_000.0` | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 23 | `PREDICTION_BID_PRICE_ROUNDING_MODE` | `"floor"` | 정책 | Kotlin decision/guardrail(5D 소비 안 함 — 5D D-5D-9) |
| 24 | `PRICE_PREDICTION_PREFERRED_PREDICTOR` | `"historical"` | 환경 | 5E |
| 25 | `PRICE_PREDICTION_ENABLE_EXPERIMENTAL_PREDICTORS` | `False` | 환경 | 5E |
| 26 | `PRICE_PREDICTION_ENSEMBLE_MODEL_PATH` | `""` | 환경(경로) | 5E |
| 27 | `PRICE_PREDICTION_ENSEMBLE_MIN_SAMPLES` | `32` | 정책(최소 표본) | 5D |
| 28 | `PRICE_PREDICTION_DISTRIBUTION_MIN_RESERVE_RECORDS` | `8` | 정책(최소 표본) | 5D |
| 29 | `PRICE_PREDICTION_DISTRIBUTION_MIN_BID_RATIO_SAMPLES` | `3` | 정책(최소 표본) | 5D |
| 30 | `PRICE_PREDICTION_AWARD_RATE_GBM_MODEL_PATH` | `""` | 환경(경로) | 5E |
| 31 | `PRICE_PREDICTION_AWARD_RATE_GBM_MIN_CATEGORY_ROWS` | `40` | 정책(미학습 가드 — 끌 수 없음) | 5C·5D(5D 미학습 가드 임계, D-5D-9) |
| 32 | `PRICE_PREDICTION_BACKTEST_MIN_TRAINING_SAMPLES` | `5` | 정책 | 5C |
| 33 | `PRICE_PREDICTION_BACKTEST_HOLDOUT_SIZE` | `5` | 정책 | 5C |

집계: **정책 23 · 환경 6 · 미분류 4** (조사 노트와 일치). 경계 밖 동류 3건(`…GBM_MIN_TRAINING_ROWS`·`…FEED_ORIGIN_ONLY`·
`GROUP_CALIBRATION_MIN_SAMPLES`)은 5C 가 만나면 같은 표 형식으로 그 slice 에 등재한다.

## 5A 가 이 표로 만드는 것
- `ml_engine/registry/policy.py` — `load_policy(path) -> Policy`(frozen dataclass, 버전 필드 필수, 미지 키 거부). **YAML 파일은 5A 에 없다**(값 없음) — test 는 임시 YAML 표본으로 로더만 고정한다.
- 환경 6 은 정책 파일이 아니라 serving/training 진입점의 설정 객체(5E) — 5A 는 자리를 만들지 않는다.

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-11 착수 | 표 등재(조사 b-1 그대로), 분류 승인 | 운영자 D-M5-6 (a) |
| 2026-09-12 | 「소비 예정」 배정 정정 — guardrail·가격 15행은 Kotlin, #31 은 5C·5D | 5D 착수 D-5D-9 |
