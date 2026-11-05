# M9 / screen-display — 실행 명령과 종료 코드

출력 전문을 붙이지 않는다(핵심 결과는 한 줄). 감사자는 명령을 다시 돌린다.
작업 디렉터리는 명령마다 적는다 — `web` 아래 명령은 `cd web` 기준이다.

## TDD — RED 실측

### 2026-11-04T23:18Z — 시각 모듈의 RED
- cmd: `npx vitest run src/display/timeDisplay.test.ts` (web)
- exit: 1
- 핵심 결과: `Test Files 1 failed` · 모듈이 없어 수집 자체가 실패(`no tests`)

### 2026-11-04T23:20Z — 금액 세 표의 RED
- cmd: `npx vitest run src/display/moneyDisplay.test.ts` (web)
- exit: 1
- 핵심 결과: `20 failed | 8 passed (28)` — 새 표·조회 함수·토큰 집합이 없다

### 2026-11-04T23:27Z — D-4 lint 술어가 enum 한 위반 자리
- cmd: `npx eslint . --max-warnings 0` (web)
- exit: 1
- 핵심 결과: `21 problems (21 errors)` — 벽시계 4 · `*At` 보간 17, 파일 여덟

### 2026-11-04T23:30Z — 화면을 고친 뒤 옛 단언의 RED
- cmd: `npx vitest run` (web)
- exit: 1
- 핵심 결과: `13 failed | 697 passed (710)` — 원문 토큰·ISO 를 잠그던 단언 13 건

## acceptance — CI `web` job 의 명령 그대로 (W-1~W-13)

### 2026-11-04T23:16Z
- cmd: `npm ci` (web, W-1)
- exit: 0
- 핵심 결과: 잠금 파일 고정판 설치 완료

### 2026-11-04T23:37Z
- cmd: `npm run lint` (web, W-2)
- exit: 0
- 핵심 결과: 지적 0 — D-4 가 enum 한 21 자리 전부 닫힘

### 2026-11-04T23:37Z
- cmd: `npm run typecheck` (web, W-3)
- exit: 0
- 핵심 결과: 오류 0

### 2026-11-04T23:37Z
- cmd: `npm run gate:src-tree` (web, W-4)
- exit: 0
- 핵심 결과: 컴파일 경계 전제 성립 · 프로그램 파일 277(src 안 83)

### 2026-11-04T23:37Z
- cmd: `npm run api:types:check` (web, W-5)
- exit: 0
- 핵심 결과: 생성 타입이 OpenAPI 문서와 바이트까지 같다

### 2026-11-04T23:35Z
- cmd: `npm run test` (web, W-6)
- exit: 0
- 핵심 결과: `732 passed (732)` · 파일 37

### 2026-11-04T23:40Z
- cmd: `npm run gate:tests` (web, W-7)
- exit: 0
- 핵심 결과: 등록부의 게이트 test 가 W-6 수집에 있고 skipped/todo/failed 0
- 비고: 이 slice 가 만든 게이트 test 셋은 **아직 등록부 밖**이다(「알려진 제한」 ①)

### 2026-11-04T23:39Z
- cmd: `npm run build` (web, W-8)
- exit: 0
- 핵심 결과: 201 modules · `dist/assets/index-*.js` 491.00 kB

### 2026-11-04T23:39Z
- cmd: `npm run gate:boundary-canary` (web, W-9)
- exit: 0
- 핵심 결과: 메인·워커 두 그래프에서 경계 플러그인이 fixture 실경로를 거부

### 2026-11-04T23:39Z
- cmd: `npx playwright install chromium` (web, W-10)
- exit: 0
- 핵심 결과: 브라우저 설치 완료

### 2026-11-04T23:39Z
- cmd: `npm run test:e2e` (web, W-11)
- exit: 0
- 핵심 결과: `2 passed` — 셸 스모크

### 2026-11-04T23:41Z
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon :app:bootJar` (저장소 뿌리, W-12)
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 23s`

### 2026-11-04T23:42Z
- cmd: `npm run test:e2e:journey` (web, W-13)
- exit: 0
- 핵심 결과: `11 passed` · 여정 레인 판정 「통과 11 · flaky 0 · 건너뜀 0 · 중단 0 · spec 4/4」
- 비고: 실 app.jar·실 Postgres·fake KONEPS 위에서 **KST 표기 단언이 실 응답으로** 섰다

## acceptance — Kotlin `check` job (S-20 이 web 전건을 돈다)

### 2026-11-04T23:52Z
- cmd: `~/.local/bin/bv-build.sh ./tools/one-command-check.sh` (저장소 뿌리, S-20)
- exit: 0
- 핵심 결과: 26 단계 전부 통과 — 「완료 — Kotlin 전건 + Python 전건 + web 전건 통과」.
  그 안의 web 전건은 `732 passed` · 셸 스모크 2 · 여정 11(flaky 0 · 건너뜀 0 · spec 4/4)

## 게이트 술어의 양성·음성 대조 (D-4)

### 2026-11-04T23:32Z
- cmd: `npx vitest run scripts/display-gate-selectors.test.mjs` (web)
- exit: 0
- 핵심 결과: `14 passed` — 심은 위반 일곱이 RED · 지나야 할 꼴 다섯이 초록 ·
  flat config 덮어쓰기 함정 둘(화면·표시 파일에서 네트워크 금지가 여전히 RED)

### 2026-11-04T23:34Z
- cmd: `npx vitest run src/screens/screenVocabulary.test.tsx` (web)
- exit: 1 → 0
- 핵심 결과: 첫 실행이 **술어의 결함**을 냈다 — 세 글자 토큰이 다른 식별자(타임라인의
  명령 토큰)의 조각에 걸렸다. 술어를 「홀로 선 토큰」으로 좁히고 그 좁힘에 양성·음성
  대조를 붙인 뒤 `7 passed`

## 변이 — 바꿔치우기 (커밋 뒤, 원복은 변이 파일만)

### 2026-11-04T23:45Z — 변이 ①a 화면 하나를 원문 보간으로 되돌림 (lint 가 보는 꼴)
- cmd: `npx eslint src/screens/WorksheetTimeline.tsx` / `npx vitest run src/screens/screenVocabulary.test.tsx` (web)
- exit: 1 / 1
- 핵심 결과: lint 가 그 자리를 집고(`no-restricted-syntax` 1) DOM 게이트도 `2 failed`

### 2026-11-04T23:46Z — 변이 ①b 같은 되돌림을 lint 가 못 보는 꼴로 (중간 변수)
- cmd: `npx eslint . --max-warnings 0` / `npx tsc --noEmit -p tsconfig.json` / `npx vitest run` (web)
- exit: 0 / 0 / 1
- 핵심 결과: **lint 0 · tsc 0 인데 DOM 게이트 `2 failed`** — 두 층이 서로를 대신하지
  않는다는 측정이다(lint 층 단독이면 이 되돌림이 초록으로 지나간다)

### 2026-11-04T23:48Z — 변이 ② 표 한 행 삭제 (`AMOUNT_BASIS_DISPLAY` 의 한 행)
- cmd: `npx tsc --noEmit -p tsconfig.json` / `npx vitest run` (web)
- exit: 2 / 1
- 핵심 결과: `satisfies` 가 컴파일에서 멈추고(`Property … is missing`) vitest `7 failed`

### 2026-11-04T23:50Z — 변이 ③ 금액 부속을 원문 이어붙이기로 되돌림
- cmd: `npx eslint . --max-warnings 0` / `npx tsc --noEmit -p tsconfig.json` / `npx vitest run` (web)
- exit: 0 / 0 / 1
- 핵심 결과: lint·tsc 가 **보지 못하고**(금액 축에는 lint 술어가 없다) 표시 test·화면
  test·DOM 게이트 세 자리에서 `4 failed`

### 2026-11-04T23:51Z — 변이 원복 확인
- cmd: `git status --porcelain --untracked-files=all -- web/`
- exit: 0
- 핵심 결과: 빈 출력 — 변이 파일 셋이 전부 HEAD 상태

## clean-tree 게이트

### 2026-11-05T00:40Z
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력

### 2026-11-05T00:40Z — 양성 대조 (비파괴 절삭)
- cmd: 같은 명령을 `web/src/display/timeDisplay.ts` 끝에 주석 한 줄을 더한 상태에서
- exit: 0
- 핵심 결과: 그 한 경로가 `M` 로 나왔다 → 주석을 지워 다시 빈 출력. `checkout --` 를
  쓰지 않았다(미커밋 산출물을 지우는 명령이다)

## 캡처 재촬영 — 막혔다 (제한 ⑩)

### 2026-11-05T00:30Z
- cmd: `npm run capture:usecase` (web)
- exit: 1
- 핵심 결과: 데스크톱 다섯 장 뒤 알림함에서 타임아웃 — 그 화면의 양성 표지가 **빈 얼굴**인데
  같은 스크립트의 씨앗이 알림을 만든다. 산출물은 무변경(성공한 실행만 바꿔 끼운다)

### 2026-11-05T00:55Z — 같은 실패를 base 에서 재현
- cmd: 버릴 clone(`web/` 를 base 로 복원 · `git diff 79f3e5c4 -- web/` 0 줄 · **같은 app.jar**)
  에서 `npm run capture:usecase`
- exit: 1
- 핵심 결과: 같은 자리·같은 타임아웃 → **이 slice 의 결함이 아니다**

### 2026-11-05T00:56Z — 산출물 무변경 확인
- cmd: `git status --porcelain -- docs/usecase`
- exit: 0
- 핵심 결과: 빈 출력 · `docs/usecase/captures` 13 파일 그대로 · staging 잔존 없음

## 크기 게이트

### 2026-11-05T00:20Z
- cmd: `cat reports/evidence/m9/screen-display/*.md | wc -l` 대
  `git diff 79f3e5c4..HEAD --numstat -- web/` 의 합
- exit: 0
- 핵심 결과: evidence 400 줄 대 산출물 1525 줄 — evidence ≤ 산출물

## 계약 갱신 2026-11-05 뒤 — 추가 실측

### 2026-11-05T08:25Z — D-1 갱신의 RED
- cmd: `npx vitest run src/display/timeDisplay.test.ts` (web)
- exit: 1
- 핵심 결과: 문면을 축어로 바꾼 단언이 `expected '—' to be '값 없음'` 으로 떨어졌다

### 2026-11-05T08:30Z — 등재가 실제로 무는가 (변이 ④, D-5)
- cmd: 등재한 게이트 test 의 한 `describe` 를 `skip` 으로 바꿔치운 뒤
  `npx vitest run`(W-6) 과 `npm run gate:tests`(W-7)
- exit: **0 / 1**
- 핵심 결과: **W-6 는 초록인데 W-7 이 집었다**(「꺼진 단언이 있다 — skipped 2」). 등재하지
  않았다면 그 꺼진 단언이 초록인 채 지나간다. 원복 뒤 둘 다 0

### 2026-11-05T08:45Z — 기존 줄 무편집 확인 (D-5)
- cmd: 등재 전 사본과 등재 뒤 파일의 앞부분을 `diff`
- exit: 0
- 핵심 결과: 차이 없음 — **추가 줄만** 들어갔다

### 2026-11-05T08:50Z — 캡처 재촬영 (D-6, 첫 시도)
- cmd: `npm run capture:usecase` (web)
- exit: 1
- 핵심 결과: 12 장은 찍혔고 **manifest 단계에서 거부** — 「`web/` 에 커밋되지 않은 변경이
  있다」(캡처 스크립트 수정이 아직 미커밋). 계약이 예고한 순서다(커밋 뒤에 촬영)

### 2026-11-05T08:55Z — 캡처 재촬영 (커밋 뒤)
- cmd: `npm run capture:usecase` (web)
- exit: 0
- 핵심 결과: `끝 — 12 장 · 합 1,010,745 바이트`. 여덟 장과 manifest 가 바뀌었다
  (`a-start` 는 시각·금액을 그리지 않고 `d-bid-box` 는 그 건이 「없음」이라 바이트 동일)

### 2026-11-05T09:00Z — 재촬영 산출물을 눈으로 확인
- cmd: `docs/usecase/captures/inbox-desktop.png` · `c-worksheet-desktop.png` 열람
- exit: 0
- 핵심 결과: 알림함 항목 시각이 `2026-11-05 17:28` 꼴 · 워크시트 기초금액이
  `1,500,000,000원 (기초금액 · 과세 구분 모름 · 공고 게시값)` · 마감이 `2026-11-13 17:28 (D-7)`

### 2026-11-05T09:20Z — acceptance 재실측 W-1~W-13
- cmd: CI `web` job 의 명령 그대로
- exit: 전부 0
- 핵심 결과: vitest `733 passed (37 파일)` · 셸 스모크 2 · 여정 11(flaky 0 · 건너뜀 0 · spec 4/4)

### 2026-11-05T09:35Z — S-20 재실측 (첫 시도, RED)
- cmd: `~/.local/bin/bv-build.sh ./tools/one-command-check.sh`
- exit: 1
- 핵심 결과: `leakPatternGate` 가 checklist 의 한 줄을 **baseline 밖 새 매치**로 세웠다 —
  금액 어휘 상수의 이름을 산문에 적은 자리. baseline 에 등재하지 않고 **문장을 고쳤다**

### 2026-11-05T09:40Z — evidence 자기매치 재측정 (참조형)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/screen-display/`
- exit: 1(매치 없음)
- 핵심 결과: 매치 0
