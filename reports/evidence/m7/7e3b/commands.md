# M7/7E-3b 실행 명령과 종료 코드

base `ac806a51` · 브랜치 `m7-7e3b/2026-10-31`. 명령은 `web/` 에서 돌렸고 Kotlin·되돌림 절만
저장소 뿌리다. **종료 코드로** 판정하고 출력은 핵심 한 줄만 적는다.

## Phase 1 착수 실측 — 하네스로 **실제 응답**을 봤다 (구현 전)

`node e2e/harness/journey-up.mjs`(출하 app.jar · 버릴 Postgres · fake KONEPS · vite preview)를
띄우고 preview 를 지나 출하 API 로만 밀었다(담기 → 투찰가 요청 → 초안 → 제출함 → 개찰 대기 →
개찰 → 적격심사 → 결과). 응답 모양을 추측하지 않았다.

| 상태 | `cases` | `aggregate` 핵심 |
| --- | --- | --- |
| 투찰 기록 0 건 | 0 | 두 축 `ABSENT_NO_RECORDED_BIDS` · 비율 null · 사유 목록 **빈 배열** |
| 제출함 1(`BID_RECORDED`) | 1 | 두 축 `ABSENT_NO_DECIDED_CASES` · 미확정 1 · 사유 `NOT_OPENED_YET` |
| + 낙찰 1(`WON`) | 2 | 기록 축 `MEASURED` **비율 `"1"`** · 분모 1 / 대사 축 분모 **0** |
| + 패찰 1(`LOST`) | 3 | 기록 축 **비율 `"0.5"`** · 낙찰 1 · 패찰 1 · 분모 2 / 대사 축 분모 **0** |

건별은 세 건 모두 같은 모양이었다 — 투찰 금액 넷 칸(`basis` 없음 · 전부 non-null) ·
`chosenOptionRef` 부재 · 확정 낙찰가 다섯 칸 전부 부재 · `reconciledOutcome UNDECIDABLE` ·
사유 `NOT_OPENED_YET` · `outcomeAgreement NOT_COMPARABLE` · `gapState AWARD_ABSENT`.

**계약 결정을 반박한 다섯**(팀장이 D-7E3b-7~11 로 수령): ⒜ 대사 축 분모 0 의 사유는 하네스에서
`WINNER_NAME_NOT_PUBLISHED` 가 아니라 `NOT_OPENED_YET` 이다(개찰 축 미수집이라 커널에 닿지
않는다 — 운영 환경의 예상값은 저장소 안에서 잴 수 없다) ⒝ 투찰 금액은 넷 칸이고 세 갈래
`MoneyReading` 이 적용될 자리가 아니다 ⒞ `cases[]` ⊋ 화면 카드(실측: `BID_RECORDED` 건이
응답에만 있다) ⒟ 집계 표본 3 ≠ 카드 2 ⒠ 비율이 `"1"`·`"0.5"` 다(뒤 0 이 떨어진다).

곁들여 쟀다 — 같은 건의 `readOpeningResult.state` 도 `NOT_OPENED_YET` 이라 **두 operation 의
개찰 축이 일치**했다. 응답 키는 건별 **23** · 집계 **16**, 다른 업체 식별 칸 **0**.

## RED — 구현 전에 붉은 것을 확인했다

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `npx vitest run src/display/myBidResultDisplay.test.ts src/display/resultDisplay.test.ts src/screens/ResultsAggregate.test.tsx src/screens/ResultsScreen.test.tsx` | 1 | **4 파일 실패** — 모듈 부재 셋 · 빈 자리 표 단언 둘 |

구현 뒤 같은 넷이 exit 0.

## 변이 — 버릴 clone 에서 **바꿔치워** 여덟 (전부 붉다)

`git clone --no-hardlinks` 한 트리에서 한 번에 하나씩 적용하고 되돌렸다. 통제군(변이 전)은
**91 passed**. 전부 컴파일되고 돌았으므로 **거동 RED** 이지 빌드 실패가 아니다.

| 변이 | 바꾼 것 | exit | 깨진 test |
| --- | --- | --- | --- |
| A 접두 바꿔치기 | 기록 축이 대사 축의 낙찰 수를 싣는다 | 1 | 1 |
| B 분모 0 을 값으로 | 비율 자리를 **언제나** 그린다 | 1 | 2 |
| C 판정 불가를 패찰로 | 「아직 판정 못 함」 문면을 패찰 문면으로 | 1 | 3 |
| D 차이 0 을 부재로 | 빈 칸 판정을 참/거짓으로 | 1 | 1 |
| E 적재 중을 없음으로 | `LOADING` 을 `MISSING` 으로 | 1 | 1 |
| F 대응을 차례로 | `caseId` 대신 목록 index 로 짝지음 | 1 | 11 |
| G 표본 문면 지우기 | 표본 정의 문면을 빈 문자열로 | 1 | 1 |
| H 축 하나 지우기 | 집계가 기록 축만 그린다 | 1 | 6 |

E 는 **이 설계 중에 발견한 빈틈**이다 — `CaseResult` 네 갈래 중 `LOADING` 만 단언이 없어 그
변이가 처음엔 초록이었다. test 를 먼저 더한 뒤 다시 쟀다(커밋 `31347968`).

## acceptance — `ci.yml` `web` job 명령 그대로 + Kotlin

| step | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| W-2 | `npm run lint` | 0 | |
| W-3 | `npm run typecheck` | 0 | |
| W-4 | `npm run gate:src-tree` | 0 | 컴파일 경계 전제 성립 |
| W-5 | `npm run api:types:check` | 0 | 생성 타입이 OpenAPI 와 바이트 동일 |
| W-6 | `npm run test` | 0 | **32 파일 · 657 passed** |
| W-7 | `npm run gate:tests` | 0 | 등록부 전부 통과 · skipped/todo 0 |
| W-8 | `npm run build` | 0 | |
| W-9 | `npm run gate:boundary-canary` | 0 | |
| W-10 | `npx playwright install chromium` | 0 | |
| W-11 | `npm run test:e2e` | 0 | 2 passed |
| W-12 | `./gradlew --no-daemon :app:bootJar` | 0 | |
| W-13 | `npm run test:e2e:journey` | 0 | **통과 11 · flaky 0 · 건너뜀 0 · spec 4/4** |
| — | `./gradlew --no-daemon check qualityBaseline` | 0 | BUILD SUCCESSFUL · 401 task |

Kotlin 쪽은 이 slice 가 한 줄도 바꾸지 않지만 계약이 요구하는 명령이라 그대로 돌렸다.

## 캡처 — `npm run capture:usecase`

| 회차 | exit | 결과 |
| --- | --- | --- |
| 1 | 0 | 12 장 · 합 706,540 — **그러나 씨앗이 조용히 실패**(쓰기에 자격 헤더 누락 → 403, 상태를 안 봐서 끝까지 돌았다). 빈 화면이었다 |
| 2 | 1 | 로그인 상태를 200 으로 못박아 멈췄다(계약은 **201**) — 멈춤 자체는 옳게 동작했고 파일을 쓰지 않았다 |
| 3 | 1 | 맥락 파일 뒤 preview 포트가 아직 안 열려 첫 이동이 거부됐다 |
| 4 | 0 | 12 장 · 합 1,022,908 — 씨앗이 닿았다(워크시트·결과 화면이 3배로 커졌다) |
| 5 | 0 | **최종** · 12 장 · 합 **1,027,409** · 최대 **138,582** — manifest 의 HEAD 가 현재 HEAD 와 일치 |

회차 1 의 결함이 상한·개인정보 단언을 모두 지나갔다는 것이 **양성 표지를 먼저 기다리게 만든
근거**다(결과 화면에서 그 건의 카드를 기다린다). 상한은 파일 300KB · 합 2MB 이고 스크립트가 잰다.

## 되돌림 실측

`rollback.md` 가 정본이다 — ①~⑥ 전부 exit 0, 되돌린 트리의 vitest **606 passed**.

## clean-tree 게이트와 비밀값 스캔

| 항목 | 결과 |
| --- | --- |
| `git status --porcelain -- <in_scope 개별 인자>` | **빈 출력** |
| 양성 대조(비파괴 절삭 — `checkout --` 를 쓰지 않았다) | 심은 뒤 ` M` 한 줄 · 되돌린 뒤 빈 출력 |
| 참조형 스캔(`-f config/quality/leak-patterns.txt`) · evidence 디렉터리 | exit 1 = 통과 |
| 같은 스캔 · 산출물 경로 | **5 건** — 전부 캡처 스크립트의 **부재 단언이 쓰는 지역 식별자와 그 전달 자리**다. 값 리터럴은 0 이고 일회 값은 실행 시각에 하네스 맥락에서 읽는다. 루트 게이트의 스캔 대상이 evidence 뿐인 사유(「유출 방어 코드 자신이 이 어휘와 겹친다」)에 해당하는 자리다 |
| 캡처 PNG 에 하네스 로그인 이름 축어 | **0 파일** |
| 문서·스크립트의 사업자번호 꼴 | **0 건** |

캡처 12 장은 찍기 **직전** DOM 단언 넷(사업자번호 꼴 · 일회 문구 · 로그인 이름 · 세션 쿠키
가독)을 모두 지났다 — 하나라도 걸리면 파일을 쓰지 않고 멈춘다.

**마지막 HEAD 의 게이트 결과 정본은 이 문서가 아니라 verifier 와 PR 조치 코멘트다** — evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다.
