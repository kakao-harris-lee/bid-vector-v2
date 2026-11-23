# M15 / 15L — commands

base `a189e8c58c8a438e2685c8daae8f83b835b925ba` · 브랜치 `fix/m15l-e2e-typecheck`

환경: WSL2 · Node `v24.12.0`(`web/.nvmrc` = 24) · npm `11.11.0`. 아래 전건은 `web` job 의 명령을
**순서 그대로** 돌린 것이고, Playwright 두 레인(W-10~W-13)과 Kotlin·Python job 은 PR head 의 CI 가 든다.

## 착수 실측 — 무엇이 검사받고 있지 않았나

| 명령 | 결과 |
|---|---|
| base 에서 strict 축을 화면 프로젝트와 같게 맞춘 임시 프로젝트로 `e2e/**/*.ts` + `playwright*.config.ts` 를 연다 | **오류 24** — TS2307 열 · TS2580 넷 · TS7016 넷 · TS7006 셋 · TS6142 둘 · TS2339 하나 |
| 같은 명령 — `@types/node` + `types`·`jsx`·`allowJs` 처방 뒤 | **오류 1** — `e2e/kotlin-read-guard.spec.ts` TS2345(`noUncheckedIndexedAccess` 아래 첨자). 나머지 23 은 처방으로 사라졌다 |
| 같은 명령 — 그 한 자리를 고친 뒤 | **오류 0** |

## acceptance — `web` job 전건(로컬, Gradle 절 제외) · 수정 라운드 2 뒤 재실행

| step | 명령 | exit |
|---|---|---|
| W-1 | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — 파일 77 · test 2051 전부 통과 |
| W-7 | `npm run gate:tests` | 0 — 등록부 기준 수집 확인(skipped/todo 0) |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |

W-4 는 `tsconfig.e2e.json` 이 생긴 뒤에도 통과한다 — 그 게이트가 읽는 것은 `tsconfig.json` 한
파일이고, 프로그램 전수·링크·집합 등식의 수가 base 와 같다(문면에는 수정 라운드 1 이 더한
`types` 술어 한 항이 늘었다).

덮개 게이트는 칸 넷 → **아홉**이 됐다(진단 · 양성 대조 · 억제 지시 0 · 명령의 꼴 · 유효 옵션 등식 ·
덮는 자리 · 음성 대조 · 상한 · 공허 가드). 소요는 2.0s → **5~10s**(호스트에 따라) — e2e 프로그램의
타입 검사를 이 자리가 직접 들기 때문이다(알려진 제한 ⑦).

여정·스모크 레인(W-10~W-13)과 `check`·`ml-engine`·`container` job 은 **PR head 의 CI** 가 든다
(로컬은 docker·브라우저 시스템 패키지 전제가 필요하고 이 slice 는 그 레인의 코드를 바꾸지 않는다).

## 변이 실측 — 이 게이트가 실제로 가르는가

`web/e2e/journey/touchMeasure.ts` 의 `label` 가지 push 에서 `NameSource` 의 필수 칸 하나를 **다시 뺀다**
(15K 가 고친 그 꼴). 변이 전에 산출물을 커밋했고, 되돌림은 `git restore --source=HEAD --worktree --`
한 경로로 했다(`git checkout --` 는 쓰지 않는다 — 미커밋 산출물을 함께 지운다).

| 판 | 명령 | 결과 |
|---|---|---|
| **head(15L)** | `npm run typecheck` | **exit 2** — `TS2345: Property 'shown' is missing … but required in type 'NameSource'` |
| **통제군: base `a189e8c58`** (임시 clone, `npm ci` 뒤 같은 변이) | `npm run typecheck` | **exit 0** — 이 OPEN 이 적은 사각 그 자체. base 의 `typecheck` 는 프로젝트가 하나다 |

## 변이 실측 — 층을 조용히 비우는 길 (수정 라운드 1 뒤)

각 행은 **「15K 가 고친 칸을 다시 뺀 상태」**(= 잡아야 할 오류가 실제로 거기 있는 판)에서 그 변이를
더해 쟀다. 덮개 게이트는 `npx vitest run scripts/e2e-typecheck-coverage.test.mjs`.

| 변이 | typecheck | 덮개 | 판정 |
|---|---|---|---|
| (통제군) 변이 없음 | 2 | 1 | 잡힘 |
| `tsconfig.e2e.json` 에 `"noCheck": true` | 0 | **1** | 잡힘 |
| 명령에 `--noCheck` | 0 | **1** | 잡힘 |
| 명령 끝에 `\|\| true` | 0 | **1** | 잡힘 |
| 명령에 ` ; true`(띄운 꼴) | 0 | **1** | 잡힘 |
| `strict` 아래 `strictNullChecks`·`noImplicitAny` 끄기 | 2 | **1** | 잡힘 |
| 둘째 프로젝트 제거 | 0 | **1** | 잡힘 |
| `include` 를 `e2e/journey` 로 좁힘 | 2 | **1** | 잡힘 |
| `noUncheckedIndexedAccess` 끄기 | 2 | **1** | 잡힘 |

앞 판에서는 위 넷(`noCheck` 둘 · `|| true` · strict 하위)이 전부 **typecheck 0 · 덮개 0** 이었다
(verifier H-1 · cr M-1·M-2). 「`include` 가 하나도 못 맞힌다」는 여전히 tsc 가 `TS18003` 으로 먼저
떨어진다(exit 2).

## 변이 실측 — 다른 층이 받는 자리

| 변이 | 결과 |
|---|---|
| e2e 에서 필수 칸을 빼고 그 자리에 `// @ts-expect-error: <설명>` | `typecheck` **0**(설계상 억제) · `eslint` **1**(`ban-ts-comment`) — 받는 층이 다르다 |
| 단독 `.mts` 에 타입 오류를 둔다 | `typecheck` **2** · 덮개 **1**. 앞 판은 `include` 와 훑기 둘 다 밖이라 exit 0 이었다 |
| 화면 프로젝트(`tsconfig.json`)의 `types` 에 `node` 추가 | `gate:src-tree` **1**. 앞 판은 exit 0 |

## 변이 실측 — 수정 라운드 2

| 변이 | 결과 | 앞 판 |
|---|---|---|
| `strictBuiltinIteratorReturn` 을 **양쪽** 끄기 | typecheck 0 · 덮개 **1** | 덮개 **0**(손 목록에 그 축이 없었다) |
| `src` 에 `/// <reference types="node" />` + `process.env` | typecheck **0**(그 줄이 TS2591 을 없앤다) · `gate:src-tree` **1** | `gate:src-tree` **0** |
| (통제군) 그 줄 없이 `process.env` 만 | typecheck **2** · `gate:src-tree` 0 | 같음 — 붉음이 **그 줄**에서 오는 것을 가른다 |
| 뿌리 `playwright.extra.config.mts` 에 `@ts-expect-error` | eslint **1** · 덮개 **1** | eslint **0**(뿌리 glob 이 `.ts` 뿐) |
| `// eslint-disable-next-line …ban-ts-comment` + `// @ts-nocheck`(심은 오류와 **같은 파일**) | lint **0** · typecheck **0** · 덮개 **1** | **0 / 0 / 0** — 세 층이 모두 지나갔다 |
| 같은 disable + `// @ts-expect-error: …`(다른 파일) | lint **0** · typecheck 2 · 덮개 **1** | 덮개 **0** |

마지막 둘이 이 라운드의 핵심이다: **lint 와 typecheck 가 둘 다 0 인데 덮개가 1 이다** — 억제를
eslint 주석으로 끌 수 없는 자리에서 세기 때문이다. `src` 레인의 같은 구멍은 고치지 않고
`OPEN-M15-SRC-TS-COMMENT-DISABLE` 로 등재했다.

## 거짓 RED 가 아닌 것 (닫힌 쪽 실패도 비용이다)

| 꼴 | 결과 |
|---|---|
| `-p ./tsconfig.e2e.json`(같은 뜻의 글자) | typecheck **0** · 덮개 **0** |
| `--project` 장형 | typecheck **0** · 덮개 **0** |
| 무변이 head(양성 통제군) | lint **0** · typecheck **0** · 덮개 **0** · `gate:src-tree` **0** |

## 개인정보·비밀값

이 slice 가 만지는 것은 타입 검사 설정과 게이트 test 다 — 자격·개인 값에 닿는 자리가 없다.
`command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15l/` 매치 0.
