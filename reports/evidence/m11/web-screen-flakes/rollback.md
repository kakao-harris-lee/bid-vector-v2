# M11 / web-screen-flakes — rollback

`git revert` range 가 아니라 **in_scope 경로 한정 복원**이다.

```
git restore --source=94ee445b50cafa669af435e651d811cc551c67e8 --staged --worktree -- <아래 목록>
```

실측 HEAD: `1457e9fe089041a3e248588ac055c816bc4ab13a`

## 복원 목록 — 기계 산출

`git diff --name-status 94ee445b..1457e9fe` 가 낸 **여덟**이고, 라운드마다 재산출한다.

| 상태 | 경로 |
| --- | --- |
| M | `docs/discovery/capability-map.md` |
| A | `reports/evidence/m11/web-screen-flakes/commands.md` |
| A | `reports/evidence/m11/web-screen-flakes/design-review.md` |
| A | `reports/evidence/m11/web-screen-flakes/scope.md` |
| M | `web/scripts/display-gate-selectors.test.mjs` |
| M | `web/scripts/screen-card-coverage.mjs` |
| M | `web/scripts/screen-card-coverage.test.mjs` |
| M | `web/src/AppRoot.test.tsx` |

여덟 전부 scope.md 의 `in_scope` 안이다 — `comm -23 <복원목록> <in_scope>` **빈 출력**. out_of_scope
로 적은 경로는 하나도 들어오지 않았다. 특히 공유 파일 둘(`web/config/gate-tests.txt` ·
`web/vite.config.ts`)과 `web/src/screens/screenVocabulary.test.tsx`(마운트 목록)는 **편집 0**
이다. 반대 방향(in_scope 인데 안 건드린 것)이 셋 있는 것은 정상이다 — 계약이 「움직일 수 있는
자리」를 적고 실측이 「움직인 자리」를 적는다.

**빈 등식은 그 자체로 건전성의 증거가 아니다.** 이 목록은 `git add` 한 경로에서 나온 것이 아니라
`git diff --name-status` 의 기계 산출이므로, 「내가 커밋하지 않아도 들어오는 경로」가 있으면 그것도
여기 선다. 지금 여덟 가운데 그런 것은 없다(전부 이 레인의 커밋이 만든 변경이고, 하네스 레인 변경
0 줄).

## 복원 원천의 전제

- **조상**: `git merge-base --is-ancestor 94ee445b 1457e9fe` 가 참이다.
- **그 SHA 에 이 slice 가 없다**: 첫 산출물 커밋(`e0b121a0`)이 base 의 후손이고 base 에 포함되지
  않는다.
- **원천을 바꾸면 다시 센다**: 이 브랜치는 main 을 병합하지 않았으므로 원천은 착수 base 그대로다.
  머지 전에 main 을 병합하면 그 **병합 커밋 SHA**(고정 — 움직이는 `origin/main` 이 아니다)가
  원천이 되고, 그때 **셋째 단언**을 잰다(복원 대상 blob 이 두 원천에서 같은가). 지금 측정으로는
  `origin/main`(`32dead40`)이 복원 대상 여덟 가운데 **어느 것도 건드리지 않았다** —
  `git diff --name-only 94ee445b..origin/main` 이 `README.md`·`milestone-10.md`·`milestone-11.md`
  셋뿐이다.

## 실측 HEAD 와 판정 SHA 사이

evidence 커밋은 언제나 실측 뒤에 오므로 **실측 HEAD == 판정 SHA 가 되는 일은 없다**. 봐야 하는
것은 그 사이에 **되돌림 대상이 움직였는가**이고, 여기서 움직인 것은 전부 다음 전칭식에 든다 —
**실측 HEAD 이후에 변경된 복원 대상은 모두 복원 원천에 없는 신설 파일이다**(그래서 복원이 그것을
지우는 동작은 실측 당시와 같다). 열거와 「원천에 없음」 확인은 verifier 가 한다.
`git rev-list --merges <실측 HEAD>..<판정 SHA>` 도 **빈 출력**이다(이 브랜치는 병합을 담지 않는다).

## 실측 — 버릴 clone 에서 ①~⑥

`--no-hardlinks` clone(`feature/m11-web-screen-flakes`, HEAD = 실측 HEAD)에서 복원을 끝까지
실행했다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 걷힌 수 | **D 3 · M 5 = 8** — 목록과 같다(신설 셋이 지워지고 수정 다섯이 되돌아간다) |
| ③ | 복원 대상의 base 대비 diff | **0 줄** |
| ③′ | **트리 동일성**(전수, 경로 한정 아님) | `git diff --name-only 94ee445b` = **0 파일** — 복원이 트리를 base 와 같게 만든다 |
| ④ | compile — `npm run typecheck` | **0** |
| ⑤ | test — `npm run test` | **0** |
| ⑥ | 게이트 — `gate:src-tree` · `gate:boundary-canary` · `lint` · `gate:tests` | 전부 **0**. `gate:tests` 는 **첫 호출에서 한 번 붉었다** — 아래 |

갈음은 「HEAD 초록」이 아니라 ③′ **트리 동일성**으로 선다.

### ⑥ 의 한 번 붉음 — 이 slice 가 고치는 바로 그 결함이다

복원된 트리는 **base** 이고, base 의 `npm run test` 는 측정된 재현율 **9.6% + 9.6%** 로 불안정하다
(§commands.md). `gate:tests` 는 **같은 명령에 JSON reporter 를 붙여 한 번 더 돌리는** step 이라 그
불안정을 그대로 물려받는다. 그래서 복원 뒤 그 게이트가 때때로 붉은 것은 복원의 결함이 아니라
**복원이 되돌린 결함이 돌아온 것**이다. 같은 트리에서 이어 돌린 `gate:tests` 8회가 전부 초록이고,
전건을 **26회** 돌려 서명이 실제로 다시 나는지 따로 쟀다 —
`TOTAL=26 A=0 B=4 OTHER=0 GREEN=22`. **서명 B 가 15.4% 로 돌아왔다**(수정 뒤 52회에서는 0 이었다).
A 는 26회에 0 인데 그것은 닫혔다는 뜻이 아니다 — base 의 A 는 52회에 5회였고 26회에 0 이 나올
확률이 작지 않다.

첫 붉음의 로그는 같은 경로를 덮어쓰는 반복문 때문에 남지 않았다 — 그래서 **그 한 건의 서명은
분류하지 못했다**. 위 26회가 그 자리를 갈음한다. 지어내지 않는다.

**보완 경로 — 실측했다.** 되돌림이 의도라면 ②의 두 수정도 함께 되돌아간다. 불안정을 되돌리고
싶지 않다면 복원 목록에서 `web/scripts/display-gate-selectors.test.mjs` 와
`web/src/AppRoot.test.tsx` 둘을 **빼고** 복원한다. 둘째 clone 에서 여섯만 복원해 실행했다 —
남은 변경이 정확히 그 둘이고(`git diff --name-only <base>` = 두 파일),
`typecheck`·`lint`·`test`·`gate:tests` 가 전부 **exit 0** 이다. 그 둘은 ①(모집단 게이트)과 파일도
술어도 겹치지 않으므로 코드 의존이 0 이다.
