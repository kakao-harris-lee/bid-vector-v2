# M7/7A checklist — P-5(`web/` 골격)·P-6(one-command)

- 계약: 같은 디렉터리의 `scope.md`. 설계 검토: `_workspace/m7-7a/01_design-review.md`(우회 표 U-1~U-8).
- base `d930d54c` · 브랜치 `m7-7a/2026-10-14` · 이 문서가 담는 마지막 산출물 커밋 `f20341b6`.
- 명령과 종료 코드는 `commands.md`, 되돌림은 `rollback.md`.
- 문서 산출물(P-1~P-4)은 세션 모델 레인 소관이라 이 문서에 넣지 않는다.

## 1. 게이트의 층 — 무엇이 어디서 닫히는가

| 층 | 자리 | 닫는 것 | 성질 |
|---|---|---|---|
| 컴파일 경계 | `tsconfig` `rootDir`·`include`(W-3) | rootDir 밖 `.ts`·`.js` 의 상대 경로·별칭 import → TS6059 | 구성 |
| 프로그램 전수 | W-4 (`ts.createProgram(...).getSourceFiles()`) | import·`files`·triple-slash·`src` 안 링크로 끌려온 **모든** 파일(선언 파일 포함)이 `src`∪`node_modules` 안인가 | 컴파일러 API |
| 경계의 전제 | W-4 (`lstat`·realpath·유효 옵션·AST) | `src` 자체가 링크인가 · `rootDir`·`include`·`allowJs`·`paths` 가 바뀌었나 · `web/` 안 링크 · 번들 경계 플러그인이 등재돼 있나 | 컴파일러 API + 파일시스템 |
| 호출 자리 집합 | W-4 (`ts.preProcessFile`) | `openapi-fetch` import 집합 == `{src/api/client.ts}` · 비리터럴 동적 import 0 | 집합 등식 |
| **번들 모듈 그래프** | Vite 플러그인 `buildEnd`(W-7) | 번들이 실제로 읽은 모듈 전수가 `src`∪`node_modules`∪`index.html` 안인가 | 그래프 단언 |
| 생성 타입 등식 | W-5 | 커밋본 ↔ OpenAPI 재생성본 바이트 등식 | 바이트 비교 |
| 보조(실수 잡기) | eslint(W-2) | 직접 네트워크 호출·HTTP 패키지 import·ts 지시어 억제·훅 규칙 | 린트 — **의도적 우회는 리뷰 소관** |

**왜 아래 두 층이 따로 필요한가**: 게이트를 전부 **TS 프로그램**에 걸면 번들의 입력인
**Vite/Rollup 모듈 그래프**는 비어 있는 채 남는다 — 두 그래프는 다른 집합이다(§3.2 N-4~N-7).
그리고 프로그램 검사의 기준점을 realpath 로 먼저 풀어 쓰면 그 검사가 항진명제가 된다(§3.1 F-1).

## 2. 수용 조건 대조

| 조건(scope.md) | 충족 근거 |
|---|---|
| P-5 「`web` job 명령 그대로 로컬 exit 0」 | W-1~W-9 를 순서대로 그대로 실행해 전부 exit 0(`commands.md`). CI 전용 step 둘(checkout·setup-node, `install-deps`)은 로컬에서 성립하지 않아 뺐고 그 둘은 게이트가 아니다 |
| P-5 「생성 타입 check 모드 음성 대조」 | §3.4 세 행 — OpenAPI 한 줄 변경 · 생성물 손 편집 · 생성물 삭제 전부 exit 1, 되돌린 뒤 exit 0. 실패 경로가 임시 디렉터리를 남기지 않는 것도 실측(0 → 0) |
| P-5 「legacy 경로·타입 import 0(게이트)」 | §3 의 **U-1~U-9 · N-1·N-2 · N-4~N-7 · N-9 · F-1·F-2 전부 RED**. 통제군 행(변이 없는 트리 · 되돌린 트리)이 같은 명령에서 exit 0 인 것과 짝으로 읽는다. **열린 자리 셋은 §4 에 등재**(CSS `@import` · 계산된 전역 이름 · 하드 링크) |
| P-5 「화면 구현 없음(빈 셸 + 라우트 자리 다섯)」 | 자리 여섯(화면 다섯 + 알림함)의 빈 셸 + 자리 표 **밖**의 없는 주소 자리. 질의·상태·디자인 토큰 0. 단위 test 가 경로 여섯·순서·id 중복 0·catch-all 이 표 밖임을 잠그고, e2e 가 알 수 없는 주소에서도 셸이 뜨는 것을 잰다 |
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

### 3.2 번들 모듈 그래프 (build 단계)

| # | 변이 | build | W-4 |
|---|---|---|---|
| — | 통제군 | 0 | 0 |
| N-4 | `import.meta.glob('…/bid-vector/frontend/src/shared/api/*.ts', { eager: true })` | **1** | 0 |
| N-5 | `…side.ts?worker` | **1** | 0 |
| N-5b | `…side.ts?raw` | **1** | 0 |
| N-6 | `resolve.alias` + `src` 안 `declare module` | **1** | 0 |
| N-7 | `index.html` 의 `<script type="module" src="../…">` | **1** | 0 |
| N-9 | `web/public/` 안 디렉터리 링크 → legacy | 0 | **1** |
| 등재 | 플러그인을 `plugins` 에서 뺌 / import 까지 뺌 / 파일 삭제 | — | **1 · 1 · 1** |

`build` 가 RED 인 행은 오류 메시지가 **문제의 파일 경로를 적는다**(모듈 id → realpath).
U-7(번들의 legacy **경로 문자열**): 깨끗한 번들에서 `bid-vector/frontend` 0 · `frontend/src` 0,
양성 대조로 같은 방식의 grep 이 번들 실재 문자열 둘을 잡는다. 낱말 `legacy` 만으로 세면 React 내부
심볼(`react.legacy_hidden` 등) 둘이 잡히므로 **경로 형태로** 센다.

### 3.3 호출 자리·전역 네트워크 표면 (보조 층)

| # | 변이 | lint | 잡은 규칙 |
|---|---|---|---|
| U-5a~e | 전역 `fetch` · `window.fetch` · `new XMLHttpRequest()` · `axios` import · `openapi-fetch` 정적 import | 1 | globals / properties+syntax / globals / imports / imports |
| N-13~N-17 | `self.XMLHttpRequest` · `navigator.sendBeacon` · `EventSource` · `WebSocket` · `document.defaultView?.fetch` · 지역 별명 `w.fetch` | 1 | 소유자를 열거하지 않는 **속성 이름** 술어(`no-restricted-syntax`) |
| 통제군 | 같은 `fetch` 호출을 `src/api/client.ts` **안**에서 | 0 | 설계대로 — 허용 자리가 실제로 하나임을 잰다 |
| 훅 | 조건부 훅 호출 | 1 | `react-hooks/rules-of-hooks` |
| N-10 | `globalThis['fe'+'tch'](…)`(계산된 이름) | **0** | **열림 — §4** |
| N-11 | `Reflect.get(window, 'fetch')` | **0** | **열림 — §4** |

### 3.4 생성 타입 등식

| # | 변이 | api:types:check |
|---|---|---|
| U-6a | 생성물 손 편집 | 1 |
| U-6b | OpenAPI 한 줄 변경 후 재생성 안 함 | 1 (바이트 30879 ≠ 30894) |
| U-6c | 생성물 삭제 | 1 (사유가 다른 메시지) |
| — | 되돌린 뒤 · 같은 명령 연속 2회 | 0 · 0 — 두 번 새로 생성한 산출물이 서로·커밋본과 바이트 동일 |

## 4. 알려진 제한 (열린 자리와 그 조건)

- **스타일은 데이터 모양이 아니다.** `src/index.css` 의 `@import` 로 legacy CSS 를 끌어오면 번들에
  들어간다(실측: build 0 · W-4 0 · dist 에 실재). 번들 그래프 플러그인은 CSS `@import` 를 모듈 id 로
  보지 않는다. ADR 0011 D-4 「복사하지 않는다」의 리뷰 대상으로 둔다 — 정적 자산 쪽(`web/public/`)은
  링크 검사가 막는다(N-9).
- **전역 네트워크 표면의 의도적 우회.** 계산으로 만든 이름(`globalThis['fe'+'tch']`)·
  `Reflect.get(window,'fetch')` 는 보조 층을 지난다(실측). 이 층의 주장은 「실수로 쓴 직접 호출을
  잡는다」까지이고 그 문면이 eslint 설정 머리 주석에 그대로 있다.
- **플러그인·게이트 스크립트 자체의 수정.** W-4 는 번들 경계 플러그인의 **등재**를 AST 로 보고(세
  형태 전부 RED 실측), 플러그인의 **판정 술어**는 `scripts/module-graph-boundary.test.mjs`(W-6)가
  잠근다. 본문을 비워 통과시키려면 두 층을 함께 바꿔야 하고 그 변경은 diff 에 남는다 — 저장소에
  CODEOWNERS 는 없다.
- **하드 링크**로 legacy 파일을 `src` 에 들이면 커밋 시점에 일반 파일이 된다 — 위협 모델이 밖으로
  둔 「복사」와 같은 부류다(구조로 막지 않는다, 리뷰 소관).
- **tsconfig `types`/`typeRoots`** 로 끌어오는 선언도 이제 프로그램 전수 검사의 대상이다(U-9·N-1 과
  같은 술어). `types` 가 가리킬 수 있는 `node_modules` 안 패키지는 허용 뿌리 안이고, 그 출처는 잠금
  파일 소관이다.
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
