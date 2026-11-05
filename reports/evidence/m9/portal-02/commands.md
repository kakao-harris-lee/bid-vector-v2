# M9 / portal-02 — 실행 명령과 종료 코드

base `79f3e5c4` · 브랜치 `feature/m9-portal-02` · worktree `bid-vector-v2-m9-portal-02`.
실 조달청 호출 0 — 모든 KONEPS 응답은 loopback in-process mock 이다.

## 2026-11-05T17:30+09:00 — RED ①(D-3 라우팅)
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon :app:test --tests 'bidvector.app.collection.MockKonepsRoutingTest'`
- exit: 1
- 핵심 결과: 2 tests, 1 failed — 표에 없는 오퍼레이션이 `expected:<404> but was:<200>`(접두 라우팅이 공고 목록 응답을 조용히 냈다).

## 2026-11-05T17:52+09:00 — GREEN ①
- cmd: 위 명령 + `--tests 'bidvector.app.collection.CollectionRunnerE2ETest' --tests '…OpeningCollectionE2ETest' --tests '…OpeningPageSizeE2ETest' --tests '…OpeningBudgetE2ETest'`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 정확 일치 표로 바꾼 뒤 수집 E2E 전부 초록.

## 2026-11-05T18:02+09:00 — 정확 일치 표 아래 adapters 축
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.koneps.*'`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — `createContext` 접두 일치를 뿌리 + 경로 정확 비교로 바꾼 뒤 초록.

## 2026-11-05T18:14+09:00 — RED ②(D-1 기본값 문면)
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon :app:test --tests 'bidvector.app.wiring.KonepsEndpointDefaultsTest'`
- exit: 1
- 핵심 결과: 5 tests, 4 failed — base·공고 목록 둘·A값·기초금액 둘이 `02` 없이 왔다. **낙찰정보서비스 불변 test 는 처음부터 초록**(통제군: 이 slice 가 그 축을 움직이지 않는다).

## 2026-11-05T18:26+09:00 — GREEN ②
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon :app:test --tests 'bidvector.app.wiring.KonepsEndpointDefaultsTest' --tests 'bidvector.app.collection.*' --tests 'bidvector.app.wiring.CollectionWiringTest' --tests 'bidvector.app.wiring.OpeningCollectionWiringTest'`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 107 tests 초록. `OpeningPageSizeE2ETest` 의 축 분류기도 같은 표로 옮긴 뒤다(접미 술어가 `02` 에서 기초금액·산식 A 를 「그 밖」으로 떨어뜨려 두 축의 단언이 공집합에서 참이 되던 자리).

## 2026-11-05T18:27+09:00 — 옛 base 리터럴 계수(acceptance)
- cmd: `git grep -n 'BidPublicInfoService"' -- '*.kt' '*.kts' '*.yml' '*.yaml' '*.properties'`
- exit: 1 (일치 0)
- 핵심 결과: 옛 base 리터럴 0건. 같은 자리에서 main source 의 옛 오퍼레이션 리터럴도 0건.

## 2026-11-05T20:50+09:00 — 전건 check ①(RED: 스타일·크기 게이트)
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:app:detekt` 2건 — `MatchingDeclarationName`(합성 표본 파일명) · `ReturnCount`(`respond` 반환 3). test 축은 전부 초록이었다.

## 2026-11-05T21:35+09:00 — 스타일·크기 표적 라운드
- cmd: `~/.local/bin/bv-build.sh bash -c "… ./gradlew --no-daemon :app:detekt :adapters:detekt :app:sizeGate :adapters:sizeGate :app:ktlintCheck :adapters:ktlintCheck"`
- exit: 0 (앞선 세 번은 1 — ktlint `when-entry-bracing` 9 · 함수 50줄 `itemsOf` 51 · 파일 500줄 505)
- 핵심 결과: 네 지적을 추출·분할로 닫은 뒤 여섯 task 전부 초록.

## 2026-11-05T22:30+09:00 — **전건 check ②(acceptance)**
- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon check`
- exit: **0**
- 핵심 결과: BUILD SUCCESSFUL in 33m 30s · 392 tasks.

## 2026-11-05T22:40+09:00 — 변이 ①(기본 경로 하나를 옛 이름으로)
- cmd: `…:app:test --tests 'bidvector.app.wiring.KonepsEndpointDefaultsTest'` (`…Cnstwk02` → `…Cnstwk` 치환 뒤)
- exit: 1
- 핵심 결과: 5 tests, **2 failed** — 열거(공고 목록 둘)와 경계(두 서비스 집합 분리) 양쪽이 잡았다. 변이 파일만 `git restore`.

## 2026-11-05T22:45+09:00 — 변이 ②(mock 을 접두 라우팅으로)
- cmd: `…:app:test --tests 'bidvector.app.collection.MockKonepsRoutingTest' --tests 'bidvector.app.collection.OpeningCollectionE2ETest'` (정확 일치 → `startsWith(name.removeSuffix("02"))` 치환 뒤)
- exit: 1
- 핵심 결과: 7 tests, **2 failed** — 기초금액 요청이 공고 목록 축으로 갔고(`noticeListCalls` 에 `…CnstwkBsisAmount02` 가 섞였다) **E2E 「표본에 뽑힌 공고만 상세를 부른다」도 함께 붉다**. 변이 파일만 `git restore`.

## 2026-11-05T22:50+09:00 — rollback ①②③ (버릴 clone, 실측 HEAD `24bd2803`)
- cmd: `git restore --source=79f3e5c4 --staged --worktree -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: D 5 · M 9 · in_scope 코드·문서의 `git diff 79f3e5c4` 빈 출력 · 복원 목록 등식(`comm -23`)은 evidence 한 경로만 남긴다.

## 2026-11-05T23:55+09:00 — rollback ④⑤⑥ (되돌린 트리, evidence 디렉터리를 남긴 채)
- cmd: `~/.local/bin/bv-build.sh bash -c "cd <버릴 clone> && ./gradlew --no-daemon check"`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 30m 37s — 되돌린 트리에서 compile·test·게이트 전부 초록(`leakPatternGate` 가 `reports/evidence` 를 입력으로 잡으므로 base 결과로 갈음하지 않고 실제로 돌렸다).

## 2026-11-06T00:00+09:00 — evidence 점검
- cmd: `wc -l reports/evidence/m9/portal-02/*.md` · `git diff --numstat 79f3e5c4..HEAD -- app adapters config docs`
- exit: 0
- 핵심 결과: evidence 262줄 대 산출물 785줄(크기 게이트 통과). 참조형 누출 스캔(`grep -rniE -f config/quality/leak-patterns.txt`)은 evidence·runbook 에서 0건.

## 2026-11-06T00:30+09:00 — web fake KONEPS 라우팅 실측 (계약 갱신분, docker·playwright 없이)
- cmd: `node e2e/harness/fake-koneps.mjs <port>` 뒤 출하 기본 경로와 옛 이름으로 `curl -o /dev/null -w '%{http_code}'`
- exit: 0
- 핵심 결과: `…Cnstwk02` → **200**(`resultCode 00` · 항목 4) · 옛 이름 → **404**. fixture 를 옛 이름으로 둔 변이에서는 **출하 기본 경로가 404** 다 — W-13 여정이 붉어지는 기구가 이 한 자리다.

## 2026-11-06T00:40+09:00 — rollback ①②③ 재산출 (실측 HEAD `a49738d3`)
- cmd: `git restore --source=79f3e5c4 --staged --worktree -- <in_scope 경로 개별 인자, web fixture 포함>`
- exit: 0
- 핵심 결과: D 5 · M 10 · 되돌린 트리의 `git diff 79f3e5c4 -- app adapters config docs web` 빈 출력(= base 와 바이트 동일) · 내 표지 0 · base 의 web fixture 옛 이름 4 복원.

## 2026-11-05T13:05Z — PR CI 라운드 1 (run 42025625713, HEAD `bea3a225`)
- cmd: `gh pr checks 130` · `gh api .../actions/jobs/127238986505`
- exit: 1 (`web` fail · `container`·`ml-engine` pass · `check` 중단)
- 핵심 결과: W-1~W-12 초록, **step 20 「여정 e2e (W-13)」 하나가 붉다** — 로그가 「fake KONEPS 를 30000ms 안에 기다리지 못했다」와 「요청 75건 받고 종료」다. 수집이 아니라 **준비 대기**가 영구 404 로 죽었다(이름 사본 셋째).

## 2026-11-05T14:10+09:00 — 준비 대기 교체 실측 (node, docker·playwright 없이)
- cmd: fake 단독 기동 뒤 `curl -D -` 로 루트·출하 기본 경로·옛 이름 / `node -e` 로 probe 식 그대로
- exit: 0
- 핵심 결과: 루트 `404` + 표지 `x-fake-koneps` → **준비 판정 true** · 출하 기본 경로 `200` · 옛 이름 `404`(표지 있음). **음성 대조**: 표지 없는 남의 서버 `200` → 준비 판정 **false**(포트 오점유를 가른다). `import` 안전 확인도 같은 자리.

## 2026-11-05T14:25+09:00 — main 병합 — **움직이는 ref 가 물었다**
- cmd: `git fetch origin` → `git log -1 origin/main`(= `4953d743`) → `git merge --no-edit origin/main`
- exit: 0 (충돌 0)
- 핵심 결과: 병합된 둘째 부모가 **`319fe212`**(PR #124 머지)다 — fetch 와 merge 사이에 공유 ref `origin/main` 이 `4953d743` → `319fe212` 로 움직였다. 그래서 **rollback 복원 원천은 `4953d743` 이 아니라 실제로 병합한 `319fe212`** 다. 내 표지 계수·줄 수는 병합 전후 동일(probe 2 · setHeader 1 · fixture 4 · 449/91 줄).

## 2026-11-05T14:40+09:00 — 겹침 계수 (합집합 함정)
- cmd: `comm -12 <내 비-병합 커밋 경로(^319fe212)> <main 이 만진 경로>`
- exit: 0
- 핵심 결과: 겹치는 공유 파일 **둘** — `config/quality/gate-tests.properties` · `docs/discovery/capability-map.md`. 내 세 web 하네스 파일을 main 이 만진 커밋은 **0** 이다.

## 2026-11-05T14:50+09:00 — rollback 재산출 (실측 HEAD `7f51bdc0`, 원천 `319fe212`)
- cmd: `git restore --source=319fe212 --staged --worktree -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: D 5 · M 12 · 결과 트리가 `319fe212` 와 **바이트 동일**(`git diff 319fe212 -- app adapters config docs web` 빈 출력). **내 표지 0**(기본값·라우팅 표·게이트 등재·OPEN 행·fixture·표지 전부) · **남의 표지 보존**(#124 게이트 등재 1 · #124 OPEN 행 2 · #125 `web/e2e/journey/shared.ts` 있음 · 옛 probe 이름 1 복원).

## 2026-11-05T15:00+09:00 — evidence 점검 (라운드 2)
- cmd: 참조형 누출 스캔 `grep -rniE -f config/quality/leak-patterns.txt` · 내 비-병합 커밋의 `git show --numstat`
- exit: 0
- 핵심 결과: evidence **0건**. 편집한 web 하네스 둘 중 `journey-up.mjs` 는 13건이지만 **병합한 main 쪽도 같은 13건**이라 내가 들인 줄이 아니다(`fake-koneps.mjs` 는 양쪽 0). 크기 게이트: evidence 409줄 대 산출물 866줄.
