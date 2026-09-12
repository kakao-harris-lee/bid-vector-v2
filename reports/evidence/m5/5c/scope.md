# Slice 계약 — M5 / 5C-1 · training kernel(낙찰률 GBM 학습·artifact writer·dataset 입구) — **착수 계약 2026-09-12(운영자 결정 대기: D-5C-1~4·7·9)**

> **지위**: 2026-09-12 착수판(세션 모델 단독, Fable 5.1). 조사 노트 `_workspace/m5-5c/01_scout_training.md`(학습 파이프라인 축)·`02_scout_evaluation.md`(평가·승격·재현성 축) —
> 둘 다 legacy `ed4b06c`. **Phase 2.5 설계 검토 대상**(fail-closed 입구·결과 타입·재현성 불변식): `_workspace/m5-5c/03_design-review.md`.
> **5C 를 둘로 가른다(D-5C-0)** — **5C-1** 학습 커널(이 계약) · **5C-2** 평가·홀드아웃·승격 측정·evaluation report(별도 계약, 5C-1 종결 뒤). 이유는 「계약 고정 결정」 D-5C-0.
> 레인: worktree `bid-vector-v2-m5c`, 브랜치 `m5-5c/2026-09-12`. 병행 레인 **5D**(`bid-vector-v2-m4e`, `m5-5d/2026-09-12`)와 소스 겹침은 `ml-engine/pyproject.toml`·`ml-engine/tests/conftest.py`·`milestone-5.md` 셋뿐 — 「레인 격리」 절.

```yaml
milestone: m5
slice: 5c1-training-kernel
base_sha: f5020aa982e6c5ade01f1660c0568dcd75c2fa7e   # PR #10 머지 커밋 = origin/main(5A·5B 실물 포함, 5D 미포함)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/training/__init__.py            # 공개 표면 재수출(아래 (2b) 표가 전수)
  - ml-engine/src/ml_engine/training/spec.py                # TrainingSpec(frozen, versioned) · TRAINING_SPECS 등록표 · resolve_training_spec(version) -> TrainingSpec | UnsupportedTrainingSpec · spec_checksum(spec) (canonical JSON sha256)
  - ml-engine/src/ml_engine/training/corpus.py              # TrainingRow(facts: FeatureFacts, label: AwardRateLabel, opened_at, stratum) · AwardRateLabel(value ∈ [0,1] 닫힌 구간, 생성 함수만) · admit_corpus(rows) -> AdmittedCorpus | CorpusRejected — 라벨 도메인·행 거부 회계(RejectedRowAccounting: 사유별 계수, 조용한 drop 0)
  - ml-engine/src/ml_engine/training/dataset.py             # DatasetManifestV1(dataset_id, sample_scope(require_declared), feed_origin_only, row_count, rows_checksum, opened_at 범위, feature_schema_version) · load_dataset(manifest_bytes, rows_bytes, expected: DatasetReference 값) -> LoadedDataset | DatasetRejected(reason ∈ CHECKSUM_MISMATCH | UNREADABLE | SCHEMA_UNSUPPORTED | ID_MISMATCH | ROWS_CHECKSUM_MISMATCH)
  - ml-engine/src/ml_engine/training/folds.py               # fold_indices(row_count, folds, seed) — legacy `_fold_indices` 이식(rng 계약 고정: numpy default_rng(seed).permutation → array_split)
  - ml-engine/src/ml_engine/training/matrix.py              # build_training_matrix(space, rows) -> TrainingMatrix(ndarray float, labels, row_provenances) — 5B `AwardRateFeatureSpace.build_row` 결과만 소비, `RowRejected` 는 회계로
  - ml-engine/src/ml_engine/training/encoding_oof.py        # 두 인코딩을 이름으로 가른다 — out_of_fold_matrix_and_residuals(rows, folds, seed, policy, trainer) (학습 행렬용 OOF + OOF 잔차) · full_corpus_feature_space(rows, policy) (artifact 탑재용 전 구간 표) — 5B `build_agency_target_encoding` 재사용, `NoObservations` 는 `TrainingRejected(NO_OBSERVATIONS)`
  - ml-engine/src/ml_engine/training/booster.py             # TrainerLike Protocol(train(matrix, labels, feature_names, categorical_indices, params, seed, rounds) -> BoosterLike) · LightGbmTrainer(실물, lightgbm 은 이 모듈만 import) · booster_to_text(BoosterLike) -> str
  - ml-engine/src/ml_engine/training/residual.py            # residual_std(residuals, floor) — legacy `_residual_std` 이식(하한 `spec.min_residual_std`)
  - ml-engine/src/ml_engine/training/artifact_writer.py     # write_artifact(trained, release_inputs) -> ArtifactBytes(bytes, sha256, release: ReleaseIdentity) — 5D scope ⑦ `ArtifactManifestV1` 필드 집합 그대로(D-5C-9 예외 하나) · canonical JSON(5B `canonical_json` 규칙 동일: 키 정렬·구분자·float repr·NaN/Inf 거부)
  - ml-engine/src/ml_engine/training/release.py             # ReleaseIdentity(release_id, feature_schema_version, code_version, dataset_id) · derive_release_id(dataset_id, training_spec_version, spec_checksum, seed, code_version) — 결정적(sha256 앞 16 hex)
  - ml-engine/src/ml_engine/training/train.py               # train_award_rate_gbm(dataset: LoadedDataset, spec: TrainingSpec, policy: TrainingPolicy, trainer: TrainerLike, code_version: CodeVersion) -> TrainedArtifact | TrainingRejected(reason, detail) — 유일 학습 진입점
  - ml-engine/src/ml_engine/training/policy.py              # TrainingPolicy(min_training_rows, encoding: EncodingPolicy 참조) + load_training_policy(path) -> TrainingPolicy | PolicyRejected(5A load_policy 위, known_keys 전수, 불변식 min_training_rows ≥ 1) · SHIPPED_TRAINING_POLICY_VERSION = "training-v1"
  - ml-engine/src/ml_engine/adapters/__init__.py            # 경계 한 줄 갱신(파일 storage 만)
  - ml-engine/src/ml_engine/adapters/dataset_files.py       # read_dataset_files(uri) -> DatasetFiles(manifest_bytes, rows_bytes) | DatasetUnreadable(reason ∈ UNSUPPORTED_SCHEME | NOT_FOUND | NOT_A_DIRECTORY) — `file://` 만(D-5C-8), 네트워크 0
  - ml-engine/policy/training-v1.yaml                       # 정책 값 실물(D-5C-7 승인 값) — `OPEN-5C-POLICY-VALUES`
  - ml-engine/tests/training/**                             # RED 먼저 — 규칙표·property(hypothesis)·재현성(같은 입력 두 번 → 같은 바이트)·legacy_parity(관측, 판정 아님)·5D 필드 집합 계약 test
  - ml-engine/tests/adapters/**
  - ml-engine/tests/gates/fixtures/bad_features_db/**       # 양성 대조 — `features` 가 sqlalchemy 를 import 하면 lint-imports 가 붉어짐(OPEN-5B-FEATURES-FORBIDDEN 해소 증거)
  - ml-engine/tests/gates/test_import_contracts.py          # 위 fixture 를 도는 test 1 추가(기존 test 무편집)
  - ml-engine/tests/conftest.py                             # hypothesis `ci` 프로파일을 루트로 승격(5B 인수 — `tests/features/conftest.py` 는 삭제) — 5A 소유 파일, hunk 격리 대상
  - ml-engine/pyproject.toml                                # (a) import-linter forbidden 계약 신설 「features·training·evaluation·registry 는 DB·HTTP·celery 를 모른다」(OPEN-5B-FEATURES-FORBIDDEN) (b) `training` extras 에 `pyyaml` (c) pytest 마커 `legacy_parity` 등록 — **`[tool.ruff.lint] select` 무편집**(5D 가 `BLE` 를 더한다 — 「레인 격리」) · 래칫 한도·allowlist 무편집
  - milestone-5.md                                          # 5C 절 착수 문단 + 5C 문면 개정(D-5C-1, 운영자 확인 대기 표기)
  - reports/evidence/m5/5c/**                               # scope·commands·checklist·reuse·policy-values·rollback·golden-manifest(N/A 사유)
out_of_scope:
  - 평가·홀드아웃·승격 측정 전부 — scoring(rmse/bias/std·paired t)·베이스라인 표·성숙도 창 정책·`_split_at_window`·MDE·required_row_count·seed 안정성·커버리지 분해·`build_verdict`·evaluation report·`policy/evaluation-v1.yaml`   # 5C-2
  - **B 계보 전부**(D-5C-1) — `PricePredictionTrainingService`·ensemble artifact 빌더·`dataset_quality`(B)·`comparison.py` rolling holdout·`ml_release/*`(gate·manifest·rollout·preflight·signing)·Platt 확률 보정·group winning_rate 통계·`calibration_raw_signal`
  - 학습 트리거·job 상태 기계·`TrainingJobService` servicer·취소 존중·폴링(2C·5E) · object storage(s3 등) adapter(6C) · `DatasetReference.uri` scheme 확정(`OPEN-2C-DATASET-URI-SCHEME` — 5C-1 은 `file://` 만)
  - `contracts/**` 편집 — `JobFailureCode` 값 추가(`OPEN-2C-FAILURE-CODES`)는 M2 additive slice(2F)·Codex 심사 대상. 5C-1 은 사유 → 코드 **매핑 표**만 남긴다(D-5C-11)
  - `registry/artifact.py`(5D 소유 read model)·`inference/**`·serving·wire 매핑 · `ruff select` 편집(5D) · 5A `policy-values.md` 표 편집(D-5C-9 대신 이 slice 의 policy-values.md 에 대조 절)
  - dataset 의 **생성**(ORM → 파일 — Kotlin/M6 소관, ADR 0004 D-3) · `code_version` 산출(git 호출 없음 — 호출자가 문자열로 준다)
  - 정책 값 튜닝·재학습 결과의 옳음 · fixtures 신설(curator — `OPEN-5C-CORPUS`)
acceptance_commands:
  # 정본 = CI `ml-engine` job 전건(하네스 2026-09-12 규율 — 부분 게이트 금지). Kotlin job 은 소스 무접촉이라 돌리지 않는다.
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                 # S-1 — lock 갱신(pyyaml → training extras) 포함
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c \"import $m\" || exit 1; done && uv sync --frozen --all-extras)"   # S-1b
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3 — override 추가 0
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4 — 신설 forbidden 포함(training 은 features·contracts·registry·adapters 만, inference·serving 금지는 layers 가)
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                              # S-5 — 5A 게이트 + 5B + 5C-1(재현성·legacy_parity 포함 실행되되 parity 는 판정 근거 아님)
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6 — 함수 50/파일 500/dict[str,Any] 경계 0
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7 — reuse.md 행 = `Reuse:` docstring 모듈 전수
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
rollback: |
    **정본 `reports/evidence/m5/5c/rollback.md`**(구현 레인). `training/**`(신규 모듈)·`adapters/dataset_files.py`·`policy/training-v1.yaml`·`tests/training/**`·`tests/adapters/**`·`tests/gates/fixtures/bad_features_db/**` 삭제
    + 공유 파일 넷(`pyproject.toml`·`tests/conftest.py`·`tests/gates/test_import_contracts.py`·`tests/features/conftest.py` 복원)은 **커밋 해시 hunk 격리**(5D 가 먼저 병합돼 있으면 같은 파일에 5D 줄이 있다 — 「남의 줄 남음」 실측).
    `milestone-5.md` 착수 문단은 문서 레인 — 목록 제외 선언. 임시 clone 실측 → in_scope diff 0 → S-1~S-7·S-9 전건 초록(5A+5B 233 test).
```

작성: 2026-09-12 착수판 — 세션 모델 단독. 근거: `milestone-5.md` 5C 다섯·완료 조건 10 · `v2-지침서.md` §3.2 이식 규칙·§5 「Python ML」·「크기와 결합도」 · `capability-map.md` ML-05(재현성 셋)·ML-07·ML-08·ML-11.4 F2·F4·F4-함정·F6 · `data-dictionary.md` §6.3 · ADR 0001 §4.1 · ADR 0006 D-7 · ADR 0009 D-5·D-6 · ADR 0010 D-8 · 2C ①②⑦·D-2C-2 (a) · 5B D-5B-5·D-5B-6·`EncodingOutcome` · 5D scope ⑦(`ArtifactManifestV1` 필드)·D-5D-4 · 5A D-M5-1~4·D-M5-6 · 조사 노트 01 §0·§1·§2·§5·§8·§10·§11 / 02 §3·§10·톱10.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점 재실행.

---

## 레인 격리 — 5D 와 공유하는 파일

| 파일 | 5D 가 만지는 것 | 5C-1 이 만지는 것 | 규칙 |
| --- | --- | --- | --- |
| `ml-engine/pyproject.toml` | `[tool.ruff.lint] select` 에 `BLE` · pytest 마커 `legacy_parity` · `serving` extras(`pyyaml` 추정) | 신설 `[[tool.importlinter.contracts]]` 블록 하나 · `training` extras 에 `pyyaml` · pytest 마커 `legacy_parity` | **`select` 무편집.** 마커는 둘이 같은 줄을 더하므로 나중에 병합하는 쪽이 중복 한 줄을 지운다(등재). 5D 의 `BLE` 가 먼저 오면 5C-1 코드는 그 규칙을 통과해야 한다(구현 규율은 지금부터 `except Exception` 0) |
| `ml-engine/tests/conftest.py` | 무편집(5D 는 `tests/inference/conftest.py` 에 자기 프로파일) | hypothesis `ci` 프로파일 루트 승격 | 5D 의 하위 conftest 가 같은 이름을 다시 등록해도 `register_profile` 은 덮어쓰기라 무해 — 5D 종결 시 그 파일을 지울지는 5D 레인 몫(등재) |
| `milestone-5.md` | 5D 절 문면·착수 문단 | 5C 절 착수 문단·문면 개정 | 다른 hunk. 병합 순서 무관 |
| `registry/` | `artifact.py` 신설(read model) | **무편집** | 5C-1 은 5D 파일을 import 하지 않는다 — 필드 집합을 **문자열 tuple 로 test 에 고정**해 5D scope ⑦ 과 대조(D-5C-9). 왕복 test 는 둘이 다 `main` 에 있을 때 `OPEN-5C-ARTIFACT-ROUNDTRIP` 으로 |
| 5A `reports/evidence/m5/5a/policy-values.md` | D-5D-9 배정 정정 | **무편집** | 5C 몫 정정(#8·#31·#32·#33 은 A 계보 학습 경로 소비 0 — 조사 01 §8-1)은 이 slice 의 `policy-values.md` 「5A 표 대조」 절에 적고 `OPEN-5C-5A-TABLE-REASSIGN` 으로 5D 병합 뒤 팀장이 반영 |

**병합 순서**: 5D 가 먼저 착수했으므로 5D → `main` 뒤 5C-1 을 rebase 한다. 그 전에는 5D 브랜치를 끌어오지 않는다(2026-09-10 규율 — 검증 전 코드 병합 금지).

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **dataset 입구(fail-closed)** — `DatasetManifestV1`(`dataset_id`·`sample_scope` 기본값 없음(5B `require_declared`)·`feed_origin_only: bool`·`row_count`·`rows_checksum`·`opened_at_first/last`·`feature_schema_version`) + `rows.jsonl`. `load_dataset(manifest_bytes, rows_bytes, expected)` — sha256(manifest_bytes) ≠ `expected.manifest_checksum` → `DatasetRejected(CHECKSUM_MISMATCH)`, `dataset_id` 불일치 → `ID_MISMATCH`, `rows_checksum` 불일치 → `ROWS_CHECKSUM_MISMATCH`, `feature_schema_version` 미지원(`resolve_schema` → `UnsupportedSchema`) → `SCHEMA_UNSUPPORTED`, 파싱 실패 → `UNREADABLE`. **거부 시 객체 미생성.** `adapters/dataset_files.py` 는 `file://<dir>` 의 `manifest.json`·`rows.jsonl` 두 파일을 읽기만(네트워크 0) | 2C ①② 「checksum 불일치는 job 안에서 FAILED」 · M5 완료 조건 「artifact checksum 불일치 fail-closed」 의 dataset 쪽 · ML-11.4 F2(표본 정의 `feed_origin_only` 를 manifest 필수 필드로) · `sample_scope` 필수 선례(조사 01 §5-2) |
| ② | **corpus 승인 + 회계** — 행마다 5B `FeatureFacts.from_proto` 와 같은 검증 경로(`FeatureInputs` proto 를 rows.jsonl 이 나른다 — 어휘를 새로 만들지 않는다)로 fact 넷을 얻고, 라벨은 `AwardRateLabel`(생성 함수 `admit_label(value) -> AwardRateLabel | LabelRejected` — **`0.0 ≤ value ≤ 1.0` 닫힌 구간** 밖·비유한 → 거부). 거부 행은 **조용히 버리지 않고** `RejectedRowAccounting`(사유별 계수: `FactRejected` 사유·`LabelRejected`·`RowRejected(MissingFact)`)로 결과에 실린다. `AwardRateObservation` 은 승인된 라벨에서만 만든다(`OPEN-5B-OBSERVATION-DOMAIN` 해소 — 근거는 legacy 값이 아니라 **5B 스키마 사실** `agency_encoding ∈ [0,1]`: 라벨 평균의 수축 인코딩이 [0,1] 에 있으려면 라벨이 [0,1] 에 있어야 한다) | `OPEN-5B-OBSERVATION-DOMAIN`(5B 인수) · `OPEN-5C-ROW-REJECTION-POLICY` → D-5C-5 · §5 「조용한 fallback 금지」 · 5B `MissingPolicy.REJECT_ROW_ON_MISSING` |
| ③ | **최소 학습 표본 게이트** — `admitted_row_count < policy.min_training_rows → TrainingRejected(INSUFFICIENT_TRAINING_ROWS, detail=(admitted, required))`. legacy 는 설정(`…_MIN_TRAINING_ROWS=500`)만 있고 거는 코드가 **없었다**(조사 01 §8-2) — 신규. 하한 1 클램프는 코드 불변식(5D D-5D-3 과 같은 형태) | M5 완료 조건 「오류·최소 표본이 0점/성공으로 변환되지 않음」 · `OPEN-5C-MIN-TRAINING-ROWS` → D-5C-7 |
| ④ | **두 인코딩을 이름으로 가른다** — (a) 학습 행렬의 `agency_encoding` 열은 **폴드별 OOF**(각 폴드에서 나머지 행으로 `build_agency_target_encoding`), (b) artifact 에 싣는 표는 **전 구간 한 번**. 둘을 한 함수로 합치지 않는다(합치면 학습 누수 또는 서빙 커버리지 소실 — 조사 01 §2-4). 폴드는 `fold_indices(row_count, spec.encoding_folds, seed)` — legacy rng 계약 그대로(무작위 K-fold, **시간 방향 없음** — D-5C-4). 어느 폴드의 인코딩이 `NoObservations` 면 `TrainingRejected(NO_OBSERVATIONS)` | ML-11.1 「training-serving skew 를 구조로」 · 5B `EncodingOutcome` 소비(5B checklist 「5C 가 `Built`/`NoObservations` 를 실제로 분기하는지」) · 조사 01 §2-4·§11-2 |
| ⑤ | **학습 행렬은 5B `build_row` 결과만** — `build_training_matrix(space, rows)`: 행마다 `space.build_row(facts)`; `FeatureRow` 의 값 tuple → `float` 행렬(열 순서 = `FEATURE_SCHEMA_V2.columns`), `RowRejected` 는 ② 회계로. 범주 열의 인덱스는 **schema 의 `FeatureKind.CATEGORICAL` 에서 산출**(legacy 는 상수 집합 — 절단). `feature_name` 은 schema 이름 그대로 `lgb.Dataset` 에 | 5B 위협 모델 (g) 「변환 함수 하나를 5C·5D 가 같은 모듈에서 import」 · 조사 01 §2-3 |
| ⑥ | **부스터 학습은 port 뒤** — `TrainerLike` Protocol 하나, `LightGbmTrainer` 가 실물(`lightgbm` import 는 이 모듈만 — mypy 는 `TYPE_CHECKING`). 파라미터는 `TrainingSpec` 에서만(코드 상수 산포 0): legacy `LIGHTGBM_PARAMS` 13키 + `num_boost_round 400` + `encoding_folds 5` + `seed 20260812` + `min_residual_std 0.002` 를 **`TRAINING_SPECS["award-rate-gbm-training-v1"]`** 로 이식(값 무변경, D-5C-2). early stopping 없음(legacy 동일). 부스터 텍스트는 `model_to_string()`(pickle 금지 — 조사 01 §5-1 사유 둘) | 2C D-2C-2 (a) 「versioned training spec, 5C 소유」 · ML-05 「`num_threads` 고정 사유」 · §5 매직넘버 |
| ⑦ | **잔차 std(후보 폭)** — OOF 잔차 `pred − label` 의 표본 표준편차, 하한 `spec.min_residual_std`. 이것이 5D `Uncertainty.dispersion`·`scenario` 의 `std` 입력이다. **Platt 확률 보정·group winning_rate 는 이식하지 않는다**(D-5C-1) | 5D scope ⑥「GBM 경로 `residual_std` 는 5C 가 `MIN_RESIDUAL_STD 0.002` 바닥」 · `OPEN-5C-CALIBRATION-SCOPE` → D-5C-1 |
| ⑧ | **artifact writer** — `write_artifact(trained, release_inputs) -> ArtifactBytes(bytes, sha256, release)`. 필드 집합은 **5D scope ⑦ `ArtifactManifestV1` 그대로**: `manifest_schema_version="artifact-manifest-v1"` · `release{release_id, feature_schema_version, code_version, dataset_id}`(**`artifact_checksum` 은 바이트 안에 없다** — D-5C-9) · `feature_manifest_checksum`(5B `compute_checksum(FeatureManifest)`) · `feature_names` · `sample_scope` · `residual_std` · `training_row_count` · `booster_model` · `reproducibility{seed, num_threads, deterministic}` + **5C-1 추가** `training_spec_version`·`training_spec_checksum`·`training_policy_version`·`feed_origin_only`·`categories`·`denominator_sources`·`agency_encoding`(5B `FeatureManifest` 를 통째로 — 표는 정렬 불변식이 이미 있다) · `rejected_rows`(② 회계). canonical JSON 은 5B `canonical_json` 과 같은 규칙(키 정렬·`(",",":")`·float repr·NaN/Inf → `CanonicalizationRejected` 전파). `ArtifactBytes.sha256` = sha256(bytes) 가 곧 2C `ArtifactReference.release.artifact_checksum` | 5D ⑦ 「5C 는 이 형태로 쓴다」 · §5 「artifact 에 schema version·code version·dataset id·checksum·metric」(metric 은 5C-2 report) · ML-05 「재현성 파라미터가 manifest 에 기록」 · 조사 01 §5-2 필수 필드 선례 |
| ⑨ | **release 식별은 결정적** — `derive_release_id(dataset_id, training_spec_version, spec_checksum, seed, code_version)` = sha256 앞 16 hex. 같은 입력 → 같은 id(재현성 test 의 전제). `code_version` 은 호출자가 문자열로 준다(git 호출 없음, 빈 문자열 거부) | 2C ⑦ `release.dataset_id == 요청 dataset_id`(consumer test 전제) · ML-05 「표본 정의가 바뀌면 version 상승」 — `sample_scope`·`feed_origin_only` 가 dataset_id 에 실려 자동 |
| ⑩ | **재현성을 test 로 고정** — 같은 `LoadedDataset`·`TrainingSpec`·`TrainingPolicy`·`code_version` 으로 두 번 학습 → `ArtifactBytes.bytes` **바이트 동일**. fake trainer 로 구조 test + **실 LightGBM 1건**(작은 합성 코퍼스, `num_threads` 고정) — legacy 가 주장만 하고 test 로 고정하지 않았던 성질(조사 02 §3-4) | M5 완료 조건 「clean environment 에서 동일 manifest/seed 입력이 재현 가능한 artifact」 · `OPEN-5C-REPRO-SCOPE` → D-5C-12 (a) |
| ⑪ | **정책 값 실물** — `policy/training-v1.yaml`(D-5C-7) + `load_training_policy`(5A `load_policy` → `PolicyRejected` 결과 타입, `known_keys` 전수, `min_training_rows ≥ 1`). κ 둘은 5B `SHIPPED_ENCODING_POLICY` 를 **재사용**(낙찰률 축 — 5D 의 사정률 축과 다르다, 새 키 없음) | ADR 0006 D-7 · `OPEN-ML-05` · 5B D-5B-7 |
| ⑫ | **import 경계 확장** — import-linter forbidden 신설: `features`·`training`·`evaluation`·`registry` → `sqlalchemy`·`psycopg`·`requests`·`httpx`·`celery` 금지(`adapters` 는 제외 — 6C 가 object storage 를 넣을 자리). 양성 대조 fixture `bad_features_db/`(`features` 가 sqlalchemy 를 import) 가 `lint-imports` 를 붉힘을 test | `OPEN-5B-FEATURES-FORBIDDEN`(5B 인수 — 5C 착수 계약 명시 조건) · M5 완료 조건 「금지 import mutation 이 CI 에서 실패」 |

**만들지 않는 것**: 평가·홀드아웃·verdict·report(5C-2) · B 계보 전부 · 트리거·job·servicer(5E) · object storage · `contracts` 편집 · `except Exception` 폴백 · `dict[str, Any]` · 비율 분할 API(F4 — 5C-2 도 만들지 않는다) · 기대값 일괄 재생성 명령(F6) · `recommended_env`·`skip_promotion_gate` 류 우회(조사 02 톱10 #5).

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5C-0** | **5C 를 5C-1(학습 커널)·5C-2(평가·승격 측정)로 가른다.** 5C-2 는 5C-1 의 `train_award_rate_gbm` 을 창마다 호출하는 소비자라 의존 방향이 한쪽이고, 산출물 종류(artifact vs evaluation report)와 정책 파일(`training-v1` vs `evaluation-v1`)이 다르다. 4B-1/4B-2·4C-1/4C-2 선례 — 검증 라운드를 짧게 | 조사 01 §0(학습 커널 = A 계보 6파일)·02 §0(평가 계통 셋 — 어느 것을 잇는지가 별도 결정) | **운영자 확인**(추천 위 · 대안: 단일 5C — 모듈 20+·정책 파일 둘·검증 표적 30+ 를 한 라운드에) |
| **D-5C-1** | **A 계보만 이식. B 계보(Platt 확률 보정·group 통계·ensemble·`ml_release`)는 범위 밖.** `milestone-5.md` 5C 문면 「기존 calibration 로직 이식」은 **「GBM OOF 잔차 std(후보 폭) 이식」** 으로 개정 — Platt 는 자격 라벨(Kotlin 1C 소유)을 요구하고 D-M2-11 (a) 가 그 라벨을 계약에서 뺐으므로 ml-engine 경계 안에서 학습할 입력이 없다(`OPEN-ML-02` 남은 물음 그대로 이월). 임의 축소가 아니라 상위 결정의 하류 — **milestone 문면 개정을 착수 커밋이 동반**(5D D-M5-9 선례) | 조사 01 §4(두 calibration 모듈은 B 계보, GBM 과 무관)·§0 · capability-map ML-09 `근거 부족` · D-M2-11 | **운영자 확인**(추천 위 · 대안: Platt 를 `PaperBid` 계약 신설과 함께 — M2 additive + Kotlin 라벨 전달, 이 milestone 밖) |
| **D-5C-2** | **하이퍼파라미터는 코드 선언 `TrainingSpec`(version 키 등록표) + 그 canonical checksum 을 artifact 에 기록.** YAML 로 두지 않는다 — 값이 artifact 에 박히므로 환경별로 갈리면 재현성이 깨진다(legacy 성문화 사유). `training_spec_version` 문자열이 2C 요청의 참조이고 미지 version 은 `UnsupportedTrainingSpec`(5E 가 `ApplicationFailure(UNSUPPORTED_TRAINING_SPEC)` 로 매핑) | 2C D-2C-2 (a) · 조사 01 §8-4·§11-10 · `OPEN-5C-HYPERPARAM-LOCATION` | **운영자 확인**(추천 위 · 대안 (b): `policy/training-spec-v1.yaml` + YAML checksum 을 manifest 에 — 정책 로더 재사용은 되나 「튜너블」 표면이 생긴다) |
| **D-5C-3** | 정책(도메인 값)과 spec(모델 정의)을 파일로 가른다 — `policy/training-v1.yaml` 은 `min_training_rows` 하나 + version. 승격 임계·창 정책은 5C-2 의 `evaluation-v1.yaml` | ADR 0006 D-7 · ML-07 acceptance 「임계는 정책 산출물, 코드 리터럴 아님」 | 계약 고정 |
| **D-5C-4** | **OOF 폴드는 무작위(legacy 그대로), 시간 방향을 요구하지 않는다.** 시간 누수는 dataset 의 cutoff(생성 측)와 5C-2 의 창 경계가 막고, 폴드는 인코딩 자기참조만 끊는다(legacy 의 역할 분리 선언 계승). 학습 구간 **내부**의 시간 방향은 알려진 제한으로 등재 — 요구하면 수치가 달라져 legacy parity 관측이 깨진다 | 조사 01 §2-4 (a)(b)(c)·§11-3 · `OPEN-5C-OOF-TIME-DIRECTION` | **운영자 확인**(추천 위 · 대안: 시간 블록 폴드 — 동작 변경, 5C-2 에서 홀드아웃 수치로 비교 뒤 결정 가능) |
| **D-5C-5** | 행 거부·라벨 도메인 — 거부는 **계수·사유별 회계로 결과에 실리고** 학습은 승인 행으로 계속(승인 행이 ③ 하한 미만이면 `TrainingRejected`). 라벨 닫힌 구간 [0,1] 은 5B 스키마 사실에서 도출(값을 지어내지 않음) | `OPEN-5C-ROW-REJECTION-POLICY` · `OPEN-5B-OBSERVATION-DOMAIN` · 5B `agency_encoding ClosedRange(0,1)` | 계약 고정 |
| **D-5C-6** | 성숙도(K7)·KST 주 경계는 5C-1 이 만지지 않는다 — 5C-2 창 정책이 **`WeekMaturity` 값을 입력으로** 받고 산출은 5D K7 몫(`OPEN-5C-MATURITY-SOURCE`, 5C-2 계약에서 확정) | ADR 0001 §4.1 표(성숙도 커널 = 5D) · layers(evaluation → inference 의존 금지 의도) | 계약 고정(5C-2 인계) |
| **D-5C-7** | 정책 값(`training-v1.yaml`, `OPEN-5C-POLICY-VALUES`): `version: training-v1` · `min_training_rows: 500` — legacy `PRICE_PREDICTION_AWARD_RATE_GBM_MIN_TRAINING_ROWS` 기본값 그대로이나 **legacy 에서 한 번도 소비되지 않은 값**이다(조사 01 §8-2). 5D 표와 달리 「legacy-behavior」가 아니라 **「legacy-declared(미소비)」** 로 표기 | 5A 표 「경계 밖 동류 3건 — 5C 가 만나면 등재」 | **값 승인 대기**(추천: 500 · 대안: 5C-2 홀드아웃 최소 평가 행 100 과 정합시켜 재정의 — 근거 없는 수치는 지어내지 않는다) |
| **D-5C-8** | dataset 은 디렉터리(`manifest.json` + `rows.jsonl`), `uri` 는 `file://` 만. 2C `manifest_checksum` = sha256(`manifest.json` bytes), manifest 안 `rows_checksum` = sha256(`rows.jsonl` bytes)(legacy `sha256-tree` 의 두 파일 특수형). 다른 scheme → `DatasetUnreadable(UNSUPPORTED_SCHEME)`(fail-closed, 5E 가 `DATASET_UNREADABLE` 로) | 2C ①②·`OPEN-2C-DATASET-URI-SCHEME` · ADR 0010 D-8 「dataset 은 storage 참조」 | 계약 고정(scheme 확정은 OPEN 유지) |
| **D-5C-9** | **artifact 바이트 안에 자기 checksum 을 넣지 않는다.** 5D scope ⑦ 의 `release{…, artifact_checksum, …}` 은 sha256(바이트)와 자기참조라 동시에 참일 수 없다 — 5C-1 은 `release` 에 넷(`release_id`·`feature_schema_version`·`code_version`·`dataset_id`)만 쓰고 checksum 은 `ArtifactBytes.sha256`(→ 2C `ArtifactReference.release.artifact_checksum`)로 나른다. 5D read model 이 `release.artifact_checksum` 을 필수로 읽으면 왕복이 깨진다 → **5D 레인에 즉시 통지**(`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`) | 2C ⑦ 「checksum 은 `release.artifact_checksum` 한 자리」(wire) · 5D ⑦ | **운영자 확인**(5D 레인 조정 필요) |
| **D-5C-10** | `release_id` 는 입력의 결정적 해시(⑨). UUID 를 쓰지 않는다 — 같은 입력의 두 학습이 다른 id 를 갖으면 재현성 test 가 바이트 동일을 잴 수 없다 | 2C D-2C-3 「형식은 계약이 정하지 않는다」 · ⑩ | 계약 고정 |
| **D-5C-11** | `TrainingRejected.reason`(StrEnum: `INSUFFICIENT_TRAINING_ROWS`·`NO_OBSERVATIONS`·`ALL_ROWS_REJECTED`·`TRAINER_ERROR`) 과 `DatasetRejected.reason` → 2C `JobFailureCode` 매핑 표를 `checklist.md` 에 — 기존 6값에 없는 것(`INSUFFICIENT_TRAINING_ROWS` 등)은 `TRAINING_ERROR` + `detail_code` 로 나르고 additive 후보로 `OPEN-2C-FAILURE-CODES` 에 등재. proto 무편집 | 2C D-2C-4 「5C 가 값을 더한다(호환 추가)」 → 2F 절차·Codex 대상 | 계약 고정 |
| **D-5C-12** | 재현성의 정의 = **(a) 같은 호스트·같은 `num_threads` 에서 artifact 바이트 동일**(legacy 주장 그대로, test 로 고정). (b) metric 허용오차 재현은 5C-2. 스레드 수를 바꾸면 갈릴 수 있음을 알려진 제한으로 | `OPEN-5C-REPRO-SCOPE` · ML-05 | 계약 고정 |
| **D-5C-13** | import-linter forbidden 확장은 `adapters` 를 **제외**한다 — 6C object storage 의 자리. `training → adapters` 의존은 허용(layers 항이 아님) | `OPEN-5B-FEATURES-FORBIDDEN` · §3.2 트리 「adapters: training 전용 storage adapter」 | 계약 고정 |

---

## 위협 모델 — 5C-1 고유 경계

**방어한다**: (a) 검증 안 된 dataset 으로 학습 — `LoadedDataset` 은 `load_dataset` 만 만든다(checksum·id·schema 전부 통과 뒤) (b) 라벨 도메인 밖·결측 행의 조용한 흡수 — 거부 + 회계 (c) 인코딩 자기참조(target leakage) — OOF 폴드, test 는 「학습 행렬 인코딩 열 ≠ 전 구간 인코딩, 모든 행」(legacy test 이식) (d) 두 인코딩 혼동 — 함수 둘, 이름 둘, 반환 타입 둘 (e) 파라미터 산포·env 튜닝 — `TrainingSpec` 만, 코드 상수 0, 정책 로더 fail-closed (f) 재현 불가 artifact — 결정적 release_id·정렬 어휘·seed/threads 고정·바이트 동일 test (g) artifact 필드 누락·기본값 접힘 — 전 필드 필수(기본값 없음), 5D 필드 집합 test (h) pickle — 텍스트 부스터만 (i) 최소 표본 미달의 「성공」 — `TrainingRejected` 결과 타입 (j) 학습 코드가 DB·HTTP 를 만짐 — forbidden 확장 + 양성 fixture (k) 예외로 실패 나르기 — 결과 타입(`BLE` 는 5D 가 더하고 5C-1 코드는 지금부터 준수).
**방어하지 않는다**: dataset 내용의 옳음(cutoff·표본 정의는 생성 측 — Kotlin/M6, `feed_origin_only` 는 기록만) · 시간 누수의 창 경계(5C-2) · 학습 구간 내부 OOF 시간 방향(D-5C-4, 알려진 제한) · 모델의 옳음(5C-2 holdout·golden) · 스레드 수가 다른 호스트 간 바이트 동일(D-5C-12) · Python 가시성(직접 생성 컨벤션 — 5B·5D 와 같은 한계, (2b) 등재) · `code_version` 문자열의 진위(호출자 몫).

**우회 후보(≥5)**: (1) `rows.jsonl` 을 바꾸고 `manifest.json` 은 그대로 → `rows_checksum` 불일치 거부 (2) manifest 의 `dataset_id` 를 요청과 다르게 → `ID_MISMATCH` (3) `sample_scope` 빈 문자열 → `require_declared` 는 None 만 잡으므로 `DatasetManifestV1.__post_init__` 이 빈 문자열 거부(5D 우회 (15) 와 같은 갈래) (4) 라벨 `1.2`·`NaN`·`-0.0`(음의 0 은 허용값) → 규칙표 property (5) 폴드 1개(`encoding_folds=1`) → OOF 가 정의되지 않음 → `TrainingSpec.__post_init__` 이 `encoding_folds ≥ 2` 강제 (6) 승인 행 0 → `ALL_ROWS_REJECTED`(`INSUFFICIENT` 와 구별 — 사유가 다르다) (7) `TrainerLike` fake 가 `feature_names` 를 무시 → `write_artifact` 가 `verify_feature_names(booster feature names, schema)` 로 정확 일치 확인(부스터 텍스트에서 읽지 않고 trainer 가 돌려준 `feature_name()` 으로) (8) `num_threads` 를 환경변수로 → spec 밖 경로 없음(LightGBM 은 params dict 만 본다 — test: `OMP_NUM_THREADS` 를 바꿔도 바이트 동일, 알려진 제한과 함께) (9) 정책 YAML 에 `min_training_rows: 0` → `PolicyRejected`(≥ 1) (10) `training` 이 `ml_engine.inference` 를 import(5D 의 K7 을 끌어옴) → layers 계약이 아니라 **forbidden 에 `ml_engine.inference`·`ml_engine.serving` 을 training/evaluation source 로 추가**(⑫ 에 포함) (11) `agency_encoding` 을 전 구간 표로 학습 행렬에 채움(누수) → (c) test (12) `release.dataset_id` 를 요청과 다르게 → `write_artifact` 가 `LoadedDataset.manifest.dataset_id` 에서만 읽는다(인자 없음).

---

## (2b) 값 획득 축 (Python)

| 표면 | 판정 |
| --- | --- |
| `TrainingSpec`·`TRAINING_SPECS`·`resolve_training_spec`·`spec_checksum` | 연다(읽기, frozen) — 등록표는 `MappingProxyType` |
| `load_dataset(manifest_bytes, rows_bytes, expected) -> LoadedDataset \| DatasetRejected` | 연다 — 유일 dataset 진입점. `LoadedDataset` 직접 생성은 컨벤션(Python 한계) |
| `admit_label`·`admit_corpus` | 연다 — 라벨·코퍼스 유일 진입점(컨벤션) |
| `fold_indices` | 연다 — 순수 |
| `build_training_matrix`·`out_of_fold_matrix_and_residuals`·`full_corpus_feature_space` | 연다 — 5B `build_row`·`build_agency_target_encoding` 만 소비 |
| `TrainerLike` Protocol·`LightGbmTrainer` | 연다 — **test fake 주입 자리**(고정 항목 「무엇을 주입하는가」: 주입되는 것은 **모델 실물**이고 결과는 부스터 텍스트 + 예측치; 예측치는 잔차 std 계산에만 쓰이고 하한이 있어 임의 float 이 그대로 응답에 실리지 않는다 — test: fake 가 NaN/inf 반환 → `TrainingRejected(TRAINER_ERROR, NON_FINITE_PREDICTION)`) |
| `train_award_rate_gbm(...) -> TrainedArtifact \| TrainingRejected` | 연다 — 유일 학습 진입점 |
| `write_artifact(...) -> ArtifactBytes` | 연다 — `TrainedArtifact` 만 받는다(`TrainedArtifact` 직접 생성은 컨벤션) |
| `derive_release_id` | 연다 — 순수 |
| `load_training_policy(path) -> TrainingPolicy \| PolicyRejected` | 연다 — 값 필수, 기본값 없음 |
| `read_dataset_files(uri)` | 연다 — `file://` 만, 디렉터리 밖 경로 탈출(`..`)은 `resolve()` 뒤 scheme 검사만(권한은 M6) |

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-5C-POLICY-VALUES` | D-5C-7 — 착수 시 승인 대상(`min_training_rows 500`, legacy-declared 미소비) |
| `OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT` | D-5C-9 — 5D 레인 통지, 5D read model 의 `release.artifact_checksum` 처리 조정 |
| `OPEN-5C-ARTIFACT-ROUNDTRIP` | 5C-1 `write_artifact` → 5D `load_artifact` 왕복 test — 둘이 `main` 에 있을 때 나중 병합 쪽이 추가 |
| `OPEN-5C-5A-TABLE-REASSIGN` | 5A 표 #8·#31·#32·#33 「5C」 배정은 A 계보 학습 경로에서 소비 0(조사 01 §8-1) — 5D 병합 뒤 팀장이 5A `policy-values.md` 정정(#8 → Kotlin guardrail, #31 → 5D 만, #32·#33 → B 계보 미이식) |
| `OPEN-5C-OOF-TIME-DIRECTION` | D-5C-4 — 알려진 제한, 5C-2 홀드아웃 수치 뒤 재판단 |
| `OPEN-5C-MATURITY-SOURCE` | D-5C-6 — 5C-2 계약 |
| `OPEN-5C-CORPUS` | 학습 dataset fixture(`manifest.json`+`rows.jsonl`, authored-from-approved-spec) — curator. 5C-1 test 는 합성 코퍼스로(값은 코드가 만든 것 — 판정 근거는 규칙·불변식) |
| `OPEN-5B-FEATURES-FORBIDDEN` | ⑫ 로 **해소**(양성 fixture 동반) |
| `OPEN-5B-OBSERVATION-DOMAIN` | ② 로 **해소**(닫힌 구간 [0,1], 근거 5B 스키마) |
| `OPEN-2C-FAILURE-CODES`·`OPEN-2C-DATASET-URI-SCHEME` | D-5C-11·D-5C-8 — 수령 유지(매핑 표·`file://` 만), 확정은 2F/6C |
| `OPEN-ML-02` | D-5C-1 — Platt 이식 없음, 남은 물음 그대로(M4 opportunity 축·5C-2 report 가 P(낙찰) 을 만들지 않음) |
| `OPEN-ML-05` | D-5C-7 값 하나 + κ 재사용 — 5A 표 정정은 `OPEN-5C-5A-TABLE-REASSIGN` |
| 5B hypothesis 프로파일 위치 | 루트 `tests/conftest.py` 승격으로 **해소**(5D 하위 conftest 중복은 무해, 등재) |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-12 착수 | 초판 — 5C 분할(D-5C-0), A 계보 한정(D-5C-1), 결정 13 | 조사 노트 01·02 |
