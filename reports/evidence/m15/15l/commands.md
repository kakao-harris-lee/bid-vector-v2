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

## acceptance — `web` job 전건(로컬, Gradle 절 제외)

| step | 명령 | exit |
|---|---|---|
| W-1 | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — 파일 77 · test 2046 전부 통과 |
| W-7 | `npm run gate:tests` | 0 — 등록부 기준 수집 확인(skipped/todo 0) |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |

W-4 는 `tsconfig.e2e.json` 이 생긴 뒤에도 같은 문면으로 통과한다 — 그 게이트가 읽는 것은
`tsconfig.json` 한 파일이고, 프로그램 전수·링크·집합 등식의 수가 base 와 같다.

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

## 변이 실측 — 층을 조용히 비우는 길 셋

세 변이 모두 **tsc 는 아무 말도 하지 않는다**. 그 자리를 게이트 test 가 받는 것을 잰다.

| 변이 | `npm run typecheck` | 게이트 test |
|---|---|---|
| ⓐ `typecheck` 에서 둘째 프로젝트를 뺀다 | exit **0** | **exit 1** — 덮이지 않은 e2e 파일 23 을 열거하고, strict 축 대조도 함께 붉는다 |
| ⓑ `include` 를 `e2e/journey/**` 로 좁힌다(일부만 맞힌다) | exit **0** | **exit 1** — 덮이지 않은 파일 7 |
| ⓑ′ `include` 가 **하나도** 못 맞힌다 | exit **2** — `TS18003: No inputs were found in config file` | (tsc 가 먼저 떨어진다) |
| ⓒ `noUncheckedIndexedAccess` 를 끈다 | exit **0** | **exit 1** — 두 프로젝트의 유효 옵션 대조가 붉는다 |

양성 쪽 통제군: 변이 없는 head 에서 같은 게이트 test 는 **exit 0 · 칸 넷 통과**다(2.0s).

## 개인정보·비밀값

이 slice 가 만지는 것은 타입 검사 설정과 게이트 test 다 — 자격·개인 값에 닿는 자리가 없다.
`command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15l/` 매치 0.
