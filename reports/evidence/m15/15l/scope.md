# M15 / 15L — e2e 를 타입 검사 아래로

```yaml
milestone: M15
slice: 15l
base_sha: a189e8c58c8a438e2685c8daae8f83b835b925ba  # main, PR #199(15K) 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/tsconfig.e2e.json                     # 신설 — e2e 레인의 TS 프로젝트
  - web/package.json                          # @types/node · typecheck 가 두 프로젝트를 돈다
  - web/package-lock.json
  - web/e2e/kotlin-read-guard.spec.ts         # strict 가 드러낸 실제 오류 하나
  - web/scripts/e2e-typecheck-coverage.test.mjs  # 신설 — e2e 진단·기준·덮는 자리를 재는 게이트 test
  - web/config/gate-tests.txt                 # 그 test 의 수집 등재(W-7)
  - web/eslint.config.js                      # 수정 라운드 1 — ban-ts-comment 를 e2e 레인으로
  - web/scripts/check-src-tree.mjs            # 수정 라운드 1 — W-4 ② 에 `types` 술어
  - docs/discovery/capability-map.md          # `OPEN-M15-E2E-TYPECHECK` 닫힘
  - milestone-15.md                           # 같은 OPEN 의 요약 줄
  - reports/evidence/m15/15l/
out_of_scope:
  - web/tsconfig.json — **건드리지 않는다.** 그 파일의 `rootDir`·`include`·`allowJs`·`paths` 와
    `resolveJsonModule` 은 어댑터 금지 경계(ADR 0011 D-3) 자신의 전제이고 `scripts/check-src-tree.mjs`(W-4)가
    그것을 단언한다 — e2e 를 거기에 끼우면 경계가 사라진다
  - vite.config.ts · scripts/**/*.mjs · eslint.config.js — 여전히 어느 TS 프로그램에도 없다(알려진 제한 ①)
  - .github/workflows/ci.yml · tools/one-command-check.sh — 둘 다 이미 `npm run typecheck` 를 부른다.
    명령이 그대로이므로 워크플로는 무편집이다
acceptance_commands:
  # CI 의 `web` job 전건 — `.github/workflows/ci.yml` 그대로(15J·15K scope 와 같은 열)
  - cd web && npm ci && npm run lint && npm run typecheck && npm run gate:src-tree
  - cd web && npm run api:types:check && npm run test && npm run gate:tests
  - cd web && npm run build && npm run gate:boundary-canary
  - cd web && npx playwright install chromium && npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar && cd web && npm run test:e2e:journey
rollback: reports/evidence/m15/15l/rollback.md
```

## 무엇을 닫는가

`OPEN-M15-E2E-TYPECHECK`(15K #199 에서 신설). `web/e2e/**` 와 뿌리의 playwright 설정이 **어느 TS
프로그램에도 들어 있지 않았다** — `tsconfig.json` 은 `include: ["src"]` 이고 web 에 다른 tsconfig 가
없었으며, Playwright 는 spec 을 타입 검사 없이 변환한다. 그 사각에서 `NameSource` 의 필수 칸 누락이
CI 다섯 job 초록 아래 살았다(15K 가 값을 고쳤지만 재발을 막는 것은 없었다).

닫는 방식은 **프로젝트를 하나 더 두고 `typecheck` 가 둘을 차례로 도는 것**이다. 착수 실측(strict 축을
`tsconfig.json` 과 같게 맞춘 프로젝트로 `e2e/**/*.ts` + `playwright*.config.ts` 를 열었을 때) 오류 24 를
갈래로 갈라 처리했다.

| 갈래 | 수 | 결정 |
|---|---|---|
| TS2307 `node:` 모듈 다섯 · TS2580 `process` | 14 | `@types/node` 를 devDependency 로 더하고 `types` 에 `node` 를 싣는다. 버전은 `web/.nvmrc` 의 Node major(24)에 맞추고 저장소 관례대로 **정확 버전**으로 고정한다 |
| TS6142 — e2e 가 `src/components/Button.tsx` 를 import | 2 | `jsx: "react-jsx"`(화면 프로젝트와 같은 값) |
| TS2339 `import.meta.env` — e2e 가 끌어온 `src/api/client.ts` | 1 | `types` 에 `vite/client`(화면 프로젝트와 같은 값) |
| TS7016 — 선언 없는 `scripts/*.mjs` import | 4 | `allowJs: true` · `checkJs: false` |
| TS7006 — 위에서 흘러나온 implicit any | 3 | 같은 처방의 파생. 따로 고치지 않는다 |
| TS2345 — `noUncheckedIndexedAccess` 아래 첨자가 `string \| undefined` | 1(위 처방 뒤 드러남) | e2e 코드의 **실제 오류**. 값으로 공허를 가르고 없으면 던지도록 고쳤다 |

### `allowJs` 인가 `.d.mts` 인가

**`allowJs: true` · `checkJs: false` 를 골랐다.**

- 손으로 쓴 `.d.mts` 는 **구현과 어긋나도 조용한 둘째 정본**이다. 어긋난 선언은 오류가 아니라 **거짓
  초록**을 낸다 — 이 slice 가 닫으려는 것과 같은 꼴의 사각을 새로 만든다. `allowJs` 는 타입을 구현
  자체에서 얻으므로 어긋날 자리가 없다.
- **strict 를 약하게 하지 않는다**: 받는 쪽은 그대로 strict 한 `.ts` 이고, 달라지는 것은 그 자리가
  `any`(TS7016)가 아니라 추론된 타입이라는 것뿐이다. 오히려 TS7006 셋이 그 `any` 의 파생이었다.
- **`scripts/**` 가 새로 검사받는 표면이 되지 않는다**: `checkJs: false` 라 그 파일들의 진단은 보고되지
  않는다. 켜지는 것은 `.ts` 쪽의 추론뿐이다.
- 화면 프로그램에는 영향이 없다 — `tsconfig.json` 의 `allowJs` 는 여전히 꺼져 있고 W-4 가 그것을
  단언한다. 두 프로젝트는 각자 `tsc -p` 로 돌고 서로의 진단을 덮지 않는다.

## 설계 검토 — 이 게이트를 조용히 비우는 길

새 층은 둘이다: `tsconfig.e2e.json` 과 「`typecheck` 가 그 프로젝트도 돈다」. **둘 다 tsc 를 초록으로
둔 채 비울 수 있다.**

**수정 라운드 1 이 이 절을 다시 썼다.** 앞 판의 술어는 **지목**(`-p` 뒤 이름)과 **축 여섯**에
걸려 있었고, verifier(H-1)·code-reviewer(M-1·M-2)가 그 술어를 지나가는 길 다섯을 실측했다 —
검사를 끄는 길은 **지목을 건드리지 않기 때문**이다. 그래서 재는 것을 바꿨다: 덮개 게이트가
e2e 프로그램을 **직접 세워**(`noCheck` 강제 off) **진단 0** 을 단언하고, 옵션은 축이 아니라
**유효 옵션 전체**를 면제 집합 밖에서 대조하며, 명령은 **꼴까지** 본다.

아래는 **수정 뒤의 실측**이다. 각 행은 「15K 가 고친 칸(`NameSource` 의 필수 칸)을 다시 뺀 상태」에서
그 변이를 더해 쟀다 — 즉 **잡아야 할 오류가 실제로 거기 있는** 판이다.

| 길 | typecheck | 덮개 게이트 | 판정 |
|---|---|---|---|
| (통제군) 변이 없음 — 심은 오류만 | 2 | 1 | 잡힘 |
| ⓐ `typecheck` 에서 둘째 프로젝트가 빠진다 | 0 | **1** | **닫힘** — 덮개가 e2e 전수의 미포함을 열거한다 |
| ⓐ′ 명령 끝에 `\|\| true` | 0 | **1** | **닫힘** — 명령의 꼴 술어(조각 ≠ `tsc [--noEmit] -p <p>`) |
| ⓐ″ 명령에 ` ; true`(띄운 꼴) | 0 | **1** | **닫힘** — 같은 술어 |
| ⓑ `include` 가 일부만 맞히도록 좁아진다 | 2 | **1** | **닫힘** — 전수 ⊆ 프로그램 |
| ⓑ′ `include` 가 하나도 못 맞힌다 | **2**(`TS18003`) | — | tsc 가 먼저 떨어진다 |
| ⓒ `strict` 아래 `strictNullChecks`·`noImplicitAny` 를 끈다 | 2 | **1** | **닫힘** — 유효 옵션 전체 등식(strict 계열은 `getStrictOptionValue` 로 푼다). 앞 판은 **선언값 여섯**만 봐서 초록이었다 |
| ⓒ′ `noUncheckedIndexedAccess` 를 끈다 | 2 | **1** | **닫힘** — 같은 등식 |
| ⓓ `tsconfig.e2e.json` 에 `"noCheck": true` 한 줄 | 0 | **1** | **닫힘** — 덮개가 `noCheck` 를 강제로 끈 채 진단을 잰다 |
| ⓓ′ 명령에 `--noCheck` | 0 | **1** | **닫힘** — 같은 자리 |
| ⓔ e2e 에 `// @ts-expect-error: <설명>` 한 줄 | **0** | 0 | **닫힘 — eslint exit 1**(`ban-ts-comment` 를 e2e 레인으로 넓혔다). 타입 층은 이 꼴을 **못 본다**(설계상 억제다) — 받는 층이 다르다 |
| ⓕ 전수를 `git ls-files` 로 센다 | — | — | **닫힘** — **디스크 훑기**로 센다(추적 전 신설 파일도 본다) |
| ⓖ 게이트 test 자신이 수집에서 빠진다 | — | — | **닫힘** — `web/config/gate-tests.txt` 등재로 W-7 이 **실행 결과**를 단언한다 |
| ⓗ 전수가 0 이면 포함 단언이 무엇이든 통과한다 | — | — | **닫힘** — 전수 > 0 · 프로젝트 > 0 · **음성 대조**(화면 프로그램 하나로는 못 덮는다) · **양성 대조**(메모리로 심은 오류를 같은 함수가 TS2322 로 보고한다) |
| ⓘ 화면 프로젝트의 `types` 에 `node` 를 더한다 | 0 | 0 | **닫힘 — `gate:src-tree` exit 1**. 이 전제는 15L 이 `@types/node` 를 들이면서 **비로소 하중을 받는다**(그 전엔 TS2688 이라는 물리적 장벽이 대신 지켰다) |
| ⓙ e2e 프로그램이 `web/` 밖으로 넓어진다 | 0 | **상한 칸이 받는다** | **닫힘** — 이 프로젝트에는 `rootDir` 이 없고 `allowJs` 가 켜져 있어 TS6059 가 날 자리가 없다 |

**거짓 RED 가 아닌 것도 쟀다**(닫힌 쪽 실패는 그 자체로 비용이다): `-p ./tsconfig.e2e.json` 과
`--project` 장형은 심은 오류가 없을 때 **typecheck 0 · 덮개 0** 이다(경로를 절대 경로로 풀어 비교).

**남는 것**: `vite.config.ts`·`scripts/**/*.mjs`·`eslint.config.js` 는 여전히 어느 TS 프로그램에도
없다(알려진 제한 ①). 이 slice 의 술어는 「e2e 레인」이고 그 셋은 그 레인이 아니다.

## 알려진 제한

1. `vite.config.ts`·`eslint.config.js`·`scripts/**/*.mjs` 는 어느 TS 프로그램에도 없다 — 타입 검사
   사각이 그만큼 남는다.
2. `checkJs: false` 라 `scripts/**`·`e2e/harness/**` 의 `.mjs` **자체의** 타입 오류는 보고되지 않는다
   — 그 파일들을 새 검사 표면으로 들이지 않으려는 의도한 경계다.
3. **그 `.mjs` 가 만드는 새 결합**(r1 cr L-4): 진단은 안 나오지만 그 파일들은 이제 프로그램 안에
   **들어오고 추론된 타입이 e2e `.ts` 로 흘러나간다**. 타입 규율이 없는 `.mjs` 의 모양이 바뀌면
   `npm run typecheck` 가 붉어질 수 있다 — 손으로 쓴 선언을 고르지 않은 이유의 뒷면이다.
4. **`moduleResolution: "bundler"` 로 Node 실행 코드를 모형한다**(r1 cr L-5): e2e 와 playwright 설정은
   Node 가 돌리는데 해석 규칙은 번들러다 — 확장자 없는 지정자가 타입 검사를 지나고 런타임에만 깨진다.
   `nodenext` 로 바꾸면 e2e 가 끌어오는 `src/**` 의 확장자 없는 import 가 전부 깨지므로 오늘의 선택을
   유지한다.
5. **`@types/node` × DOM lib 선언 충돌이 `skipLibCheck: true` 아래 가려져 있다**(r1 cr 열린 질문):
   `types: ["vite/client","node"]` + `lib: [… "DOM"]` 은 `fetch`·`Response`·`Headers`·`AbortController`
   에서 선언이 겹치고 어느 쪽이 이기는지 문면으로 서지 않는다. 오늘 e2e 가 그 전역을 쓰지 않아 실해가
   관측되지 않는다(typecheck exit 0). 그 날은 e2e 가 `fetch` 를 직접 쓰기 시작할 때다.
6. 덮개 게이트는 `typecheck` 명령이 `tsc [--noEmit] -p <project>` 조각들이라고 본다. `tsc -b` 같은 다른
   꼴로 바꾸면 **RED 로** 드러난다(조용히 통과하지 않는다) — 그때 이 술어를 함께 고쳐야 한다.

## 거동 변화

출하 코드 0. 바뀌는 것은 게이트 층과 `web/e2e/kotlin-read-guard.spec.ts` 의 공허 가르기 한 자리이고,
그 자리는 **같은 조건에서 같은 실패**를 낸다(정본을 못 찾으면 던진다 — 앞 판은 `expect` 가 던졌다).

## 하네스 레인 변경

없음.

## 수정 라운드 1

PR #200 verifier **not-ready**(H-1) · code-reviewer **COMMENT**(medium 4 · low 5) 처리.
판정 SHA `342b414e0`.

- **H-1 / cr M-1·M-2**: 덮개 게이트가 **지목과 축 여섯**이 아니라 **진단·유효 옵션 전체·명령의 꼴**을
  재도록 바꿨다. 위 「조용히 비우는 길」 표가 수정 뒤 실측이다.
- **cr M-3**: W-4 ② 에 `types == ["vite/client"]` 술어를 더했다. 그 전제는 이 slice 가 `@types/node` 를
  들이면서 비로소 하중을 받는다.
- **cr M-4 / v M-1**: eslint `ban-ts-comment` 세 형태 금지를 e2e 레인으로 넓혔다. 옵션은 상수 한 자리로
  두 블록이 나눠 싣는다(뒤 블록이 앞 블록의 같은 규칙 옵션을 덮는 꼴을 이 저장소가 이미 겪었다).
- **cr L-1 / v L-1**: `include` 와 디스크 훑기의 확장자 집합을 `.ts`·`.tsx`·`.mts`·`.cts` 로 맞췄다.
- **cr L-2**: `-p` 경로를 절대 경로로 풀어 비교한다(거짓 RED 제거, 실측 0/0).
- **cr L-3**: e2e 프로그램 전수의 **상한**(⊆ `web/`) 칸을 더했다.
- **cr L-4·L-5 · 열린 질문**: 위 「알려진 제한」 ③④⑤ 로 등재했다.
