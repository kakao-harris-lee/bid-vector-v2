# M5/5D 정책 값 — `OPEN-5D-POLICY-VALUES`(**사용자 승인 2026-09-12, 착수 시**)

> **지위: 승인.** 전부 legacy-behavior(`ed4b06c` 모듈 상수·`app/core/config.py`), 5D 가 지어낸 수치 없음
> (단 `scenario.clamp_max` 는 2026-09-16 운영자 결정 ①로 `1.0`이다 — 계약 후보율 축 ≤ 1 에 맞춘 값, legacy `1.4` 대체,
> 아래 표 각주·change_history 참고). 정본은 이 문서 —
> 실물은 `ml-engine/policy/inference-v1.yaml`(평탄 키, `PolicyScalar` 제약), 로더 `load_inference_policy` 가 `known_keys` 전수·값 불변식으로
> 대조하고 test 가 이 표와 YAML 을 대조한다. 값을 바꾸려면 이 문서 → YAML → test 순.

| 키(평탄) | 값 | legacy 좌표 |
| --- | --- | --- |
| `version` | `inference-v1` | — (`Candidate.weight_policy_version` 으로 응답에 실림) |
| `scenario.z` | `1.2816` | `app/ai/predictors/scenario_spec.py` `SCENARIO_INTERVAL_Z` |
| `scenario.conservative.weight` / `scenario.base.weight` / `scenario.aggressive.weight` | `0.24` / `0.52` / `0.24` | `CANDIDATE_SCENARIOS`(합 1 불변식) |
| `scenario.conservative.z_sign` / `scenario.base.z_sign` / `scenario.aggressive.z_sign` | `-1` / `0` / `1` | 같은 곳 |
| `scenario.clamp_min` / `scenario.clamp_max` | `0.7` / `1.0` | `app/ai/predictors/historical/statistics.py` `clamp_bid_rate`(`clamp_max` 는 legacy 값 `1.4`가 아니다 — 5F-1 갱신, 아래 change_history) |
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
| `maturity.time_axis_substitute` | `"NOT_APPROVED"` | **M9/ml-lifecycle 신설**(SET-06 · `OPEN-SET-10`). 개찰 시각 결측 시 마감 시각 대체를 승인된 정책으로 채택할 것인가 — **미결이므로 미승인**이고, 그래서 대체 후보 시각만 있는 행은 조용히 대체되지 않고 사유 있는 시간축 측정 불가로 산출된다(legacy 는 조용히 대체했고 provenance 가 소실됐다). **legacy 대응 상수가 없는 신설 칸이고 값은 「아직 승인하지 않았다」의 기계적 표현이다** — 운영자가 채택하면 `"APPROVED"` 가 된다. bool 이 아니라 닫힌 어휘인 이유: PyYAML 이 `yes`·`Yes`·`YES`·`on`·`On`·`true`·`True` 를 bool 로 읽어 bool 칸은 그 표기로도 승인이 생겼다(verifier L-2 실측 · 표기 전수는 수정 라운드 2 에서 실측해 정정 — `y`·`1` 은 bool 이 아니다) |

## 이 slice 가 값을 갖지 않는 것
합성 `confidence` 계수(이식 안 함) · seed·num_threads(5C reproducibility) · 5A 표의 guardrail·가격 15행(Kotlin) · embargo 임계(`OPEN-SET-06`, Kotlin).

## 승인된 산출물 지문 (M9/ml-lifecycle, 2026-11-06)

| version | 승인된 파일 내용 sha256 |
| --- | --- |
| `inference-v1` | `1d8a8cf6d5fbfa495e9503c003029dd29f05fd3b63f924ac5e58dfe35ada3b96` |

사유와 절차는 `reports/evidence/m5/5c2/policy-values.md` §4 와 같다(같은 등록부의 둘째 줄).

**이 지문을 묻는 자리**(M9/ml-lifecycle 수정 라운드 3 에서 추가): 조립 근
`app.server._preload` 가 `inference.policy.load_approved_inference_policy` 로 읽는다. 그래서
`ML_ENGINE_INFERENCE_POLICY` 가 가리킬 수 있는 것은 **이 지문의 산출물**뿐이다 — 같은
`inference-v1` 이름을 단 편집 사본도, 이름을 바꿔 단 사본도 부팅이 거부한다. 출하 파일 자체를
고치는 길은 **CI** 의 등록부 ↔ 출하 지문 등식 test 가 막는다.

**앞 판 문면 정정**(cr r3 H-1): 이 절은 2026-11-06 첫 판에서 「`APPROVED` 로 바꾼 사본을 같은
이름으로 로드하는 길이 닫힌다」고 적었는데, 그때는 **런타임에 이 등록부를 묻는 자리가 없었다**
(등록부의 효과가 CI 층뿐이었다) — 승인 장부가 열린 축을 닫혔다고 적은 거짓 문면이었다. 같은
라운드에서 그 자리를 만들어 문면이 참이 됐고, 위 두 문단이 「무엇이 어느 층에서 막히는가」를
적는다.

이 축에서 지키는 값 둘: **대체 승인 칸**(`maturity.time_axis_substitute` — `OPEN-SET-10` 이
미결인 동안 미승인이어야 한다)과 **`maturity.window_days`** — 뒤쪽은 `build_weekly_maturity`
→ `plan_evaluation_windows` 로 흘러 **평가 창 선택**, 따라서 승격 판정 입력을 바꾼다.

**값을 바꾸려면 세 자리를 같은 커밋에서** — ⑴ 위 표(이 문서) ⑵
`ml-engine/policy/inference-v1.yaml` ⑶ `ml_engine.registry.policy_approvals` 한 줄.

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-12 착수 | 표 등재·승인 | 운영자 「승인」(D-5D-8 legacy 값) |
| 2026-09-16 (5F-1) | `scenario.clamp_max` `1.4` → `1.0` | 운영자 결정 ①(`OPEN-5E2-CANDIDATE-RATE-UPPER` (a) 채택·종결) — 계약 응답 후보율 축은 ≤ 1(D-2B-8·D-2F-4)인데 legacy clamp 상한(1.4)이 1 을 넘어 5E-2 wire 층이 적법한 입력도 `MappingRejected`로 거부했다. 이 값은 더 이상 legacy-behavior 그대로가 아니다(legacy `1.4`는 위 표 각주로 보존). 산식·다른 값 무변경 |
