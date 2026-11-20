# M14 / fullreview-fixes (14F) — 되돌리는 법과 그 실측

**실측 HEAD: `9973340f2ebd8f74fdc5ebec5514d0a8305acfd7`**(수정 라운드 2 의 마지막 산출물 커밋에서 다시 쟀다)

되돌림은 **range revert 가 아니라 경로 한정 복원**이다. 복원 목록은 손으로 적지 않고
`git diff --name-only <base>..HEAD` 로 **기계 산출**했으며, 라운드마다 다시 산출한다.

## 복원 원천

**고정 SHA `15a142c6`**(이 slice 의 착수 base). 머지 전이므로 원천이 착수 base 와 **같고**,
그래서 「복원 대상 blob 이 착수 base 와 새 원천에서 같은가」(셋째 단언)는 **같은 SHA 를 양쪽에
두는 항등식**이다 — 다른 수가 나올 자리가 없다. 머지 뒤 되돌림은 PR revert 가 받는다.

움직이는 `origin/main` 을 쓰지 않는다.

## 명령

```
git restore --source=15a142c6 --staged --worktree -- $(git diff --name-only 15a142c6..HEAD)
```

목록은 **그 자리에서 다시 산출**한다(파일에 박아 두지 않는다 — 박으면 라운드마다 낡는다).

## 실측 ①~⑥ — 버릴 clone 에서

저장소를 `--no-hardlinks` 로 복제하고 실측 HEAD 를 checkout 한 뒤 위 명령을 **끝까지** 돌렸다.

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령의 종료 코드 | **0** |
| ② | 복원 뒤 `git status --porcelain` 의 D/M 계수 | **D 5 · M 40**(= `base..HEAD` 의 `A 5 · M 40` 과 짝이 맞는다) · 복원 목록과 변경 경로의 **양방향 `comm` 0/0** |
| ③ | 복원 뒤 base 와의 diff | **빈 출력**(`git diff --quiet` exit 0) |
| ④ | 복원된 트리의 컴파일 | `tsc --noEmit` exit **0** |
| ⑤ | 복원된 트리의 test | `npm run test` exit **0** — 파일 74 · test **1894**(= base 의 수) |
| ⑥ | 복원된 트리의 게이트 | `npm run lint` 0 · `npm run gate:tests` 0 · `ktlintCheck detekt cpdCheck leakPatternGate` 0(`leakPatternGate` 실행됨) |

**갈음이 아니라 트리 동일성으로 잰다.** 복원 뒤 색인 트리 해시가 base 의 트리 해시와 **같다**:

```
base tree     9d3e42a38bc4f4d5c72a6625c1b0784b79c3e1c9
restored tree 9d3e42a38bc4f4d5c72a6625c1b0784b79c3e1c9
```

그 등식은 ③ 보다 강하다 — ③ 은 **목록 안의 경로**만 보지만, 트리 해시는 저장소 전체를 본다.
곧 **복원 목록이 완전하다**는 증거이기도 하다(목록 밖에 남은 이 slice 의 변경이 있었다면 두 해시가
갈린다).

## 계수의 뜻

| 표지 | 수 | 왜 |
| --- | --- | --- |
| **내 표지 0** | D 4 | 이 slice 가 **신설한** 파일 넷(evidence 넷)이 복원으로 사라진다 |
| **남의 표지 보존** | M 37 | 나머지는 원천에 있던 파일이고 그 내용이 base 로 돌아간다 — 지워지지 않는다 |

## 실측 HEAD 와 판정 SHA 사이

실측 HEAD 뒤에 오는 커밋은 **evidence 뿐**이다. 그 파일들은 복원 목록 안에 있지만 원천
`15a142c6` 에 **없는 신설 파일**이고, 그래서 2026-11-11 규율의 예외(「나온 경로가 전부 원천에
없는 신설 파일일 때」)에 해당한다. **수를 적지 않고** 전칭식으로 적는다 — 라운드가 한 번 더
돌면 수는 늘지만 그 성질은 그대로다:

> 실측 HEAD 와 판정 SHA 사이에서 복원 목록 안의 경로가 움직였다면, 그 경로는 전부
> `reports/evidence/m14/fullreview-fixes/` 아래의 **원천에 없는 신설 파일**이다.

열거와 「원천 부재」 확인은 verifier 가 한다. 그 사이에 **머지 커밋이 없다**는 것도 함께 본다
(`git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력).

## 라운드마다 다시 잰다

이 절의 수치는 **수정 라운드 2 의 실측**이다. 앞의 것은 `D 4 · M 37`(라운드 0) ·
`D 5 · M 39`(라운드 1) · `D 5 · M 40`(라운드 1 보강 · 라운드 2) 였고, 라운드가 파일을 더하면 그 수가 움직인다 — 그래서 **목록을 파일에 박지 않고** 명령으로
산출하며, 실측도 라운드마다 다시 돈다(앞 라운드의 실측을 옮겨 적지 않는다).

## 이 slice 가 되돌리지 않는 것

- `docs/usecase/captures/` 의 출하 캡처 — **건드리지 않았다**(이 slice 의 앞·뒤 캡처는 레인
  scratchpad 에만 있다).
- 하네스(`CLAUDE.md` · `.claude/`) — `base..HEAD` 에 줄이 없다(scope.md 의 「하네스 레인 변경」).
