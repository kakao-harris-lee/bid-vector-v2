# M9 / screen-display — rollback

실측 HEAD: `170af22c` (이 slice 의 **마지막 산출물 커밋** — 뒤의 커밋은 evidence 뿐이다)

## 전제

- base `79f3e5c4` 는 HEAD 의 **조상**이다(`git merge-base --is-ancestor` exit 0).
- base 에 이 slice 의 산출물이 **없다**(`git show 79f3e5c4:web/src/display/timeDisplay.ts` exit ≠ 0).
- 이 slice 는 **아직 main 에 없다**. 복원 출처는 base SHA 하나이고 움직이는 ref(`origin/main`)를
  쓰지 않는다. **머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다 — 머지 순간
  같은 복원은 아무것도 지우지 않으면서 exit 0 을 낸다(의미가 뒤집힌다).
- **공유 파일 하나**가 목록에 있다: `web/config/gate-tests.txt` 는 slice 들이 줄을 더해 온
  등록부다. 다만 `git log --oneline 79f3e5c4..170af22c -- web/config/gate-tests.txt` 의 커밋이
  **이 slice 의 하나뿐**이라, base 복원이 지우는 것은 이 slice 가 더한 줄뿐이다(다른 slice 의
  줄은 이 브랜치의 range 에 없다). 그래서 커밋 해시별 hunk 격리가 필요하지 않다.
- 하네스 레인 변경 **없음**(`git log --oneline 79f3e5c4..HEAD -- CLAUDE.md .claude/` 빈 출력).
  되돌리지 않는다.

## 복원 목록 — 기계 산출

목록은 손으로 쓰지 않는다:

```
git diff --name-status 79f3e5c4..170af22c
```

`A` 는 삭제 대상, `M` 은 base 로 복원. `--source` 에 없는 경로는 삭제되므로 신규 파일에 별도
`git rm` 이 필요 없다. **라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 목록이 낡는 것이 이
결함의 실제 원인이다. 계약 갱신 2026-11-05 로 경로가 **28 → 39** 로 늘었다(등록부 ·
캡처 스크립트 · 캡처 산출물 아홉).

**이 slice 의 evidence 디렉터리(`reports/evidence/m9/screen-display/**`)는 되돌리지 않는다** —
기록은 남긴다. 그래서 목록에서 뺀다. 이 분리가 **실측을 유효하게 유지하는 장치**이기도 하다:
evidence 커밋은 복원 목록 안의 경로를 건드리지 않으므로 실측 HEAD 뒤의 evidence 커밋이 실측을
낡게 만들지 않는다(아래 유효성 확인).

```
git restore --source=79f3e5c4 --staged --worktree -- \
  docs/usecase/captures/b-notices-desktop.png \
  docs/usecase/captures/b-notices-mobile.png \
  docs/usecase/captures/c-worksheet-desktop.png \
  docs/usecase/captures/c-worksheet-mobile.png \
  docs/usecase/captures/e-results-desktop.png \
  docs/usecase/captures/e-results-mobile.png \
  docs/usecase/captures/inbox-desktop.png \
  docs/usecase/captures/inbox-mobile.png \
  docs/usecase/captures/manifest.json \
  web/config/gate-tests.txt \
  web/e2e/capture/usecase-captures.mjs \
  web/e2e/journey/journey.spec.ts \
  web/e2e/journey/results.spec.ts \
  web/e2e/journey/shared.ts \
  web/eslint.config.js \
  web/scripts/display-gate-selectors.test.mjs \
  web/src/display/editSessionDisplay.test.ts \
  web/src/display/editSessionDisplay.ts \
  web/src/display/moneyDisplay.test.ts \
  web/src/display/moneyDisplay.ts \
  web/src/display/noticeDisplay.test.ts \
  web/src/display/noticeDisplay.ts \
  web/src/display/timeDisplay.test.ts \
  web/src/display/timeDisplay.ts \
  web/src/screens/BidBoxCard.tsx \
  web/src/screens/BidBoxScreen.test.tsx \
  web/src/screens/InboxScreen.test.tsx \
  web/src/screens/InboxScreen.tsx \
  web/src/screens/InstantText.tsx \
  web/src/screens/NoticeCard.tsx \
  web/src/screens/ResultCard.tsx \
  web/src/screens/ResultsScreen.test.tsx \
  web/src/screens/SettingsScreen.test.tsx \
  web/src/screens/TodayNoticesScreen.test.tsx \
  web/src/screens/TodayNoticesScreen.tsx \
  web/src/screens/WorksheetScreen.test.tsx \
  web/src/screens/WorksheetScreen.tsx \
  web/src/screens/WorksheetTimeline.tsx \
  web/src/screens/screenVocabulary.test.tsx
```

## 임시 clone 실측 — ①~⑥

실측 HEAD `170af22c` 에서 만든 버릴 clone 에서 위 명령을 **실제로** 돌렸다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | 위 `git restore` | 0 |
| ② D/M 수 | `git status --porcelain` 의 첫 칸 계수 | `D` 5 · `M` 34 (목록 39 와 같다) |
| ③ 트리 동일성 | `git diff 79f3e5c4 -- web/` · `-- docs/usecase` | **둘 다 0 줄** — 되돌린 트리가 base 와 바이트까지 같다(갈음은 「HEAD 초록」이 아니라 트리 동일성으로만 본다) |
| ④ compile | `npx tsc --noEmit -p tsconfig.json` · `npm run build` | 0 · 0 |
| ⑤ test | `npx vitest run` | `674 passed (674)` · 파일 34 (이 slice 가 더한 test 셋과 단언이 함께 걷혔다 — HEAD 는 733/37) |
| ⑥ 게이트 | 이 slice 가 닿은 web 레인 게이트 넷 — `lint`(W-2) · `gate:src-tree`(W-4) · `gate:tests`(W-7) · `gate:boundary-canary`(W-9) | 0 · 0 · 0 · 0 |

**W-7 이 되돌린 트리에서도 0 인 것이 중요하다**: 등록부와 게이트 test 파일이 **함께** 걷히므로
「등록부에는 있는데 파일이 없다」가 생기지 않는다.

### ⑥b 보존한 evidence 가 게이트를 붉히는가 — 두 번 쟀다

| 기준 | 매치 | 뜻 |
| --- | --- | --- |
| 실측 HEAD `170af22c` 의 evidence | **1** | 그 시점의 checklist 가 비밀값 스캔 어휘를 한 번 밟고 있었다 — 되돌린 트리에서 `leakPatternGate` 가 붉어진다 |
| 그 뒤 `3f507eb1` 의 evidence | **0** | 그 문장을 고쳤다(baseline 등재가 아니라 문장 수정). 그 커밋부터 이 조건이 사라진다 |

**되돌릴 때 적용되는 것은 뒤의 값**이다 — 복원은 산출물 경로만 만지고 evidence 는 **그때의
HEAD 그대로** 남기 때문이다. 그래서 지금 이후의 되돌림에서 이 축은 초록이다. 앞의 값을 함께
적는 이유는 「그 시점에 돌렸다면 붉었다」가 사실이기 때문이다(M4/leak-baseline-coord 선례 —
세 라운드 동안 「게이트 전부 정상 통과」로 적혀 있었다).

## 실측 유효성

verifier 가 대조하는 것은 「실측 HEAD == 판정 SHA」가 아니다(둘은 영원히 다르다 — evidence
커밋이 언제나 뒤에 온다). **그 사이에 되돌림 대상이 움직였는가**를 본다:

```
git diff --name-only 170af22c..<판정 SHA> -- <위 복원 목록 경로들>
```

`170af22c..3f507eb1` 에서 **빈 출력**이다 — 그 뒤 커밋은 evidence 뿐이고 evidence 는 복원 목록
밖이다.

## 되돌린 뒤의 상태

7E-1~7E-3b 의 화면 다섯과 여정 넷은 그대로 선다 — 이 slice 가 더한 것은 **표시 어휘 한 모듈 ·
금액 표 네 개 · lint 술어 두 묶음 · 게이트 test 둘과 그 등재 · 캡처 표지 한 줄**이고 앞 층을
치우지 않는다. 되돌리면 화면이 다시 응답의 ISO 원문과 금액 토큰 원문을 그리고
(`OPEN-7E3B-TIME-DISPLAY`·`OPEN-7E3B-MONEY-WORDING` 가 다시 열린다), `OPEN-7E-GATE-RESIDUAL` ⑨ 의
처방도 함께 되돌아간다. **캡처는 옛 문면으로 돌아가고 캡처 스크립트의 선행 결함도 되살아난다** —
그 상태에서 `capture:usecase` 는 알림함에서 다시 죽는다(그 자체가 base 의 사실이다).

## 비활성화만 하는 경로 (게이트만 끄기)

게이트 술어만 끄고 표시 어휘는 남기려면 `web/eslint.config.js` 의 **화면·표시 전용 블록 하나**를
지운다(그 블록의 주석이 「M9/screen-display D-1·D-4」로 시작한다). 그러면 W-2 는 초록으로
돌아오고 화면의 문면은 그대로다 — 다만 그 순간부터 **되돌림을 막는 층이 DOM 게이트 하나**다.
그때는 `web/scripts/display-gate-selectors.test.mjs` 와 등록부의 그 줄도 함께 지운다(그 test 가
지운 블록을 전제한다).
