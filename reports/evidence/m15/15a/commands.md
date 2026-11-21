# M15 / 15A — 실행 명령과 종료 코드

> 명령만 남긴다(출력 전문 금지). 감사자는 명령을 다시 돌린다.
> 마지막 HEAD 의 게이트 결과 정본은 **verifier 와 PR 조치 코멘트**다 — evidence 는 자기 마지막
> 커밋의 post-state 를 담을 수 없다(`evidence-pack` 규격).

## 착수 실측 (base `99f2fb25`)

- cmd: `git rev-parse 99f2fb25` · `git merge-base --is-ancestor 99f2fb25 HEAD`
- exit: 0
- 핵심 결과: base 40자가 `99f2fb25989078d7ab1640de0dc8e399f5d1688d` 이고 HEAD 의 조상이다.

- cmd: `ls adapters/src/main/resources/db/migration | sort -V | tail -1`
- exit: 0
- 핵심 결과: `V27__execution_row_release.sql` — V28 이 다음 번호다(계약 배정과 같다).

## RED — V28 이관 갈래 셋

- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.persistence.AccountScopedMigrationTest'`
- exit: 1
- 핵심 결과: 5 tests, 5 failed — `No migration with a target version 28 could be found`.

## GREEN — V28 + 마이그레이션 래칫 다섯

- cmd: `./gradlew --no-daemon :adapters:test --tests '…AccountScopedMigrationTest' --tests '…CleanMigration*' --tests '…MigrationLockTimeoutTest'`
- exit: 0
- 핵심 결과: AccountScopedMigrationTest 5 · CleanMigration* 22 · MigrationLockTimeoutTest 4 — 실패·건너뜀 0.

## 패키지 순환 — 끊기 전후

- cmd: `./gradlew --no-daemon check --continue` (라운드 1 전건)
- exit: 1
- 핵심 결과: `ArchitectureGateTest > 패키지에 순환이 없다` 가 **순환 넷**을 낸다. 넷 다
  `workflow.access` 로 들어오는 변을 요구하고 그 원천은 `workflow.strategy`(계정 인자) 하나다.
  같은 라운드가 test 35 건을 함께 냈다(아래 「라운드별 전건」 r1).

- cmd: `./gradlew --no-daemon compileKotlin compileTestKotlin --continue` (식별자 이사 직후)
- exit: 0
- 핵심 결과: 모듈 아홉 전건. 한 타입의 패키지를 옮긴 편집 195 파일이 컴파일로 닫혔다.

## acceptance ① — Kotlin `check` 전건

- cmd: `./gradlew --no-daemon check --continue`
- exit: 0
- 핵심 결과: **BUILD SUCCESSFUL in 38m 40s** — 실패 task 0 · 실패 test 0
  (433 actionable tasks). 라운드별 추이는 아래.

## acceptance ② — `web` job 전건 (라운드 4 에 들었다)

> 왜 들었는지는 `scope.md` 「web 접촉」. 아래는 CI `web` job 의 step 순서 그대로다.

- cmd: `npm ci` (W-1)
- exit: 0

- cmd: `npm run lint` · `npm run typecheck` · `npm run gate:src-tree` (W-2·3·4)
- exit: 0 · 0 · 0

- cmd: `npm run api:types:check` (W-5)
- exit: 0
- 핵심 결과: **생성 타입이 OpenAPI 문서와 바이트까지 같다(195341 bytes).** 이 slice 의 OpenAPI
  변경이 `security:` 선언뿐이라는 주장이 여기서 측정으로 선다(착수 때는 예측이었다).

- cmd: `npm run test` · `npm run gate:tests` (W-6·7)
- exit: 0 · 0
- 핵심 결과: 74 파일 1997 test 통과 · 등록부의 게이트 test 가 그 수집에 있고 skipped/todo 0.

- cmd: `npm run build` · `npm run gate:boundary-canary` (W-8·9)
- exit: 0 · 0

- cmd: `npx playwright install chromium` · `npm run test:e2e` (W-10·11)
- exit: 0 · 0
- 핵심 결과: 셸 스모크 39/39.

- cmd: `./gradlew --no-daemon :app:bootJar` (W-12)
- exit: 0

- cmd: `npm run test:e2e:journey` (W-13)
- exit: **1 → 0**
- 핵심 결과: 첫 실행은 하네스가 기동 중에 죽어 여정이 **하나도 수집되지 않았다** —
  `전략 심기 실패: POST /api/strategy/edit-sessions → 401`. 하네스를 여정 계정의 세션으로
  옮긴 뒤 **28/28 통과**이고 하네스가 `전략 여력 상한 5 적용(개정 1)` 을 낸다.

## 라운드별 전건 추이

| 라운드 | 명령 | exit | 실패 task | 실패 test |
| --- | --- | --- | --- | --- |
| r1 | `check --continue` | 1 | 9 | 35 |
| r2 | `check --continue` | 1 | 5 | 3 |
| r3 | `check --continue` | 0 | 0 | 0 |

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 바꾼 경로 전수>`
- exit: 0 (매치 있음)
- 핵심 결과: 바뀐 파일 전체로는 77 줄이 걸리지만, **이 slice 가 더한 줄** 가운데 걸리는 것은
  **둘**이고 둘 다 Testcontainers 의 합성 비밀번호와 그 컨테이너 객체의 접근자다(저장소가
  마이그레이션 test 마다 쓰는 같은 관용구, 값은 `*_test_only` 꼴). 판정의 정본은 이 grep 이
  아니라 `check` 안의 `leakPatternGate` 이고 그것이 통과했다(acceptance ①).
- 어휘를 이 문서에 축어로 적지 않으려고 패턴 파일 참조형으로 돌렸다. Telegram id·사업자 정보는
  패턴으로 못 잡으므로 육안 확인을 병기한다: 이 slice 의 diff 에 외부 식별자·실 계정 값이 0 이고,
  test 가 심는 값은 전부 `fixture-`·`합성`·`e2e-`·`journey-` 접두 합성값이다.

## 마지막 HEAD

evidence 를 편집하는 커밋의 post-state 는 이 문서가 담을 수 없다. 마지막 HEAD 의 `check`
결과는 **verifier 리포트와 PR 조치 코멘트**가 정본이다(`evidence-pack` 규격).
