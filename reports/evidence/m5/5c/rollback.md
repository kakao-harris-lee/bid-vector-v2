# M5/5C-1 — rollback.md

## 파일 목록(기계 산출)

```
git diff --name-status f5020aa..HEAD -- \
  ml-engine/src/ml_engine/training ml-engine/src/ml_engine/adapters \
  ml-engine/policy/training-v1.yaml ml-engine/tests/training ml-engine/tests/adapters \
  ml-engine/tests/gates/fixtures/bad_features_db ml-engine/tests/gates/test_import_contracts.py \
  ml-engine/tests/conftest.py ml-engine/tests/features/conftest.py \
  ml-engine/pyproject.toml ml-engine/uv.lock reports/evidence/m5/5c
```

출력(HEAD `01ad507`, base `f5020aa` = PR #10 머지 커밋):

```
A  ml-engine/policy/training-v1.yaml
M  ml-engine/pyproject.toml
M  ml-engine/src/ml_engine/adapters/__init__.py
A  ml-engine/src/ml_engine/adapters/dataset_files.py
M  ml-engine/src/ml_engine/training/__init__.py
A  ml-engine/src/ml_engine/training/artifact_writer.py
A  ml-engine/src/ml_engine/training/booster.py
A  ml-engine/src/ml_engine/training/corpus.py
A  ml-engine/src/ml_engine/training/dataset.py
A  ml-engine/src/ml_engine/training/encoding_oof.py
A  ml-engine/src/ml_engine/training/folds.py
A  ml-engine/src/ml_engine/training/matrix.py
A  ml-engine/src/ml_engine/training/policy.py
A  ml-engine/src/ml_engine/training/release.py
A  ml-engine/src/ml_engine/training/residual.py
A  ml-engine/src/ml_engine/training/spec.py
A  ml-engine/src/ml_engine/training/train.py
A  ml-engine/tests/adapters/conftest.py
A  ml-engine/tests/adapters/test_dataset_files.py
M  ml-engine/tests/conftest.py
D  ml-engine/tests/features/conftest.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/features/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/pyproject.toml
M  ml-engine/tests/gates/test_import_contracts.py
A  ml-engine/tests/training/conftest.py
A  ml-engine/tests/training/test_booster.py
A  ml-engine/tests/training/test_corpus.py
A  ml-engine/tests/training/test_dataset.py
A  ml-engine/tests/training/test_encoding_oof.py
A  ml-engine/tests/training/test_folds.py
A  ml-engine/tests/training/test_matrix.py
A  ml-engine/tests/training/test_policy.py
A  ml-engine/tests/training/test_residual_release.py
A  ml-engine/tests/training/test_spec.py
A  ml-engine/tests/training/test_train_artifact.py
M  ml-engine/uv.lock
A  reports/evidence/m5/5c/checklist.md
A  reports/evidence/m5/5c/commands.md
A  reports/evidence/m5/5c/golden-manifest.json
A  reports/evidence/m5/5c/policy-values.md
A  reports/evidence/m5/5c/reuse.md
A  reports/evidence/m5/5c/scope.md
```

**`reports/evidence/m5/5c/scope.md`은 이 목록에 기계적으로 잡히지만 5C-1 구현 레인이 만들지
않았다** — `2e9f100`(팀장 착수 계약 고정 커밋, base 이후·구현 레인 착수 이전)가 저작했다.
이 slice 의 어떤 커밋도 `scope.md`를 만지지 않는다(`git log f5020aa..HEAD -- reports/evidence/m5/5c/scope.md`
= `2e9f100` 단 하나) — 롤백 대상에서 **제외**한다.

`reports/evidence/m5/5c/policy-values.md`는 `2e9f100`가 이미 만든 파일에 이 slice 의 커밋
`01ad507`이 `change_history`에 한 행만 추가했다 — **공유 파일**로 취급해 커밋 해시 hunk
격리 대상에 넣는다(전체 삭제 아님).

## 공유 파일 넷 + 1 — 커밋 해시 hunk 격리

| 파일 | 이 slice 의 유일 커밋 | 5D 흔적(2026-09-12 실측 시점) |
| --- | --- | --- |
| `ml-engine/pyproject.toml` | `6647a66` | 없음(5D 는 `m5-5d/2026-09-12` 별도 브랜치, 아직 미병합) |
| `ml-engine/tests/conftest.py` | `6647a66` | 없음 |
| `ml-engine/tests/gates/test_import_contracts.py` | `6647a66` | 없음 |
| `ml-engine/tests/features/conftest.py`(삭제) | `6647a66` | 없음 |
| `reports/evidence/m5/5c/policy-values.md` | `01ad507` | 해당 없음(5C-1 전용 문서) |

**5D 병합 전(현재)**: 위 4파일 각각 이 slice 의 커밋이 유일한 변경이므로 hunk 격리는
파일 전체를 `f5020aa`로 되돌리는 것과 결과가 같다(아래 실측이 이 경로).
**5D 병합 후(향후)**: `git diff <sha>~1..<sha> -- <파일> | git apply -R`로 각 파일에서
**이 slice 의 hunk만** 걷어내고, 적용 뒤 "5D 가 넣은 줄이 남아 있는지"를 diff로 재확인한다
(2026-09-09 하네스 규율 — 「남의 줄 남음」 실측 필수).

## rollback 절차(임시 worktree 실측, `cp -r` 금지)

```bash
git worktree add --detach <tmp> HEAD   # 01ad507

cd <tmp>
rm -rf ml-engine/policy/training-v1.yaml \
  ml-engine/src/ml_engine/adapters/dataset_files.py \
  ml-engine/src/ml_engine/training/{artifact_writer,booster,corpus,dataset,encoding_oof,folds,matrix,policy,release,residual,spec,train}.py \
  ml-engine/tests/adapters ml-engine/tests/training ml-engine/tests/gates/fixtures/bad_features_db \
  reports/evidence/m5/5c/{checklist.md,commands.md,golden-manifest.json,reuse.md}

git restore --source=f5020aa --staged --worktree -- \
  ml-engine/src/ml_engine/adapters/__init__.py \
  ml-engine/src/ml_engine/training/__init__.py \
  ml-engine/uv.lock \
  ml-engine/tests/features/conftest.py

# 공유 파일 넷 — 이 slice 유일 커밋(6647a66) hunk 되돌리기
git diff 6647a66~1..6647a66 -- ml-engine/pyproject.toml ml-engine/tests/conftest.py \
  ml-engine/tests/gates/test_import_contracts.py | git apply -R

# policy-values.md — 이 slice 유일 커밋(01ad507) hunk 되돌리기
git diff 01ad507~1..01ad507 -- reports/evidence/m5/5c/policy-values.md | git apply -R
```

## 실측 결과(2026-09-12, `git worktree add --detach`)

- `git diff --stat 2e9f100 -- ml-engine reports/evidence/m5/5c` → **빈 출력**(rollback 후 작업
  트리가 `2e9f100`, 즉 5C-1 착수 직전 상태와 정확히 일치 — `scope.md`는 애초에 두 상태에서
  동일하므로 diff 대상에서 자연히 빠진다).
- `find ml-engine/src/ml_engine/training ml-engine/tests/training ml-engine/tests/adapters ml-engine/tests/gates/fixtures/bad_features_db` →
  `training/__init__.py`(base 판, 재수출 없는 빈 상태) 하나만 남고 나머지 전부 소거 확인.
- acceptance 재실행(`ml-engine/` 안, `uv sync --frozen --all-extras` 뒤):

| 명령 | exit |
| --- | --- |
| `uv run ruff check . && uv run ruff format --check .` | 0 |
| `uv run mypy --strict src/ml_engine` | 0("18 source files" — training/adapters 제거로 31→18) |
| `uv run lint-imports` | 0("Contracts: 5 kept, 0 broken" — 신설 계약 소거로 6→5) |
| `uv run python -m pytest tests -q` | 0(**233 passed** — 5A+5B 베이스라인과 정확히 일치) |
| `uv run python tools/design_ratchet.py --check` | 0 |
| `uv run python tools/reuse_provenance_check.py` | 0 |
| S-9(python-version 대조) | 0 |

worktree 는 검증 뒤 `git worktree remove --force <tmp>`로 제거했다(원본 저장소 무변경 확인 —
`git worktree list`에 5C-1 워크트리 재등장 없음).

## `milestone-5.md`

이 slice 의 착수 문단·5C 문면 개정은 **팀장 문서 레인 소관**(구현 레인 편집 금지 지시,
`launch` 프롬프트) — 이 rollback 목록에서 제외한다. 팀장이 개정했다면 그 되돌림은 팀장
자신의 커밋 단위로 별도 처리한다.
