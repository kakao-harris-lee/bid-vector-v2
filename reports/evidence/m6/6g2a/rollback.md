# M6/6G-2a — rollback

실측 HEAD: `09e32d6b` (이 slice 의 **마지막 산출물 커밋** — r2 사후 일괄의 구현 완료 문단)

base `30c6659e`. 되돌림은 **range revert 가 아니라 경로 한정**이다 — 같은 range 에 팀장 레인
커밋(`228bd505`)이 있고, 그 커밋의 `milestone-6.md` hunk 는 이 slice 의 문단 **과 팀장 레인의
운영자 결정 문단을 함께** 담는다.

## 목록은 손으로 쓰지 않는다

```
git diff --name-status 30c6659e..HEAD
```
에서 기계적으로 낸다. 라운드마다 다시 산출한다 — 목록이 낡는 것이 이 결함의 실제 원인이다.

산출 결과에서 `reports/evidence/m6/6g2a/**`(되돌리지 않는다)와 공유 파일 `milestone-6.md`
(아래 ②)를 빼면 **A 0 · M 3** 이다.

| 상태 | 경로 |
|---|---|
| M | `ml-engine/tests/app/test_backtest_policy_sensitivity.py` |
| M | `ml-engine/tests/evaluation/_backtest_fixture.py` |
| M | `ml-engine/tests/evaluation/_backtest_support.py` |

**새 파일이 0 이다.** 이 slice 는 기존 세 파일만 고쳤고 디렉터리를 만들지 않았다 — 6G-2d 가
라운드마다 새 파일이 늘어 복원 목록이 낡았던 자리(A 1 → 14)가 여기서는 생기지 않는다.
수정 라운드가 새 파일을 만들면 **in_scope 와 대조하고 이 표를 다시 산출한다**.

`ml-engine/src/**` 는 목록에 **없다**(D-6G2a-9 기대값). 그래서 test 만 되돌려도 ④ 가 사라진
심볼을 가리키는 일이 구조적으로 생기지 않는다 — 6G-2d 가 세 라운드에 걸쳐 겪은 실패
갈래다. 그 사실을 ③ 과 ④ 로 확인했다.

## ① Python 경로 한정 복원

```
git restore --source=30c6659e --staged --worktree -- \
  ml-engine/tests/app/test_backtest_policy_sensitivity.py \
  ml-engine/tests/evaluation/_backtest_fixture.py \
  ml-engine/tests/evaluation/_backtest_support.py
```

경로는 **개별 인자**다(글롭 하나로 묶지 않는다 — 묶으면 evidence 와 공유 파일까지 걷는다).

## ② 공유 파일 `milestone-6.md` — 문단 단위 삭제

`milestone-6.md` 를 담은 커밋은 **다섯**(`228bd505` 착수 문단, `a3cde08d` 구현 완료 문단,
`f204f794`·`3d2ddccb`·`09e32d6b` 그 문단의 갱신 셋)이고,
앞의 hunk 는 **팀장 레인의 운영자 결정 문단을 같이** 담는다. 그래서 **hunk 역적용을 쓸 수
없다** — `git diff 228bd505~1..228bd505 -- milestone-6.md | git apply -R` 은 남의 문단까지
지운다(`--3way` 도 자동 해소하지 못한다).

파일에는 이 slice 와 맞물린 문단이 **셋** 있다 — 착수 · 구현 완료 · 그 사이의 운영자 결정이다.
지우는 것은 **착수 · 구현 완료 · 종결 셋**이고 운영자 결정 문단은 **남는다**(실수집 시작 조건의 기록이다). 각 블록은
아래 표지로 시작하는 줄부터 다음 빈 줄까지다.

| 지울 블록 | 시작 표지 |
|---|---|
| 착수 | `**6G-2a 착수 2026-10-01` |
| 구현 완료 | `**6G-2a 구현 완료 2026-10-01` (라운드마다 문면이 길어졌지만 표지는 그대로다) |
| 종결(팀장 레인) | `**6G-2a 종결 2026-10-01` — D-6G2a-22 종결 문단, 같은 표지 단위 삭제 |

| **남기는** 블록 | 시작 표지 |
|---|---|
| 운영자 결정(팀장 레인) | `**운영자 결정 2026-10-01` |

**지우지 않는 것**: 같은 hunk 의 `**운영자 결정 2026-10-01(6G-2d 머지 뒤, 실수집 시작
조건)**` 문단 — 팀장 레인의 산출물이다.

확인은 **둘 다** 본다. 표지는 **줄 시작**으로 본다 — 「6G-2a」라는 글자만 세면 다른 레인의
종결 문단(6G·6G-2d)이 「다음: 6G-2a」로 언급하는 두 줄이 섞여 0 이 되지 않는다(실측).

| 확인 | 명령이 세는 것 | 기대 | 실측 |
|---|---|---|---|
| 내 줄 사라짐 | `grep -c '^\*\*6G-2a '` | 0 | **0** |
| 남의 줄 남음 | `grep -c '^\*\*운영자 결정 2026-10-01'` | 1 | **1** |
| 남의 레인이 언급하는 줄 | `grep -c '6G-2a'` | base 와 같은 **2** | **2** |

## 실측 (버릴 clone, ①~⑥)

`09e32d6b` 를 clone 해 위 절차를 그대로 돌렸다. r0 · r1 · r2 사후 일괄 — **라운드마다 다시
쟀다**(목록이 낡는 것이 이 결함의 실제 원인이다). 되돌리는 경로와 절차는 r0 과 같다: 세
라운드가 **새 파일을 하나도 만들지 않았고** 기존 세 파일만 고쳤다. 갈음은 「HEAD 초록」이 아니라 **트리
동일성**으로 했다 — 되돌린 세 경로가 base 와 **같은 트리**임을 ③ 이 재고, 그 위에서 ④⑤⑥ 을
돌렸다.

| 단계 | 재는 것 | 결과 |
|---|---|---|
| ① | 경로 한정 복원 명령 exit | **0** |
| ② | 문단 블록 삭제 — 지운 블록 수 · 세 확인 | **2 블록**(54,125 -> 52,019 바이트) · 내 줄 0 · 남의 줄 1 · base 와 같은 언급 2 |
| ③ | 되돌린 뒤 상태 수 · base 대비 diff | **M 4**(세 경로 + 공유 파일) · 세 경로의 diff **빈 출력** · 공유 파일은 남의 문단 **2 줄만** 남음 |
| ④ | import(출하 모듈 · 되돌린 test 지원 모듈) + 수집 | **import ok** · **1,209 tests collected**(base 와 같은 수) |
| ⑤ | `pytest tests -q` | **1,209 passed**(192 초) |
| ⑥ | ruff check · ruff format --check · mypy --strict · import-linter · 설계 래칫 | **전부 exit 0** |

③ 의 「세 경로의 diff 빈 출력」이 트리 동일성이고, ④ 의 「1,209 collected」가 그 동일성의
독립 확인이다 — base 의 test 수와 정확히 같다(이 slice 가 더한 **141** 이 사라졌다).

## 이 실측이 유효한 범위

evidence 커밋은 언제나 마지막 산출물 커밋 **뒤에** 오므로 실측 HEAD 와 판정 SHA 는 영원히
다르다. verifier 가 대조하는 것은 둘의 동일성이 아니라 **그 사이에 되돌림 대상이
움직였는가**다:

```
git diff --name-only 09e32d6b..<판정 SHA> -- \
  ml-engine/tests/app/test_backtest_policy_sensitivity.py \
  ml-engine/tests/evaluation/_backtest_fixture.py \
  ml-engine/tests/evaluation/_backtest_support.py \
  milestone-6.md
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 미검증이다. 이 확인은 **CI 텍스트
게이트로 대체되지 않는다**.
