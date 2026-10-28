# M6/6G-rc — 되돌리기

실측 HEAD: `f8990b90` (이 slice 의 **마지막 산출물 커밋**. 뒤에 오는 evidence 커밋은
되돌림 대상이 아니다 — 아래 목록에 evidence 경로가 없다.)

base: `0768bda1` · 브랜치 `m6-6grc/2026-10-28` (아직 머지 전이고 **main 을 병합한 적이 없다** —
그래서 공유 파일도 base 에서 복원한다. 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다.)

## 목록은 기계가 낸다

```
git diff --name-status 0768bda1..f8990b90
```

`A` 는 삭제 대상, `M` 은 base 로 복원. **라운드마다 파일이 늘면 이 절차를 다시 돌린다** —
목록이 낡는 것이 이 결함의 실제 원인이다. 그 산출에서 `reports/evidence/m6/6grc/**` 는
빼며(장부는 되돌리지 않는다), `CLAUDE.md`·`.claude/**` 는 애초에 이 range 에 없다.

## 절차

```
git restore --source=0768bda1 --staged --worktree -- \
  docs/runbook/m6-6g-real-collection.md \
  ml-engine/policy/strategy-backtest-v2.yaml \
  ml-engine/src/ml_engine/evaluation/backtest/floor.py \
  ml-engine/src/ml_engine/evaluation/backtest/metrics.py \
  ml-engine/src/ml_engine/evaluation/backtest/policy.py \
  ml-engine/src/ml_engine/evaluation/backtest/policy_values.py \
  ml-engine/src/ml_engine/evaluation/backtest/rules.py \
  ml-engine/src/ml_engine/evaluation/backtest/strategies.py \
  ml-engine/tests/app/test_backtest_golden.py \
  ml-engine/tests/app/test_backtest_job.py \
  ml-engine/tests/app/test_backtest_policy_sensitivity.py \
  ml-engine/tests/app/test_seed_count_pinned.py \
  ml-engine/tests/evaluation/_backtest_support.py \
  ml-engine/tests/evaluation/test_backtest_exclusions.py \
  ml-engine/tests/evaluation/test_backtest_policy.py \
  ml-engine/tests/evaluation/test_backtest_sample_scope.py \
  ml-engine/tests/evaluation/test_backtest_strategies.py \
  ml-engine/tests/evaluation/test_evaluation_no_stray_numeric_literals.py
```

경로는 **개별 인자**다(변수 하나로 묶으면 pathspec 이 하나가 되어 아무것도 매치하지 않고
exit 0 이 된다). 신규 파일(`strategy-backtest-v2.yaml`)은 base 에 없어 같은 명령이 지운다 —
`git rm` 을 따로 두지 않는다. `git checkout <base> -- <경로>` 는 쓰지 않는다.

**hunk 격리는 필요 없다.** 각 파일을 이 range 에서 만진 커밋이 이 slice 의 둘뿐임을
`git log --oneline 0768bda1..f8990b90 -- <파일>` 로 확인한다(아래 실측 ⓪).

되돌린 뒤 **v1 정책이 출하로 돌아온다** — v1 파일은 바이트 동일하게 보존돼 있고 base 의
로더는 새 키를 요구하지 않으므로 그대로 적재된다. 되돌릴 것이 정책 값 하나가 아니라
**거동**이므로(앵커·적용 범위) 부분 비활성화 수단은 두지 않았다.

## 실측 — 임시 clone (①~⑥)

trailing echo 로 가리지 않고 각 명령의 종료 코드를 그 자리에서 읽는다. 트리는 **되돌린 뒤의
실제 상태**다 — 코드는 base, `reports/evidence/m6/6grc/` 는 이 slice 의 문서가 그대로 있다.

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ⓪ | 각 파일을 만진 커밋이 이 slice 뿐인가 | 열여덟 파일 전부 이 slice 의 세 커밋만 — 남의 줄이 없어 hunk 격리 불필요 |
| ① | 복원 명령 종료 코드 | 0 |
| ② | D/M 수 | `D` 1(신규 v2 정책) · `M` 17 — 합 18 = 목록 길이 |
| ③ | `git diff 0768bda1 -- <목록>` | 0 줄. v2 정책 파일 없음 · v1 정책 파일 있음 · 이 slice 의 술어 이름을 담은 `.py` **0 개** |
| ④ | 되돌린 트리의 compile (`compileall`) | 0 |
| ⑤ | 되돌린 트리의 test (`pytest tests -q`) | 0 — **1605 passed**(HEAD 의 1625 보다 스물 적다 = 이 slice 가 더한 test 수) |
| ⑥-a | 되돌린 트리의 `ml-engine` job 게이트 | ruff check · ruff format · mypy --strict · lint-imports · 설계 래칫 · 재활용 출처 **전부 0** |
| ⑥-b | 되돌린 트리의 Kotlin `check` | 0 — `BUILD SUCCESSFUL`(28m 47s). `leakPatternGate` 가 이 slice 의 evidence 를 읽는 상태로 돌았다 |

⑥-b 는 **결과 표를 채우기 전의** evidence 문면으로 쟀다 — 이 표가 더하는 것은 종료 코드와
수뿐이고 스캔 어휘를 더하지 않는다. 마지막 HEAD(이 표를 담은 커밋)의 Kotlin `check` 는
완료 보고가 정본이다.

되돌린 트리에서 **v1 정책이 그대로 적재된다**는 것은 ⑤ 가 증명한다(base 의 test 가 출하
경로를 v1 으로 보고 전부 초록이다).
