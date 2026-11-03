# chore — 여정 ① e2e 응답 포착 경합 (W-13 간헐 실패)

- base: main `f0f6e660` · 브랜치 `fix/e2e-onboarding-race`
- 근거: main CI `check` S-20 · `web` W-13 이 같은 자리에서 간헐 실패 — `957587c4`(#110) · `783817a5`(#114) · PR #118 head
  `6103ef7f`. 실패 단언은 여정 ① 「⑦ 되돌림」의 `pending` 이 `CONSTRUCTION_CAPACITY` 를 담는가이고, 받은 값은 지우기
  **전** 상태(`["BUDGET_RANGE"]`)다(M6 종결 판정 open-inventory 후속 ⑤ · PR #118 verifier r1 H-1).

## 원인 (가설 — 구현 레인이 먼저 실측으로 확정한다)

`checklistResponse(page)` 는 「다음에 오는 200 GET `/api/onboarding-checklist` 응답」을 잡는다. 지우기 클릭 **전에** 걸리므로,
그 전에 출발해 늦게 닿은 다시 읽기 응답(재진입·배너)이 지우기 뒤 다시 읽기보다 먼저 잡힐 수 있다. 직전 주석이 한 경로(홈
경유 두 질의)를 화면 단언으로 막았지만, 「화면이 최신을 그렸다」는 「늦은 응답이 아직 날아오는 중이 아니다」를 보장하지 않는다.

## in_scope

- `web/e2e/journey/onboarding.spec.ts` — 응답 포착을 **지우기 요청 자신의 응답 뒤에 출발한** checklist GET 으로 못박는다
  (요청 출발 순서로 거른다). 같은 helper 를 쓰는 다른 자리도 같은 꼴이면 함께.
- `reports/evidence/chore/e2e-onboarding-race/**`

## out_scope

- 제품 코드(`web/src/**`, Kotlin) — 거동 0. 클라이언트가 지우기 성공 뒤에만 다시 읽는다는 것은 그대로 전제다.
- 다른 spec · CI 워크플로 · 재시도 횟수(`retries`)·타임아웃 늘리기 — **재시도·대기 늘리기로 덮지 않는다**.

## acceptance

- CI `web` job 의 명령 그대로(W-1~W-13) 초록 + Kotlin `check`(S-20 이 같은 스크립트를 돈다) — PR head 의 CI 4 job.
- **경합 재현 대조**: 늦은 응답을 인위로 만들어(예: `page.route` 로 지우기 직전 checklist GET 하나를 지연) **구판 단언이 RED**,
  새 판이 GREEN 임을 실측한다. 재현 장치는 일회 측정이면 evidence 에 방법과 결과 한 줄, 상시로 둘 가치가 있으면 spec 에.
- 반복 실측: 여정 ① 표적 반복(예: `--repeat-each`)에서 실패 0 — 횟수와 결과를 적는다.

## rollback

`git restore --source=<base> --staged --worktree -- web/e2e/journey/onboarding.spec.ts` + evidence 디렉터리 삭제.

## 하네스 레인 변경

없음.
