# M5/5C-1 — rollback.md

> **2026-09-12 verifier r1 M-2 뒤 재산출.** 이전 판(HEAD `8819eaf` 시점 작성)은 그 판 자신을
> 담은 커밋(`8819eaf`, `rollback.md` 자신을 추가)을 목록에서 빠뜨려 "빈 출력"이 재현되지
> 않았다(2026-09-10 규율 위반 — 「rollback 목록은 자기를 담은 커밋을 가리킬 수 없다」). 이
> 판은 verifier r1 수정 라운드(H-1·H-2·H-3·M-1·LOW 7)가 전부 끝난 뒤 산출했고, **이 파일을
> 갱신하는 커밋 자신 이후로 이 slice 가 in_scope 경로를 더 건드리지 않는다** — 그래서 아래
> 목록이 그 커밋을 포함해도 낡지 않는다(같은 함정의 재발 방지 조건을 명시).

## 파일 목록(기계 산출, verifier r1 수정 라운드 전부 반영 뒤)

```
git diff --name-status f5020aa..HEAD -- \
  ml-engine/src/ml_engine/training ml-engine/src/ml_engine/adapters \
  ml-engine/policy/training-v1.yaml ml-engine/tests/training ml-engine/tests/adapters \
  ml-engine/tests/gates/fixtures/bad_features_db ml-engine/tests/gates/test_import_contracts.py \
  ml-engine/tests/conftest.py ml-engine/tests/features/conftest.py \
  ml-engine/pyproject.toml ml-engine/uv.lock reports/evidence/m5/5c
```

출력(base `f5020aa` = PR #10 머지 커밋):

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
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/evaluation/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/features/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/inference/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/registry/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/serving/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/training/__init__.py
A  ml-engine/tests/gates/fixtures/bad_features_db/pyproject.toml
M  ml-engine/tests/gates/test_import_contracts.py
A  ml-engine/tests/training/conftest.py
A  ml-engine/tests/training/test_booster.py
A  ml-engine/tests/training/test_corpus.py
A  ml-engine/tests/training/test_dataset.py
A  ml-engine/tests/training/test_encoding_oof.py
A  ml-engine/tests/training/test_folds.py
A  ml-engine/tests/training/test_no_stray_numeric_literals.py
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
A  reports/evidence/m5/5c/rollback.md
A  reports/evidence/m5/5c/scope.md
```

`matrix.py`·`test_matrix.py`는 verifier r1 M-1 뒤 삭제됐다 — 순추가(add) 뒤 순삭제라 base
대비 diff 에 **나타나지 않는다**(net zero, 기계 산출 그대로 반영됨 — 목록에 없다고 낡은 것이
아니다).

**`reports/evidence/m5/5c/scope.md`·`policy-values.md`의 base 판(`2e9f100`)은 5C-1 구현
레인이 만들지 않았다** — 팀장 착수 계약 커밋이다(`git log f5020aa..HEAD -- scope.md` =
`2e9f100` 단독). `policy-values.md`는 이 slice 의 `01ad507`이 `change_history`에 한 행만
추가했다(부분 수정 — hunk 격리 대상). `scope.md`는 이 slice 의 어떤 커밋도 만지지 않는다
(hunk 격리 대상에서 완전히 제외, rollback 시 손대지 않는다).

## 공유 파일 넷 + 1 — 커밋 해시 hunk 격리

| 파일 | 이 slice 의 커밋(적용 순서) | 5D 흔적(2026-09-12 실측 시점) |
| --- | --- | --- |
| `ml-engine/pyproject.toml` | `6647a66` | 없음(5D 는 `m5-5d/2026-09-12` 별도 브랜치, 아직 미병합) |
| `ml-engine/tests/conftest.py` | `6647a66` | 없음 |
| `ml-engine/tests/gates/test_import_contracts.py` | `6647a66` → `79d084e`(verifier r1 H-3) **둘** | 없음 |
| `ml-engine/tests/features/conftest.py`(삭제) | `6647a66` | 없음 |
| `reports/evidence/m5/5c/policy-values.md` | `01ad507`(팀장의 `2e9f100` 위에 한 행 추가) | 해당 없음(5C-1 전용 문서) |

**`test_import_contracts.py`는 이제 이 slice 의 커밋 둘이 같은 파일을 순차로 고쳤다** — hunk
되돌리기는 **적용 역순**(나중 커밋 먼저)으로 한다: `79d084e` 되돌리기 → `6647a66` 되돌리기.
순서를 바꾸면 `git apply -R`가 대상 hunk 를 찾지 못해 실패한다(먼저 되돌려야 할 변경이 아직
반영 안 된 상태이므로).

**5D 병합 전(현재)**: 위 파일 각각 이 slice 의 커밋(들)이 유일한 변경이므로 hunk 격리는 파일
전체를 `f5020aa`로 되돌리는 것과 결과가 같다(아래 실측이 이 경로).
**5D 병합 후(향후)**: 각 hunk 를 걷어낸 뒤 "5D 가 넣은 줄이 남아 있는지"를 diff 로 재확인한다
(2026-09-09 하네스 규율 — 「남의 줄 남음」 실측 필수).

## rollback 절차(임시 worktree 실측, `cp -r` 금지)

```bash
git worktree add --detach <tmp> HEAD

cd <tmp>
rm -rf ml-engine/policy/training-v1.yaml \
  ml-engine/src/ml_engine/adapters/dataset_files.py \
  ml-engine/src/ml_engine/training/{artifact_writer,booster,corpus,dataset,encoding_oof,folds,policy,release,residual,spec,train}.py \
  ml-engine/tests/adapters ml-engine/tests/training ml-engine/tests/gates/fixtures/bad_features_db \
  reports/evidence/m5/5c/{checklist.md,commands.md,golden-manifest.json,reuse.md,rollback.md}

git restore --source=f5020aa --staged --worktree -- \
  ml-engine/src/ml_engine/adapters/__init__.py \
  ml-engine/src/ml_engine/training/__init__.py \
  ml-engine/uv.lock \
  ml-engine/tests/features/conftest.py

# 공유 파일 셋(pyproject.toml·tests/conftest.py) — 이 slice 유일 커밋(6647a66) hunk 되돌리기
git diff 6647a66~1..6647a66 -- ml-engine/pyproject.toml ml-engine/tests/conftest.py \
  | git apply -R

# test_import_contracts.py — 적용 역순으로 둘(79d084e 먼저, 6647a66 나중)
git diff 79d084e~1..79d084e -- ml-engine/tests/gates/test_import_contracts.py | git apply -R
git diff 6647a66~1..6647a66 -- ml-engine/tests/gates/test_import_contracts.py | git apply -R

# policy-values.md — 이 slice 유일 커밋(01ad507) hunk 되돌리기
git diff 01ad507~1..01ad507 -- reports/evidence/m5/5c/policy-values.md | git apply -R
```

## 실측 결과(2026-09-12, verifier r1 수정 라운드 뒤 재실행, `git worktree add --detach`)

- `git diff --stat 2e9f100 -- ml-engine reports/evidence/m5/5c` → `scope.md` 2줄만 남는다.
  그 2줄은 이 slice 의 수정이 아니라 **팀장이 verifier r1 보고 뒤에 낸 계약 갱신
  커밋(`f33e443`, 이 rollback 목록 밖 — scope.md 는 위 「hunk 격리 대상에서 완전히 제외」)**
  이다 — `f33e443`은 `6647a66`(내 마지막 착수 커밋)과 `eed117f`(내 첫 수정 커밋) 사이에
  들어온 팀장 문서 커밋이라 내 rollback 이 되돌리지 않는다(scope.md 는 애초에 손대지
  않는다고 선언했다). `scope.md` 를 제외하면(`git diff --stat 2e9f100 -- ml-engine
  "reports/evidence/m5/5c/*" ":!reports/evidence/m5/5c/scope.md"`) **빈 출력** — rollback
  후 작업 트리가 5C-1 구현이 전혀 없던 상태와 정확히 일치한다.
- `find ml-engine/src/ml_engine/training ml-engine/tests/training ml-engine/tests/adapters ml-engine/tests/gates/fixtures/bad_features_db` →
  `training/__init__.py`(base 판, 재수출 없는 빈 상태) 하나만 남고 나머지 전부 소거 확인.
- acceptance 재실행(`ml-engine/` 안, `uv sync --frozen --all-extras` 뒤):

| 명령 | exit |
| --- | --- |
| `uv run ruff check . && uv run ruff format --check .` | 0 |
| `uv run mypy --strict src/ml_engine` | 0("18 source files") |
| `uv run lint-imports` | 0("Contracts: 5 kept, 0 broken") |
| `uv run python -m pytest tests -q` | 0(**233 passed** — 5A+5B 베이스라인과 정확히 일치) |
| `uv run python tools/design_ratchet.py --check` | 0 |
| `uv run python tools/reuse_provenance_check.py` | 0 |
| S-9(python-version 대조) | 0 |

worktree 는 검증 뒤 `git worktree remove --force <tmp>`로 제거했다(원본 저장소 무변경 확인 —
`git worktree list`에 5C-1 워크트리 재등장 없음).

## `milestone-5.md`

이 slice 의 착수 문단·5C 문면 개정은 팀장 문서 레인 소관 커밋(`2e9f100`)이 이미 완료했다
(verifier r1 L-2 확인) — 구현 레인은 손대지 않았으므로 이 rollback 목록에서 계속 제외한다.
