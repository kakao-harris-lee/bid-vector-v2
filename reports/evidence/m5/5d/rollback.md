# M5/5D — rollback.md

정본. 목록은 `git diff --name-status <base>..<코드 마지막 커밋> -- <in_scope 경로>`로
기계 산출(경로를 개별 인자로 — pathspec 하나로 넓히면 팀장 문서 레인(ADR·milestone-5·
prep·5a/5d policy-values·scope.md, 같은 range 안에 섞여 있음)까지 걸린다). 공유 파일
`milestone-5.md`·`docs/adr/0001-*.md`·`reports/evidence/m5/prep/m5-prep.md`·
`reports/evidence/m5/5a/policy-values.md`·`reports/evidence/m5/5d/{scope.md,policy-values.md}`
는 팀장 문서 레인 소유 — 이 목록에서 제외한다. `commands.md`·`checklist.md`·
`rollback.md`(이 파일 자신)는 자기 자신을 담은 커밋을 가리킬 수 없다는 규율(2026-09-10)
— 목록에서 제외. `reuse.md`만 코드 레인 소유 evidence 라 대상에 포함(단일 파일 경로).

base = `f5020aa982e6c5ade01f1660c0568dcd75c2fa7e`, 코드 마지막 커밋 =
`145847ca8ccc24f43264ec797dccf192b591e40b`(verifier r1 수정 라운드 반영 — 착수 커밋
`2a1bae7`와 파일 **집합**은 동일하다, 이번 라운드는 기존 파일 내용만 바꿨다).

```
$ git diff --name-status f5020aa982e6c5ade01f1660c0568dcd75c2fa7e..145847ca8ccc24f43264ec797dccf192b591e40b \
    -- ml-engine/src/ml_engine/inference ml-engine/src/ml_engine/registry/artifact.py \
       ml-engine/policy ml-engine/tests/inference ml-engine/tests/registry ml-engine/pyproject.toml
A	ml-engine/policy/inference-v1.yaml
M	ml-engine/pyproject.toml
M	ml-engine/src/ml_engine/inference/__init__.py
A	ml-engine/src/ml_engine/inference/assessment.py
A	ml-engine/src/ml_engine/inference/maturity.py
A	ml-engine/src/ml_engine/inference/policy.py
A	ml-engine/src/ml_engine/inference/predict.py
A	ml-engine/src/ml_engine/inference/reserve_draw.py
A	ml-engine/src/ml_engine/inference/results.py
A	ml-engine/src/ml_engine/inference/scenario.py
A	ml-engine/src/ml_engine/registry/artifact.py
A	ml-engine/tests/inference/conftest.py
A	ml-engine/tests/inference/golden/test_kernel_golden.py
A	ml-engine/tests/inference/test_assessment.py
A	ml-engine/tests/inference/test_maturity.py
A	ml-engine/tests/inference/test_policy.py
A	ml-engine/tests/inference/test_predict.py
A	ml-engine/tests/inference/test_reserve_draw.py
A	ml-engine/tests/inference/test_results.py
A	ml-engine/tests/inference/test_scenario.py
A	ml-engine/tests/registry/test_artifact.py
```

## 되돌리기 명령(신설 경로 삭제 + 편집 파일 복원, 경로 개별 인자)

`ml-engine/pyproject.toml`은 5A 소유 공유 파일이라 hunk 격리(`git apply -R`) — 전체
복원이 아니다. 이번 라운드가 그 파일에 더한 hunk(`warn_unreachable = true`)도 base부터
head까지의 diff 안에 포함되므로 같은 명령 한 번으로 두 라운드 편집이 함께 걷힌다.
`ml-engine/src/ml_engine/inference/__init__.py`는 5D 착수 전 원본이 docstring 뿐이었으므로
(base 존재) `git restore`로 전체 복원해도 안전하다.

```bash
rm -f ml-engine/policy/inference-v1.yaml \
      ml-engine/src/ml_engine/inference/assessment.py \
      ml-engine/src/ml_engine/inference/maturity.py \
      ml-engine/src/ml_engine/inference/policy.py \
      ml-engine/src/ml_engine/inference/predict.py \
      ml-engine/src/ml_engine/inference/reserve_draw.py \
      ml-engine/src/ml_engine/inference/results.py \
      ml-engine/src/ml_engine/inference/scenario.py \
      ml-engine/src/ml_engine/registry/artifact.py \
      reports/evidence/m5/5d/reuse.md
rmdir ml-engine/policy 2>/dev/null || true
rm -rf ml-engine/tests/inference ml-engine/tests/registry
git restore --source=f5020aa982e6c5ade01f1660c0568dcd75c2fa7e --staged --worktree \
    -- ml-engine/src/ml_engine/inference/__init__.py
git diff f5020aa982e6c5ade01f1660c0568dcd75c2fa7e -- ml-engine/pyproject.toml \
    | git apply -R
```

## 임시 clone 실측(2026-09-12, verifier r1 수정 뒤 재실측 — `git clone --no-hardlinks`)

1. `/tmp/5d-rollback-check2`에 `git clone --no-hardlinks`(현재 브랜치 HEAD, 코드 마지막
   커밋 `145847c` 포함, evidence 커밋 `bf59998`까지).
2. 위 되돌리기 명령 실행 → `git status --short`가 신설 10개 삭제(정책 YAML 1 +
   inference 8 + registry/artifact.py 1) + `tests/inference`·`tests/registry` 디렉터리
   삭제(11 파일) + `reuse.md` 삭제 + `__init__.py`·`pyproject.toml` 수정만 보임(2회
   라운드 편집이 한 번에 걷힘).
3. `git diff f5020aa982e6c5ade01f1660c0568dcd75c2fa7e -- ml-engine/src/ml_engine/inference
   ml-engine/src/ml_engine/registry/artifact.py ml-engine/policy ml-engine/tests/inference
   ml-engine/tests/registry ml-engine/pyproject.toml reports/evidence/m5/5d/reuse.md
   | wc -l`(작업트리 diff, `--cached` 없음) → **0**(in_scope 전체 원복 확인, 2 라운드
   누적 편집 포함).
4. `cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q`
   → **233 passed**(5A 156 + 5B 77, 5D 전부가 사라져 base 상태로 정확히 복귀 — 회귀 0).
5. `uv run mypy --strict src/ml_engine` → `Success: no issues found in 18 source files`
   (5D 이전 5A+5B 파일 수와 일치 — `warn_unreachable=true`도 함께 걷혔으므로 5A/5B 만의
   strict 결과가 재현된다).
6. `uv run lint-imports` → `Contracts: 5 kept, 0 broken`(되돌린 트리가 5A+5B 게이트
   그대로 선다).

## 알려진 제한

되돌리기는 `reuse.md`만 삭제한다(단일 파일 경로 한정 — 디렉터리 전체를 지우면 팀장
문서 레인 소유 `scope.md`·`policy-values.md`까지 삭제 대상에 들어간다). `commands.md`·
`checklist.md`·`rollback.md`(이 파일 자신)는 목록에 없다(2026-09-10 규율 — rollback
목록은 자기 자신을 담은 커밋을 가리킬 수 없다). `pyproject.toml`의 되돌리기는
`git apply -R`(hunk 단위)라 이 slice 가 만든 hunk 가 base 시점과 동일할 때만 깨끗하게
적용된다 — 5A 소유 파일에 다른 동시 편집이 있었다면 수동 3-way 해소가 필요할 수 있다
(M4 실측과 같은 갈래, 여기서는 발생하지 않았다: 임시 clone 재현이 exit 0 확인, 2라운드
누적 hunk 도 한 번에 걷힘을 이번 재실측이 추가로 확인했다).
