# M9 / budget-clear 되돌림

**실측 HEAD: `f5cbecba`**(두 번째 `origin/main` 병합 커밋 = 지금 브랜치 head).
**복원 원천은 그 병합이 받은 main `4953d743`** 다 — 아래 「원천이 바뀐 이유」. 앞선 실측들
(`a8120fb5`·`ae6f4044`·`a246585e`, 원천 `6c38fb32`)은 옮기지 않고 원천을 올려 **다시 쟀다**.
앞 라운드들의 실측(`c36a01e1` · `be1026dc`)은 옮기지 않았다 — 목록·수·트리 등식을 **원천을 바꿔 다시 쟀다**
(cr L-6 · v L-1 은 그 라운드에서 이미 받았다).

## 원천이 바뀐 이유 — 병합 뒤 base 에서 복원하면 남의 줄을 지운다

이 브랜치는 `a8120fb5` 에서 main(`6c38fb32`)을, **`f5cbecba` 에서 다시 main(`4953d743`)** 을 받았다. base 나
옛 병합 SHA 에서 복원하면 그 사이 들어온 **다른 레인의 작업**이 함께 사라진다 — PR #122(m9-prod-tools) ·
PR #126(CVE apt 하한) · **PR #125(m9-screen-display)**. 그래서 원천을 **가장 최근에 받은 main 의 고정 SHA
`4953d743`** 로 올린다(움직이는 `origin/main` 을 쓰지 않는다).

- **전제 단언**: `4953d743` 에 이 slice 가 **없다** — `git merge-base --is-ancestor 75472895 4953d743` 이
  실패한다(첫 산출물 커밋 미포함). 그래서 그 SHA 로의 복원이 「내 것만 지우고 남의 것은 남긴다」가 된다.
- **첫 병합(`a8120fb5`)이 내 본문을 접지 않았다**: `git diff 6c38fb32..a8120fb5` 의 patch-id 가 병합 **전**
  slice diff(`79f3e5c4..34ddda2b`)와 **같다**(`d3d2ac3c…`) · 경로 18 · main 과의 교집합 **0**.
- **둘째 병합(`f5cbecba`)은 교집합이 0 이 아니다** — PR #125 가 내 파일 셋을 함께 만졌다
  (`web/src/display/editSessionDisplay.ts`·`.test.ts` · `web/src/screens/SettingsScreen.test.tsx`). 그래서
  patch-id 등식이 성립하지 않고(한 파일에 양쪽 줄이 함께 있다) **합집합 보존을 계수로 쟀다** — 아래 절.

## 복원 목록 — 기계 산출

```
git diff --name-only --diff-filter=M 4953d743..HEAD   # 되돌릴 수정
git diff --name-only --diff-filter=A 4953d743..HEAD   # 지울 추가
```

**추가 쪽은 고정 수로 적지 않는다.** 그 목록은 evidence 커밋마다 길어지고, 그래서 되돌리는 명령이
**실행 시점에 재산출**한다. 실측 HEAD `f5cbecba` 에서의 값은 **수정 13 · 추가 5** 였다(추가 넷이 이 slice 의
evidence 이고, 이 문서를 담은 evidence 커밋 뒤에는 그 수가 다시 바뀐다 — 절차는 그대로 맞다).

수정 13 — `app/src/main/kotlin/bidvector/app/http/StrategyEditRequests.kt` ·
`app/src/main/kotlin/bidvector/app/wiring/StrategyFieldValue.kt` ·
`app/src/test/kotlin/bidvector/app/architecture/EditableFieldVocabularyGateTest.kt` ·
`config/quality/gate-tests.properties` · `docs/discovery/capability-map.md` · `milestone-6.md` ·
`openapi/bidvector-operator-api.yaml` · `reports/evidence/m6/closure/open-inventory.md` ·
`web/e2e/journey/onboarding.spec.ts` · `web/src/api/generated/operator-api.ts` ·
`web/src/display/editSessionDisplay.test.ts` · `web/src/display/editSessionDisplay.ts` ·
`web/src/screens/SettingsScreen.test.tsx`

추가 — `app/src/test/kotlin/bidvector/app/http/StrategyBudgetClearEndpointTest.kt` 와 이 slice 의 evidence
디렉터리(`reports/evidence/m9/budget-clear/**`, 통째로 지운다).

range revert 를 쓰지 않는다 — 공유 작업 트리에서 다른 레인의 커밋이 범위에 들어온다.

## 되돌리는 명령

```
MOD=$(git diff --name-only --diff-filter=M 4953d743..HEAD)
ADD=$(git diff --name-only --diff-filter=A 4953d743..HEAD)
git restore --source=4953d743 --staged --worktree -- $MOD
rm -f $ADD && rmdir reports/evidence/m9/budget-clear
```

**`reports/evidence/m9` 는 지우지 않는다.** 앞 판의 명령은 그 상위 디렉터리까지 `rmdir` 했는데, 병합으로
`reports/evidence/m9/prod-tools`(다른 레인의 evidence 넷)가 그 안에 들어왔다 — 실측에서 그 `rmdir` 이 **exit 1**
로 거부됐고(디렉터리가 비어 있지 않다) 그것이 올바른 거동이다. 명령에서 그 인자를 뺐다: 지우는 것은 **내 slice
디렉터리 하나**뿐이다.

**`PR` 머지 뒤의 되돌림은 PR revert 가 정답이다** — 이 명령은 머지 **전** 브랜치에서 되돌리는 절차다.
공유 파일(`capability-map.md` · `milestone-6.md` · `open-inventory.md` · `gate-tests.properties` ·
`SettingsScreen.test.tsx`)은 그 원천 선택이 특히 중요하다(위 「원천이 바뀐 이유」).

## 「되돌림 대상이 움직였는가」 — 이 술어를 어떻게 읽는가

verifier 가 보는 것은 `git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 경로들>` 이다. 복원 목록이
**이 slice 의 evidence 디렉터리를 담으므로** 그 술어를 문면 그대로 적용하면 영원히 비지 않는다 — evidence
커밋은 실측보다 **항상 뒤**에 온다. 그래서 두 쪽을 나눠 적는다.

- **산출물 경로**(evidence 디렉터리 제외): `git diff --name-only f5cbecba..<판정 SHA> -- <그 경로들>` 이
  **빈 출력**이어야 하고, 실측 HEAD 뒤의 커밋이 evidence 뿐이라 성립한다.
- **evidence 디렉터리**: 이 술어에서 **뺀다**. 되돌림이 그 디렉터리를 통째로 지우므로 내용이 무엇이든 결과가
  같다(`rm` 은 파일 내용을 보지 않는다).

## 임시 clone 실측 — ①~⑥

저장소 밖 임시 clone 을 `f5cbecba` 로 체크아웃해 위 명령을 그대로 돌렸다.

| # | 무엇 | 실측 |
| --- | --- | --- |
| ① | 명령 종료 코드 | `git restore` exit 0 · `rm -f` exit 0 · 내 디렉터리 `rmdir` **exit 0**(상위 `m9` 를 함께 지우려던 앞 판 인자는 exit 1 로 거부됐다 — 남의 evidence 가 그 안에 있다. 그 인자를 뺀 뒤 깨끗하다) |
| ② | 되돌린 수 | 수정 **13** · 추가 제거 **5** (기계 목록과 같은 수) |
| ③ | 받은 main 대비 diff | `git diff 4953d743 --stat` **빈 출력** · 미추적 잔여 **0** |
| ④ | 컴파일 | **트리 동일성으로 갈음** — 아래 |
| ⑤ | test | **트리 동일성으로 갈음** |
| ⑥ | 게이트 | **트리 동일성으로 갈음** |

**트리 동일성**: 되돌린 트리의 해시가 **그 main 의 트리와 같다**
(`git write-tree` == `git rev-parse 4953d743^{tree}` — 둘 다 `a6d8641d…`). 되돌린 트리가 `4953d743` 과
**바이트까지 같은 트리**이므로 ④⑤⑥ 을 다시 돌리는 것은 그 커밋을 다시 재는 것과 같다. `4953d743` 은 `main`
의 병합 커밋이고 그 커밋에서 CI 가 돌았다. 갈음의 근거는 「HEAD 가 초록이다」가 아니라 **트리가 같다**는 것이다.

## 합집합 보존 — PR #125 와 같은 파일 셋을 만진 병합

둘째 병합은 **겹치는 파일이 셋**이다(PR #125 가 금액·시각 표시를 고치며 같은 파일을 만졌다). 충돌은 0 이지만
「충돌 0 자동 병합이 같은 본문을 접는다」가 이 저장소의 알려진 함정이라, 병합 **전** 양쪽 표지를 세고 병합
**뒤** 합집합이 되는지를 쟀다.

| 표지 | 내 쪽(병합 전) | #125(병합 전) | 병합 뒤 |
| --- | --- | --- | --- |
| `editSessionDisplay.ts` — `FIELD_ACCEPTS_NULL` / `AmountWonField` / `amountWon: null` / 「비우면 그 금액대를」 | 3 / 2 / 1 / 1 | 0 | **3 / 2 / 1 / 1** |
| `editSessionDisplay.ts` — `amountQualifierTextOf` / `amountTextOf` / `moneyDisplay` import | 0 | 2 / 2 / 1 | **2 / 2 / 1** |
| `editSessionDisplay.test.ts` — 내 test 셋 · `FIELD_ACCEPTS_NULL` | 1·1·1 / 4 | 0 | **1·1·1 / 4** |
| `editSessionDisplay.test.ts` — 「부가세 포함 · 운영자 선언값」 / 「단위를 부속 칸이 되풀이했다」 | 0 | 1 / 1 | **1 / 1** |
| `SettingsScreen.test.tsx` — 내 비우기 test | 1 | 0 | **1** |
| `SettingsScreen.test.tsx` — 「부가세 별도 · 운영자 선언값」 / 「이 화면에 남았다」 | 0 | 1 / 1 | **1 / 1** |
| `it(` 수 — `editSessionDisplay.test.ts` / `SettingsScreen.test.tsx` | 33 / 27 | 30 / 26 | **33 / 27** |

- **중복 정의 0**: `FIELD_ACCEPTS_NULL`·`AmountWonField`·`budgetView`·`valueBodyOf`·`sentValueView`·`SLOT_INPUT`·
  `FIELD_VALUE_SLOT` 각각 **정확히 1 회** 선언. import 줄 셋, 중복·고아 0.
- **고아 주석 0**: 세 파일에서 단독 `/**` 와 단독 `*/` 의 수가 같다(12·10·7).
- **#125 의 본문이 접히지 않았다**: `budgetView` 가 그들의 판 그대로다(`amountTextOf(won, currency)` ·
  `amountQualifierTextOf(...)`) — 내 앞 판의 `parts.join(' · ')` 꼴은 남아 있지 않다.
- **양쪽 test 가 둘 다 돈다**: 병합 뒤 `npm run test` **38 파일 · 761 test 전부 통과**이고, 겹치는 test 파일
  둘은 main 의 `web/config/gate-tests.txt` 에 등재돼 있어 `gate:tests` 가 「수집에 있고 skipped/todo 0」까지
  단언한다.

**계수 대조**(내 표지 0 · 남의 표지 보존):

- 내 표지 — `m9-budget-clear` 를 담은 소스·설정 파일 **0** · `acceptsClearing` 를 담은 파일 **0** ·
  `editSessionDisplay.ts` 의 `FIELD_ACCEPTS_NULL` **0**.
- 남의 표지 — 여정 ① 의 `D-7E2b-9` 주석 **2 줄 남음** · `open-inventory.md` 의 「§4 후속」 **2 줄 남음** ·
  내가 더한 §5~§7 절 **0 절**(그 셋은 내 것이다).
- **병합으로 들어온 남의 것이 전부 남았다** — PR #125 의 `amountTextOf` **2 회** · 「부가세 포함/별도」 문면
  **각 1** · `reports/evidence/m9/screen-display` **넷** · PR #122 의 `m9/prod-tools` **넷** ·
  PR #126 의 `CVE-2026-109518` 파일 **넷**. 원천을 base 나 옛 병합 SHA 로 두었다면 사라졌을 자리다.

## clean-tree 게이트

`git status --porcelain -- <in_scope 경로 개별 인자>` 빈 출력. 양성 대조는 **비파괴 절삭**으로 한 번:
편집 중인 파일 하나에 한 줄을 더해 같은 명령이 그 파일을 내는 것을 보고 그 줄을 지웠다(`git checkout --` 쓰지
않음 — 미커밋 산출물을 함께 지운다).
