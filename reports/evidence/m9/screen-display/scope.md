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

## 계약 갱신 2026-11-05 (팀장)

구현 레인의 보고 셋에 대한 결정이다. 갱신 전 판정 SHA 는 `142b4ec3` 이고, 이 절 뒤의 작업은 그
SHA 뒤에 온다.

### in_scope 추가 셋

| 경로 | 사유 |
| --- | --- |
| `web/scripts/display-gate-selectors.test.mjs` | D-4 게이트의 양성·음성 대조. 실제 `eslint.config.js` 를 불러 린트하므로 `eslint`·`node:path` 를 import 하고, 그래서 `web/src/**` 에 둘 수 없다(ADR 0011 D-3 의 경계 — 같은 사유의 선례가 `web/scripts/` 에 있다). **이미 커밋돼 있고 유지한다.** |
| `web/config/gate-tests.txt` | 새 게이트 test 셋의 W-7 등재. **추가 줄만** 넣고 기존 줄은 무편집이다. |
| `web/e2e/capture/usecase-captures.mjs` | 알림함 캡처가 막혀 있다(아래 D-6). 양성 표지 한 줄을 고친다. |
| `web/scripts/screen-card-coverage.{mjs,test.mjs}` | **r1 수정 라운드에서 생겼다**(v M-1 처방 「열거 말고 구조」). DOM 어휘 게이트의 **모집단**을 `src/screens/**` 의 AST 에서 유도해 마운트 목록과 집합 등식을 건다. 앞의 `display-gate-selectors.test.mjs` 와 같은 사유로 `web/scripts/` 에 있다(node 모듈 import — `web/src/**` 에 둘 수 없다). r2 v L-B 로 이 절에 등재한다. |

**경로 정정**: 갱신 전 in_scope 의 `web/e2e/shared.ts` 는 **존재하지 않는 경로**다. 실제 파일은
`web/e2e/journey/shared.ts` 이고 그것은 이미 `web/e2e/journey/**` 안이다.

### 결정 추가

| ID | 결정 |
| --- | --- |
| D-1 갱신 | **없는 시각의 표지는 `값 없음` 이다**(`—` 를 쓰지 않는다). 저장소가 그 문면을 하나로 모아 왔고(「같은 뜻에 두 말을 두지 않는다」) 같은 관례가 여섯 자리에 있다 — 시각 칸만 다른 글자를 쓰면 이 slice 가 없애려는 바로 그 불일치를 만든다. **읽지 못한 값은 `알 수 없음` 그대로**다(둘은 여전히 갈린다). |
| D-5 | 새 게이트 test 셋(`timeDisplay.test.ts` · `screenVocabulary.test.tsx` · `display-gate-selectors.test.mjs`)을 `web/config/gate-tests.txt` 에 등재한다. |
| D-6 | 캡처 스크립트의 알림함 **양성 표지를 빈 얼굴에서 알림 행 하나로** 바꾼다. 지금 씨앗이 건을 결과까지 밀어 알림을 만들므로 빈 얼굴은 서지 않는다(base 에서도 재현된 선행 결함). 고친 뒤 `capture:usecase` 로 재촬영해 `docs/usecase/**` 를 갱신한다. |
| D-7 | **타임라인의 명령 토큰 원문**(`ADD_TO_BID_BOX` 꼴)은 이 slice 의 범위 밖이다 — `OPEN-M9-TIMELINE-COMMAND-WORDING` 로 checklist 에 등재만 한다. |
