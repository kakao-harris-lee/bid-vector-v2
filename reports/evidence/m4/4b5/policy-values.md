# M4/4B-5 정책 값 — legacy-behavior 층

> **지위: 사용자 승인 2026-09-10 — `OPEN-4B5-POLICY-VALUES` 종결.** 값은 legacy 산식
> 상수 그대로다(D-4B5-2 예외 하나 제외 전부) — 4B-4 의 확률 축 재정규화 같은 변환이
> 없다. 좌표는 확정된 legacy 파일이라 `file:line` 인용을 허용한다(scope.md 착수 계약).
> **값은 legacy 그대로지만 거동은 legacy 와 셋이 의도적으로 갈린다** — §3 「의도된
> 갈림 셋」(verifier r1 F-1·F-3·F-7·r2 G-1). 정본은 이 문서 — 값을 바꾸려면 이 문서를
> 먼저 갱신한다.

## change_history

| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-10 착수 | §1~§4 값 등재, placeholder 지위 | 구조 검증용 — 실측 근거는 legacy 값 자체 |
| 2026-09-10 verifier r1→r2 | §3 「의도된 갈림」 셋 등재(F-1·F-3·F-7 정정) | `_workspace/m4-4b5/04_verifier_report.md`·`05_verifier_report_r2.md` |
| 2026-09-10 사용자 승인 | 값 전부 확정, `OPEN-4B5-POLICY-VALUES` 종결 | 아래 「사용자 승인」 절 |

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

**의도된 갈림 셋(legacy 산식을 그대로 재현하지 않는 자리, verifier r1 F-3·F-7·r2 G-1):**

1. **`MarginInputs.init` 의 `Rate ≤ 1` 거부(verifier r1 F-1, 판정층 짝은 r2 G-1 이 축별로
   정정)** — legacy 는 세 율을 `max(0, min(1, ·))` 로 **조용히 잘라** 쓴다. V2 는 자르지
   않고 **거부**한다(D-4B5-1 방향, `DerivationInputs.kt` KDoc). 판정층 짝은 축마다 다르다
   — `recommendedRate`·`predictedRate` 는 2B `D-2B-8`(`Rate.fraction ≤ 1` 계약)·4D-1
   `Unavailable(ContractViolation)` 매핑이 상류에서 이미 강제하는 값의 마지막 안전판이다.
   **`floorRate` 는 상류 관문이 없다**(`Notice.floorRate`·`Canonicalize`·
   `NoticeReconstruction` 전수 grep 0건) — 이 `init` 이 유일한 관문이고, 4B-6 조합기가
   `MarginInputs` 생성 전에 `Notice.floorRate > 1` 을 `Absent(FloorRateOutOfRange)` 로
   걸러야 한다(D-4B6-4 인계).
2. **`floor = 1` 에서 legacy 는 `0`, V2 는 `recommended`** — legacy 는 `max(1e-6, 1-floor)`
   엡실론 분모로 사실상 `headroom → 0` 에 수렴한다(하한이 100%라 여유 없음). V2 는
   scope.md ③ 이 명시 고정한 값(「floor = 1 이면 `rec`」, 4B-5 착수 계약, 설계 검토 우회
   (6))을 그대로 따른다 — 이것은 이 slice의 **선택된 재설계**이지 계산 오류가 아니다.
   실측 차: `rec=0.95, floor=1.0` → V2 `0.7225` vs legacy `0.5325`(`MarginDerivation.kt`
   `floorHeadroomOf` KDoc 참고).
3. **legacy `round(·, 2)` 2자리 반올림은 재현하지 않는다**(verifier r1 F-7) — ③④ 결과는
   `BigDecimal` 전 정밀도로 남고, 4B-4 `composePriority` 가 다시 가중합하므로 조기
   반올림이 오히려 정밀도를 잃는다는 판단이다. 값 손실이 있는지는 실측하지 않았다.

## §4 budgetCaptureRounding(D-4B5-4 신설 슬롯)

| 항목 | 값 | 근거 |
| --- | --- | --- |
| scaleDigits | `6` | `RateArithmetic.kt` KDoc "legacy 6자리는 test 정책에만" 관례(구조적 placeholder, 아래 참고) |
| mode | `HALF_UP` | 위와 같음 — placeholder |

**§4 는 legacy-behavior 가 아니라 구조 placeholder다** — deriveBudgetCapture 자체가
scope.md 표의 산식(`clamp01(recommended.bidRateAgainst(base))`)을 그대로 구현하려면
`bidRateAgainst`(1B)가 요구하는 반올림 정책이 필요해서 생긴 슬롯이다. `scaleDigits=6`은
비율(0~1) 정밀도를 실질적으로 6자리까지 보존하면 최종 `clamp01` 결과(소수 둘째 자리
수준 비교가 목적)에 영향이 없다는 판단이다. **사용자 승인 2026-09-10 으로 값 자체는
확정됐다** — 정밀도 민감도(자리수가 결과를 얼마나 바꾸는가)의 실측 근거는 여전히
없다(알려진 제한, `checklist.md`).

## §5 미결(⑤ 보류)

competitiveness 밴드(`.8×avg→.95, ≤avg→.75, 1.2×avg→.50, else .25`, `bid_recommendation.py:44-49,111-124`)
값 자체는 legacy 에서 확인했으나(scope.md ⑤ 표), 이 slice는 `deriveCompetitiveness` 를
구현하지 않아 정책 데이터에도 반영하지 않았다(milestone-4.md 4B-5 착수 문단, checklist
「알려진 제한」).

## 사용자 승인

**2026-09-10 — `OPEN-4B5-POLICY-VALUES` 종결.** slice 4B-5 종결 승인과 함께 받았다
(§1~§4 값 전부, 「의도된 갈림」 셋 포함). 정본은 이 문서 — evidence
`reports/evidence/m4/4b5/checklist.md` 「사용자 승인 2026-09-10」 절.
