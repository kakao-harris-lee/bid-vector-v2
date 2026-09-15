# Slice 계약 — M5 / 5C-2 · evaluation(성숙도 창 홀드아웃·베이스라인·검정력 진단·승격 측정·evaluation report) — **착수 계약 2026-09-15(사용자 지시 「5C-2 착수」 · 운영자 확인 대기: D-5C2-1·3·7·9)**

> **지위**: 2026-09-15 착수판(세션 모델 단독, Fable 5.1). 조사 노트는 5C-1 과 공유 — `_workspace/m5-5c2/01_scout_training.md` §3(split)·§6(평가 지표) · `02_scout_evaluation.md` §0~§2(평가 계통 셋·ML-07 게이트 커널 전문 1-5·임계 전수 2-6·완화 경로 2-7·대조표 2-8)·§3~§5·§7·§10·톱 10+보강(legacy `ed4b06c`).
> **Phase 2.5 설계 검토 대상**(게이트·임계·결과 타입 — ML-07 acceptance 네 줄이 전부 「우회할 수 없는가」다): `_workspace/m5-5c2/03_design-review.md`.
> 5C-1(PR #13 병합, `e4abc90`)이 세운 학습 커널 위에 선다. 5D-2(PR #14, `c669a71`)와는 소스 겹침 0.
> 레인: worktree `bid-vector-v2-m5c2`, 브랜치 `m5-5c2/2026-09-15`. 병행 레인 없음(5E 미착수).

```yaml
milestone: m5
slice: 5c2-evaluation
base_sha: c669a71affabed165c1afbd8b11c8245b81a3a5a   # (PR #14 머지 커밋 = origin/main, 5C-1·5D·5D-2 실물 포함)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/evaluation/__init__.py          # 공개 표면 재수출((2b) 표가 전수)
  - ml-engine/src/ml_engine/evaluation/policy.py            # EvaluationPolicy(frozen) + load_evaluation_policy(path) -> EvaluationPolicy | PolicyRejected — 5A load_policy 위, known_keys 전수, 값 불변식(D-5C2-3) · SHIPPED_EVALUATION_POLICY_VERSION="evaluation-v1" · policy_checksum
  - ml-engine/src/ml_engine/evaluation/scoring.py           # K-S 이식: rmse_bias_std · paired_t · improvement_ratio (numpy, ddof=1 그대로)
  - ml-engine/src/ml_engine/evaluation/baselines.py         # BaselineSpec 표 5(global_mean·category·amount_band·category_x_band·agency(min_count)) · group_mean_predictions(예측 + 행별 커버리지 마스크) · amount_band(edges 는 policy)
  - ml-engine/src/ml_engine/evaluation/segments.py          # SegmentSpec 축 · segment_scores(재학습 없이 자르기만) · regressed_segments(1행 세그먼트 제외 규칙)
  - ml-engine/src/ml_engine/evaluation/diagnostics.py       # K-D 이식: minimum_detectable_improvement(검정력 50% 그대로) · required_row_count(None 의미 유지) · coverage_splits · unlearned_cells · category_counts · summarize_stability(sign·verdict 두 축)
  - ml-engine/src/ml_engine/evaluation/windows.py           # K-W 이식: WeekMaturity 입력 타입 · plan_evaluation_windows(rows, maturities, policy) -> WindowPlan(windows, excluded: (window, ExclusionReason)) · 제외 사유 닫힌 enum 4 · 규칙 표(먼저 걸리는 사유) · holdout_overlaps(인덱스 기준) · **비율 분할 API 없음**
  - ml-engine/src/ml_engine/evaluation/verdict.py           # GateOutcome = Passed | Failed | NotEvaluable(reason, required_rows) — 판정식 한 줄(model_rmse < baseline_rmse ∧ paired_t < −threshold), 임계는 policy 에서만
  - ml-engine/src/ml_engine/evaluation/report.py            # EvaluationReportV1(frozen, 창별 결과·진단·안정성·코퍼스 프로필·policy version+checksum·training spec version·창별 release_id) · PromotionMeasurement = Promotable | NotPromotable(reasons) | NotEvaluable · canonical JSON(5B 규칙) + sha256 → 2C EvaluationReportReference 값
  - ml-engine/src/ml_engine/training/holdout.py             # K-H 이식: run_holdout(dataset: LoadedDataset, maturities, spec, training_policy, evaluation_policy, trainer, code_version) -> EvaluationReportV1 | HoldoutRejected — 창마다 5C-1 train_award_rate_gbm 호출(층: training → evaluation 허용, 역방향 금지) · _split_at_window(학습 < start, 평가 [start,end), 경계 동시각은 평가측) · 두 층(gate_train clean-base / train_rows 전층) · seed 재채점(헤드라인 seed 선두, 끄기 없음)
  - ml-engine/src/ml_engine/training/_holdout_fit.py        # 구현 중 분리(2026-09-16) — 창 분할 + 창별 GBM 학습(파일 500줄 래칫: allowlist 편집 대신 분리, 「줄 수만 맞추기 위한 분할」 여부는 verifier 표적)
  - ml-engine/src/ml_engine/training/_holdout_window.py     # 구현 중 분리 — 창 채점 + WindowResult 조립. 공개 진입점은 holdout.py 의 run_holdout 하나
  - ml-engine/src/ml_engine/training/__init__.py            # run_holdout·HoldoutRejected 재수출
  - ml-engine/policy/evaluation-v1.yaml                     # 정책 값 실물(D-5C2-3, OPEN-5C2-POLICY-VALUES)
  - ml-engine/tests/evaluation/**                           # RED 먼저 — 산식 항등식·규칙표·property·legacy_parity(관측)·정책 위반 규칙표·완화 경로 부재 test
  - ml-engine/tests/training/test_holdout*.py               # 합성 코퍼스로 창 분할·경계 동시각·양쪽 비어 있음·seed 안정성·report 왕복(canonical bytes 재현·checksum 재계산)
  - milestone-5.md                                          # 5C-2 착수 문단 + 5C-1 PR #13 병합 등재
  - reports/evidence/m5/5c2/**
out_of_scope:
  - **B 계보 전부**(D-5C-1 승계) — `ml_release/*` 승격 게이트·`comparison.py`·dataset_quality(B)·Platt·group 통계·`skip_promotion_gate`·`recommended_env`
  - 운영 승격·배포·rollout(ML-08 롤아웃 — 6C/6E) · `TrainingJobService` servicer·job 상태·report 저장소 쓰기(5E) · report `uri` 의 storage(adapters — `file://` 쓰기는 5E 가 5C-1 `dataset_files` 옆에)
  - 성숙도 **계산**(5D K7 `build_weekly_maturity`·`resolve_maturity`) — 5C-2 는 주별 `(opened_count, settled_count)` 를 **입력으로 받는다**(D-5C2-2). 성숙도 기반 **업무 판정**(embargo 적용 여부의 운영 결정)은 Kotlin(SET-06)
  - 서빙 미학습 공종 가드(5D `segment_availability`) 통과 여부의 평가 경로 적용 — legacy 도 우회(조사 02 §1-5 (g)) → `OPEN-5C2-UNLEARNED-GUARD`
  - `contracts/**` 편집 · 5C-1 파일(`training/{spec,policy,corpus,dataset,folds,encoding_oof,booster,residual,release,train,artifact_writer}.py`) 편집 · 5D/5D-2 파일 · `pyproject.toml`(편집 필요 0 예상 — 마커·extras·계약 전부 기존) · 5A `policy-values.md`
  - `published_floor` 세그먼트 축(`FeatureFacts` 에 없고 legacy 에서 백필 커버리지의 함수 — `OPEN-5C-SEGMENT-PUBLISHED-FLOOR` 종결: 축 없음) · 릴리스 간 비교(legacy 부재, 신규 요구 아님 — 등재만)
  - 정책 값 튜닝·재학습 결과의 옳음 · fixtures 신설(curator — `OPEN-5C-CORPUS` 승계)
acceptance_commands:
  # 정본 = CI `ml-engine` job 전건. evidence 를 고치는 라운드는 Kotlin `check` 도 전건(5C-1 S-10 규율 승계).
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                 # S-1
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c \"import $m\" || exit 1; done && uv sync --frozen --all-extras)"   # S-1b
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2 — BLE 포함
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3 — warn_unreachable 포함, override 추가 0
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4 — evaluation 은 features·contracts 만, training → evaluation 만 허용
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                              # S-5
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
  - "./gradlew --no-daemon check"                                                                      # S-10 — evidence 편집 라운드 한정(leakPatternGate)
rollback: |
    **정본 `reports/evidence/m5/5c2/rollback.md`**(구현 레인). `evaluation/*.py`(신규)·`training/holdout.py`·`policy/evaluation-v1.yaml`·`tests/evaluation/**`·`tests/training/test_holdout*.py` 삭제 +
    `training/__init__.py`(5C-1 파일 — 재수출 두 줄 hunk 격리)·`milestone-5.md`(문서 레인 — 목록 제외). 임시 worktree 실측 → in_scope diff 0 → S-1~S-7·S-9 전건 초록(`main` 의 test 수 직접 계수).
```

작성: 2026-09-15 착수판 — 세션 모델 단독. 근거: `milestone-5.md` 5C(시간 누수 없는 split·rolling/group holdout·worst-segment report·metric·promotion 은 측정만)·완료 조건 「승인된 ML metric threshold 충족 또는 `not-promotable` 로 명시」·「재현 가능한 metric」 · `capability-map.md` ML-07(acceptance 5 + 작업 규율)·ML-05·ML-11.4 F4·F4-함정·F5·F6 · SET-06(성숙도 계산/판정 분리) · 2C ⑧ `EvaluationReportReference` · ADR 0006 D-7 · ADR 0009 · 5C-1 D-5C-0·4·6·12 · 5D policy `maturity.window_days 7` · 조사 노트 02 §1-5·§2-6·§2-7·§2-8 / 01 §3·§6.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점 재실행.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **채점 커널 이식**(`scoring.py`) — `rmse_bias_std`(같은 잔차에서 셋: 「치우침이 줄어서」와 「모양이 좋아져서」 구별)·`paired_t`(제곱오차 차이의 대응 t, std 0 → 0)·`improvement_ratio`. numpy 직접 구현 유지(scipy 도입 없음 — ddof 차이로 수치가 달라진다, 조사 01 §11-7). 항등식 test `rmse² = bias² + std²·(n−1)/n` | 5C 「metric」 · legacy test 의 수학 항등식(authoritative 후보) |
| ② | **베이스라인 표 + 커버리지 마스크**(`baselines.py`) — 다섯 spec 을 **선언 데이터**로(분기 아님), `agency` 만 `min_count`(policy `agency_baseline_min_count`). 금액대 밴드 경계는 policy(`amount_band_edges`, legacy 1e8/5e8/1e9/5e9 — `OPEN-5C-BUDGET-BAND-SOURCE` 종결: 정책 데이터). `group_mean_predictions` 는 비율이 아니라 **행별 bool 마스크**(폴백 소수 행에 유의성이 걸렸는지 분해하기 위해) | 조사 02 §1-1 · `OPEN-5C-BUDGET-BAND-SOURCE` |
| ③ | **세그먼트 재채점**(`segments.py`) — 축은 `category` + `amount_band`(둘 다 `FeatureFacts` 에서 산출). `published_floor` 축은 **없다**(D-5C2-9). `regressed_segments` 는 `improvement_ratio < 0 ∧ row_count > 1`(1행 세그먼트 제외 사유 이식) | 5C 「worst-segment report」 · 조사 01 §6-2 |
| ④ | **「못 이겼다 / 못 쟀다」 계측 이식**(`diagnostics.py`) — MDE(검정력 50% 그대로 — 부풀리지 않는 사유 docstring 이식)·`required_row_count`(모델이 더 나쁘면 `None`, 0 아님)·`coverage_splits`(baseline-covered / baseline-fallback 각각 채점, 같은 t 식)·`unlearned_cells`·`category_counts`(레짐 단절 공시)·`summarize_stability`(`sign_consistent`·`verdict_consistent` 둘 다, 단독 사용 금지 문면) | ML-07 「검정력 공시」·acceptance ①「`not_evaluable(사유, 필요 표본 수)`」 · F5 |
| ⑤ | **성숙도 창 정책 이식**(`windows.py`) — 입력 `WeekMaturity(start, end, opened_count, settled_count)`(KST 주, 5D K7 산출과 같은 형태 — **계산은 밖**, D-5C2-2). 규칙 표 순서로 제외 사유 하나(`IMMATURE`·`INSUFFICIENT_EVALUATION_ROWS`·`NO_TRAINING_ROWS`·`BEYOND_MAX_ORIGINS`, 먼저 걸리는 사유). `holdout_overlaps` 는 값 동일성이 아니라 **인덱스**로. **비율 분할(`train_fraction`) API 를 만들지 않는다**(F4 — 다섯 origin 이 같은 5일로 붕괴한 실측). 성숙도 `opened_count == 0` 인 주는 `IMMATURE`(측정 불가 = 통과 아님) | 5C 「시간 누수 없는 split, rolling/group holdout」 · SET-06 · F4·F4-함정 · 조사 01 §3-1·§3-4 |
| ⑥ | **판정식과 3값 결과**(`verdict.py`) — `passed = model_rmse < baseline_rmse ∧ paired_t < −policy.paired_t_threshold`, 비교 대상 `policy.gate_baseline`(`category_x_band`), 채점 모델 `gbm_all_strata`(보수 변형 `gate_stratum_only` 는 나란히 재되 판정 아님). 결과 `GateOutcome = Passed(t, mde, …) \| Failed(t, mde, required_rows) \| NotEvaluable(reason, required_rows)` — **bool 쌍 없음**. `NotEvaluable` 사유: 창 없음(`NO_EVALUABLE_WINDOW`)·MDE 미충족(`UNDERPOWERED` — `improvement_ratio < mde`)·seed 불안정(`SEED_UNSTABLE` — `¬(sign ∧ verdict consistent)`) | ML-07 acceptance ①② 「MDE 미충족이면 pass/fail 아니라 `not_evaluable`」·「성숙한 창 없으면 통과로 보이지 않음」 · F5 |
| ⑦ | **홀드아웃 실행기**(`training/holdout.py`, training 층) — 창마다 `_split_at_window`(학습 `opened_at < start`, 평가 `[start, end)`, **경계 동시각은 평가측** — 누수의 위험은 크기가 아니라 비대칭) → 두 층(`gate_train` clean-base / `train_rows` 전층)으로 5C-1 `train_award_rate_gbm` 2회 → 홀드아웃 예측은 `TrainedArtifact.booster.predict(행렬)` + 5B `build_row`(D-5C2-7) → ①~⑥. 어느 쪽이든 비면 `HoldoutRejected(EMPTY_SIDE)`(조용한 0건 평가 금지). seed 재채점: `policy.stability_seeds` 전부, **헤드라인 seed 선두**, **끄는 인자 없음**(완화 경로 #4·#6 폐쇄). 창 수 상한 `max_origins` 는 policy(CLI 없음) | 5C 「rolling/group holdout」 · 조사 01 §3-1·§3-2·§3-3 · 조사 02 §2-7 #4~#6 |
| ⑧ | **evaluation report + 승격 측정**(`report.py`) — `EvaluationReportV1`(frozen): `report_schema_version="evaluation-report-v1"` · `policy_version`+`policy_checksum` · `training_spec_version`+checksum · `feature_schema_version` · `dataset_id`·코퍼스 프로필(행 수·`opened_at` 범위·strata·categories·`feed_origin_only`) · 창별 `WindowResult`(경계·성숙도·행 수·베이스라인 5 rmse·모델 2 rmse·`GateOutcome`·MDE·required_rows·coverage·unlearned cells·segments·**창별 `release_id`**·stability) · 제외 창 목록(사유) · `holdout_overlaps` · `unaccounted_row_count`(회계 불변식 = 0) · `stability_seed_count` · `promotion = Promotable(window) \| NotPromotable(reasons) \| NotEvaluable(reason)`(**latest-window 판정 하나** — 이름에 한정, `all_origins` 는 진단 필드). canonical JSON(5B 규칙, NaN/Inf 거부) + sha256 → 2C `EvaluationReportReference{uri(5E), checksum, report_schema_version}`. **자동 배포·rollout·`recommended_env` 없음** | 완료 조건 「threshold 충족 또는 `not-promotable` 명시」 · 2C ⑧ · ML-07 acceptance ④「리포트가 정책 version 을 싣고 version 이 바뀌면 판정이 구분」 · F5 |
| ⑨ | **정책 값 실물**(`policy/evaluation-v1.yaml` + `evaluation/policy.py`) — 표는 policy-values.md. 불변식: `paired_t_threshold > 0`·`0 < maturity_threshold ≤ 1`·`min_evaluation_rows ≥ 1`·`max_origins ≥ 1`·`stability_seeds` 비어 있지 않음·`amount_band_edges` 오름차순 양수. **`EvaluationPolicy` 를 받는 함수만 있고 임계를 낱개 인자로 받는 public 함수는 없다**(완화 경로 #1·#2 폐쇄 — `WindowPolicy` 주입·`build_verdict(threshold=)` 부재) | ML-07 acceptance ③「정책 산출물에 존재, 코드 리터럴 아님」·④「CLI·env·요청으로 완화 불가」 · ADR 0006 D-7 · 조사 02 §2-6·§2-7 |
| ⑩ | **재현성** — 같은 `LoadedDataset`·maturities·spec·정책·seed 로 두 번 → report canonical bytes **동일**(창별 `release_id` 도 동일 — 5C-1 결정적 파생). 실 LightGBM 1건 + fake trainer 구조 test | 완료 조건 「재현 가능한 metric」 · 5C-1 D-5C-12 |

**만들지 않는 것**: 비율 분할 · 임계 낱개 인자·CLI·env · seed 안정성 끄기 · bool 쌍 판정 · 릴리스 간 비교 · 승격 실행·rollout·`skip_*` 우회 · 성숙도 계산(K7) · 서빙 가드(5D) import · report 저장(5E) · `except Exception` · `dict[str, Any]`.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5C2-1** | **층 배치** — 순수 커널(채점·베이스라인·세그먼트·진단·창 정책·판정·report 타입·정책)은 `evaluation/`, 창마다 학습을 부르는 실행기만 `training/holdout.py`. import-linter layers 가 `training > evaluation > features` 라 evaluation 은 training 을 import 할 수 없고, 둘 다 `inference` 금지(5C-1 forbidden). 실행기가 evaluation 을 import 하는 방향만 열려 있다 | `pyproject.toml` layers·forbidden 계약(5A·5C-1) | **운영자 확인**(추천 위 · 대안: `evaluation` 을 training 위 층으로 재배치 — 5A 계약 편집, 5D·5E 영향) |
| **D-5C2-2** | **성숙도는 입력** — `WeekMaturity(start, end, opened_count, settled_count)` 를 호출자가 준다(5E/M6 이 5D K7 `build_weekly_maturity` 로 만든다). evaluation 안에서 비율 `settled/opened` 를 계산하는 3줄이 K7 `Observed.ratio` 와 **중복**된다 — 알려진 제한(`OPEN-5C-MATURITY-SOURCE` 종결 — 대안 「K7 을 `features` 층으로 이전」은 5D 파일 이동이라 2F/5E 후보 등재) | ADR 0001 §4.1(성숙도 커널 = 5D) · SET-06(계산/판정 분리) · layers | 계약 고정 |
| **D-5C2-3** | **임계는 전부 `policy/evaluation-v1.yaml`**(legacy 코드 상수 → 정책 데이터, 값 무변경): `paired_t_threshold 2.58`·`maturity_threshold 0.70`·`min_evaluation_rows 100`·`max_origins 5`·`gate_stratum clean-base`·`gate_baseline category_x_band`·`agency_baseline_min_count 10`·`stability_seeds [20260812,1,7,42,2026]`·`amount_band_edges [1e8,5e8,1e9,5e9]`. legacy 는 「CLI 를 두지 않는 이유」로 코드 상수를 택했고 ML-07 acceptance ③ 은 「정책 산출물, 리터럴 아님」이라 **둘의 교집합 = 로더로만 읽는 versioned 파일 + 완화 표면 없음**. report 가 `policy_version`·`policy_checksum` 을 싣는다 | ML-07 acceptance ③④ · ADR 0006 D-7 · 조사 02 §2-6·톱10 #11 | **운영자 확인**(추천 위 · 대안: 코드 선언 `EvaluationSpec`(5C-1 D-5C-2 형태) — 임계는 artifact 에 박히는 값이 아니라 판정 정책이므로 D-7 이 우선) |
| **D-5C2-4** | 판정 어휘 3값 `Passed \| Failed \| NotEvaluable(reason, required_rows)` — legacy 의 bool 셋 조합(`gate_evaluable`·`gate_passed_at_latest_window`·`…_all_origins`)을 타입으로. `NotEvaluable` 사유 enum: `NO_EVALUABLE_WINDOW`·`UNDERPOWERED`·`SEED_UNSTABLE`. **`UNDERPOWERED` 는 legacy 가 콘솔 요약에서만 하던 판정을 결과 타입으로 승격**(ML-07 acceptance ① 문면) | ML-07 ①② · F5 · 조사 02 §1-5 (b) | 계약 고정 |
| **D-5C2-5** | seed 재채점은 **끌 수 없다**(`--no-stability` 대응물 없음) — `stability_seeds` 는 정책 필수, 헤드라인 seed 가 목록 선두(중복 제거). `stability_seed_count` 를 report 에 실어 「재지 않았다」가 표현 불가 | 조사 02 §2-7 #4·#6 | 계약 고정 |
| **D-5C2-6** | 승격 측정은 **latest-window 하나**의 `GateOutcome` 에서 파생(`Promotable(window_start) \| NotPromotable(reasons) \| NotEvaluable(reason)`), `all_origins` 판정은 진단 필드. 운영 승격은 운영자 결정(2C ⑧ 「참조는 입력일 뿐 승격이 아니다」) | 완료 조건 「`not-promotable` 명시」 · 2C ⑧ · 조사 02 §2-3(릴리스 간 비교 부재) | 계약 고정 |
| **D-5C2-7** | **홀드아웃 예측은 `TrainedArtifact.booster` 직접 호출**(+ 5B `build_row`) — legacy 의 「직렬화·복원·서빙과 같은 경로」(artifact → `load_artifact` → `predict_bid_rates`)는 evaluation/training 이 `inference` 를 import 못 해 **재현 불가**. 잃는 것: 직렬화 왕복이 수치를 바꾸지 않는다는 증거(5C-1 왕복 test 가 바이트 동일·필드 통과까지는 증명) · 서빙 미학습 가드 통과(legacy 도 우회). → `OPEN-5C2-SERVING-PATH-PARITY`(5E 통합 test: 같은 창의 booster 직접 예측 vs `predict_bid_rates` 후보 중심값 일치) | layers·forbidden · 조사 02 §1-5 (g) | **운영자 확인**(추천 위 · 대안: 실행기를 `serving` 층으로 — 5E 소유, 착수 순서 역전) |
| **D-5C2-8** | 시간 분할 경계 — 학습 `opened_at < window.start`, 평가 `[start, end)`, **경계 동시각 행은 평가측**(누수는 GBM 에만 붙는 비대칭 이득). 학습 구간 내부 OOF 시간 방향은 5C-1 D-5C-4 그대로(요구 안 함). 데이터 생성 측 cutoff 낙관(라벨 가용 시점)은 dataset manifest 가 `opened_at_last` 를 나르므로 report 코퍼스 프로필에 그대로 실린다 — 방어 아님, 공시 | 조사 01 §3-1·§3-3 · 5C-1 D-5C-4 | 계약 고정 |
| **D-5C2-9** | 세그먼트 축은 `category`·`amount_band` 둘 — `published_floor` 축 **없음**(`FeatureFacts` 에 없고, legacy 에서 백필 커버리지의 함수라 학습·서빙 의미가 달랐다 — F3 판단 기준 「축의 의미가 다르면 쓰지 않는다」). `OPEN-5C-SEGMENT-PUBLISHED-FLOOR` 종결 | F3 · 조사 01 §12 | **운영자 확인**(추천 위 · 대안: 진단 전용 축으로 유지 — 입력에 그 필드가 없어 불가) |
| **D-5C2-10** | 완화 경로 폐쇄 표(조사 02 §2-7 대응): #1 `WindowPolicy` public 필드 → `EvaluationPolicy` 는 로더만 생성(Python 컨벤션, (2b) 등재) · #2 `build_verdict(threshold=)` → 임계 인자 없는 `gate_outcome(scores, policy)` · #3 `stratum=` 키워드 → policy 값만 · #4 `--no-stability` → 없음 · #5 `--max-origins` → policy · #6 `--seed` → 헤드라인 seed 는 spec(5C-1)이고 안정성 목록에 항상 포함 · #7 `--folds` → spec · #8 `feed_origin_only` → dataset manifest 기록값(평가가 바꾸지 못함) · #9 서빙 가드 → D-5C2-7 등재 | ML-07 acceptance ④ | 계약 고정 |
| **D-5C2-11** | 이식 출처 — `award_rate_scoring.py`·`award_rate_diagnostics.py`·`award_rate_windows.py`·`award_rate_holdout.py`·`award_rate_backtest_report.py`(`app/services/ml_training/`, `ed4b06c`) → `Reuse:` 포인터 5 모듈(scoring·diagnostics·windows·holdout·report). baselines·segments 는 scoring 에서 갈라 나온 것이라 같은 원본 포인터. verdict·policy 는 신규 | ADR 0009 D-6 | 계약 고정 |
| **D-5C2-12** | `HoldoutRejected.reason`·`NotEvaluable.reason` → 2C `JobFailureCode` 매핑 표를 checklist 에(5C-1 D-5C-11 과 같은 형식, `EVALUATION_ERROR` + `detail_code`). proto 무편집 | 2C D-2C-4 | 계약 고정 |

---

## 위협 모델 — 5C-2 고유 경계

**방어한다**: (a) 임계 완화 표면 — 정책 파일 + 로더 전용 타입, 낱개 인자·CLI·env 없음 (b) 「못 쟀다」가 통과로 읽힘 — 3값 결과, `NotEvaluable` 은 `Promotable` 이 될 수 없다(타입) (c) 시간 누수 — 창 경계 반개구간·동시각 평가측·`holdout_overlaps` 측정·`unaccounted_row_count == 0` 불변식 (d) 조용한 0건 평가 — 한쪽 비면 `HoldoutRejected` (e) 비율 분할 — API 부재 (f) seed 로 통과 만들기 — 안정성 필수·헤드라인 포함·`SEED_UNSTABLE` (g) 검정력 없는 통과 — `UNDERPOWERED` (h) 베이스라인 무력화(얕은 기관 자기 평균) — `agency` min_count · unlearned cells 공시 (i) 매직넘버 — 정책 부재 시 `PolicyRejected` (j) 재현 불가 report — canonical bytes 동일 test (k) 예외로 실패 — 결과 타입(`BLE`).
**방어하지 않는다**: 성숙도 값의 옳음(K7·호출자) · dataset cutoff 의 라벨 가용 낙관(생성 측, 공시만) · 학습 구간 내부 OOF 시간 방향(D-5C-4) · 직렬화 왕복 수치 동일성·서빙 가드(D-5C2-7, `OPEN-5C2-SERVING-PATH-PARITY`·`OPEN-5C2-UNLEARNED-GUARD`) · 정책 값 내용 · Python 가시성(직접 생성 컨벤션) · 릴리스 간 회귀.

**우회 후보(≥5)**: (1) `EvaluationPolicy(...)` 직접 생성으로 임계 0.5 → Python 한계, (2b) 등재 + report 가 `policy_checksum` 을 실어 **어떤 정책으로 판정했는지가 report 에 남는다**(위조하면 checksum 이 출하 YAML 과 다르다 — 5E 가 대조) (2) 성숙도 입력을 `opened=settled=1` 로 위조 → 방어 밖(호출자 신뢰) — report 가 창별 `(opened, settled)` 를 실어 공시 (3) 창 하나만 넣어 `all_origins == latest` → `max_origins`·제외 목록·`stability` 가 report 에 (4) 학습 행에 평가 창 행 섞기 → `_split_at_window` 가 유일 분할, `holdout_overlaps`·회계 불변식 test (5) MDE 계산에 `n=1` → 0.0 반환(legacy 경계) → `UNDERPOWERED`(`improvement < 0.0` 은 거짓이므로 **별도 규칙**: `n < 2 → NotEvaluable(NO_EVALUABLE_WINDOW)`) — 설계 검토 항목 (6) `required_rows` 를 0 으로 채움 → `None` 유지, 타입 `int | None` (7) 정책 YAML 미지 키·`stability_seeds: []`·`maturity_threshold: 0` → `PolicyRejected` (8) evaluation 이 training 을 import(실행기를 evaluation 에 두기) → layers 위반 → lint-imports 붉음 (9) `paired_t` 의 std 0 → 0 반환 → `Failed`(통과 아님) (10) 세그먼트 1행 통과 → `regressed_segments` 제외 규칙 + 세그먼트 합 = 전체 회계 test (11) 헤드라인 seed 를 안정성 목록에서 빼기 → 목록 조립이 선두 고정(test) (12) report 의 `promotion=Promotable` 을 `NotEvaluable` 창에서 → 파생 함수 하나(`derive_promotion(latest: GateOutcome)`), `match` 소진.

---

## (2b) 값 획득 축 (Python)

| 표면 | 판정 |
| --- | --- |
| `EvaluationPolicy`(frozen)·`load_evaluation_policy`·`SHIPPED_EVALUATION_POLICY_VERSION`·`policy_checksum` | 연다 — 로더만 생성(컨벤션, Python 한계). **이 타입이 완화 seam 이다** — legacy `WindowPolicy` 와 같은 자리이나 값은 파일에서만 오고 report 가 checksum 을 남긴다 |
| `rmse_bias_std`·`paired_t`·`improvement_ratio`·`minimum_detectable_improvement`·`required_row_count` | 연다 — 순수, 임계 인자 없음(`required_row_count` 는 `policy` 를 받음) |
| `group_mean_predictions`·`segment_scores`·`coverage_splits`·`unlearned_cells`·`summarize_stability` | 연다 — 순수, spec 은 policy 에서 |
| `WeekMaturity`·`plan_evaluation_windows(rows, maturities, policy) -> WindowPlan` | 연다 — 성숙도 입력은 호출자 신뢰(방어 밖, report 공시) |
| `gate_outcome(scores, policy) -> GateOutcome` | 연다 — 유일 판정 진입점, 임계 인자 없음 |
| `EvaluationReportV1`·`canonical_report_bytes`·`report_checksum`·`derive_promotion` | 연다 — 읽기·순수 |
| `run_holdout(...) -> EvaluationReportV1 \| HoldoutRejected`(training 층) | 연다 — 유일 실행 진입점. `trainer` 주입은 5C-1 `TrainerLike`(고정 항목: 주입되는 것은 모델 실물뿐, 판정 함수·임계 주입 없음) |
| 「경계로 처리」 행: 성숙도 입력·dataset cutoff | 실측 목록에 넣는다 — 위조 입력이 report 에 그대로 공시되는지(값을 숨기지 않는지) test |

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-5C2-POLICY-VALUES` | D-5C2-3 표 — 착수 시 승인 대상(전부 legacy 코드 상수, 지어낸 값 0) |
| `OPEN-5C2-SERVING-PATH-PARITY` | D-5C2-7 — 5E 통합 test(booster 직접 예측 vs `predict_bid_rates` 중심값) |
| `OPEN-5C2-UNLEARNED-GUARD` | 평가 경로가 5D `segment_availability` 를 지나지 않는다(legacy 동일) — 5E 에서 같은 정책 값으로 창별 미학습 공종 행 수를 report 에 공시할지 결정 |
| `OPEN-5C-MATURITY-SOURCE` | D-5C2-2 로 **종결**(입력) — K7 `features` 이전은 2F/5E 후보로 등재 |
| `OPEN-5C-BUDGET-BAND-SOURCE` | ② 로 **종결**(정책 데이터 `amount_band_edges`) |
| `OPEN-5C-SEGMENT-PUBLISHED-FLOOR` | D-5C2-9 로 **종결**(축 없음) |
| `OPEN-5C-REJECT-ACCOUNTING`(5C-1 L-8) | 5C-2 는 `TrainedArtifact.rejected_rows`(성공 경로)를 창별 report 에 공시 — `TrainingRejected` 의 회계 필드 추가는 5C-1 파일 편집이라 **미해소 유지**(5E 전) |
| `OPEN-5C-CORPUS` | 승계 — 5C-2 test 는 합성 코퍼스(규칙·불변식 판정), 실코퍼스 evaluation fixture 는 curator |
| `OPEN-5D2-POLICY-VALUES`(`agency_sample_threshold`, 5C 재학습 지표 뒤) | 5C-2 report 가 낼 지표로 값 근거를 만들 수 있게 됨 — 결정은 운영자, 5E 전 |
| `OPEN-5D2-BID-RATE-UPPER`(표본 밴드 1.5 vs D-2B-8) | 5C-2 코퍼스 실측(라벨 분포 최댓값)을 report 코퍼스 프로필에 실어 근거 제공 — 결정은 2F |
| `OPEN-ML-02`·`OPEN-5C-OOF-TIME-DIRECTION`·`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`·`OPEN-5C-YAML-ERROR-5D`·`OPEN-5C-5A-TABLE-REASSIGN` | 승계·무변경 |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 (verifier r1 · code-reviewer 뒤) | **⑤ 문면 재확인** — `min_evaluation_rows` 하한은 창 안 행 수가 아니라 **`build_row` 를 통과해 실제로 채점되는 행 수**(paired_t 의 n)에 건다(창 계획 단계의 선검사는 조기 제외로 남기되 buildability 필터 뒤 **재대조가 필수** — 5C-1 ③ 과 같은 계열). 버려진 행은 창별 `WindowResult.dropped_rows`(사유별 계수, `RejectedRowAccounting` 재사용)로 공시하고 `unaccounted_row_count` 회계에서 「회계됨」으로 접지 않는다. **⑥ 문면 재확인** — 판정 술어는 `verdict.py` 하나(`_passes`), 안정성 sweep 도 그 함수를 호출(이중 구현 금지). `trial_outcome` 은 **public 아님**(`_trial_outcome`, 안정성 sweep 내부 전용) — `Promotable` 은 `gate_outcome` 결과에서만. (2b) 표 갱신. **⑨ 보강** — 정책 로더는 `.nan`/`.inf`·비유한 값을 `PolicyRejected` 로(불변식 비교 전 유한성 검사), `policy_checksum` 은 로더 통과 값만 받는다. **게이트 보강** — 숫자 리터럴 slice test 를 `evaluation/**` 도 덮게(설계 검토 (5)-10), 설계 검토 (1) 표의 「닫는다」 기제 아홉에 각각 변이를 붉히는 test(경계 `<`·`contains`·인덱스 겹침·`unaccounted` 계측·정책 참조·헤드라인 seed 선두·MDE 배수·임계 리터럴). in_scope 추가: `ml-engine/tests/training/test_no_stray_numeric_literals.py`(기존 5C-1 test 확장) 또는 `tests/evaluation/test_no_stray_numeric_literals.py`. `OPEN-5C-REJECT-ACCOUNTING` 처분 통일: 5C-2 는 **성공 경로의 `rejected_rows` 와 창별 `dropped_rows` 를 report 에 공시**, `TrainingRejected` 의 회계 필드는 5C-1 파일이라 미해소 유지 | verifier r1 H-1·H-2·H-3·M-1·M-2·M-4 · code-reviewer H-1·M-1·M-2 |
| 2026-09-15 착수 | 초판 — 결정 12, 5C-1 인수 OPEN 처분 | 사용자 「5C-2 착수」 · 조사 노트 01·02 |
