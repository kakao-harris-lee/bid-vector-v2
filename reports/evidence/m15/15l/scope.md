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
  - web/scripts/e2e-typecheck-coverage.test.mjs  # 신설 — 층을 비우는 길 셋을 받는 게이트 test
  - web/config/gate-tests.txt                 # 그 test 의 수집 등재(W-7)
  - docs/discovery/capability-map.md          # `OPEN-M15-E2E-TYPECHECK` 닫힘
  - milestone-15.md                           # 같은 OPEN 의 요약 줄
  - reports/evidence/m15/15l/
out_of_scope:
  - web/tsconfig.json — **건드리지 않는다.** 그 파일의 `rootDir`·`include`·`allowJs`·`paths` 와
    `resolveJsonModule` 은 어댑터 금지 경계(ADR 0011 D-3) 자신의 전제이고 `scripts/check-src-tree.mjs`(W-4)가
    그것을 단언한다 — e2e 를 거기에 끼우면 경계가 사라진다
  - vite.config.ts · eslint.config.js · scripts/**/*.mjs — 여전히 어느 TS 프로그램에도 없다(알려진 제한 ①)
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
둔 채 비울 수 있다** — 아래 넷을 실측했고, ⓐⓑⓒ 는 `scripts/e2e-typecheck-coverage.test.mjs` 로 닫았다.

| 길 | 실측 | 처리 |
|---|---|---|
| ⓐ `typecheck` 에서 둘째 프로젝트가 빠진다 | tsc 는 한 마디도 하지 않는다(명령이 성공한다) | **닫힘** — 게이트 test 가 `package.json` 의 그 명령이 **실제로 지목하는** 프로젝트들을 열어 프로그램 전수를 얻는다. 지목이 줄면 덮이지 않는 파일이 열거되며 RED |
| ⓑ `include` 가 좁아진다 | 하나도 못 맞히면 TS18003 으로 **떨어진다**(실측 exit 2). **일부만** 맞히는 좁힘은 exit 0 이다 — 15K 가 산 자리가 그 꼴이다 | **닫힘** — 같은 test 가 디스크의 e2e `.ts` 전수 ⊆ 프로그램 전수를 단언한다 |
| ⓒ strict 축이 약해진다 | e2e 가 「검사받는다」는 말만 서고 기준이 달라진다. tsc 는 당연히 초록이다 | **닫힘** — 같은 test 가 두 프로젝트의 **유효 옵션**을 여섯 축에서 대조하고, 두 쪽이 함께 꺼지는 꼴을 막으려 값까지 못박는다 |
| ⓓ 전수를 `git ls-files` 로 세면 **아직 추적되지 않은** 새 spec 이 술어 밖이다 | 저장소가 이미 겪은 꼴이다(「게이트는 추적 전엔 눈이 없다」) | **닫힘** — 전수를 **디스크 훑기**로 센다 |
| ⓔ 게이트 test 자신이 수집에서 빠진다 | Playwright·vitest 둘 다 수집에서 빠진 파일을 말하지 않는다 | **닫힘** — `web/config/gate-tests.txt` 에 등재해 W-7 이 **실행 결과**로 단언한다(같은 커밋에서 등재) |
| ⓕ 전수가 0 이면 포함 단언이 무엇이든 통과한다 | 공허 | **닫힘** — 전수 > 0 · 프로젝트 수 > 0 을 먼저 단언하고, **음성 대조**(화면 프로그램 하나로는 그 전수를 못 덮는다)가 술어가 실제로 가르는 것을 잰다 |

**남는 것**: `vite.config.ts`·`eslint.config.js`·`scripts/**/*.mjs` 는 여전히 어느 TS 프로그램에도 없다
(알려진 제한 ①). 이 slice 의 술어는 「e2e 레인」이고 그 셋은 그 레인이 아니다.

## 거동 변화

출하 코드 0. 바뀌는 것은 게이트 층과 `web/e2e/kotlin-read-guard.spec.ts` 의 공허 가르기 한 자리이고,
그 자리는 **같은 조건에서 같은 실패**를 낸다(정본을 못 찾으면 던진다 — 앞 판은 `expect` 가 던졌다).

## 하네스 레인 변경

없음.
