# M9 / screen-display — 시각·금액 표시 어휘 (OPEN-7E3B-TIME-DISPLAY · OPEN-7E3B-MONEY-WORDING)

- base: main `79f3e5c4` · 브랜치 `feature/m9-screen-display` · worktree `bid-vector-v2-m9-screen-display`
- 근거: `docs/discovery/m7-screen-data-requirements.md` 「화면 전반의 표시 어휘 두 축」 · `reports/evidence/m7/7e3b/checklist.md` 알려진 제한 11·14·15 · M7 종결 checklist §4.
- 사용자 지시 2026-11-05: 넘긴 후속 전부 진행(추천안으로).

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | 시각 표기: **KST(Asia/Seoul) 변환, 초 이하 절삭**, 형식 `YYYY-MM-DD HH:mm`(같은 해 생략하지 않음). null → `—`, 파싱 불가 → 원문 대신 `알 수 없음`(값이 보이게 — 조용히 빈칸 금지). 상대 표기(D-day 등)는 기존 자리 유지하되 같은 모듈 경유. `now` 는 주입(`Date.now`/`new Date()` 를 `src/screens`·`src/display` 에서 직접 부르지 않는다 — `OPEN-7E-GATE-RESIDUAL` ⑨ 처방). |
| D-2 | 신규 `web/src/display/timeDisplay.ts` 하나가 유일한 시각 서식 자리. 원문 ISO 를 화면에 보간하던 자리(알림함 `occurredAt` · 워크시트 타임라인 · 입찰함 카드·워크시트의 `nextCheckpointAt` · 워크시트 `deadlineAt`) 전부 경유. |
| D-3 | 금액 부속 토큰(basis·VAT·provenance)은 `moneyDisplay.ts` 의 **전수 표**(`satisfies Record<…>`)로만 문면이 된다. 표 밖 값은 `displayOrUnknown` 으로 보이게. 화면 C 머리와 화면 C 입력 선택지가 같은 문면. 한국어 문면은 기존 `VAT_TREATMENT_DISPLAY` 어조를 따른다(추천안으로 진행, 사용자 확인은 PR 에서). |
| D-4 | 재발 방지 게이트: 「화면 파일이 `*At` 필드를 직접 보간하는 자리 0」과 「DOM 에 금액 토큰 원문(`BASE_AMOUNT`·`PUBLISHED`·`INCLUSIVE` 등 openapi enum 전수) 0」을 **구조로** 잠근다 — eslint `no-restricted-syntax`(AST) 또는 enum 전수에서 유도한 vitest 집합 단언. 문자열 grep 술어 금지. |

## in_scope

- `web/src/display/**` (`notificationDisplay.ts` 는 **제외** — 다른 레인 소유)
- `web/src/screens/**` (해당 화면과 test)
- `web/e2e/journey/**` · `web/e2e/shared.ts` (ISO 원문 단언을 표기로)
- `web/eslint.config.*` (D-4 규칙을 쓸 때)
- `docs/usecase/**` (캡처 재촬영 — slice 끝에 한 번, 스크립트가 dirty `web/` 를 거부하므로 커밋 뒤)
- `reports/evidence/m9/screen-display/**`

## out_scope

Kotlin·openapi·generated 클라이언트·`notificationDisplay.ts`·`ci.yml`·서버 응답 형식 변경.

## acceptance

- CI `web` job 명령 그대로(W-1~W-13) + Kotlin `check` job(S-20 이 e2e 를 돈다) — `.github/workflows/ci.yml` 에서 명령을 그대로 가져온다.
- `timeDisplay` 전수 vitest(UTC→KST 날짜 넘김 · 절삭 · null · 파싱 불가) · 토큰 표 전수 등식 · D-4 게이트의 양성 대조(심은 위반이 RED) 각 한 줄.
- 변이: 화면 하나에서 원문 보간으로 되돌림 → RED · 표 한 행 삭제 → RED.

## rollback

`git restore --source=79f3e5c4 --staged --worktree -- <in_scope 경로>` + 새 파일 삭제; 목록은 `git diff --name-status 79f3e5c4..HEAD` 기계 산출(`rollback.md`).

## 하네스 레인 변경

없음.
