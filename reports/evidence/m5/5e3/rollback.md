# M5/5E-3 — rollback.md

착수 커밋 `495fa80`(팀장 — scope.md·milestone-5.md) + `98246fb`(팀장 — scope.md 어휘
정정), base `8799e05`(PR #20 병합). 목록은 `git diff --name-status 8799e05..HEAD`로
기계 산출(아래, 2026-09-16 구현 종결 시점 재산출 — 라운드마다 파일이 늘면 이 절차를
다시 돌린다).

```
M	milestone-5.md
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

## 절차

1. **신규 파일 삭제**(개별 경로, `-A`/`.` 금지):
   ```
   rm ml-engine/tests/gates/test_policy_loaders_fail_closed.py \
      ml-engine/tests/registry/__init__.py \
      ml-engine/tests/registry/test_policy.py
   rm -r reports/evidence/m5/5e3
   ```
2. **수정 파일을 base 로 복원**(개별 인자, `git restore --source=<base> --staged
   --worktree --`):
   ```
   git restore --source=8799e05 --staged --worktree -- \
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
   8799e05..HEAD -- milestone-5.md` 로 확인). 그래서 커밋별 hunk 격리 대신 착수
   커밋 경계 기준 단일 역적용 한 번으로 충분하다(2026-09-16 규율):
   ```
   git diff 495fa80~1..HEAD -- milestone-5.md | git apply -R
   ```
   (`495fa80~1..HEAD`로 범위를 잡는 이유: 이 파일에 대한 유일한 편집이 `495fa80`
   이므로 `495fa80~1..HEAD`와 `495fa80~1..495fa80`은 이 파일에 한해 동일한 diff를
   낸다 — 뒤 라운드가 이 파일을 또 만지면 `495fa80~1..HEAD`로 다시 계산해야 한다.)
4. **하네스 레인 절 재등재** — scope.md §「하네스 레인 변경」은 착수 시점 "없음"
   이었다. 이 slice 기간 중 `CLAUDE.md`·`.claude/` 변경 여부 재확인:
   ```
   git log --oneline 8799e05..HEAD -- CLAUDE.md .claude/
   ```
   결과 0건(실측, 구현 종결 시점) — 재등재할 내용 없음, scope.md 원문 그대로
   유지.

## 임시 clone 실측(①~⑥, 실행 완료 — 2026-09-16)

```
git clone . /tmp/5e3-rollback-check && cd /tmp/5e3-rollback-check
git checkout m5-5e3/2026-09-16
# 위 1~3 절차 실행
```

- ① 신규 파일 삭제 — `git status --short` 로 신규 파일 셋 + evidence 디렉터리가
  worktree 에서 사라짐(삭제 대상 표시)을 확인. exit 0.
- ② 수정 파일 복원 — `git diff --stat`(복원 대상 8 파일)이 빈 diff(0 changed) 임을
  확인. exit 0.
- ③ `milestone-5.md` 단일 역적용 — `git apply -R` exit 0. 확인은 HEAD 대비가 아니라
  **base 대비**로 잰다(`git diff 8799e05 -- milestone-5.md`가 빈 diff, 0줄) — HEAD
  대비 diff 는 되레 두 문단이 제거된 변경 자체를 보여준다(예상된 pending 변경이지
  「빈 diff」가 아니다. base 대비가 「제자리로 돌아갔는가」의 올바른 판정 기준).
- ④ 되돌린 트리에서 `(cd ml-engine && uv sync --frozen --all-extras && uv run
  python -m pytest tests -q)` → **938 passed**(base 시점 수치로 복귀, 5E-2 종결
  시점과 동일).
- ⑤ `(cd ml-engine && uv run mypy --strict src/ml_engine)` → `Success: no issues
  found in 72 source files`(신규 소스 파일 없이 기존 파일 6개만 편집했으므로
  파일 수는 base 와 동일 — 구현 종결 시점의 72 와 같다, `registry/policy.py`·
  `app/server.py`·`app/pipeline.py`·`inference/policy.py`·`training/holdout.py`·
  `training/__init__.py` 는 파일 삭제가 아니라 내용 복원이라 파일 수 자체는
  변하지 않는다).
- ⑥ **게이트 단계** — `./gradlew --no-daemon check` exit 0(BUILD SUCCESSFUL). 이
  slice 는 Kotlin 소스를 만지지 않았으므로 되돌린 트리도 base 와 동일하게 초록
  이어야 한다.

되돌린 트리 = 5E-2 종결 상태(`_load_inference_policy_safe` 래퍼 존재, `registry/
policy.py::load_policy`가 문법 오류를 raw 예외로 전파, `run_holdout`에 취소 확인
없음, test 938).

## 실측 결과

구현 종결 시점(HEAD `b78082d`)에서 임시 clone 으로 ①~⑥ 전부 실행했다. ①②③ 성립
(diff 0), ④ 938 passed, ⑤ `Success: no issues found in 72 source files`, ⑥
`BUILD SUCCESSFUL`. 임시 clone 은 확인 뒤 삭제했다. exit 코드와 핵심 결과는
`commands.md`에 옮기지 않는다(evidence-pack 규율 — rollback 실측은 이 문서 자체가
정본).
