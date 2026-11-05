# M9 / web-followups — rollback

실측 HEAD: `68834ea2`

> **이 실측은 아직 유효하지 않다 — 재실측이 예정돼 있다.** 판정 SHA 는 그 뒤 `bb2f1bae`(표적
> 재검증 L-2)로 갔고, 그 커밋이 **복원 목록 안의 파일 하나**(공고 카드)를 움직였다. 그래서
> 아래 §「실측 유효성」의 대조가 **빈 출력이 아니다**. 그리고 팀장 지시로 이 브랜치는 곧 PR #130
> 의 머지 SHA 를 합치며, 그 순간 **복원 원천 자체가 base 에서 그 SHA 로 바뀐다**. 두 변화를
> 한 번에 받는 것이 맞으므로 **병합 뒤에 목록을 재산출하고 ①~⑥ 을 다시 돌린다** — 지금 한 번
> 더 재는 것은 곧 버려질 측정이다. 이 문단이 그 사실의 선언이고, 재실측 전에는 이 절을 근거로
> 쓰지 않는다.

PR #132 수정 라운드로 **복원 목록을 재산출했다**: 22 → **25**(표 조회 모듈 · 공고 카드 · 그 화면
test 셋이 늘었다). 앞 라운드의 실측(`50bc35c0` · 22 경로)은 그 시점의 사실이고 아래는 **그 다음
라운드의 실측**이다 — 목록이 낡는 것이 이 결함의 실제 원인이라 라운드마다 다시 돌린다.

복원 원천: `df4976cc`(base) · 복원 목록 산출:
`git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u`(**내 커밋 전부**)

## 전제

- **이 브랜치는 main 을 합치지 않았다.** `git merge-base HEAD df4976cc` 가 `df4976cc` 이고 커밋
  열넷이 전부 내 것이다(병합 커밋 0). main 은 그 뒤 움직였지만(#131 까지) 이 브랜치는 아직
  병합하지 않았다 — **병합이 생기면 복원 원천을 「합친 main 의 고정 SHA」로 바꿔 재산출한다**. 그래서 복원 원천이 **base 그대로**다 — 합친 main 이 있는
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

산출은 30 경로이고 그 가운데 **이 slice 의 evidence 디렉터리
(`reports/evidence/m9/web-followups/**`)는 되돌리지 않는다** — 기록은 남긴다. 그래서 목록에서
뺀다(**25 경로**). 이 분리가 실측을 유효하게 유지하는 장치이기도 하다: evidence 커밋은 복원
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
  web/src/display/stateDisplay.ts \
  web/src/display/timeDisplay.test.ts \
  web/src/display/timeDisplay.ts \
  web/src/screens/NoticeCard.tsx \
  web/src/screens/TodayNoticesScreen.test.tsx \
  web/src/screens/WorksheetScreen.test.tsx \
  web/src/screens/WorksheetTimeline.tsx \
  web/src/screens/screenVocabulary.test.tsx
```

## 임시 clone 실측 — ①~⑥

실측 HEAD `68834ea2` 에서 만든 버릴 clone 에서 **문서의 명령 블록을 그대로 뽑아** 돌렸다 —
목록이 맞는지가 아니라 **문서대로 실행되는지**를 재는 것이 요지다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | 위 `git restore` | 0 |
| ② D/M 수 | `git status --porcelain` 의 첫 칸 계수 | `D` 1 · `M` 24 (목록 25 와 같다 — 신규 파일은 교차언어 게이트 test 하나) |
| ③ 트리 동일성 | `git diff --name-only df4976cc`(**작업 트리** 대조) | 나온 경로가 **이 slice 의 evidence 다섯뿐**이다 — 되돌린 트리가 base 의 트리와 같다(되돌리지 않기로 한 자리만 남는다). 커밋끼리 대조하면 복원 결과를 보지 못한다 |
| ④ compile | `npx tsc --noEmit -p tsconfig.json` · `npm run build` | 0 · 0 |
| ⑤ test | `npx vitest run` | `762 passed (762)` · 파일 38 (이 slice 가 더한 test 파일 하나와 단언 65 가 함께 걷혔다 — 판정 SHA 는 821/39) |
| ⑥ 게이트 | 이 slice 가 닿은 web 레인 게이트 다섯 — `lint`(W-2) · `gate:src-tree`(W-4) · `api:types:check`(W-5) · `gate:tests`(W-7) · `gate:boundary-canary`(W-9) | 0 · 0 · 0 · 0 · 0 |

**W-7 이 되돌린 트리에서도 0 인 것이 중요하다**: 등록부 줄과 게이트 test 파일이 **함께** 걷히므로
「등록부에는 있는데 파일이 없다」가 생기지 않는다.

### 「내 표지 0 · 남의 표지 보존」 계수

종료 코드가 아니라 **계수**로 완료를 판정한다.

| 축 | 센 것 | 결과 |
| --- | --- | --- |
| 내 표지 0 | 이 slice 가 들인 이름 여덟(명령 표 · 명령 토큰 유니온 · 명령 조회 함수 · 달력일 함수 · 시계 표면 목록 상수 · 어휘 선언 목록 상수 · 화면 파일 훑기 함수 · D-day 설명 문면 상수)을 담은 `web/` 파일 수 | 여덟 전부 **0 파일** |
| 내 표지 0 | 새 게이트 test 파일 존재 | **없음** |
| 내 표지 0 | 표 조회 모듈이 base 와 같은가(`Object.hasOwn` 은 **표지가 못 된다** — base 의 프로필 표시 모듈이 이미 쓴다) | base 와 **0 줄 차이** |
| 남의 표지 보존 | 앞 판의 모집단 술어 이름(`VOCABULARY_CALLS`) | **2 파일** — 앞 판의 술어가 되살아났다 |
| 남의 표지 보존 | 옛 D-day 문면(「마감까지 남은 날(내림)」) | **1 파일** — 되돌린 화면이 옛 문면을 되찾았다 |
| 남의 표지 보존 | 다른 레인 소유 둘(알림함 화면 · 알림 표시 모듈) · e2e 여정 · 금액 표시 모듈 | 전부 base 와 **0 줄 차이** |
| 남의 표지 보존 | `tools/prod-guards-check.sh` 존재 | 보존 |

`Object.hasOwn` 이 표지가 못 되는 것은 **그 처방이 저장소 선례를 따른 것**이기 때문이다 —
프로필 표시 모듈이 이미 같은 꼴로 표를 읽고 있었고, 이 라운드가 그 관례를 공통 조회 자리에
옮겼다(cr L-3).

## 실측 유효성

verifier 가 대조하는 것은 「실측 HEAD == 판정 SHA」가 아니다(둘은 영원히 다르다 — evidence
커밋이 언제나 뒤에 온다). **그 사이에 되돌림 대상이 움직였는가**를 본다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 복원 목록 경로들>
```

**지금은 빈 출력이 아니다**: 실측 HEAD 는 `68834ea2` 인데 판정 SHA 가 `bb2f1bae` 이고, 그 커밋이
복원 목록 안의 공고 카드를 움직였다(JSDoc 자리 바로잡기 — 글자 변화는 없지만 **그 판정은 파일이
움직였는가**이지 내용이 바뀌었는가가 아니다). 그래서 이 절은 **머리의 선언대로 재실측 전까지
근거가 되지 않는다**.

재실측은 병합 뒤에 한 번 돈다(복원 원천이 base 에서 머지 SHA 로 함께 바뀐다). 그때 이 블록의
두 SHA 를 새 값으로 적고, 실측 HEAD 는 다시 **그 시점의 마지막 산출물 커밋**이 된다 — 뒤에 오는
evidence 커밋은 복원 목록 **밖**이라 그 뒤로는 빈 출력이 유지된다.

## 되돌린 뒤의 상태

PR #125 의 화면 표시 어휘(시각·금액)는 그대로 선다 — 이 slice 가 더한 것은 **명령 표 하나 ·
D-day 셈 하나 · 게이트 술어 셋**이고 앞 층을 치우지 않는다. 되돌리면 다섯 OPEN 이 **다시 열린다**:
타임라인이 명령 토큰 원문을 그리고, D-day 가 24시간 나눗셈으로 돌아가고, 모집단이 호출 이름에
걸리고, 시계 금지가 `Date` 라는 이름과 제외 디렉터리 둘로 좁아지고, wire 어휘가 어느 계약에도
묶이지 않는다. **네트워크 전역 목록이 시계 블록에 덮이는 선행 결함도 되살아난다**(맨 `fetch` 가
화면 파일에서 초록). 캡처는 옛 문면과 옛 D-day 로 돌아간다.

**PR #132 수정 라운드의 것도 함께 되돌아간다**: 시계 금지에서 구조 분해와 빈 인자 서식 호출이
다시 열리고, 모집단 훑기가 평면·`.tsx` 로 좁아지고, Kotlin 읽기가 못 읽는 조각을 다시 **조용히
버리며**, 표 조회가 프로토타입 키에서 폴백을 삼키고, 화면이 다시 「마감까지 남은 날(내림)」이라고
말한다(그 마지막 하나는 되돌린 트리에서 **계수로 확인했다**).

## 비활성화만 하는 경로 (게이트만 끄기)

새 게이트 셋만 끄고 거동(명령 문면 · 달력일 D-day)은 남기려면 셋을 각각 끈다.

- 교차언어 등식: `web/scripts/money-vocab-cross-lang.test.mjs` 와 **등록부의 그 줄**을 함께
  지운다(한쪽만 지우면 W-7 이 「등록부에는 있는데 파일이 없다」로 붉어진다).
- 시계 범위: eslint 설정의 시계 표면 목록 상수를 `['Date']` 로 되돌리고 제외 디렉터리 둘을
  되살리며 구조 분해·빈 인자 선택자를 지운 뒤, 그 축을 재는 대조 블록
  (`display-gate-selectors.test.mjs` 의 「지금을 낼 수 있는 다른 표면도 막는다」와 「API·세션 층」
  둘)을 지운다.
- 모집단 심볼 그래프: `screen-card-coverage.mjs` 의 해소를 호출 이름 술어로 되돌리고 그 대조 넷을
  지운다. 등식 자체는 남으므로 모집단 게이트는 계속 선다(힘만 앞 판으로 돌아간다). 훑기의
  재귀·`.ts` 는 **그대로 둘 수 있다** — 그 둘은 술어가 아니라 범위라 끄는 이유가 다르다.

세 경로 모두 **DOM 어휘 게이트와 화면 test 는 남는다** — 거동을 되돌리는 변이는 그쪽이 받는다.
표 조회의 자기 키 검사(cr L-3)와 화면의 D-day 설명 문면(cr M-3)은 **게이트가 아니라 산출물
거동**이라 이 절의 대상이 아니다(끄려면 위의 전체 복원이다).
