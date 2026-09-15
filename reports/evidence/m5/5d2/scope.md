# Slice 계약 — M5 / 5D-2 · 분포 엔진 조립(predictor = 분포 단독) — **착수 계약 2026-09-13**

> **지위**: 운영자 결정 2026-09-13(「5D 종결·5D-2 착수 진행」, 추천안: D-5D2-1 (b) 분포 단독 · D-5D2-2~7 · `agency_sample_threshold` 값은 (c) OPEN). `OPEN-5D-DISTRIBUTION-ENGINE`
> (5D verifier r1 범위 판정 — K5·K6 → 후보 3 을 잇는 조립 자리가 5D in_scope 에 없음)을 닫는 소형 slice. 조사 digest `_workspace/m5-5d2/01_digest.md`(legacy `distribution.py`·
> `distribution_extraction.py`·`orchestration.py`·M2 D-2B-3·5D 실물·정책 표·golden 010/011). 세션 모델 단독 작성. **Phase 2.5 설계 검토 대상**(fail-closed·결과 타입): `_workspace/m5-5d2/02_design-review.md`.
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m5-5d2/2026-09-13`, base = 5D 종결 head(PR #12 head; PR #12 머지 뒤 `origin/main` 흡수). `ml-engine/**` 만.

```yaml
milestone: m5
slice: 5d2-distribution-engine
base_sha: cefb19c9269ac3b29d0246175fb04d71c04aa3d4   # 5D 종결 커밋 = PR #12 head
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/inference/observations.py       # wire CompetitionSample → ReserveDrawSample | SampleRejected(reason) — 관문: base_amount(Money 5성분, 5B facts 규칙 재사용) · reserve_prices 정확히 policy.reserve.expected_price_count(15) · 각 가격 > 0 · center = draw_mean_moments(ratios).mean 이 policy.assessment.plausible 밴드 안 · selected_numbers ≥ 2(1-기반, 범위 안)면 실현 사정률(진단용, 부족하면 `Missing` — 거부 아님, 설계 검토 (1)) · observed_bid_rate 가 policy.bid_ratio.plausible 밴드 안 · provenance 라벨 미러(AssessmentProvenance). 제외는 사유별 계수
  - ml-engine/src/ml_engine/inference/distribution.py       # 조립기 predict_distribution(request: DistributionRequest, policy) -> Success | Unmeasurable — admit_clean → 행마다 K6 draw_mean_moments → aggregate_level_observation ×3(agency/category/global) → K5 resolve_assessment_posterior → predictive_std = sqrt(posterior.std² + mean(draw_var)) → bid_ratio = median(observed_bid_rate_i / center_i) → build_scenario_candidates(center = posterior.mean, std, scale = bid_ratio) · Uncertainty(sample_size = clean 관측 행 수, dispersion = 비율 표본 std, estimate_margin = z·predictive_std/√n, interval_source = TIME_HOLDOUT_RESIDUAL?? → D-5D2-8) · Diagnostics(training_row_count = 0, segment_support, shrinkage_weight = level_weights.agency, excluded_observations)
  - ml-engine/src/ml_engine/inference/availability.py       # distribution_availability(observation_count, ratio_sample_count, policy) -> Available | Unmeasurable — 가용성 게이트와 조립기가 같은 함수(D-5D2-7)
  - ml-engine/src/ml_engine/inference/engine.py             # 진입점 serve_bid_rates(request, policy) → 분포 엔진 하나(D-5D2-1 (b)); GBM predict_bid_rates 는 연결하지 않음(코드 상수 ENGINE = DISTRIBUTION — D-5D2-2)
  - ml-engine/src/ml_engine/inference/policy.py             # 슬롯 추가: assessment.agency_sample_threshold(값 없음 — Optional? 아님: 필수 키이되 값은 OPEN-5D2-POLICY-VALUES 승인 전 YAML 에 넣지 않고 로더가 「미선언」을 PolicyRejected 로 — D-5D2-3) · 기존 reserve.*·bid_ratio.*·assessment.plausible_* 여섯 슬롯이 소비처를 얻음
  - ml-engine/src/ml_engine/inference/results.py            # DistributionRelease(내부 타입, D-5D2-5) · Diagnostics.agency_sample_count·agency_sample_below_threshold 추가(golden 011)
  - ml-engine/policy/inference-v1.yaml                      # agency_sample_threshold 는 값 승인 뒤 추가(그 전까지 test 는 case 정책 주입) — 이 slice 에서 YAML 편집 없음이 기본
  - ml-engine/tests/inference/{test_observations,test_distribution,test_availability,test_engine}.py · tests/inference/golden(011 skip 해제 — verified_paths 만)
  - milestone-5.md(5D-2 착수 문단), reports/evidence/m5/5d2/**(policy-values = OPEN 표)
out_of_scope:
  - historical predictor·ensemble(V2 계약에 입력 없음 — legacy 실제 기본 경로였으나 V2 에 존재하지 않음) · GBM 서빙 연결(ML-05 게이트 미통과) · `bid_to_assessment_ratio` 환산(D-5D2-6) · 합성 confidence · 폴백(ADR 0001 D-6) · wire 변경(`Diagnostics` 필드·`ModelRelease` 의미 — 2F/5E) · servicer(5E) · Kotlin
acceptance_commands:
  # 정본 = CI ml-engine job 전건(5D 와 동일)
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                 # S-1
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c \"import $m\" || exit 1; done && uv sync --frozen --all-extras)"   # S-1b
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4
  - "(cd ml-engine && uv run python -m pytest tests -q -m 'not legacy_parity')"                       # S-5 — golden 011 포함 14/14
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7
  - "(cd ml-engine && uv run python -m pytest tests -q -m legacy_parity)"                             # S-8 — 관측 전용
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
rollback: |
    **정본 `reports/evidence/m5/5d2/rollback.md`**(구현 레인). 신설 파일 삭제 + `policy.py`·`results.py`·`__init__.py`·golden test hunk 격리(5D 소유 파일). 문서 레인(milestone-5·scope·policy-values) 목록 제외.
    임시 clone 실측 → in_scope diff 0 → `pytest tests -q -m 'not legacy_parity'`(5D 347+1 skip 복귀).
```

작성: 2026-09-13, 세션 모델 단독. 근거: `milestone-5.md` 5D 「objective 별 후보와 diagnostics」·완료 조건 · `capability-map.md` ML-01·ML-03·**ML-04**(두 acceptance)·ML-05(게이트 미통과) · `data-dictionary.md` §6.5 · ADR 0001 D-4·**D-6**(조용한 폴백 금지) · 2B ①·③~⑦·**D-2B-3**(`CompetitionSample`·`ReserveDrawObservation` 은 분포 predictor 때문)·D-2B-6 · 5D D-5D-1~11 · 5A 정책 표 #24~#29 · golden `ml-kernel-010`·`011`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **관측 정제**(`observations.py`) — wire `CompetitionSample` 한 건 → `ReserveDrawSample(center, draw_std, realized_assessment_ratio?, observed_bid_rate, provenance, agency, category)` 또는 `SampleRejected(reason ∈ {BaseAmountInvalid, PriceCountMismatch, NonPositivePrice, CenterOutOfBand, BidRateOutOfBand, NoReserveDraw})`(추첨번호 부족은 거부가 아니라 실현 사정률 `Missing` — 설계 검토 (1)). legacy `observe_reserve_draw` 의 관문 넷 + 밴드 둘을 **결과 타입**으로(None 반환·조용한 skip 제거), 제외는 사유별 계수 → `Diagnostics.excluded_observations` | 5D ⑤ 「조용한 drop 금지」 · 조사 c-3 |
| ② | **조립기**(`distribution.py`) — legacy `_estimate_distribution` 흐름 그대로: `admit_clean`(5D) → K6 `draw_mean_moments`(정제된 ratios, `policy.reserve.draw_count`) → 3계층 `aggregate_level_observation` → K5 `resolve_assessment_posterior`(`policy.assessment.*`) → `predictive_std = sqrt(posterior.std² + mean(draw_var))` → `bid_ratio = median(observed_bid_rate_i / center_i)`(D-5D2-6 — 축 섞지 않음) → `build_scenario_candidates(center=posterior.mean, std=predictive_std, scale=bid_ratio, policy)`. `Diagnostics.shrinkage_weight = posterior.level_weights.agency`(ML-04 ②), `segment_support = DIRECT(agency 관측 ≥ 1) \| PARENT_CATEGORY \| GLOBAL`, `agency_sample_count`, `agency_sample_below_threshold`(D-5D2-3). 모든 실패는 `Unmeasurable(reason, detail)` | ML-04 ① ② · ML-01 · §6.5 · 2B ③~⑦ |
| ③ | **가용성 = 추정 같은 함수**(`availability.py`, D-5D2-7) — `observation_count < policy.reserve.min_reserve_records(8)` → `Unmeasurable(INSUFFICIENT_SAMPLES, TOO_FEW_OBSERVATIONS)` · `ratio_sample_count < policy.bid_ratio.min_samples(3)` → `(INSUFFICIENT_SAMPLES, TOO_FEW_RATIO_SAMPLES)`. 조립기가 이 함수를 호출하고 진입점도 같은 함수(legacy 「availability 에서만 검사, 직접 호출은 표본 1건도 통과」 결함 제거) | ML-02 ② 의 분포 판 · 5A 표 #28·#29 |
| ④ | **진입점**(`engine.py`) — `serve_bid_rates(request: DistributionRequest, policy) -> KernelResult`. 엔진은 분포 하나(D-5D2-1 (b)); `predict_bid_rates`(GBM) 는 import 하지 않음(연결 없음이 코드로 보임). 선택 축·폴백 없음 | D-2B-3 · ADR 0001 D-6 · ML-05 |
| ⑤ | **정책 슬롯**(D-5D2-3) — `assessment.agency_sample_threshold: int ≥ 1` 필수 키. **값은 `OPEN-5D2-POLICY-VALUES`**(legacy 대응 상수 없음, 운영자 (c): 5C 재학습 지표 뒤 결정) — 승인 전 `inference-v1.yaml` 에 넣지 않으며 로더는 미선언을 `PolicyRejected(MissingKey)` 로 낸다(서빙은 5E 가 켜는 시점에 값이 있어야 한다는 사실이 로더에서 드러남). test 는 case 정책(golden 011 synthetic 10)을 주입 | ADR 0006 D-7 · 5D D-5D-8 |
| ⑥ | **golden 011 해소** — `tests/inference/golden` 의 011 skip 제거, `verified_paths` 만 단언(`diagnostics.segmentSupport`·`agencySampleCount`·`agencySampleBelowThreshold`·`shrinkageWeight.fraction`·`shrinkageWeightCarriedInResponse`·`levelWeights.*`·`levelWeightSum`·`posteriorMean`·`effectiveSampleCount`·`posteriorStd`) | D-M5-7 (a) · `OPEN-5D-GOLDEN` |

**만들지 않는 것**: GBM 연결 · historical/ensemble · 환산 계수 이식 · confidence · 폴백 · wire 필드 · YAML 값 신설(승인 전) · servicer.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5D2-1** | 서빙 엔진 = **분포 단독**. GBM 은 5D 이식물 그대로 두되 진입점에 연결하지 않음 | D-2B-3(계약이 `CompetitionSample` 을 넣은 유일 사유) · historical 은 V2 계약에 입력 없음 · ML-04 ①② 도달 경로 · D-6 폴백 없음 · ML-05 「게이트 미통과」 | **확정 (b) 2026-09-13** |
| **D-5D2-2** | 선택 축을 정책 파일에 넣지 않음(5A 표 #24·#25 「환경/5E」 유지). 엔진이 하나라 `ENGINE = DISTRIBUTION` 코드 상수. `#27 ENSEMBLE_MIN_SAMPLES` 는 소비처 없음 → 5A 표 배정 「미이식」 정정(팀장 문서 레인) | digest §5 | 확정 |
| **D-5D2-3** | `assessment.agency_sample_threshold` 키 신설, **값은 OPEN**(운영자 (c)). 로더는 미선언 → `PolicyRejected` | golden 011 · legacy 대응 없음 | 확정(값 대기) |
| **D-5D2-4** | `Uncertainty.sample_size` = clean 관측 행 수(추정에 실제 쓰인 표본), `dispersion` = 비율 표본(`observed_bid_rate_i / center_i`) 표준편차, `estimate_margin` = `z · predictive_std / √sample_size`(신뢰구간 반폭 — §6.5 정의; legacy 의 「std 자체」 재산출 분기는 이식하지 않음, `sample_size < min_samples_for_variance` 는 5D 규칙대로 `Unmeasurable`) | D-2B-6 · §6.5 | 확정 |
| **D-5D2-5** | `Success.release`(`ModelRelease` 필수) — 아티팩트 없는 엔진: 내부 `DistributionRelease(policy_version, code_version, method="reserve-draw-distribution")` 만 두고 **wire `ModelRelease` 매핑은 5E/2F**(`OPEN-5D2-RELEASE-FOR-DISTRIBUTION`) | 2B ⑥ 필수 필드와 충돌 — 계약이 답 없음 | 확정(OPEN) |
| **D-5D2-6** | `bid_to_assessment_ratio` 환산 **이식하지 않음** — legacy 자신이 「basis 혼재로 근사·캘리브레이션 미검증」. M2 가 `observed_bid_rate`(투찰율 축)를 따로 나르므로 `bid_ratio = median(observed_bid_rate_i / center_i)` 로 축을 섞지 않음 | ADR 0001 D-4 · `distribution_extraction.py:103-117` | 확정 |
| **D-5D2-7** | 최소 표본 임계는 가용성·추정이 **같은 함수** | ML-02 ② 유추 · legacy 결함 | 확정 |
| **D-5D2-8** | `Uncertainty.interval_source` — 분포 엔진은 잔차 기반이 아니라 사후분산 기반이어서 wire 두 값(`CROSS_VALIDATION_RESIDUAL`·`TIME_HOLDOUT_RESIDUAL`) 어느 것도 맞지 않음 → **wire 값을 지어 채우지 않는다**: 내부 `IntervalSource.POSTERIOR_PREDICTIVE` 추가, wire 매핑은 5E/2F additive(`OPEN-5D2-INTERVAL-SOURCE-WIRE`) | 2B D-2B-6 · §6.5 | 확정(OPEN) |

---

## 위협 모델 — 5D-2 고유 경계

**방어한다**: (a) 비-CLEAN 유입 — `admit_clean` 재사용 (b) 조용한 drop — 사유별 계수 (c) 폴백 — 없음(엔진 하나) (d) 임계 우회 — 같은 함수 (e) 관문·밴드 밖 관측 — 결과 타입 (f) 분산 0·NaN — 5D K6 결과 타입 전파 (g) 엔진 선택을 요청이 좌우 — objective 로 표현하지 않음 (h) 값 지어내기 — `ModelRelease`·`interval_source` 를 wire 값으로 위장하지 않음(OPEN 둘) (i) 승인 없는 정책 값 — 임계 키 미선언은 `PolicyRejected`.
**방어하지 않는다**: 분포 수학의 옳음(golden·승인 명세) · wire 매핑(5E) · 정책 값 내용 · Python 가시성.

**우회 후보(≥5)**: (1) `SampleRejected` 를 `continue` 로 접고 계수 안 함 → `excluded_observations` == 거부 수 test (2) 표본 1건으로 조립기 직접 호출 → 같은 함수가 `Unmeasurable` (3) `interval_source` 에 wire 값 대입 → 내부 enum 값 고정 test (4) GBM `predict_bid_rates` import → `engine.py` import 목록 test(+ `CompositionBoundary` 상당) (5) `agency_sample_threshold` 기본값 → 로더 미선언 거부 (6) `bid_ratio` 를 legacy 환산으로 → 함수 부재(grep) (7) `center` 밴드 밖을 clamp → `CenterOutOfBand` 거부 (8) 3계층 중 global 0 → 5D `NO_GLOBAL_SAMPLES`.

---

## (2b) 값 획득 축
| 표면 | 판정 |
| --- | --- |
| `serve_bid_rates`·`predict_distribution` | 연다 — 유일 진입점 둘(진입점이 조립기를 부름) |
| `DistributionRequest` frozen(samples·base_amount·agency·category) | 연다(입력) — `from_proto` 검증 진입점 |
| `ReserveDrawSample` | 생성은 `observe_sample` 만(컨벤션, Python 한계) |
| `DistributionRelease`·`IntervalSource.POSTERIOR_PREDICTIVE` | 내부 타입 — wire 매핑 없음(OPEN) |

---

## OPEN — 수령·신설
| OPEN | 처리 |
| --- | --- |
| `OPEN-5D2-POLICY-VALUES` | `agency_sample_threshold` 값 — 운영자 (c): 5C 재학습 지표 뒤. 그 전 서빙은 로더가 막음 |
| `OPEN-5D2-RELEASE-FOR-DISTRIBUTION` | `ModelRelease` 를 아티팩트 없는 엔진이 어떻게 채우는가 — 5E/2F |
| `OPEN-5D2-INTERVAL-SOURCE-WIRE` | `POSTERIOR_PREDICTIVE` wire 추가 — 2F additive |
| `OPEN-5D-DIAGNOSTICS-WIRE`·`OPEN-5D-DISTRIBUTION-ENGINE` | 전자 수령(필드 둘 더 늘어남: `agency_sample_count`·`agency_sample_below_threshold`), 후자 **이 slice 종결로 해소** |
| 5A 표 #27 | 「미이식(ensemble 밖)」 정정 — 착수 커밋 |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-15 (구현 중 발견) | **`OPEN-5D2-SAMPLE-SEGMENT` 신설** — wire `CompetitionSample`(`features.proto` 7 필드)에 표본별 기관·공종 축이 **없어** 3계층(발주기관/공종/전역) 수축을 서빙 경로에서 만들 수 없다(legacy 는 관측 행에 `agency_name`·`category` 가 있었다). 처분: ① 5D-2 는 **global-only** — `ReserveDrawSample` 에서 `agency`/`category` 제거, 조립기는 전 표본을 global 집계, agency/category 레벨 `None`, `segment_support = GLOBAL`, `agency_sample_count = 0`·`agency_sample_below_threshold = true`(사실대로 — 요청의 `FeatureInputs.agency_id` 로 표본을 같은 기관이라 가정하지 않음) ② `DistributionRequest` 표본에 `segment: SampleSegment \| Missing` 슬롯을 지금 두되 `from_proto` 는 항상 `Missing`(후속이 wire 에서 채움 — 조립기 시그니처 불변) ③ **ML-04 ② 는 서빙 경로에서 도달 불가** — 5D-2 종결 조건에서 제외, golden 011 은 K5 직접 호출 검증(test 문면 명시) ④ M2 2F additive: `CompetitionSample.agency_id`·`category_code`(`optional`, 불투명 문자열, `AgencyIdFact`/`CategoryCodeFact` 형태) 추가 → 후속 소비 라운드가 3계층 연결. ① 표의 `ReserveDrawSample(… agency, category)` 문면은 이 결정으로 정정 | 구현 레인 발견 2026-09-15 · D-2B-3(식별자 없음 원칙은 유지 — 기관·공종은 식별자가 아니라 fact) · ML-04 「3계층 수축」 |
| 2026-09-15 (verifier r1 뒤) | **① 관문 보강** — `observed_bid_rate.fraction` 은 wire `Rate` 파싱 관문(빈 문자열·비수치·비유한·`≤ 0`·`> 1`(D-2B-8 「fraction > 1 은 INVALID_REQUEST」 — 표본 단위로는 `SampleRejected(BidRateUnparseable \| BidRateOutOfBand)`)을 **거부 사유**로 — `Decimal()` 예외가 `serve_bid_rates` 밖으로 새는 경로 0. `reserve_prices` 의 각 `Money` 는 `amount_won > 0` 만이 아니라 **5B `Money` 다섯 성분 규칙**(currency KRW·basis BASE_AMOUNT·provenance ≠ UNSPECIFIED)으로 → 위반 `SampleRejected(ReservePriceInvalid)`. `award_rate`(optional) 도 있으면 같은 파싱 관문. **③ 문면 정정** — `ratio_sample_count == observation_count` 가 파이프라인상 항상 성립해 `TOO_FEW_RATIO_SAMPLES` 는 `distribution_availability` 단위 술어로만 도달(알려진 제한 2 — 정합). **⑥ 문면 정정** — golden 011 `verified_paths` 에 `posteriorStd` 없음(fixture 정본 우선). golden 011 test docstring 에 「서빙 경로가 아니라 커널 직접 호출」 명시(계약 갱신 623baa1 ③) | verifier r1 F-1(high: `InvalidOperation` 탈출 — 저장소에서 wire `Rate.fraction` 을 파싱하는 유일 자리, test 0)·F-2(medium: `reserve_prices` basis/currency 미검증)·F-3~F-8(low) |
