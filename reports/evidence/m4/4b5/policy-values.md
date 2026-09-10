# M4/4B-5 정책 값 — legacy-behavior 층

> **지위: `OPEN-4B5-POLICY-VALUES`, 승인 대기.** 값은 legacy 산식 상수 그대로다(D-4B5-2
> 예외 하나 제외 전부) — 4B-4 의 확률 축 재정규화 같은 변환이 없다. 좌표는 확정된 legacy
> 파일이라 `file:line` 인용을 허용한다(scope.md 착수 계약).

## §1 urgency 밴드 + noDeadlineUrgency (D-4B5-2)

| 항목 | 값 | legacy 좌표 |
| --- | --- | --- |
| 밴드(오름 `≤`) | `6h→1.0, 24h→0.8, 72h→0.55, ∞→0.25` | `allocation.py:47-54` `URGENCY_SCORE_BANDS` |
| 마감 미공시 | `0.3`(`noDeadlineUrgency`) | `allocation.py:47` `URGENCY_SCORE_UNKNOWN` — legacy 별도 상수, sentinel 아님(D-4B5-2) |

## §2 complexity 밴드·가중치·상수

| 항목 | 값 | legacy 좌표 |
| --- | --- | --- |
| 예산 밴드(내림 `≥`) | `5억→.92, 2억→.78, 1억→.62, else .38` | `score_tables.py` `_BUDGET_COMPLEXITY_BANDS` |
| 마감 밴드(오름 `≤`) | `6h→1.0, 24h→.78, 72h→.52, ∞→.24` | `score_tables.py` `_DEADLINE_COMPLEXITY_BANDS` |
| 마감 미공시 | `0.3`(`noDeadlineComplexity`) | `score_tables.py` `_DEADLINE_MISSING_COMPLEXITY_SIGNAL` |
| 가중치 | `budget .30·keyword .25·deadline .15·loadRatio .10·match .10·capacity .10` | `score_tables.py` `_EXECUTION_COMPLEXITY_COMPOSITE_WEIGHTS` |
| keyword 상수 | `keywordBase .24 · keywordStep .08`(`min(1, .24+hits×.08)`) | `scoring.py:306-347` `_estimate_execution_complexity_score` |

## §3 margin 가중치·alignmentTolerance

| 항목 | 값 | legacy 좌표 |
| --- | --- | --- |
| 가중치 | `recommendedRate .35·floorHeadroom .20·predictionAlignment .20·priceFitness .15·capacity .10` | `score_tables.py` `_EXPECTED_MARGIN_COMPOSITE_WEIGHTS` |
| alignmentTolerance | `0.12` | `scoring.py:255-304` `_estimate_expected_margin_score` (`abs(rec-pred)/0.12`) |

## §4 budgetCaptureRounding(D-4B5-4 신설 슬롯)

| 항목 | 값 | 근거 |
| --- | --- | --- |
| scaleDigits | `6` | `RateArithmetic.kt` KDoc "legacy 6자리는 test 정책에만" 관례(구조적 placeholder, 아래 참고) |
| mode | `HALF_UP` | 위와 같음 — placeholder |

**§4 는 legacy-behavior 가 아니라 구조 placeholder다** — deriveBudgetCapture 자체가
scope.md 표의 산식(`clamp01(recommended.bidRateAgainst(base))`)을 그대로 구현하려면
`bidRateAgainst`(1B)가 요구하는 반올림 정책이 필요해서 생긴 슬롯이다. `scaleDigits=6`은
비율(0~1) 정밀도를 실질적으로 6자리까지 보존하면 최종 `clamp01` 결과(소수 둘째 자리
수준 비교가 목적)에 영향이 없다는 판단이며, 실측(정밀도 민감도 분석) 근거는 없다 —
승인 대상.

## §5 미결(⑤ 보류)

competitiveness 밴드(`.8×avg→.95, ≤avg→.75, 1.2×avg→.50, else .25`, `bid_recommendation.py:44-49,111-124`)
값 자체는 legacy 에서 확인했으나(scope.md ⑤ 표), 이 slice는 `deriveCompetitiveness` 를
구현하지 않아 정책 데이터에도 반영하지 않았다(milestone-4.md 4B-5 착수 문단, checklist
「알려진 제한」).

## 사용자 승인

미승인 — `OPEN-4B5-POLICY-VALUES` 종결은 slice 종결 승인과 함께 받는다.
