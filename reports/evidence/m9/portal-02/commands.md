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
