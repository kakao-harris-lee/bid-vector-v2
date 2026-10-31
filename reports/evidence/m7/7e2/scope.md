# M7/7E-2 — 화면 D 내 입찰함 + 설정 · 7E-1 게이트 잔여 닫기 (착수 계약, 2026-10-30, 팀장)

- **근거**: 운영자 결정 2026-10-30(「7E-2 는 D·설정 먼저 + 체크리스트 API 병렬」) — 화면 A(시작하기)는 체크리스트 operation(M7/7C④-3)
  머지 뒤 별 조각으로 붙인다. 7E 분할 D-7E-0(7E-1 계약)의 갱신이다: **7E-2 = D · 설정 · 여정 ④ + `OPEN-7E-GATE-RESIDUAL`**,
  **7E-2b = A 시작하기 · 여정 ①**(7C④-3 뒤), **7E-3 = E · 알림함 · 여정 ⑤ · 캡처**.
- **base**: main **`fa5ca096`**(2026-10-30 고정) · worktree `bid-vector-v2-m7-7e2` · 브랜치 `m7-7e2/2026-10-30`. rollback 복원 기준도
  `fa5ca096`(전제: 그 SHA 에 이 slice 없음 — 첫 산출물 커밋에서 단언).
- **입력 정본**: 7E-1 계약 `reports/evidence/m7/7e1/scope.md`(D-7E-1~10 — 하네스·시간·상태 표시·§6.5·개인정보·CI 규율은 **그대로
  구속**) · 7E-1 checklist §4 `OPEN-7E-GATE-RESIDUAL` · `docs/discovery/m7-screen-data-requirements.md` 「D. 내 입찰함」 · 「설정」 ·
  `docs/discovery/ux-journey-research.md` §6 · `docs/adr/0011-web-frontend-stack.md`.
- **레인**: 구현 하나(`kotlin-implementer` — 하네스가 app.jar·Gradle 을 다룬다). 판정 `verifier`(opus) + `code-reviewer`(sonnet).
  Codex 대상 아님.

## 화면 D·설정이 쓰는 operation (base `fa5ca096` OpenAPI 에서 — 이 밖을 부르지 않는다)

- D 내 입찰함: `GET /api/bid-cases`(상태별 목록 · 다음 확인 시점 · 지난 일정 경고 — 서버 판정) · `GET /api/bid-cases/{caseId}` ·
  `/timeline` · 상태 전이 중 이 화면의 「다음 행동 하나」에 필요한 것(`/defer` · `/resume` · `/skip` · `/await-opening` · `/opening` ·
  `/qualification-review` · `/outcome` · `/outcome-correction` 가운데 착수 보고에서 화면 요구표와 대조해 확정).
- 설정: `GET /api/strategy` · `/api/strategy/edit-sessions` 여섯(begin · read · value · confirm · edit · cancel) · `GET /api/profile` ·
  `POST /api/profile/qualification-facts` · `/business-details` · `GET`·`POST /construction-capacities` · `/construction-capacities/remove`.
- 승인 목록은 7E-1 의 `approvedOperations.ts` (메서드, 경로) 쌍 한 자리에 더한다(타입·게이트·여정이 그것을 쓴다).
- 부족한 API 는 **만들지 않는다**(Kotlin `src/main` out) — 보고한다.

## 팀장 결정 (추천안 — 착수 보고 실측이 반박하면 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7E2-1 여정 ④** | 「제출함」 기록 → 내 입찰함 상태 탭에서 그 건 · 다음 확인 시점 배지 · (가능하면) 개찰 대기 전이. 7E-1 하네스 재사용(실 app.jar · fake KONEPS · 시각 조작 0). DOM 값 ↔ 같은 실행 API 응답 대조. |
| **D-7E2-2 설정 = 전략 편집 세션** | 감시 조건·임계치·후보 상한·활성 투찰 상한은 **편집 세션**(begin → value → confirm, 동시 편집 `seenRevision` 충돌 얼굴 포함)으로만 바꾼다. 7E-1 하네스가 전략을 API 로 심던 자리(D-7E-8)는 **그대로 두고**, 설정 화면이 같은 값을 화면으로 세우는 여정을 하나 더한다(그것이 제안 목록 `MATCHED` 카드 여정 — 7E-1 D-7E-9 가 넘긴 것). |
| **D-7E2-3 프로필** | 면허·지역·실적·사업 정보·나라장터 준비(자기 신고)를 읽고 쓴다. 사업자번호는 **자기 신고 입력만**(검증 없음 — 요구표). 화면·콘솔·스크린샷·trace 에 사업자번호 평문 0(D-7E-6 그대로). |
| **D-7E2-4 범위 밖 자리** | 알림 채널 선택(R-4) · 페이퍼 투찰 탭(SET-03) · 국세청 확인은 빈 자리 + 「준비 중」. 탭·버튼을 만들지 않는다. |
| **D-7E2-5 `OPEN-7E-GATE-RESIDUAL` 닫기** | 7E-1 checklist §4 의 다섯을 이 slice 에서 닫는다: ① client import 해석을 TS 자신의 해석(`checker.getSymbolAtLocation(moduleSpecifier)`)으로 + 「값 export 는 함수뿐」 전수 ② `publicDir` 판정을 해석된 vite 설정(`resolveConfig`)으로 또는 `dist` 루트 == {`index.html`, `assets`} ③ 문자열 갈래 + 빌드 시점 디렉터리 ④ CSS custom property + `getComputedStyle` 하드코딩 계열 ⑤ `caseVersion` 가드 전제(버전을 올리지 않는 쓰기가 계약에 생기면 무력 — 계약 test 로 잠그거나 제한 유지). 각각 양성 대조 RED. **게이트 술어 변경이므로 이 항목들은 앞 커밋들과 분리된 커밋**으로. |
| **D-7E2-6 7C②-b 와의 순서** | M7/7C②-b(PR #103)가 7E-1 상태 표에 `IMPLAUSIBLE` 을 더하는 중이다. 7C②-b 가 먼저 머지되면 이 slice 가 main 병합으로 받는다(손 병합 금지, 재생성). M7/7C④-3 이 OpenAPI·생성 타입을 바꾼다 — 이 slice 는 OpenAPI 를 **읽기만** 하고, 나중 머지 쪽이 재생성한다. |

## 설계 검토 (Phase 2.5 — 레인이 착수 보고에 채운다, 구현 전)

- 7E-1 계약의 (0)~(3) 을 그대로 잇고, 이 slice 의 새 표면(편집 세션 상태 기계의 화면 · 프로필 폼 · D 상태 탭)에 대해 우회 ≥5 와
  값 획득 축(새 public 표면 전수)을 적는다. 특히 **클라이언트가 상태를 추론하는 자리**(지난 일정 경고 · 다음 확인 시점 · 탭 분류)는
  전부 API 값에서만 나와야 한다.

## in_scope

- `web/**`(화면 D·설정 · 공통 컴포넌트 확장 · vitest · e2e 여정 ④·설정 · 하네스 · 게이트 스크립트) · `.github/workflows/ci.yml` 의 `web`
  job(여정 추가 시) · `tools/one-command-check.sh`(축어 일치) · e2e fixture(출처·해시) · `reports/evidence/m7/7e2/**` · 7E-1 evidence 의
  `OPEN-7E-GATE-RESIDUAL` 종결 표시 · 요구표 D·설정 행 판정 문면.

## out_scope

- Kotlin `src/main` · OpenAPI 편집 · 화면 A·E·알림함 · 캡처·서비스 설명 · 채널 발송 · 7F.

## 순서

1. **Phase 1 착수 보고(구현 전)**: D 상태 탭·배지·경고가 기대는 API 필드 실측 · 화면별 operation 확정 · 설계 검토 · 여정 ④ 하네스 계획.
2. 화면 test·e2e 먼저(RED) → 구현 → 게이트 잔여(별 커밋) → 변이 → acceptance → rollback → evidence → 동결 보고.
3. 규율: **편집 → 커밋 → 측정**(변이 되돌림이 미커밋 수정을 지우지 않게) · 동결 직전 주장 ↔ `git show HEAD:<파일>` 대조 · 하네스는
   남의 컨테이너에 닿지 않는다(7E-1 락 규율).

## acceptance

- `ci.yml` `web` job 명령 그대로(여정 포함) + Kotlin `check` job + `qualityBaseline`.

## rollback

- in_scope 경로 한정 `git restore --source=fa5ca096 --staged --worktree --` + 새 파일 삭제, 목록 기계 산출. 공유 파일은 병합된 main 고정
  SHA 에서. 임시 clone ①~⑥, `실측 HEAD` 기록, 복원 블록 ↔ 기계 목록 대조(7E-1 ⓪ 행).

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-30 착수(팀장) · 운영자 승인 2026-10-30.
- 2026-10-30 **D-7E2-7(팀장, 착수 보고 수령)**: 사업자번호 — 계약(`Profile`·`BusinessDetailsRequest`)에 칸이 0 이고 `/business-details`
  문면이 「자리가 없다」고 적는다. 입력칸을 만들면 값이 클라이언트에 머문다(API 밖 값 · 개인정보 보관) → **D-7E2-4 의 빈 자리(「준비 중」)로
  옮긴다**. D-7E2-3 의 나머지(상호·소재지·나라장터 준비·면허·지역어·시평액)는 그대로.
- 2026-10-30 **D-7E2-8(팀장)**: D 탭 표 — 요구표 여섯 이름 유지, `BID_PLANNED` → 「투찰가 받음」, `DEADLINE_PASSED_UNRECORDED` → 「종료」.
  다음 행동 표는 착수 보고 §5 그대로(전이는 계약 전이표와 같은 모양, 표에서 파생한 전수 test). D 가 쓰는 operation 은 착수 보고 §2 의 +4
  (`await-opening`·`opening`·`qualification-review`·`outcome`), 설정은 +14 — `readBidCase`·`/timeline`·`/defer`·`/skip`·`/outcome-correction` 은
  쓰지 않아 목록에 두지 않는다.
- 2026-10-30 **D-7E2-9(팀장)**: 범위는 계약대로 한 slice(프로필 포함), 커밋 다섯 묶음(승인 목록·queries / D + 여정 ④ / 설정 전략 + MATCHED 여정 /
  설정 프로필 / 게이트 잔여 넷 각각 별 커밋). 설정 여정은 끝에서 감시 조건을 다시 비워 `WATCH_NOT_CONFIGURED` 로 돌아옴을 단언한다(여정 순서
  독립 — 빈 `terms` 가 거부되면 그때 보고). `routes.tsx` 에 설정 자리 하나 추가는 in_scope.
