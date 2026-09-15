# M5/5C-1 — rollback.md

> **2026-09-13 rebase 뒤(5D 병합·D-5C-9b·왕복 test) 재산출.** base 는 **`cefb19c`**(5D 가 로컬
> `main`에 먼저 병합된 지점 — scope.md 「병합 순서」). 이전 판(`f5020aa` 기준, verifier r1 뒤)은
> rebase 로 커밋 해시가 전부 바뀌었고 base 자체도 바뀌어 그대로 쓸 수 없다. **이 파일을 갱신하는
> 커밋 자신 이후로 이 slice 가 in_scope 경로를 더 건드리지 않는다면** 아래 목록은 낡지 않는다
> (2026-09-10 규율 — 「rollback 목록은 자기를 담은 커밋을 가리킬 수 없다」).

## 파일 목록(기계 산출)

```
git diff --name-status cefb19c..HEAD -- \
  ml-engine/src/ml_engine/training ml-engine/src/ml_engine/adapters \
  ml-engine/policy/training-v1.yaml ml-engine/tests/training ml-engine/tests/adapters \
  ml-engine/tests/gates/fixtures/bad_features_db ml-engine/tests/gates/test_import_contracts.py \
  ml-engine/tests/conftest.py ml-engine/tests/features/conftest.py \
  ml-engine/pyproject.toml ml-engine/uv.lock reports/evidence/m5/5c
```

출력(base `cefb19c`, HEAD 이 문서 갱신 커밋 직전 `c8b5541`):

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
A  ml-engine/tests/training/test_artifact_roundtrip.py
A  ml-engine/tests/training/test_booster.py
A  ml-engine/tests/training/test_corpus.py
A  ml-engine/tests/training/test_dataset.py
A  ml-engine/tests/training/test_encoding_oof.py
A  ml-engine/tests/training/test_folds.py
A  ml-engine/tests/training/test_no_stray_numeric_literals.py
A  ml-engine/tests/training/test_residual_release.py
A  ml-engine/tests/training/test_spec.py
A  ml-engine/tests/training/test_train_artifact.py
A  ml-engine/tests/training/test_training_policy.py
M  ml-engine/uv.lock
A  reports/evidence/m5/5c/checklist.md
A  reports/evidence/m5/5c/commands.md
A  reports/evidence/m5/5c/golden-manifest.json
A  reports/evidence/m5/5c/policy-values.md
A  reports/evidence/m5/5c/reuse.md
A  reports/evidence/m5/5c/rollback.md
A  reports/evidence/m5/5c/scope.md
```

`matrix.py`·`test_matrix.py`는 verifier r1 M-1 뒤 삭제됐다 — 순추가 뒤 순삭제라 base 대비
diff 에 나타나지 않는다(net zero, 이전 판과 동일 사실). `test_policy.py`는 이 rebase 세션에서
`test_training_policy.py`로 개명됐다(`ea67c10`) — base(`cefb19c`)에는 애초에 `test_policy.py`
가 없었으므로(5C-1 이 만든 파일) rename 이 아니라 신규 파일 `A`로만 나타난다.

## `scope.md`·`policy-values.md` — 이번 판에서 **둘 다** rollback 대상에서 제외(판단 근거 갱신)

**`scope.md`**는 이전 판과 동일하게 제외한다 — 팀장 문서 레인 소관 커밋(`873352f`·`61c3a23`·
`82b6f40`·`bf6cc8c`)만 만졌고 구현 레인 커밋은 이 파일을 건드리지 않는다(`git log
cefb19c..HEAD -- reports/evidence/m5/5c/scope.md`로 실측).

**`policy-values.md`는 이번 판에서 제외 범위를 넓혔다** — 이전 판(verifier r1 뒤)은 팀장의
착수 커밋 위에 구현 레인 커밋 하나(그때 `01ad507`)만 얹혀 있어 그 한 커밋만 hunk 격리로
되돌리면 됐다. 이번 rebase 뒤에는 `git log cefb19c..HEAD -- reports/evidence/m5/5c/policy-values.md`
가 **넷**을 보인다: `873352f`(팀장, 표 등재)·`d04cc3b`(구현 레인, r1 구현 확인 한 줄 —
이전 판의 `01ad507`에 해당)·`bf6cc8c`(팀장, **사용자 승인 반영** — 「운영자 확인 대기」를
「확정」으로 바꾸는 승인 기록)·`ea67c10`(이번 세션, rename 인용 갱신 한 줄). 팀장 커밋
(`bf6cc8c`)이 구현 레인 커밋(`d04cc3b`) 다음에 끼어들며 같은 파일의 인접 영역을 고쳤을 가능성이
있어, `d04cc3b`·`ea67c10` 두 커밋만 순서대로 hunk 되돌리기를 시도하면 `bf6cc8c`가 이미 그
근방을 바꿔놓은 상태와 충돌하거나(운이 나쁘면) 혹은 우연히 성공하더라도 **팀장의 승인
기록까지 함께 걷어낼 위험**을 배제할 수 없다 — 사용자 승인이라는 계약 상태 기록을 코드
rollback 절차가 실수로 되돌리는 것은 「낡는 좌표」류보다 위험한 사고다. **판단**: 이 파일
전체를 scope.md 와 같은 범주(구현 코드가 아니라 계약/승인 기록)로 재분류해 rollback 대상에서
제외한다(계약과 어긋나 판단이 필요했던 자리 — 팀장 보고, 아래 「인계」 참고).

## 공유 파일 다섯 — 커밋 해시 hunk 격리

5D 는 base(`cefb19c`)에 **이미 병합돼 있다** — 아래 파일들에 5D 가 넣은 줄은 이 slice 의
어떤 커밋도 만지지 않았다(diff 실측, 「실측 결과」절). 그래서 hunk 격리는 "5C-1 커밋만
되돌리면 파일 전체가 `cefb19c`와 바이트 동일해진다"는 성질을 갖는다(5D 줄과 5C-1 줄이
서로 다른 영역이라 겹치지 않음).

| 파일 | 이 slice 의 커밋(적용 순서) | rebase 전 판의 대응 커밋(참고) |
| --- | --- | --- |
| `ml-engine/pyproject.toml` | `59113d2` | (구) `6647a66` |
| `ml-engine/tests/conftest.py` | `59113d2` | (구) `6647a66` |
| `ml-engine/tests/gates/test_import_contracts.py` | `4a9bbf6` → `59113d2`(적용 역순, 나중 커밋 먼저) | (구) `79d084e` → `6647a66` |
| `ml-engine/tests/features/conftest.py`(삭제) | `59113d2` | (구) `6647a66` |
| `ml-engine/uv.lock` | `59113d2` | 이전 판은 `git restore`로만 처리(같은 커밋이 유일 변경이라 결과 동일) |

**해시가 바뀐 이유**: 2026-09-13 rebase(5D 를 `main`에 먼저 병합한 뒤 5C-1 을 그 위로 얹음)가
전 커밋을 재작성했다 — 구조(어떤 파일을 어떤 순서로 고쳤는가)는 이전 판과 동일하고 해시만
바뀌었다(실측: 각 커밋의 diff 내용이 이전 판 서술과 문자 그대로 일치).

## rollback 절차(임시 worktree 실측, `cp -r` 금지)

```bash
git worktree add --detach <tmp> HEAD

cd <tmp>
rm -rf ml-engine/policy/training-v1.yaml \
  ml-engine/src/ml_engine/adapters/dataset_files.py \
  ml-engine/src/ml_engine/training/{artifact_writer,booster,corpus,dataset,encoding_oof,folds,policy,release,residual,spec,train}.py \
  ml-engine/tests/adapters ml-engine/tests/training ml-engine/tests/gates/fixtures/bad_features_db \
  reports/evidence/m5/5c/{checklist.md,commands.md,golden-manifest.json,reuse.md,rollback.md}

git restore --source=cefb19c --staged --worktree -- \
  ml-engine/src/ml_engine/adapters/__init__.py \
  ml-engine/src/ml_engine/training/__init__.py \
  ml-engine/uv.lock \
  ml-engine/tests/features/conftest.py

# 공유 파일 둘(pyproject.toml·tests/conftest.py) — 이 slice 유일 커밋(59113d2) hunk 되돌리기
git diff 59113d2~1..59113d2 -- ml-engine/pyproject.toml ml-engine/tests/conftest.py \
  | git apply -R

# test_import_contracts.py — 적용 역순으로 둘(4a9bbf6 먼저, 59113d2 나중)
git diff 4a9bbf6~1..4a9bbf6 -- ml-engine/tests/gates/test_import_contracts.py | git apply -R
git diff 59113d2~1..59113d2 -- ml-engine/tests/gates/test_import_contracts.py | git apply -R

# scope.md·policy-values.md 는 손대지 않는다(위 절 — 둘 다 rollback 대상 제외)
```

## 실측 결과(2026-09-13, `git worktree add --detach`, base `cefb19c`, HEAD `c8b5541`)

- `rm`·`restore`·hunk 되돌리기 전부 exit 0(`git apply -R -v`가 각 파일에 "패치 깔끔하게
  적용" 확인, conflict 0 — `--3way` 폴백 불필요).
- **in_scope diff**: `git diff --stat cefb19c -- ml-engine reports/evidence/m5/5c
  ":(exclude)reports/evidence/m5/5c/scope.md" ":(exclude)reports/evidence/m5/5c/policy-values.md"`
  → **빈 출력**(exit 0, 줄 0) — rollback 후 작업 트리가 5C-1 구현이 전혀 없던 상태(5D 만
  병합된 `cefb19c`)와 정확히 일치한다.
- **「내 줄 사라짐」grep 실측**: `pyyaml==6.0.3",`(training extras 줄, `dev` 그룹의 동명 줄과는
  문맥으로 구분 — `training = [...]` 블록 안 확인)·`features/training/evaluation/registry 는
  DB···`(신설 forbidden 계약 이름)·`hypothesis`(전체 문자열, `tests/conftest.py`)·
  `test_real_pyproject_has_features_forbidden_contract`(함수 이름) — 전부 매치 0.
- **「남의 줄(5D) 남음」grep 실측**: `pyproject.toml`의 `select = [..., "BLE"]`(89행)·
  `legacy_parity: legacy 산식과의...`마커 등록(59행)·`test_import_contracts.py`의
  `test_forbidden_contract_forbids_bidvector_with_indirect_imports_allowed`(64행, 5A/5D
  공유 기존 test) — 전부 매치 확인(그대로 남음).
- **되돌린 트리에서 acceptance 재실행**(`ml-engine/` 안, `uv sync --frozen --all-extras` 뒤):

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `uv run ruff check . && uv run ruff format --check .` | 0 | "All checks passed!" |
| `uv run mypy --strict src/ml_engine` | 0 | "Success: no issues found in 27 source files" |
| `uv run lint-imports` | 0 | "Contracts: 5 kept, 0 broken"(5C-1 신설 forbidden 계약 없음 — base 그대로) |
| `uv run python -m pytest tests -q` | 0 | **351 passed, 1 skipped** — 팀장이 인수 시 제시한 「5D 종결 시점 351 기대」와 정확히 일치 |
| `uv run python tools/design_ratchet.py --check` | 0 | "설계 래칫 위반 없음" |
| `uv run python tools/reuse_provenance_check.py` | 0 | "재활용 출처 두 자리 일치 — 위반 0" |
| S-9(python-version 대조) | 0 | 일치 |

worktree 는 검증 뒤 `git worktree remove --force <tmp>`로 제거했다(원본 저장소 무변경 확인 —
`git worktree list`에 재등장 없음).

## 인계(팀장) — policy-values.md 재분류 판단

이번 판은 `policy-values.md`를 `scope.md`와 같은 「계약/승인 기록, rollback 비대상」 범주로
재분류했다(이전 판은 「구현 레인 소유, hunk 격리 대상」이었다) — 사유는 위 절. **원한다면**
`d04cc3b`·`ea67c10` 두 커밋만의 hunk 를 `bf6cc8c`와 겹치지 않는 자리인지 확인한 뒤 좁게
되돌리는 대안도 가능하나, 이번 세션은 사용자 승인 기록 훼손 위험을 코드 rollback 편의보다
우선해 보수적으로 제외했다.

## `milestone-5.md`

이 slice 의 착수 문단·5C-1 종결 문단은 팀장 문서 레인 소관 커밋(`873352f`·`bf6cc8c`)이 이미
완료했다 — 구현 레인은 손대지 않았으므로(`git log cefb19c..HEAD -- milestone-5.md` 실측)
이 rollback 목록에서 계속 제외한다.
