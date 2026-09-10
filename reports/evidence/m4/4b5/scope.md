# Slice 계약 — M4 / 4B-5 · 성분 파생 함수 (decision 순수 — `PriorityInputs` 를 fact 에서 만든다)

> **지위**: **착수 계약 2026-09-10.** 운영자 결정 2026-09-10 E-1 (c)·E-4 (a). 세션 모델 단독 작성.
>
> **자리.** 4B-4 는 `PriorityInputs`(성분 다섯 + penalty 입력 셋, 각 `ScoreFact<UnitScore>`)를 **값으로** 받는다. 그 값을 도메인 fact(마감·금액·4D-1 `Predicted`·용량·
> 텍스트 키워드 수)에서 만드는 **순수 파생 함수**가 4B-5 다. 여전히 `decision` 안 — port·시각(`Clock`)·텍스트 합성·임베딩 호출은 없다. 그것들은 4B-6(workflow
> 조합기 `OpportunityAnalysis : MlAnalysisPort` + 임베딩 port + 텍스트 합성 규약 `OPEN-2E-TEXT-SYNTHESIS` + 시장 평균·workload port)이 한다. 4D-1 교훈(한 slice 에 층을
> 섞지 않는다) 그대로.
>
> **legacy 산식의 처리(E-4 (a)).** 밴드 표·가중치 표·상수의 **값**은 legacy-behavior 층으로 재활용(`allocation.py:47-54`·`:495-532`, `allocation_core.py:104-`,
> `opportunity_analysis/score_tables.py`, `scoring.py:255-357`, `ai/bid_recommendation.py:44-49,111-124`, `opportunity_analysis/base.py:73-88` — 확정된 legacy 파일이라 좌표
> 허용), **구조**는 정책 데이터 + 순수 함수. legacy 의 sentinel(「base 없으면 중립 0.5」·「capacity None → 0」·「시장 평균 없으면 예산으로 대체」)은 전부 **`Absent(reason)`**
> 로 바꾼다 — 4B-4 재정규화가 그 부재를 흡수한다(`data-dictionary.md` §6.3 sentinel 금지). **예외 하나**: 「마감 미공시 → urgency 0.3 · complexity deadline 신호 0.3」은
> legacy 가 **별도 상수**로 둔 의도된 값(기본값이 아니라 「마감 미공시 공고」의 점수)이라 정책값으로 남긴다(D-4B5-2). STR-05(`category_priority_overrides`·
> `auto_workload_penalty_multiplier`)는 capability-map 이 `후속` — 이 slice 는 값도 슬롯도 만들지 않는다.
>
> **레인 격리.** 다른 세션은 `m4/2026-09-08` 에서 M3 후속 3G(`adapters/persistence` test·`milestone-3.md`·capability-map)를 진행 중 — `decision` 과 겹치지 않는다.
> 이 slice 는 4B-4 병합 커밋 `9eddf75`(=`main`)에서 가른 브랜치 `m4-4b5/2026-09-10`(worktree `bid-vector-v2-m4e`). 공유 파일은 `config/quality/gate-tests.properties`
> (`gate.tests.decision` 키)·`milestone-4.md` 뿐.

```yaml
milestone: m4
slice: 4b5-component-derivations
base_sha: 9eddf7525b74f89ce279acbb3adf4948f05c25f1   # 4B-4 병합 커밋 = main (m4/2026-09-08 은 3G 편집 중이라 ff 대기)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다.
branch: m4-4b5/2026-09-10
in_scope:
  - decision/src/main/kotlin/bidvector/decision/priority/derive/**   # Band·DerivationPolicyData + DERIVATION_POLICY·파생 함수 ①~⑦·입력 값 타입(MarginInputs·ComplexityInputs·KeywordHits)·부재 사유 sealed(DerivationAbsence)
  - decision/src/test/kotlin/bidvector/decision/priority/derive/**  # 밴드 경계 전수·합성 손계산·Absent 사유·정책 불변식·property(단조·범위)·출하 정책 값 산식 재구성
  - config/quality/gate-tests.properties                            # gate.tests.decision 등재만
  - milestone-4.md                                                  # 4B 절 4B-5 문단(착수·종결)
  - reports/evidence/m4/4b5/**                                      # + policy-values.md(legacy-behavior 층 표, 승인 대상)
out_of_scope:
  - workflow/** · adapters/**                                       # 4B-6(조합기·port·합성 규약·Clock)·4D-2(client)
  - decision/src/main/kotlin/bidvector/decision/priority/*.kt       # 4B-4 산출물(`PriorityInputs`·`composePriority`·`SemanticMatch`·`PriorityPolicyData`·`ScoreFact`) — 소비만. **CPD 가 4B-4 의 private 가중합과 충돌하면 멈추고 보고**(계약 갱신으로 `internal` 승격 여부 결정)
  - decision 의 다른 커널(VerdictLadder·LadderInput 등)
  - 시각 계산(`Clock`·`Notice.deadlineAt` → 남은 시간)·텍스트 → 키워드 매칭(문자열 처리)·시장 평균 조회·workload 집계·추천가 산출(4D-1 후보율 × 기초금액 = 1B `RoundingPolicy`)   # 전부 4B-6 — 이 slice 는 결과 값(Duration?·hits·Money?·BidAmount?)을 받는다
  - `match`(4B-4 `SemanticMatch`) · `similarity`(비성분, D-4B4-5) · STR-05 값·슬롯(후속) · capacity 스케일 정규화(`>1 → /100`, legacy 스케일 혼재 흡수 — V2 입력은 `UnitScore`)
  - fixtures/** · app/**                                            # corpus 는 `OPEN-4B5-CORPUS`
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — domainSourceReferenceGate·moduleDependencyGate·api-type-policy·cpdCheck 포함
  - "./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.derive.*'"             # S-2
  - "./gradlew --no-daemon :decision:test"                                                             # S-3 — 4B-1·4B-3·4B-4 test 무변경
  - "./gradlew --no-daemon :decision:gateExecutionGate"                                                # S-4
  - "./gradlew --no-daemon :app:test --tests '*Conformance*'"                                         # S-5 — corpus 무영향
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m4/4b5/rollback.md`**. 신설 패키지 둘(main·test) 삭제 + `gate-tests.properties`·`milestone-4.md` 줄 단위(최신→과거 hunk). 병합 전에는 「브랜치를 버린다」.
```

근거: `milestone-4.md` 4B · `data-dictionary.md` §6.1·§6.3 · `capability-map.md` STR-05(`후속`)·DEC-06 · 4B-4 계약·산출물 · shared-kernel `Money`·`BaseAmount`·`BidAmount`·
`MoneyArithmetic`(`bidRateAgainst`)·`Rate` · 4D-1 `Predicted`(`BidRateCandidates`·`PriceFitness`) · 4B-2 `CapacitySnapshot` · 조사 노트 `_workspace/m2-2e/01_scout_opportunity_scoring.md`
§1.1·§1.3 · legacy 축어(위) · `_workspace/m4-4b5/02_design-review.md`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 9eddf7525b74f89ce279acbb3adf4948f05c25f1..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**.

---

## 이 slice 가 하는 일

| # | 파생 함수(순수) | 입력(값 — 4B-6 이 fact 에서 만든다) | 산식(legacy-behavior 값 → 정책 데이터) | 결과·Absent 사유 |
| --- | --- | --- | --- | --- |
| ① | `deriveUrgency(remaining: Duration?)` | 마감까지 남은 시간(`Clock`·`Notice.deadlineAt` 은 4B-6) | 밴드(상한 포함 `≤`) `6h→1.0, 24h→0.8, 72h→0.55, ∞→0.25`; `null` → 정책값 `noDeadlineUrgency=0.3`(D-4B5-2) | 항상 `Present` |
| ② | `deriveBudgetCapture(recommended: BidAmount?, base: BaseAmount?)` | 4D-1 base 후보율 × 기초금액(1B `RoundingPolicy`, 4B-6)·`Notice.baseAmount` | `clamp01(recommended.bidRateAgainst(base))`(1B 산술만, D-4B5-4); base 없음/≤0 → **Absent(BaseAmountMissing)**(legacy 중립 0.5 를 부재로), recommended 없음 → **Absent(RecommendationMissing)** | `Present` \| `Absent` |
| ③ | `deriveExpectedMargin(inputs: MarginInputs)` | `recommendedRate: Rate`·`floorRate: Rate?`·`predictedRate: Rate`·`priceFitness: UnitScore`·`capacity: UnitScore`(4D-1 `Predicted`: base 후보율·`PriceFitness`; `Notice.floorRate`; 용량 — 부재면 `MarginInputs` 자체를 못 만드니 4B-6 이 `Absent(PredictionMissing)` 로 낸다) | 가중합 `recommendedRate .35 · floorHeadroom .20 · alignment .20 · priceFitness .15 · capacity .10`(합 1): `floorHeadroom = floor>0 ? clamp01((rec−floor)/(1−floor)) : rec`(floor = 1 이면 `rec`), `alignment = clamp01(1 − |rec−pred| / alignmentTolerance(0.12))` | `Present` |
| ④ | `deriveExecutionComplexity(inputs: ComplexityInputs)` | `budget: Money?`·`keywordHits: KeywordHits`·`remaining: Duration?`·`loadRatio: UnitScore`·`match: UnitScore?`·`capacity: UnitScore?` | 가중합 `.30·.25·.15·.10·.10·.10`: budget 밴드(`≥`, 내림) `5억→.92, 2억→.78, 1억→.62, else .38`, `keyword = min(1, keywordBase(.24) + hits×keywordStep(.08))`, deadline 밴드 `6h→1.0, 24h→.78, 72h→.52, ∞→.24`(없음 `noDeadlineComplexity=.3`), `matchFriction = 1−match`, `capacityFriction = 1−capacity`. **budget·match·capacity 부재 → 그 항 제외·재정규화**(4B-4 와 같은 규칙, 무엇이 빠졌는지 `usedSignals` 로) | `Present(score, usedSignals)` — 전부 부재는 구조상 불가(keyword·deadline·load 는 항상 값) |
| ⑤ | `deriveCompetitiveness(bid: Money?, marketAverage: Money?)` | 추천가·시장 평균(4B-6 port — 없으면 Absent) | 밴드 `bid ≤ .8×avg→.95, ≤avg→.75, ≤1.2×avg→.50, else .25`(1B 산술 비교); avg 없음 → **Absent(MarketAverageMissing)**(legacy 는 예산으로 대체 — sentinel 이라 폐기), bid 없음 → `Absent(RecommendationMissing)` | `Present` \| `Absent` |
| ⑥ | `deriveLoadRatio(snapshot: CapacitySnapshot 상당 값 — current: Int, max: Int)` | 4B-2 `CapacitySnapshot`(workflow 타입이라 정수 둘로 받는다) | `min(1, current / max(1, max))` | 항상 `Present` |
| ⑦ | `workloadNotCollected(): ScoreFact<UnitScore>` | — | 상수 `Absent(WorkloadNotCollected)` — 집계 port 는 4B-6 | `Absent` |
| ⑧ | **`DerivationPolicyData`** — 밴드 넷(urgency·complexity-deadline·complexity-budget·competitiveness)·가중치 표 둘(margin·complexity, 합 1)·상수 다섯(`noDeadlineUrgency`·`noDeadlineComplexity`·`alignmentTolerance`·`keywordBase`·`keywordStep`)·competitiveness 비율 둘(.8·1.2). 불변식: 합 = 1·밴드 상한 단조·상수 범위. `EffectiveDatedPolicy` 슬롯 `DERIVATION_POLICY`, 값은 `policy-values.md`(legacy-behavior), **승인 대상 `OPEN-4B5-POLICY-VALUES`** | — | ADR 0010 D-1 관례 | — |

**만들지 않는 것**: Clock·port·텍스트 처리·시장 평균·workload 집계·추천가 산출·조합기 배선(4B-6) · STR-05 · corpus · 4B-4 파일 편집.

---

## 계약 고정 결정 (D-4B5-1~6)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4B5-1** | legacy sentinel(중립 0.5·capacity 0·예산 대체)은 `Absent(reason)` — 4B-4 재정규화가 흡수. 부재 사유는 `MlUnavailableReason` 이 아니라 **별도 sealed `DerivationAbsence`**(ML 미가용이 아니라 fact 부재) | §6.3 · 4B-4 D-4B4-1 | 계약 고정 |
| **D-4B5-2** | 「마감 미공시」는 부재가 아니라 값(0.3 둘) — legacy 별도 상수(`URGENCY_SCORE_UNKNOWN`·`_DEADLINE_MISSING_COMPLEXITY_SIGNAL`)의 의도된 점수. 정책값·승인 대상 | `allocation.py:47-48`·`score_tables.py` | 착수 가정 |
| **D-4B5-3** | 시간 밴드는 `Duration` 비교, 상한 포함(`≤`) — 시 단위 정수 변환 없음(legacy 는 정수 시간; 경계 의미는 같다) | 조사 | 계약 고정 |
| **D-4B5-4** | 금액 비교·비율은 shared-kernel 산술만(`BidAmount.bidRateAgainst(BaseAmount)`·`Money` 비교) — `double`/`BigDecimal` 직접 나눗셈 금지(1B 교차 대입 방지) | 1B · api-type-policy | 계약 고정 |
| **D-4B5-5** | ④ keyword 14개 목록은 **4B-6**(문자열 매칭 소유)의 정책 데이터 — 이 slice 는 `KeywordHits(count ≥ 0)` 만 | 층 분리 | 계약 고정 |
| **D-4B5-6** | ④ 는 부분 부재를 재정규화(4B-4 규칙)하고 ③ 은 전부 필수(부재면 4B-6 이 `Absent(PredictionMissing)`) — legacy ③ 은 predictor 부재 시 각 항을 0 으로 접었으나 그것은 sentinel | §6.3 | 계약 고정 |

---

## 위협 모델 — 4B-5 고유 경계

**방어한다**: (a) sentinel 재유입(①~⑤ Absent 반환, `?:` 로 숫자를 넣는 자리 0) (b) 밴드 경계 오프바이원(경계 양쪽 전수) (c) 가중치 합 ≠ 1·밴드 비단조·상수 범위 밖 정책(⑧ `init`)
(d) `double`·직접 나눗셈 금액 산술(D-4B5-4) (e) 4B-4 입력 타입 밖 값(전부 `UnitScore`) (f) 리터럴 상수(⑧). **방어하지 않는다**: fact 획득·시각·문자열(4B-6) · 값의 옳음(승인·관측) ·
4B-4 조합 · 사다리.

**승인 문면과의 대조**: `milestone-4.md` 4B 「decision 후보 조립」의 입력 조립 — 4B-4 가 조합, 4B-5 가 성분 값. E-4 (a) 「정책 데이터 + 순수 커널로 재작성, 값은 재활용」의 실물.

**우회 후보(≥5)**: (1) base 없음 → 0.5 (2) 밴드 경계 6h 정확히·6h+1ns (3) 합 ≠ 1 표 (4) 밴드 역순·중복 상한 정책 (5) `hits` 음수 (6) `floor = 1` 분모 0 (7) `alignmentTolerance` 리터럴
(8) ④ 전 항 부재(구조상 불가 — keyword·deadline·load 는 항상 값) (9) `Money` 를 `BigDecimal` 로 꺼내 나눔.

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-4D-LADDER-SCORE-SOURCE`(결정 (a), 분해) | 4B-5 = Kotlin 성분 파생 — 잔여 4B-6(조합기·port·합성)·M5 provider·4D-2 |
| **`OPEN-4B5-POLICY-VALUES`**(신설) | ⑧ 값 — 종결 승인 시 확정 |
| **`OPEN-4B5-CORPUS`**(신설) | 밴드·합성 전수 표의 corpus 승격 — 병합 뒤 curator |
| STR-05 | `후속` 그대로 — 슬롯도 만들지 않는다 |
| **`OPEN-4B5-COMPETITIVENESS`**(신설, 계약 갱신 1) | ⑤ 는 이 slice 에서 **구현하지 않는다** — 상수 `Absent(MarketAverageMissing)` 만 |

---

## 계약 갱신 — 2026-09-10 (구현 중, 팀장 등재)

1. **⑤ `deriveCompetitiveness` 제외 — `OPEN-4B5-COMPETITIVENESS` 신설.** 구현 레인이 D-4B5-4 「없으면 멈추고 보고」대로 멈췄다. 실측: (i) shared-kernel `Basis` 에 「시장 평균」
   축이 없어 `marketAverage` 를 `Money` 로 타입화할 생성 경로가 없다 (ii) `Money` 스케일 연산은 `BaseAmount × BidRate` 하나뿐이라 `avg × 0.8`·`avg × 1.2` 를 만들 수단이
   없다 (iii) `compareKnownVat` 는 같은 타입 쌍만 비교한다. 뿌리는 더 깊다 — **V2 는 「투찰 시장 평균」을 수집·정의한 적이 없다**(capability-map 에 그 축이 없고, legacy 는
   `market_data.get("average_bid", budget)` 로 예산을 sentinel 로 썼다). 따라서 이것은 산술의 부재가 아니라 **fact 의 부재**다. 처분: ⑤ 는 `workloadNotCollected` 와 같은
   상수 함수 `competitivenessNotCollected(): ScoreFact<UnitScore> = Absent(MarketAverageMissing)` 만 두고, `DerivationPolicyData` 에 competitiveness 필드를 **만들지 않는다**
   (소비자 없는 죽은 필드 금지). 4B-4 재정규화가 그 성분을 흡수하므로 priority 는 나머지 넷으로 선다. `OPEN-4B5-COMPETITIVENESS` 는 「시장 평균 fact 의 정의·수집(M3
   후속 — 개찰 결과 집계 축)·shared-kernel basis 결정」을 묻는 운영자 항목으로 capability-map §14 에 등재(이 slice 는 등재만).
2. **④ budget 밴드 비교의 산술 — 허용 범위 명시.** 정책 임계(5억·2억·1억)와 `BaseAmount` 의 비교는 basis 를 섞는 산술이 아니라 **같은 basis(기초금액) 안의 크기 비교**다.
   허용: 임계를 `BaseAmount`(또는 원 단위 `Long`)로 정책 데이터에 두고 `BaseAmount.export().won` 크기 비교. 금지(D-4B5-4 그대로): `BigDecimal` 나눗셈·basis 교차·`double`.
   checklist 에 근거 등재.
3. **② `budgetCapture` 의 `RoundingPolicy` 파라미터** — `BidAmount.bidRateAgainst(BaseAmount, origin, Resolution<RoundingPolicy>)` 시그니처에 맞춰 `DerivationPolicyData.budgetCaptureRounding`
   필드를 둔다(1D `FloorShortfallPolicyData.criticalRateRounding` 선례). 값은 policy-values.md 등재·승인 대상.
4. **`DerivationAbsence.MoneyArithmeticUnmeasurable(reason)`** — shared-kernel `Measurement.Unmeasurable` 의 잔여 사유(VAT 불일치 등)를 접지 않고 나르는 가지 하나. 허용.
