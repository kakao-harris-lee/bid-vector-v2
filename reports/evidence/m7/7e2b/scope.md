# M7/7E-2b — 화면 A 시작하기 + 여정 ① (착수 계약, 2026-10-30, 팀장)

- **근거**: 운영자 지시 2026-10-30(「7C④-3 머지 끝나면 7E-2b 착수」). 7E 분할의 갱신(7E-2 계약 「근거」): **7E-2b = A 시작하기 ·
  여정 ①**(7C④-3 뒤), 7E-3 = E · 알림함 · 여정 ⑤ · 캡처. 선행 operation `readOnboardingChecklist`(M7/7C④-3, PR #106)가 main 에 들어왔다.
- **base**: main **`1e387499`**(PR #106 머지, 2026-10-30 고정) · worktree `bid-vector-v2-m7-7e2b` · 브랜치 `m7-7e2b/2026-10-30`. rollback 복원
  기준도 `1e387499`(전제: 그 SHA 에 이 slice 없음 — 첫 산출물 커밋에서 단언).
- **입력 정본**: 7E-1 계약 `reports/evidence/m7/7e1/scope.md`(D-7E-1~10 — 하네스·시간·상태 표시·§6.5·개인정보·CI 규율은 **그대로 구속**) ·
  7E-2 계약 `reports/evidence/m7/7e2/scope.md`(D-7E2-2 편집 세션 · D-7E2-3 프로필 · D-7E2-4 빈 자리 · D-7E2-7 사업자번호) · 7C④-3 계약
  `reports/evidence/m7/7c4c/scope.md`(세 목록의 의미) · `docs/discovery/m7-screen-data-requirements.md` 「A. 시작하기」 ·
  `docs/discovery/ux-journey-research.md` §6.2 A · `docs/adr/0011-web-frontend-stack.md`.
- **레인**: 구현 하나(`kotlin-implementer` — 하네스가 app.jar·Gradle 을 다룬다). 판정 `verifier`(opus) + `code-reviewer`(sonnet) 병렬.
  Codex 대상 아님.

## 화면 A 가 쓰는 operation (base `1e387499` OpenAPI 에서 — 이 밖을 부르지 않는다)

- `GET /api/onboarding-checklist`(새로 승인 목록에 더한다).
- 항목을 채우는 쓰기는 **7E-2 설정 화면이 이미 승인 목록에 둔 operation 만** — 프로필(`/api/profile` 읽기 · `qualification-facts` ·
  `business-details` · `construction-capacities` 읽기·추가·제거) · 전략 편집 세션 여섯. 승인 목록에 새로 더하는 것은 체크리스트 하나뿐이어야
  한다(착수 보고에서 실측 — 더 필요하면 보고).
- 부족한 API 는 **만들지 않는다**(Kotlin `src/main` · OpenAPI out) — 보고한다.

## 팀장 결정 (추천안 — 착수 보고 실측이 반박하면 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7E2b-1 서버 판정만 표시** | 항목의 완료·미완·선행 대기·해당 없음은 **`readOnboardingChecklist` 응답에서만** 나온다. 해당 없음 = 닫힌 어휘에서 세 목록의 여집합(OpenAPI 문면 그대로). 진행률 = `completed` / (`completed` + `pending`), `awaiting` 은 분자·분모 밖. 화면이 프로필·전략 값을 보고 완료를 추론하는 코드 0(정적 게이트 또는 test 로 잠근다 — 착수 보고 설계 검토에서 정한다). 쓰기 성공 뒤에는 체크리스트를 **다시 읽어** 갱신한다(낙관적 완료 표시 금지). |
| **D-7E2b-2 마법사 하나 · 폼은 설정과 하나** | A 는 항목 순서(서버가 정한 선언 순서)대로 걷는 마법사 하나다. 각 항목의 입력 폼은 **7E-2 설정 화면의 같은 컴포넌트(`ProfileSection`·`StrategySection`)를 재사용**한다(같은 필드를 두 곳에서 저장하지 않는다 — UX §6.2 A 「설정에서 같은 폼으로 수정」). 항목 → 폼 대응표(표시용 상수, 판정 아님)는 착수 보고에서 확정하고, 어휘 8개 전수 대응을 test 로 잠근다(OpenAPI enum 에서 파생 — 새 항목이 생기면 RED). |
| **D-7E2b-3 빈 자리** | 사업자번호·국세청 확인(D-7E2-7) · 알림 채널 선택(R-4) · 첫 공고의 「어느 입력이 좁혔는가」(`OPEN-6A3-EVALUATION-DETAIL`) 는 빈 자리 + 「준비 중」. 입력칸·버튼을 만들지 않는다. |
| **D-7E2b-4 다음 행동 하나** | 마법사 끝(미완 0)은 화면 B(오늘의 공고)로 가는 행동 하나. 미완이 있으면 셸·홈에서 A 로 가는 진입 하나(배너 또는 링크 — 서버 `pending` 이 비어 있지 않을 때만). 첫 공고 미리보기는 B 로의 이동으로 갈음한다(미리보기 전용 API 를 만들지 않는다). |
| **D-7E2b-5 여정 ①** | 7E-1 하네스 재사용(실 app.jar · fake KONEPS · 시각 조작 0). 빈 계정 → A 진입 · 미완 목록(DOM ↔ 같은 실행 API 응답 대조) → 마법사로 항목 채움 → 체크리스트 완료 · 선행 대기 짝 표시 단언 → B 로 이동. **여정 순서 독립**: 끝에서 채운 값을 되돌리고 되돌림까지 단언한다(7E-2 D-7E2-9 와 같은 방식). 되돌릴 operation 이 없는 값(예: 지울 수 없는 프로필 칸)이 있으면 그때 보고 — 우회 fixture 를 만들지 않는다. |
| **D-7E2b-6 7E-2 잔여** | 7E-2 checklist 의 OPEN(⑥~⑨)은 이 slice 범위가 아니다 — A 가 그 자리를 새로 넓히면(같은 게이트 술어를 쓰는 새 파일) 착수 보고에 적는다. |

## 설계 검토 (Phase 2.5 — 레인이 착수 보고에 채운다, 구현 전)

- (0) 위협 모델 경계: 방어 — 클라이언트가 체크리스트 상태를 추론하는 것 · 세 상태가 두 상태로 접히는 표시(awaiting 을 미완으로 셈) · 같은
  필드를 두 폼이 따로 저장하는 것 · 개인정보(D-7E-6) 노출. 방어하지 않음 — 서버 판정의 옳고 그름(7C④-3).
- (1) 열거인가 구성인가 · (2) 우회 ≥5(예: 응답 대신 프로필 값으로 완료 판정 · 쓰기 성공으로 낙관 완료 · awaiting 을 pending 에 합침 ·
  해당 없음을 미완으로 표시 · 폼 복제) · (2b) 값 획득 축(새 public 표면 — 컴포넌트·hook·승인 목록 행 전수) · (3) 과잉·미달.

## in_scope

- `web/**`(화면 A · 공통 컴포넌트 확장 · `routes.tsx` 진입 · vitest · e2e 여정 ① · 하네스 · 게이트 스크립트) · `.github/workflows/ci.yml` 의
  `web` job(여정 추가 시) · `tools/one-command-check.sh`(축어 일치) · e2e fixture(출처·해시) · `reports/evidence/m7/7e2b/**` · 요구표 A 행 판정
  문면(`docs/discovery/m7-screen-data-requirements.md`).

## out_scope

- Kotlin `src/main` · OpenAPI 편집 · 생성 타입 손 편집 · 화면 E · 알림함 · 캡처·서비스 설명 · 채널 발송 · 7F · 7E-2 OPEN ⑥~⑨.

## 순서

1. **Phase 1 착수 보고(구현 전)**: 체크리스트 응답 실측(빈 계정·채운 계정에서 세 목록) · 항목 → 폼 대응표 · 승인 목록 증분 · 설계 검토 ·
   여정 ① 하네스 계획(되돌림 가능성 실측 포함).
2. 화면 test·e2e 먼저(RED) → 구현 → 변이 → acceptance → rollback → evidence → 동결 보고.
3. 규율: **편집 → 커밋 → 측정** · 동결 직전 주장 ↔ `git show HEAD:<파일>` 대조 · 하네스는 남의 컨테이너에 닿지 않는다(7E-1 락 규율) ·
   새 test class 는 만드는 커밋에서 `gate-tests.properties` 등재(해당 시).

## acceptance

- `ci.yml` `web` job 명령 그대로(여정 포함) + Kotlin `check` job + `qualityBaseline`.

## rollback

- in_scope 경로 한정 `git restore --source=1e387499 --staged --worktree --` + 새 파일 삭제, 목록 기계 산출. 공유 파일은 병합된 main 고정
  SHA 에서. 임시 clone ①~⑥, `실측 HEAD` 기록, 복원 블록 ↔ 기계 목록 대조.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-30 착수(팀장) · 운영자 지시 2026-10-30.
- 2026-10-31 **D-7E2b-7(팀장, 착수 보고 수령 — D-7E2b-2 갱신)**: 마법사의 **단계는 쓰기 단위**다(사업 정보 → 자격 사실 → 시공능력평가액 →
  전략 금액대 → 전략 감시 조건). 항목 8 ↔ 쓰기 단위 5 라 선언 순서대로 걸으면 같은 절 단위 교체 폼이 세 번 선다. 체크리스트 **목록**은 서버의
  선언 순서 그대로 보인다. 항목 → 단계 대응은 표시용 상수이고 계약 enum 전수 등식 test 로 잠근다.
- 2026-10-31 **D-7E2b-8(팀장 — D-7E2b-2 갱신)**: 폼 재사용은 **컴포넌트 분리**로 — `ProfileSection` 을 블록 컴포넌트 셋(사업 정보 · 자격 사실 ·
  시공능력평가액)으로, `StrategySection` 의 한 칸 편집을 재사용 단위로 올리고 설정 화면은 그 조각을 조립한다. 분리는 **거동 무변경 리팩터 커밋을
  먼저 따로** 두고 그 커밋에서 기존 `ProfileSection.test.tsx`·`SettingsScreen.test.tsx`·설정 여정이 그대로 초록임을 실측한다. 마법사가 자기 폼을
  새로 쓰지 않는다.
- 2026-10-31 **D-7E2b-9(팀장 — D-7E2b-4·5 갱신)**: `BUDGET_RANGE` 는 계약에 비우는 operation 이 없다(`amountWon` 생략·`null`·`terms:[]` 모두 400) —
  적용하면 제안 목록이 `WATCH_NOT_CONFIGURED` → `NO_MATCH` 로 바뀌어 여정 ②·설정 여정의 출발 단언이 깨지고 teardown 으로도 못 돌린다. 그래서
  여정 ① 은 **금액대 단계를 걷되 적용하지 않는다**(값 입력 → 「그만두기」) — 여정은 `pending:[BUDGET_RANGE]` 로 끝나고 나머지 일곱의 전이·되돌림을
  전부 단언한다. 「미완 0 → B 로 가는 행동 하나」(D-7E2b-4)는 **vitest 화면 test**(완료 응답 투입)로 잠근다. 남의 여정의 출발 단언은 바꾸지 않는다.
  `LICENSES` 는 되돌림이 없지만 여정 ②③ 단언에 영향이 없음을 실측했다(착수 보고 §5) — evidence 에 그 실측을 남긴다.
  **후속 등재 `OPEN-7E2B-BUDGET-CLEAR`**: 전략 금액대를 비우는 편집 값이 계약에 없다(7C 후속 — Kotlin `src/main`·OpenAPI, 이 slice out_scope).
