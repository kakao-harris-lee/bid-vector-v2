# M9 / web-followups — rollback

실측 HEAD: `50bc35c0` (이 slice 의 **마지막 산출물 커밋** — 뒤의 커밋은 evidence 뿐이다)

복원 원천: `df4976cc`(base) · 복원 목록 산출:
`git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u`(**내 커밋 전부**)

## 전제

- **이 브랜치는 main 을 합치지 않았다.** `git merge-base HEAD df4976cc` 가 `df4976cc` 이고 커밋
  아홉이 전부 내 것이다(병합 커밋 0). 그래서 복원 원천이 **base 그대로**다 — 합친 main 이 있는
  경우의 「고정 SHA」 처방이 필요 없다. 합치는 일이 생기면 그 절차로 **다시 산출한다**.
- base 는 이 slice 의 산출물을 갖지 않는다.
- **머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다 — 머지 순간 같은 복원은
  아무것도 지우지 않으면서 exit 0 을 낸다(의미가 뒤집힌다).
- 하네스 레인 변경 **없음**(`git log --oneline df4976cc..HEAD -- CLAUDE.md .claude/` 빈 출력).

## 복원 목록 — 기계 산출

목록은 손으로 쓰지 않는다:

```
git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u
```

산출은 24 경로이고 그 가운데 **이 slice 의 evidence 디렉터리
(`reports/evidence/m9/web-followups/**`)는 되돌리지 않는다** — 기록은 남긴다. 그래서 목록에서
뺀다(**22 경로**). 이 분리가 실측을 유효하게 유지하는 장치이기도 하다: evidence 커밋은 복원
목록 안의 경로를 건드리지 않으므로 실측 HEAD 뒤의 evidence 커밋이 실측을 낡게 만들지 않는다.

신규 파일은 원천에 없으므로 삭제되고(`--source` 에 없는 경로는 삭제된다 — 별도 `git rm` 이
필요 없다), 그 밖은 원천의 내용으로 돌아간다. **라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

```
git restore --source=df4976cc --staged --worktree -- \
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
  web/eslint.config.js \
  web/scripts/display-gate-selectors.test.mjs \
  web/scripts/money-vocab-cross-lang.test.mjs \
  web/scripts/screen-card-coverage.mjs \
  web/scripts/screen-card-coverage.test.mjs \
  web/src/display/caseDisplay.test.ts \
  web/src/display/caseDisplay.ts \
  web/src/display/timeDisplay.test.ts \
  web/src/display/timeDisplay.ts \
  web/src/screens/WorksheetScreen.test.tsx \
  web/src/screens/WorksheetTimeline.tsx \
  web/src/screens/screenVocabulary.test.tsx
```

## 임시 clone 실측 — ①~⑥

실측 HEAD `50bc35c0` 에서 만든 버릴 clone 에서 **문서의 명령 블록을 그대로 뽑아** 돌렸다 —
목록이 맞는지가 아니라 **문서대로 실행되는지**를 재는 것이 요지다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | 위 `git restore` | 0 |
| ② D/M 수 | `git status --porcelain` 의 첫 칸 계수 | `D` 1 · `M` 21 (목록 22 와 같다 — 신규 파일은 교차언어 게이트 test 하나) |
| ③ 트리 동일성 | `git diff --name-only df4976cc`(**작업 트리** 대조) | 나온 경로가 **이 slice 의 evidence 둘뿐**이다 — 되돌린 트리가 base 의 트리와 같다(되돌리지 않기로 한 자리만 남는다). 커밋끼리 대조하면 복원 결과를 보지 못한다 |
| ④ compile | `npx tsc --noEmit -p tsconfig.json` · `npm run build` | 0 · 0 |
| ⑤ test | `npx vitest run` | `762 passed (762)` · 파일 38 (이 slice 가 더한 test 파일 하나와 단언 54 가 함께 걷혔다 — 판정 SHA 는 810/39) |
| ⑥ 게이트 | 이 slice 가 닿은 web 레인 게이트 다섯 — `lint`(W-2) · `gate:src-tree`(W-4) · `api:types:check`(W-5) · `gate:tests`(W-7) · `gate:boundary-canary`(W-9) | 0 · 0 · 0 · 0 · 0 |

**W-7 이 되돌린 트리에서도 0 인 것이 중요하다**: 등록부 줄과 게이트 test 파일이 **함께** 걷히므로
「등록부에는 있는데 파일이 없다」가 생기지 않는다.

### 「내 표지 0 · 남의 표지 보존」 계수

종료 코드가 아니라 **계수**로 완료를 판정한다.

| 축 | 센 것 | 결과 |
| --- | --- | --- |
| 내 표지 0 | 이 slice 가 들인 이름 여섯(명령 표 · 명령 토큰 유니온 · 명령 조회 함수 · 달력일 함수 · 시계 표면 목록 상수 · 어휘 선언 목록 상수)을 담은 `web/` 파일 수 | 여섯 전부 **0 파일** |
| 내 표지 0 | 새 게이트 test 파일 존재 | **없음** |
| 남의 표지 보존 | 앞 판의 모집단 술어 이름(`VOCABULARY_CALLS`) | **2 파일** — 앞 판의 술어가 되살아났다 |
| 남의 표지 보존 | 다른 레인 소유 둘(알림함 화면 · 알림 표시 모듈) · e2e 여정 둘 · 금액 표시 모듈 | 전부 base 와 **0 줄 차이** |
| 남의 표지 보존 | `tools/prod-guards-check.sh` 존재 | 보존 |

## 실측 유효성

verifier 가 대조하는 것은 「실측 HEAD == 판정 SHA」가 아니다(둘은 영원히 다르다 — evidence
커밋이 언제나 뒤에 온다). **그 사이에 되돌림 대상이 움직였는가**를 본다:

```
git diff --name-only 50bc35c0..<판정 SHA> -- <위 복원 목록 경로들>
```

판정 SHA 가 `50bc35c0` 이므로 이 대조는 **자기 자신과의 대조**이고 빈 출력이다. 뒤에 오는 evidence
커밋은 복원 목록 **밖**이므로 그것들이 붙어도 빈 출력이 유지된다.

## 되돌린 뒤의 상태

PR #125 의 화면 표시 어휘(시각·금액)는 그대로 선다 — 이 slice 가 더한 것은 **명령 표 하나 ·
D-day 셈 하나 · 게이트 술어 셋**이고 앞 층을 치우지 않는다. 되돌리면 다섯 OPEN 이 **다시 열린다**:
타임라인이 명령 토큰 원문을 그리고, D-day 가 24시간 나눗셈으로 돌아가고, 모집단이 호출 이름에
걸리고, 시계 금지가 `Date` 라는 이름과 제외 디렉터리 둘로 좁아지고, wire 어휘가 어느 계약에도
묶이지 않는다. **네트워크 전역 목록이 시계 블록에 덮이는 선행 결함도 되살아난다**(맨 `fetch` 가
화면 파일에서 초록). 캡처는 옛 문면과 옛 D-day 로 돌아간다.

## 비활성화만 하는 경로 (게이트만 끄기)

새 게이트 셋만 끄고 거동(명령 문면 · 달력일 D-day)은 남기려면 셋을 각각 끈다.

- 교차언어 등식: `web/scripts/money-vocab-cross-lang.test.mjs` 와 **등록부의 그 줄**을 함께
  지운다(한쪽만 지우면 W-7 이 「등록부에는 있는데 파일이 없다」로 붉어진다).
- 시계 범위: eslint 설정의 시계 표면 목록 상수를 `['Date']` 로 되돌리고 제외 디렉터리 둘을
  되살린 뒤, 그 축을 재는 대조 블록(`display-gate-selectors.test.mjs` 의 「지금을 낼 수 있는 다른
  표면도 막는다」와 「API·세션 층」 둘)을 지운다.
- 모집단 심볼 그래프: `screen-card-coverage.mjs` 의 해소를 호출 이름 술어로 되돌리고 그 대조 넷을
  지운다. 등식 자체는 남으므로 모집단 게이트는 계속 선다(힘만 앞 판으로 돌아간다).

세 경로 모두 **DOM 어휘 게이트와 화면 test 는 남는다** — 거동을 되돌리는 변이는 그쪽이 받는다.
