# M5/5E-3 — rollback.md

착수 커밋 `495fa80`(팀장 — scope.md·milestone-5.md) + `98246fb`(팀장 — scope.md 어휘
정정), base `8799e05`(PR #20 병합). 목록은 `git diff --name-status 8799e05..HEAD`로
기계 산출(아래, **수정 라운드 1 종결 시점** 재산출 — 라운드마다 파일이 늘면 이
절차를 다시 돌린다).

```
M	milestone-5.md
M	ml-engine/pyproject.toml
M	ml-engine/src/ml_engine/app/pipeline.py
M	ml-engine/src/ml_engine/app/server.py
M	ml-engine/src/ml_engine/inference/policy.py
M	ml-engine/src/ml_engine/registry/policy.py
M	ml-engine/src/ml_engine/training/__init__.py
M	ml-engine/src/ml_engine/training/holdout.py
M	ml-engine/tests/app/test_pipeline.py
A	ml-engine/tests/gates/test_policy_loaders_fail_closed.py
A	ml-engine/tests/registry/__init__.py
A	ml-engine/tests/registry/test_policy.py
M	ml-engine/tests/training/test_holdout.py
A	reports/evidence/m5/5e3/**  (scope.md·commands.md·checklist.md·reuse.md·golden-manifest.json·rollback.md)
```

수정 라운드 1 이 늘린 파일은 `ml-engine/pyproject.toml`(수정, D-5E3-6 import-linter
계약) 하나뿐이다 — `tests/gates/test_policy_loaders_fail_closed.py`는 이미 목록에
있던 파일의 재작성(신규 아님).

## 절차

1. **신규 파일 삭제**(개별 경로, `-A`/`.` 금지):
   ```
   rm ml-engine/tests/gates/test_policy_loaders_fail_closed.py \
      ml-engine/tests/registry/__init__.py \
      ml-engine/tests/registry/test_policy.py
   rm -r reports/evidence/m5/5e3
   ```
2. **수정 파일을 base 로 복원**(개별 인자, `git restore --source=<base> --staged
   --worktree --`) — `pyproject.toml` 포함(아래 「pyproject.toml 은 hunk 격리가
   아니라 통짜 복원으로 충분한 이유」 참고):
   ```
   git restore --source=8799e05 --staged --worktree -- \
     ml-engine/pyproject.toml \
     ml-engine/src/ml_engine/app/pipeline.py \
     ml-engine/src/ml_engine/app/server.py \
     ml-engine/src/ml_engine/inference/policy.py \
     ml-engine/src/ml_engine/registry/policy.py \
     ml-engine/src/ml_engine/training/__init__.py \
     ml-engine/src/ml_engine/training/holdout.py \
     ml-engine/tests/app/test_pipeline.py \
     ml-engine/tests/training/test_holdout.py
   ```
3. **`milestone-5.md`는 착수 커밋 기준 단일 역적용** — 이 파일을 만진 커밋은
   `495fa80` 하나뿐이다(다른 slice 의 줄이 이 range 안에 없다, `git log --oneline
   8799e05..HEAD -- milestone-5.md` 로 확인, 수정 라운드 1 재확인 결과 여전히
   `495fa80` 하나). 그래서 커밋별 hunk 격리 대신 착수 커밋 경계 기준 단일 역적용
   한 번으로 충분하다(2026-09-16 규율):
   ```
   git diff 495fa80~1..HEAD -- milestone-5.md | git apply -R
   ```
4. **`pyproject.toml`은 hunk 격리가 아니라 통짜 복원으로 충분한 이유** — 2026-09-09
   규율(「공유 파일은 줄 단위로 끝나지 않는다」)은 **두 slice** 가 같은 파일을
   나눠 만졌을 때 커밋 해시로 hunk 를 격리하라는 것이다. `git log --oneline
   8799e05..HEAD -- ml-engine/pyproject.toml`을 실행하면 이 range 안에서 이
   파일을 만진 커밋이 `014752d`(이 slice, HIGH-1 수정) **하나뿐**임이 확인된다
   — 다른 slice 의 줄이 이 range 안에 없으므로 hunk 격리와 통짜 `restore --
   source=8799e05`는 이 파일에 한해 같은 결과를 낸다(위 2 의 명령이 곧 hunk
   격리의 결과와 동치). 이후 다른 slice 가 이 파일을 만지고 병합돼 range 가
   넓어지면 이 절차를 재확인해야 한다.
5. **하네스 레인 절 재등재** — scope.md §「하네스 레인 변경」은 착수 시점 "없음"
   이었다. 이 slice 기간 중 `CLAUDE.md`·`.claude/` 변경 여부 재확인:
   ```
   git log --oneline 8799e05..HEAD -- CLAUDE.md .claude/
   ```
   결과 0건(실측, 수정 라운드 1 종결 시점) — 재등재할 내용 없음, scope.md 원문
   그대로 유지.

## 임시 clone 실측(①~⑥, 실행 완료 — 2026-09-16, 수정 라운드 1 종결 시점)

```
git clone . /tmp/5e3-rollback-check-r1 && cd /tmp/5e3-rollback-check-r1
git checkout m5-5e3/2026-09-16
# 위 1~4 절차 실행
```

- ① 신규 파일 삭제 — `git status --short` 로 신규 파일 셋 + evidence 디렉터리가
  worktree 에서 사라짐(삭제 대상 표시)을 확인. exit 0.
- ② 수정 파일 복원(9 파일, `pyproject.toml` 포함) — `git diff --stat`이 빈
  diff(0 changed) 임을 확인. exit 0.
- ③ `milestone-5.md` 단일 역적용 — `git apply -R` exit 0. 확인은 **base 대비**로
  잰다(`git diff 8799e05 -- milestone-5.md`가 빈 diff, 0줄).
- ④ 되돌린 트리에서 `(cd ml-engine && uv sync --frozen --all-extras && uv run
  python -m pytest tests -q)` → **938 passed**(base 시점 수치로 복귀, 5E-2 종결
  시점과 동일).
- ⑤ `(cd ml-engine && uv run mypy --strict src/ml_engine)` → `Success: no issues
  found in 72 source files`(파일 삭제가 아니라 내용 복원이라 파일 수 자체는
  변하지 않는다).
- ⑥ **게이트 단계** — `(cd ml-engine && uv run lint-imports)` → `Contracts: 7
  kept, 0 broken`(D-5E3-6 계약이 사라져 8 → 7 로 되돌아간다) + `./gradlew
  --no-daemon check` exit 0(BUILD SUCCESSFUL). 이 slice 는 Kotlin 소스를 만지지
  않았으므로 되돌린 트리도 base 와 동일하게 초록이어야 한다.

되돌린 트리 = 5E-2 종결 상태(`_load_inference_policy_safe` 래퍼 존재, `registry/
policy.py::load_policy`가 문법 오류를 raw 예외로 전파, `run_holdout`에 취소 확인
없음, import-linter 계약 7 개, test 938).

## 실측 결과

구현 종결 시점(HEAD `b78082d`)과 **수정 라운드 1 종결 시점(HEAD `fe76d50`)** 둘
다 임시 clone 으로 ①~⑥ 전부 실행했다. ①②③ 성립(diff 0), ④ 938 passed, ⑤
`Success: no issues found in 72 source files`, ⑥ `lint-imports` 7 kept·`./gradlew
--no-daemon check` `BUILD SUCCESSFUL`. 임시 clone 은 확인 뒤 삭제했다. exit 코드와
핵심 결과는 `commands.md`에 옮기지 않는다(evidence-pack 규율 — rollback 실측은 이
문서 자체가 정본).
