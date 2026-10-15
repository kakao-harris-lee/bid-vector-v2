# ADR 0011 — 웹 프런트엔드 스택과 V2 계약 단일 출처 (`web/`)

- **상태**: **승인 — 운영자 결정 R-1(a) 2026-10-03**(`milestone-7.md` 「운영자 결정」). 이 ADR 은 그 결정을 등재하고 7A 가
  구현할 경계를 고정한다. 세션 모델이 단독으로 썼다(CLAUDE.md 운영자 지시 2026-09-04).
- **작성일**: 2026-10-14 (M7/7A)
- **대응**: `ADR 0008` D-4 물음 3(「legacy 프런트 재사용 어댑터가 V2 도메인을 왜곡하지 않는가」 — 게이트) · `milestone-7.md`
  7A 행 · `docs/discovery/m7-screen-data-requirements.md`(D-4 물음 1·2 의 답).
- **legacy 기준**: `bid-vector` symlink 의 `frontend/`(읽기 전용 참고).

## 1. 맥락

M6 까지 V2 는 운영자 API(OpenAPI 수작성 단일 출처 `openapi/bidvector-operator-api.yaml`)만 있고 화면이 없다. legacy 는 같은 계열
스택의 React 대시보드를 가졌으나 2026-10-03 중지됐다. R-1(a)는 **같은 스택을 새 프로젝트로** 세우라고 결정했다 — 스택 학습
비용과 컴포넌트 관행은 이어받되, 화면이 legacy 의 타입·경로·데이터 모양을 끌고 오는 경로는 닫는다. 7A 의 요구표는 V2 계약이
화면 요구의 대부분을 아직 덮지 않음을 보였다(ⓑ 50 행) — 그래서 이 ADR 의 핵심은 「화면이 계약 밖에서 데이터 모양을 지어내지
못하게 하는 구조」다.

## 2. 결정

### D-1. 스택

`web/` 하나의 새 프로젝트: **React 19 · Vite · TypeScript(strict) · react-router · TanStack Query · Tailwind CSS · vitest ·
Playwright · eslint**. Node 는 `web/.nvmrc` 가 정본(작성 시점 호스트 24 LTS), 패키지 관리자는 **npm + `package-lock.json`**(CI 는
`npm ci` — 잠금 파일 밖 해석 금지). 정확한 판은 `package.json`·잠금 파일이 정본이고 이 ADR 은 판을 적지 않는다(낡는 좌표).

### D-2. 타입과 호출은 V2 OpenAPI 에서만 온다

- 화면의 API 타입은 `openapi/bidvector-operator-api.yaml` 에서 **생성**한다(`openapi-typescript`). 생성물은 커밋하고, CI 는
  **check 모드**로 다시 생성해 커밋본과 바이트가 다르면 RED 다 — 문서를 고치고 생성을 잊거나, 생성물을 손으로 고치는 두 경로가
  같은 자리에서 막힌다.
- 호출은 그 생성 타입으로 타입이 매겨지는 클라이언트(`openapi-fetch`) 하나로만 한다 — 경로 문자열·요청/응답 모양을 손으로
  적는 fetch 래퍼를 두지 않는다. 계약에 없는 operation 을 부르면 **컴파일되지 않는다**.
- 화면이 필요로 하는데 계약에 없는 값은 **계약을 먼저 고친다**(7B·7C 가 OpenAPI 에 operation 을 더한다). 목 서버로 화면을 먼저
  만들지 않는다(`milestone-7.md` 「권장 순서」 — 계약이 두 자리가 된다).

### D-3. 어댑터 금지 게이트 — 구조로 닫는다 (`ADR 0008` D-4 ③)

- **legacy 소스 유입 0 — 두 그래프를 각각 닫는다**(7A 검토 r1 정정). 화면 코드가 닿는 그래프는 둘이고 게이트도 둘이다:
  ① **TS 프로그램** — `rootDir: src` 는 출발점일 뿐이다(TS6059 는 선언 파일·심볼릭 링크 경로에 걸리지 않는다 — 실측). 그래서
  `web` job 의 src-tree 게이트가 `ts.createProgram(...).getSourceFiles()` **전수**의 realpath 가 `web/src` ∪ `web/node_modules`
  안임과 `web/src` 자체가 링크가 아님을 단언한다. ② **Vite/Rollup 모듈 그래프** — `import.meta.glob`·`?raw`·`?worker`·
  `resolve.alias`·HTML `<script>` 는 tsc 를 거치지 않으므로, 빌드 플러그인이 `buildEnd` 에서 모듈 id 전수의 realpath 가
  `web/src` ∪ `web/node_modules` ∪ `web/index.html` 안임을 단언한다. **워커는 별도 Rollup 빌드**라 같은 플러그인을 `worker.plugins`
  에도 둔다(7A 검토 r2 실측 — 메인 그래프만 보면 워커 관용구 두 줄로 legacy 가 워커 chunk 에 실린다). 플러그인이 **실제로 활성**인지는
  설정 속 이름의 출현이 아니라 **카나리 빌드**가 잰다 — 실제 설정으로 밖 파일을 끌어들이는 probe(메인·워커, 이름·자리는 실행마다 무작위)를
  빌드해 **그 fixture 의 실경로를 적는** 경계 오류로 실패해야 통과한다. 카나리가 잡는 것은 **우연히 끈 형태**(조건식·덮어쓰기·중복 키·`apply`·본문
  비우기)이고, **카나리를 알아채는 설정 일반**(`process.argv` · 빌드 옵션 모양(`rollupOptions.input`·`outDir`) · 탐침 이름 패턴 ·
  같은 이름의 가짜 플러그인이 탐침 경로를 그대로 적는 형태)은 구조로 닫히지 않아 리뷰 소관 알려진 제한이다(7A 검토 r3·r4).
  fixture 실경로 조건이 주장하는 것은 「경계가 **다른 사유로** 낸 오류를 통과로 읽지 않는다」까지다. ③ **`node_modules` 의 출처** — `node_modules` 는 허용 뿌리이므로 그 안이 레지스트리 패키지뿐임을 잠금 파일에서
  단언한다(`resolved` 전수가 **레지스트리 origin** `https://registry.npmjs.org/` · `file:`·`link:`·git 0 · 등록부가 있고 비지
  않음 · 클라이언트 패키지의 **설치 경로 집합이 하나**(npm 별칭 의존 0)). 이름 목록(eslint 금지 경로)은 1차 게이트로 쓰지 않는다.
- **클라이언트 패키지의 사용처는 한 자리다**: D-2 의 생성 타입 + 타입 클라이언트가 **정해진** 호출 경로다. 게이트가 증명하는 것은
  「클라이언트 패키지(정체 기준)를 끌어오는 자리 == {`src/api/client.ts`}」까지이고, **「서버를 부르는 경로가 그것 하나뿐」은 증명하지
  않는다** — 그 주장은 아래 보조 lint 와 리뷰 소관이다(7A 검토 r5 계열 진단: r2~r5 에 다시 열린 사례의 능력은 전부 이미 등재된 손 HTTP
  하나였고, legacy 파일 유입 경계는 r2 뒤 다시 열리지 않았다). 그 등식은 **두 층**으로 선다(7A 검토 r4) — ① **TS 프로그램 층**(src-tree 게이트): 프로그램 파일 안에서 클라이언트 패키지로 **해석되는** import(지정자 문자열이
  아니라 해석된 모듈이 속한 **패키지 이름** 기준) 집합 == {`src/api/client.ts`}, 비리터럴 동적 `import()` 는 `src` 에서 0, `src` 안에
  TS 프로그램이 보지 못하는 스크립트(`.js` 류) 0 ② **번들 그래프 층**(메인·워커): 그 패키지를 패키지 밖에서 끌어온 간선 전수
  (**정적 `importers` ∪ 동적 `dynamicImporters`**)가 그 한 자리인가. 두 층이 필요한 이유는 서로의 사각이 다르기 때문이다 — TS 층은
  프로그램 밖 파일(`src/*.js` · `index.html` 인라인)을 보지 못하고, 번들 층은 아무도 쓰지 않는 클라이언트에 대해 말할 것이 없다. legacy 의 응답 모양을 흉내 내려면 계약 문서를 바꿔야 하고 그 변경은 계약 test 가 V2 구현과 대조한다.
- **브라우저 네트워크 전역은 보조 층이다**: `fetch`·`XMLHttpRequest`·`EventSource`·`WebSocket`·`sendBeacon` 직접 사용 금지 lint 는
  **실수로 쓴 직접 호출을 잡는 층**이고 의도적 우회(별칭 변수·문자열 조립·`Reflect`)를 닫지 않는다 — DOM 타입에서 그 전역을 뺄 수
  없어 구조로 닫을 층이 없다. 의도적 우회는 리뷰 소관이고 알려진 제한으로 등재한다(7A 검토 r1). 같은 부류로 등재하는 것(7A 검토
  r5): `index.html` 인라인 스크립트(lint·TS 대상 밖)의 직접 호출과 외부 URL `import` · 다른 HTTP 패키지를 더해 동적 `import()` 로
  쓰는 것(`no-restricted-imports` 는 `import()` 식을 보지 않는다) · 다른 패키지에 vendoring 된 클라이언트 사본. 구조로 더 줄일 싼
  후보(`index.html` 스크립트 집합 등식 · 의존 키 집합의 승인 목록 등식)는 「유일」을 증명하지 않으므로 7A 에서 하지 않고 7E 가 화면을
  올릴 때 다시 본다.
- 게이트는 `web` CI job 이 돈다 — typecheck · src-tree(프로그램 전수 · 링크 · 집합 등식 · 잠금 출처 · 플러그인 등재) · 생성 check ·
  번들 그래프(메인·워커) · 카나리(경계 활성). 층마다 **음성 대조**를 evidence 에 남긴다(어느 층이 무엇을 잡는지는
  `reports/evidence/m7/7a/checklist.md` 의 게이트 층 표가 정본).

### D-4. legacy 를 참고하는 범위

컴포넌트 구성·스타일 관행·e2e 시나리오(여정 단위)까지만 본다. **복사하지 않는다** — 참고한 자리는 커밋 메시지나 PR 에 「참고:
legacy 경로」로만 적는다. legacy 의 화면 분할(opportunities/projects 이원화 · 두 마법사)은 요구표 판정 ⓒ 로 버린다.

### D-5. CI 와 one-command

`web` job 하나가 `npm ci` → lint → typecheck → src-tree → 생성 check → vitest → build → 카나리 → 브라우저 설치 → Playwright
스모크를 순서대로 돈다(W-1~W-10, 이름과 명령의 정본은 `ci.yml`. 작성 시점 화면이 없으므로 스모크는 셸이 뜨는지 하나). `tools/one-command-check.sh`(M6 완료 조건 1)가 같은 명령을 포함한다 — 줄일 수 있는 단위는
job 이다(하네스 2026-09-12).

### D-6. 비밀값과 번들

번들에는 `VITE_` 접두 공개 설정만 들어간다. 인증은 7D(세션 쿠키 HttpOnly·SameSite + CSRF)가 정하고, 6A 의 단일 운영자 토큰은
브라우저에 싣지 않는다. 웹 의존 패키지의 취약점 스캔은 6E-2b(이미지 스캔) 범위 밖이다 — `OPEN-7A-WEB-DEPENDENCY-SCAN` 으로 등재하고
7F(운영 반입 이미지)에서 닫는다.

## 3. 대안

- **legacy 프런트를 옮겨 와 어댑터로 붙인다** — D-4 ③ 게이트가 금지한다(V2 API 를 legacy 모양으로 되돌림). 기각.
- **다른 스택(예: 서버 렌더링 프레임워크)** — R-1 이 (a)로 닫았다. 기각.
- **생성 타입 없이 손 타입** — 계약이 두 자리가 된다. 기각.
- **pnpm/yarn** — 이점이 작고 CI 에 도구가 하나 는다. npm 으로 시작하고 필요가 실측되면 이 ADR 을 개정한다.

## 4. 결과

- 화면이 V2 계약 밖의 데이터 모양을 쓰는 경로가 컴파일 단계에서 닫힌다.
- 대가: 계약에 없는 화면 요구는 7B·7C 가 계약을 먼저 고칠 때까지 화면이 그 자리를 비워 둔다(요구표 ⓑ 가 그 목록이다).

## 5. OPEN

- `OPEN-7A-WEB-DEPENDENCY-SCAN` — 웹 의존 패키지 취약점 스캔(7F).
