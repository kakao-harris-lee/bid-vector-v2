# M9 / screen-display — rollback

실측 HEAD: `913b7ec5`

## 전제

- base `79f3e5c4` 는 HEAD 의 **조상**이다(`git merge-base --is-ancestor` exit 0).
- base 에 이 slice 의 산출물이 **없다**(`git show 79f3e5c4:web/src/display/timeDisplay.ts` exit ≠ 0).
- 이 slice 는 **아직 main 에 없다**. 그래서 복원 출처는 base SHA 하나이고, 움직이는 ref
  (`origin/main`)를 쓰지 않는다. 머지 뒤의 되돌림은 이 절차가 아니라 **그 PR 의 머지 커밋
  revert** 다 — 머지 순간 같은 복원은 아무것도 지우지 않으면서 exit 0 을 낸다.
- **다른 slice 의 줄이 섞인 공유 파일이 없다.** `git log --oneline --no-merges 79f3e5c4..HEAD`
  의 커밋이 전부 이 slice 의 것이라(저자 한 사람·메시지 접두 `m9-screen-display`) 커밋 해시별
  hunk 격리가 필요하지 않다. 그래서 base 기준 경로 한정 복원 하나로 끝난다.
- 하네스 레인 변경 **없음**(`git log --oneline 79f3e5c4..HEAD -- CLAUDE.md .claude/` 빈 출력).
  되돌리지 않는다.

## 복원 목록 — 기계 산출

목록은 손으로 쓰지 않는다:

```
git diff --name-status 79f3e5c4..HEAD
```

`A` 는 삭제 대상, `M` 은 base 로 복원. `--source` 에 없는 경로는 삭제되므로 신규 파일에 별도
`git rm` 이 필요 없다. **라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 목록이 낡는 것이 이
결함의 실제 원인이다.

**이 slice 의 evidence 디렉터리(`reports/evidence/m9/screen-display/**`)는 되돌리지 않는다** —
기록은 남긴다. 그래서 목록에서 뺀다. 그 선택이 게이트를 붉히지 않는 것을 아래 ⑥ 에서 쟀다.
이 분리가 **실측을 유효하게 유지하는 장치**이기도 하다: evidence 커밋은 복원 목록 안의 경로를
건드리지 않으므로 실측 HEAD 뒤의 evidence 커밋이 실측을 낡게 만들지 않는다.

```
git restore --source=79f3e5c4 --staged --worktree -- \
  web/src/display/timeDisplay.ts \
  web/src/display/timeDisplay.test.ts \
  web/src/display/moneyDisplay.ts \
  web/src/display/moneyDisplay.test.ts \
  web/src/display/noticeDisplay.ts \
  web/src/display/noticeDisplay.test.ts \
  web/src/display/editSessionDisplay.ts \
  web/src/display/editSessionDisplay.test.ts \
  web/src/screens/InstantText.tsx \
  web/src/screens/screenVocabulary.test.tsx \
  web/src/screens/InboxScreen.tsx \
  web/src/screens/InboxScreen.test.tsx \
  web/src/screens/WorksheetTimeline.tsx \
  web/src/screens/BidBoxCard.tsx \
  web/src/screens/BidBoxScreen.test.tsx \
  web/src/screens/WorksheetScreen.tsx \
  web/src/screens/WorksheetScreen.test.tsx \
  web/src/screens/ResultCard.tsx \
  web/src/screens/ResultsScreen.test.tsx \
  web/src/screens/NoticeCard.tsx \
  web/src/screens/TodayNoticesScreen.tsx \
  web/src/screens/TodayNoticesScreen.test.tsx \
  web/src/screens/SettingsScreen.test.tsx \
  web/eslint.config.js \
  web/scripts/display-gate-selectors.test.mjs \
  web/e2e/journey/shared.ts \
  web/e2e/journey/journey.spec.ts \
  web/e2e/journey/results.spec.ts
```

## 임시 clone 실측 — ①~⑥

실측 HEAD `913b7ec5` 에서 만든 버릴 clone 에서 위 명령을 **실제로** 돌렸다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | 위 `git restore` | 0 |
| ② D/M 수 | `git status --porcelain` 의 첫 칸 계수 | `D` 5 · `M` 23 (목록 28 과 같다 — 신규 다섯이 삭제, 변경 스물셋이 복원) |
| ③ diff 빈 것 | `git diff 79f3e5c4 -- web/` | **0 줄** — 되돌린 `web/` 트리가 base 와 **바이트까지 같다**(갈음은 「HEAD 초록」이 아니라 트리 동일성으로만 본다) |
| ④ compile | `npx tsc --noEmit -p tsconfig.json` · `npm run build` | 0 · 0 |
| ⑤ test | `npx vitest run` | `674 passed (674)` · 파일 34 (이 slice 가 더한 test 셋과 단언이 함께 걷혔다 — HEAD 는 732/37) |
| ⑥ 게이트 | 이 slice 가 닿은 web 레인 게이트 넷 — `npm run lint`(W-2) · `gate:src-tree`(W-4) · `gate:tests`(W-7) · `gate:boundary-canary`(W-9) | 0 · 0 · 0 · 0 |

**되돌리지 않은 evidence 디렉터리가 게이트를 붉히지 않는다**: 같은 clone 에서
`grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/` 매치 **0**. (선례는
M4/leak-baseline-coord — 되돌리지 않은 evidence 문서의 자기매치가 base 의 baseline 에 없어
게이트가 exit 1 이었다. 이 slice 의 evidence 는 그 어휘를 축어로 담지 않는다 — 참조형 명령만
적는다.)

**⑥ 에 Kotlin `check` 를 넣지 않은 이유**: 이 slice 가 바꾼 것은 `web/**` 뿐이고 Kotlin 게이트는
그 경로를 보지 않는다. Kotlin 축이 이 slice 에 닿는 유일한 자리는 `check` job 의 S-20
(`one-command-check.sh` 가 web 전건을 돈다)이고, 그 web 전건이 위 ④⑤⑥ 이다.

## 되돌린 뒤의 상태

7E-1~7E-3b 의 화면 다섯과 여정 넷은 그대로 선다 — 이 slice 가 더한 것은 **표시 어휘 한 모듈 ·
금액 표 세 개 · lint 술어 두 묶음 · 게이트 test 둘**이고 앞 층을 치우지 않는다. 되돌리면 화면이
다시 응답의 ISO 원문과 금액 토큰 원문을 그리고(`OPEN-7E3B-TIME-DISPLAY`·
`OPEN-7E3B-MONEY-WORDING` 가 다시 열린다), `OPEN-7E-GATE-RESIDUAL` ⑨ 의 처방도 함께 되돌아간다.

## 비활성화만 하는 경로 (게이트만 끄기)

게이트 술어만 끄고 표시 어휘는 남기려면 `web/eslint.config.js` 의 **화면·표시 전용 블록 하나**를
지운다(그 블록의 주석이 「M9/screen-display D-1·D-4」로 시작한다). 그러면 W-2 는 초록으로
돌아오고 화면의 문면은 그대로다 — 다만 그 순간부터 **되돌림을 막는 층이 DOM 게이트 하나**다.
게이트 test 쪽은 `scripts/display-gate-selectors.test.mjs` 가 그 블록을 전제하므로 함께 지운다.
