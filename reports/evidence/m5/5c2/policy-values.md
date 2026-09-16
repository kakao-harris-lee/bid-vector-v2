# M5/5C-2 정책 값 — `OPEN-5C2-POLICY-VALUES`(**승인 2026-09-16 — 착수 2026-09-15 시점엔 승인 대기**)

> **지위: 승인(2026-09-16, 운영자 「둘 다 승인」 — M5 종결 판정 `reports/evidence/m5/closure/checklist.md` §3 ④, `OPEN-5C2-POLICY-VALUES` 종결). 값 근거는 legacy-behavior, 재조정은 실 코퍼스 재학습 slice.** 실물은 `ml-engine/policy/evaluation-v1.yaml`(평탄 키, `PolicyScalar` 제약 — 목록은 쉼표 구분 문자열이 아니라 **YAML 시퀀스**를 5A 로더가 거부하므로, 목록 값 둘은 `stability_seeds`·`amount_band_edges` 를 **평탄 인덱스 키**(`stability_seeds.0 …`) 로 둔다 — 로더가 접두로 모아 tuple 로 조립), 로더 `load_evaluation_policy` 가 `known_keys` 전수·값 불변식으로 대조하고 test 가 이 표와 YAML 을 대조한다. 값을 바꾸려면 이 문서 → YAML → test 순. **전부 legacy 코드 상수(`ed4b06c`)의 무변경 이식 — 5C-2 가 지어낸 수치 0.** ML-07 acceptance ③ 「임계는 정책 산출물에 존재하고 코드 리터럴로 존재하지 않는다」의 이행.

## §1 정책 값(YAML)

| 키(평탄) | 값 | legacy 좌표 | 층 |
| --- | --- | --- | --- |
| `version` | `evaluation-v1` | — (report `policy_version` 으로 실림) | — |
| `paired_t_threshold` | `2.58` | `app/services/ml_training/award_rate_holdout.py` `GATE_PAIRED_T_THRESHOLD`(양측 1% — 「사후에 느슨하게 만들지 않는다」) | legacy-behavior |
| `gate_baseline` | `category_x_band` | `award_rate_scoring.py` `GATE_BASELINE_NAME` | legacy-behavior |
| `gate_model` | `gbm_all_strata` | `award_rate_holdout.py` `GATE_MODEL_NAME`(보수 변형 `gbm_gate_stratum_only` 는 나란히 재되 판정 아님) | legacy-behavior |
| `gate_stratum` | `clean-base` | `award_rate_windows.py` `GATE_STRATUM` | legacy-behavior |
| `maturity_threshold` | `0.70` | `award_rate_windows.py` `GATE_MATURITY_THRESHOLD`(embargo — 「손잡이가 아니다, CLI 플래그를 두지 않는 이유도 같다」) | legacy-behavior |
| `min_evaluation_rows` | `100` | `award_rate_windows.py` `GATE_MIN_EVALUATION_ROWS`(「하한만 선언」 — 145행 창 seed 부호 반전 실측이 정규근사 근거를 반증, 검정력은 창별 리포트 몫) | legacy-behavior |
| `max_origins` | `5` | `award_rate_windows.py` `GATE_MAX_ORIGINS`(legacy 유일 CLI 노출 — V2 는 CLI 없음) | legacy-behavior |
| `agency_baseline_min_count` | `10` | `award_rate_scoring.py` `AGENCY_BASELINE_MIN_COUNT`(얕은 기관 자기 평균이 베이스라인을 부당하게 나쁘게 만든다) | legacy-behavior |
| `stability_seeds.0` ~ `.4` | `20260812, 1, 7, 42, 2026` | `award_rate_diagnostics.py` `GATE_STABILITY_SEEDS`(헤드라인 seed 20260812 = 5C-1 spec `seed` — 조립 시 선두 고정) | legacy-behavior |
| `amount_band_edges.0` ~ `.3` | `100000000, 500000000, 1000000000, 5000000000` | `app/services/synthetic_experiment/constants.py` 밴드 경계(lt_1eok/1eok_5eok/5eok_10eok/10eok_50eok/gte_50eok) — legacy 는 업무 모듈 private 이름을 교차 import(`award_rate_scoring.py`), V2 는 정책 데이터로(`OPEN-5C-BUDGET-BAND-SOURCE` 종결) | legacy-behavior |
| `segment_axes.0` ~ `.1` | `category, amount_band` | `award_rate_scoring.py` `SEGMENT_SPECS` 두 축 중 `category` 이식, `published_floor` 는 **없음**(D-5C2-9 — `FeatureFacts` 에 없고 백필 커버리지의 함수) · `amount_band` 는 베이스라인 축을 세그먼트로도 씀(신규 축 — 운영자 확인) | 이식 1 + 신규 1 |

## §2 정책이 아닌 것(같은 자리에 두지 않는다)
- `encoding_folds 5`·`seed 20260812`·`min_residual_std 0.002`·LightGBM 파라미터 — 5C-1 `TrainingSpec`(artifact 에 박히는 값).
- `min_training_rows 500` — 5C-1 `training-v1.yaml`(창마다 학습에 적용된다 — 설계 검토 (15)).
- `maturity.window_days 7` — 5D `inference-v1.yaml`(K7 계산 측). 5C-2 는 주 창을 **입력**으로 받는다(D-5C2-2).
- `min_category_rows 40`(미학습 가드) — 5D. 평가 경로 미적용(`OPEN-5C2-UNLEARNED-GUARD`).
- 승격 게이트 B 계보 env 5·프리셋 4 — 이식 없음(D-5C-1).

## §3 불변식(로더가 거부)
`paired_t_threshold > 0` · `0 < maturity_threshold ≤ 1` · `min_evaluation_rows ≥ 2`(설계 검토 (16) — `paired_t` ddof=1) · `max_origins ≥ 1` · `agency_baseline_min_count ≥ 1` · `stability_seeds` 비어 있지 않음·정수·중복 없음 · `amount_band_edges` 양수·엄격 오름차순 · `segment_axes` ⊆ {category, amount_band}·중복 없음 · `gate_baseline` ∈ 베이스라인 표 이름 · `gate_stratum` 비어 있지 않음.

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-15 착수 | 표 등재(승인 대기) | D-5C2-3 · 조사 02 §2-6 |
| 2026-09-16 승인 | 지위 「승인 대기」→「승인」(값·표 무변경) | 운영자 「둘 다 승인」 — M5 종결 판정 `reports/evidence/m5/closure/checklist.md` §3 ④ |
| 2026-09-15 구현 | `policy/evaluation-v1.yaml` 실물 작성·로더(`evaluation/policy.py`) 구현 완료 — 값 §1 표와 완전 일치, 재학습·튜닝 근거로 인한 값 변경 0건 | `test_evaluation_policy.py::test_shipped_policy_file_matches_policy_values_md`(값 11개 하드코딩 대조) |
