# Slice 계약 — M4 / 4B-4 · `priority`·`match` 조합 커널 (decision, 순수)

> **지위**: **착수 계약 2026-09-10.** 운영자 결정 2026-09-10 E-1 (c)·E-2 (c)·E-3 (a)·E-4 (a). 세션 모델 단독 작성.
>
> **왜 커널만인가.** 4D-1 은 client·정책·매핑·release 대조를 한 slice 에 넣어 verifier 4라운드(재작업 3/5)를 썼고, 되풀이 원인은 「기존 골격의 계약을 모른 채
> 가지를 더함」이었다. 4B-4 는 **입력을 값으로 받는 순수 함수와 정책 데이터**만 세운다(1D `FloorShortfallKernel`·4B-1 `VerdictLadder` 와 같은 층). 어느 fact 를
> 어떻게 얻는가(임베딩 port·pgvector kNN·시장 평균·workload)와 use case 배선은 **4B-5**(workflow 조합기 + port + 텍스트 합성 규약)가, 실 client 는 4D-2 가
> 맡는다. `decision` 은 4C-2 lane 이 만지지 않는 모듈이라 병렬이 깨끗하다.
>
> **legacy 산식의 처리(E-4 (a)).** 가중합·penalty·offset 범위의 **값**은 `legacy-behavior` 층으로 재활용하되 **구조**는 정책 데이터 + 순수 커널로 재작성한다.
> 확률 축(legacy `PROBABILITY 0.40`)은 계약에 없으므로(2E D-2E-1·ML-03) 나머지 다섯 가중치를 재정규화한 값이 초기값이다(조사 §7 5 「재정규화」).
>
> **레인 격리.** 다른 세션은 `m4/2026-09-08`(=`main`, `20f7ad0`)에서 4C-2 를 진행 중. 이 slice 는 `20f7ad0` 에서 가른 브랜치 `m4-4b4/2026-09-10`(worktree
> `bid-vector-v2-m4e`). 공유 파일은 `config/quality/gate-tests.properties`(`gate.tests.decision` 키)·`milestone-4.md` 뿐.

```yaml
milestone: m4
slice: 4b4-priority-composition-kernel
base_sha: 20f7ad0041de5e167c49bd00d9fdc00220711b56   # 2E 병합 커밋 = m4/2026-09-08 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다.
branch: m4-4b4/2026-09-10
in_scope:
  - decision/src/main/kotlin/bidvector/decision/priority/**      # Component(enum 5)·ScoreFact(Present|Absent)·PriorityInputs·PriorityPolicyData + PRIORITY_POLICY·composePriority(순수)·PriorityOutcome(Composed|Unavailable)·UnitVector·SemanticMatch(MatchOutcome)
  - decision/src/test/kotlin/bidvector/decision/priority/**      # 전수 표·재정규화 property·부재 처리·순서 무관·단조·경계·정책 불변식·코사인 property·enum 소진
  - strategy/src/main/kotlin/bidvector/strategy/Score.kt         # `ProbabilityScore` KDoc 정정만(E-3 (a))
  - config/quality/gate-tests.properties                         # gate.tests.decision 등재만
  - milestone-4.md                                               # 4B 절 4B-4 문단(착수·종결)
  - reports/evidence/m4/4b4/**                                   # + policy-values.md(legacy-behavior 층 값 표, 승인 대상)
out_of_scope:
  - workflow/** · adapters/**                                    # 4B-5(port·조합기·합성 규약)·4D-2(client)
  - decision 의 기존 커널(VerdictLadder·LadderInput·ReviewReason 등) # 4B-1·4B-3·4D-1 종결 — 편집 0
  - 성분 값의 **산출**(마감→urgency, 금액→budgetCapture, 4D-1 Predicted→expectedMargin, 텍스트→complexity, 시장 평균→competitiveness, workload)   # 4B-5 파생 함수
  - 사다리 임계(LadderPolicySlot)·확률 축·Platt(OPEN-ML-02) · `similarity` 성분(legacy priority 가중합에 없다 — 조사 §1.1)
  - fixtures/** · app/**                                         # corpus 는 병합 뒤 curator(OPEN-4B4-CORPUS)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — decision 은 domain 층: domainSourceReferenceGate·moduleDependencyGate(shared-kernel 만)·api-type-policy
  - "./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'"                    # S-2
  - "./gradlew --no-daemon :decision:test"                                                             # S-3 — 4B-1·4B-3 test 무변경
  - "./gradlew --no-daemon :strategy:compileKotlin :strategy:test"                                     # S-4 — KDoc 만 바뀜
  - "./gradlew --no-daemon :decision:gateExecutionGate"                                                # S-5
  - "./gradlew --no-daemon :app:test --tests '*Conformance*'"                                         # S-6 — corpus 무영향
  - "./gradlew qualityBaseline"                                                                        # S-7
rollback: |
    **정본은 `reports/evidence/m4/4b4/rollback.md`**. 신설 패키지 둘(main·test) 삭제 + `Score.kt`·`gate-tests.properties`·`milestone-4.md` 줄 단위(최신→과거 hunk).
    병합 전에는 「브랜치를 버린다」.
```

근거: `milestone-4.md` 4B · `data-dictionary.md` §6.1(수학 커널 = Python / 업무 판정 = Kotlin)·§6.3(sentinel 금지) · `capability-map.md` DEC-06·ML-03·ML-09 ·
2E `embedding.proto`(L2 벡터·dimension) · 4B-1 `LadderInput`(`priorityScore`·`matchedScore` 소비자) · 조사 노트 `_workspace/m2-2e/01_scout_opportunity_scoring.md`
§1.1·§1.3·§7 · 운영자 결정 2026-09-10 · `_workspace/m4-4b4/02_design-review.md`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 20f7ad0041de5e167c49bd00d9fdc00220711b56..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`PriorityInputs`** — 성분 다섯 `match`·`urgency`·`competitiveness`·`budgetCapture`·`expectedMargin`(각 `ScoreFact` = `Present(UnitScore) \| Absent(reason)`) + penalty 입력 `loadRatio`·`workload`·`complexity`(각 `ScoreFact`). **`match` 만 필수** — Absent 면 `PriorityOutcome.Unavailable(MlUnavailableReason)`(임베딩 없이는 priority 를 만들지 않는다, 4B-3 경로로 흐른다); 나머지 부재는 재정규화 | 조사 §1.1 여섯 가중합 − 확률 축 · E-1 (c) · §6.3 sentinel 금지 |
| ② | **`PriorityPolicyData`** — `weights: Map<Component, BigDecimal>`(전 값 키 존재·합 = 1 불변식; 초기값 legacy 0.23/0.14/0.08/0.06/0.09 ÷ 0.60), `loadPenalty(ratioWeight 0.18, workloadWeight 0.12)`, `complexityPenalty(threshold 0.55, slope 0.18, cap 0.12)`, `categoryOffsetRange ±0.20`, `normEpsilon`. `EffectiveDatedPolicy` 슬롯 `PRIORITY_POLICY`. 값은 `policy-values.md`(legacy-behavior 층: `allocation.py:38-43`·`:505-532`, `operator_strategy_tuning.py:16-17`), **승인 대상 `OPEN-4B4-POLICY-VALUES`** | ADR 0010 D-1 관례 · NOTI-01 「임계값이 코드 상수에 있는 형태 채택 안 함」 |
| ③ | **`composePriority(inputs, policy): PriorityOutcome`** 순수 — `Present` 성분만 가중합하고 그 성분들의 가중치 합으로 나눠 재정규화 → penalty 는 입력이 `Present` 인 항만(부재면 그 항을 안 센다 — 「모르면 깎지 않는다」가 아니라 「모르면 그 항이 없다」, KDoc 근거) → clamp01 → `UnitScore`. 결과 `Composed(priority, usedComponents, droppedComponents, appliedPenalties)` — 무엇이 빠졌는지 값으로 남긴다 | 조사 §1.1 산식 · 4B-2 「탈락은 값으로」 관례 |
| ④ | **`SemanticMatch.of(notice: UnitVector, profile: UnitVector, categoryOffset: BigDecimal, policy): MatchOutcome`** — `Matched(UnitScore)` \| `DimensionMismatch` \| `OffsetOutOfRange`. 코사인 = 내적(L2 전제), offset 은 정책 범위 밖이면 clamp 가 아니라 **관측 가능한 거부**. `UnitVector(values: DoubleArray)` 생성 불변식: 차원 > 0·norm 1±ε(정책 ε) | 조사 §2 `matched_score = clamp01(score + offset)` · 2E ② L2 · 4D-1 G-1 교훈(판정은 `init` 이 아니라 결과 sealed) |
| ⑤ | **`ProbabilityScore` KDoc 정정** — 「가격 적합도(추정) 축 — P(낙찰)이 아니다(ML-03·D-M2-8). 계약(2E)에 확률 축이 없어 현재 소비자 없음; `OPEN-ML-02` 결정 뒤 `calibrated_win_rate`」 | E-3 (a) |

**만들지 않는 것**: 성분 산출 · port · use case 배선 · 임계 · 확률 축 · `similarity` 성분 · corpus · 벡터의 shared-kernel 승격.

---

## 계약 고정 결정 (D-4B4-1~5)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4B4-1** | 부재 성분은 재정규화(가중치 합으로 나눔). 성분이 `match` 하나뿐이면 priority = match 이고 그 사실을 `droppedComponents` 가 나른다 | §6.3 sentinel 금지 · 조사 §7 5 | 계약 고정 |
| **D-4B4-2** | `match` 부재는 `Unavailable` — 임베딩 없이는 priority 를 만들지 않는다(fail-safe 방향) | 4B-3 경로 · ADR 0010 D-6 | 계약 고정 |
| **D-4B4-3** | 벡터 산술은 `Double`(계약이 float, 정밀도 요구 없음) — `UnitScore` 로 나갈 때 `BigDecimal` 변환, scale 은 정책 값. 1B 금액 규율(`double` 금지)과 축이 다름을 KDoc 에 | api-type-policy 가 domain 공개 API 의 부동소수 저장을 막는지 **착수 시 실측** — 막으면 `UnitVector` 를 `internal` + `List<BigDecimal>` 입력으로 | 착수 가정 — 게이트 실측 뒤 확정 |
| **D-4B4-4** | 가중치·penalty·offset 초기값은 legacy 재정규화 — 승인 대상 | ADR 0010 D-1 「보수적 + 측정 의무」 | 착수 가정 |
| **D-4B4-5** | `similarity` 는 성분이 아니다 — legacy `priority` 가중합 여섯 항에 없다(probability blend 안에만 있었다). 4B-5 가 필요하면 정책 version 으로 성분을 더한다(enum 값 추가 = 소진 `when` 갱신) | 조사 §1.1·§1.2 | 계약 고정 |

---

## 위협 모델 — 4B-4 고유 경계

**방어한다**: (a) 부재 성분의 sentinel 0(①③) (b) 가중치 합 ≠ 1·전사상 누락 정책(②) (c) 확률 축의 몰래 유입(`Component` enum 에 없음 — 소진 `when`) (d) 비정규·차원 불일치 벡터의 코사인(④)
(e) offset 무한·조용한 clamp(④) (f) 조합 결과에서 「어느 성분이 빠졌나」가 사라짐(③) (g) `match` 없이 priority 가 나오는 것(①). **방어하지 않는다**: 성분 값의 옳음·산출(4B-5) ·
가중치 값의 옳음(승인·관측) · 사다리(4B-1) · 임베딩 품질(M5) · 빌드 스크립트를 임의로 쓰는 저자.

**승인 문면과의 대조**: `milestone-4.md` 4B 「decision 후보 조립」의 입력 조립 축 — 4B-1(사다리)·4B-2(조합 use case)·4B-3(미가용) 뒤 남은 「점수를 어디서 얻는가」의 Kotlin 몫.

**우회 후보(≥5)**: (1) Absent 를 0 으로 → property(수동 계산 대조) (2) 합 1.0001 정책 → 생성 실패 (3) `Component.Probability` 추가 → 소진 `when` 컴파일 (4) 차원 다른 벡터 → `DimensionMismatch`
(5) offset 0.5 → `OffsetOutOfRange` (6) 전부 Absent → `Unavailable`(0 이 아님) (7) 가중치 순회 순서 의존 → 셔플 property (8) penalty 부재를 0 penalty 로 → `appliedPenalties` 대조.

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-4D-LADDER-SCORE-SOURCE`(결정 (a), 분해) | 이 slice 가 Kotlin 조합 커널 몫 — 4B-5(port·합성 규약)·M5 provider·4D-2 가 잔여 |
| **`OPEN-4B4-POLICY-VALUES`**(신설) | ② 값 — 종결 승인 시 확정 |
| **`OPEN-4B4-CORPUS`**(신설) | 조합·코사인 전수 표의 corpus 승격 — 병합 뒤 curator |
| `OPEN-ML-02`·`OPEN-4B1-LADDER-THRESHOLDS` | 경계 밖 |
