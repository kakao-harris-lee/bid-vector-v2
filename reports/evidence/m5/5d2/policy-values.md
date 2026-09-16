# M5/5D-2 정책 값 — `OPEN-5D2-POLICY-VALUES`(**잠정값 채택, 종결** — 5F-1, 2026-09-16)

> **지위: 잠정 승인.** `assessment.agency_sample_threshold`(기관 표본 임계 — 미만이면 `Diagnostics.agency_sample_below_threshold = true`,
> 수축 가중치가 응답 근거에 실림: ML-04 ②)는 legacy 에 대응 상수가 없어 값을 지어내지 않는다. 후보 (b) golden synthetic `10`(`ml-kernel-011`,
> 승인된 golden case 가 실제로 쓰는 값)을 잠정 채택한다 — 운영자 결정 2026-09-16 ③. **재승인 조건: 5C 재학습 지표가 나오면 그 지표로
> 이 값을 다시 판정한다**(후보 (a) 40 은 별개 축 — `gbm.min_category_rows`와 같은 GBM 미학습 가드(5A #31), 기관 표본 임계와 다른 성질이라
> 재승인 시에도 직접 비교 대상이 아니다). `inference-v1.yaml` 에 `10`으로 실려 있고, 로더는 이 키가 없으면 여전히
> `PolicyRejected(MissingKey)`를 낸다. test 는 이 값 자체를 잠그는 test(`tests/inference/test_policy.py::
> test_shipped_policy_file_loads_with_lowered_clamp_and_agency_sample_threshold`)와, 그 값과 무관한 다른 test 를 위한 synthetic
> 격리(`case 정책`, golden 은 자체 cfg)를 구분해 쓴다.

| 키 | 값 | 상태 |
| --- | --- | --- |
| `assessment.agency_sample_threshold` | `10`(잠정 — 후보 (b) golden synthetic) | 채택·종결(재승인 조건부) |

## 이 slice 가 값을 갖지 않는 것
기존 `reserve.*`·`bid_ratio.*`·`assessment.plausible_*`(5D 승인 값을 소비만) · predictor 선택 축(D-5D2-2 — 정책 파일 밖, 엔진 하나) · `#27 ENSEMBLE_MIN_SAMPLES`(미이식).

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-13 착수 | 키 신설 승인·값 OPEN | 운영자 「5D 종결·5D-2 착수 진행」(추천 (c)) |
| 2026-09-16 (5F-1) | 값 `10`(후보 (b)) 잠정 채택, `OPEN-5D2-POLICY-VALUES` 종결(재승인 조건부) | 운영자 결정 ③ — 5C 재학습 지표 전까지 legacy 대응 상수가 없어 지어낸 값 대신 승인된 golden case(`ml-kernel-011`)가 쓰는 값을 옮김 |
