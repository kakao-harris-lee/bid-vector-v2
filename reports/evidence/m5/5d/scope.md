# Slice 계약 — M5 / 5D · inference kernels — **초안, 구현 전**

> **지위**: M2 진행 중 세션 모델이 쓴 초안. 착수는 5A(·5B 변환) 뒤 운영자 지시. 커널 이식은 M2 와 독립이나 출력 어휘는 2B 와 같은 형태(후보 3·
> 성분 셋·`Unmeasurable` 두 사유)로 둔다 — wire 매핑은 5E.

```yaml
milestone: m5
slice: 5d-inference-kernels
base_sha: 040ab9d   # 초안 앵커 — **5A 승인 뒤 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/inference/**              # 커널 이식: reserve_draw_distribution · assessment_shrinkage · award_margin_distribution(반사 KDE+Silverman) · settlement_maturity(계산만) · LightGBM predict adapter · scenario(후보 3) · 결과 타입
  - ml-engine/src/ml_engine/registry/**               # artifact 로드·checksum 검증·feature_names 대조(불일치 = fail-closed) — 5C 가 쓰는 manifest 는 2C 형태
  - ml-engine/tests/inference/**                       # property(수학 불변식)·경계(최소 표본·singular·NaN/Inf)·golden(D-M5-7)·legacy 회귀 대조(`legacy-behavior`)
  - ml-engine/tests/gates/**                          # 래칫 갱신(이식 코드 동일 적용)
  - reports/evidence/m5/5d/reuse.md                   # 재활용 출처 표(ADR 0009)
  - fixtures/manifest.yaml, fixtures/input/**, fixtures/expected/**   # 조건부 — D-M5-7 (a) 신설 case(curator)
  - milestone-5.md, reports/evidence/m5/5d/**
out_of_scope:
  - features/ 변환(5B) · training/evaluation(5C) · serving(5E)
  - win-proxy 커널(`award_landing_*`) — D-M5-8 결정 전
  - 업무 법정 하한·자격·최종 결정(Kotlin decision·qualification) · `review_required`·guardrail 10필드(조사 01 (b-5))
  - Platt 캘리브레이션의 자격 라벨(D-M2-11 (a))
  - 합성 `confidence`(§6.5 「사전에 올리지 않는다」)
  - contracts/** 내용(M2)
acceptance_commands:
  - "(cd ml-engine && uv sync --frozen --all-extras && lint-imports && ruff check . && mypy --strict src/ml_engine)"   # S-1
  - "(cd ml-engine && python -m pytest tests/inference -q)"                                            # S-2 — property·경계·golden
  - "(cd ml-engine && python -m pytest tests/gates -q && python tools/design_ratchet.py --check)"      # S-3 — 래칫(이식 코드 포함)
  - "(cd ml-engine && python -m pytest tests/inference -q -m legacy_parity)"                           # S-4 — legacy 출력 대조(정밀도 명시) — **판정 근거 아님**, 회귀 관측
  - "./gradlew :app:test --tests '*Conformance*'"                                                      # S-5 — 조건부(fixture 신설 시 runner 가 ml-engine 을 부르지 않으므로 해당 없음 — 5D corpus 는 pytest golden 으로만; 여기서는 Kotlin 쪽 무영향 확인)
rollback: |
    **정본은 `reports/evidence/m5/5d/rollback.md`**(착수 시). inference/·registry/ 를 걷으면 5A/5B 상태.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-5.md` 5D·완료 조건 · `v2-지침서.md` §3.2·§4.4 · `capability-map.md` ML-01~ML-05·ML-09·ML-11 · `data-dictionary.md` §6.3~6.5 ·
`ADR 0001` D-6 · `ADR 0009` · M2 `2b/scope.md` ③~⑦(출력 형태) · 조사 노트 `_workspace/m2-prep/01_scout_ml_interface.md` (b)·(c) · `_workspace/m5-prep/01_scout_ml_package.md`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **순수 커널 이식(수학 유지, 결합 제거)** — `reserve_draw_distribution`(4/15 추첨 닫힌식) · `assessment_shrinkage`(계층 수축 사후분포) · `award_margin_distribution`(반사 보정 KDE + Silverman) · `settlement_maturity`(성숙도 **계산**만 — 판정은 Kotlin, §6.4). 각각 원본 경로·commit 을 docstring 포인터로, 수정 내역은 `reuse.md`. `dict[str, Any]` 경계 0 — 입력·출력은 dataclass | ML-04·ML-11.1 · `OPEN-ML-01` 해소 「수학 커널 = ml-engine」 · ADR 0009 |
| ② | **LightGBM predict adapter** — artifact 의 `feature_names`·`categories`·`denominator_sources` 와 요청 피처의 **정확 일치**가 아니면 `Unavailable`(fail-closed, legacy 유지) · 미학습 공종(학습 행 수 임계 미만)은 **`UNTRAINED_SEGMENT`**, 표본 얕음은 **`INSUFFICIENT_SAMPLES`** — 다른 값, **설정으로 끌 수 없음**(임계 `max(1, …)` 클램프 형태 유지) · 가용성 게이트와 예측 경로가 같은 판정 함수 | ML-02 acceptance 셋 · M2 D-2A-3 |
| ③ | **결과 타입** — `KernelResult = Success(candidates[3], fitness, uncertainty, diagnostics) | Unmeasurable(reason)`. **예외로 실패를 나르지 않는다**; `except Exception → 다른 predictor 폴백`(legacy c-2) 없음. 후보 3 은 `CONSERVATIVE·BASE·AGGRESSIVE` 순서 고정, 율은 `Decimal`(fraction)로 — float 는 커널 내부까지만, 경계에서 `Decimal` 로 한 번 | ML-01 · M2 2B ③·④ · ADR 0001 D-6 · ADR 0010 D-3 제3 변환 금지 |
| ④ | **불확실성 성분 셋 + 출처** — `sample_size`(그 추정에 실제로 쓰인 표본)·`dispersion`·`estimate_margin` + `interval_source ∈ {CROSS_VALIDATION_RESIDUAL, TIME_HOLDOUT_RESIDUAL}`. 합성 `confidence`(계수 아홉 아핀 결합·[0.45,0.95] 클램프) **이식하지 않음** | §6.5 · ML-03 「표본 부족이 confidence 0 이 아니라 사유 있는 측정 불가」 · H-4 |
| ⑤ | **경계 거동** — 최소 표본 미만 → `INSUFFICIENT_SAMPLES`; singular(분산 0)·NaN·Infinity 입력 → `Unmeasurable(FEATURE_ABSENT|DEGENERATE)`(예외 아님); `0` 원·음수 금액은 커널 입력에서 거부(legacy `max(amount, 1.0)` 바닥 **불채택**). 각각 property test | 5D 「최소 표본, singular input, NaN/Infinity 처리」 · 조사 01 (c-3) |
| ⑥ | **objective 별 후보·diagnostics** — `OptimizationObjective`(2B D-2B-4, 초기 `SCENARIO_TRIPLE` 하나)에 따라 시나리오 z(±1.2816)·가중치(0.24/0.52/0.24)는 **versioned policy 데이터**(legacy 릴리스 상수 → D-M5-6 분류)이고 응답이 policy version 을 싣는다. diagnostics 는 타입 있는 필드만(`training_row_count`·`segment_support`) | 5D 「optimization objective 별 후보와 diagnostics」 · M2 2B ⑦ · §5 매직넘버 |
| ⑦ | **golden corpus** — D-M5-7 (a): ML-02·03·04 acceptance 문면에서 curator 가 `authored-from-approved-spec` case 신설(입력 표본·기대 후보/사유·정밀도). legacy 출력 대조는 `legacy_parity` 마커로 분리(판정 아님) | §3.2 「기대값은 승인된 명세와 authoritative fixture」 |

**만들지 않는 것**: 변환(5B) · 학습(5C) · servicer(5E) · 가격 산출(Kotlin) · 임계치 소비 사다리(M4 4B) · win-proxy · 합성 신뢰도 · 폴백.

---

## 운영자 결정 필요 — 착수 전(`prep` D-M5-5·7·8) · 계약 고정(D-5D-1~4)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5D-1** | 커널 내부 수치는 `float`/numpy, **경계(입력 검증 뒤·출력 조립 전)에서 `Decimal` 한 번** — 정밀도(scale)는 정책 데이터. 2A D-2A-5 「`double` 은 계약 어디에도 없다」의 Python 쪽 실현 | M2 D-M2-6 · §9 재현성 | 계약 고정 |
| **D-5D-2** | `Unmeasurable` 사유 enum 은 **2A `UnmeasurableReason` 과 같은 이름·값**(5E 매핑이 항등) — 5D 가 값을 더 필요로 하면 2A 에 호환 추가(제공자 먼저) | 2A D-2A-3 | 계약 고정 |
| **D-5D-3** | 미학습 가드 임계는 policy 데이터이되 **하한 1 클램프는 코드 불변식**(끌 수 없음) | ML-02 「설정으로 비활성화할 수 없다」 | 계약 고정 |
| **D-5D-4** | 결정성 — `num_threads` 고정·seed 는 학습(5C) 소유; 5D predict 는 스레드 수와 무관하게 같은 답임을 test(LightGBM predict 결정성) | ML-05 · 조사 01 (d) | 계약 고정 |

---

## 위협 모델 — 5D 고유 경계

**방어한다**: (a) 실패의 폴백 접힘(③ 결과 타입 — 예외 경로 없음, `except Exception` 금지는 ruff `BLE001`) (b) 두 사유의 합침(② enum 분리 test) (c) 가드 비활성화(D-5D-3 불변식 test — 임계 0 을 줘도 1) (d) 피처 순서 불일치의 조용한 통과(② fail-closed test) (e) 합성 신뢰도 재유입(④ 필드 부재 + 리뷰) (f) `dict[str, Any]` 경계 재유입(래칫 0) (g) 매직넘버(⑥ 정책 데이터 부재 시 구성 실패).
**방어하지 않는다**: 수학의 옳음 자체(재활용 — 승인 명세·golden 이 판정) · 학습 데이터 편향(5C·ML-07) · wire 매핑(5E) · 정책 값 내용.

**우회 후보(≥5)**: (1) `Unmeasurable` 대신 후보 3 을 `0` 율로 → `Candidate.bid_rate > 0` 불변식 (2) 예외를 잡아 `Success` 로 → `BLE001` + 결과 타입 test (3) 임계를 정책에서 `0` → 클램프 (4) `feature_names` 대조를 부분집합으로 → 정확 일치 test(순서 포함) (5) NaN 을 `0.0` 으로 치환 → 경계 property test (6) `confidence` 를 diagnostics 에 실음 → diagnostics 는 닫힌 dataclass, 필드 추가는 리뷰 (7) legacy parity 를 판정 test 로 승격 → 마커 분리·완료 조건 문면.

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- M2 조사 01 (b)~(c): 후보 3·인덱스 1 기준·`confidence` 클램프·(c-2) 폴백·(c-3) 값 접힘 — ③④⑤ 근거.
- 대기(조사 노트 `_workspace/m5-prep/01_scout_ml_package.md` (a)·(d)·(f)·(g)).

---

## OPEN — 수령·신설

| OPEN | 5D 처리 |
| --- | --- |
| `OPEN-ML-03` | `PriceFitness` 타입 ≠ 확률 — Python 쪽 타입 분리(NewType) 로 승계 |
| `OPEN-ML-05` | ⑥ 시나리오 상수·임계 → 정책 데이터(D-M5-6 분류) |
| `OPEN-ML-06` | 범위 밖(D-M5-8) |
| `OPEN-SET-06`(성숙도 embargo 임계) | 5D 는 계산만 — 값·판정은 Kotlin·그 OPEN |
