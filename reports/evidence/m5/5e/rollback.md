# M5/5E-1 — rollback.md

base = `d78e162`(PR #15 머지 = origin/main). 이 slice 의 커밋 전부(`003c695`~`0780f86`)는
**단일 레인**(이 구현자)이 만들었다 — base 이후 이 브랜치를 만진 다른 레인이 없어 hunk
격리 없이 파일 단위로 되돌릴 수 있다(M3/M4 의 다중 레인 공유 파일 hunk 분리와 다른
사정). 유일한 예외는 `reports/evidence/m5/5e/policy-values.md`·`milestone-5.md`·
`reports/evidence/m5/5e/scope.md` — 이 셋은 **하네스 레인**(팀장, 착수 계약 커밋
`41076d0`)이 만들었고, `policy-values.md`만 이 구현자가 change_history 한 줄을 더했다
(그 한 줄만 역적용, 파일 자체는 하네스 소유이므로 되돌리지 않는다).

## 기계 산출 diff(`git diff --name-status d78e162..HEAD`, in_scope 경로만)

```
A  ml-engine/policy/serving-v1.yaml
A  ml-engine/setup.py
M  ml-engine/src/ml_engine/adapters/__init__.py
A  ml-engine/src/ml_engine/adapters/artifact_files.py
M  ml-engine/src/ml_engine/adapters/dataset_files.py
A  ml-engine/src/ml_engine/app/__init__.py
A  ml-engine/src/ml_engine/app/pipeline.py
A  ml-engine/src/ml_engine/app/server.py
M  ml-engine/src/ml_engine/serving/__init__.py
A  ml-engine/src/ml_engine/serving/embedding.py
A  ml-engine/src/ml_engine/serving/grpc.py
A  ml-engine/src/ml_engine/serving/policy.py
A  ml-engine/src/ml_engine/serving/prediction.py
A  ml-engine/src/ml_engine/serving/readiness.py
A  ml-engine/src/ml_engine/serving/status.py
M  ml-engine/src/ml_engine/training/dataset.py
A  ml-engine/src/ml_engine/training/jobs/__init__.py
A  ml-engine/src/ml_engine/training/jobs/pipeline.py
A  ml-engine/src/ml_engine/training/jobs/runner.py
A  ml-engine/src/ml_engine/training/jobs/servicer.py
A  ml-engine/src/ml_engine/training/jobs/state.py
A  ml-engine/src/ml_engine/training/jobs/store.py
A  ml-engine/tests/app/test_pipeline.py
A  ml-engine/tests/app/test_server.py
M  ml-engine/tests/gates/test_serving_purity.py
A  ml-engine/tests/gates/test_wheel_reexport.py
A  ml-engine/tests/serving/test_embedding.py
A  ml-engine/tests/serving/test_grpc.py
A  ml-engine/tests/serving/test_policy_kotlin_parity.py
A  ml-engine/tests/serving/test_prediction.py
A  ml-engine/tests/serving/test_readiness.py
A  ml-engine/tests/serving/test_serving_policy.py
A  ml-engine/tests/training/test_dataset_settlements.py
A  ml-engine/tests/training/test_jobs_pipeline.py
A  ml-engine/tests/training/test_jobs_runner.py
A  ml-engine/tests/training/test_jobs_servicer.py
A  ml-engine/tests/training/test_jobs_state.py
A  ml-engine/tests/training/test_jobs_store.py
A  ml-engine/tools/build_hook.py
M  ml-engine/uv.lock
M  ml-engine/pyproject.toml
M  .github/workflows/ci.yml
M  config/quality/leak-pattern-baseline.txt
A  reports/evidence/m5/5e/checklist.md
A  reports/evidence/m5/5e/commands.md
A  reports/evidence/m5/5e/reuse.md
M  reports/evidence/m5/5e/policy-values.md   # 하네스 소유 파일 — 내 hunk 만 역적용
```

**하네스 레인 변경**(`41076d0` — 팀장, 이 slice 착수 계약): `milestone-5.md`·
`reports/evidence/m5/5e/scope.md`·`reports/evidence/m5/5e/policy-values.md`(최초 등재).
같은 range 안에 있으나 **이 구현자의 rollback 대상이 아니다**(2026-09-04 규율).

## 롤백 명령(실측 완료 — 아래 「임시 worktree 실측」 참고)

```bash
NEW_FILES=(
  ml-engine/policy/serving-v1.yaml ml-engine/setup.py
  ml-engine/src/ml_engine/adapters/artifact_files.py
  ml-engine/src/ml_engine/app/__init__.py ml-engine/src/ml_engine/app/pipeline.py
  ml-engine/src/ml_engine/app/server.py
  ml-engine/src/ml_engine/serving/embedding.py ml-engine/src/ml_engine/serving/grpc.py
  ml-engine/src/ml_engine/serving/policy.py ml-engine/src/ml_engine/serving/prediction.py
  ml-engine/src/ml_engine/serving/readiness.py ml-engine/src/ml_engine/serving/status.py
  ml-engine/src/ml_engine/training/jobs/__init__.py
  ml-engine/src/ml_engine/training/jobs/pipeline.py
  ml-engine/src/ml_engine/training/jobs/runner.py
  ml-engine/src/ml_engine/training/jobs/servicer.py
  ml-engine/src/ml_engine/training/jobs/state.py
  ml-engine/src/ml_engine/training/jobs/store.py
  ml-engine/tests/app/test_pipeline.py ml-engine/tests/app/test_server.py
  ml-engine/tests/gates/test_wheel_reexport.py
  ml-engine/tests/serving/test_embedding.py ml-engine/tests/serving/test_grpc.py
  ml-engine/tests/serving/test_policy_kotlin_parity.py
  ml-engine/tests/serving/test_prediction.py ml-engine/tests/serving/test_readiness.py
  ml-engine/tests/serving/test_serving_policy.py
  ml-engine/tests/training/test_dataset_settlements.py
  ml-engine/tests/training/test_jobs_pipeline.py
  ml-engine/tests/training/test_jobs_runner.py
  ml-engine/tests/training/test_jobs_servicer.py
  ml-engine/tests/training/test_jobs_state.py
  ml-engine/tests/training/test_jobs_store.py
  ml-engine/tools/build_hook.py
  reports/evidence/m5/5e/checklist.md reports/evidence/m5/5e/commands.md
  reports/evidence/m5/5e/reuse.md
)
RESTORE_FILES=(
  ml-engine/pyproject.toml ml-engine/uv.lock
  ml-engine/src/ml_engine/adapters/__init__.py
  ml-engine/src/ml_engine/adapters/dataset_files.py
  ml-engine/src/ml_engine/serving/__init__.py
  ml-engine/src/ml_engine/training/dataset.py
  ml-engine/tests/gates/test_serving_purity.py
  .github/workflows/ci.yml config/quality/leak-pattern-baseline.txt
)

git rm -q -- "${NEW_FILES[@]}"
git restore --source=d78e162 --staged --worktree -- "${RESTORE_FILES[@]}"
# 하네스 소유 파일(policy-values.md)은 이 구현자의 evidence 커밋(0780f86) 한 hunk만 역적용
git diff 0780f86~1..0780f86 -- reports/evidence/m5/5e/policy-values.md | git apply -R
rmdir ml-engine/src/ml_engine/app ml-engine/src/ml_engine/training/jobs 2>/dev/null || true
```

`git checkout <base> --`가 아니라 `git restore --source=<base> --staged --worktree --`를
쓴다(2026-09-04 규율 — 신규 경로에서 `checkout`은 pathspec 오류로 exit 1). 신규 경로는
`git rm`으로(복원할 base 내용이 없다).

## 임시 worktree 실측(2026-09-15, 완료)

```bash
git worktree add --detach <tmp>/rollback-check HEAD   # HEAD=0780f86
cd <tmp>/rollback-check
# (위 롤백 명령 실행)
```

- `git status --short` — 위 `NEW_FILES` 전부 `D`, `RESTORE_FILES` 전부 `M`, `policy-values.md`
  만 `M`(내 한 줄) — 정확히 예상한 파일 집합.
- `git diff d78e162 -- "${RESTORE_FILES[@]}"` → **빈 출력**(RESTORE_FILES 는 base 와 완전히
  같아졌다).
- `git status --short milestone-5.md reports/evidence/m5/5e/scope.md` → **빈 출력**(하네스
  파일은 손대지 않았다 — `git diff d78e162 -- <이 둘>`이 여전히 내용을 보이는 것은 하네스
  커밋 `41076d0`이 그대로 남아 있기 때문이고, 이것이 올바른 상태다).
- `(cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q)` →
  **658 passed** — base(`d78e162`) 임시 worktree 직접 계수(commands.md)와 **정확히 일치**.
- `(cd ml-engine && uv run ruff check . && uv run mypy --strict src/ml_engine && uv run lint-imports)`
  → 전부 초록, `mypy`: **54 source files**(base 와 일치), `lint-imports`: **Contracts: 6
  kept**(5E-1 이 더한 `app 은 DB·HTTP·업무 모듈을 모른다` 계약과 ignore_imports 둘이
  사라져 base 의 6개 계약으로 정확히 돌아갔다).
- `./gradlew --no-daemon check` → **BUILD SUCCESSFUL**(346 tasks, 211 executed/135 cached).
- 임시 worktree 는 `git worktree remove --force`로 정리했다.

## 알려진 제한

- 이 rollback 은 `m5-5e/2026-09-16` 브랜치 위에서 **파일 상태만** base 로 되돌린다 — 커밋
  이력은 되쓰지 않는다(evidence-pack 규율, 사실은 커밋 로그에 남긴다).
- `config/quality/leak-pattern-baseline.txt`는 이 slice 가 등재한 오탐 키 1건만 제거된다
  (다른 slice 의 기존 291줄은 무손상 — `git restore --source=d78e162`가 파일 전체를
  base 상태로 정확히 되돌리므로, base 이후 **이 브랜치에서** 그 파일을 만진 다른 커밋이
  없다는 전제가 성립할 때만 안전하다. 이 브랜치는 단일 레인이라 성립을 확인했다).
