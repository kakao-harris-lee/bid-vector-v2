# 4B-4 정책 데이터 값 표 — **사용자 승인 2026-09-10**

> **승인일: 2026-09-10.** 승인은 slice 4B-4 종결 승인과 같은 결정에 실렸다 — 정본은
> `reports/evidence/m4/4b4/checklist.md` 「사용자 승인」 절이다. 4D-1
> `policy-values.md`·4E `policy-values.md`와 같은 표 형식(층·근거·범위)을 유지한다.
> `PriorityPolicyData.kt`의 `PRIORITY_POLICY.source`가 이 문서를 가리킨다.
>
> **착수 시(2026-09-10)에는 scope.md D-4B4-4가 구조만 고정한 착수 placeholder였다.**
> 이 승인으로 값 자체가 운영 정책값이 됐다(`OPEN-4B4-POLICY-VALUES` 종결) — 4D-1
> `MlCallPolicyData`·4E `NotificationDeliveryPolicyData`가 착수 placeholder에서 종결
> 승인으로 확정된 것과 같은 절차. 값을 바꾸려면 이 문서를 먼저 갱신한다(3A
> `KONEPS_COLLECTION_POLICY` 관례).

---

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `legacy-behavior` | 기존 Python이 실제로 하는 값 | §1(가중치)·§2(penalty)·§3(categoryOffset) — `bid-vector@ed4b06c`(symlink) `allocation.py`·`operator_strategy_tuning.py` |
| 엔지니어링 판단(새 축) | legacy에 대응이 없는 값 | §4(`normEpsilon`) — 2E `embedding.proto` L2 벡터 계약 자체는 새로 생겼고 legacy에는 vector norm 개념이 없다 |
| 운영자 승인 | 값 자체를 운영자가 정함 | **§1~§4 전체 — 사용자 승인 2026-09-10**(slice 4B-4 종결 승인과 같은 결정, `checklist.md` 「사용자 승인」 절) |

---

## 1. 가중치 재정규화 (scope.md ②, D-4B4-1·D-4B4-4)

legacy `opportunity_score`(`allocation.py:103-110`)는 여섯 항의 가중합이다(조사
§1.1): `PROBABILITY 0.40 · MATCH 0.23 · URGENCY 0.14 · COMPETITIVENESS 0.08 ·
BUDGET_CAPTURE 0.06 · EXPECTED_MARGIN 0.09`(합 1.00). 2E 계약에 확률 축이 없어
(ML-03·D-M2-8) 나머지 다섯을 `1 - 0.40 = 0.60`으로 나눠 재정규화했다(조사 §7 5).

| `Component` | legacy 가중치 | `÷0.60` (반올림 전) | scale 4 반올림 | `PRIORITY_POLICY` 값 |
| --- | --- | --- | --- | --- |
| `Match` | 0.23 | 0.383333... | 0.3833 | **0.3834**(잔차 흡수, 아래 참고) |
| `Urgency` | 0.14 | 0.233333... | 0.2333 | 0.2333 |
| `Competitiveness` | 0.08 | 0.133333... | 0.1333 | 0.1333 |
| `BudgetCapture` | 0.06 | 0.100000 | 0.1000 | 0.1000 |
| `ExpectedMargin` | 0.09 | 0.150000 | 0.1500 | 0.1500 |

**잔차 처리**: 다섯 값을 각각 scale 4로 반올림하면 합이 0.9999(0.3833+0.2333+0.1333+
0.1000+0.1500)가 되어 `PriorityPolicyData.init`의 합=1 불변식을 깬다. 잔차 0.0001을
최대 가중치(`Match`)에 흡수해 0.3834로 고정했다 — 합이 정확히 1.0000이 된다. 흡수
대상을 `Match`로 고른 것은 임의의 엔지니어링 선택이다(legacy 값 자체가 재정규화
과정에서 소수점 넷째 자리까지 보존을 주장하지 않는다) — 운영자가 다른 분배(예:
비례 배분)를 원하면 이 표를 갱신한다.

## 2. penalty (scope.md ②, 조사 §1.1 `allocation.py:505-532`)

| 정책 슬롯 | legacy 값 | `PRIORITY_POLICY` 값 | 근거 |
| --- | --- | --- | --- |
| `LoadPenaltyPolicy.ratioWeight` | `0.18`(`allocation.py:505-517`) | 0.18 | 무변경 재활용 |
| `LoadPenaltyPolicy.workloadWeight` | `0.12` | 0.12 | 무변경 재활용 |
| `ComplexityPenaltyPolicy.threshold` | `0.55`(`allocation.py:528-532`) | 0.55 | 무변경 재활용 |
| `ComplexityPenaltyPolicy.slope` | `0.18` | 0.18 | 무변경 재활용 |
| `ComplexityPenaltyPolicy.cap` | `0.12` | 0.12 | 무변경 재활용 |

**재활용하지 않은 것**: `workload_source=="auto"`일 때의 `auto_workload_penalty_multiplier`
배수(전략 컬럼, 조사 §1.1)는 이 slice의 `loadRatio`·`workload` 입력 자체에 이미 접혀
들어온다고 가정한다(성분 산출은 4B-5 소관, scope.md 「만들지 않는 것」) — 이 정책
슬롯에는 배수 자리를 두지 않았다.

## 3. categoryOffset 범위 (scope.md ④, 조사 §1.2 `operator_strategy_tuning.py:16-17`)

| 정책 슬롯 | legacy 값 | `PRIORITY_POLICY` 값 |
| --- | --- | --- |
| `categoryOffsetMin` | `-0.2` | -0.20 |
| `categoryOffsetMax` | `0.2` | 0.20 |

무변경 재활용 — legacy `_apply_category_priority_override`가 같은 범위로 offset을
clamp 전 거부한다(이 slice는 clamp 대신 `MatchOutcome.OffsetOutOfRange`, D-4B4 위협
모델 (e)).

## 4. `normEpsilon` (scope.md ②, `UnitVector` norm 불변식)

| 정책 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `normEpsilon` | 0.0001 | **legacy 대응 없음** — 2E `embedding.proto`의 L2 정규화 벡터 계약이
이 slice에서 처음 소비되고, legacy Python에는 vector norm 불변식 개념 자체가 없다
(`project_similarity.py`가 코사인을 직접 계산할 뿐 정규화를 값으로 검증하지 않는다).
엔지니어링 판단값 — float32 임베딩을 L2 정규화한 뒤 합연산 반올림으로 흔히 발생하는
`1e-6`~`1e-4` 수준의 오차를 통과시키는 보수적 상한. 사용자 승인 2026-09-10으로 값의
**존재**가 확정됐다 — 관측 데이터(M5 provider)에 따른 값의 **옳음** 재검토는 별도
실측 경로가 열리면(4B-5 이후) 그 결과로 이 문서의 정책 version을 갱신한다.

**실측 갱신 경로.** 4B-5·M5 provider가 임베딩 오차 분포를 실측하면 그 결과로 §4 값을
갱신하고 이 문서의 정책 version을 올린다(ADR 0010 D-1 「보수적 상한 + 측정 의무」와
같은 규율) — 이 승인은 「값이 존재해도 되는가」를 확정한 것이지 「값이 옳은가」를
실측한 것이 아니다.

---

## 5. `change_history`

- **2026-09-10 최초 등재** — 구현 레인이 §1~§4 값을 구조 검증용 착수 placeholder로 심음.
- **2026-09-10 사용자 승인** — slice 4B-4 종결 승인과 같은 결정으로 §1~§4 값 자체가
  확정됐다(`OPEN-4B4-POLICY-VALUES` 종결). 값은 착수 시점에서 변경 없음. 임베딩 오차
  실측에 따른 §4(`normEpsilon`) 갱신 경로는 4B-5 이후로 활성 유지.
