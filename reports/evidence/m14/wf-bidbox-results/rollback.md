# M14 / wf-bidbox-results (14D) — rollback

**실측 HEAD: `9304968fb26f2cae383ad6bd5049eb1fe1f99fa1`** (수정 라운드 2 의 마지막 산출물 커밋).
이 파일을 담은 커밋은 그 뒤에 오므로 두 SHA 는 영원히 다르다 — 재는 것은 **그 사이에 되돌림
대상이 움직였는가**다(아래 「유효성」).

되돌리는 방식은 range revert 가 아니라 **in_scope 경로 한정 복원**이다.

```
git restore --source=056f405c9e5659b9a068288f1104f9e04a6de016 --staged --worktree -- <아래 경로들>
```

## 복원 목록 — 기계 산출

`git diff --name-status 056f405c..9304968f` 의 전수다(손으로 적은 열거가 아니다). 라운드마다
재산출한다.

| 상태 | 경로 |
| --- | --- |
| A | `reports/evidence/m14/wf-bidbox-results/checklist.md` |
| A | `reports/evidence/m14/wf-bidbox-results/commands.md` |
| A | `reports/evidence/m14/wf-bidbox-results/design-review.md` |
| A | `reports/evidence/m14/wf-bidbox-results/rollback.md` |
| A | `reports/evidence/m14/wf-bidbox-results/scope.md` |
| A | `web/scripts/screen-write-reach.mjs` |
| A | `web/scripts/screen-write-reach.test.mjs` |
| M | `docs/discovery/capability-map.md` |
| M | `web/config/gate-tests.txt` |
| M | `web/e2e/journey/touch-target.spec.ts` |
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

전수 25(신설 7 · 수정 18).

## 공유 파일

`docs/discovery/capability-map.md` 는 공유 파일이다. **머지 전이면** 이 slice 의 §12.3 하위 절
하나만 지우면 되고, **머지 뒤면** PR revert 로 되돌린다. 그 절은 이 slice 가 **새로 더한 것**이라
복원이 남의 줄을 지우지 않는다 — 아래 ⑥ 의 계수가 그것을 잰다.

`web/config/gate-tests.txt` · `web/scripts/screen-vocabulary-registry.mjs` 도 공유 파일이고,
이 slice 가 **끝에 더한 줄**만 들고 있다.

## 실측 ①~⑥ — 버릴 clone 에서

`git clone` 한 뒤 `9304968f` 로 체크아웃하고 위 복원을 돌렸다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | `git status --porcelain` 의 D/M 수 | **D 7 · M 18**(복원 목록의 신설 7 · 수정 18 과 같다) |
| ③ | 복원 뒤 그 경로들의 base 대비 diff | **빈 출력** — 트리가 base 와 동일하다 |
| ④ | `npm run typecheck` | **0** |
| ⑤ | `npm run test` | **0**(70 파일 · 1669 통과 — 이 slice 의 증분이 사라진 수) |
| ⑥ | `gate:tests` · `gate:src-tree` · `lint` · `build` | 전부 **0** |

**복원된 트리에서는 ⑤ 가 첫 판에 바로 초록이다.** 판정 트리의 W-6 가 가끔 붉는 것은
`kotlin-read-guard` 의 5초 예산이고, 복원된 트리에는 이 slice 의 무거운 게이트 test 가 없으므로
그 압력도 없다. 바탕의 빚(무거운 script test 의 기본 예산)은 **별도 chore PR** 이 든다.

**완료는 종료 코드가 아니라 계수로 잰다.**

| 계수 | 결과 |
| --- | --- |
| **내 표지 0** — 이 slice 가 들여온 이름이 남았는가 | **0 파일**. 항목 **열**로 센다: 앞의 아홉 + `outletContextSites`(라운드 2 가 더했다). **열 전부 base 계수 0 임을 먼저 확인했다** |
| **남의 표지 보존** — 14A·14E 의 것이 살아 있는가 | **17 파일** (두 단 원시 요소 · 두 축 타일 · 공고 투영) |

**표지 목록을 한 번 고쳤다**(이 라운드의 실측). 처음 센 목록에 `openCaseCountOf` 가 들어 있어
복원 뒤 계수가 1 이었다 — 그런데 그 이름은 **14A 가 지은 상류 어휘**이고(base 계수 2) 이 slice 는
그것을 **옮겼을 뿐**이다. 상류가 이미 쓰는 낱말을 내 표지로 삼으면 그 계수는 내 산출물을 세지
않는다. 그래서 목록에서 빼고, 남은 아홉이 **원천에 없다는 것을 먼저 재고** 다시 셌다.
| 트리 동일성 | 복원 경로 전수에서 base 와 **동일** |

## 유효성 (verifier 가 대조하는 것)

`git diff --name-only 9304968f..<판정 SHA> -- <위 복원 목록의 경로들>` 이 빈 출력이면 유효하다.
한 줄이라도 나오면 **그 경로가 전부 원천(base `056f405c`)에 없는 신설 파일일 때만** 유효하다 —
이 slice 에서 그에 해당하는 것은 `reports/evidence/m14/wf-bidbox-results/**` 뿐이고, 열거와
원천 부재 확인은 verifier 가 한다.

`git rev-list --merges 9304968f..<판정 SHA>` 도 빈 출력이어야 한다(그 사이 main 병합이 오면
실측 자리가 그 병합 커밋이다).

복원 원천을 `056f405c` 에서 바꾸면 **셋째 단언**을 다시 잰다 — 복원 대상 blob 이 착수 base 와 새
원천에서 같은가이고, 다르면 새 원천 기준으로 ①~⑥ 을 다시 센다.
