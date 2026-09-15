# M5/5D-2 정책 값 — `OPEN-5D2-POLICY-VALUES`(**값 대기**, 운영자 결정 2026-09-13 (c))

> **지위: 키 신설 승인, 값 미정.** `assessment.agency_sample_threshold`(기관 표본 임계 — 미만이면 `Diagnostics.agency_sample_below_threshold = true`,
> 수축 가중치가 응답 근거에 실림: ML-04 ②)는 legacy 에 대응 상수가 없어 값을 지어내지 않는다. 운영자 (c): **5C 재학습 지표가 나오면 값을 정한다.**
> 그 전까지 `inference-v1.yaml` 에 키를 넣지 않으며 로더는 미선언을 `PolicyRejected(MissingKey)` 로 낸다(서빙(5E)이 켜지려면 값 승인이 선행돼야 함이 로더에서 드러난다).
> test 는 case 정책(golden 011 synthetic 10)을 주입해 규칙만 검증한다.

| 키 | 값 | 상태 |
| --- | --- | --- |
| `assessment.agency_sample_threshold` | **미정**(후보: (a) 5A #31 과 같은 40 (b) golden synthetic 10 (c) 5C 지표 뒤) | (c) — OPEN |

## 이 slice 가 값을 갖지 않는 것
기존 `reserve.*`·`bid_ratio.*`·`assessment.plausible_*`(5D 승인 값을 소비만) · predictor 선택 축(D-5D2-2 — 정책 파일 밖, 엔진 하나) · `#27 ENSEMBLE_MIN_SAMPLES`(미이식).

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-13 착수 | 키 신설 승인·값 OPEN | 운영자 「5D 종결·5D-2 착수 진행」(추천 (c)) |
