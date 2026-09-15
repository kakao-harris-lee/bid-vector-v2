# M5/5C-1 — rollback.md

> **2026-09-15 재산출(팀장 지시) — `policy-values.md` 처리를 「제외」에서 「hunk 격리」로
> 교체.** base 는 여전히 **`d4727fc`**(PR #12 병합, 2차 rebase). 이전 판은 `policy-values.md`
> 를 `scope.md`와 같이 rollback 대상에서 **제외**했다(팀장 승인 커밋이 구현 레인 커밋과
> 같은 파일을 인접 수정해 좁은 hunk 되돌리기가 승인 기록을 훼손할 위험이 있다는 추정).
> 팀장이 "대안을 문서에 명령으로 적고 임시 worktree 에서 한 번 실제 실행해 exit 를 남기라"
> 고 지시해 **실제로 실행**했더니(아래 「실측」) **충돌 없이 깔끔하게 적용됐다** — 팀장
> 승인 커밋(`265b77e`)이 파일 **머리**(제목·지위 문구)만 고치고 구현 레인 커밋 둘
> (`a89f133`·`691885d`)은 파일 **끝**(`change_history` 표에 행 추가)만 고쳐 두 영역이
> 겹치지 않았기 때문이다. **추정이 틀렸음이 실측으로 드러나** 이번 판은 `policy-values.md`
> 도 다른 공유 파일처럼 hunk 격리 대상으로 승격한다 — `scope.md`만 계속 제외한다(그 파일은
> 구현 레인 커밋이 **0건**이라 격리할 hunk 자체가 없다, 실측 아래).

## 파일 목록(기계 산출)

```
git diff --name-status d4727fc..HEAD -- \
  ml-engine/src/ml_engine/training ml-engine/src/ml_engine/adapters \
  ml-engine/policy/training-v1.yaml ml-engine/tests/training ml-engine/tests/adapters \
  ml-engine/tests/gates/fixtures/bad_features_db ml-engine/tests/gates/test_import_contracts.py \
  ml-engine/tests/conftest.py ml-engine/tests/features/conftest.py \
  ml-engine/pyproject.toml ml-engine/uv.lock reports/evidence/m5/5c
```

출력(base `d4727fc`, HEAD 이 문서 갱신 커밋 직전 `452e9d8` — 팀장의 `scope.md` `base_sha`
갱신 커밋 포함, 그 커밋은 `scope.md` 한 파일만 만져 아래 목록에 영향 없음):

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

`ml-engine/uv.lock`은 목록에서 빠진다 — 5D PR #12(`0310b1a`)가 pyyaml을 serving·training
extras 모두에 이미 추가해 이 slice 의 동일 추가와 identical-content 병합되며 base 와
바이트 동일해졌다(2차 rebase 뒤 `git diff d4727fc..HEAD -- ml-engine/uv.lock` 빈 출력,
commands.md 「2차 rebase」절). `matrix.py`·`test_matrix.py`는 net-zero(추가 뒤 삭제)라
diff 에 안 보인다. `test_policy.py`는 base 에 없었으므로 `test_training_policy.py`로
신규 `A` 표기.

## `scope.md` — 유일한 rollback 제외 대상(구현 레인 커밋 0건, 격리할 hunk 자체가 없다)

`git log d4727fc..HEAD -- reports/evidence/m5/5c/scope.md` → `8683f29`·`df4d3c7`·
`2d87dc7`·`265b77e`·`452e9d8` 전부 팀장 문서 레인 커밋(메시지 접두 `docs(m5-5c1)`,
내용 전부 계약 고정·갱신·`base_sha` 정정) — 구현 레인이 이 파일을 만진 적이 없다.
「hunk 격리」는 되돌릴 구현 레인 hunk 가 있을 때 의미가 있는 절차이고, 이 파일은 그
전제 자체가 성립하지 않는다 — 「제외」가 아니라 「대상 없음」이 정확한 서술이다.

## 공유 파일 셋 — 커밋 해시 hunk 격리

5D 는 base(`d4727fc`)에 **이미 병합돼 있다** — 아래 파일들에 5D 가 넣은 줄은 이 slice 의
어떤 커밋도 만지지 않았다(diff 실측, 「실측 결과」절).

| 파일 | 이 slice 의 커밋(적용 순서) | 비고 |
| --- | --- | --- |
| `ml-engine/pyproject.toml` | `2d2ce27` | training extras 의 `pyyaml==6.0.3` 추가 줄이 base(`d4727fc`)에 이미 있어 이 커밋 diff 에서 그 부분만 빠짐(git 3-way 자동 병합, 남은 diff 는 comment 두 곳 + forbidden 계약 신설) |
| `ml-engine/tests/conftest.py` | `2d2ce27` | — |
| `ml-engine/tests/gates/test_import_contracts.py` | `57ae3e4` → `2d2ce27`(적용 역순, 나중 커밋 먼저) | — |
| `ml-engine/tests/features/conftest.py`(삭제) | `2d2ce27` | — |
| `reports/evidence/m5/5c/policy-values.md` | `691885d` → `a89f133`(적용 역순, 나중 커밋 먼저) | **이번 판에서 격리 대상으로 승격**(아래 「실측」— 팀장 승인 커밋 `265b77e`와 영역이 겹치지 않아 충돌 없이 적용됨을 확인) |

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

# policy-values.md — 적용 역순으로 둘(691885d 먼저, a89f133 나중) — 팀장 승인(265b77e)의
# 머리글(제목·지위 문구)은 다른 영역이라 그대로 남는다(아래 실측이 이를 확인)
git diff 691885d~1..691885d -- reports/evidence/m5/5c/policy-values.md | git apply -R
git diff a89f133~1..a89f133 -- reports/evidence/m5/5c/policy-values.md | git apply -R

# scope.md 는 손대지 않는다(구현 레인 커밋 0건, 위 절)
```

## 실측 결과(2026-09-15, `git worktree add --detach`, base `d4727fc`, HEAD `452e9d8`)

- **`main`(=`d4727fc`) 자체에서 직접 pytest 를 세어 기대치를 확정**(팀장 지시) —
  `/Users/harris/Development/private/bid-vector-v2`(root worktree, `main`, `d4727fc`)에서
  `uv sync --frozen --all-extras && uv run python -m pytest tests -q` → **351 passed,
  1 skipped**. 이 값이 아래 되돌린 트리의 기대치다.
- `rm`·`restore`·hunk 되돌리기 전부 exit 0(`git apply -R -v`가 각 파일에 "패치 깔끔하게
  적용" 확인, conflict 0 — `policy-values.md` 포함 전부).
- **`policy-values.md` 좁은 되돌리기 실측**(팀장이 요구한 「실제 실행 + exit」):
  - `git diff 691885d~1..691885d -- reports/evidence/m5/5c/policy-values.md | git apply -R -v`
    → exit 0("패치 깔끔하게 적용").
  - `git diff a89f133~1..a89f133 -- reports/evidence/m5/5c/policy-values.md | git apply -R -v`
    → exit 0("패치 깔끔하게 적용") — **이전 판이 예상했던 충돌이 재현되지 않았다.**
  - 되돌린 파일의 머리(`# M5/5C-1 정책 값 — ...사용자 승인 2026-09-13...` · `> **지위:
    승인.**`)는 `265b77e`가 낸 그대로 남았고, `change_history` 표는 팀장의 최초 행
    (`2026-09-12 착수 | 표 등재(승인 대기)`) 하나만 남아 구현 레인이 붙인 두 행
    (`a89f133`의 "구현 확인"·`691885d`의 "rebase 뒤")이 사라졌음을 파일 내용으로 직접
    확인(head/tail 대조).
- **in_scope diff**: `git diff --stat d4727fc -- ml-engine reports/evidence/m5/5c
  ":(exclude)reports/evidence/m5/5c/scope.md"` → `policy-values.md` 43줄 추가만 남는다
  (파일 전체가 base 에 존재한 적이 없어 diff 가 "신규 파일"로 잡히는 것은 예상된 동작 —
  `scope.md`와 같은 사정). 그 43줄이 정확히 「`8683f29`(팀장 최초 작성) + `265b77e`(팀장
  승인 편집)」의 합과 바이트 동일한지는 아래로 확인한다.
- **정합 확인**: 되돌린 `policy-values.md`에서 구현 레인 행 둘을 뺀 결과가 팀장 최신
  커밋(`265b77e`)의 스냅샷과 **정확히 같은 하나 전 상태**(즉 `a89f133`가 붙인 행이
  `265b77e`엔 이미 있고 이번 되돌리기가 그 행까지 걷어냈으므로, `265b77e` 자체와는
  한 행 차이가 남는다 — 이는 결함이 아니라 "구현 레인 행 둘 다 제거"라는 되돌리기의
  정의상 결과다). 팀장이 원하는 것이 "구현 레인 기여 전부 제거"라면 이 결과가 맞고,
  "`a89f133`(첫 구현 확인)은 남기고 `691885d`(rebase 정합)만 제거"를 원한다면
  `691885d`의 hunk 만 되돌리면 된다(그 경우도 위와 같은 이유로 conflict 없이 될 것으로
  예상되나 이번 실측은 「둘 다 제거」 경로만 실행했다).
- **되돌린 트리에서 acceptance 재실행**(`ml-engine/` 안, `uv sync --frozen --all-extras` 뒤):

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `uv run ruff check . && uv run ruff format --check .` | 0 | "All checks passed!" |
| `uv run mypy --strict src/ml_engine` | 0 | "Success: no issues found in 27 source files" |
| `uv run lint-imports` | 0 | "Contracts: 5 kept, 0 broken" |
| `uv run python -m pytest tests -q` | 0 | **351 passed, 1 skipped** — `main`(`d4727fc`) 직접 실측치와 **정확히 일치** |
| `uv run python tools/design_ratchet.py --check` | 0 | "설계 래칫 위반 없음" |
| `uv run python tools/reuse_provenance_check.py` | 0 | "재활용 출처 두 자리 일치 — 위반 0" |
| S-9(python-version 대조) | 0 | 일치 |

worktree 는 검증 뒤 `git worktree remove --force <tmp>`로 제거했다(원본 저장소 무변경 확인 —
`git worktree list`에 재등장 없음).

## `milestone-5.md`

이 slice 의 착수 문단·5C-1 종결 문단은 팀장 문서 레인 소관 커밋(`8683f29`·`265b77e`)이 이미
완료했다 — 구현 레인은 손대지 않았으므로(`git log d4727fc..HEAD -- milestone-5.md` 실측)
`scope.md`와 같은 사유로 계속 제외한다(구현 레인 커밋 0건, 격리 대상 없음).
