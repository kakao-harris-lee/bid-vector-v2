# M11 / web-screen-flakes — rollback

`git revert` range 가 아니라 **in_scope 경로 한정 복원**이다.

```
git restore --source=94ee445b50cafa669af435e651d811cc551c67e8 --staged --worktree -- <아래 목록>
```

실측 HEAD: **이 문서를 담은 커밋**(머지 차례의 마지막 산출물 커밋 직전 자리 — 아래 §실측 HEAD
가 SHA 를 든다). r0 의 실측은 `1457e9fe` 에서 했고 ①~⑥ 과
보완 경로가 아래 표에 있다 — 그 측정은 r1 편집 **앞**이고 원천도 착수 base 였다. 최종 실측은
병합 뒤 **마지막 산출물 커밋**에서 하고, 원천은 병합한 `origin/main` 의 **고정 SHA** 이며
**셋째 단언**(복원 대상 blob 이 착수 base 와 새 원천에서 같은가)을 함께 잰다. `capability-map.md`
는 앞 레인들이 §12.3 에 절을 더하므로 **두 원천에서 다를 전망**이고, 그때 그 파일 기준 계수를
새 원천에서 다시 센다 — 복원이 「내 절만 지우고 남의 절은 남기는가」를 둘 다 세는 것이 그
자리다(원천 = base 였던 r0 에서는 전수 트리 동일성이 갈음이었지만, 공유 파일에 남의 내용이
들어오면 그 갈음을 더는 쓸 수 없다).

## 복원 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 의 기계 산출이고 **라운드마다 재산출한다**. r1 에서 **열**
이 됐다 — r0 의 여덟에 그때는 아직 없던 evidence 둘(`checklist.md`·`rollback.md`)이 들어왔다
(verifier r1 장부층 지적).

| 상태 | 경로 |
| --- | --- |
| M | `docs/discovery/capability-map.md` |
| A | `reports/evidence/m11/web-screen-flakes/` 다섯 — `scope`·`design-review`·`commands`·`checklist`·`rollback` |
| M | `web/scripts/display-gate-selectors.test.mjs` |
| M | `web/scripts/screen-card-coverage.mjs` |
| M | `web/scripts/screen-card-coverage.test.mjs` |
| M | `web/src/AppRoot.test.tsx` |

열 전부 scope.md 의 `in_scope` 안이다 — `comm -23 <복원목록> <in_scope>` **빈 출력**. 완전성도 본다(r1 cr F-7) — r0 의 목록은 실측 HEAD
기준 여덟이라 그 뒤 커밋에서 생긴 evidence 둘을 담지 않았고, 적힌 pathspec 을 그대로 돌리면 그
둘이 남았다. 지금 목록은 **판정 SHA 기준 열**이고, 신설 다섯은 복원 원천에 없으므로 복원이 그
다섯을 지운다(`git cat-file -e <원천>:<경로>` 로 확인하는 자리이며 verifier 가 센다). out_of_scope
로 적은 경로는 하나도 들어오지 않았다. 특히 공유 파일 둘(`web/config/gate-tests.txt` ·
`web/vite.config.ts`)과 `web/src/screens/screenVocabulary.test.tsx`(마운트 목록)는 **편집 0**
이다. 반대 방향(in_scope 인데 안 건드린 것)이 셋 있는 것은 정상이다 — 계약이 「움직일 수 있는
자리」를 적고 실측이 「움직인 자리」를 적는다.

**빈 등식은 그 자체로 건전성의 증거가 아니다.** 이 목록은 `git add` 한 경로에서 나온 것이 아니라
`git diff --name-status` 의 기계 산출이므로, 「내가 커밋하지 않아도 들어오는 경로」가 있으면 그것도
여기 선다. 지금 열 가운데 그런 것은 없다(전부 이 레인의 커밋이 만든 변경이고, 하네스 레인 변경
0 줄).

## 복원 원천의 전제

- **조상**: `git merge-base --is-ancestor 94ee445b 1457e9fe` 가 참이다.
- **그 SHA 에 이 slice 가 없다**: 첫 산출물 커밋(`e0b121a0`)이 base 의 후손이고 base 에 포함되지
  않는다.
- **원천을 바꾸면 다시 센다**: 이 브랜치는 main 을 병합하지 않았으므로 원천은 착수 base 그대로다.
  머지 전에 main 을 병합하면 그 **병합 커밋 SHA**(고정 — 움직이는 `origin/main` 이 아니다)가
  원천이 되고, 그때 **셋째 단언**을 잰다(복원 대상 blob 이 두 원천에서 같은가). 지금 측정으로는
  `origin/main`(`32dead40`)이 복원 대상 열 가운데 **어느 것도 건드리지 않았다** —
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

복원된 트리는 **base** 이고 base 의 전건은 재현율 **9.6%+9.6%** 로 불안정하다. `gate:tests` 는
**같은 명령을 한 번 더 돌리는** step 이라 그 불안정을 물려받는다. 즉 복원의 결함이 아니라
**복원이 되돌린 결함이 돌아온 것**이다. 같은 트리에서 이어 돌린 `gate:tests` 8회는 전부 초록이고,
전건 **26회**로 따로 쟀다 — `TOTAL=26 A=0 B=4 GREEN=22`, **서명 B 15.4% 재현**(수정 뒤 52회 0).
A 의 26회 0 은 닫혔다는 뜻이 아니다(base 의 A 는 52회 5회였다).

첫 붉음의 로그는 반복문이 같은 경로를 덮어써 남지 않았고, **그 한 건의 서명은 분류하지 못했다**.
위 26회가 그 자리를 갈음한다. 지어내지 않는다.

**보완 경로 — 실측했다.** 불안정을 되돌리고 싶지 않다면 복원 목록에서 ② 의 두 파일
(`display-gate-selectors.test.mjs`·`AppRoot.test.tsx`)을 **빼고** 복원한다. 둘째 clone 에서
여섯만 복원해 실행했고, 남은 변경이 정확히 그 둘이며
`typecheck`·`lint`·`test`·`gate:tests` 가 전부 **exit 0** 이다. 그 둘은 ① 과 파일도 술어도 겹치지
않는다.
