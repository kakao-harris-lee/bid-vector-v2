# M5/5C-2 — rollback.md

base = `c669a71`(origin/main, PR #14 병합). `scope.md`는 구현 레인 커밋이 0건이라
(팀장 착수 커밋 `30b42d6`만 만졌다) 목록에서 제외한다(5C-1 rollback.md 와 같은 판단
근거). `milestone-5.md`는 문서 레인 소관이라 되돌리지 않는다(scope.md 원문 명시).

## 파일 목록(기계 산출)

```
git diff --name-status c669a71..HEAD -- \
  ml-engine/src/ml_engine/evaluation \
  ml-engine/src/ml_engine/training/holdout.py \
  ml-engine/src/ml_engine/training/_holdout_fit.py \
  ml-engine/src/ml_engine/training/_holdout_window.py \
  ml-engine/src/ml_engine/training/__init__.py \
  ml-engine/policy/evaluation-v1.yaml \
  ml-engine/tests/evaluation \
  ml-engine/tests/training/test_holdout.py \
  ml-engine/tests/training/test_no_stray_numeric_literals.py \
  reports/evidence/m5/5c2/policy-values.md reports/evidence/m5/5c2/reuse.md \
  reports/evidence/m5/5c2/checklist.md reports/evidence/m5/5c2/commands.md \
  reports/evidence/m5/5c2/golden-manifest.json reports/evidence/m5/5c2/rollback.md
```

출력(라운드마다 파일이 늘면 이 절차를 다시 돌린다):

```
A	ml-engine/policy/evaluation-v1.yaml
M	ml-engine/src/ml_engine/evaluation/__init__.py
A	ml-engine/src/ml_engine/evaluation/baselines.py
A	ml-engine/src/ml_engine/evaluation/diagnostics.py
A	ml-engine/src/ml_engine/evaluation/policy.py
A	ml-engine/src/ml_engine/evaluation/report.py
A	ml-engine/src/ml_engine/evaluation/scoring.py
A	ml-engine/src/ml_engine/evaluation/segments.py
A	ml-engine/src/ml_engine/evaluation/verdict.py
A	ml-engine/src/ml_engine/evaluation/windows.py
M	ml-engine/src/ml_engine/training/__init__.py
A	ml-engine/src/ml_engine/training/_holdout_fit.py
A	ml-engine/src/ml_engine/training/_holdout_window.py
A	ml-engine/src/ml_engine/training/holdout.py
A	ml-engine/tests/evaluation/test_baselines.py
A	ml-engine/tests/evaluation/test_diagnostics.py
A	ml-engine/tests/evaluation/test_evaluation_policy.py
A	ml-engine/tests/evaluation/test_public_signatures.py
A	ml-engine/tests/evaluation/test_report.py
A	ml-engine/tests/evaluation/test_scoring.py
A	ml-engine/tests/evaluation/test_segments.py
A	ml-engine/tests/evaluation/test_verdict.py
A	ml-engine/tests/evaluation/test_windows.py
A	ml-engine/tests/training/test_holdout.py
M	ml-engine/tests/training/test_no_stray_numeric_literals.py
A	reports/evidence/m5/5c2/policy-values.md
A	reports/evidence/m5/5c2/reuse.md
A	reports/evidence/m5/5c2/checklist.md
A	reports/evidence/m5/5c2/commands.md
A	reports/evidence/m5/5c2/golden-manifest.json
A	reports/evidence/m5/5c2/rollback.md
```

`reports/evidence/m5/5c2/policy-values.md`는 팀장 착수 커밋(`30b42d6`)이 만든 파일이지만
구현 레인이 `change_history`에 행 1개를 추가했다 — 5C-1 rollback.md 의 판단(head/tail
hunk 가 겹치지 않으면 `base..HEAD` 전체 되돌리기가 안전)을 그대로 적용한다: base 에
이 파일이 아예 없었으므로(위 diff 「A」) 되돌리면 파일 자체가 사라진다 — **팀장의
착수 내용(§1 표·§2·§3)도 함께 사라진다는 뜻이다.** 팀장 착수 결정 자체를 되돌리는
것이 아니라 "구현 완료" 사실만 지우려면, 구현 레인 커밋 하나만 hunk 격리로 되돌린다
(아래 「공유 파일 hunk 격리」 절). `scope.md`는 구현 레인 커밋이 없어 되돌릴 hunk가
없다(팀장 착수 결정 그대로 유지).

## 되돌리는 것 — 신규 파일(삭제) + 수정 파일(복원)

```bash
# 신규 파일 삭제
rm -f ml-engine/policy/evaluation-v1.yaml \
  ml-engine/src/ml_engine/evaluation/baselines.py \
  ml-engine/src/ml_engine/evaluation/diagnostics.py \
  ml-engine/src/ml_engine/evaluation/policy.py \
  ml-engine/src/ml_engine/evaluation/report.py \
  ml-engine/src/ml_engine/evaluation/scoring.py \
  ml-engine/src/ml_engine/evaluation/segments.py \
  ml-engine/src/ml_engine/evaluation/verdict.py \
  ml-engine/src/ml_engine/evaluation/windows.py \
  ml-engine/src/ml_engine/training/_holdout_fit.py \
  ml-engine/src/ml_engine/training/_holdout_window.py \
  ml-engine/src/ml_engine/training/holdout.py \
  ml-engine/tests/evaluation/test_baselines.py \
  ml-engine/tests/evaluation/test_diagnostics.py \
  ml-engine/tests/evaluation/test_evaluation_policy.py \
  ml-engine/tests/evaluation/test_public_signatures.py \
  ml-engine/tests/evaluation/test_report.py \
  ml-engine/tests/evaluation/test_scoring.py \
  ml-engine/tests/evaluation/test_segments.py \
  ml-engine/tests/evaluation/test_verdict.py \
  ml-engine/tests/evaluation/test_windows.py \
  ml-engine/tests/training/test_holdout.py \
  reports/evidence/m5/5c2/policy-values.md \
  reports/evidence/m5/5c2/reuse.md \
  reports/evidence/m5/5c2/checklist.md \
  reports/evidence/m5/5c2/commands.md \
  reports/evidence/m5/5c2/golden-manifest.json \
  reports/evidence/m5/5c2/rollback.md

# 수정 파일 복원(base 상태로) — 구현 레인 단독 소유(위 git log 확인, 겹치는 slice 없음)
git restore --source=c669a71 --staged --worktree -- \
  ml-engine/src/ml_engine/evaluation/__init__.py \
  ml-engine/src/ml_engine/training/__init__.py \
  ml-engine/tests/training/test_no_stray_numeric_literals.py
```

`training/__init__.py`·`evaluation/__init__.py`·`test_no_stray_numeric_literals.py`
셋 다 `git log --oneline c669a71..HEAD -- <파일>`이 구현 레인 커밋만 보여준다(다른
slice 겹침 0) — hunk 격리 없이 `git restore --source`로 안전하게 base 전체 복원한다.

## 공유 파일 hunk 격리(대안 — `policy-values.md`를 팀장 착수 내용까지 지우지 않고
싶을 때)

```bash
git diff <구현 레인이 policy-values.md 를 만진 커밋 해시>~1..<같은 해시> \
  -- reports/evidence/m5/5c2/policy-values.md | git apply -R
```

구현 레인이 이 파일을 만진 커밋은 change_history 행 추가 1개뿐(단일 hunk, 파일
끝) — `30b42d6`(팀장, §1~§3)과 삽입 지점이 겹치지 않아 격리 적용은 충돌 없이
성립한다(5C-1 policy-values.md 와 같은 구조).

## 임시 worktree 실측

```
git worktree add --detach /tmp/5c2-rollback-head HEAD   # exit 0 (실행: 2026-09-15)
# 위 rm + git restore 명령을 /tmp/5c2-rollback-head 안에서 실행
git -C /tmp/5c2-rollback-head diff --name-status c669a71 -- \
  ml-engine/src/ml_engine/evaluation ml-engine/src/ml_engine/training/holdout.py \
  ml-engine/src/ml_engine/training/_holdout_fit.py ml-engine/src/ml_engine/training/_holdout_window.py \
  ml-engine/src/ml_engine/training/__init__.py ml-engine/policy/evaluation-v1.yaml \
  ml-engine/tests/evaluation ml-engine/tests/training/test_holdout.py \
  ml-engine/tests/training/test_no_stray_numeric_literals.py \
  reports/evidence/m5/5c2/policy-values.md reports/evidence/m5/5c2/reuse.md \
  reports/evidence/m5/5c2/checklist.md reports/evidence/m5/5c2/commands.md \
  reports/evidence/m5/5c2/golden-manifest.json reports/evidence/m5/5c2/rollback.md
# → 빈 출력이어야 한다(in_scope diff 0)
(cd /tmp/5c2-rollback-head/ml-engine && uv sync --frozen --all-extras && \
 uv run ruff check . && uv run mypy --strict src/ml_engine && uv run lint-imports && \
 uv run python -m pytest tests -q && uv run python tools/design_ratchet.py --check && \
 uv run python tools/reuse_provenance_check.py && \
 uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']")
# → 모두 exit 0, pytest 수는 main(c669a71) 직접 계수(521)와 같아야 한다
```

**실측 결과**(2026-09-15, `/tmp/5c2-rollback-head`, base HEAD `9d9978d`+`7a7eed3` 시점):

- `rm` 22개 + `git restore` 3개: exit 0
- in_scope `git diff --name-status c669a71` 위 경로 전부: **빈 출력**(diff 0)
- `main`(c669a71) 직접 pytest 계수: **521 passed**(위 별도 worktree `/tmp/5c2-rollback-check`
  에서 실측)
- 되돌린 트리 `uv run python -m pytest tests -q`: **521 passed**(동일 — 회귀 없음)
- 되돌린 트리 `uv run ruff check .`: exit 0
- 되돌린 트리 `uv run mypy --strict src/ml_engine`: exit 0
- 되돌린 트리 `uv run lint-imports`: exit 0(`training/evaluation 층` 계약이 `evaluation`
  패키지가 다시 스캐폴드 상태로 돌아가도 여전히 KEPT — 빈 패키지도 계약을 어기지 않는다)
- 되돌린 트리 `uv run python tools/design_ratchet.py --check`: exit 0
- 되돌린 트리 `uv run python tools/reuse_provenance_check.py`: exit 0(evaluation·holdout
  Reuse 포인터가 파일과 함께 사라져 대조 대상 자체가 없다)
- 되돌린 트리 python 버전 assertion: exit 0

되돌린 트리가 컴파일뿐 아니라 **전 게이트(lint·type·import·design-ratchet·reuse·pytest)
초록**임을 확인했다(evidence-pack 규격 ④⑤⑥).

## 복구 시간

되돌리기: 명령 실행 수 초. 재적용(rollback 취소, 즉 이 slice 를 다시 살리기)은
`git restore --source=<이 slice 의 HEAD 커밋>`으로 같은 목록을 반대 방향으로 복원하거나,
브랜치를 처음부터 재체크아웃한다(구현 레인 커밋이 모두 독립적이라 되돌리기 전 상태로의
복귀는 `git checkout <5c2 브랜치 HEAD> -- <목록>`으로 즉시 가능).
