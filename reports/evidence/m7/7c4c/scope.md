# M7/7C④-3 — 시작하기 체크리스트 operation + `awaiting` 짝 배열 (평탄 술어의 깊이 1 object 확장) (착수 계약, 2026-10-30, 팀장)

- **근거**: 운영자 결정 2026-10-30(「7E-2 는 D·설정 먼저 + 체크리스트 API 병렬」). `OPEN-7C4-AWAITING-CHECKLIST`(capability map, 7C④-2b
  신설 2026-10-28)를 닫는다. 화면 A(시작하기)의 체크리스트 진행 상태는 **서버가 판정**한다(요구표 A 「체크리스트 진행 상태」 행 —
  클라이언트 추론 금지). 판정 커널은 7C④-2a 가 세웠다(`workflow.profile.OnboardingChecklist` — `OnboardingSnapshot`,
  세 상태 `ItemApplicability`). 빠진 것은 HTTP operation 과 그 응답 모양이다.
- **base**: main **`fa5ca096`**(PR #104 7C④-2b 머지, 2026-10-30 고정) · worktree `bid-vector-v2-m7-7c4c` · 브랜치 `m7-7c4c/2026-10-30`.
  rollback 복원 기준도 `fa5ca096`(전제: 그 SHA 에 이 slice 없음 — 첫 산출물 커밋에서 단언).
- **정본**: 이 파일. 결정 ID `D-7C4C-N`. 앞 조각의 결정 `D-7C4-8`(특히 Q3 — 「wire 를 두 번 바꾸지 않는다」·「세 상태를 두 상태로
  접은 응답은 내보내지 않는다」·「체크리스트 operation 은 `awaiting` 과 함께 선다」)은 **그대로 구속한다**.
- **레인**: 구현 하나(`kotlin-implementer`). 판정 `verifier`(opus) + `code-reviewer`(sonnet) 병렬. Codex 대상 아님(인증·암호화·
  마이그레이션·파기 경로 아님 · `contracts/**` proto 아님).
- **성격**: **게이트·계약형 slice** — 두 레인이 공유하는 계약 게이트(`OpenApiContractTest.isFlatPropertyDefinition`)의 술어를 바꾼다.
  구현 전에 **설계 검토(Phase 2.5)** 를 착수 보고로 낸다.

## 팀장 결정 (추천안으로 진행 — 착수 보고 실측이 반박하면 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7C4C-1 응답 모양** | `OPEN-7C4-AWAITING-CHECKLIST` 의 선택지 **(a)**: 평탄 술어를 **깊이 1 object** 까지 확장하고 `awaiting` 을 **짝 배열**(`[{item, prerequisite}]`)로 낸다. (b) 스칼라 배열 둘(자리로만 묶임)은 기각, (c) 미루기는 운영자 결정으로 기각. |
| **D-7C4C-2 술어 확장의 경계** | 「깊이 1 object」 = 배열 아이템(또는 속성)이 object 이고 **그 object 의 속성이 전부 스칼라 또는 스칼라 배열**인 경우만 통과. 깊이 2(object 안 object·object 배열) · `additionalProperties` 자유 맵 · `oneOf/anyOf/allOf` 는 여전히 거부. 확장은 **구조(스키마 트리)로** 판정하고 문자열 술어를 쓰지 않는다. 기존에 거부되던 모양 중 이 확장으로 **새로 통과하는 것의 전수**를 착수 보고에 적는다(오늘 spec 에서 0 이어야 한다 — 이 slice 의 `awaiting` 외에). |
| **D-7C4C-3 operation** | `GET /api/onboarding-checklist`(계정 주체 전용 — `AccountScopedRoutes` 의 프로필 뿌리 또는 새 뿌리, 착수 보고에서 실측해 정한다). 응답은 커널의 세 상태를 그대로: 완료 항목 · 미완 항목 · `awaiting` 짝(항목, 기다리는 선행 항목). 「해당 없음」 항목은 어느 목록에도 들지 않는다(커널 KDoc). 진행 상태를 저장하지 않는다(D-7C4-1 P1). |
| **D-7C4C-4 어휘** | 항목·선행 이름은 OpenAPI enum 으로 고정하고 Kotlin enum ↔ OpenAPI enum 양방향 등식 test. 생성 TS 재생성(`api:types:check`). web 화면 코드는 만들지 않는다(화면 A 는 7E 후속). |
| **D-7C4C-5 공유 게이트 변경 규율** | 술어 확장 커밋은 **단독 커밋**이고, 양성 대조(깊이 2 · 자유 맵 · 합성 키워드 각각 RED) + 음성 대조(`awaiting` 꼴 GREEN · 기존 spec 전부 GREEN)를 그 커밋 직후 실측해 commands 에 남긴다. 7E-2(web) 레인과 같은 OpenAPI·생성 타입 파일을 만지지 않도록 — 7E-2 는 OpenAPI 를 읽기만 한다. |

## 설계 검토 (Phase 2.5 — 착수 보고, 구현 전)

- (0) 위협 모델 경계: 방어 — 계약 게이트가 복잡한 중첩 모양을 통과시켜 클라이언트가 해석 규칙을 떠안는 것 · 세 상태가 두 상태로
  접히는 것 · 계정 경계 우회(다른 계정의 체크리스트). 방어하지 않음 — 화면 표시(7E).
- (1) 열거인가 구성인가 — 깊이 판정은 스키마 트리의 구성으로.
- (2) 우회 ≥5: `$ref` 로 깊이 숨기기 · `items` 안 `$ref` · `allOf` 로 감싸기 · `additionalProperties: true` · nullable object · 빈 `properties`.
- (2b) 값 획득 축: 새 public 표면(operation · 응답 타입 · 술어 함수) 전수.
- (3) 과잉·미달: 깊이 2 를 열면 과잉, `awaiting` 이 스칼라로 접히면 미달.

## in_scope

- `app/src/main/kotlin/bidvector/app/http/**` 의 체크리스트 controller·응답 · 필요한 wiring · `AccountScopedRoutes`(뿌리 추가 시).
- `workflow/src/main/kotlin/bidvector/workflow/profile/**` 의 조회 use case(커널 판정 의미는 바꾸지 않는다).
- `app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt`(술어) 와 체크리스트 test · 계약·보안 test(새 operation 등재).
- `openapi/bidvector-operator-api.yaml` · `web/src/api/generated/operator-api.ts`(재생성) · `config/quality/*`(게이트 등재·허용 목록, 기계적 귀결).
- `docs/discovery/capability-map.md` 의 `OPEN-7C4-AWAITING-CHECKLIST` 행 종결 · `docs/discovery/m7-screen-data-requirements.md` A 「체크리스트 진행 상태」 행.
- `reports/evidence/m7/7c4c/**`.

## out_scope

- web 화면 코드(7E) · 커널 판정 의미 변경 · DB 마이그레이션(진행 상태 저장 없음) · 깊이 2 이상 허용.

## acceptance

- CI job 명령 그대로: Kotlin `check` + `qualityBaseline` · **`web` job 전체**(OpenAPI·생성 타입이 바뀐다).

## rollback

- in_scope 경로 한정 `git restore --source=fa5ca096 --staged --worktree --` + 새 파일 삭제, 목록은 `git diff --name-status fa5ca096..HEAD`
  기계 산출. 공유 파일은 병합된 main 고정 SHA 에서. 임시 clone ①~⑥ 실측, `실측 HEAD` 기록.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-30 착수(팀장) · 운영자 승인 2026-10-30.
