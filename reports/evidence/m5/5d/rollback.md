# M5/5D — rollback.md

정본. 목록은 `git diff --name-status <base>..<코드 마지막 커밋> -- <in_scope 경로>`로
기계 산출(경로를 개별 인자로 — pathspec 하나로 넓히면 팀장 문서 레인(ADR·milestone-5·
prep·5a/5d policy-values·scope.md, 같은 range 안에 섞여 있음)까지 걸린다). 공유 파일
`milestone-5.md`·`docs/adr/0001-*.md`·`reports/evidence/m5/prep/m5-prep.md`·
`reports/evidence/m5/5a/policy-values.md`·`reports/evidence/m5/5d/{scope.md,policy-values.md}`
는 팀장 문서 레인 소유 — 이 목록에서 제외한다. **`fixtures/**`·`reports/evidence/m5/
5d-golden/**`는 curator golden 레인 소유**(별도 worktree·브랜치, 이미 사용자 승인·병합
완료 — 병합 커밋 `5ef6eb8`) — 이 slice 의 rollback 대상이 아니고, 아래 되돌리기 명령은
그 병합을 되돌리지 않는다. `commands.md`·`checklist.md`·`rollback.md`(이 파일 자신)는
자기 자신을 담은 커밋을 가리킬 수 없다는 규율(2026-09-10) — 목록에서 제외. `reuse.md`만
코드 레인 소유 evidence 라 대상에 포함(단일 파일 경로).

base = `f5020aa982e6c5ade01f1660c0568dcd75c2fa7e`, 코드 마지막 커밋 =
`3a40bc3e1889f33ef6981d3dd3e2fad5a734c2a1`(golden 통합 M-3 반영 — **파일 집합이 이전
라운드(`4ad2de7`)와 다시 달라졌다**: `ml-engine/tests/inference/golden/_adapter.py` 신규.
아래 목록·되돌리기 명령·임시 clone 재실측을 모두 이 파일 추가를 반영해 다시 산출했다).

```
$ git diff --name-status f5020aa982e6c5ade01f1660c0568dcd75c2fa7e..3a40bc3e1889f33ef6981d3dd3e2fad5a734c2a1 \
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
A	ml-engine/src/ml_engine/inference/rounding.py
A	ml-engine/src/ml_engine/inference/scenario.py
A	ml-engine/src/ml_engine/registry/artifact.py
A	ml-engine/tests/inference/conftest.py
A	ml-engine/tests/inference/golden/_adapter.py
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

`ml-engine/tests/inference/golden/test_kernel_golden.py`는 base 시점에 없었다(착수
커밋이 skip 전용 골격을 신설했다) — `A`가 맞다. `golden/` 디렉터리 전체를 `rm -rf`로
되돌린다(`_adapter.py`도 함께 걷힌다).

## 되돌리기 명령(신설 경로 삭제 + 편집 파일 복원, 경로 개별 인자)

`ml-engine/pyproject.toml`은 5A 소유 공유 파일이라 hunk 격리(`git apply -R`) — 전체
복원이 아니다. 세 라운드(착수·r1·r2)가 그 파일에 더한 hunk 전부가 base부터 head까지의
diff 안에 포함되므로 같은 명령 한 번으로 걷힌다(M-3 은 `pyproject.toml`을 편집하지
않았다). `ml-engine/src/ml_engine/inference/__init__.py`는 5D 착수 전 원본이 docstring
뿐이었으므로(base 존재) `git restore`로 전체 복원해도 안전하다.

```bash
rm -f ml-engine/policy/inference-v1.yaml \
      ml-engine/src/ml_engine/inference/assessment.py \
      ml-engine/src/ml_engine/inference/maturity.py \
      ml-engine/src/ml_engine/inference/policy.py \
      ml-engine/src/ml_engine/inference/predict.py \
      ml-engine/src/ml_engine/inference/reserve_draw.py \
      ml-engine/src/ml_engine/inference/results.py \
      ml-engine/src/ml_engine/inference/rounding.py \
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

## 임시 clone 실측(2026-09-12, golden 통합 M-3 뒤 재실측 — `git clone --no-hardlinks`)

1. `/tmp/5d-rollback-check4`에 `git clone --no-hardlinks`(현재 브랜치 HEAD, 코드 마지막
   커밋 `3a40bc3` 포함 — golden 병합 커밋 `5ef6eb8`도 이 브랜치 이력에 있지만, 되돌리기
   명령이 `fixtures/**`·`5d-golden` evidence 를 건드리지 않으므로 그 병합 내용은 그대로
   남는다).
2. 위 되돌리기 명령 실행 → `git status --short`가 신설 11개 삭제(정책 YAML 1 + inference
   9(`rounding.py` 포함) + registry/artifact.py 1) + `tests/inference`(`golden/` 전체 —
   `_adapter.py`·`test_kernel_golden.py` 포함)·`tests/registry` 디렉터리 삭제 +
   `reuse.md` 삭제 + `__init__.py`·`pyproject.toml` 수정만 보임(세 라운드 편집이 한
   번에 걷힘). `fixtures/ml-kernel-*`·`reports/evidence/m5/5d-golden/**`는 목록 밖이라
   `git status`에 나타나지 않는다(그대로 남음 확인).
3. `git diff f5020aa982e6c5ade01f1660c0568dcd75c2fa7e -- ml-engine/src/ml_engine/inference
   ml-engine/src/ml_engine/registry/artifact.py ml-engine/policy ml-engine/tests/inference
   ml-engine/tests/registry ml-engine/pyproject.toml reports/evidence/m5/5d/reuse.md
   | wc -l`(작업트리 diff, `--cached` 없음) → **0**(in_scope 전체 원복 확인).
4. `cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q`
   → **233 passed**(5A 156 + 5B 77, 5D 전부가 사라져 base 상태로 정확히 복귀 — 회귀 0).
5. `uv run mypy --strict src/ml_engine` → `Success: no issues found in 18 source files`.
6. `uv run lint-imports` → `Contracts: 5 kept, 0 broken`(되돌린 트리가 5A+5B 게이트
   그대로 선다).

## 알려진 제한

되돌리기는 `reuse.md`만 삭제한다(단일 파일 경로 한정 — 디렉터리 전체를 지우면 팀장
문서 레인 소유 `scope.md`·`policy-values.md`까지 삭제 대상에 들어간다). `commands.md`·
`checklist.md`·`rollback.md`(이 파일 자신)는 목록에 없다(2026-09-10 규율 — rollback
목록은 자기 자신을 담은 커밋을 가리킬 수 없다). **`fixtures/**`·`5d-golden` evidence 는
이 slice 소유가 아니므로 이 rollback 이 되돌릴 수도, 되돌려서도 안 된다** — golden
corpus 자체를 되돌리려면 curator 레인의 별도 rollback(`reports/evidence/m5/5d-golden/
rollback.md`)이 필요하다(그 레인의 in_scope·소유). `pyproject.toml`의 되돌리기는
`git apply -R`(hunk 단위)라 이 slice 가 만든 hunk 가 base 시점과 동일할 때만 깨끗하게
적용된다 — 임시 clone 재현이 exit 0 확인. 파일 집합이 라운드마다 달라질 수 있다는 것
자체가 이 문서를 매 라운드 「SHA 만 갱신」으로 끝낼 수 없는 이유다 — `_adapter.py`
신설이 이번 라운드의 실례다.
