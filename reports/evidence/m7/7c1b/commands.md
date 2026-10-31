# M7/7C①b — 실행 명령과 종료 코드

빌드는 전부 확인 셋(`pgrep` · `free` · `ps`)을 **별도 호출로 먼저** 본 뒤
`flock /home/deploy/.local/bid-vector-build.lock ./gradlew --no-daemon …` 로 돌렸다. 순서대로 적는다(날짜 2026-10-22 KST, 정확한 시각을 남기지 않은 단계는 순번만 적는다).
출력 전문은 싣지 않는다(핵심 결과 한 줄).

## ① 특성화 test 먼저 (추출 전 원래 코드)

`:workflow:test --tests '…EvaluateCandidatesScreeningCharacterizationTest'` → exit 0, 4/4.
ktlint 지적(줄 길이·체인) 정리 뒤 `:workflow:ktlintTestSourceSetCheck` + `EvaluateCandidates*` 를 **추출 파일을
치운 상태**에서 다시 돌렸다 → exit 0(8 · 4 · 14). 그 뒤 특성화 커밋을 확정했다.

## ② 추출 refactor 의 거동 불변

`:workflow:ktlintCheck :workflow:detekt :workflow:test` → exit 0. workflow test 486 · 실패 0 · 건너뜀 0.

## ③ 표적 (workflow 제안 + app 제안·계약·검색)

`:workflow:test --tests 'bidvector.workflow.suggestion.*' :app:test --tests 'bidvector.app.http.Suggestion*' …` → exit 0.
use case 10 · endpoint 7 · E2E 4 · 계약 6 · 기존 계약 15 · 검색 endpoint 15, 실패 0.
E2E 관측값: `[7c1b-cost] candidates=200 elapsedMillis=184`.

## ④ 생성 타입 (W-5)

`web/` 에서 `npm ci` → `npm run api:types` → `npm run api:types:check` → 「바이트까지 같다」, exit 0.
`npm run typecheck` · `npm run lint` · `npm run gate:src-tree` → exit 0.

## ⑤ 전건 `check --continue` (1차, 커밋 전 트리)

exit 1. 잡힌 것 넷 — 처분은 `checklist.md` 4 절.
- detekt `ComplexInterface`
- workflow ktlint `class-signature`
- `AppHttpDependencyGateTest`(주입 표면 · 면제 수)
- `ConstantTimeComparisonStructureTest`(`SuggestionControllerKt` 의 `areEqual`)

수정 뒤 그 넷과 제안 test 의 표적 재실행 → exit 0.

## ⑥ 전건 `check --continue` (구현 커밋 `8cb354e2`)

exit 0, BUILD SUCCESSFUL.

## ⑦ 변이 아홉

`checklist.md` 2 절의 표가 결과다. 아홉 전부 exit 1 이고, 실패는 표의 test 다(컴파일 오류 0). 첫 실행은 실패 test 이름을
거두지 못해(조용한 출력 모드) 결과 XML 에서 거두도록 고쳐 다시 돌렸다.

## 비밀값 스캔

`grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7c1b/` → 빈 출력.

## rollback ①~⑥ · 마지막 HEAD 의 게이트 결과

`rollback.md` 가 적는다.

## 수정 라운드 1 (2026-10-23 ~ 24 KST)

날짜가 Phase 2 와 다르다. 확인 셋은 실행마다 따로 먼저 봤다.

- **표적**(커밋 전 트리, 08:00): `:workflow:test --tests 'bidvector.workflow.suggestion.*' :app:test --tests 'bidvector.app.http.Suggestion*' --tests '…OpenApiSuggestionContractTest'` + 두 모듈 ktlint test·detekt → exit 1. detekt 둘(`SuggestionE2EProbes` 미사용 매개변수 · E2E 줄 길이)이었고 test 는 use case 10 · endpoint 7 · E2E 8 실패 0. 둘을 고친 뒤 test 커밋.
- **W-5**(`web/`): `npm run api:types:check` → 「바이트까지 같다(50843 bytes)」 exit 0 · `typecheck` · `lint` · `gate:src-tree` exit 0.
- **전건 `check --continue`** — rebase 전 `a6ef9cf2`(base `a61ddff1`) exit 0 · **rebase 뒤 `1e46c616`**(base `56bb7474`) exit 0, `leakPatternGate` 실행.
- **변이**: 통제군 + 일곱, `checklist.md` 2 절. rebase 전·뒤 두 번 돌렸고 RED 집합이 같다.
- **rollback ①~⑥**: `rollback.md`. rebase 전(실측 HEAD `4d605859`, base `a61ddff1`)에도 exit 0 이었고, 정본은 rebase 뒤 실측이다.

### rebase 전 ↔ 뒤 SHA (둘째 rebase, 커밋 제목으로 대응)

Phase 2 문면의 SHA(`9ccf656f` · `8cb354e2` · `f255f749` · `38af04b7` 등)는 **첫 rebase 전** 값이고, 같은 제목의 커밋이 아래 「뒤」 열이다.

| 전 | 뒤 | 커밋 |
|---|---|---|
| `344c5aeb` | `8b115f38` | 착수 계약 + Phase 1 |
| `f1132055` | `c095aa59` | 특성화 test |
| `ec06f519` | `8b5180ae` | 게이트 셋 추출 refactor |
| `b48ae507` | `8e71e4a7` | 제안 목록 구현 |
| `599006a3` | `1380882b` | 추적표 |
| `45fd8fe0` | `3504148b` | Phase 2 evidence |
| `6664c772` | `ddf1f065` | 수정 라운드 1 test |
| `4d605859` | `19b34b6c` | 추적표 STR-07(**마지막 산출물 커밋**) |
| `a6ef9cf2` | `1e46c616` | 수정 라운드 1 evidence 초안 |

## 승인 전 일괄 (D-7C1B-4, 2026-10-24 KST, base `998b59dd`)

- **셋째 rebase** → 충돌 하나(추적표 집계 문단, `checklist.md` §10). 이 라운드의 산출물 커밋 `c5a35921`(마지막 산출물 커밋).
- **찬 `check`** `./gradlew --no-daemon check --continue --rerun-tasks --no-build-cache`(HEAD `c5a35921`, 05:20~05:32) → exit 0, `leakPatternGate` 실행.
- **web job**(`web/`, CI 의 step 순서 그대로): `npm ci` · `lint` · `typecheck` · `gate:src-tree` · `api:types:check` · `test`(28) · `gate:tests` · `build` · `gate:boundary-canary` · `test:e2e`(Playwright 2) → 열 step 모두 exit 0.
- **변이**: 통제군 + 여덟(`checklist.md` 2 절). 첫 실행의 M14 허위 RED 와 재실행은 그 절에 적었다.
- **rollback ①~⑦**: `rollback.md`(실측 HEAD `c5a35921`).

### 셋째 rebase 전 ↔ 뒤 SHA

| 전 | 뒤 | 커밋 |
|---|---|---|
| `8b115f38` | `a3af4095` | 착수 계약 + Phase 1 |
| `c095aa59` | `480ee80b` | 특성화 test |
| `8b5180ae` | `59ea4342` | 게이트 셋 추출 refactor |
| `8e71e4a7` | `fcb0876d` | 제안 목록 구현 |
| `1380882b` | `9b3a980b` | 추적표 |
| `3504148b` | `c31d1067` | Phase 2 evidence |
| `ddf1f065` | `b79292be` | 수정 라운드 1 test |
| `19b34b6c` | `d56222db` | 추적표 STR-07 |
| `1e46c616` | `2d003299` | 수정 라운드 1 evidence 초안 |
| `dbd94907` | `8d9554d4` | 수정 라운드 1 evidence(앞 판정 SHA) |
| `4ccb3b73` | `81609dc9` | 계약 D-7C1B-4 |
| `a6a5a693` | `c5a35921` | 승인 전 일괄 test·KDoc(**마지막 산출물 커밋**) |
