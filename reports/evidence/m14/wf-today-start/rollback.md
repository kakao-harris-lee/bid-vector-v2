# M14 / wf-today-start (14B) — 되돌리기

```
실측 HEAD: 38053140f649e901a0b7d0e6df1516d5bed8d4b7
base:      056f405c9e5659b9a068288f1104f9e04a6de016
```

## 어떻게 되돌리나

range revert 가 아니라 **in_scope 경로 한정 복원**이다. 복원 목록은 손으로 적은 열거가 아니라
`git diff --name-only <base>..HEAD` 의 **기계 산출**이고, 라운드마다 다시 뽑는다.

```
git diff --name-only 056f405c..HEAD | sort > /tmp/rb-changed.txt
git restore --source=056f405c --staged --worktree -- $(cat /tmp/rb-changed.txt | tr '\n' ' ')
```

**복원 목록 등식**: 기계 산출과 되돌린 집합이 같다(`comm -23` · `comm -13` 둘 다 빈 출력).
목록은 **20 경로**다 — 산출물 15 · evidence 5.

**재측정이다**(팀장 추가 지시로 산출물 커밋이 더 왔다). 앞 라운드의 실측을 옮기지 않고 새 HEAD 에서
처음부터 다시 쟀다 — 늘어난 넷은 `web/e2e/railMeasure.ts` · `web/e2e/journey/layout-rails.spec.ts` ·
`web/config/journey-specs.txt` · `reports/evidence/.../rollback.md` 다.

**공유 파일 주의**: 이 slice 의 복원 목록에 **다른 레인과 겹치는 파일이 없다**. `web/e2e/layout.spec.ts`
와 `web/e2e/journey/touch-target.spec.ts` 는 팀장 판정으로 **14B 소유**이고 14C·14D 가 만지지 않는다.
머지 뒤에 되돌릴 일이 생기면 그때는 PR revert 다(위 복원은 머지 전 경로다).

## 실측 — 버릴 clone 에서 ①~⑥

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | `git restore …` 종료 코드 | **0** |
| ② | 되돌린 뒤 `git status --porcelain` 의 상태 계수 | **D 7 · M 13**(= 신설 7 + 수정 13, 복원 목록 20 과 같다) |
| ③ | 복원 목록 한정 `git diff <base>` 줄 수 | **0** — 그리고 **트리 전체** `git diff <base>` 도 빈 출력이다(**트리 동일성**) |
| ④ | 컴파일 — `npm run typecheck` | exit **0** |
| ⑤ | test — `npm run test` | exit **0** · 1669 통과(이 slice 가 더한 49 가 빠진 수다) |
| ⑥ | 게이트 — `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` | 전부 exit **0** |

**e2e 두 레인(W-11 · W-13)은 트리 동일성으로 갈음한다.** 되돌린 트리가 base 와 **바이트까지 같고**
(③ 의 전체 diff 빈 출력), base 는 머지된 main 커밋이다. 「HEAD 가 초록이니까」가 아니라 **같은
트리이기 때문**이다.

## 완료의 계수 — 내 표지 0 · 남의 표지 보존

종료 코드가 아니라 **항목 집합**으로 센다(어휘 grep 이 아니라 이 slice 가 세운 식별자 전수다).

| 축 | 항목 | 되돌린 뒤 |
| --- | --- | --- |
| 내 표지(13) | `notice-amount-compact` · `notice-qualification` · `notice-aside` · `chip-all` · `onboarding-section-` · `onboarding-preview` · `compactAmountTextOf` · `DEADLINE_SOON_DAYS` · `searchState` · `SHIPPED_RAIL_FLOOR` · `railViolations` · `measureRails` · `layout-rails` | **전부 0 파일** |
| 남의 표지(9) | `notice-amount` 3 · `notice-agency` 3 · `chip-agency` 2 · `onboarding-step-body` 2 · `TWO_COLUMN_ATTRIBUTE` 3 · `koreanAmountTextOf` 5 · `TOUCH_TARGET_SCREENS` 3 · `absenceTextOf` 18 · `touchMeasure` 5 | **전부 보존** |

`layout-rails` 가 0 인 것이 **등록부까지 되돌아갔다**는 뜻이다 — spec 파일과 `journey-specs.txt` 의
줄이 함께 사라져야 여정 레인의 집합 등식이 되돌린 트리에서도 선다.

## 실측 HEAD 와 판정 SHA 사이

이 파일을 담은 커밋이 실측 뒤에 온다. 그래서 verifier 가 보는 것은 「실측 HEAD == 판정 SHA」가
아니라 **그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 목록>
```

이 출력에 나오는 경로는 **전부 원천(base `056f405c`)에 없는 신설 파일**이다 — 이 slice 의 evidence
디렉터리(`reports/evidence/m14/wf-today-start/`)뿐이고, 그 디렉터리는 base 에 존재하지 않는다.
열거와 원천 부재 확인은 verifier 가 한다(이 문장은 전칭식이다).

`git rev-list --merges <실측 HEAD>..<판정 SHA>` 도 빈 출력이다 — 실측 뒤에 main 병합이 오지 않았다.
