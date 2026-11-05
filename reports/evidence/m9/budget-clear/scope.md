# M9 / budget-clear — 금액대 비우기 · STR-02 요건 축 정리 · 조립 장부 (OPEN-7E2B-BUDGET-CLEAR · OPEN-6F4-STR02-REQUIREMENTS-AXIS(LLM 없는 몫) · OPEN-6F-ASSEMBLY 계열 장부)

- base: main `79f3e5c4` · 브랜치 `feature/m9-budget-clear` · worktree `bid-vector-v2-m9-budget-clear`
- 근거: `reports/evidence/m7/7e2b/scope.md` D-7E2b-9 · `milestone-6.md` 6F-4 결정 A · `reports/evidence/m6/closure/open-inventory.md` §2.
- 사용자 지시 2026-11-05(전체 진행, 추천안) · 사용자 결정 2026-11-05: 6F-5-b LLM 추출 **보류**.

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | **비우기 계약**: 편집 값 요청에서 `MIN_BUDGET`·`MAX_BUDGET` 필드에 한해 `amountWon: null` 을 **명시적 비움**으로 받는다. 칸 생략은 여전히 400(`INVALID_REQUEST`). 다른 필드의 `null` 도 400 유지. Kotlin 은 `EditValue` 에 비움 variant(예 `Cleared`)를 더하고 `StrategyFieldValue` 가 `minBudget/maxBudget = null` 로 적용. **마이그레이션 0**(도메인·DB 이미 nullable). openapi 의 `EditSessionValueRequest` 절만. |
| D-2 | 비움 뒤 귀결: `readStrategy` 가 null · 제안이 `WATCH_NOT_CONFIGURED` 로 · 온보딩 체크리스트 `BUDGET_RANGE` pending 복귀 · `BudgetLimitNotComparable` 미발생. 웹 설정 폼·화면 A 에 「비우기」 동작. 여정 ①(`web/e2e/journey/onboarding.spec.ts`)이 D-7E2b-9 로 「걷되 적용 안 함」으로 묶어 둔 단계를 **실제 적용**으로 바꾸되, 다른 여정의 출발 단언을 깨지 않게 되돌림까지 단언. |
| D-3 | **STR-02 요건 축**: 요건 원문 출처는 LLM 추출(보류)·본문 출처(`OPEN-6F4-NOTICE-BODY-SOURCE`)에 걸려 닫히지 않는다. 이 slice 는 ⓐ capability-map STR-02 행과 `OPEN-6F4-STR02-REQUIREMENTS-AXIS` 행에 「LLM 보류(2026-11-05)로 축소 유지」와 받는 자리(6F-5-b)를 적고 ⓑ 「요건 원문이 감시 텍스트에 실리는 production 경로 0」 ArchUnit 단언이 살아 있음을 확인만 한다. 코드 변경 0. |
| D-4 | **조립 장부**: `OPEN-6F-ASSEMBLY`·`OPEN-6F4W-ASSEMBLE-CALLER` 는 이미 닫혔다(6A-3f3). `open-inventory.md` §2 의 그 행을 실측으로 좁힌다 — 남은 것은 재확인 ④(합성 `toString` 진단 경로) · `OPEN-6F4W-UNAVAILABLE-PRODUCER` · `OPEN-6F4-TITLE-INGEST` · `OPEN-6F10-*`. 이 slice 는 장부 문면만(뒤의 것들은 다른 M9 slice 가 받는다). 또 같은 문서 §2 의 「운영자 결정 선행 slice 셋 … 셋 다 M6 안에서 열리지 않았다」는 **6B-4 가 PR #71 로 머지**됐으므로 사실 정정(`OPEN-5E-JOB-PERSISTENCE` 닫힘 후보, `OPEN-5E-JOB-QUEUE-BOUND` 부분 종결 — `reports/evidence/m6/6b4/checklist.md` 근거). 닫힌 evidence 는 append 형식(정정 문단)으로. |

## in_scope

- `openapi/bidvector-operator-api.yaml` (EditSession 절만) · `web/src/api/generated/**`
- `app/src/main/kotlin/bidvector/app/http/StrategyEditRequests.kt` · `app/src/main/kotlin/bidvector/app/wiring/StrategyFieldValue.kt` · `workflow/src/main/kotlin/bidvector/workflow/strategy/**`(EditValue 가 거기 있으면) 와 각 test
- `web/src/display/editSessionDisplay.ts` · 설정 폼/화면 A 파일(`web/src/screens/` 안 해당 파일만 — 시각·금액 표시 레인과 겹치면 멈추고 보고) · `web/e2e/journey/onboarding.spec.ts`
- `docs/discovery/capability-map.md` (STR-02 · 해당 OPEN 행) · `reports/evidence/m6/closure/open-inventory.md` (정정 문단) · `milestone-6.md` (「배선 밖 잔여」 6B-4 정정 한 줄)
- `config/quality/**` 게이트 등재 · `reports/evidence/m9/budget-clear/**`

## out_scope

마이그레이션 · 요건 원문 수집·LLM · 감시 커널(`strategy/.../Text.kt`·`WatchRules.kt`) 변경 · 알림함 어휘.

## acceptance

- CI Kotlin `check` · `web` job 명령 그대로 exit 0.
- test: 비움 200 + 귀결 넷(D-2) · 생략 400 · 다른 필드 null 400 · openapi enum ↔ Kotlin 집합 등식 유지.
- 변이: 비움 variant 를 0 원으로 바꿔치움 → RED · 생략도 비움으로 받게 → RED.

## rollback

in_scope 경로 한정 `git restore --source=79f3e5c4 …` + 새 파일 삭제; 목록 기계 산출(`rollback.md`).

## 하네스 레인 변경

없음.

## 계약 갱신 2026-11-05 (팀장)

- in_scope 에 `app/src/main/kotlin/bidvector/app/http/JsonValueReaders.kt` 를 더한다 — 첫 판이 거기에 헬퍼를 넣었다가 `c36a01e1` 에서 base 와 같게 되돌렸다(순 diff 0). `--no-merges --name-only` 합집합 술어가 그 경로를 내므로 계약이 받아 둔다. 거동 영향 0.
- 「비움 = 빈 칸 + 값 보내기」(전용 버튼 없음)는 D-2 의 「비우기 동작」으로 받는다.

## 계약 갱신 2026-11-05 r2 (팀장 — PR #124 수정 라운드)

- **D-4 문면 정정(팀장 실수)**: D-4 가 조립 계열의 잔여로 든 `OPEN-6F4-TITLE-INGEST` 는 **이미 닫혔다** —
  6F-8 이 수령·닫음(`reports/evidence/m6/6f8/scope.md` OPEN 표 · D-6F8-2 · milestone-6 「6F-8 종결 2026-09-24 —
  PR #45」). 그 오류가 `open-inventory.md` §5 로 옮겨 갔고, 정정은 같은 문서의 §6(날짜 붙은 덧붙임)이 한다.
  `OPEN-6F10-*` 와일드카드도 같은 자리에서 실제 식별자로 바꾼다.
- in_scope 에 test 두 파일을 **이름으로** 등재한다(v L-2 — 그 전에는 「각 test」로만 읽혔다):
  `app/src/test/kotlin/bidvector/app/architecture/EditableFieldVocabularyGateTest.kt` ·
  `web/src/display/editSessionDisplay.test.ts`. 수정 라운드가 더하는 셋도 같은 자리로 받는다:
  `app/src/test/kotlin/bidvector/app/http/StrategyBudgetClearEndpointTest.kt`(신설, 첫 판) ·
  `web/src/screens/SettingsScreen.test.tsx`(cr L-5 — 설정 화면의 금액 비우기 한 건).
- `web/src/screens/SettingsScreen.test.tsx` 는 **웹 화면 표시 레인과 겹칠 수 있는 파일**이다. 이 slice 는
  test 하나를 더하는 것뿐이고 같은 파일의 다른 줄을 건드리지 않는다.
