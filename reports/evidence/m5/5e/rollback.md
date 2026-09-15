# M5/5E-1 — rollback.md

base = `d78e162`(PR #15 머지 = origin/main). 이 slice 의 코드·evidence 커밋 전부는
**단일 레인**(이 구현자)이 만들었다(fix round 1 포함) — base 이후 이 브랜치를 만진
다른 레인이 없어 hunk 격리 없이 파일 단위로 되돌릴 수 있다(M3/M4 의 다중 레인 공유
파일 hunk 분리와 다른 사정). 예외 셋은 `reports/evidence/m5/5e/policy-values.md`·
`milestone-5.md`·`reports/evidence/m5/5e/scope.md` — **하네스 레인**(팀장, 착수 계약
커밋 `41076d0` + 뒤이은 계약 갱신 (2)(3)(4): `d7f8b1a`·`661dcfd`·`b8ea0e2`, 판별은
author 가 아니라 커밋 메시지 내용 — 넷 다 이 구현자와 같은 git 신원으로 커밋됐다)이
만들었다.

- `milestone-5.md`는 착수 이후 이 range 에서 전혀 다시 손대지 않았다 — 되돌릴 게 없다.
- `policy-values.md`는 이 구현자가 change_history 한 줄을 더했다 — **그 한 줄만**
  역적용하고 파일 자체(하네스 소유)는 그 밖을 되돌리지 않는다.
- `scope.md`는 verifier r1 **M-10**으로 처분이 바뀌었다 — 「하네스 소유라 안
  건드린다」가 아니라 **자기 이력 전체를 착수 커밋 `41076d0` 기준 단일 역적용**한다
  (하네스 규칙 2026-09-16 — 같은 slice 안에서 여러 커밋이 이어 만진 파일은 커밋별
  hunk 역적용 대신 착수 시점 기준 한 번에). scope.md 의 **내용** 정오 자체는 팀장
  소관(L-1)이고, 이 문서는 scope.md 가 스스로 선언한 처분(계약 갱신 이력 (4) 행)을
  그대로 따른다.

fix round 1(2026-09-16, H-1~M-8·L 일괄)은 이 셋 중 어느 것도 만지지 않았다 — 이
구현자가 낸 새 하네스 레인 변경 없음.

## 기계 산출 diff(`git diff --name-status d78e162..HEAD`, in_scope 경로만 — 아래에서
하네스 소유 `milestone-5.md`·`scope.md`는 제외했다, `policy-values.md`는 하네스가
만들었지만 이 구현자가 한 줄을 더해 포함한다)

```
M  .github/workflows/ci.yml
M  config/quality/leak-pattern-baseline.txt
A  ml-engine/policy/serving-v1.yaml
M  ml-engine/pyproject.toml
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
A  ml-engine/tests/adapters/test_artifact_files.py
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
A  reports/evidence/m5/5e/checklist.md
A  reports/evidence/m5/5e/commands.md
A  reports/evidence/m5/5e/golden-manifest.json
A  reports/evidence/m5/5e/policy-values.md   # 하네스 소유 파일 — 내 hunk 만 역적용
A  reports/evidence/m5/5e/reuse.md
A  reports/evidence/m5/5e/rollback.md
```

**직전 판(`77476c7`) 대비 정정**: `policy-values.md`를 그때는 `M`으로 적었으나, 실제
`git diff --name-status d78e162..HEAD` 산출은 `A`다(base 에 그 파일 자체가 없었다 —
`41076d0`이 새로 만들었다). 소유·역적용 처분은 그대로다(하네스가 만든 파일, 이
구현자는 한 줄만 더했다) — 상태 문자만 기계 산출과 어긋났던 것을 바로잡았다.

**fix round 1(2026-09-16)이 이전 판(`77476c7`) 대비 새로 추가한 경로**:
`ml-engine/tests/adapters/test_artifact_files.py`(M-6 신설 test 파일)·
`ml-engine/src/ml_engine/app/server.py`는 이미 있었으나 내용이 바뀜(M-3·L-3)·
`reports/evidence/m5/5e/golden-manifest.json`은 이전 판에도 있었다(N/A 선언, 착오
정정 아님, 목록에는 이번에 처음 반영). 그 밖의 실질 코드 변경(H-1~M-8)은 전부 기존
파일의 내용 수정이라 파일 목록 자체는 늘지 않았다.

**하네스 레인 커밋**(팀장 — `41076d0`(착수)·`d7f8b1a`·`661dcfd`·`b8ea0e2`(계약 갱신
(2)(3)(4), 전부 `scope.md`를 만졌다)): `milestone-5.md`·`reports/evidence/m5/5e/scope.md`·
`reports/evidence/m5/5e/policy-values.md`(최초 등재). 같은 range 안에 있으나 **산출물이
아니다**(2026-09-04 규율). `milestone-5.md`는 rollback 대상 아님(무편집). `policy-values.md`
는 위 예외대로 내 한 줄만. `scope.md`는 「대상 아님」이 아니라 **자기 이력 단일
역적용**(M-10, 아래 롤백 명령 참고) — scope.md 내용 자체의 옳고 그름은 여전히 팀장
소관(L-1)이고 이 문서는 그 처분 방식만 따른다.

## 롤백 명령(실측 완료 — 아래 「임시 clone 실측」 참고)

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
  ml-engine/tests/adapters/test_artifact_files.py
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
  reports/evidence/m5/5e/golden-manifest.json reports/evidence/m5/5e/reuse.md
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
# scope.md — 자기 이력 전체를 착수 커밋(41076d0) 기준 단일 역적용(M-10, 하네스 규칙
# 2026-09-16 — 같은 파일을 이어 만진 커밋별 hunk 역적용 대신 착수 시점 기준 한 번에)
git diff 41076d0 -- reports/evidence/m5/5e/scope.md | git apply -R
# 위 역적용이 scope.md 의 「하네스 레인 변경」 절(착수 시점엔 없었다 — 계약 갱신 (2)에서
# 신설)도 함께 지운다. evidence-pack 규격상 그 절은 항상 있어야 하므로, 되돌린 파일에
# HEAD 시점 원문 그대로 재등재한다(단일 역적용이 잃는 유일한 것 — M-10).
cat >> reports/evidence/m5/5e/scope.md <<'SCOPE_SECTION_EOF'

---

## 하네스 레인 변경(리뷰 요청 시점마다 갱신 — `git log --oneline d78e162..HEAD -- CLAUDE.md .claude/`)

없음(2026-09-16 verifier r1 요청 시점). 등재되는 커밋은 slice 산출물이 아니며 in_scope 밖, 운영자 승인 하에 같은 range 에 있다.
SCOPE_SECTION_EOF
rmdir ml-engine/src/ml_engine/app ml-engine/src/ml_engine/training/jobs 2>/dev/null || true
```

`rollback.md` 자신은 `NEW_FILES`에 넣지 않았다 — base 이후 이 문서가 존재하지 않는
것은 사실이지만(그래서 위 기계 산출 diff 에는 `A`로 잡힌다), 절차서 자신을 절차
실행 중에 지우면 감사 기록이 남지 않는다. 이전 판(`77476c7`)도 같은 이유로 스스로를
빼고 썼다 — 이번에 그 관례를 명시로 못 박았다.

`git checkout <base> --`가 아니라 `git restore --source=<base> --staged --worktree --`를
쓴다(2026-09-04 규율 — 신규 경로에서 `checkout`은 pathspec 오류로 exit 1). 신규 경로는
`git rm`으로(복원할 base 내용이 없다).

## 임시 clone 실측(2026-09-16, M-9·M-10 반영 재검증 — 완료)

```bash
git clone . <tmp>/rollback-check-r3   # HEAD = f992fd5(fix round 1 종결)
cd <tmp>/rollback-check-r3
# (위 롤백 명령 실행 — scope.md 역적용·재등재 포함)
```

- `git status --short` — 위 `NEW_FILES` 전부 `D`, `RESTORE_FILES` 전부 `M`,
  `policy-values.md`·`scope.md` 둘 다 `M` — 정확히 예상한 파일 집합. `rollback.md`는
  건드리지 않아 clone 그대로 남았다(의도된 상태). `milestone-5.md`는 `git status
  --short milestone-5.md` 빈 출력(무편집 확인).
- `git diff d78e162 -- "${RESTORE_FILES[@]}"` → **빈 출력**(RESTORE_FILES 는 base 와
  완전히 같아졌다).
- `git diff 41076d0 -- reports/evidence/m5/5e/scope.md` → 재등재한 「하네스 레인 변경」
  절 hunk **하나만** 남는다(그 절 텍스트 자체는 41076d0 이후 신설이라 diff 에 남는
  것이 맞다 — 그 밖의 모든 계약 갱신 (2)(3)(4) 내용은 사라져 41076d0 와 정확히
  같아졌다). 재등재한 절 본문을 `grep`으로 원문과 대조 — 한 글자도 다르지 않음.
- **M-9/M-10 이 요구한 확인**: 되돌린 트리에서 `grep -rn "setup.py\|test_dataset_
  settlements\|leak-pattern-baseline" reports/evidence/m5/5e/scope.md
  reports/evidence/m5/5e/policy-values.md milestone-5.md` → **빈 출력**. 계약 갱신
  (2)(3)(4)가 in_scope 에 추가했던 세 경로(`setup.py`·`uv.lock`·
  `test_dataset_settlements.py`)를 가리키는 문장이 되돌린 scope.md 에도, 다른 하네스
  파일에도 남지 않았다 — 「in_scope 선언 없는 파일을 가리키는 문장」이 없다.
- `(cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q)` →
  **658 passed** — base(`d78e162`) 직접 계수와 **정확히 일치**.
- `(cd ml-engine && uv run ruff check . && uv run mypy --strict src/ml_engine && uv run lint-imports)`
  → 전부 초록, `mypy`: **54 source files**(base 와 일치), `lint-imports`: **Contracts: 6
  kept**(base 계약 수와 일치, `app` 계약·grpc ignore_imports 둘 다 사라짐).
- `./gradlew --no-daemon check` → **BUILD SUCCESSFUL**(⑥ 게이트 단계, 2026-09-12 규율)
  — `leakPatternGate` 포함 base 시절 task 구성으로 전부 통과(scope.md 는 게이트가
  파일명으로 제외하는 대상이라 이 통과가 scope.md 내용을 보증하진 않는다 — 팀장 주의
  그대로, 이 문서의 새 산문도 간접 표현으로만 썼다). 되돌리지 않은
  `config/quality/leak-pattern-baseline.txt`는 `git restore --source=d78e162`로
  base 내용 그대로 복원되므로(이 slice 가 등재한 항목은 사라진다) 이 브랜치 밖의
  다른 baseline 항목과 충돌하지 않는다(이 브랜치는 단일 레인이라 전제 성립 확인).
- 임시 clone 은 절차 확인 뒤 완전히 삭제했다(`rm -rf`, worktree 잔여물이 아니라 독립
  clone 이라 `git worktree remove` 대상 아님).

## 알려진 제한

- 이 rollback 은 `m5-5e/2026-09-16` 브랜치 위에서 **파일 상태만** base 로 되돌린다 — 커밋
  이력은 되쓰지 않는다(evidence-pack 규율, 사실은 커밋 로그에 남긴다).
- `config/quality/leak-pattern-baseline.txt`는 이 slice 가 등재한 오탐 키 1건만 제거된다
  (다른 slice 의 기존 291줄은 무손상 — `git restore --source=d78e162`가 파일 전체를
  base 상태로 정확히 되돌리므로, base 이후 **이 브랜치에서** 그 파일을 만진 다른 커밋이
  없다는 전제가 성립할 때만 안전하다. 이 브랜치는 단일 레인이라 성립을 확인했다).
- `rollback.md` 자신은 위에서 설명한 이유로 롤백 대상 목록에 없다 — 완전한 base
  상태 재현이 필요하면 이 파일도 별도로 `git rm`해야 한다(감사 기록 보존과 완전
  복원은 상충하므로 운영자가 필요에 따라 선택).
- `scope.md`의 단일 역적용은 계약 갱신 (2)(3)(4)가 in_scope 에 넓힌 세 경로(M-9:
  `setup.py`·`uv.lock`·`test_dataset_settlements.py`)의 선언도 함께 되돌린다 — 그
  경로들 자체는 `NEW_FILES`/`RESTORE_FILES`가 이미 파일 상태로 처리하므로 실질
  영향은 없다(M-9 는 그래서 이 문서의 목록을 바꾸지 않는다고 명시했다). scope.md
  **내용**의 옳고 그름(예: in_scope 폭이 실제로 맞는지)은 이 rollback.md 가 판단하지
  않는다 — 팀장 소관(L-1).
- 재등재하는 「하네스 레인 변경」 절은 이 rollback.md 작성 시점(fix round 1 종결,
  `f992fd5`)의 원문을 그대로 박아 넣는다 — 이후 팀장이 그 절 내용을 바꾸면(예:
  CLAUDE.md·.claude/ 변경이 실제로 생기면) 이 스크립트의 heredoc 도 같이 갱신해야
  한다(그렇지 않으면 재등재본이 그 시점 HEAD 와 어긋난다).
