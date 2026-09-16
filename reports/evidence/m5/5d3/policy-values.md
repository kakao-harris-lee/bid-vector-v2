# M5/5D-3 — policy-values.md

신설 정책 값 없음 — 이 slice는 `ml-engine/policy/inference-v1.yaml`을 편집하지 않았고
`InferencePolicy`에 새 필드를 추가하지 않았다.

## test 가 쓴 값의 출처

- `tests/inference/test_distribution.py`·`test_observations.py`·`test_engine.py`
  (D-5D3-7 test 제외)는 5D-2가 이미 둔 `_policy_support.shipped_inference_policy_for_test()`
  (출하 YAML + `assessment.agency_sample_threshold` placeholder `1`)를 그대로 쓴다 — 이
  slice가 새로 주입한 정책 값은 없다.
- `tests/inference/test_engine.py::test_serve_bid_rates_wire_driven_direct_segment_support_
  matches_golden_011`은 golden `fixtures/input/ml-kernel-011.json`의 `policy.assessment.*`
  다섯 값(`agencyPriorStrength` 15·`categoryPriorStrength` 30·`minPredictiveStd` 0.004·
  `minSamplesForVariance` 2·`agencySampleThreshold` 10)을 `dataclasses.replace`로 얹는다 —
  이 값들은 골든 corpus 자신의 synthetic 값(D-M5-golden 레인 규율, 5D-2와 같은 출처)이지
  이 slice가 새로 정한 값이 아니다.

## `OPEN-5D2-POLICY-VALUES` 영향 없음

출하 `assessment.agency_sample_threshold`(임계 실값)는 여전히 미정(5C 재학습 지표 뒤,
운영자 결정 2026-09-13 (c))이다 — 이 slice는 그 OPEN을 닫거나 건드리지 않는다.
