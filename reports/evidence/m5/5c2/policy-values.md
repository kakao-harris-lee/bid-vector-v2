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
| `effective_date` | `"2026-09-16"` | **M9/ml-lifecycle 신설**(ML-07 acceptance ③ 의 「effective date 를 가진 정책 산출물」). 값은 **이 표의 승인 날짜**다 — 아래 change_history 2026-09-16 행(운영자 「둘 다 승인」, M5 종결 판정 `reports/evidence/m5/closure/checklist.md` §3 ④). 새 임계가 아니라 **그 승인 기록을 기계가 읽게 적은 것**이고, 효력 시작 전에 시작한 평가 창의 통과는 승격 근거로 인용되지 않는다. **인용 필수** — 비인용 ISO 날짜는 YAML 이 `date` 객체로 읽어 정책 값 축(문자열·수·bool) 밖이고 로더가 거부한다 | 승인 기록 파생(지어낸 날짜 아님) |
| `segment_axes.0` ~ `.1` | `category, amount_band` | `award_rate_scoring.py` `SEGMENT_SPECS` 두 축 중 `category` 이식, `published_floor` 는 **없음**(D-5C2-9 — `FeatureFacts` 에 없고 백필 커버리지의 함수) · `amount_band` 는 베이스라인 축을 세그먼트로도 씀(신규 축 — 운영자 확인) | 이식 1 + 신규 1 |

## §2 정책이 아닌 것(같은 자리에 두지 않는다)
- `encoding_folds 5`·`seed 20260812`·`min_residual_std 0.002`·LightGBM 파라미터 — 5C-1 `TrainingSpec`(artifact 에 박히는 값).
- `min_training_rows 500` — 5C-1 `training-v1.yaml`(창마다 학습에 적용된다 — 설계 검토 (15)).
- `maturity.window_days 7` — 5D `inference-v1.yaml`(K7 계산 측). 5C-2 는 주 창을 **입력**으로 받는다(D-5C2-2).
- `min_category_rows 40`(미학습 가드) — 5D. 평가 경로 미적용(`OPEN-5C2-UNLEARNED-GUARD`).
- 승격 게이트 B 계보 env 5·프리셋 4 — 이식 없음(D-5C-1).

## §3 불변식(로더가 거부)
`paired_t_threshold > 0` · `0 < maturity_threshold ≤ 1` · `min_evaluation_rows ≥ 2`(설계 검토 (16) — `paired_t` ddof=1) · `max_origins ≥ 1` · `agency_baseline_min_count ≥ 1` · `stability_seeds` 비어 있지 않음·정수·중복 없음 · `amount_band_edges` 양수·엄격 오름차순 · `segment_axes` ⊆ {category, amount_band}·중복 없음 · `gate_baseline` ∈ 베이스라인 표 이름 · `gate_stratum` 비어 있지 않음 · `effective_date` 는 ISO-8601 날짜 **문자열**(M9/ml-lifecycle).

## §4 승인된 산출물 지문 (M9/ml-lifecycle, 2026-11-06)

| version | 승인된 파일 내용 sha256 |
| --- | --- |
| `evaluation-v1` | `fba628efb97b313ff511504e942a11ca5dc709c454a34ff73d84b09ae4670855` |

이 표가 생긴 이유: 앞 판은 **version 문자열이 내용에 묶여 있지 않았다**. 임계만 완화한 사본을
같은 `evaluation-v1` 이름으로 로드하면(코드가 써도, `ML_ENGINE_EVALUATION_POLICY` 로 가리켜도)
모든 게이트가 초록이었다 — verifier 실측(M9/ml-lifecycle 수정 라운드 2, v H-2). 지문은
`ml_engine.registry.policy_approvals` 의 코드 상수 등록부와 같은 값이다.

**어느 층이 무엇을 막는가**(cr r3 M-2 정정 — 앞 판은 이 거부를 **뿌리 로더**에 귀속했는데
그 검사는 같은 라운드에 옮겨졌다):
- **조립 근**(`app.server._preload` → `evaluation.policy_source.load_approved_evaluation_policy`)
  이 미승인 산출물을 거부한다 — 이름을 유지한 편집 사본과 이름을 바꿔 단 사본 **둘 다**.
- **게이트 자리**(`evaluation.policy_source.verify_policy_source`, `app.pipeline._run_holdout`
  가 부른다)가 **다시** 묻는다 — 부팅 뒤에 정책 객체를 바꿔 끼우는 길이 있기 때문이다
  (수정 라운드 3, v H-3a).
- **CI** 가 출하 파일 자체를 고치는 길을 막는다 — 아래 지문 등식 test.
- 뿌리 로더(`registry.policy.load_policy`)는 승인을 **묻지 않는다**(사유는 그 모듈 docstring —
  승인 밖 조합으로 로더를 검사하는 값 불변식 test 들이 재던 것을 잃는다).

**이 version 의 값을 바꾸려면 세 자리를 같은 커밋에서 바꿔야 한다** — ⑴ §1 표(이 문서)
⑵ `ml-engine/policy/evaluation-v1.yaml` ⑶ 등록부 한 줄. 어긋나면 test 가 붉다
(`tests/registry/test_policy_approvals.py`). 지문은 **주석·공백까지 포함한 파일 내용**의
해시다(그 자리에 근거가 적히므로 의도된 성질이다).

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-15 착수 | 표 등재(승인 대기) | D-5C2-3 · 조사 02 §2-6 |
| 2026-09-16 승인 | 지위 「승인 대기」→「승인」(값·표 무변경) | 운영자 「둘 다 승인」 — M5 종결 판정 `reports/evidence/m5/closure/checklist.md` §3 ④ |
| 2026-09-15 구현 | `policy/evaluation-v1.yaml` 실물 작성·로더(`evaluation/policy.py`) 구현 완료 — 값 §1 표와 완전 일치, 재학습·튜닝 근거로 인한 값 변경 0건 | `test_evaluation_policy.py::test_shipped_policy_file_matches_policy_values_md`(값 11개 하드코딩 대조) |
| 2026-11-06 신설 | `effective_date` 한 칸 추가(**임계 11개 무변경**). M9/ml-lifecycle 이 ML-07 ③ 을 이행하며 더했고, 이 문서를 갱신하지 않은 채 YAML·test 만 고친 것을 code-reviewer M-4 가 지적해 **같은 라운드에서** 등재했다(절차는 머리말대로 이 문서 → YAML → test). 값의 지위: 위 2026-09-16 승인 날짜의 파생이고, **effective date 로서의 별도 승인은 받지 않았다** — 사용자 확인 항목 | `test_shipped_policy_file_matches_policy_values_md`(값 **12개** 대조) · verifier M-2 |
