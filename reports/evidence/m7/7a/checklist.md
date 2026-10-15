# M7/7A checklist — P-5(`web/` 골격)·P-6(one-command)

- 계약: 같은 디렉터리의 `scope.md`. 설계 검토: `_workspace/m7-7a/01_design-review.md`(우회 표 U-1~U-8).
- base `d930d54c` · 브랜치 `m7-7a/2026-10-14` · 이 문서가 담는 마지막 산출물 커밋 `e3630411`.
- 명령과 종료 코드는 `commands.md`, 되돌림은 `rollback.md`.
- 문서 산출물(P-1~P-4)은 세션 모델 레인 소관이라 이 문서에 넣지 않는다.
- **이 게이트 셋이 증명하는 범위**(ADR 0011 D-3 · 7A 검토 r5): ① legacy **파일** 유입 0(프로그램
  전수 · 번들 그래프 · 잠금 출처) ② 「클라이언트 패키지(**정체** 기준)를 끌어오는 자리 ==
  {`src/api/client.ts`}」. **「서버를 부르는 경로가 그것 하나뿐」은 증명하지 않는다** — 브라우저가
  HTTP 를 낼 길은 전역·다른 의존·외부 URL·`index.html` 처럼 여럿이고, 그 주장은 보조 lint 와 리뷰
  소관이다(§4 의 알려진 제한).

## 1. 게이트의 층 — 무엇이 어디서 닫히는가

| 층 | 자리 | 닫는 것 | 성질 |
|---|---|---|---|
| 컴파일 경계 | `tsconfig` `rootDir`·`include`(W-3) | rootDir 밖 `.ts`·`.js` 의 상대 경로·별칭 import → TS6059. **TS6059 는 접두 비교다** — `src` 를 접두로 공유하는 이웃(`web/src-legacy/x.ts`)에는 나지 않고(실측: typecheck 0) 그 자리는 W-4 의 프로그램 전수(성분 단위 비교)가 받는다(실측: exit 1) | 구성 |
| 프로그램 전수 | W-4 (`ts.createProgram(...).getSourceFiles()`) | import·`files`·triple-slash·`src` 안 링크로 끌려온 **모든** 파일(선언 파일 포함)이 `src`∪`node_modules` 안인가 | 컴파일러 API |
| 경계의 전제 | W-4 (`lstat`·realpath·유효 옵션·AST) | `src` 자체가 링크인가 · `rootDir`·`include`·`allowJs`·`paths` 가 바뀌었나 · `web/` 안 링크(설치본·산출물 제외) · 경계 플러그인이 두 자리에 **원소로** 등재됐나(**빠른 선행 검사** — 활성은 카나리가 잰다) | 컴파일러 API + 파일시스템 |
| 호출 자리 집합 — TS **프로그램 안** | W-4 (`ts.resolveModuleName` → 해석된 모듈의 **패키지 이름**) | 클라이언트 패키지로 해석되는 import 집합 == `{src/api/client.ts}` · 비리터럴 동적 import 0 · 해석되지 않는 bare 지정자는 위반 · **`src` 안 비 TS 스크립트 0**(그 파일은 tsc 프로그램에도 네트워크 린트에도 들어오지 않았다) | 집합 등식 — **이 층은 TS 프로그램만 본다** |
| 호출 자리 집합 — **번들 전수** | 경계 플러그인(W-7, 메인·워커) | 클라이언트 패키지(식별은 **패키지 이름**)를 패키지 밖에서 끌어온 간선 전수 — **정적 `importers` ∪ 동적 `dynamicImporters`** — 가 `src/api/client.ts` 하나인가. Vite 의 실제 해석을 쓰므로 `resolve.alias`·접미 지정자·TS 밖 파일(`index.html` 인라인)의 **그 패키지 import** 도 같은 술어에 걸린다 — 다만 그 파일의 **직접 `fetch`·외부 URL import** 는 어느 층의 대상도 아니다(§4) | 집합 등식 |
| **번들 모듈 그래프** | Vite 플러그인 `buildEnd` — 메인 `plugins` **와** `worker.plugins` 두 자리(W-7) | 번들이 실제로 읽은 모듈 전수가 `src`∪`node_modules`∪`index.html` 안인가. 워커는 **별도 Rollup 빌드**라 등재가 두 자리다 | 그래프 단언 |
| **경계의 활성** | 카나리 빌드(W-8) | 실제 `vite.config.ts` 로 빌드를 돌려 web 밖 파일을 끌어온 probe 가 **경계 오류로 거부되는가** — 메인·워커 둘 다 | 효과 측정 |
| node_modules 의 출처 | W-4 (잠금 파일·`.npmrc`) | 등록부가 있고 비지 않음 · `packages[*].resolved` 전수가 **레지스트리 origin**(`https://registry.npmjs.org/`) · `link:true`·`file:`·git 0 · `inBundle` 은 **부모 출처**로 센다 · 클라이언트 패키지의 **설치 경로 집합이 하나**(npm 별칭 0) · `install-links` 설정 없음 | 정본 대조 |
| 생성 타입 등식 | W-5 | 커밋본 ↔ OpenAPI 재생성본 바이트 등식 | 바이트 비교 |
| 보조(실수 잡기) | eslint(W-2) | 네트워크 이름의 **점 접근·리터럴 계산 접근·템플릿 형태·구조 분해**(소유자를 열거하지 않는다) · HTTP 패키지 정적 import · ts 지시어 억제 · 훅 규칙 | 린트 — **대상은 `src` 의 `.ts`·`.tsx` 뿐**이고 의도적 우회는 리뷰 소관 |

**왜 아래 두 층이 따로 필요한가**: 게이트를 전부 **TS 프로그램**에 걸면 번들의 입력인
**Vite/Rollup 모듈 그래프**는 비어 있는 채 남는다 — 두 그래프는 다른 집합이다(§3.2 N-4~N-7).
그리고 프로그램 검사의 기준점을 realpath 로 먼저 풀어 쓰면 그 검사가 항진명제가 된다(§3.1 F-1).

## 2. 수용 조건 대조

| 조건(scope.md) | 충족 근거 |
|---|---|
| P-5 「`web` job 명령 그대로 로컬 exit 0」 | W-1~W-10 을 순서대로 그대로 실행해 전부 exit 0(`commands.md`). CI 전용 step 둘(checkout·setup-node, `install-deps`)은 로컬에서 성립하지 않아 뺐고 그 둘은 게이트가 아니다 |
| P-5 「생성 타입 check 모드 음성 대조」 | §3.4 세 행 — OpenAPI 한 줄 변경 · 생성물 손 편집 · 생성물 삭제 전부 exit 1, 되돌린 뒤 exit 0. 실패 경로가 임시 디렉터리를 남기지 않는 것도 실측(0 → 0) |
| P-5 「legacy 경로·타입 import 0(게이트)」 | §3 의 **U-1~U-9 · N-1·N-2 · N-4~N-9 · P-1\* · F-1·F-2 · O-5 · G-3 전부 RED**. 통제군 행(변이 없는 트리 · 되돌린 트리)이 같은 명령에서 exit 0 인 것과 짝으로 읽는다. **열린 자리는 §4 에 등재**(CSS `@import`·`url()` 자산 · 계산된 전역 이름 · 하드 링크) |
| P-5 「화면 구현 없음(빈 셸 + 라우트 자리 여섯 — 화면 A~E 다섯 + 알림함, 그리고 없는 주소 자리)」 | 자리 여섯(화면 다섯 + 알림함)의 빈 셸 + 자리 표 **밖**의 없는 주소 자리. 질의·상태·디자인 토큰 0. 단위 test 가 경로 여섯·순서·id 중복 0·catch-all 이 표 밖임을 잠그고, e2e 가 알 수 없는 주소에서도 셸이 뜨는 것을 잰다. **오류 경계는 셸 라우트가 아니라 그 안의 경로 없는 자식**에 있다 — 셸 라우트에 두면 자식 throw 가 셸을 갈아치운다(구판에서 RED 를 실측했고 `AppRoot.test.tsx` 가 그 자리를 잠근다, PR #75 리뷰 A) |
| P-6 「S-20 exit 0」 | `tools/one-command-check.sh` exit 0(`commands.md`). web 절 명령은 job 과 순서까지 같고, CI 에서 그 스크립트를 돌리는 `check` job 에 Node 툴체인과 브라우저 시스템 의존을 더했다 — 같은 게이트를 두 다른 툴체인에서 재던 상태를 끝낸다 |

## 3. 우회 실측

판정 기준: 해당 층의 명령(`typecheck` · `gate:src-tree`(W-4) · `lint` · `build` · `api:types:check`)
중 **하나라도 비0** 이면 RED. 변이는 **바꿔치우기**다 — 모든 행은 통제군(같은 명령 전부 exit 0)과
대조해 읽는다. 변이는 산출물 커밋 뒤에 심고 즉시 되돌렸으며, 되돌림은 `git checkout --` 이 아니라
사본 복원·신규 파일 삭제로 했다. legacy 대상은 실제 저장소(직접 상대 경로 또는 `bid-vector` 링크
경유)를 쓰고, 저장소에 없는 모양이 필요한 행만 web 밖 fixture 를 쓴다.

### 3.1 프로그램·컴파일 경계

| # | 변이 | typecheck | W-4 | lint | 판정 |
|---|---|---|---|---|---|
| — | 통제군 | 0 | 0 | 0 | GREEN(대조 기준) |
| U-1 | legacy `.ts` 를 상대 경로로 import | 2 | 1 | 0 | RED |
| U-2 | `paths` 별칭이 legacy 를 가리킴 | 2 | 1 | 0 | RED |
| U-3 | `src` 안 심볼릭 링크(파일) → legacy | **0** | 1 | 0 | RED |
| U-4c | `@ts-nocheck` + legacy import | **0** | **1** | 1 | RED — 지시어 억제가 W-4 에는 통하지 않는다 |
| U-8 | `allowJs: true` + rootDir 밖 legacy `.js` | 2 | 1 | 0 | RED |
| U-9 | `files` 로 밖의 파일을 프로그램에 추가 | 2 | 1 | 0 | RED |
| N-1 | 밖의 **`.d.ts`** 를 `import type`(실제 legacy `openapi.d.ts`) | **0** | **1** | 0 | RED (W-4 단독) |
| N-2 | `/// <reference path>` → 밖 `.d.ts` | **0** | **1** | 1 | RED — W-4 가 지므로 상속 린트 규칙에 의존하지 않는다 |
| F-1 | **`web/src` 자체를 심볼릭 링크로** | 0 | **1** | 0 | RED (W-4 단독) |
| F-2 | 리터럴 동적 `import('openapi-fetch')` + 손 계약 | 0 | **1** | 0 | RED (집합 등식) |
| F-2b | 비리터럴 동적 `import(변수)` | 0 | **1** | 0 | RED |
| O-5 | `import cc from '../node_modules/openapi-fetch'`(상대 경로로 같은 패키지) | 0 | **1** | 0 | RED — 집합 등식을 **해석된 모듈**로 세므로 지정자 모양과 무관 |
| O-5b | 서브패스 `openapi-fetch/dist/index.js` | 0 | **1** | 0 | RED |
| O-5c | 승인된 호출 자리가 그 import 를 잃음 | 0 | **1** | 0 | RED — 등식의 반대 방향(「하나뿐」이 아니라 「하나는 있다」) |
| G-4 | 건너뛰기 이름을 **링크 이름으로**(`src/dist` → legacy) | 0 | **1** | 0 | RED, 사유가 「web/ 안에 심볼릭 링크가 있다: src/dist」로 정확해졌다 |
| R-1 | `resolve.alias: { '@oa': 'openapi-fetch' }` + `src` 안 `declare module '@oa'` → 호출 자리 밖에서 손 경로 호출 | 0 | **1**(「지정자를 해석할 수 없다: … → @oa」) | 0 | RED — **build 도 1**(번들 그래프의 집합 등식: 「호출 자리(src/api/client.ts) 밖에서 끌어온다」) |
| R-1b | 같은 시도를 **워커 파일 안에서** | 0 | **1** | 0 | RED — 워커 그래프의 플러그인 인스턴스가 같은 등식을 센다 |
| H-1 | `boundaryRoots` 에 legacy 뿌리 한 줄 + `…?raw` import | 0 | **1** | 0 | RED — **W-6 도 1**(허용 뿌리 집합 등식). 뿌리를 넓혀도 build 는 초록이므로 이 둘이 그 변경을 보는 층이다 |
| V-1 | `src/legacy-call.js`(+`.d.ts`)의 **동적** `import('openapi-fetch')` → 손 경로 호출 | 0 | **1**(「src 안에 TS 프로그램이 보지 못하는 스크립트가 있다」) | 0 | RED — **build 도 1**(동적 간선: `legacy-call.js → …/openapi-fetch/dist/index.mjs`) |
| V-4 | npm 별칭 의존 `of2@npm:openapi-fetch` + `import createClient from 'of2'` | 2(곁가지) | **1** 두 사유(「설치돼 있다(별칭 의존): node_modules/of2」 · 「호출 자리 밖에서 import 한다: src/_aliased.ts」) | 0 | RED — **build 도 1**. 식별이 디렉터리 이름이 아니라 **패키지 이름**이라 별칭 디렉터리도 클라이언트로 센다 |

### 3.2 번들 모듈 그래프 (build 단계) — 메인과 **워커** 둘

| # | 변이 | build | W-4 |
|---|---|---|---|
| — | 통제군 | 0 | 0 |
| N-4 | `import.meta.glob('…/*.ts', { eager: true })` (fixture · 실제 legacy `shared/api/*.ts` 둘 다) | **1** | 0 |
| N-5 / N-5b | `…side.ts?worker` / `?raw` | **1** | 0 |
| N-6 | `resolve.alias` + `src` 안 `declare module` | **1** | 0 |
| N-7 | `index.html` 의 `<script type="module" src="../…">` | **1** | 0 |
| **P-1** | `src/w.ts` 가 밖을 glob 하고 `new Worker(new URL('./w.ts', import.meta.url), { type: 'module' })` | **1** | 0 |
| **P-1b** | 같은 워커 파일을 `import W from './w.ts?worker'` | **1** | 0 |
| **P-1c** | P-1 을 실제 legacy `frontend/src/shared/api/*.ts` 로 | **1** | 0 |
| V-2 | `index.html` 인라인 스크립트의 **동적** `import('openapi-fetch')`(src 무변경) | **1** | 0 — W-4 는 TS 프로그램만 본다(그 문면을 출력에 적었다) |
| N-9 | `web/public/` 안 디렉터리 링크 → legacy | 0 | **1** |
| N-8 | `src/index.css` 의 `@import` 로 밖 CSS | 0 | 0 | **열림 — §4** |

P-1 계열이 r2 의 high 였다: Vite 는 워커를 **별도 Rollup 빌드**로 묶고 그 빌드는 `worker.plugins`
만 쓰므로, 메인 그래프에는 `src/w.ts?worker_file…` 하나만 보여 경계가 그것을 `src` 안으로 봤다.
같은 플러그인을 `worker.plugins` 에도 등재해 닫았다. build RED 는 전부 경계 플러그인의 오류 문면으로
확인했고(다른 원인 아님) 메시지가 **문제 파일의 realpath** 를 적는다.

### 3.2b 경계를 **끄는** 형태 — W-4(선행 검사)와 카나리(활성 측정)

| # | `vite.config.ts`·플러그인 변형 | W-4 | 카나리 | 누가 잡나 |
|---|---|---|---|---|
| — | 통제군 | 0 | 0 | — |
| A-2 | `process.env.NOPE ? boundary() : null` | **1** | **1** | 둘 다 |
| A-3 | `{ ...boundary(), buildEnd() {} }` | **1** | **1** | 둘 다 |
| A-4 | `{ ...boundary(), apply: 'serve' }` | **1** | **1** | 둘 다 |
| A-6 | `[moduleGraphBoundary][1]`(참조만, 호출 없음) | **1** | **1** | 둘 다 |
| B-7 | `[moduleGraphBoundary].length && react()` | **1** | **1** | 둘 다 |
| G-1B | `...(false ? [boundary()] : [])` | **1** | **1** | 둘 다 |
| G-1A | `process.env.SKIP_BOUNDARY ? false : boundary()` | **1** | 0 | **W-4** — 킬 스위치가 **그 실행에서 꺼져 있으면** 카나리는 정상 통과한다(켜져 있으면 빌드와 카나리가 같은 환경이라 카나리도 RED) |
| A-5 | 같은 객체에 `plugins` 키 둘(뒤가 이김) | 0 | **1** | **카나리** |
| B-5 | 뒤따르는 `...{ plugins: [...] }` 가 덮어씀 | 0 | **1** | **카나리** |
| 워커만 제거 | `worker.plugins` 만 떼어냄 | **1** | **1**(워커 사례) | 둘 다 |
| 본문 무력화 | 플러그인 `buildEnd` 를 `return;` 으로 무력화 | 0 | **1**(두 사례 모두) | **카나리** (W-6 단위 test 는 순수 술어만 잠그므로 0 — 이것이 카나리가 필요한 이유다) |
| 제거 세 형태 | `plugins` 에서 제거 / import 제거 / 파일 삭제 | **1·1·1** | — | W-4 |
| H-4a | 경계 플러그인이 **무관한 사유**로 멈춘다(`this.error('무관한 사유로…')`) | 0 | **1** | **카나리** — 「경계 플러그인이 멈추기는 했지만 이 실행의 fixture 를 거부한 것이 아니다」 |
| H-4b | 경계를 끄고 **같은 이름·같은 문면**의 가짜 플러그인이 탐침 id 에 반응 | 0 | **1** | **카나리** — 가짜는 이 실행의 **무작위 fixture 실경로**를 적을 수 없다 |

### 3.3 호출 자리·전역 네트워크 표면 (보조 층)

| # | 변이 | lint | 잡은 규칙 |
|---|---|---|---|
| U-5a~e | 전역 `fetch` · `window.fetch` · `new XMLHttpRequest()` · `axios` import · `openapi-fetch` 정적 import | 1 | globals / properties+syntax / globals / imports / imports |
| N-13~N-17 | `self.XMLHttpRequest` · `navigator.sendBeacon` · `EventSource` · `WebSocket` · `document.defaultView?.fetch` · 지역 별명 `w.fetch` | 1 | 소유자를 열거하지 않는 **속성 이름** 술어(`no-restricted-syntax`) |
| 통제군 | 같은 `fetch` 호출을 `src/api/client.ts` **안**에서 | 0 | 설계대로 — 허용 자리가 실제로 하나임을 잰다 |
| 훅 | 조건부 훅 호출 | 1 | `react-hooks/rules-of-hooks` |
| L-14 | 목록 **밖** 소유자의 계산 접근 — `globalThis.window['fetch']` · `document.defaultView!['fetch']` · `top!['fetch']` · `parent['fetch']` · `frames['fetch']` · `const { fetch: f } = top!` | 1 | 소유자 무관 선택자 넷(점 접근·리터럴 계산·템플릿·구조 분해) — 앞 판(소유자 × 이름)에서는 여섯 전부 **0** 이었다 |
| N-10 | `globalThis['fe'+'tch'](…)`(**계산 조립**) | **0** | **열림 — §4** |
| N-11 | `Reflect.get(window, 'fetch')` | **0** | **열림 — §4** |

### 3.4 생성 타입 등식

| # | 변이 | api:types:check |
|---|---|---|
| U-6a | 생성물 손 편집 | 1 |
| U-6b | OpenAPI 한 줄 변경 후 재생성 안 함 | 1 (바이트 30879 ≠ 30894) |
| U-6c | 생성물 삭제 | 1 (사유가 다른 메시지) |
| — | 되돌린 뒤 · 같은 명령 연속 2회 | 0 · 0 — 두 번 새로 생성한 산출물이 서로·커밋본과 바이트 동일 |

### 3.4b 클라이언트 패키지 사용처 — 다른 유입 모양 (r5 재현)

| # | 변이 | 막은 층 |
|---|---|---|
| G-1 / G-2 | `src` 에서 `import.meta.glob('../node_modules/openapi-fetch/…')` eager / lazy | **build**(정적 / 동적 간선) |
| W-1 | 워커 파일 안 lazy glob → 동적 import | **build**(워커 그래프의 동적 간선) |
| X-1 | `src` 안 re-export `export { default as cc } from 'openapi-fetch'` | **W-4**(집합 등식) |
| H-3 | `index.html` 인라인 **비리터럴** 동적 import | 어느 층도 아니지만 런타임에서 bare 지정자가 해석되지 않아 기능하지 않는다(참고 — 「비리터럴 동적 import 0」은 `src` 한정 단언이다) |
| 잠금 origin | `registry.npmjs.org.evil` · `…@evil.example` · 대소문자 · `:443` · 자격 포함 | 전부 **W-4 1**(origin 접두 뒤의 `/` 가 호스트 확장을 막는다 · 뒤 둘은 fail-closed 쪽 오탐) |

### 3.4c 게이트 test 가 **돌았는가** (W-7 — 실행 결과 단언)

W-6 는 수집된 test 만 본다 — 수집에서 빠지거나 꺼진 test 는 **조용히 초록**이다. 아래 변이는 모두
W-6 가 0 인 채 게이트 test 를 무력화했고(앞 판의 AST `include` 리터럴 단언도 전부 지나갔다),
W-7 이 한 술어로 받는다.

| # | 변이 | W-6 | W-7 | 사유 문면 |
|---|---|---|---|---|
| — | 통제군 | 0 | 0 | — |
| B-1 | `test.exclude` 에 `'scripts/**'` 한 줄 | 0 | **1** | 「수집에 없다」 |
| B-2 | `include` 리터럴을 **미끼** 속성(`...{ x: { include: […] } }`)으로 옮김 | 0 | **1** | 「수집에 없다」 |
| B-4 | 게이트 test 의 `describe` → `describe.skip` | 0 | **1** | 「꺼진 단언이 있다」 |
| B-5 | `package.json` 의 `"test": "vitest run src"`(CLI 경로 필터) | 0 | **1** | 「수집에 없다」 — 같은 npm 스크립트를 타므로 필터가 함께 적용된다 |
| — | 게이트 test 파일 삭제 | 0 | **1** | 「파일이 없다」 |
| — | 등록부에서 그 줄 제거 | 0 | **1** | 「등록부가 비었다」(공허한 전수 단언을 막는다) |

### 3.5 `node_modules` 의 출처 (W-4 — 잠금 파일·`.npmrc`)

| # | 변이 | typecheck | W-4 | build |
|---|---|---|---|---|
| G-3 | web 밖 패키지를 `file:` 의존으로 더하고 `.npmrc` 에 `install-links=true` → `npm ci` 가 **실디렉터리 사본**을 `node_modules` 에 만든다. bare specifier 로 그 타입·코드를 import | 0 | **1** | 0 |
| — | 같은 상태의 W-4 사유 둘 | — | 「잠금 항목의 출처가 레지스트리 URL 이 아니다: … → file:../legacy-pkg」 · 「`.npmrc` 에 install-links 설정이 있다」 | — |
| — | 되돌린 뒤 | 0 | 0 | 0 |

이 행이 ④ 의 전제(「`node_modules` 안은 레지스트리에서 온 것」)를 재는 자리다 — typecheck·build 는
이 변이를 **보지 못한다**(둘 다 0).

전수 단언은 **대상이 0 건이면 공허하게 참**이므로 등록부 자체를 먼저 단언한다(같은 저장소의
`leakPatternGate` 가 패턴 0 건에서 멈추는 것과 같은 꼴):

| # | 변이 | W-4 |
|---|---|---|
| L-7 | `lockfileVersion: 1`(npm 6 형 — `packages` 없음, `dependencies` 만) | **1**(두 사유: 버전 · 등록부 부재) |
| H-2a | `packages: {}` | **1**(「전수 단언이 공허하게 통과한다」) |
| H-2b | 뿌리 항목(`""`) 제거 | **1** |
| H-3a | `inBundle: true` 항목 + 부모가 `file:` | **1**(부모·자식 두 사유) |
| H-3b | `inBundle: true` 항목 + 부모가 레지스트리 URL | **0** — npm 의 정상 모양을 막지 않는다(출처는 부모가 정한다) |
| L-10 | `resolved: https://codeload.github.com/…/tar.gz/<sha>`(비레지스트리 https tarball) | **1** — 술어가 `https://` 접두가 아니라 **origin 일치**다 |
| V-4 | npm 별칭 의존 항목(`name: openapi-fetch` · `resolved` 는 그 tarball) | **1** — 설치 경로 집합 등식(별칭 항목의 `version` 에는 실제 판이 들어가 `npm:` 정규식은 이 자리에서 결코 맞지 않는다) |
| L-13 | 같은 위반의 **사유 분리**(판정은 그대로) — 중첩 사본 `node_modules/react/node_modules/openapi-fetch` · 별칭 `node_modules/of2` · 둘 다 `node_modules/foo/node_modules/of3` | **1 · 1 · 1**, 사유가 각각 「중첩 사본」·「별칭 디렉터리」·「중첩 사본 + 별칭 디렉터리」로 나온다 |

## 4. 알려진 제한 (열린 자리와 그 조건)

- **스타일과 자산은 데이터 모양이 아니다.** `src/index.css` 의 `@import` 로 legacy CSS 를 끌어오면
  번들에 들어가고(실측: build 0 · W-4 0 · dist 에 실재), CSS `url('../../<밖>/bg.png')` 로 web 밖
  **자산 파일**이 dist 에 실린다(verifier r2 L-6 실측). 번들 그래프 플러그인은 CSS `@import`·`url()`
  을 모듈 id 로 보지 않는다. ADR 0011 D-4 「복사하지 않는다」의 리뷰 대상으로 둔다 — `web/public/`
  쪽 링크는 W-4 가 막는다(N-9).
- **`index.html` 인라인 스크립트와 외부 URL 은 어느 게이트의 대상도 아니다**(r5 M-10 실측).
  진입 문서의 인라인 `<script type="module">` 안의 평문 `fetch('/legacy/…')` 와
  `import('https://esm.sh/openapi-fetch@…')` 는 다섯 게이트가 모두 초록이고 dist 에 그 호출이
  남는다 — 린트의 대상은 `src` 의 `.ts`·`.tsx` 뿐이고, 외부 URL 은 번들 **그래프에 들어오지
  않아** 간선이 없다. 우회 형태가 아니라 **대상 밖**이다. 구조로 줄일 싼 후보(`index.html` 의
  `<script>` 집합 등식)는 「유일」을 증명하지 않으므로 7A 에서 하지 않고 **7E 가 화면을 올릴 때
  다시 본다**(팀장 결정).
- **다른 HTTP 의존·vendoring 사본은 집합 등식 밖이다**(r5 M-11 실측). 집합 등식은
  `openapi-fetch` 의 **정체**에 걸려 있다. 새 의존(`axios` 등)을 더해 **동적** `import()` 로 쓰면
  다섯 게이트가 초록이다(`no-restricted-imports` 는 `import()` 식을 보지 않고, 그 이름 목록은 ADR
  이 스스로 1차 게이트로 쓰지 않는다고 적은 열거다). 다른 패키지 안에 vendoring 된 사본도 그
  패키지 이름으로 식별된다. 싼 후보(`dependencies` 키 집합의 승인 목록 등식)도 같은 이유로 7E 로
  미룬다.
- **전역 네트워크 표면에 남는 형태는 「계산 조립」과 `Reflect` 둘이다**(r6 L-14 처리 뒤). 소유자
  열거는 걷었고(점 접근·리터럴 계산 접근·템플릿 형태·구조 분해를 **소유자와 무관하게** 받는다,
  실측 아홉 행) 남는 것은 이름을 **런타임에 조립**하는 형태(`['fe'+'tch']`)와
  `Reflect.get(window,'fetch')` 다 — 둘 다 정적 선택자로 닫을 수 없고 리뷰 소관이다.
- **전역 네트워크 표면의 의도적 우회.** 계산으로 만든 이름(`globalThis['fe'+'tch']`)·
  `Reflect.get(window,'fetch')` 는 보조 층을 지난다(실측). 이 층의 주장은 「실수로 쓴 직접 호출을
  잡는다」까지이고 그 문면이 eslint 설정 머리 주석에 그대로 있다. **대상 범위도 그 주석에 적었다** —
  `src` 의 `.ts`·`.tsx` 뿐이다(그래서 위 두 항이 그 밖이다). 반대 방향의 대가도 적는다 —
  속성 이름 술어는 **오탐**을 낸다(네트워크와 무관한 `.fetch` 멤버도 걸린다, 실측). 그때는 그 줄에
  `eslint-disable-next-line` 을 달고 리뷰로 간다.
- **게이트 test 의 수집은 설정 문자열로 서지 않는다**(r6 M-12 처리 결과). 앞선 판은
  `vite.config.ts` 의 `include` 리터럴을 AST 로 찾았는데, 그 술어는 **자기가 막겠다고 한 형태의
  거울상**(`test.exclude` 한 줄 · 미끼 `include` 속성 · `describe.skip` · CLI 경로 필터)을 못
  막았다(§3.4c). 이제 W-7 이 **W-6 와 같은 명령**에 JSON reporter 를 붙여 돌려 등록부의 파일이
  실제로 통과했는지 본다. 남는 자리는 **그 스크립트·step·등록부 줄을 지우는 것**이고 아래 항과
  같은 부류(리뷰 소관)다.
- **플러그인·게이트 스크립트 자체의 수정.** 층이 셋이다: W-4 가 **등재**를 AST 로 보고(배열의 직접
  원소가 그 플러그인의 호출인가 — 메인·워커 두 자리), W-6 가 **판정 술어와 허용 뿌리**를 잠그고
  (허용 뿌리는 집합 등식으로 — 뿌리에 한 줄을 더하면 다른 층은 보지 못하고 W-6 만 깨진다, 실측),
  카나리(W-8)가 **활성**을 빌드로 잰다. 측정으로 각 층이 혼자 잡는 자리가 갈린다(§3.2b) — 특히 **플러그인 본문을
  비우는 수정은 W-4·W-6 가 아니라 카나리가 잡는다**. 카나리가 잡는 것은 **우연히 끈 형태**
  (조건식·덮어쓰기·중복 키·`apply`·본문 비우기)다. 통과 조건의 「**이 실행의** fixture 실경로가
  오류 문면에 있다」가 주장하는 것은 두 가지까지다 — 경계가 **다른 사유로** 낸 오류를 통과로 읽지
  않는다 · **탐침 id 에만 반응하는**(경로를 적지 않는) 가짜를 가른다. **탐침 경로를 그대로 적어
  주는 가짜는 가르지 못한다**(실측).
- **카나리를 알아채는 설정 일반은 리뷰 소관이다.** 실측된 형태 넷: `process.argv` 분기 · 빌드
  옵션 모양으로 가르기(`rollupOptions.input` 이 문자열인지 · `outDir` 위치) · 탐침 이름 접두에
  반응 · 같은 이름의 가짜 플러그인이 그 경로를 그대로 적어 `this.error`. 무작위 nonce 와 임시
  `outDir` 은 그 폭을 줄이는 조치일 뿐 닫는 것이 아니다 — 자기 검사는 언제나 특례로 갈 수 있다.
- **환경변수 킬 스위치**: `cond ? false : boundary()` 형태는 W-4 가 모양으로 잡는다. 카나리는 빌드와
  **같은 환경**에서 돌므로, 그 스위치가 CI 에서 켜져 있으면 카나리도 함께 RED 가 된다(실측: 스위치가
  꺼진 실행에서는 카나리가 정상 통과 — 그때 잡는 것은 W-4 다).
- **하드 링크**로 legacy 파일을 `src` 에 들이면 커밋 시점에 일반 파일이 된다 — 위협 모델이 밖으로
  둔 「복사」와 같은 부류다(구조로 막지 않는다, 리뷰 소관).
- **tsconfig `types`/`typeRoots`** 로 끌어오는 선언도 이제 프로그램 전수 검사의 대상이다(U-9·N-1 과
  같은 술어). `types` 가 가리킬 수 있는 `node_modules` 안 패키지는 허용 뿌리 안이고, 그 출처는 잠금
  파일 소관이다.
- **번들 층의 등식은 클라이언트를 아무도 쓰지 않으면 공허하게 참이다**(설계대로). 7A 의
  `apiClient` 는 어디서도 import 되지 않아 그래프에 없고, 실리지 않은 코드에 대해 단언할 것이
  없다. 반대 방향(「`client.ts` 가 import 한다」)은 W-4 의 TS 등식이 진다. 7E 가 `apiClient` 를
  화면에 연결한 뒤에는 「그 패키지가 그래프에 있고 간선 집합이 정확히 `{client.ts}`」로 조일 수
  있다 — 그때 조이는 것이 이 항의 후속이다.
- **CI 에서 web 레인이 두 번 돈다**(PR #75 리뷰 W). `web` job 이 W-1~W-10 을 돌고, `check` job 의
  S-20 이 같은 명령을 다시 돈다(완료 조건 1 이 one-command 를 CI 에서 직접 재기 때문이다). 비용은
  설치·빌드·브라우저 다운로드가 두 번이고, 그 대가로 「one-command 가 실제로 성립한다」가 CI 에서
  측정된다. 줄이려면 완료 조건 1 의 측정 자리를 옮기는 결정이 필요하다 — 7A 에서 하지 않는다.
- **`check` job 의 web 설치가 실패하면 S-20 의 Kotlin·Python 전건까지 건너뛴다**(PR #75 리뷰 L).
  `one-command-check.sh` 는 `set -euo pipefail` 로 web 절의 `npm ci` 실패에서 멈추는데, 그 절이
  **스크립트 끝**에 있어 앞의 Kotlin·Python 은 이미 돌았다 — 반대로 `check` job 에 새로 둔
  **설치 step 둘**(`npm ci`·`install-deps`)은 S-20 **앞**이라 거기서 실패하면 S-20 자체가 돌지
  않는다(네트워크·apt 의존이 Kotlin 게이트의 전제가 된 셈이다). 같은 job 안이라 빨강은 보이지만
  원인과 범위를 혼동하지 않도록 등재한다.
- **one-command 의 「새 checkout 에서 e2e 까지」는 브라우저 시스템 패키지가 있는 장비 전제**다
  (PR #75 리뷰 V). 없는 장비에서는 `npx playwright install-deps chromium`(apt·sudo)이 먼저
  필요하고, 그 step 은 CI 전용으로 빼 두었다(스크립트 머리에 같은 전제를 적었다).
- **스모크 실패에 산출물이 0 건인 경우**(PR #75 리뷰 G). `web` job 의 trace 업로드는 조건을
  스모크 step 하나로 좁히고 `if-no-files-found: error` 를 유지한다 — 이번 PR 의 RED 처럼
  webServer 가 뜨지 않아 test 가 한 번도 돌지 못하면 trace 가 0 건이고, 그 상태는 조용히 넘기지
  않고 업로드 step 의 빨강으로 표시된다. `check` job 쪽은 깨진 step 이 Kotlin·Python 일 수도
  있어 0 건이 정상이므로 기본(경고) 동작을 쓴다.
- **의존 패키지 취약점 스캔 없음** — `OPEN-7A-WEB-DEPENDENCY-SCAN`(7F). 이 slice 는 `npm ci` 가 잠금
  파일 밖을 해석하지 않고 `engine-strict=true` 로 선언 범위 밖 Node 에서 설치가 멈추는 것까지 본다
  (양성 대조: `engines.node` 를 범위 밖으로 바꾸면 `npm ci` exit 1 · 되돌리면 0).
- **Playwright 스모크는 둘**(셸이 뜬다 · 알 수 없는 주소에서도 뜬다). 여정 스모크는 7E·7D 뒤.
- `ml-engine`·`container` job 은 입력 무변경이라 돌리지 않았다 — 근거는 `commands.md` 의 기계 확인.
- **비활성화 방법**: `web/` 는 런타임 코드가 아니고 어디서도 참조되지 않는다(Gradle
  `settings.gradle.kts` 에 없고 Python 패키지도 아니다). CI 에서 끄는 길은 `web` job 삭제와 S-20 의
  web 절 제거 둘이다. 운영 영향 0.

## 5. N/A 항목 (evidence-pack 규격)

- `differential.json`: N/A — Python 대조 대상 거동이 없다(골격에 도메인 계산이 없다).
- `golden-manifest.json`: N/A — fixture 를 쓰지 않는다. 생성 타입의 입력은 저장소의 OpenAPI 문서이고
  그 등식은 W-5 가 바이트로 잰다.
