# M15 / 15O — commands

base `71b1ad3a7aca2f1e6d6b2eba3384a9d1622ee3dc` · 브랜치 `fix/m15o-ts-directive-ban`

환경: WSL2 · Node `v24.12.0`(`web/.nvmrc` = 24) · npm `11.11.0`. 아래 전건은 `web` job 의 명령을
**순서 그대로** 돌린 것이고, Playwright 두 레인(W-10~W-13)과 Kotlin·Python job 은 PR head 의 CI 가 든다.

변이는 전부 **산출물 커밋 뒤에** 돌렸고, 되돌림은 미리 떠 둔 사본을 덮어쓰는 쪽이다
(`git checkout --` 를 쓰지 않는다). 각 변이 뒤 `git status --porcelain` 이 빈 출력인 것을 확인했다.

## 착수 실측 — 무엇이 검사받고 있지 않았나 (base `71b1ad3a7`)

| 변이 | lint | typecheck | gate:src-tree | 덮개 |
|---|---|---|---|---|
| (a) `src` 에 이름 단 disable + `// @ts-expect-error` + 심은 타입 오류 | **0** | **0** | **0** | **0** |
| (a′) 같은 꼴을 파일 머리의 `// @ts-nocheck` 로 | **0** | **0** | **0** | — |
| (c)ⓘ `web/e2elib/helper.ts` 에 `// @ts-expect-error: <설명>` + 타입 오류, spec 이 import | **0** | **0** | — | **0** |
| (c)ⓘ 대조 — 지시 없이 같은 helper | — | **2** | — | **1** |
| (c)ⓙ `src` 에 `/// <reference path="…/@types/node/index.d.ts" />` + `process.env` | **0** | **0** | **0** | — |
| (c)ⓙ 대조 — 그 줄 없이 `process.env` 만 | — | **2** | **0** | — |
| (c)ⓙ′ `src` 에 `/// <reference lib="dom.asynciterable" />`(base 술어로) | — | — | **0** | — |

대조 둘이 요점이다: **붉음이 없던 것이 방어가 아니라 미측정이었다.** helper 는 지시를 빼면
typecheck 가 2 이므로 **e2e 프로그램 안**이고, `path` 참조는 빼면 typecheck 가 2 이므로 그 한 줄이
실제로 전역 표면을 **연다**.

## 그물 좁힘 — 실제 모집단에서의 양·음성

두 프로그램의 전수(차감은 설치본 접두 하나)는 **142 파일**이다. 같은 모집단에서 15L 판(주석 토큰
어디에나)은 offender **1**(`src/display/caseDisplay.test.ts` 의 JSDoc 산문 — 실해 아님), 15O 판(주석
본문의 줄머리)은 **0** 이다. 좁힘이 막아야 할 꼴을 놓치지 않는 것은 합성 원문 **열**로 쟀다(한 줄
`//` · 삼중 사선 · 블록 주석 두 꼴 · JSDoc 줄 · 설명 단 `expect-error` · 들여쓴 줄 · BOM 뒤 ·
슬래시·별표 교차 두 꼴) — **전부 센다**.

**어느 그물이 받는지는 따로 쟀다**(r1 cr M2·M3). 「블록 주석·JSDoc 꼴은 컴파일러가 무시한다」는
**틀린 문장이었다** — 실측은 다음과 같다.

| 꼴 | ⓐ 컴파일러 | ⓑ 렉서 |
|---|---|---|
| 한 줄 `//` · 삼중 사선 · 들여쓴 줄 · BOM 뒤 · 설명 단 `expect-error` | **인지** | 인지 |
| 블록 주석 `ignore` · 블록 주석 `expect-error` | **인지(억제까지)** | 인지 |
| 블록 주석 `nocheck` · JSDoc 줄 세 이름 | **인지 안 함** | **인지** ← ⓑ 전용 |
| 슬래시·별표 교차(`ignore`·`expect-error`) | **인지** | 첫 판 **놓침** → 지금 인지 ← ⓐ 전용이던 자리 |
| 산문 언급 둘 | 인지 안 함 | 인지 안 함 |

그래서 **ⓐ 가 정의이고 ⓑ 는 그 바깥을 더 받는다**(상위집합이 아니다). 대조도 그 꼴로 다시 세웠다.

## acceptance — `web` job 전건(로컬, Gradle 절 제외)

| step | 명령 | exit |
|---|---|---|
| W-1 | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — 파일 **78** · test **2094** 전부 통과(이 slice 가 더한 것은 파일 하나와 test 43 — 덮개 칸 9 → **33** · 새 범위 test **19**) |
| W-7 | `npm run gate:tests` | 0 — 등록부 기준 수집 확인(skipped/todo 0) |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |

W-4 의 판정 문면에 항 셋이 늘었다(`exclude` 없음 · `src` 안 파일 참조 지시의 경계 · `src` 안 lib
참조 지시 0). 프로그램 전수·링크·집합 등식의 수는 base 와 같다.

**수정 라운드 1 뒤 clean 트리에서 다시 돌렸다** — 선언 파일 probe(제한 ③ 측정)가 앞 전건과 시간이
겹쳐서, 그 전건의 수를 쓰지 않고 재실행한 값을 싣는다.

여정·스모크 레인(W-10~W-13)과 `check`·`ml-engine`·`container` job 은 **PR head 의 CI** 가 든다
(로컬은 docker·브라우저 시스템 패키지 전제가 필요하고 이 slice 는 그 레인의 코드를 바꾸지 않는다).

## 변이 실측 — head 에서 같은 길이 막히는가

덮개 = `npx vitest run scripts/e2e-typecheck-coverage.test.mjs` ·
범위 = `npx vitest run scripts/ban-ts-comment-reach.test.mjs`.

| 변이 | lint | typecheck | gate:src-tree | 덮개 | 범위 |
|---|---|---|---|---|---|
| (통제군) 무변이 head | 0 | 0 | 0 | 0 | 0 |
| ⓐ `src` + 이름 단 disable + `@ts-expect-error` + 심은 오류 | 0 | 0 | 0 | **1** | — |
| ⓑ 같은 꼴 `@ts-nocheck` | 0 | 0 | 0 | **1** | — |
| ⓒ `e2elib/helper.ts` + 지시, spec 이 import | 0 | 0 | — | **1** | — |
| ⓚ `src` 에 `/// <reference path=…>` | 0 | 0 | **1** | — | — |
| ⓛ `src` 에 `/// <reference lib=…>` | — | — | **1** | — | — |
| ⓗ eslint 뿌리 glob 에서 `.mts` 하나 제거 | **0** | — | — | — | **1** |
| ⓘ `ts-expect-error` 옵션을 `allow-with-description` 로 | **0** | — | — | — | **1** |
| ⓔ 모집단 차감을 넓혀 `src` 를 뺀다 | — | — | — | **1** | — |

**ⓐⓑⓒ 가 이 slice 의 중심이다**: lint 와 typecheck 가 **둘 다 0** 인데 덮개가 1 이다 — 억제를
eslint 주석으로 끌 수 없는 자리에서 세기 때문이고, 모집단이 디스크 glob 이 아니라 **프로그램**이라
파일을 옆 디렉터리로 옮기는 ⓒ 도 같은 칸이 받는다. **ⓗ 는 15L r2 N-3 의 재현**이다(그때는 lint 가
조용히 초록이었고 사람이 읽어서 찾았다). **ⓘ 는 거동으로 보이지 않는 후퇴**라 유효 설정 등식만이
받는다(오늘 트리에 억제 주석이 0 이라 lint 메시지가 늘지 않는다). **ⓔ 는 비공허를 든다** —
「화면 레인 ≥ 1」 칸이 `expected 0 to be greater than 0` 으로 떨어진다.

## 변이 실측 — 수정 라운드 1

착수 base 가 아니라 **r1 전의 head**(`6545d59bf`)에서 통과하던 것들이다. 범위 = 범위 test.

| 변이 | lint | typecheck | gate:src-tree | 덮개 | 범위 |
|---|---|---|---|---|---|
| H-1 `web/src/node_modules/helper.ts` + 지시, `src` 가 상대 import | 0 | 0 | 0 | **1** | — |
| H-1 대조 — 지시만 뺀 것 | — | **2** | 0 | **1** | — |
| H-1 `web/e2elib/node_modules/helper.ts` + 지시, e2e 가 import | 0 | 0 | — | **1** | — |
| H-1 `web/src/node_modules/orphan.ts` + 지시, **아무도 import 하지 않음** | — | — | — | **1**(화면 래칫) | — |
| H1 `exclude` 로 테스트 파일 제외(한 줄) | — | **0** | **1** | **1**(빠진 20) | — |
| H1 같은 것 두 줄(`.ts`+`.tsx`) | — | — | **1** | **1**(빠진 45) | — |
| H1 `exclude` 로 디렉터리 하나 제외 | — | — | **1** | **1** | — |
| M1 `src` 에 슬래시·별표 교차 지시 + 심은 오류 | — | **0** | — | **1** | — |
| M2 그물에서 이름 둘(`ignore`·`expect-error`) 제거 | — | — | — | **1**(칸 3) | — |
| L1 `src/**` eslint glob 을 두 확장자로 되돌림 | **0** | — | — | — | **1**(칸 2) |

H-1 의 앞 세 행이 verifier 가 실측한 자리 그대로이고, **지시를 빼도 덮개가 1 인 것**이 새 전용 칸이다.
넷째 행은 **import 되지 않는** 경우로, TS 기본 `exclude` 가 그 디렉터리를 프로그램에서 빼므로 전용 칸이
아니라 **화면 래칫**(디스크 전수 ⊆ 모집단)이 받는다 — 두 길이 각각 다른 칸에 걸린다.
H1 의 세 행은 `typecheck` 가 **초록**인 채 두 층이 붉는다 — 검사 대상이 줄어드는 변이라 tsc 자신은
아무 말도 하지 않는다. M2 의 세 칸은 첫 판에서 **전부 조용했다**(ⓐ 가 떠받쳤다).

## 거짓 RED 가 아닌 것 (닫힌 쪽 실패도 비용이다)

| 꼴 | 결과 |
|---|---|
| 무변이 head 전건 | 위 acceptance 아홉 전부 **0** |
| `src` 의 JSDoc 산문 속 지시 언급 둘(오늘 트리) | 덮개 **0** — 15L 그물에서는 1 이었다 |
| 치환 없는 템플릿 문자열 속 그 문구 · 보통 문자열 속 그 문구 | 지시 **0** |
| 치환 **있는** 템플릿 문자열 속 그 문구 · 줄머리에서 그 꼴로 시작하는 산문 | 지시 **1** — 알려진 제한 ②(닫힌 쪽 실패) |
| `src` **안**을 가리키는 `/// <reference path=…>`(r1 cr L2 음성 대조) | `gate:src-tree` **0** · typecheck **0** — ④c 는 금지가 아니라 **경계**다 |
| `src` 의 선언 파일에 없는 타입 이름(r1 — 방어가 아니라 **미측정**) | typecheck **0** · 덮개 **0** · `gate:src-tree` **0**. 통제군으로 같은 본문을 보통 `.ts` 로 두면 typecheck **2** → `OPEN-M15-SRC-DTS-SKIPLIBCHECK` 신설 |

## clean-tree

`git status --porcelain -- <in_scope 개별 인자>` **빈 출력**. 양성 대조 1 회 — `gate-tests.txt` 에
한 줄을 더하면 ` M` 한 줄이 서고, **비파괴 절삭**(그 줄만 잘라 낸 사본으로 덮어쓰기)으로 되돌리면
다시 빈 출력이다(`checkout --` 를 쓰지 않는다).

## 개인정보·비밀값

이 slice 가 만지는 것은 게이트 test 와 경계 술어다 — 자격·개인 값에 닿는 자리가 없다.
`command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15o/` 매치 0.
