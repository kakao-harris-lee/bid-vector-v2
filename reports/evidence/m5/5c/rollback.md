# M5/5C-1 — rollback.md

> **2026-09-15 2차 rebase(main → `d4727fc`, PR #12) 뒤 재산출.** base 는 **`d4727fc`**(팀장
> 지시 — `cefb19c` 위에 5D PR #12 「pyyaml 을 serving/training extras 로」`0310b1a` + 머지
> 커밋이 origin `main`에 얹힘). 이전 판(`cefb19c` 기준)은 이 2차 rebase 로 커밋 해시가 다시
> 전부 바뀌었고 base 자체도 바뀌어 그대로 쓸 수 없다. **이 파일을 갱신하는 커밋 자신 이후로
> 이 slice 가 in_scope 경로를 더 건드리지 않는다면** 아래 목록은 낡지 않는다(2026-09-10 규율).

## 파일 목록(기계 산출)

```
git diff --name-status d4727fc..HEAD -- \
  ml-engine/src/ml_engine/training ml-engine/src/ml_engine/adapters \
  ml-engine/policy/training-v1.yaml ml-engine/tests/training ml-engine/tests/adapters \
  ml-engine/tests/gates/fixtures/bad_features_db ml-engine/tests/gates/test_import_contracts.py \
  ml-engine/tests/conftest.py ml-engine/tests/features/conftest.py \
  ml-engine/pyproject.toml ml-engine/uv.lock reports/evidence/m5/5c
```

출력(base `d4727fc`, HEAD 이 문서 갱신 커밋 직전 `0b4e891`):

```
A	ml-engine/policy/training-v1.yaml
M	ml-engine/pyproject.toml
M	ml-engine/src/ml_engine/adapters/__init__.py
A	ml-engine/src/ml_engine/adapters/dataset_files.py
M	ml-engine/src/ml_engine/training/__init__.py
A	ml-engine/src/ml_engine/training/artifact_writer.py
A	ml-engine/src/ml_engine/training/booster.py
A	ml-engine/src/ml_engine/training/corpus.py
A	ml-engine/src/ml_engine/training/dataset.py
A	ml-engine/src/ml_engine/training/encoding_oof.py
A	ml-engine/src/ml_engine/training/folds.py
A	ml-engine/src/ml_engine/training/policy.py
A	ml-engine/src/ml_engine/training/release.py
A	ml-engine/src/ml_engine/training/residual.py
A	ml-engine/src/ml_engine/training/spec.py
A	ml-engine/src/ml_engine/training/train.py
A	ml-engine/tests/adapters/conftest.py
A	ml-engine/tests/adapters/test_dataset_files.py
M	ml-engine/tests/conftest.py
D	ml-engine/tests/features/conftest.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/evaluation/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/features/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/inference/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/registry/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/serving/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/ml_engine/training/__init__.py
A	ml-engine/tests/gates/fixtures/bad_features_db/pyproject.toml
M	ml-engine/tests/gates/test_import_contracts.py
A	ml-engine/tests/training/conftest.py
A	ml-engine/tests/training/test_artifact_roundtrip.py
A	ml-engine/tests/training/test_booster.py
A	ml-engine/tests/training/test_corpus.py
A	ml-engine/tests/training/test_dataset.py
A	ml-engine/tests/training/test_encoding_oof.py
A	ml-engine/tests/training/test_folds.py
A	ml-engine/tests/training/test_no_stray_numeric_literals.py
A	ml-engine/tests/training/test_residual_release.py
A	ml-engine/tests/training/test_spec.py
A	ml-engine/tests/training/test_train_artifact.py
A	ml-engine/tests/training/test_training_policy.py
A	reports/evidence/m5/5c/checklist.md
A	reports/evidence/m5/5c/commands.md
A	reports/evidence/m5/5c/golden-manifest.json
A	reports/evidence/m5/5c/policy-values.md
A	reports/evidence/m5/5c/reuse.md
A	reports/evidence/m5/5c/rollback.md
A	reports/evidence/m5/5c/scope.md
```

**`ml-engine/uv.lock`은 이번 판에서 목록에서 완전히 빠졌다** — 5D PR #12(`0310b1a`)가
`pyyaml`을 serving·training extras 모두에 이미 추가해 lockfile 을 갱신했고, 이 slice의
동일 추가(2차 rebase 전 `59113d2`)가 그 위로 재적용되며 git 이 identical-content 로
병합해 최종 내용이 base 와 **바이트 동일**해졌다(2차 rebase 뒤 `git diff d4727fc..HEAD --
ml-engine/uv.lock` 빈 출력 실측, commands.md 「2차 rebase」 절). `matrix.py`·`test_matrix.py`
는 여전히 net-zero(추가 뒤 삭제)라 diff 에 안 보인다. `test_policy.py`는 base 에 없었으므로
`test_training_policy.py`로 신규 `A` 표기.

## `scope.md`·`policy-values.md` — rollback 대상에서 제외(이전 판과 동일 판단)

**`scope.md`**는 팀장 문서 레인 소관 커밋만 만졌다(`8683f29`·`df4d3c7`·`2d87dc7`·`265b77e`,
`git log d4727fc..HEAD -- scope.md` 실측) — 구현 레인 커밋 0.

**`policy-values.md`**는 넷: `8683f29`(팀장, 표 등재)·`a89f133`(구현 레인, r1 구현 확인 한
줄)·`265b77e`(팀장, **사용자 승인** 반영)·`691885d`(이번 세션, rename 인용 갱신 한 줄).
팀장 승인 커밋(`265b77e`)이 구현 레인 커밋(`a89f133`) 다음에 같은 파일의 인접 영역을
고쳐, 좁은 hunk 되돌리기가 사용자 승인 기록을 함께 훼손할 위험이 있다 — 이전 판과 같은
근거로 이 파일 전체를 rollback 대상에서 제외한다(원한다면 `a89f133`·`691885d` 두 커밋만
좁게 되돌리는 대안 가능, 「인계」 참고).

## 공유 파일 둘 — 커밋 해시 hunk 격리(uv.lock 은 대상 아님, 위 참고)

5D 는 base(`d4727fc`)에 **이미 병합돼 있다** — 아래 파일들에 5D 가 넣은 줄은 이 slice 의
어떤 커밋도 만지지 않았다(diff 실측, 「실측 결과」절).

| 파일 | 이 slice 의 커밋(적용 순서) | 1차 rebase(`cefb19c` 기준) 대응 커밋 | 2차 rebase 뒤 diff 축소 |
| --- | --- | --- | --- |
| `ml-engine/pyproject.toml` | `2d2ce27` | `59113d2` | training extras 의 `pyyaml==6.0.3` 추가 줄이 base(`d4727fc`)에 이미 있어 이 commit 의 diff 에서 **빠짐**(git 3-way 자동 병합이 identical addition 을 단일화) — 남은 diff 는 comment 두 곳 + forbidden 계약 신설뿐 |
| `ml-engine/tests/conftest.py` | `2d2ce27` | `59113d2` | 변경 없음(5D 무관 영역) |
| `ml-engine/tests/gates/test_import_contracts.py` | `57ae3e4` → `2d2ce27`(적용 역순, 나중 커밋 먼저) | `4a9bbf6` → `59113d2` | 변경 없음 |
| `ml-engine/tests/features/conftest.py`(삭제) | `2d2ce27` | `59113d2` | 변경 없음 |

**해시가 다시 바뀐 이유**: 2026-09-15 2차 rebase(`main`이 PR #12 로 `d4727fc`로 이동)가
전 커밋을 재작성했다 — 구조(어떤 파일을 어떤 순서로 고쳤는가)는 1차 rebase 판과 동일하고
해시만 바뀌었으며, `pyproject.toml`의 diff **내용**은 위 표대로 축소됐다(실측).

## rollback 절차(임시 worktree 실측, `cp -r` 금지)

```bash
git worktree add --detach <tmp> HEAD

cd <tmp>
rm -rf ml-engine/policy/training-v1.yaml \
  ml-engine/src/ml_engine/adapters/dataset_files.py \
  ml-engine/src/ml_engine/training/{artifact_writer,booster,corpus,dataset,encoding_oof,folds,policy,release,residual,spec,train}.py \
  ml-engine/tests/adapters ml-engine/tests/training ml-engine/tests/gates/fixtures/bad_features_db \
  reports/evidence/m5/5c/{checklist.md,commands.md,golden-manifest.json,reuse.md,rollback.md}

git restore --source=d4727fc --staged --worktree -- \
  ml-engine/src/ml_engine/adapters/__init__.py \
  ml-engine/src/ml_engine/training/__init__.py \
  ml-engine/tests/features/conftest.py

# 공유 파일 둘(pyproject.toml·tests/conftest.py) — 이 slice 유일 커밋(2d2ce27) hunk 되돌리기
git diff 2d2ce27~1..2d2ce27 -- ml-engine/pyproject.toml ml-engine/tests/conftest.py \
  | git apply -R

# test_import_contracts.py — 적용 역순으로 둘(57ae3e4 먼저, 2d2ce27 나중)
git diff 57ae3e4~1..57ae3e4 -- ml-engine/tests/gates/test_import_contracts.py | git apply -R
git diff 2d2ce27~1..2d2ce27 -- ml-engine/tests/gates/test_import_contracts.py | git apply -R

# uv.lock 은 base 와 이미 바이트 동일이라 되돌릴 대상이 없다
# scope.md·policy-values.md 는 손대지 않는다(위 절)
```

## 실측 결과(2026-09-15, `git worktree add --detach`, base `d4727fc`, HEAD `0b4e891`)

- `rm`·`restore`·hunk 되돌리기 전부 exit 0(`git apply -R -v`가 각 파일에 "패치 깔끔하게
  적용" 확인, conflict 0).
- **in_scope diff**: `git diff --stat d4727fc -- ml-engine reports/evidence/m5/5c
  ":(exclude)reports/evidence/m5/5c/scope.md" ":(exclude)reports/evidence/m5/5c/policy-values.md"`
  → **빈 출력**(exit 0) — rollback 후 작업 트리가 5C-1 구현이 전혀 없던 상태(5D PR #12 까지
  병합된 `d4727fc`)와 정확히 일치한다.
- **「내 줄 사라짐」grep 실측**: `features/training/evaluation/registry 는 DB···`(신설
  forbidden 계약 이름)·`test_real_pyproject_has_features_forbidden_contract`(함수 이름) —
  전부 매치 0.
- **「남의 줄(5D) 남음」grep 실측**: `pyproject.toml`의 `pyyaml==6.0.3`(dev·serving·training
  extras 세 곳 전부, 27·33·44행)·`select = [..., "BLE"]`(97행)·`test_import_contracts.py`의
  `test_forbidden_contract_forbids_bidvector_with_indirect_imports_allowed`(64행) — 전부
  매치 확인(그대로 남음).
- **되돌린 트리에서 acceptance 재실행**(`ml-engine/` 안, `uv sync --frozen --all-extras` 뒤):

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `uv run ruff check . && uv run ruff format --check .` | 0 | "All checks passed!" |
| `uv run mypy --strict src/ml_engine` | 0 | "Success: no issues found in 27 source files" |
| `uv run lint-imports` | 0 | "Contracts: 5 kept, 0 broken"(5C-1 신설 forbidden 계약 없음 — base 그대로) |
| `uv run python -m pytest tests -q` | 0 | **351 passed, 1 skipped** — 1차 rebase 판과 동일(코드 변경 없음, dependency 선언 병합만 있었으므로 예상대로) |
| `uv run python tools/design_ratchet.py --check` | 0 | "설계 래칫 위반 없음" |
| `uv run python tools/reuse_provenance_check.py` | 0 | "재활용 출처 두 자리 일치 — 위반 0" |
| S-9(python-version 대조) | 0 | 일치 |

worktree 는 검증 뒤 `git worktree remove --force <tmp>`로 제거했다(원본 저장소 무변경 확인 —
`git worktree list`에 재등장 없음).

## 인계(팀장) — policy-values.md 재분류 판단(1차 rebase 판에서 이월, 변경 없음)

`policy-values.md`를 `scope.md`와 같은 「계약/승인 기록, rollback 비대상」 범주로 유지한다
— 사유는 위 절. **원한다면** `a89f133`·`691885d` 두 커밋만의 hunk 를 `265b77e`와 겹치지
않는 자리인지 확인한 뒤 좁게 되돌리는 대안도 가능하나, 이번 세션도 사용자 승인 기록 훼손
위험을 코드 rollback 편의보다 우선해 보수적으로 제외했다.

## `milestone-5.md`

이 slice 의 착수 문단·5C-1 종결 문단은 팀장 문서 레인 소관 커밋(`8683f29`·`265b77e`)이 이미
완료했다 — 구현 레인은 손대지 않았으므로(`git log d4727fc..HEAD -- milestone-5.md` 실측)
이 rollback 목록에서 계속 제외한다.
