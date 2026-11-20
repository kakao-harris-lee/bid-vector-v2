# M14 / wf-bidbox-results (14D) — rollback

**실측 HEAD: `be02c94e3c708cbe835f81478329e8646b6de71d`** (이 slice 의 마지막 산출물 커밋).
이 파일을 담은 커밋은 그 뒤에 오므로 두 SHA 는 영원히 다르다 — 재는 것은 **그 사이에 되돌림
대상이 움직였는가**다(아래 「유효성」).

되돌리는 방식은 range revert 가 아니라 **in_scope 경로 한정 복원**이다.

```
git restore --source=056f405c9e5659b9a068288f1104f9e04a6de016 --staged --worktree -- <아래 경로들>
```

## 복원 목록 — 기계 산출

`git diff --name-status 056f405c..be02c94e` 의 전수다(손으로 적은 열거가 아니다). 라운드마다
재산출한다.

| 상태 | 경로 |
| --- | --- |
| A | `reports/evidence/m14/wf-bidbox-results/checklist.md` |
| A | `reports/evidence/m14/wf-bidbox-results/commands.md` |
| A | `reports/evidence/m14/wf-bidbox-results/design-review.md` |
| A | `reports/evidence/m14/wf-bidbox-results/scope.md` |
| A | `web/scripts/screen-write-reach.mjs` |
| A | `web/scripts/screen-write-reach.test.mjs` |
| M | `docs/discovery/capability-map.md` |
| M | `web/config/gate-tests.txt` |
| M | `web/scripts/screen-vocabulary-registry.mjs` |
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

전수 22(신설 6 · 수정 16).

## 공유 파일

`docs/discovery/capability-map.md` 는 공유 파일이다. **머지 전이면** 이 slice 의 §12.3 하위 절
하나만 지우면 되고, **머지 뒤면** PR revert 로 되돌린다. 그 절은 이 slice 가 **새로 더한 것**이라
복원이 남의 줄을 지우지 않는다 — 아래 ⑥ 의 계수가 그것을 잰다.

`web/config/gate-tests.txt` · `web/scripts/screen-vocabulary-registry.mjs` 도 공유 파일이고,
이 slice 가 **끝에 더한 줄**만 들고 있다.

## 실측 ①~⑥ — 버릴 clone 에서

`git clone` 한 뒤 `be02c94e` 로 체크아웃하고 위 복원을 돌렸다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | `git status --porcelain` 의 D/M 수 | **D 6 · M 16**(복원 목록의 신설 6 · 수정 16 과 같다) |
| ③ | 복원 뒤 그 경로들의 base 대비 diff | **빈 출력** — 트리가 base 와 동일하다 |
| ④ | `npm run typecheck` | **0** |
| ⑤ | `npm run test` | **0**(70 파일 · 1669 통과 — 이 slice 의 증분이 사라진 수) |
| ⑥ | `gate:tests` · `gate:src-tree` · `lint` · `build` | 전부 **0** |

**⑤ 의 첫 판이 한 번 붉었다.** `scripts/kotlin-read-guard.test.mjs` 1건이고 재실행에서 통과했다
(단독 25/25 · 전건 재실행 exit 0 · 70 파일 1669 통과). **복원된 트리는 그 경로들에 대해 base 와
바이트 동일**이므로 이 실패는 base 에도 있는 것이고 이 slice 가 만든 것이 아니다 — 같은 서명이
판정 트리의 W-7 첫 판에서도 한 번 나왔다(`commands.md`).

**완료는 종료 코드가 아니라 계수로 잰다.**

| 계수 | 결과 |
| --- | --- |
| **내 표지 0** — 이 slice 가 들여온 이름이 남았는가 | **0 파일** (항목 집합으로 센다: 「전체」 축 · 새 게이트 · 새 탭 id · 배지 조회 · 공고 행 부재 문면) |
| **남의 표지 보존** — 14A·14E 의 것이 살아 있는가 | **17 파일** (두 단 원시 요소 · 두 축 타일 · 공고 투영) |
| 트리 동일성 | 복원 경로 전수에서 base 와 **동일** |

## 유효성 (verifier 가 대조하는 것)

`git diff --name-only be02c94e..<판정 SHA> -- <위 복원 목록의 경로들>` 이 빈 출력이면 유효하다.
한 줄이라도 나오면 **그 경로가 전부 원천(base `056f405c`)에 없는 신설 파일일 때만** 유효하다 —
이 slice 에서 그에 해당하는 것은 `reports/evidence/m14/wf-bidbox-results/**` 뿐이고, 열거와
원천 부재 확인은 verifier 가 한다.

`git rev-list --merges be02c94e..<판정 SHA>` 도 빈 출력이어야 한다(그 사이 main 병합이 오면
실측 자리가 그 병합 커밋이다).

복원 원천을 `056f405c` 에서 바꾸면 **셋째 단언**을 다시 잰다 — 복원 대상 blob 이 착수 base 와 새
원천에서 같은가이고, 다르면 새 원천 기준으로 ①~⑥ 을 다시 센다.
