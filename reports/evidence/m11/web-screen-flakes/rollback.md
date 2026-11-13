# M11 / web-screen-flakes — rollback

`git revert` range 가 아니라 **in_scope 경로 한정 복원**이다.

```
git restore --source=fdb4b24cc84f88f238e93e75344dcaa0cc5d7c1a --staged --worktree -- <아래 목록>
```

원천은 **병합한 `origin/main` 의 고정 SHA** 다 — 움직이는 `origin/main` 도 아니고 **내 병합
커밋도 아니다**(그것은 이 slice 를 담고 있어 복원이 no-op 이 된다).

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

- **조상**: `git merge-base --is-ancestor fdb4b24c HEAD` 가 참이다(병합했다).
- **그 SHA 에 이 slice 가 없다**: 첫 산출물 커밋이 `fdb4b24c` 의 조상이 아니다.
- **셋째 단언 — 쟀고 하나가 다르다.** 복원 대상 열의 blob 이 착수 base `94ee445b` 와 새 원천
  `fdb4b24c` 에서 같은가. 아홉은 같고(신설 다섯은 양쪽에 없다) **`docs/discovery/capability-map.md`
  하나가 다르다** — 11A 가 §12.3 끝에 (k) 절을 더했다. 그래서 그 파일은 **새 원천 기준으로**
  센다: 복원이 **내 (l) 절을 지우고 11A 의 (k) 절은 남겨야** 한다.
- r0 과 갈리는 자리가 여기다. 원천이 착수 base 였을 때는 전수 트리 동일성이 갈음이었지만, 공유
  파일에 **남의 내용**이 들어온 뒤로는 그 갈음을 쓸 수 없다 — 「내 표지 0」과 「남의 표지 보존」을
  **둘 다** 센다.

## 실측 HEAD 와 판정 SHA 사이

evidence 커밋은 언제나 실측 뒤에 오므로 **실측 HEAD == 판정 SHA 가 되는 일은 없다**. 봐야 하는
것은 그 사이에 **되돌림 대상이 움직였는가**이고, 여기서 움직인 것은 전부 다음 전칭식에 든다 —
**실측 HEAD 이후에 변경된 복원 대상은 모두 복원 원천에 없는 신설 파일이다**(그래서 복원이 그것을
지우는 동작은 실측 당시와 같다). 열거와 「원천에 없음」 확인은 verifier 가 한다.
`git rev-list --merges <실측 HEAD>..<판정 SHA>` 도 **빈 출력**이다(이 브랜치는 병합을 담지 않는다).

## 실측 — 버릴 clone 에서 ①~⑥

실측 HEAD **`f894d49e8ccc7c14c64daaf9a95ed0d0c3c3792b`** · 원천 `fdb4b24c` ·
`--no-hardlinks` clone 에서 복원을 끝까지 실행했다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 걷힌 수 | **D 5 · M 5 = 10** — 목록과 같다(신설 다섯이 지워지고 수정 다섯이 되돌아간다) |
| ③ | 복원 대상의 **원천** 대비 diff | **0 줄**(staged·worktree 둘 다) |
| ③′ | **공유 파일의 두 계수** | 아래 |
| ④ | compile — `typecheck` · `lint` | **0** · **0** |
| ⑤ | test — `npm run test` | **0** |
| ⑥ | 게이트 — `gate:src-tree` · `gate:boundary-canary` · `gate:tests` | **0** · **0** · 첫 호출 **1**, 아래 |

### ③′ 「내 표지 0 · 남의 표지 보존」 — 갈음이 트리 동일성이 아닌 이유

공유 파일(`capability-map.md`)에 **11A 의 내용**이 들어와 있으므로 전수 트리 동일성만으로는
「남의 것을 지우지 않았다」가 서지 않는다. 둘을 따로 센다.

| 계수 | 복원 전 | 복원 후 |
| --- | --- | --- |
| 내 `#### (l) M11/11E` 절 | 1 | **0** |
| 11A `#### (k) M11/11A` 절 | 1 | **1** |
| 내 신설 ID `…CROSS-FILE-VOCAB` | 1 | **0** |
| 11A 고유 문자열 `총수 최댓값` | 3 | **3** |
| §12.3 의 `#### (` 절 수 | 12 | **11** |

보너스로 전수도 쟀다 — 복원 뒤 `git diff --name-only fdb4b24c` 가 **0 파일**이다(복원 대상 밖을
건드리지 않았다는 뜻이고, 위 두 계수를 **대신하지는 않는다**).

### ⑥ 의 한 번 붉음 — 복원이 되돌린 결함이 돌아온 것이다

복원된 트리는 **내 수정이 없는 main** 이다. `gate:tests` 는 전건과 **같은 명령을 한 번 더
돌리는** step 이라 그 불안정을 물려받는다. 이어 돌린 `gate:tests` **6회가 전부 초록**이고, 전건을
**26회** 돌려 분류했다 — `TOTAL=26 A=2 B=3 GREEN=21`. **두 서명이 모두 돌아왔다**(출하판에서는
52회 0·0). 즉 복원의 결함이 아니라 복원이 되돌린 결함이다.

**첫 붉음의 로그는 이번에도 남지 않았다** — 반복문이 같은 변수로 출력을 덮었다(r0 과 **같은
실수**다). 그래서 그 한 건의 서명은 r0 과 마찬가지로 **분류하지 못했고**, 위 26회가 그 자리를
갈음한다. 지어내지 않는다.

**보완 경로**: 불안정을 되돌리고 싶지 않다면 복원 목록에서 ② 의 두 파일
(`display-gate-selectors.test.mjs`·`AppRoot.test.tsx`)을 빼고 복원한다 — r1 에서 실측했다
(남는 변경이 정확히 그 둘이고 `typecheck`·`lint`·`test`·`gate:tests` 가 전부 exit 0).

