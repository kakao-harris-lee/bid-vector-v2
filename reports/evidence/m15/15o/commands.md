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

두 프로그램의 `node_modules` 밖 전수는 **142 파일**이다. 같은 모집단에서 15L 판(주석 토큰 어디에나)은
offender **1**(`src/display/caseDisplay.test.ts` 의 JSDoc 산문 — 실해 아님), 15O 판(주석 본문의
줄머리)은 **0** 이다. 좁힘이 막아야 할 꼴을 놓치지 않는 것은 합성 원문 여덟으로 쟀다(한 줄 `//` ·
삼중 사선 · 블록 주석 두 꼴 · JSDoc 줄 · 설명 단 `expect-error` · 들여쓴 줄 · BOM 뒤) — **전부
센다**. 그 가운데 블록 주석·JSDoc 꼴은 오늘 컴파일러가 무시하는 자리이고 렉서 그물이 받는다.

## acceptance — `web` job 전건(로컬, Gradle 절 제외)

| step | 명령 | exit |
|---|---|---|
| W-1 | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — 파일 **78** · test **2079** 전부 통과(이 slice 가 더한 것은 파일 하나와 test 28 — 새 범위 test 17 · 덮개 11) |
| W-7 | `npm run gate:tests` | 0 — 등록부 기준 수집 확인(skipped/todo 0) |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |

W-4 의 판정 문면에 항 둘이 늘었다(`src` 안 파일 참조 지시의 경계 · `src` 안 lib 참조 지시 0).
프로그램 전수·링크·집합 등식의 수는 base 와 같다.

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

## 거짓 RED 가 아닌 것 (닫힌 쪽 실패도 비용이다)

| 꼴 | 결과 |
|---|---|
| 무변이 head 전건 | 위 acceptance 아홉 전부 **0** |
| `src` 의 JSDoc 산문 속 지시 언급 둘(오늘 트리) | 덮개 **0** — 15L 그물에서는 1 이었다 |
| 치환 없는 템플릿 문자열 속 그 문구 · 보통 문자열 속 그 문구 | 지시 **0** |
| 치환 **있는** 템플릿 문자열 속 그 문구 | 지시 **1** — 알려진 제한 ②(15L 제한 ⑨ 그대로, 닫힌 쪽 실패) |
| 줄머리에서 그 꼴로 시작하는 산문 | 지시 **1** — 알려진 제한 ③ |

## clean-tree

`git status --porcelain -- <in_scope 개별 인자>` **빈 출력**. 양성 대조 1 회 — `gate-tests.txt` 에
한 줄을 더하면 ` M` 한 줄이 서고, **비파괴 절삭**(그 줄만 잘라 낸 사본으로 덮어쓰기)으로 되돌리면
다시 빈 출력이다(`checkout --` 를 쓰지 않는다).

## 개인정보·비밀값

이 slice 가 만지는 것은 게이트 test 와 경계 술어다 — 자격·개인 값에 닿는 자리가 없다.
`command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15o/` 매치 0.
