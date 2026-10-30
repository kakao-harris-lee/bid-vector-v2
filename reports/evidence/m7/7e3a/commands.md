# M7/7E-3a 실행 명령과 종료 코드

base `003ec91d` · 브랜치 `m7-7e3a/2026-10-31`. 명령은 `web/` 에서 돌렸고(acceptance 절만 저장소
뿌리) **종료 코드로** 판정한다. 출력은 핵심 한 줄만 적는다.

## Phase 1 착수 실측 — 하네스로 **실제 응답**을 봤다 (구현 전)

`node e2e/harness/journey-up.mjs`(출하 app.jar · 버릴 Postgres · fake KONEPS · vite preview)를
두 번 띄우고 preview 를 지나 호출했다. 응답 모양을 추측하지 않았다.

| 물음 | 자리 | 결과 |
| --- | --- | --- |
| 알림 트리거가 발화하는가 | `GET /api/notifications` × 다섯 시점(로그인 직후 · 투찰가 요청 뒤 · 개찰 대기 뒤 · 개찰 기록 뒤 · 낙찰 확정 뒤) | **전부 `[]` · `X-Total-Count: 0` · `X-Unread-Count: 0`** |
| 왜 발화하지 않는가 | `*/src/main` 의 `NotificationInboxPort` 호출자 | **0** — 쓰기 포트의 빈이 출하 조립에 없다(`NotificationInboxWiring` 은 읽기 쪽만 꽂는다). `NotificationFact` 를 짓는 production 코드도 0 |
| 서버가 그 사실을 말하는가 | `POST /api/bid-price-requests` 응답 | `inboxRecordState: NOT_RECORDED_YET` · `inboxRecordNotRecordedReason: INBOX_WRITE_PORT_NOT_MERGED`(계약 선언 enum) |
| 읽음 처리 표면은 사는가 | `/api/notifications/read` · `/read-notice`(항목 0) | 둘 다 **200** `{markedCount:0, unreadCount:0}` |
| 결과 단계 전이를 하네스에서 만들 수 있는가 | 담기 → draft → record → await-opening → opening → qualification-review → outcome | **전부 200**, `caseVersion 0→6`. KONEPS 가 필요 없다(운영자 기록) |
| 되돌릴 수 있는가 | `WON` 에서 `outcome-correction` · `await-opening` · `skip` | 정정만 **200**(→`LOST`), 나머지 둘은 **409 `INVALID_TRANSITION`** → D-7E3a-9 |
| `readOpeningResult` 가 무엇을 내는가 | 세 공고 키 | `NOT_OPENED_YET`(마감 미도래) · `NOTICE_ABSENT`(미수집 키) · `NOT_COLLECTED`(마감 지난 fixture, 임시 항목으로 실측 후 되돌림). **아홉 중 셋** · 잠정 축은 `NOT_OBSERVED` 하나 |
| 건 전이가 개찰 결과를 움직이는가 | `/opening` 기록 **뒤** 같은 공고 조회 | **`NOT_OPENED_YET` 그대로** — 두 축이 다르다 |
| 마감 지난 공고를 담으면 | `POST /api/bid-cases` | 즉시 `DEADLINE_PASSED_UNRECORDED` → 여정 ⑤ 의 공고는 **마감 미도래**여야 한다 |
| 승인 목록 증분 | base OpenAPI ↔ 화면이 부르는 집합 | **정확히 넷**. 결과 단계 전이 넷은 7E-2 가 D 를 위해 이미 올려 두었다 |

## RED — 구현 전에 붉은 것을 확인한 자리

| 라운드 | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| 순수 핵 | `npx vitest run src/display/openingResultDisplay.test.ts src/display/notificationDisplay.test.ts` | 1 | `Cannot find module './openingResultDisplay'` · 같은 꼴 둘 — **Tests: no tests** |
| 화면 E | `npx vitest run src/display/resultDisplay.test.ts src/screens/ResultsScreen.test.tsx` | 1 | 같은 꼴 — 모듈 부재 |
| 알림함 | `npx vitest run src/screens/InboxScreen.test.tsx` | 1 | 같은 꼴 — 모듈 부재 |

구현 뒤 같은 명령이 각각 exit 0(23 · 34 · 15 passed).

## 리팩터 커밋의 거동 보존

여정의 공통 걸음 셋을 `shared.ts` 로 옮긴 커밋(`5e9625ba`)과 fixture 항목 하나를 더한 커밋
(`addc3b81`)의 트리에서 쟀다 — **여정 ⑤ 를 쓰기 전**이다.

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `npx playwright test --config=playwright.journey.config.ts --list` | 0 | **9 tests in 3 files** — 옮기기 전과 같은 목록 |
| `npm run test:e2e:journey` | 0 | **9 passed (36.8s)** — 기존 여정 전부 초록 |
| `npm run typecheck` · `npx eslint e2e/journey/` | 0 · 0 | |

## 여정 ⑤ 를 세우며 **두 번 붉었다** (둘 다 spec 의 결함)

| 라운드 | 무엇이 붉었나 | 사유 | 고침 |
| --- | --- | --- | --- |
| 1 | `getByTestId('bid-case-3')` 2분 대기 뒤 timeout | 화면 D 는 **고른 탭의 건만** 그린다. 상태에 맞는 탭을 고르지 않아 카드가 DOM 에 아예 없었다 | 걸음마다 `CASE_TAB_LABEL[상태]` 탭을 먼저 고른다(화면을 떠났다 오면 첫 탭으로 돌아간다) |
| 2 | `getByLabel('개찰 결과').selectOption` timeout | 그 입력은 `select` 가 아니라 **라디오 묶음**이다(계약이 순위 숫자를 받지 않아 선택지가 둘·셋) | `getByRole('radio', { name })` 로 고른다. 결과 단계 여섯의 문면을 공용 표에 더했다 |

고친 뒤 **11 passed (42.7s)** — 여정 ⑤ 2.4s · 알림함 0.6s.

## 변이 — 열을 모두 RED 로 잰다 (버릴 clone)

clone 의 통제군은 **430 passed**(화면·표시 전건). 각 변이는 원문을 **바꿔치운다**(더하기만 한
변이를 두지 않는다). `tsc` 도 함께 재서 **어느 층이 잡았는지** 가른다.

| # | 변이 | 표적 | test exit | tsc exit |
| --- | --- | --- | --- | --- |
| M1 | 아홉 갈래 중 둘을 한 라벨로 접는다 | `openingResultDisplay.test.ts` | **1**(1 failed) | 0 |
| M2 | 확정이 비면 잠정 금액으로 메운다 | 위 + `ResultsScreen.test.tsx` | **1**(5 failed) | 0 |
| M3 | 금액을 값 하나만으로 싣는다 | `openingResultDisplay.test.ts` | **1**(1 failed) | 0 |
| M4 | 안읽음 배지를 본문 행에서 센다 | `InboxScreen.test.tsx` | **1**(1 failed) | 0 |
| M5 | 읽음을 낙관으로 먼저 보인다 | `InboxScreen.test.tsx` | **1**(1 failed) | 0 |
| M6 | 떨어진 같은 공고를 한 묶음으로 끌어올린다 | `notificationDisplay.test.ts` | **1**(1 failed) | 0 |
| M7 | 화면 E 에 결과 기록 전이를 둔다 | `ResultsScreen.test.tsx` | **1**(1 failed) | 0 |
| M8 | 결과 단계 표에 「제출함」을 넣는다 | `resultDisplay.test.ts` | **1**(2 failed) | 0 |
| M9 | 낙찰률을 목록에서 세어 짓는다 | `ResultsScreen.test.tsx` | **1**(1 failed) | 0 |
| M10 | 승인 목록 밖 operation 을 부른다 | `screen-operations.test.mjs` | **1**(1 failed) | **2** |

**M10 만 `tsc` 도 붉다** — 타입 좁힘과 AST 등식 두 층이 같은 우회를 각각 잡는다.

### M5 는 처음에 **초록이었다** — test 를 고쳐야 RED 가 됐다

| 단계 | 명령 | exit | 결과 |
| --- | --- | --- | --- |
| 앞 판 test + 변이 | `npx vitest run src/screens/InboxScreen.test.tsx` | **0** | 15 passed — **변이가 지나갔다** |
| 고친 test + **원본 구현**(통제군) | 같은 명령 | 0 | 15 passed |
| 고친 test + 변이 | 같은 명령 | **1** | `응답 전에 읽음으로 바뀌었다 — 낙관 갱신이다: expected '읽음' to contain '안읽음'` |

사유: 앞 판은 누른 **직후** 읽음 상태를 읽어 쓰기가 아직 뜨지도 않은 시점을 쟀다. 버튼이 잠긴
것(요청이 떠 있다는 관측)을 먼저 기다리게 고쳤다 — 커밋 `40ce0cc0`.

## acceptance — `ci.yml` 세 job 의 명령 그대로

`~/.local/bin/bv-build.sh ./tools/one-command-check.sh` (ci.yml 과 축어 일치, 빌드 자리 락).

| 구간 | 핵심 결과 |
| --- | --- |
| Kotlin `check` · `qualityBaseline` | **BUILD SUCCESSFUL** 둘 |
| Python 전건(S-1 ~ S-11) | 전부 통과 |
| web W-1 ~ W-9 | eslint · typecheck · `gate:src-tree` · `api:types:check` · **vitest 544 passed (28 files)** · `gate:tests`(「등록부 기준 · skipped/todo 0 · failed 0」) · build · 경계 카나리 |
| web W-10 ~ W-11 | Playwright 스모크 **2 passed** |
| web W-12 ~ W-13 | `:app:bootJar` **BUILD SUCCESSFUL** · 여정 e2e **11 passed (43.1s)** |

**전체 exit 0.** 이 slice 가 더한 test 는 **72**(rollback 트리의 472 ↔ HEAD 의 544).

`.github/workflows/ci.yml` 과 `tools/one-command-check.sh` 는 **고치지 않았다** — W-13 이
`playwright test --config=playwright.journey.config.ts` 로 **여정 디렉터리 전체**를 돌리므로 새
spec 파일이 자동으로 든다(목록이 9 → 11 로 늘어난 것이 그 측정이다).

## 개인정보 (D-7E-6)

- 새 화면 둘에 사업자번호 입력칸·표시 칸 **0** — 두 operation 의 응답에 그 칸이 없다.
- 기존 여정 test 「개인정보·비밀값이 화면과 콘솔에 나오지 않는다」는 **화면 B 만 방문한다** —
  그 초록은 새 화면 둘을 덮지 않는다(r1 v L-3 정정). 새 화면의 근거는 **응답 스키마에 PII 칸이
  0** 이라는 것이고(`OpeningResultView`·`NotificationItem` 어디에도 사업자번호·대표자명·상호가
  없다) 화면이 그 밖의 값을 만들지 않는다는 것이다.
- 새 주소 둘(`/results`·`/inbox`)은 여정의 **같은 출처 요청 감시**를 지난다(허용 경로에 등재).
- 알림함 응답 스키마에 PII 칸이 없다(7C③ 이 저장 층에서 막는다) — 화면이 더하지 않는다.

---

# 수정 라운드 1 (r1) — verifier `not-ready` · code-review `request_changes`

판정 SHA `689dd3b2` 에 대한 지적을 받아 고쳤다. **고침마다 「고치기 전 RED → 고친 뒤 GREEN」**
을 잰다 — 고치기 전 RED 는 버릴 clone 에서 **그 고침을 되돌리는 변이**로 만든다.

## 지적 ↔ 커밋 대응표

| 지적 | 커밋 | 고친 것 |
| --- | --- | --- |
| **v H-1 · CR H-1** 조건부 `test.skip` | `7072e6c7` | skip 을 단언으로. evidence §3-3 문면도 함께 |
| **v H-1 구조 처방** 여정 레인에 「skipped 0」 | `a5b6603e` | 리포터 산출물로 판정하는 게이트(**게이트 술어 변경이라 분리 커밋**) |
| **CR M-1 = v M-4** 링크가 안 보이는 탭으로 | `048a0363` | `/bids?tab=<id>` + D 가 `useSearchParams` 로 첫 탭 |
| **CR M-2 = v M-2·M-3** 로딩·오류 얼굴 test 0 | `1bb9acfb` | E·알림함·개찰 절의 세 얼굴 test |
| **v M-1** 응답 전 배지가 0 을 지어냄 | `1bb9acfb` | 적재 중 배지 「값 없음」 단언 |
| **CR M-3** 계약 칸 둘을 버림 | `1bb9acfb` | 중복 건수 · 결과 축 관측 시각 + **칸 전수 표** |
| **CR M-4 = v L-1** 문면을 재지 않는 test | `1bb9acfb` | 문면 자체 단언 + 음성 대조 |
| **CR L-1** 읽음 뒤 포커스 유실 | `1bb9acfb` · `d5adc1de` | 버튼을 지우지 않고 잠근다 + 그것을 잠그는 test |
| **CR L-2** 쓰기 성공 live region | `1bb9acfb` | 배지를 `role="status"`·`aria-live` 로(토스트는 두지 않는다 — 되돌릴 행동이 없다) |
| **CR L-3** 묶음 key·testId 겹침 | `1bb9acfb` | 묶음 **차례**를 key·testId 에 |
| **CR L-4** `Field` 이름 충돌 | `1bb9acfb` | 지역 판을 `ResultField` 로 |
| **CR L-5** 개찰 401 에 재시도 | `1bb9acfb` | 401 을 로그인 길로 가른다 |
| **v L-2** `driveToRecorded` 주석 | `1bb9acfb` | 여정 ④·⑤ 둘을 말하고 `null` 의 뜻을 적는다 |
| **v L-3** 개인정보 문면 과장 | evidence 커밋 | 그 test 는 화면 B 만 방문한다 — 새 화면의 근거는 **응답 스키마에 PII 칸 0** |
| **CR OQ-1** 모르는 상태가 E 에서 사라짐 | evidence 커밋 | 저장소 공통 선택(D 의 `tabOf` 와 같은 모양) → 제한 8 + 후속 ID |
| **CR OQ-2** 목록 대기 술어 | `69f2f30c` | 조이면 흔들린다(react-query 중복 제거) → **출처를 둘로** 두고 사유를 제한 9 에 |

## H-1 — 표적 재검증 (실 하네스, 버릴 clone)

verifier 의 오염 재현을 **그대로** 돌렸다: 여정 ⑤ 앞에 그 건을 미리 미는 spec 을 둔다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| 고치기 전(verifier 실측) | `CI=1 playwright test aa-contaminate.spec.ts results.spec.ts` | exit **0** · `1 skipped · 2 passed` — 단언이 하나도 돌지 않았다 |
| **고친 뒤** | 같은 명령 | exit **1** · `1 failed · 2 passed` — `여정 ⑤ 의 건이 WATCHING 에서 출발하지 않았다 …` |

## H-1 구조 게이트 — 양성·음성 대조 (실 하네스)

| 단계 | playwright exit | 게이트 exit | 결과 |
| --- | --- | --- | --- |
| **양성**: 건너뛰는 test 하나 + 통과 둘 | **0** | **1** | `skipped 1건 … zz-skip-probe.spec.ts › 건너뛰는 test 하나` |
| **음성**: 건너뜀 없는 실행 | 0 | **0** | `통과 2 · flaky 0 · 건너뜀 0 · 중단 0` |

양성 대조가 **Playwright 단독으로는 exit 0** 임을 같은 실행에서 보인다 — 그것이 이 게이트가
메우는 구멍이다. 술어 단위 test 아홉(`scripts/journey-results.test.mjs`)이 함께 선다.

## 고침마다 「고치기 전 RED」 — 변이 열둘 (버릴 clone, `npm ci` 로 설치)

각 변이는 **그 고침을 되돌린다**. 전부 exit 1.

| 변이 | 표적 | exit | 실패 수 |
| --- | --- | --- | --- |
| VM13 응답 전 배지에 0 을 지어낸다 | `InboxScreen.test.tsx` | 1 | 1 |
| VM14 E 의 적재 중을 빈 얼굴로 접는다 | `ResultsScreen.test.tsx` | 1 | 1 |
| VM15 알림함의 적재 중을 빈 얼굴로 접는다 | `InboxScreen.test.tsx` | 1 | 1 |
| VM17 개찰 조회 실패를 「아직 개찰 전」으로 접는다 | `ResultsScreen.test.tsx` | 1 | 1 |
| VM11 「다 오지 않았습니다」 줄을 지운다 | `InboxScreen.test.tsx` | 1 | 1 |
| r1M2 링크에서 탭을 뺀다(맨 `/bids`) | E·D test | 1 | 4 |
| r1M2b D 가 URL 탭을 무시한다 | `BidBoxScreen.test.tsx` | 1 | 2 |
| r1M3 중복 건수 칸을 버린다 | `ResultsScreen.test.tsx` | 1 | 3 |
| r1M3b 결과 축 관측 시각 칸을 버린다 | `ResultsScreen.test.tsx` | 1 | 3 |
| r1L5 개찰 401 에 재시도를 단다 | `ResultsScreen.test.tsx` | 1 | 1 |
| r1L1 읽은 뒤 버튼을 지운다 | `InboxScreen.test.tsx` | 1 | 1 |
| r1L3 묶음 testId 에서 차례를 뺀다 | `InboxScreen.test.tsx` | 1 | 6 |

**고친 뒤 GREEN** 은 아래 acceptance 가 같은 트리에서 전건으로 보인다.

## r1 acceptance — 같은 명령, 같은 축어 일치

`~/.local/bin/bv-build.sh ./tools/one-command-check.sh` → **exit 0**.

| 구간 | 핵심 결과 |
| --- | --- |
| Kotlin `check` · `qualityBaseline` · `:app:bootJar` | **BUILD SUCCESSFUL** 셋 |
| Python 전건 | 통과 |
| web vitest(W-6) | **577 passed (29 files)** — r1 이 더한 33 |
| web `gate:tests`(W-7) | 「등록부 기준 · skipped/todo 0 · failed 0」 |
| web 스모크(W-11) | 2 passed |
| web 여정(W-13) | **11 passed (43.6s)** + **새 게이트**: `여정 레인: 통과 11 · flaky 0 · 건너뜀 0 · 중단 0` |

`.github/workflows/ci.yml` · `tools/one-command-check.sh` 는 **r1 에서도 손대지 않았다** — 게이트가
`npm run test:e2e:journey` 안에서 이어 돌기 때문이다(축어 일치 유지).

## r1 rollback — 재산출·재실측

실측 HEAD **`69f2f30c`**(r1 의 마지막 산출물 커밋). 목록이 **22 → 30**(복원 15 · 삭제 15)으로
늘었다 — 여정 게이트 새 파일 셋과 그 배선 둘(`package.json`·`playwright.journey.config.ts`),
탭 deep link 가 건드린 화면 D 파일 셋이다. `comm` 양쪽 빈 출력, 버릴 clone 에서 ①~⑥ 전부 초록
(트리·인덱스 둘 다 base 와 diff 0 · vitest **472** · 게이트 다섯 0).
