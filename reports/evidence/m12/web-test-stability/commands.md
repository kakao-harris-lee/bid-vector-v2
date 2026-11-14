# M12 / web-test-stability — 실행 명령과 종료 코드

출력 전문을 붙이지 않는다. 핵심 결과는 한 줄이고, **그 수를 낸 조건(표적·부하·횟수)을 같은
줄에 적는다** — 구조나 부하가 바뀌면 다시 재야 하는 수이기 때문이다.

## ② 게이트 test 칸 예산 — 조건 셋, 가장 센 부하는 20회

세 조건이 **같은 명령**을 돌리고 조건만 다르다 — `npx vitest run --reporter=json --outputFile=…`
(987 test / 234 파일, `npm run test` 와 같은 수집). 측정 하네스는 레인 scratch 에 둔다.
**이 측정은 예비다** — 12C 가 그 모듈 그래프를 바꾸므로 최종은 12C 머지 뒤 main 에서 다시 잰다.

### 2026-11-14 조건 A — 16코어, 다른 레인(12C) vitest 와 일부 겹침
- cmd: `measure-cells.sh idle 10`
- exit: 0 (10/10, 실패한 run 0)
- 핵심 결과: 후보 둘의 **첫 칸 4~16ms**, 최대 칸 `kotlin-source-ownership` 1308ms(예산 5000ms 의 3.8배 여유) · `money-vocab-cross-lang` 319ms(15.7배)

### 2026-11-14 조건 B — `taskset -c 0,1`(2코어 고정)
- cmd: `measure-cells.sh cpu2 10 0,1`
- exit: 0 (10/10, 실패한 run 0)
- 핵심 결과: 칸 시간이 **내려간다**(최대 칸 353ms · 48ms) — vitest 가 affinity 를 보고 fork 를 둘만 띄워 칸 안의 경합이 줄기 때문이고, 벽시계는 반대로 10s → 39s 다

### 2026-11-14 조건 C — 전건 vitest **둘 동시**(레인 둘이 겹치는 꼴, fork 배수, loadavg 14~44), 10+10회
- cmd: `measure-pair.sh pairA 10` · `measure-pair.sh pairB 10`
- exit: 0 (20/20, primary·noise 모두 실패한 run 0)
- 핵심 결과: 첫 칸은 여전히 **2~25ms**, 20회 합산 최악 칸 `kotlin-source-ownership` 2342ms(**2.1배 여유** — 3배 문턱 밑이라 신설 OPEN) · `money-vocab-cross-lang` 421ms(11.9배) · 대조로 둔 `display-gate-selectors`(11E 가 고친 파일) 1042ms(4.8배)

## ① 여정 e2e — 착수 기준선 (대조의 통제군)

### 2026-11-14 출하 jar
- cmd: `bv-build.sh ./gradlew --no-daemon :app:bootJar` (W-12)
- exit: 0
- 핵심 결과: `app/build/libs/app.jar` — 이 slice 는 Kotlin 을 편집하지 않으므로 이 jar 가 대조 전후에 같다

### 2026-11-14 착수 HEAD 의 여정 레인 전건
- cmd: `npm run test:e2e:journey` (W-13)
- exit: 0
- 핵심 결과: 통과 12 · flaky 0 · 건너뜀 0 · spec 4/4, 1.6m — 뒤의 RED 가 주입 때문임을 이 통제군이 든다

### 2026-11-14 경합 재현 — 구판 단언에 주입을 걸었다 (주입은 커밋하지 않는다)
- cmd: `npx playwright test --config=playwright.journey.config.ts -g '여정 ③'` + 주입(기록 쓰기가 보이기 **전에 출발한** 타임라인 읽기만 6s 늦춘다)
- exit: 1
- 핵심 결과: `expect(locator).toHaveCount(expected) failed` — **Expected 2 · Received 3**, PR #157 첫 CI 와 같은 서명이고 간헐이 아니라 결정적이다

주입의 전제(실측): 질의 함수가 `signal` 을 쓰지 않으므로 react-query 의 취소는 **전선의 응답을
막지 않는다** — 늦은 응답은 언제나 도착하고, 그래서 거를 수 있는 축은 **요청 출발 순서**뿐이다.

### 2026-11-14 신판 + 같은 주입 — 두 쪽을 다 잰다
- cmd: 같은 명령 · 같은 주입, 산출물 쪽
- exit: 0
- 핵심 결과: 1 passed — 구판을 결정적으로 붉히는 그 주입 아래 **신판이 초록**이다(음성 대조)

## 변이 — 전부 바꿔치우기, 커밋 뒤에 돌렸다

### 2026-11-14 M1 — 읽기 술어를 구판으로 바꿔치운다 (주입 유지)
- cmd: 같은 명령 · 같은 주입 · `readsAfterWrite` 의 읽기 술어를 「경로+200」으로
- exit: 1
- 핵심 결과: `포착한 타임라인이 그 기록 뒤가 아니다` — **Expected 2 · Received 1**, `caseVersion` 등식이 줄 수 대조보다 먼저 터진다

### 2026-11-14 M2 — `driveToRecorded` 의 「거둔다」를 지운다 (여정 ④ · 출발 시각 계측)
- cmd: `npx playwright test --config=playwright.journey.config.ts -g '여정 ④'` + 포착한 요청의 출발 시각이 탐색 클릭보다 뒤인지 계측(계측은 커밋하지 않는다)
- exit: 1 → (거둠 복원) 0
- 핵심 결과: 거둠이 없으면 포착이 **클릭보다 3.3ms 앞서 출발한** 읽기를 집는다. 거두면 같은 계측이 초록이다

### 2026-11-14 M3 — `ensureInBidBox` 의 「거둔다」가 없던 상태 (여정 ③ · 같은 계측)
- cmd: `… -g '여정 ③'` + 같은 계측(워크시트 건 목록 포착)
- exit: 1 → (거둠 추가) 0
- 핵심 결과: 담기 쓰기가 낳은 재조회가 **클릭보다 1.6ms 앞서** 출발해 걸린다 — 착수 보고에 없던 자리이고 전수 확인에서 나왔다

## 전건 acceptance — CI `web` job 그대로

코드 마지막 커밋에서 열을 차례로 돌렸다. `npm ci`(W-1)·`:app:bootJar`(W-12)는 착수 HEAD 의
것을 쓴다(잠금 파일·Kotlin 편집 0 — `checklist.md` 알려진 제한).

### 2026-11-14 W-2 ~ W-13
- cmd: `npm run lint` · `typecheck` · `gate:src-tree` · `api:types:check` · `test` · `gate:tests` · `build` · `gate:boundary-canary` · `test:e2e` · `test:e2e:journey`
- exit: 0 (열 모두)
- 핵심 결과: 여정 레인 통과 12 · flaky 0 · 건너뜀 0 · spec 4/4

### 2026-11-14 Kotlin `check` — evidence 가 leak-pattern 게이트의 스캔 뿌리에 든다
- cmd: `bv-build.sh ./gradlew --no-daemon check`
- exit: 동결 보고가 든다
- 핵심 결과: 마지막 HEAD 의 정본은 **verifier 와 PR 조치 코멘트**다(CLAUDE.md — evidence 에 최종 수를 넣으려는 시도가 커밋을 또 낳는다)

## ② 판정 근거

정본은 `checklist.md` ② 다. 요지: 구속 조건은 CPU 희소가 아니라 **fork 과다구독**이고(조건 B 가
가른다), 후보 둘의 **첫 칸은 어느 조건에서도 기동 비용을 지지 않는다**.
