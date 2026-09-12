# M5/5B — rollback.md

정본. `git diff --name-status <base>..<코드 마지막 커밋>`으로 기계 산출한 목록(공유 파일
`milestone-5.md`·`reports/evidence/m5/prep/m5-prep.md`·`scope.md`·`policy-values.md`는
팀장 문서 레인 소유, `commands.md`·`checklist.md`·`rollback.md`(이 파일 자신)는 자기
자신을 담은 커밋을 가리킬 수 없다는 규율 — 이 목록에서 전부 제외한다. `reuse.md`만 코드
레인 소유 evidence 라 대상에 포함, 경로는 디렉터리가 아니라 **단일 파일**로 명시한다 —
디렉터리로 넓히면 위 제외 대상(scope.md 등)까지 걸려 들어온다).

base = `cc90f7060c3df2a002450420d1fc3de952d9b612`, 코드 마지막 커밋 = `2685fe5`(verifier r1
M-1/M-2/L-1 수정 포함 — 파일 집합은 최초 코드 커밋 `abf5951` 때와 동일, 기존 파일 수정만
추가됐다).

```
$ git diff --name-status cc90f7060c3df2a002450420d1fc3de952d9b612..2685fe5 \
    -- ml-engine/src/ml_engine/features ml-engine/tests/features reports/evidence/m5/5b/reuse.md
M       ml-engine/src/ml_engine/features/__init__.py
A       ml-engine/src/ml_engine/features/encoding.py
A       ml-engine/src/ml_engine/features/facts.py
A       ml-engine/src/ml_engine/features/manifest.py
A       ml-engine/src/ml_engine/features/normalize.py
A       ml-engine/src/ml_engine/features/rows.py
A       ml-engine/src/ml_engine/features/schema.py
A       ml-engine/src/ml_engine/features/shrinkage.py
A       ml-engine/src/ml_engine/features/vocabulary.py
A       ml-engine/tests/features/test_encoding.py
A       ml-engine/tests/features/test_facts.py
A       ml-engine/tests/features/test_manifest.py
A       ml-engine/tests/features/test_require_declared.py
A       ml-engine/tests/features/test_rows.py
A       ml-engine/tests/features/test_schema.py
A       ml-engine/tests/features/test_shrinkage.py
A       ml-engine/tests/features/test_vocabulary.py
A       reports/evidence/m5/5b/reuse.md
```

## 되돌리기 명령(신설 경로 삭제 + 편집 파일 복원, 경로 개별 인자)

```bash
rm -f ml-engine/src/ml_engine/features/encoding.py \
      ml-engine/src/ml_engine/features/facts.py \
      ml-engine/src/ml_engine/features/manifest.py \
      ml-engine/src/ml_engine/features/normalize.py \
      ml-engine/src/ml_engine/features/rows.py \
      ml-engine/src/ml_engine/features/schema.py \
      ml-engine/src/ml_engine/features/shrinkage.py \
      ml-engine/src/ml_engine/features/vocabulary.py
rm -rf ml-engine/tests/features
rm -f reports/evidence/m5/5b/reuse.md
git restore --source=cc90f7060c3df2a002450420d1fc3de952d9b612 --staged --worktree \
    -- ml-engine/src/ml_engine/features/__init__.py
```

`pyproject.toml`은 이 slice에서 편집하지 않았다(S-9 대조 외 변경 없음) — 공유 파일 hunk
격리 대상 아님.

## 임시 clone 실측(2026-09-12 재실측 — verifier r1 M-3 수정 뒤, `git clone --no-hardlinks`)

**verifier r1 M-3**: 이전 판의 확인 명령이 `git diff --cached <base> -- <경로>`였다 —
`rm`은 삭제를 스테이징하지 않으므로(`git add`를 부르지 않는 한) 그 명령은 항상 **작업
트리 반영 전 상태**를 비교해 in_scope 파일이 그대로 남아 있는 것처럼 보인다(실측:
1914줄, 0 아님). 옳은 확인은 `--cached` 없는 **작업트리 diff**다.

1. `/tmp/5b-rollback-check2`에 `git clone --no-hardlinks`(HEAD `2685fe5`).
2. 위 되돌리기 명령 실행 → `git status --short`가 신설 8+8+1=17개 삭제(전부 unstaged,
   좌측 칸 공백·` D`) + `__init__.py` 수정만 보임(예상과 일치).
3. **두 명령 대조**(M-3 재현) —
   `git diff --cached cc90f7060c3df2a002450420d1fc3de952d9b612 -- ml-engine/src/ml_engine/features
   ml-engine/tests/features reports/evidence/m5/5b/reuse.md | wc -l` → **1914**(틀린 확인,
   스테이징 안 된 삭제를 못 본다) vs. `git diff cc90f7060c3df2a002450420d1fc3de952d9b612
   -- ml-engine/src/ml_engine/features ml-engine/tests/features
   reports/evidence/m5/5b/reuse.md | wc -l`(`--cached` 없음) → **0**(in_scope 작업트리
   diff 없음, 옳은 확인).
4. `cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q` →
   **156 passed**(5A 원래 카운트, 5B 전부가 사라짐 — 회귀 없이 정확히 base 상태로 복귀).
5. `uv run mypy --strict src/ml_engine` → `Success: no issues found in 10 source files`.
6. `uv run lint-imports` → `Contracts: 5 kept, 0 broken`(되돌린 트리가 5A 게이트 그대로 선다).

## 알려진 제한

되돌리기는 `reuse.md`만 삭제한다(단일 파일 경로로 한정 — 디렉터리 전체를 지우면
`scope.md`·`policy-values.md`(팀장 문서 레인 소유)까지 삭제 대상에 들어간다) —
`commands.md`·`checklist.md`·`rollback.md`(이 파일들 자신)는 목록에 없다(2026-09-10
규율 — rollback 목록은 자기 자신을 담은 커밋을 가리킬 수 없다, 이 evidence 문서들은
코드 롤백과 독립적으로 별도 커밋에 남는다).
