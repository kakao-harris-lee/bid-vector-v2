# M5/5C-2 — checklist.md

## M5 완료 조건 중 5C-2 담당 근거

- 「시간 누수 없는 split, rolling/group holdout」 — `evaluation/windows.py::plan_evaluation_windows`
  (embargo + 표본 하한 + 최근 N) · `_holdout_fit.py::build_split`(경계 동시각은 평가측,
  `_split_at_window` legacy 이식) · `holdout_overlaps`(측정, 설계상 0).
- 「worst-segment report」 — `evaluation/segments.py::segment_scores`/`regressed_segments`
  (축 `category`·`amount_band`, D-5C2-9).
- 「metric」 — `evaluation/scoring.py`(rmse·bias·std·paired_t·improvement_ratio) +
  `evaluation/diagnostics.py`(MDE·required_row_count·coverage_splits·unlearned_cells).
- 「promotion 은 측정만」 — `evaluation/report.py::Promotion`(`Promotable`은
  `derive_promotion`이 `Passed`에서만 생성) + `training/holdout.py::run_holdout`은
  report 를 반환할 뿐 배포·rollout 을 호출하지 않는다(코드 전수 grep: `deploy`·`rollout`
  참조 0).
- 완료 조건 「승인된 ML metric threshold 충족 또는 `not-promotable`로 명시」 —
  `GateOutcome = Passed | Failed | NotEvaluable`(3값, bool 쌍 아님) + `Promotion`이
  그대로 옮긴다.
- 완료 조건 「재현 가능한 metric」 — `test_run_holdout_is_reproducible_with_fake_trainer`
  (canonical bytes 동일) · `test_run_holdout_reproducible_with_real_lightgbm`(실 LightGBM,
  `num_threads=1`).

## D-5C2-1~12 이행

| ID | 결정 | 충족 근거 |
| --- | --- | --- |
| D-5C2-1 | 순수 커널은 `evaluation/`, 실행기는 `training/holdout.py`(+`_holdout_fit.py`/`_holdout_window.py`) | import-linter 계약 `training/evaluation 층 — features·contracts 위로만 의존`(S-4 KEPT) · evaluation 어떤 파일도 `ml_engine.training` import 없음(grep 확인) |
| D-5C2-2 | 성숙도는 입력(`WeekMaturity`) | `evaluation/windows.py::WeekMaturity(start,end,opened_count,settled_count)` · `run_holdout`의 `maturities: Sequence[WeekMaturity]` 인자, 5D `inference.maturity` import 0 |
| D-5C2-3 | 임계는 전부 `policy/evaluation-v1.yaml` | `evaluation/policy.py::load_evaluation_policy` · `test_evaluation_policy.py::test_shipped_policy_file_matches_policy_values_md`(값 11개 하드코딩 대조) |
| D-5C2-4 | 판정 3값 `Passed \| Failed \| NotEvaluable(reason)` | `evaluation/verdict.py` · `NotEvaluableReason`(3값: `NO_EVALUABLE_WINDOW`·`UNDERPOWERED`·`SEED_UNSTABLE`) |
| D-5C2-5 | seed 재채점 끌 수 없음, 헤드라인 선두 | `_holdout_fit.py::stability_seed_order`(헤드라인 우선, 목록 조립 시 고정) · `run_holdout`/`_evaluate_one_window`에 안정성 비활성 플래그 없음(시그니처 확인, `test_no_bare_threshold_seed_or_layer_parameter_on_run_holdout`) |
| D-5C2-6 | 승격은 latest-window 하나 | `evaluation/report.py::derive_promotion(latest: GateOutcome \| None, ...)` — `windows[-1]`만 본다, `all_origins`는 진단 필드로만 존재(`WindowResult` 개별 항목, `EvaluationReportV1`에 all_origins bool 없음) |
| D-5C2-7 | 홀드아웃 예측은 `booster.predict` 직접 + `build_row` | `_holdout_fit.py::feature_space_from_manifest`(재구성) → `matrix_for` → `booster.predict` · `OPEN-5C2-SERVING-PATH-PARITY`(아래 OPEN 절) |
| D-5C2-8 | 경계 동시각은 평가측 | `_holdout_fit.py::build_split`의 `gate_test_all`(`indices_in_window`가 `[start,end)`) vs `gate_train`(`opened_at < window.start`) — 반개구간 겹침 없음 |
| D-5C2-9 | 세그먼트 축 `category`·`amount_band`, `published_floor` 없음 | `evaluation/segments.py`에 `published_floor`/`floor` 문자열 0(grep 확인) — `_AXIS_KEY_BUILDERS`가 두 축만 |
| D-5C2-10 | 완화 경로 폐쇄 표 | `test_public_signatures.py::test_no_evaluation_public_function_takes_bare_threshold_or_seed_parameters`(게이트 진입점 3개 `policy` 하나만) · `EvaluationPolicy`는 `load_evaluation_policy`만 값을 채운다(로더 관례, Python 가시성 한계는 알려진 제한) |
| D-5C2-11 | 이식 출처 5 모듈 포인터 | `reports/evidence/m5/5c2/reuse.md`(S-7 대조 통과) |
| D-5C2-12 | `HoldoutRejected`/`NotEvaluable` → `JobFailureCode` 매핑 | 아래 매핑 표 |

## (2b) 값 획득 축 — 실측

| 표면 | 판정 | 실측 |
| --- | --- | --- |
| `EvaluationPolicy`(frozen)·`load_evaluation_policy` | 연다 — 로더만 생성(관례) | `EvaluationPolicy(...)` 직접 생성으로 임의 임계 가능(Python 한계, 알려진 제한). `test_evaluation_policy_direct_construction_enforces_invariants`가 명백한 위반(0 이하 등)은 막는다는 것만 확인 |
| `gate_outcome`/`passes_gate` | 연다 — 유일 판정 진입점(+ 판정식 자체), `policy` 하나만 | `test_gate_outcome_and_passes_gate_require_policy_parameter`. **2026-09-16 정정(verifier r1 H-2·H-3)** — `trial_outcome`은 안정성 없이 `Passed`를 낼 수 있어 public 이면 위협 모델 (f)의 우회 표면이었다. `_trial_outcome`(비공개)으로 내리고, 판정식 자체를 `passes_gate`(public)로 승격해 안정성 sweep(`_run_stability`)이 그 함수만 쓰게 했다(이중 구현 금지) |
| `plan_evaluation_windows` | 연다 — `policy` 하나만, 낱개 임계 없음 | `test_public_signatures.py` 전수 |
| `run_holdout` | 연다 — 유일 실행 진입점. `trainer: TrainerLike` 주입은 5C-1 과 같은 자리(모델 실물만, 판정·임계 주입 없음) | `test_no_bare_threshold_seed_or_layer_parameter_on_run_holdout` |
| `EvaluationReportV1`·`WindowResult` 등 결과 타입 | 연다 — 읽기·직접 생성 가능(관례로만 방어) | 5C-1 과 같은 알려진 제한(①) |
| 「경계로 처리」 행 — 성숙도 입력(`WeekMaturity`) | 위조 입력이 report 에 그대로 공시(값을 숨기지 않는다) | `WindowResult.window_opened_count`/`window_settled_count`가 입력 그대로 실린다(`_holdout_window.py::_assemble_window_result`) — 위조하면 report 자체가 그 위조를 드러낸다 |
| 「경계로 처리」 행 — dataset cutoff(`opened_at`) | 공시만, 방어 없음 | `EvaluationReportV1.corpus_opened_at_first/last`가 코퍼스 실제 범위를 싣는다 |
| 이번 구현이 만든 새 public 표면 | — | 아래 절 |

## 이번 구현이 만든 새 public 표면과 밖에 허락하는 것

- `ml_engine.evaluation.*`(정책·채점·베이스라인·세그먼트·진단·창·판정·report 전체) —
  training 층(및 장래 5E/6C)에게 순수 평가 커널을 연다. 업무 판정(embargo 적용 여부,
  실제 승격 실행)은 열지 않는다 — `GateOutcome`/`Promotion`은 값일 뿐 부수효과가 없다.
- `ml_engine.training.holdout.run_holdout`/`HoldoutRejected`/`HoldoutRejectionReason` —
  5E(job worker)가 부를 유일 진입점. dataset·정책·spec·trainer 를 받아 report 또는
  거부를 낸다 — DB 쓰기·아티팩트 저장은 하지 않는다(5E 소관).
- `ml_engine.training._holdout_fit`/`_holdout_window`(leading underscore, 비공개) —
  `holdout.py`가 아니라 이 두 파일에서 직접 import 하면 창 분할·GBM 학습 세부·안정성
  sweep 세부에 접근할 수 있다(Python 가시성 한계, 관례 위반). 정상 소비자는 `holdout.py`
  최상위 이름만 본다.

## 알려진 제한

1. **K7 성숙도 비율 3줄 중복** — `evaluation/windows.py::WeekMaturity.maturity_ratio`가
   `settled_count / opened_count`를 계산한다. 5D K7 `inference.maturity.Observed.ratio`와
   동일 식이지만 import 는 layers 위반이라 재사용하지 않는다(D-5C2-2, `OPEN-5C-MATURITY-SOURCE`
   종결 — 대안은 K7 을 `features` 층으로 이전하는 2F/5E 후보).
2. **서빙 경로 불일치(`OPEN-5C2-SERVING-PATH-PARITY`)** — 홀드아웃 예측이
   `TrainedArtifact.feature_manifest`에서 재구성한 `AwardRateFeatureSpace`로 직접
   `booster.predict`를 부른다(D-5C2-7). legacy 의 "아티팩트 → predictor 로더 →
   `predict_rates`" 직렬화 왕복 경로와 수치가 같다는 것은 **증명되지 않았다** — 5E 가
   같은 창의 두 경로 중심값을 대조하는 통합 test 로 확인해야 한다.
3. **서빙 미학습 공종 가드 미적용(`OPEN-5C2-UNLEARNED-GUARD`)** — 홀드아웃 예측 경로가
   5D `segment_availability`를 지나지 않는다(legacy 도 우회). 미학습 공종 행이 홀드아웃
   수치에 그대로 들어간다 — `unlearned_baseline_cells`가 베이스라인 쪽만 공시하고
   모델 쪽 미학습은 별도 공시가 없다.
4. **성숙도 입력 신뢰** — `WeekMaturity(opened_count, settled_count)`의 정확성을 이
   slice 는 검증하지 않는다(D-5C2-2, 호출자 신뢰). 위조 입력은 report 에 그대로
   공시되므로 사후 감사는 가능하나 사전 차단은 없다.
5. **Python 가시성 한계** — `EvaluationPolicy`·결과 타입 전부 직접 생성으로 불변식을
   부분적으로 우회할 수 있다(관례로만 방어, 5B/5C-1 과 같은 한계).
6. **`max_origins` 상한 없음** — 정책 값을 아주 크게 잡으면 성숙 창 전부를 평가한다
   (비용 문제, 정확성 문제 아님). 방어하지 않기로 스코프에서 등재됨(우회 후보 (19)).
7. **`min_training_rows`(5C-1 training-v1 정책)가 창마다 적용된다** — 창의 `train_rows`
   서브셋이 5C-1 `train_award_rate_gbm`을 지날 때마다 500행 하한을 거친다. 합성 test
   코퍼스는 창당 500행 이상을 만들거나(비쌈) `TrainingPolicy(min_training_rows=...)`를
   test 전용으로 낮춰 구성한다(5C-1 컨벤션 허용, `test_holdout.py`가 이 방식 사용,
   docstring 명시 없음 — 함수명 `_training_policy(min_training_rows=5)`로 자명).
8. **`published_floor` 세그먼트 축 없음** — D-5C2-9 로 종결(축 자체가 존재하지 않음,
   기능 누락이 아니라 설계 결정).
9. **conservative 변형(`gbm_gate_stratum_only`) 학습 실패 시 생략** — 게이트 모델
   (`gbm_all_strata`)이 성공하고 보수 변형만 실패하면 창은 정상 평가되고 `models`
   tuple 에 보수 변형 항목만 빠진다(신규 회복 경로, legacy 에 이 실패 상태가 없어
   대응 동작이 없었다).
10. **stability sweep 이 게이트 모델만 재학습** — legacy `_one_window_stability`는
    `evaluate_award_rate_holdout` 전체(두 GBM 변형)를 seed 마다 재실행하지만, 이
    구현은 판정에 쓰이는 `gbm_all_strata`만 재학습한다(비용 절감, 보수 변형은 애초에
    판정에 쓰이지 않으므로 정보 손실 없음).

## `HoldoutRejected`/`NotEvaluable` → 2C `JobFailureCode` 매핑 표(D-5C2-12)

5C-1 D-5C-11 과 같은 형식. 2C `JobFailureCode`(6값)에 없는 사유는 `TRAINING_ERROR`가
아니라 **`EVALUATION_ERROR`** + `detail_code`로 나른다(evaluation 단계 실패이므로 5C-1
학습 실패와 어휘를 가른다). proto 무편집.

| 이 slice 의 결과 타입 | `reason` | `JobFailureCode` | `detail_code` |
| --- | --- | --- | --- |
| `HoldoutRejected` | `EMPTY_SIDE` | `EVALUATION_ERROR` | `HOLDOUT_EMPTY_SIDE` |
| `HoldoutRejected` | `INVALID_MATURITY_INPUT` | `EVALUATION_ERROR` | `INVALID_MATURITY_INPUT` |
| 창 제외(`WindowExclusion.reason`, report 필드일 뿐 job 실패 아님) | `TRAINING_REJECTED` | — (report 는 여전히 성공 반환, `excluded_windows`에 기록) | — |
| `GateOutcome.NotEvaluable`(report 필드, job 실패 아님) | `NO_EVALUABLE_WINDOW`/`UNDERPOWERED`/`SEED_UNSTABLE` | — (report 성공, `promotion=PromotionNotEvaluable`) | — |

「창 제외」와 「`NotEvaluable`」은 **job 실패가 아니다** — `run_holdout`은 정상적으로
`EvaluationReportV1`을 반환하고, 그 report 안에 측정 불가 사실이 값으로 실린다(D-5C2-4
「못 쟀다는 별도 어휘」 취지 — job 계약과 도메인 계약을 섞지 않는다). `JobFailureCode`
매핑 대상은 `HoldoutRejected`(실행 자체의 실패) 하나뿐이다.

## OPEN

| OPEN | 처리 |
| --- | --- |
| `OPEN-5C2-SERVING-PATH-PARITY` | 알려진 제한 2 — 5E 통합 test 이월 |
| `OPEN-5C2-UNLEARNED-GUARD` | 알려진 제한 3 — 5E 가 정책 값으로 공시 여부 결정 |
| `OPEN-5C-MATURITY-SOURCE` | D-5C2-2 로 종결(입력) |
| `OPEN-5C-BUDGET-BAND-SOURCE` | scope ② 로 종결(정책 데이터) |
| `OPEN-5C-SEGMENT-PUBLISHED-FLOOR` | D-5C2-9 로 종결(축 없음) |
| `OPEN-5C-REJECT-ACCOUNTING`(5C-1 L-8) | 미해소 유지(5E 전) — `TrainedArtifact.rejected_rows`를 창별 report 에 공시하는 추가 필드는 5C-1 파일 편집이라 이번 slice 범위 밖 |
| `OPEN-5C-CORPUS` | 승계 — 5C-2 test 는 전부 합성 코퍼스, 실코퍼스 evaluation fixture 는 curator 몫 |

## 계약과 어긋나 판단이 필요했던 자리

- **`training/holdout.py` 단일 파일 계획 → 3파일 분리.** scope.md·설계 검토 둘 다
  `training/holdout.py` 하나를 계획했다. 구현 결과 창 단위 오케스트레이션(분할·GBM
  두 변형 학습·베이스라인·진단·안정성 sweep·report 조립)이 설계 래칫
  `file_loc_soft_limit`(500줄)을 넘겼고, 완화 경로는 둘뿐이었다: (a)
  `pyproject.toml` `[tool.design-ratchet]` allowlist 에 항목 추가(팀장 소관 파일,
  「편집 필요가 생기면 멈추고 보고」 지시 대상) (b) 파일을 나눈다. 계약을 바꾸지
  않고 (b)를 택했다 — `holdout.py`(public 진입점, 218줄)·`_holdout_fit.py`(창 분할
  + GBM 학습, 272줄)·`_holdout_window.py`(베이스라인·진단·안정성·조립, 336줄) 셋
  다 500줄 아래다. public 표면(`run_holdout`·`HoldoutRejected`·
  `HoldoutRejectionReason`)은 `holdout.py`에만 있고 나머지 둘은 leading underscore
  로 비공개임을 표시했다. **팀장 검토 대상**: allowlist 편집이 더 나은 선택이었다면
  롤백 없이 `pyproject.toml`에 항목을 추가하고 세 파일을 다시 합치는 것도 가능
  (rollback.md 의 in_scope 목록에 세 파일이 모두 등재돼 있어 되돌리기 쉽다).
