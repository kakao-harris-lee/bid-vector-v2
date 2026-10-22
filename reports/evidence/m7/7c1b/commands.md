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
