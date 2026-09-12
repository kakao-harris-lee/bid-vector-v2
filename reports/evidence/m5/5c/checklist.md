# M5/5C-1 — checklist.md

## D-5C-0~13 충족 근거

| ID | 결정 | 충족 근거 |
| --- | --- | --- |
| D-5C-0 | 5C 를 5C-1(학습 커널)·5C-2(평가)로 분리 | 이 slice 자체가 5C-1 — `training/**`만 만들고 `evaluation/**`는 무편집(scope out_of_scope). **운영자 확인 대기 항목**(scope.md 표) — 착수 시 추천안대로 진행 |
| D-5C-1 | A 계보만 이식, B 계보(Platt·group 통계·ensemble·`ml_release`) 범위 밖 | `training/**` 전체에 `calibration`·`Platt`·`sigmoid`·`ml_release` 참조 0(grep 확인). `residual.py`가 유일한 "불확실성 폭" 산출 — OOF 잔차 std. milestone-5.md 5C 문면 개정은 팀장 문서 레인 소관(구현 레인 편집 금지 지시) — `2e9f100`이 이미 개정 완료(취소선 + D-5C-1 개정문, `git diff f5020aa..HEAD -- milestone-5.md` 확인, verifier r1 L-2) |
| D-5C-2 | 하이퍼파라미터는 코드 선언 `TrainingSpec` + checksum | `spec.py::TrainingSpec`·`TRAINING_SPECS`(frozen `MappingProxyType`)·`spec_checksum`(canonical JSON sha256) · `test_spec.py::test_shipped_spec_matches_policy_values_md`(legacy 13+4값 전건 대조) |
| D-5C-3 | 정책(도메인 값)과 spec(모델 정의) 파일 분리 | `policy/training-v1.yaml`은 `min_training_rows` 하나만(`policy.py::_KNOWN_KEYS`) — 하이퍼파라미터는 여기 없다 |
| D-5C-4 | OOF 폴드는 무작위, 시간 방향 요구 없음 | `folds.py::fold_indices`(legacy rng 계약 그대로) · `test_folds.py::test_legacy_parity_rng_contract_seed_20260812_n13_folds5`. **학습 구간 내부 시간 방향은 알려진 제한**(아래) |
| D-5C-5 | 행 거부 회계 + 라벨 도메인 [0,1] | `corpus.py::RejectedRowAccounting`(사유별 계수) · `AwardRateLabel.__post_init__`(직접 생성도 방어) · `test_corpus.py::test_admit_corpus_accounting_invariant_admitted_plus_rejected_equals_input` |
| D-5C-6 | 성숙도·KST 창은 5C-1 이 만지지 않는다 | `training/**`·`adapters/**`에 `settlement_maturity`·`WeekMaturity`·KST 참조 0(grep 확인) — 5C-2 인계 |
| D-5C-7 | 정책 값 `min_training_rows: 500`(legacy-declared, 미소비) | `policy/training-v1.yaml` · `policy-values.md` §1. **운영자 확인 대기**(scope.md 표) |
| D-5C-8 | dataset = 디렉터리(`manifest.json`+`rows.jsonl`), `file://`만 | `dataset.py::load_dataset` · `adapters/dataset_files.py::read_dataset_files`(scheme≠file → `UNSUPPORTED_SCHEME`) · `test_dataset_files.py` |
| D-5C-9 | `release`에 자기 checksum 없음(자기참조 금지) | `release.py::ReleaseIdentity`(4필드: `release_id`·`feature_schema_version`·`code_version`·`dataset_id`) · `test_release_identity_has_no_artifact_checksum_field` · checksum 은 `ArtifactBytes.sha256`(재계산 대조 test 존재). **`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT` — 5D 레인 통지 필요**(아래 「인계」, 5D scope ⑦ 의 `release.artifact_checksum` 이 이 slice 산출물에 없다) |
| D-5C-10 | `release_id`는 결정적 해시, UUID 아님 | `release.py::derive_release_id` · `test_derive_release_id_is_deterministic`·`test_derive_release_id_changes_when_any_input_changes`(5축 각각) |
| D-5C-11 | `TrainingRejected`/`DatasetRejected` → `JobFailureCode` 매핑 | 아래 표. `contracts/**` 무편집 — 매핑은 이 문서에만, proto 값 추가는 `OPEN-2C-FAILURE-CODES`(2F) |
| D-5C-12 | 재현성 = 같은 호스트·같은 `num_threads`에서 바이트 동일 | `test_train_artifact.py::test_train_and_write_artifact_reproducible_bytes`(실 LightGBM, `num_threads=1`, 두 번 학습 → `ArtifactBytes.bytes` 동일) |
| D-5C-13 | forbidden 확장은 `adapters` 제외 | `pyproject.toml` 신설 계약 `source_modules`에 `ml_engine.adapters` 없음 · `test_import_contracts.py::test_bad_features_db_fixture_is_broken_by_lint_imports` |

## `TrainingRejected`/`DatasetRejected`/`DatasetUnreadable` → 2C `JobFailureCode` 매핑 표(D-5C-11)

2C `JobFailureCode`(6값: `UNSPECIFIED`·`DATASET_UNREADABLE`·`DATASET_CHECKSUM_MISMATCH`·
`TRAINING_ERROR`·`EVALUATION_ERROR`·`CANCELLED_BY_REQUEST`·`WORKER_RESOURCE_EXHAUSTED`)에
**없는** 사유는 `TRAINING_ERROR` + `detail_code`로 나른다(proto 무편집, D-5C-11).

| 이 slice 의 결과 타입 | `reason`/`detail` | `JobFailureCode` | `detail_code` |
| --- | --- | --- | --- |
| `DatasetRejected` | `CHECKSUM_MISMATCH` | `DATASET_CHECKSUM_MISMATCH` | (manifest checksum 불일치, 값 그대로 기존 코드에 대응) |
| `DatasetRejected` | `ROWS_CHECKSUM_MISMATCH` | `DATASET_CHECKSUM_MISMATCH` | `ROWS_CHECKSUM_MISMATCH`(manifest 쪽과 구별) |
| `DatasetRejected` | `UNREADABLE` | `DATASET_UNREADABLE` | (파싱 실패 상세 — 예: `ROW_COUNT_MISMATCH`) |
| `DatasetRejected` | `SCHEMA_UNSUPPORTED` | `TRAINING_ERROR` | `SCHEMA_UNSUPPORTED` — **기존 6값에 없음**, additive 후보 |
| `DatasetRejected` | `ID_MISMATCH` | `TRAINING_ERROR` | `DATASET_ID_MISMATCH` — **기존 6값에 없음**, additive 후보 |
| `DatasetUnreadable`(adapters) | `UNSUPPORTED_SCHEME`/`NOT_FOUND`/`NOT_A_DIRECTORY` | `DATASET_UNREADABLE` | 사유 이름 그대로 |
| `TrainingRejected` | `INSUFFICIENT_TRAINING_ROWS` | `TRAINING_ERROR` | `INSUFFICIENT_TRAINING_ROWS` |
| `TrainingRejected` | `NO_OBSERVATIONS` | `TRAINING_ERROR` | `NO_OBSERVATIONS`(+ fold 번호) |
| `TrainingRejected` | `ALL_ROWS_REJECTED` | `TRAINING_ERROR` | `ALL_ROWS_REJECTED` |
| `TrainingRejected` | `TRAINER_ERROR` | `TRAINING_ERROR` | `TRAINER_ERROR`(+ lightgbm 상세 또는 `NON_FINITE_PREDICTION`) |
| `write_artifact` → `NameMismatch`/`CanonicalizationRejected` | — | `TRAINING_ERROR` | `ARTIFACT_ASSEMBLY_FAILED` — 정상 운영에서는 도달 불가(자기 schema 로 만든 부스터가 자기 이름과 어긋날 수 없다), 결함 방어선 |

전 항목이 `additive` 필요분은 `OPEN-2C-FAILURE-CODES`(2F, Codex 심사 대상)로 이월 — 이 slice 는
proto 를 건드리지 않았다.

## (2b) 값 획득 축 — 5C-1 이 여는 public 표면

| 표면 | 판정 |
| --- | --- |
| `TrainingSpec`·`LightGbmHyperparameters`·`TRAINING_SPECS`·`resolve_training_spec`·`spec_checksum` | 연다(frozen dataclass, 값 불변식은 `__post_init__` — `encoding_folds≥2`·`num_boost_round≥1`·`num_threads≥1`·`min_residual_std>0`) — 등록표 조회 진입점은 `resolve_training_spec` 이지만 `TrainingSpec(...)` 직접 생성으로 임의 값을 우회할 수 있다(Python 가시성 한계, 알려진 제한 ①) |
| `load_dataset(manifest_bytes, rows_bytes, expected) -> LoadedDataset \| DatasetRejected` | 연다 — 유일 진입점. `LoadedDataset(manifest=..., raw_rows=...)` 직접 생성은 검증을 전부 우회한다(알려진 제한 ①과 같은 갈래) — `test_train_award_rate_gbm_rejects_empty_dataset_as_all_rows_rejected`가 직접 생성 경로를 실제로 쓰는 test 이므로, 이 우회가 **관례로만 방어됨**을 스스로 증명한다 |
| `admit_label`·`admit_corpus` | 연다 — `AwardRateLabel`은 직접 생성도 `__post_init__`이 도메인을 강제해 **닫혀 있다**(5B 대비 강화 — 이 slice 는 방어선을 하나 늘렸다) |
| `TrainerLike`·`LightGbmTrainer` | 연다 — test fake 주입 자리(고정 항목). 주입 자리는 booster 실물 하나뿐이고, fake 가 반환하는 예측치는 `OutOfFoldBuilt.residuals`/최종 `residual_std`에만 흘러 하한(`floor`)과 유한성 검사(`NON_FINITE_PREDICTION`)를 지난다 — 임의 float 이 그대로 응답에 실리지 않는다(등재, 없던 권한 아님) |
| `write_artifact(trained) -> ArtifactBytes \| NameMismatch \| CanonicalizationRejected`(verifier r1 H-1 뒤 — 인자 하나) | 연다 — 유일 조립 지점. `release`(`release_id`·`code_version`·`dataset_id`)는 전부 `trained` 자신의 필드에서만 파생된다(호출자 인자 경로 없음 — 우회 (12) 시그니처 차원 폐쇄). `TrainedArtifact(...)` 직접 생성(`booster` 필드에 임의 `BoosterLike` 주입, 또는 `dataset_id`/`code_version` 필드에 임의 값)으로 `feature_name()` 검증만 통과하면 임의 값을 실을 수 있다(알려진 제한 ①·②, 5B 「booster 텍스트는 자기서술」과 같은 갈래 — Python 가시성 한계는 여전하다, 다만 **write_artifact 의 별도 인자로 우회하는 경로는 없다**) |
| `derive_release_id(*, dataset_id, training_spec_version, training_spec_checksum, seed, code_version) -> str`(verifier r1 H-1 뒤 — `ReleaseInputs` 타입 삭제) | 연다 — 순수 함수. 값 자체의 유효성(비어있음 등)은 각 값의 원 출처 타입이 이미 강제한다(`DatasetManifestV1`·`CodeVersion`·`TrainingSpec`) — 이 함수 자신은 재검증하지 않는다 |
| `RejectedRowAccounting`·`with_missing_fact_rejections` | 연다 — 회계 필드 직접 조작으로 임의 계수를 `TrainedArtifact.rejected_rows`에 실을 수 있다(관례로만 방어, 알려진 제한 ①) |
| 함수 전부(`load_dataset`·`admit_corpus`·`out_of_fold_matrix_and_residuals`·`full_corpus_feature_space`·`train_award_rate_gbm`·`write_artifact`·`derive_release_id`·`residual_std`·`fold_indices`) | 순수·결과 타입 반환 — 예외는 프로그래밍 오류(`TypeError`)와 `LightGbmTrainer.train` 안의 `lightgbm.basic.LightGBMError`(→ `TrainerFailed`)만. `build_training_matrix`는 verifier r1 M-1 뒤 삭제(아래) |

**「object 커널을 세야 할 때 무엇을 주입하는가」**(v2-slice-pipeline 고정 항목) — 이 slice 에
`object`류 커널은 없다(카운터 상태 없음, 모든 학습은 무상태 순수 함수 + `TrainerLike` 주입).

## 알려진 제한

1. **Python 은 가시성을 강제하지 못한다**(5B·5D 와 같은 한계) — `LoadedDataset`·`TrainedArtifact`·
   `RejectedRowAccounting`·`TrainingSpec` 등 결과 타입의 직접 생성을 막을 수 없다. 강제는
   `load_dataset`/`train_award_rate_gbm`/`resolve_training_spec` 진입점 관례 + test 뿐이다.
   유일한 예외는 `AwardRateLabel`(값 불변식을 `__post_init__`이 강제 — 방어선 추가).
2. **OOF 시간 방향(D-5C-4, `OPEN-5C-OOF-TIME-DIRECTION`)** — 학습 구간 **내부**에서 미래 행의
   라벨이 과거 행의 `agency_encoding` 피처에 (인코딩을 통해) 영향을 줄 수 있다. legacy 는 이것을
   요구하지 않았고(폴드는 자기참조 차단만), 이 slice 도 요구하지 않는다 — 5C-2 홀드아웃 수치
   확인 뒤 재판단 대상.
3. **호스트 간 재현성 미보장(D-5C-12)** — `num_threads`가 다른 두 호스트는 다른 바이트를 낼 수
   있다(LightGBM 결정성 보장의 전제). test 는 같은 프로세스 안에서만 바이트 동일을 확인한다.
4. **중복 행 미검출** — `rows.jsonl`에 같은 공고가 두 번 있어도 `admit_corpus`는 둘 다 승인한다
   (dataset 생성 측 책임, legacy 도 막지 않았다 — 우회 후보 (13)). `manifest.row_count ≠ 실제 행 수`
   만 `UNREADABLE`로 잡는다(부분 파일 방어).
5. **5D `ArtifactManifestV1`와의 필드 어긋남 둘** — (a) 이 slice 의 `release`는 `artifact_checksum`을
   담지 않는다(D-5C-9) — 5D read model 이 그 키를 필수로 읽으면 왕복이 깨진다.
   (b) 이 slice 는 5D 기본 필드 밖에 8개(`training_spec_version`·`training_spec_checksum`·
   `training_policy_version`·`feed_origin_only`·`categories`·`denominator_sources`·
   `agency_encoding`(5D 밖 신규 형태, 아래 6 참조)·`rejected_rows`)를 더 싣는다 — 5D read model 이
   미지 키를 거부하면 이 slice 의 artifact 를 읽지 못한다. **`OPEN-5C-ARTIFACT-ROUNDTRIP`**(5D 레인
   병합 뒤 왕복 test 필요) · **`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`**(5D 즉시 통지 대상).
6. **`agency_encoding` 필드는 5B `FeatureManifest`를 통째로 embed** — legacy `PersistedAwardRateGbmArtifact`의
   `agency_encoding`(리스트 하나)과 이름은 같지만 형태가 다르다(schema_version·columns·
   categories·denominator_sources·agency_means·category_means·global_mean·두 κ 를 전부 담는
   중첩 객체). **판단 근거**: scope.md ⑧이 "5B FeatureManifest 를 통째로"라고 명시했고,
   `categories`/`denominator_sources`가 이미 별도 top-level 필드로도 있어(legacy 호환) 중복이지만
   `feature_manifest_checksum`이 검증하는 바로 그 canonical 구조를 재구현 없이 그대로 embed 하는
   쪽이 정렬 불변식 이중 구현보다 안전하다고 판단했다(계약과 어긋나 판단이 필요했던 자리 — 팀장
   보고).
7. **부스터 텍스트는 자기서술** — `booster_model`(LightGBM `model_to_string()`)의 내용이 실제로
   그 `feature_manifest`/`training_row_count`로 학습됐다는 것은 어떤 층도 재검증하지 않는다
   (fake trainer 로 위조 가능 — 5D `load_artifact`도 checksum·feature_names 대조만 한다, 5B와 같은
   경계 밖 선언).
8. **`CorpusRejected`의 판정 범위** — legacy·design-review 어디에도 명시적 정의가 없어, 이 slice 는
   **"입력 `rows` 자체가 빈 시퀀스"** 로만 좁혔다(0건 이상 처리했으나 전부 거부된 경우는
   `AdmittedCorpus(rows=(), ...)`로 남기고 `train_award_rate_gbm`이 `ALL_ROWS_REJECTED`로 판정).
   판단 근거: design-review uctions (6)이 "승인 행 0 → `ALL_ROWS_REJECTED`"를 `train.py` 몫으로
   명시했고, `admit_corpus`가 그 갈래까지 흡수하면 두 실패 경로(구조적 공백 vs 전량 거부)가
   구별 불가능해진다(계약과 어긋나 판단이 필요했던 자리 — 팀장 보고).
9. **`RawTrainingRow`의 envelope 검증은 `dataset.py`가 전담** — `opened_at` tz-aware·`stratum`
   비어있지 않음 검증을 corpus.py(도메인 계층)가 아니라 dataset.py(구조 계층)에 두었다 — 설계
   검토가 `CorpusRejected`를 이 갈래로 상정했을 가능성이 있으나, 구조적으로 이미 dataset.py 가
   raw row 파싱을 전담하므로 같은 검증을 두 곳에 중복하지 않기로 판단했다(계약과 어긋나 판단이
   필요했던 자리 — 팀장 보고).
10. **`spec.py`에 `Reuse:` 포인터를 달았다** — 설계 검토 (4)는 "reuse.md 행 7"(`spec.py` 제외)을
    계획했으나, `LightGbmHyperparameters`의 13개 값이 legacy 값 그대로라 이식으로 표기하는 것이
    더 정직하다고 판단해 8행으로 늘렸다(`reuse.md` 머리말에 편차 사유 기록).
11. **libomp(macOS 로컬)** — `commands.md` 참조. CI(Linux)에서는 일반적으로 재현되지 않는다.

## 「이번 구현이 만든 새 public 표면」

- `ml_engine.training.*` 패키지 전체(신규) — `ml_engine.adapters.dataset_files`(신규 모듈).
- import-linter 신설 forbidden 계약(pyproject.toml) — `features`·`training`·`evaluation`·`registry`가
  DB/HTTP/업무 모듈/`inference`/`serving`을 볼 수 없게 됨(이전에는 `features`가 무방비였다,
  `OPEN-5B-FEATURES-FORBIDDEN` 해소).
- `training` extras 에 `pyyaml` 추가(uv.lock 갱신 2줄).
- pytest 마커 `legacy_parity` 등록.
- hypothesis `ci` 프로파일이 `tests/conftest.py`(루트)로 승격 — 스위트 전체가 이제 이 프로파일을
  물려받는다(5B 인수 해소, 부작용 없음 확인 — S-5 전건 통과).

## verifier r1 수정 라운드가 만든/지운 public 표면

- **지웠다** — `ml_engine.training.matrix`(`build_training_matrix`·`TrainingMatrix`, M-1).
  `src/` 안 production 호출부가 없었고(`encoding_oof.py`가 자체 로직으로 학습 행렬을
  조립한다), 전 구간 인코딩 공간을 넘기면 누수 행렬을 그대로 내는 위험한 public 경로였다.
  `training/__init__.py` `__all__`에서도 제거.
- **지웠다** — `ml_engine.training.release.ReleaseInputs`(H-1). `derive_release_id`는 이제
  다섯 값을 키워드 인자로 직접 받는다(래핑 타입 없음).
- **좁혔다** — `write_artifact(trained, release_inputs)` → `write_artifact(trained)`(H-1).
  `release` 신원의 다섯 입력이 전부 `trained` 자신에서만 파생되도록 시그니처 자체가 바뀌었다
  — 호출자가 별도로 `dataset_id`/`code_version`/`seed`를 실어 보낼 경로가 사라졌다.
- **강화했다** — ⑫ import-linter forbidden 계약의 존재를 `tests/gates/test_import_contracts.py`가
  이제 실제 `pyproject.toml`을 tomllib 로 읽어 직접 검증한다(H-3). `bad_features_db/` fixture는
  더 이상 자기 계약을 갖지 않고 실제 계약을 임시 사본에 복사해 실행한다.

## 인계(팀장/타 레인)

- ~~milestone-5.md 5C 문면 개정~~ — `2e9f100`이 이미 완료했다(verifier r1 L-2, 낡은 인계 제거).
- **`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`** — 5D 레인에 즉시 통지. 5D `registry/artifact.py`의
  `ArtifactManifestV1` read model 이 `release.artifact_checksum`을 필수로 읽으면 이 slice 산출물과
  왕복이 깨진다.
- **`OPEN-5C-ARTIFACT-ROUNDTRIP`** — 5D 가 `main`에 병합된 뒤 `write_artifact` → 5D `load_artifact`
  왕복 test 필요(어느 쪽이 나중에 병합하든 그 레인이 추가).
- **`OPEN-5C-5A-TABLE-REASSIGN`** — 5A `policy-values.md` #8·#31·#32·#33 재배정(scope.md §3 표) —
  이 slice 의 `policy-values.md` §3에 이미 기록, 5A 표 자체는 5D 병합 뒤 팀장이 정정.

## D-5C-11 관련 재확인 — 「Codex 코드 심사 범위」 해당 없음

이 slice 는 인증·인가·암호화·비밀번호/토큰·DB 마이그레이션·데이터 파기 경로를 만들지 않는다
(`v2-slice-pipeline`/CLAUDE.md 운영자 지시 2026-09-11 범위 밖) — 완료 조건은 `verifier`
ready-for-review + 사용자 승인.
