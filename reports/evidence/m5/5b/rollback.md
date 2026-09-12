# M5/5B — rollback.md

정본. `git diff --name-status <base>..<코드 마지막 커밋>`으로 기계 산출한 목록(공유 파일
`milestone-5.md`·`reports/evidence/m5/prep/m5-prep.md`는 팀장 문서 레인 소유 — 이 목록에서
제외한다, scope.md rollback 절 선언).

base = `cc90f7060c3df2a002450420d1fc3de952d9b612`, 코드 마지막 커밋 = `abf5951`.

```
$ git diff --name-status cc90f7060c3df2a002450420d1fc3de952d9b612..abf5951 \
    -- ml-engine/src/ml_engine/features ml-engine/tests/features reports/evidence/m5/5b
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

## 임시 clone 실측(2026-09-12, `git clone --no-hardlinks` — `cp -r` 금지 규율)

1. `/tmp/5b-rollback-check`에 `git clone --no-hardlinks`(HEAD `abf5951`).
2. 위 되돌리기 명령 실행 → `git status --short`가 신설 8+8+1=17개 삭제 + `__init__.py` 수정만
   보임(예상과 일치).
3. `git diff --cached cc90f7060c3df2a002450420d1fc3de952d9b612 -- ml-engine/src/ml_engine/features
   ml-engine/tests/features reports/evidence/m5/5b/reuse.md | wc -l` → **0**(in_scope diff 없음).
4. `cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q` →
   **156 passed**(5A 원래 카운트, 5B 66개가 사라짐 — 회귀 없이 정확히 base 상태로 복귀).

## 알려진 제한

되돌리기는 `reuse.md`만 삭제한다 — `commands.md`·`checklist.md`·`rollback.md`(이 파일들
자신)는 목록에 없다(2026-09-10 규율 — rollback 목록은 자기 자신을 담은 커밋을 가리킬 수
없다, 이 evidence 문서들은 코드 롤백과 독립적으로 별도 커밋에 남는다).
