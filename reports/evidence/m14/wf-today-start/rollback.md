# M14 / wf-today-start (14B) — 되돌리기

```
실측 HEAD: 1a67bfcbd2c5c7df7ed4834c057bd477cc2d0ad5
복원 원천: 9fc1877e0acccad184344a77d485c89a3cadee90   (머지한 main — 착수 base 가 아니다)
착수 base: 056f405c9e5659b9a068288f1104f9e04a6de016
```

## 어떻게 되돌리나

range revert 가 아니라 **in_scope 경로 한정 복원**이다. 복원 목록은 손으로 적은 열거가 아니라
`git diff --name-only <base>..HEAD` 의 **기계 산출**이고, 라운드마다 다시 뽑는다.

```
git diff --name-only 9fc1877e..HEAD | sort > /tmp/rb.txt
git restore --source=9fc1877e --staged --worktree -- $(cat /tmp/rb.txt | tr '\n' ' ')
```

**복원 목록 등식**: 기계 산출과 되돌린 집합이 같다(`comm -23` · `comm -13` 둘 다 빈 출력).
목록은 **실측 HEAD 기준 26 경로**다 — 산출물 20 · evidence 5 · capability map 1. 이 파일을 담은
커밋이 더해져도 판정 SHA 에서 **같은 26** 이다(`rollback.md` 가 이미 목록에 있다).

**원천이 착수 base 가 아니라 머지한 main 이다.** 이 branch 는 `9fc1877e` 를 `--no-ff` 로 병합했고,
그 안에 14C(#181)와 chore(#180)가 들어 있다 — base 로 되돌리면 **남의 것까지 지운다**.

**셋째 단언**(복원 대상 blob 이 착수 base 와 새 원천에서 같은가):
`git diff --name-only 056f405c 9fc1877e -- <복원 목록>` 이 **한 줄**을 낸다 —
`docs/discovery/capability-map.md`. 14C 가 그 사이에 §12.3 (aa) 를 더했기 때문이고, 그래서
**원천을 새 SHA 로 바꾼 것이 옳다**: base 에서 복원하면 (aa) 가 함께 사라진다. 나머지 25 경로의
blob 은 두 SHA 에서 같다. `web/vite.config.ts`(#180)는 이 목록에 **들지 않는다** — 이 slice 가
건드리지 않았다. 어느 SHA 에서 센 수인지 적는
것이 요점이다 — 앞 라운드 보고가 그 말을 빠뜨려 거짓 불일치로 읽혔다(cr L-10).

**공유 파일 주의**: 복원 목록에 다른 레인과 겹치는 파일이 **넷** 있다 — `web/config/gate-tests.txt`
와 `web/scripts/screen-vocabulary-registry.mjs`(꼬리에 줄을 더하는 자리라 14C·14D 와 **덧붙임
충돌**이 난다 · 머지 probe 실측 · 합집합으로 푼다), `web/src/display/noticeDisplay.ts`(계약 소유가
14A 인 파일에 더하기 하나), `web/e2e/capture/usecase-captures.mjs`(세 레인이 쓰는 캡처 도구).
복원은 그 파일들을 base 로 되돌리므로, **머지 뒤**에 되돌릴 일이 생기면 그때는 PR revert 다.

## 실측 — 버릴 clone 에서 ①~⑥

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | `git restore …` 종료 코드 | **0** |
| ② | 되돌린 뒤 `git status --porcelain` 의 상태 계수 | **D 9 · M 17**(= 신설 9 + 수정 17, 복원 목록 26 과 같다) |
| ③ | 복원 목록 한정 `git diff <원천>` 줄 수 | **0** — 그리고 **트리 전체** `git diff <원천>` 도 빈 출력이다(**트리 동일성**) |
| ④ | 컴파일 — `npm run typecheck` | exit **0** |
| ⑤ | test — `npm run test` | exit **0** · 1708 통과(이 slice 가 더한 84 가 빠진 수다 — 1792 → 1708) |
| ⑥ | 게이트 — `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` | 전부 exit **0** |

**재시도가 필요 없었다.** 앞 라운드까지 ⑤⑥ 을 떨어뜨리던 5s 타임아웃 flake 는 **PR #180 으로
닫혔다**(vitest 기본 `testTimeout` 30s) — 이번에는 한 번에 통과했다.

**e2e 두 레인(W-11 · W-13)은 트리 동일성으로 갈음한다.** 되돌린 트리가 원천과 바이트까지 같고
(③ 의 전체 diff 빈 출력), 원천 `9fc1877e` 는 머지된 main 커밋이다. 「HEAD 가 초록이니까」가 아니라 **같은
트리이기 때문**이다.

## 완료의 계수 — 내 표지 0 · 남의 표지 보존

종료 코드가 아니라 **항목 집합**으로 센다(어휘 grep 이 아니라 이 slice 가 세운 식별자 전수다).

| 축 | 항목 | 되돌린 뒤 |
| --- | --- | --- |
| 내 표지(22) | 앞의 열여덟에 `computedDisplaysOf` · `notice-facts` · `b2-notices-default` · `seedWatchCondition` 을 더한 전수 | **전부 0 파일** |
| **14C 의 표지**(7) | `amount-self-entry-note` 2 · `worksheet-agency` 3 · `worksheet-price-card` 2 · `worksheet-record-card` 2 · `worksheet-number` 1 · `worksheet-title` 3 · `worksheet-deadline` 2 | **전부 보존** |
| 그 밖의 표지(9) | `notice-amount` 3 · `notice-agency` 3 · `chip-agency` 2 · `onboarding-step-body` 2 · `TWO_COLUMN_ATTRIBUTE` 3 · `koreanAmountTextOf` 5 · `TOUCH_TARGET_SCREENS` 3 · `absenceTextOf` 19 · `onboarding-not-ready` 2 | **전부 보존** |
| capability map 절 | **(ab) 1 → 0** · (t)~(aa) 여덟은 **각각 1 로 보존** | 등재가 절 단위로 떨어진다 |
| #180 · 14C 의 공유 파일 | `vite.config.ts` 의 `testTimeout` 1 · `PageHeader` 의 제목 표지 1 | **보존** |

**14C 표지는 이름을 지어내지 않았다**: `git diff 056f405c 9fc1877e -- WorksheetScreen.tsx PageHeader.tsx`
에서 `data-testid` 를 기계로 뽑아 썼다. 첫 시도에 손으로 적은 둘(`worksheet-countdown` ·
`worksheet-layout`)은 **존재하지 않는 이름**이라 0 을 냈다 — 그대로 뒀으면 「보존됨」이 공허했다.

`layout-rails` · `railScreenSlots` 가 0 인 것이 **등록부까지 되돌아갔다**는 뜻이다 — spec·술어
파일과 `journey-specs.txt` · `gate-tests.txt` 의 줄이 함께 사라져야 두 레인의 집합 등식이 되돌린
트리에서도 선다. `onboarding-not-ready` 가 base 의 수(2)로 **돌아온 것**이 그 짝이다(이 slice 가
걷은 표지가 복원된다).

## 실측 HEAD 와 판정 SHA 사이

이 파일을 담은 커밋이 실측 뒤에 온다. 그래서 verifier 가 보는 것은 「실측 HEAD == 판정 SHA」가
아니라 **그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 목록>
```

이 출력에 나오는 경로는 **전부 원천(base `056f405c`)에 없는 신설 파일**이다 — 이 slice 의
evidence 디렉터리(`reports/evidence/m14/wf-today-start/`)뿐이고, 그 디렉터리는 base 에 존재하지
않는다. 열거와 원천 부재 확인은 verifier 가 한다(이 문장은 전칭식이다).

`git rev-list --merges <실측 HEAD>..<판정 SHA>` 도 빈 출력이다 — 실측 뒤에 main 병합이 오지 않았다.
