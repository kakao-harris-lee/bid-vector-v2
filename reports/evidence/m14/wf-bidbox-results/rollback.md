# M14 / wf-bidbox-results (14D) — rollback

**실측 HEAD: `35b265cb37d744d47920a88256858c6ecb172141`** (머지 차례의 마지막 산출물 커밋).
이 파일을 담은 커밋은 그 뒤에 오므로 두 SHA 는 영원히 다르다 — 재는 것은 **그 사이에 되돌림
대상이 움직였는가**다(아래 「유효성」).

되돌리는 방식은 range revert 가 아니라 **in_scope 경로 한정 복원**이다.

**복원 원천이 바뀌었다** — 착수 base 가 아니라 **머지한 main `335c976e`** 다. 셋째 단언을 다시
재서 그렇게 정했다(아래 「원천을 바꾼 까닭」).

```
git restore --source=335c976ecfbbd737d8689b782f5c31a731879f15 --staged --worktree -- <아래 경로들>
```

## 원천을 바꾼 까닭 — 셋째 단언

복원 대상 blob 이 **착수 base 와 새 원천에서 같은가**를 쟀다. **다섯이 다르다**:
`docs/discovery/capability-map.md` · `web/config/gate-tests.txt` ·
`web/e2e/journey/touch-target.spec.ts` · `web/scripts/screen-vocabulary-registry.mjs` ·
`web/scripts/rail-screens.mjs`(**base 에 아예 없다** — 14B 가 만들었다).

그래서 base 에서 복원하면 14B·14C 의 일이 함께 지워진다. 원천을 `335c976e` 로 바꾸고 ①~⑥ 을
그 기준으로 **다시 셌다**.

## 복원 목록 — 기계 산출

`git diff --name-status 335c976e..35b265cb` 의 전수다(손으로 적은 열거가 아니다). 라운드마다
재산출한다.

| 상태 | 경로 |
| --- | --- |
| A | `reports/evidence/m14/wf-bidbox-results/checklist.md` |
| A | `reports/evidence/m14/wf-bidbox-results/commands.md` |
| A | `reports/evidence/m14/wf-bidbox-results/design-review.md` |
| A | `reports/evidence/m14/wf-bidbox-results/rollback.md` |
| A | `reports/evidence/m14/wf-bidbox-results/scope.md` |
| A | `web/scripts/screen-write-reach-bypass.test.mjs` |
| A | `web/scripts/screen-write-reach-inject.test.mjs` |
| A | `web/scripts/screen-write-reach.mjs` |
| A | `web/scripts/screen-write-reach.test.mjs` |
| M | `docs/discovery/capability-map.md` |
| M | `web/config/gate-tests.txt` |
| M | `web/e2e/journey/touch-target.spec.ts` |
| M | `web/scripts/rail-screens.mjs` |
| M | `web/scripts/screen-vocabulary-registry.mjs` |
| M | `web/src/AppShell.tsx` |
| M | `web/src/display/bidBoxDisplay.test.ts` |
| M | `web/src/display/bidBoxDisplay.ts` |
| M | `web/src/display/caseDisplay.ts` |
| M | `web/src/display/myBidResultDisplay.ts` |
| M | `web/src/display/resultDisplay.test.ts` |
| M | `web/src/display/resultDisplay.ts` |
| M | `web/src/screens/BidBoxCard.tsx` |
| M | `web/src/screens/BidBoxScreen.test.tsx` |
| M | `web/src/screens/BidBoxScreen.tsx` |
| M | `web/src/screens/InboxScreen.tsx` |
| M | `web/src/screens/ResultCard.tsx` |
| M | `web/src/screens/ResultsScreen.test.tsx` |
| M | `web/src/screens/ResultsScreen.tsx` |

전수 28(신설 9 · 수정 19).

## 공유 파일

`docs/discovery/capability-map.md` 는 공유 파일이다. **머지 전이면** 이 slice 의 §12.3 하위 절
하나만 지우면 되고, **머지 뒤면** PR revert 로 되돌린다. 그 절은 이 slice 가 **새로 더한 것**이라
복원이 남의 줄을 지우지 않는다 — 아래 ⑥ 의 계수가 그것을 잰다.

`web/config/gate-tests.txt` · `web/scripts/screen-vocabulary-registry.mjs` 도 공유 파일이고,
이 slice 가 **끝에 더한 줄**만 들고 있다.

## 실측 ①~⑥ — 버릴 clone 에서

`git clone` 한 뒤 `35b265cb` 로 체크아웃하고 위 복원을 돌렸다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | `git status --porcelain` 의 D/M 수 | **D 9 · M 19**(복원 목록의 신설 9 · 수정 19 와 같다) |
| ③ | 복원 뒤 그 경로들의 **새 원천** 대비 diff | **빈 출력** — 트리가 `335c976e` 와 동일하다 |
| ④ | `npm run typecheck` | **0** |
| ⑤ | `npm run test` | **0**(**71 파일 · 1792** — 이 slice 의 증분이 사라지고 14B·14C 의 것은 남은 수) |
| ⑥ | `gate:tests` · `gate:src-tree` · `lint` · `build` | 전부 **0** |

**⑤ 가 첫 판에 바로 초록이다.** 라운드 3 에서 게이트 test 의 비용을 절반 아래로 줄인 뒤로는
판정 트리의 W-6 도 3/3 초록이다 — 복원 트리와 판정 트리가 이 축에서 더 이상 갈리지 않는다.

**완료는 종료 코드가 아니라 계수로 잰다.**

| 계수 | 결과 |
| --- | --- |
| **내 표지 0** — 이 slice 가 들여온 이름이 남았는가 | **0 파일**. 항목 **열셋**으로 센다(`outletCacheKey` 는 머지 차례에 없앴다). **전부 원천 계수 0 임을 먼저 확인했다**. 더해서 **레일 래칫이 14B 의 두 이름으로 돌아왔다**(내 `bid-box` 한 줄만 빠진다) |
| **남의 표지 보존** — 14A·14E 의 것이 살아 있는가 | **19 파일**(14B·14C 가 더한 자리를 포함해 늘었다) |
| **14B·14C 의 산출물 보존** | main 의 diff 에서 **기계로** 뽑은 파일 **18 개가 `335c976e` 와 바이트 동일**, 다름 0 |
| **§12.3 글자** | (ac) **1 → 0** · (t)~(ab) **아홉 전부 남는다** |

**표지 목록을 한 번 고쳤다**(이 라운드의 실측). 처음 센 목록에 `openCaseCountOf` 가 들어 있어
복원 뒤 계수가 1 이었다 — 그런데 그 이름은 **14A 가 지은 상류 어휘**이고(base 계수 2) 이 slice 는
그것을 **옮겼을 뿐**이다. 상류가 이미 쓰는 낱말을 내 표지로 삼으면 그 계수는 내 산출물을 세지
않는다. 그래서 목록에서 빼고, 남은 아홉이 **원천에 없다는 것을 먼저 재고** 다시 셌다.
| 트리 동일성 | 복원 경로 전수에서 base 와 **동일** |

## 유효성 (verifier 가 대조하는 것)

`git diff --name-only 35b265cb..<판정 SHA> -- <위 복원 목록의 경로들>` 이 빈 출력이면 유효하다.
한 줄이라도 나오면 **그 경로가 전부 원천(base `056f405c`)에 없는 신설 파일일 때만** 유효하다 —
이 slice 에서 그에 해당하는 것은 `reports/evidence/m14/wf-bidbox-results/**` 뿐이고, 열거와
원천 부재 확인은 verifier 가 한다.

`git rev-list --merges 35b265cb..<판정 SHA>` 는 **빈 출력이 아니다 — 여섯이다**. 그 가운데
하나는 조상을 잇는 `-s ours` 머지이고, 나머지 다섯은 **main 자신의 머지 커밋**(#180·#181·#182
계열)이 조상이 이어지면서 그 범위에 들어온 것이다.

**그래도 실측은 무효가 되지 않는다.** 이 머지는 **트리 동일성**을 지닌다 — `git diff <실측 뒤
HEAD> <판정 SHA>` 가 **빈 출력**이고, 복원 경로 집합도 **28 로 같다**. 되돌림 대상이 움직이지
않았으므로 복원 원천(`335c976e`)도 그대로다.

### 조상을 잇는 머지에 대하여 (사실 기록)

머지 커밋 `75ae4a52` 는 **부모가 하나**였다. 충돌을 푸는 도중 `git stash` 를 돌렸고 그것이
`MERGE_HEAD` 를 지워서, 뒤이은 커밋이 평범한 단일 부모 커밋으로 기록됐다. **트리는 옳았다** —
충돌을 합집합으로 풀었고 양쪽 내용이 다 들어 있어 계수 확인이 전부 통과했다(그래서 머지 차례
보고까지 드러나지 않았다). 빠진 것은 **조상 관계**뿐이고, 그대로 두면 PR 이 main 을 담지 않은
것으로 보여 같은 변경이 한 번 더 재생된다.

고친 방법은 `git merge --no-ff -s ours origin/main` 이다 — **부모만** 잇고 트리는 건드리지
않는다. 이력을 되쓰지 않았다. 확인한 것 넷: 트리 동일(`git diff` 빈 출력) · 조상 성립
(`merge-base --is-ancestor` exit 0) · 경로 집합 28 로 동일 · 부모 둘.

복원 원천을 또 바꾸면 **셋째 단언**을 다시 잰다 — 복원 대상 blob 이 착수 base 와 새
원천에서 같은가이고, 다르면 새 원천 기준으로 ①~⑥ 을 다시 센다.
