# Slice 계약 — M2 / 2B · Prediction RPC (`BidPredictionService`) — **초안, 구현 전**

> **지위**: M1/1E 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> 착수는 2A 승인 뒤 운영자 지시로 하며, 그때 `base_sha` 를 재고정하고 아래 「착수 전 결정」의 답을 받은 뒤 `milestone-2.md`
> 착수 문단을 쓴다. 2A 의 값 타입·봉투·오류 패턴을 전제한다.

```yaml
milestone: m2
slice: 2b-prediction-rpc
base_sha: cbfb180   # 착수 2026-09-07 재고정(2A 잔여 일괄 커밋 = 2A 종결 시점). 초안 시점은 c9022d9 였다
head_sha: 리뷰 시점의 HEAD
in_scope:
  - contracts/proto/bidvector/ml/v1/prediction.proto  # BidPredictionService { CalculateOptimalBid, GetModelMetadata } + 요청·응답 메시지
  - contracts/proto/bidvector/ml/v1/features.proto    # FeatureInputs(feature_schema_version 아래의 이름 붙은 입력 fact, 각 fact 는 oneof { value, MissingReason })
  - contracts/testdata/prediction/**                  # round-trip·fake servicer 표본(canonical 바이트 + 사람이 읽는 JSON 원본)
  - adapters/src/test/kotlin/**                        # 생성 stub 위의 consumer test — Kotlin in-process fake servicer 대상(실제 client 배선은 M4)
  - ml-engine/tests/test_prediction_contract.py       # provider 쪽 계약 test — fake servicer 가 계약 규칙(fail-closed·oneof)을 지키는지
  - config/quality/gate-tests.properties              # `gate.tests.adapters` 등재 수 갱신(2A 관례) — verifier r1 F-1 로 계약 정정(사후 확인, 2026-09-07). rollback·clean-tree 는 처음부터 이 파일을 덮고 있었다
  - milestone-2.md                                    # 「Slice 2B」 착수 문단, **착수 시**
  - reports/evidence/m2/2b/**
out_of_scope:
  - EstimateShortfall RPC                             # D-M2-4 (a) 제외 — Kotlin decision 소유(1D), legacy 도 ML 경로 밖
  - 업무 판정 필드 일체                                 # review_required · guardrail/floor 10 · granularity 3 · regime 2 · 후보 선택 2 · bid_target_menu — 조사 노트 01 (b-5)
  - 투찰 금액(원) 산출                                  # 응답은 율만. BidAmount = 기초금액 × 율 은 Kotlin 1B 의 RoundingPolicy 소유(D-2B-2)
  - 피처 변환(log·target encoding·범주 코드화)         # ml-engine features/ 소유(M5 5B) — wire 는 원 fact 만(D-2B-1)
  - 경쟁 표본의 정제 규칙                               # 어느 행을 버리는가는 Kotlin 어댑터(M4)·ml-engine(M5) 각자의 소유. 계약은 표본의 형태만
  - 자격 라벨·Platt P(낙찰)                            # OPEN-ML-02 — 받지 않는다(D-M2-8)
  - 실제 client(4D)·실제 servicer(5E)·breaking gate 증명(2D)
  - ml-contract/**, ml-engine/src/ml_engine/contracts/**   # 생성물은 VCS 밖(2A D-2A-0 (c)) — 변경이 나올 수 없는 경로라 in_scope 아님
  - shared-kernel/**, 도메인 모듈, fixtures/**
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "(cd contracts && buf lint && buf build)"                                                          # S-2
  - "./gradlew :adapters:test --tests '*Prediction*Contract*'"                                        # S-3 — consumer test(fake servicer). 생성은 included build 가 adapters 의 test 의존으로 수행
  - "(cd ml-engine && python -m pytest tests/test_prediction_contract.py tests/test_contract_roundtrip.py -q)"   # S-4
  - "./gradlew qualityBaseline"                                                                        # S-5
rollback: |
    **정본은 `reports/evidence/m2/2b/rollback.md`**(착수 시 작성). prediction.proto·features.proto 와 그 생성물·test 를 걷으면 2A 상태.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 근거: `milestone-2.md` 「Slice 2B」·「설계 규칙」·「완료 조건」 · `v2-지침서.md` §3.2·§4.1·§4.4 ·
`data-dictionary.md` §6.2·§6.3·§6.5 · `capability-map.md` ML-01·ML-02·ML-03 · `ADR 0003` D-2·D-3 · `ADR 0010` 초안 · 조사 노트 01 (a)·(b)·(c)·(h).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline cbfb180..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-07) **없음**.

**착수 2026-09-07 — 운영자 결정**: D-2B-1 (a) 원 fact · D-2B-2 (a) 율만 · D-2B-3 (a) `CompetitionSample` + `ReserveDrawObservation`,
식별자 없음 · D-2B-4 (a) `OptimizationObjective { SCENARIO_TRIPLE }` 만. 2A 종결 승인 같은 날. 정본: `milestone-2.md` 2B 착수 문단.
2A 인계 수령: 결과 봉투 `oneof result { Success, Unmeasurable, ApplicationFailure }` 의 첫 실물은 이 slice 의 응답 메시지다(2A
checklist 알려진 제한 7 · 위협 (a) 「oneof + test」가 여기서 성립).

---

## 이 slice 가 하는 일

`milestone-2.md` 「Slice 2B」 를 **RPC 둘 + 메시지 셋(요청·응답·피처 입력)** 으로 낸다.

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`CalculateOptimalBid` 입력** — `PredictionEnvelope`(2A ④ — 선택자·schema version 포함) + `FeatureInputs`(D-2B-1: 원 fact — `base_amount: Money[basis=BASE_AMOUNT 필수]`(그 `provenance` 성분은 금액 획득 축)·`category_code`·`agency_id`(불투명 식별자, DB id 아님)·`base_amount_provenance_label: BaseAmountProvenanceLabel`(기초금액 **판정 라벨** 축 — `denominator_source` 자리, 2A ③ 의 둘째 enum)) + `repeated CompetitionSample`(D-2B-3) + `OptimizationObjective`(D-2B-4). 대상 공고의 `opened_on` 은 **피처가 아니다**(legacy 에서 cutoff·홀드아웃 분할 전용 — 조사 (a-2)) — `FeatureInputs` 에 없고, 표본 쪽 `CompetitionSample.opened_on` 만 관측 시점으로 남는다. floor rate 는 **입력에 없다** — legacy 에서도 모델 입력이 아니라 guardrail 입력이었고(조사 (a-4)), V2 는 Kotlin `decision` 소유 | 2B 「입력: canonical 금액, floor rate, versioned feature vector, 정제된 경쟁 표본, optimization objective」 — floor rate 는 D-M2-4 로 제외, 「versioned feature vector」는 `feature_schema_version` 아래의 이름 붙은 fact 로 읽는다(D-2B-1) |
| ② | **결측은 fact 마다 사유** — `FeatureInputs` 의 모든 fact 가 `oneof { value, MissingReason missing }`. 결측을 `0`·`-1`·빈 문자열로 나르는 필드 없음(legacy 는 어휘 밖 범주를 `-1.0`, 0원을 `max(amount, 1.0)` 로 접었다 — 조사 (a-2)) | `data-dictionary.md` §6.3 · §13.3 |
| ③ | **응답 = `oneof result`**(2A ⑤) — `Success { repeated Candidate candidates(정확히 3, 라벨 `CONSERVATIVE`·`BASE`·`AGGRESSIVE` 순서 고정), PriceFitness fitness, Uncertainty uncertainty, ModelRelease release, Diagnostics diagnostics }` · `Unmeasurable { reason ∈ { UNTRAINED_SEGMENT, INSUFFICIENT_SAMPLES, FEATURE_ABSENT } , detail_code }` · `ApplicationFailure`. **`UNTRAINED_SEGMENT` 와 `INSUFFICIENT_SAMPLES` 는 다른 값** | ML-01 「3개 후보」 · ML-02 「점수가 아니라 `Unavailable`이고 사유가 학습된 적 없음과 표본 얕음을 구별」 · ADR 0001 D-6 |
| ④ | **후보는 율뿐** — `Candidate { CandidateLabel label, Rate bid_rate, BidRateOrigin origin(= RECOMMENDED, 2A D-2A-7), Weight weight(decimal) }`. 원 단위 가격 없음(D-2B-2). 후보 셋의 가중치는 응답이 싣되 **정책 version 을 동반**(legacy 는 경로마다 0.24/0.52/0.24 와 0.22/0.56/0.22 가 갈렸다 — 조사 (b-1)) | ML-01 「각 후보의 rate가 fraction 단위」 · §4.1 |
| ⑤ | **불확실성은 성분 셋 + 출처** — `Uncertainty { sample_size(uint32, 그 추정에 실제로 쓰인 표본), dispersion(Rate 축 decimal), estimate_margin(decimal), IntervalSource interval_source ∈ { CROSS_VALIDATION_RESIDUAL, TIME_HOLDOUT_RESIDUAL } }`. 합성 `confidence` 없음(H-4). `PriceFitness { decimal score }` — 이름에 probability·win 없음(D-M2-8) | §6.5 「이 산식을 사전에 올리지 않는다」 · ML-01 「폭의 출처가 구조화된 값」 · ML-03 「낙찰 확률로 표기하지 않는다」 |
| ⑥ | **모델 식별은 구조화** — `ModelRelease { release_id, artifact_checksum, feature_schema_version, code_version, dataset_id }`(H-9). §5 가 artifact 에 요구하는 다섯 중 **`metric` 은 artifact manifest 소유**이고 응답이 나를 이유가 없다 — 응답은 release 를 **지목**하는 데 필요한 넷 + 식별자. `Success.release` 는 `exact_release` 요청이면 그것과, `latest_promoted` 요청이면 `GetModelMetadata.promoted` 와 같아야 한다(2A ⑥ 제3 변환 금지) | `v2-지침서.md` §5(artifact 의 다섯) · §9 재현성 |
| ⑦ | **diagnostics 는 타입이 있다** — `Diagnostics { training_row_count, SegmentSupport segment_support ∈ { DIRECT, PARENT_CATEGORY, GLOBAL }, … }`. `dict[str, Any]`·자유 문자열 진단 없음(조사 (b-3)). 사람이 읽는 `explanation` 없음 — 렌더링은 Kotlin 표현 계층 | 2B 「diagnostics」 · §3.2 |
| ⑧ | **`GetModelMetadata`** — 입력은 `RequestEnvelope` 만(2A ④ — 선택자 없음, 이 RPC 가 승격 release 를 알아내는 자리라 선택자를 요구하지 않는다) → `{ ModelRelease promoted, repeated string supported_feature_schema_versions, Readiness readiness ∈ { READY, LOADING, NOT_READY } }`. `latest_promoted` 선택자의 해석 근거를 client 가 얻는 유일한 자리 | 2B 「release, feature schema, readiness 정보」 |
| ⑨ | **consumer/provider test(fake)** — Kotlin in-process fake servicer 가 계약 규칙(3후보 고정·`Unmeasurable` 두 사유 구별·release 일치·`UNSPECIFIED` 거부)을 낸다는 것을 양쪽에서 같은 testdata 로 확인. 실제 socket 은 2D | 2D 「fake servicer를 이용한 consumer/provider test」의 2B 몫 |

**만들지 않는 것**: 업무 판정 20필드(out_of_scope) · 가격 산출 · 피처 변환 · 표본 정제 · 자격 라벨 · 사람이 읽는 문장 · `pricing_mode` 같은 구현 이름(H-10).

---

## 운영자 결정 필요 — 착수 전(D-2B-1~4, **전부 (a) 채택 2026-09-07**) · 계약 고정(D-2B-5~8)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-2B-1** | **wire 가 나르는 것은 원 fact 인가 변환된 피처인가.** legacy GBM 피처 5개 중 `log_amount`·`agency_encoding`·`agency_sample_count` 는 **아티팩트가 있어야 계산되는 파생값**(target encoding 은 학습 통계) | (a) **원 fact**(`FeatureInputs`) — 변환은 ml-engine `features/` 가 training·serving 공용으로 (b) 변환된 `repeated double` 벡터 (c) 둘 다 | **(a)** — (b) 는 Kotlin 이 아티팩트 통계를 알아야 하고 §3.2 「training과 serving은 같은 feature schema와 변환 코드」가 깨진다(training-serving skew 를 구조로 막던 legacy 장치 — ML-11.1 — 를 잃음). `milestone-2.md` 의 「feature 배열만 보내지 말고 schema/version/order를 검증」은 (a) 에서 `feature_schema_version` + 이름 붙은 필드로 성립. **피처 이름·순서 계약(`AWARD_RATE_FEATURE_NAMES`)은 ml-engine 안(5B)에 남는다** | 착수 전 |
| **D-2B-2** | **응답에 원 단위 투찰가를 싣는가** | (a) **율만** (b) 율 + 가격 | **(a)** — `BidAmount` 는 `internal constructor` 이고 반올림은 1B `RoundingPolicy`(versioned) 소유. Python 이 2dp 반올림한 가격(legacy)을 실으면 반올림 정책이 두 자리에 생긴다. Kotlin 이 `BidRate → BidAmount` 를 만든다(1B 산술) | 착수 전 |
| **D-2B-3** | **경쟁 표본의 형태.** legacy 는 duck-typed dict 15+키에 DB id 셋(`historical_data_id`·`project_id`·`tender_result_id`)이 실렸다 | (a) **`CompetitionSample { Rate observed_bid_rate, BidRateOrigin origin(= OBSERVED), Money base_amount[BASE_AMOUNT], BaseAmountProvenanceLabel base_amount_provenance_label, opened_on, optional Rate award_rate, optional ReserveDrawObservation reserve_draw }`** 에 **`ReserveDrawObservation { repeated Money reserve_prices(15, basis=BASE_AMOUNT 축의 예비가격), repeated uint32 selected_numbers(4) }`** — 식별자 없음, 정제는 송신 전 Kotlin 어댑터(M4) (b) 표본 대신 「표본 조회 키」를 보내고 ml-engine 이 읽음 (c) GBM 경로만 덮고 예비가격 관측은 제외 | **(a)** — (b) 는 serving 이 DB 를 갖게 된다(§3.2 금지). **(c) 는 ML-04(예정가 분포 추정, `V2 필수`)의 distribution predictor 가 행마다 `reserve_prices`·`selected_numbers`·`base_amount_basis` 를 읽으므로(legacy `distribution.py` `_extract_reserve_observations`, 리뷰 r1) 완료 조건 「요청만으로 Python serving 이 DB 조회 없이 계산 가능」이 성립하지 않는다.** §6.3 의 누수 차단(예비가격·추첨번호·낙찰가를 받지 않는다)은 **대상 공고의 피처**에 대한 규칙이고 과거 공고의 관측은 그 대상이 아니다 — GBM 피처(`FeatureInputs`)에는 없고 표본에만 있다. GBM 은 표본을 쓰지 않으므로 `repeated` 가 비어 있어도 유효(「표본 없음」은 `Unmeasurable(INSUFFICIENT_SAMPLES)` 의 원인이지 요청 오류가 아니다) | 착수 전 |
| **D-2B-4** | **`OptimizationObjective` 를 요청에 두는가.** legacy 에는 요청 파라미터가 없고 시나리오 셋은 릴리스 상수 | (a) **enum 을 두되 M5 가 지원하는 값이 하나(`SCENARIO_TRIPLE`)면 그것만 정의** — 미지원 값 fail-closed (b) 두지 않음 | **(a)** — `milestone-2.md` 문면이 입력에 넣었고, enum 추가는 호환 변경이다. (b) 는 나중에 필드 추가가 필요할 때 봉투를 바꾸게 된다 | 착수 전 |
| **D-2B-5** | 후보 라벨 축은 **하나** — 운영자 대면 메뉴(`recommended`/`aggressive`/`safe`, H-7)는 Kotlin 표현 계층 | — | 계약 고정 |
| **D-2B-6** | `historical_sample_size` 같은 **엔진마다 뜻이 다른 이름**(H-8)은 쓰지 않는다 — `Uncertainty.sample_size` 는 「그 추정에 실제로 쓰인 표본」 하나, `Diagnostics.training_row_count` 는 아티팩트 학습 행 수 | — | 계약 고정 |
| **D-2B-7** | `predicted_bid_rate`·`competitive_target_bid_rate`(같은 값 두 이름, H-5) 는 옮기지 않는다 — 후보 `BASE` 의 `bid_rate` 하나 | — | 계약 고정 |
| **D-2B-8** | 단위 추측 없음 — 율 필드에 percent 관용(H-11, `PERCENT_SCALE_THRESHOLD`) 을 옮기지 않는다. `Rate.fraction` 이 `1` 초과면 계약 위반(`INVALID_REQUEST`) | — | 계약 고정 |

---

## 위협 모델 — 2B 고유 경계

**방어한다**: (a) 업무 판정 필드의 재유입(필드 부재 — 추가는 breaking gate 와 리뷰 항목 「계약에 업무 규칙이 새어 들어갔는지」) (b) `Unmeasurable` 두 사유의 합침(enum 값 분리 + fake servicer test) (c) 후보 수·순서 위반(provider test — 3 아닌 응답 거부) (d) 합성 신뢰도·확률 이름의 재유입(필드 부재, D-M2-8) (e) DB id 의 wire 유입(`agency_id` 는 불투명 식별자로 정의 — 값의 의미는 Kotlin 만) (f) 결측의 sentinel 접힘(oneof) (g) 다른 release 의 답(2A ⑥).
**방어하지 않는다**: Kotlin 어댑터의 표본 정제·매핑의 정직성(M4) · ml-engine 이 실제로 `UNTRAINED_SEGMENT` 를 내는가(M5 5D — ML-02 「설정으로 끌 수 없다」는 5D 의 test) · 가중치·시나리오 값의 옳음(정책 데이터) · `agency_id` 의 안정성(M3).

**우회 후보(≥5)**: (1) `Diagnostics` 에 `map<string,string>` 을 넣어 판정을 실어 보냄 → buf lint 로 `map` 금지 규칙 + 리뷰 (2) `Success.candidates` 를 1개만 → provider/consumer test (3) `Unmeasurable` 대신 `Success` 에 sample_size=0 → `Uncertainty.sample_size ≥ 1` 불변식 test (4) `release` 를 요청과 다르게 → 2A ⑥ (5) `CompetitionSample` 에 `optional string source_id` 추가 → breaking gate 는 못 잡는다(추가는 호환) — **리뷰 항목**으로만, 알려진 제한 (6) `OptimizationObjective` 미지 값 → fail-closed test (7) `bid_rate.fraction = "87.995"` → D-2B-8 거부 test.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (조사 노트 01)

- 진입점 `predict_price()` 가 모델 입력(`PricePredictionContext`)과 guardrail 입력(`legal_floor_bid_rate`·`estimation_amount`)을 **이미 시그니처에서 갈라 두었다** — floor rate 제외의 legacy 근거.
- GBM 피처 5개 중 3개가 아티팩트 의존 파생값 → D-2B-1 (a). `denominator_source` 는 서빙에서 `clean-base` 상수 고정 — V2 는 요청의 `BaseAmountProvenanceLabel` 이 실값을 나른다(D-M2-10).
- GBM 은 경쟁 표본을 읽지 않고 distribution predictor 만 읽는다 — 행마다 `reserve_prices`·`selected_numbers`·`base_amount_basis`(`clean` 행만) → D-2B-3 의 `ReserveDrawObservation` 과 「비어 있어도 유효」.
- 후보는 항상 3, 인덱스 1 이 기준 — 호출부가 위치로 집는다 → 라벨 enum + 순서 고정 + provider test.
- 응답 `bid_rate_candidates` 는 `list[dict[str, Any]]`, 진단은 전부 dict·자유 문자열, 모델 식별은 문자열 하나 → ③·⑥·⑦.
- (c-2) 폴백 반례 → 2A ⑥·ADR 0010 D-3. (c-3) `confidence` 클램프 [0.45, 0.95] → 「측정 불가」 표현 부재 → ③ `Unmeasurable`.
- 어휘 불일치 H-1(`agency_sample_count` 실물 이름 — 계약엔 없음, 5B 소관)·H-4~H-11 은 위 표가 처리. H-1 의 capability-map 정정은 착수 시 등재.

---

## OPEN — 수령·신설

| OPEN | 2B 처리 |
| --- | --- |
| `OPEN-ML-03` | D-M2-8 (a) — `PriceFitness`·`Uncertainty` 분리, probability 축 필드 없음 → **계약 수준에서 닫는 후보** |
| `OPEN-ML-02` | 받지 않는다. 조사 (b-5) 가 Platt P(낙찰) 의 사용자 도달 경로(opportunity analysis)와 자격 의존을 실측 — 사유 보강만, 처리는 운영자·M5 |
| `OPEN-DIC-03` | `SkipReason` — Kotlin 소유, 계약 무관 |
| 신설 후보 `OPEN-2B-OBJECTIVE-VALUES` | `OptimizationObjective` 의 M5 지원 집합 — 5D 가 확정 |
| 신설 후보 `OPEN-2B-AGENCY-ID` | `agency_id` 의 정본(불투명 식별자의 발급·안정성) — M3 수집 축 |
