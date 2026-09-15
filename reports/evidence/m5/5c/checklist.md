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
| D-5C-9 | `release`에 자기 checksum 없음(자기참조 금지) — **Python 타입** 차원 | `release.py::ReleaseIdentity`(4필드: `release_id`·`feature_schema_version`·`code_version`·`dataset_id`) · `test_release_identity_has_no_artifact_checksum_field` · `ArtifactBytes.sha256`(최종 bytes 전체의 sha256, wire `ArtifactReference.release.artifact_checksum`)은 재계산 대조 test 존재. **D-5C-9b(2026-09-13, 계약 갱신 이력)로 바이트 안 값은 갱신** — 아래 D-5C-9b 행·「같은 이름·다른 정의」 표 참고 |
| D-5C-9b | 5D read model 이 `release.artifact_checksum`을 비어 있지 않은 문자열로 요구(등가성은 안 봄) → writer 가 「그 필드를 빈 문자열로 둔 canonical bytes 의 sha256」(두 단계 직렬화)을 채운다 | `artifact_writer.py::_fill_self_described_artifact_checksum`(`_assemble_payload`가 블랭크 payload 를 만들고, 이 함수가 그 canonical bytes 의 sha256 을 `release.artifact_checksum`에 채운다) · `test_train_artifact.py::test_write_artifact_field_set_matches_5d_scope_plus_5c1_additions`(존재 단언으로 뒤집음)·`test_release_artifact_checksum_is_blank_canonical_bytes_sha256`(재계산 일치)·`test_release_artifact_checksum_differs_from_artifact_bytes_sha256`(최종 bytes sha256 과 다름)·`test_write_artifact_reproducible_bytes_include_release_artifact_checksum`(재현성) · **5D read model 통과 실증**은 `test_artifact_roundtrip.py`(아래) |
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

## 같은 이름·다른 정의 — `artifact_checksum`(D-5C-9b)

두 자리가 **이름은 같지만 값의 정의가 다르다**. `OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`는 이
정합(둘을 하나로 합칠지, 이름을 가를지)이 아직 없어 **열린 채로 둔다** — 정합은 2F/5E 나 5D
후속 변경 몫.

| 자리 | 정의 | 계산 | 값이 바뀌는 조건 |
| --- | --- | --- | --- |
| wire `ArtifactReference.release.artifact_checksum`(2C, registry/승격 소비) | `ArtifactBytes.sha256` — **최종 bytes 전체**의 sha256 | writer 가 실제 채운 값으로 두 번째 직렬화를 끝낸 뒤 그 bytes 를 해시 | payload 의 **어떤** 필드든 바뀌면 바뀐다(자기 자신 포함하지 않음 — 자기참조 아님) |
| 바이트 안 JSON `release.artifact_checksum`(5D read model `_parse_release`가 읽는 값) | 그 필드를 빈 문자열 `""`로 둔 **canonical bytes**(1단계 직렬화)의 sha256 | `_assemble_payload`가 블랭크 payload 를 만들고 `_fill_self_described_artifact_checksum`이 그 bytes 를 해시해 같은 자리를 채운다(두 단계 직렬화, 자기참조 회피) | payload 의 **자기 자신을 제외한** 다른 필드가 바뀌면 바뀐다 — 최종 `ArtifactBytes.sha256`과 값이 **항상 다르다**(블랭크 문자열이 채워진 뒤와 전의 바이트가 다르므로) |

`test_release_artifact_checksum_differs_from_artifact_bytes_sha256`가 이 불일치를 매 학습마다
실측 확인한다(우연히 같아지는 경우가 없음을 재확인하는 회귀 방지).

**verifier r3 L-1** — 바이트 안 값(둘째 행)은 **어떤 층도 재계산 대조하지 않는다.** 5D
`_parse_release`는 「비어 있지 않은 문자열」만 확인하고 `expected`와의 등가성은 보지
않는다(5D 문면 그대로, 자기서술 필드) — 실측: 그 자리에 `x` 한 글자를 심어도
`load_artifact`가 `LoadedArtifact`를 낸다. **결함이 아니다**(5D 설계 그대로) — 다만 이
자리의 무결성을 어느 층도 보증하지 않는다는 사실 자체가 등재 밖에 있었다. 정합은
`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`(위 표) 몫으로 그대로 둔다.

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
5. **5D `ArtifactManifestV1`와의 필드 어긋남 — (a)는 D-5C-9b(2026-09-13)로 해소, (b)는 실증 완료.**
   (a) ~~이 slice 의 `release`는 `artifact_checksum`을 담지 않는다~~ — D-5C-9b 로 채워졌다(위
   「같은 이름·다른 정의」 표). Python 타입 `ReleaseIdentity`(4필드)는 여전히 그 필드가 없지만,
   바이트 안 JSON `release.artifact_checksum`은 블랭크 canonical bytes 의 sha256 으로 채워
   5D read model 의 「비어 있지 않은 문자열」 요구를 만족한다.
   (b) 이 slice 는 5D 기본 필드 밖에 8개(`training_spec_version`·`training_spec_checksum`·
   `training_policy_version`·`feed_origin_only`·`categories`·`denominator_sources`·
   `agency_encoding`(5D 밖 신규 형태, 아래 6 참조)·`rejected_rows`)를 더 싣는다 — `test_artifact_roundtrip.py::
   test_5c1_additional_fields_are_not_rejected_by_5d_read_model`이 5D read model 이 미지
   top-level 키를 거부하지 **않음**을 실측했다(read model 은 `payload.get(key)`로만 읽고 키
   집합 전체를 대조하지 않는다). **`OPEN-5C-ARTIFACT-ROUNDTRIP`은 이 rebase 에서 닫혔다**
   (`test_artifact_roundtrip.py`, 아래 「닫힌 OPEN」) · **`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`는
   이름·값 정의가 여전히 둘이라 열린 채로 남는다**(위 표, 정합은 2F/5E/5D 후속 몫).
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

## rebase 뒤 수정(D-5C-9b·왕복 test)이 만든 public 표면 — 예상 0, 실측 0

`write_artifact`의 wire 출력에 필드 값 하나(`release.artifact_checksum`)가 추가됐지만
**필드 집합 자체는 5D scope ⑦이 처음부터 요구한 대로**(계약 갱신 이력 2026-09-13)라 새
public **Python 심볼**은 아니다. 이번 라운드에서 새로 도입한 이름(`_ArtifactPayload`·
`_ReleasePayload`·`_JsonValue`·`_canonical_bytes`·`_assemble_payload`·
`_fill_self_described_artifact_checksum`)은 전부 모듈 밑줄 접두(`training/__init__.py`
재수출 목록·`__all__`에 없음) — 외부에서 import 하는 경로가 없다(`grep -rn
"_ArtifactPayload\|_ReleasePayload\|_JsonValue\|_assemble_payload\|_fill_self_described"
tests/ src/ml_engine` 실측 — 매치 전부가 `artifact_writer.py` 파일 자신 안에만 있고,
그 파일 밖(`test_train_artifact.py` 포함) 매치는 0). `test_artifact_roundtrip.py`가
`ml_engine.registry.artifact`를
import 하는 것은 test 파일 안에서만이고(위 파일 docstring), production 코드의 import
그래프는 무변경.

**verifier r3 L-2** — 설계 래칫(`tools/design_ratchet.py`)의 약한 경계 판정은 **함수
시그니처**(매개변수·반환 annotation)만 보고 **클래스 본문 annotation은 보지 않는다**
(`is_weak_annotation`이 `ast.FunctionDef`/`ast.AsyncFunctionDef` 노드에서만 호출됨,
실측: `TypedDict` 본문에 `dict[str, object]` 를 하나 더 심어도 `--check` exit 0). 이
사각 안에 `_ArtifactPayload`의 `reproducibility`·`rejected_rows` 두 필드가 그 형태로
남아 있다 — **5C-1 결함이 아니다**(도구의 선언된 범위 안, 5A 소유). 두 값은
`dataclasses.asdict` → `json.dumps` 로만 흘러 타입 있는 소비자가 없어 실질 위험은
낮지만, 사각 자체를 등재한다.

## verifier r3 H-1 수정이 만든 public 표면 — 예상 0, 실측 0

프로덕션 코드(`src/ml_engine/**`)는 이번 라운드(verifier r3 인수 시점 `7e16a23` 이후)에서
**무변경** — 변이 주입·원복만 있었고 커밋된 diff 는 test 파일 둘뿐(`git diff --stat
7e16a23..HEAD -- ml-engine/src/` 실측: 빈 출력). 새로 도입한 이름
(`_ConstantPredictionBooster`·`_ConstantPredictionTrainer`·
`_labeled_dataset`)은 `test_train_artifact.py` 안의 모듈 밑줄 접두 test fixture 뿐이고
(`grep -rn` 실측 — 그 파일 밖 매치 0), `_expected_ref`의 시그니처 변경(`written` 한
인자 → `trained, written` 두 인자)은 `test_artifact_roundtrip.py` 자기 자신의 모듈
private 헬퍼라 외부 계약이 아니다.

## PR #13 code-reviewer 수정(HIGH 2·MEDIUM 2·LOW 1)이 만든 public 표면 — 새 enum 값 0

**새 enum 값을 만들지 않았다** — H-2(`policy.py`)는 `yaml.YAMLError`를 기존
`PolicyRejectionReason.MALFORMED`로 매핑했고, M-2(`dataset_files.py`)는 host 있는
`file://` URI를 기존 `DatasetUnreadableReason.UNSUPPORTED_SCHEME`으로 매핑했다(둘 다
"파일을 정상적으로 못 읽는다"는 의미가 이미 그 값과 같다 — 새 값을 만들 만큼 다른
사유가 아니라고 판단, checklist 인계 절 참고).

`train.py`의 새 함수(`_required_rows`·`_oof_error`)는 모듈 밑줄 접두이고
`training/__init__.py` 재수출 목록에 없다(`grep -rn "_oof_error\|_required_rows"
src/ml_engine tests/` 실측 — `train.py` 밖 매치 0). `_build_oof_and_space`가
`TrainingPolicy` 인자를 하나 더 받게 됐지만 이 함수 자체가 모듈 밑줄 접두라 외부
계약이 아니다. `dataset.py`의 `_parse_manifest`는 시그니처 무변경(내부 검증만 강화).
공개 결과 타입(`PolicyRejected`·`DatasetRejected`·`DatasetUnreadable`·
`TrainingRejected`)의 필드 집합도 무변경 — 값의 원인(`reason`)이 늘었을 뿐 타입
자체는 그대로다.

## PR #13 인계 — `OPEN-5C-YAML-ERROR-5D`

5D `inference/policy.py::load_inference_policy`도 같은 5A `registry.policy.load_policy`
를 거치므로 H-2 와 같은 구멍(`yaml.YAMLError` 미포착)을 가질 가능성이 높다 — **5D
코드는 편집하지 않는다**(5C 경계 밖). 5D 레인 또는 팀장이 확인·시정할 대상으로 등재.

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

## 닫힌 OPEN(이 rebase, 2026-09-13 계약 갱신 이력)

- ~~**`OPEN-5C-ARTIFACT-ROUNDTRIP`**~~ — 닫힘. 5D 가 `main`에 먼저 병합됐으므로(계약 갱신
  이력 「나중 병합 쪽 규칙」) 이 slice 가 `test_artifact_roundtrip.py`로 `write_artifact` →
  5D `load_artifact` 왕복(fake trainer 1·실 LightGBM 1·변조·release_id 불일치·5C-1 추가
  필드 여덟·manifest 필드 전달)을 실측 확인했다.

## 인계(팀장/타 레인) — 남은 것

- ~~milestone-5.md 5C 문면 개정~~ — `2e9f100`이 이미 완료했다(verifier r1 L-2, 낡은 인계 제거).
- **`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`** — **여전히 열려 있다.** D-5C-9b 로 왕복 실패는
  해소됐으나(위 「같은 이름·다른 정의」 표), 같은 이름의 두 값(wire `ArtifactReference.release.
  artifact_checksum` = 최종 bytes sha256 vs 바이트 안 `release.artifact_checksum` = 블랭크
  canonical bytes sha256)을 하나로 합칠지 이름을 가를지는 이 slice 가 정하지 않는다 — 정합은
  2F(계약 additive)·5E(승격 registry 소비 측)·5D 후속 변경 중 어디서 할지 팀장 결정 대상.
- **`OPEN-5C-5A-TABLE-REASSIGN`** — 5A `policy-values.md` #8·#31·#32·#33 재배정(scope.md §3 표) —
  이 slice 의 `policy-values.md` §3에 이미 기록, 5A 표 자체는 5D 병합 뒤 팀장이 정정.

## D-5C-11 관련 재확인 — 「Codex 코드 심사 범위」 해당 없음

이 slice 는 인증·인가·암호화·비밀번호/토큰·DB 마이그레이션·데이터 파기 경로를 만들지 않는다
(`v2-slice-pipeline`/CLAUDE.md 운영자 지시 2026-09-11 범위 밖) — 완료 조건은 `verifier`
ready-for-review + 사용자 승인.
