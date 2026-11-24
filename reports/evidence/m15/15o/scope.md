# M15 / 15O — 타입 검사를 끄는 지시를 두 프로그램에서 막는다

```yaml
milestone: M15
slice: 15o
base_sha: 71b1ad3a7aca2f1e6d6b2eba3384a9d1622ee3dc  # main, PR #201(15M) 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/scripts/e2e-typecheck-coverage.test.mjs   # (a)·(c)ⓘ — 억제 지시 금지를 두 프로그램 전수로
  - web/scripts/ban-ts-comment-reach.test.mjs     # 신설 — (b) 규칙이 미치는 범위를 측정으로
  - web/config/gate-tests.txt                     # 그 test 의 수집 등재(W-7)
  - web/scripts/check-src-tree.mjs                # (c)ⓙ — W-4 ④ 에 참조 두 축
  - docs/discovery/capability-map.md              # `OPEN-M15-SRC-TS-COMMENT-DISABLE` 닫힘
  - milestone-15.md                               # 같은 OPEN 의 요약 줄
  - reports/evidence/m15/15o/
out_of_scope:
  - web/eslint.config.js — **건드리지 않는다.** 저장소 규약은 `eslint-disable-next-line` 을 리뷰로
    가는 명시적 탈출구로 두고, 이 slice 는 그 규약을 **유지하는 쪽**을 골랐다. 그래서 금지를
    「그 규칙의 disable 금지」로 넓히지 않고, 타입 검사를 끄는 지시만 **eslint 밖 층**에서 막는다
  - web/tsconfig.json · web/tsconfig.e2e.json — 두 프로그램의 정의는 이 slice 의 **입력**이다((a) 의
    모집단이 그 둘에서 나오므로, 손대면 술어와 대상이 같은 손을 탄다)
  - web/package.json · package-lock.json — 새 의존 0. `typecheck` 명령도 무편집이다
  - .github/workflows/ci.yml · tools/one-command-check.sh — 이미 같은 명령을 부른다. 새 게이트 test 는
    `npm run test` 수집과 `npm run gate:tests` 등록부로 서므로 워크플로는 무편집이다
acceptance_commands:
  # CI 의 `web` job 전건 — `.github/workflows/ci.yml` 그대로(15L·15M scope 와 같은 열)
  - cd web && npm ci && npm run lint && npm run typecheck && npm run gate:src-tree
  - cd web && npm run api:types:check && npm run test && npm run gate:tests
  - cd web && npm run build && npm run gate:boundary-canary
  - cd web && npx playwright install chromium && npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar && cd web && npm run test:e2e:journey
rollback: reports/evidence/m15/15o/rollback.md
```

## 무엇을 닫는가

`OPEN-M15-SRC-TS-COMMENT-DISABLE`(15L r2 신설 · r3 에서 세 축으로 넓힘, ID 고정) — **세 축 전부**.

열려 있던 것은 「타입 검사를 조용히 끄는 자리 셋」이다. 15L 이 세운 억제 지시 금지의 대상은
`e2e/` 디스크 전수와 뿌리 playwright 설정이었고, 그 경계 밖에서 셋이 **네 층을 모두 지났다** —
착수 base 에서 (a)·(a′)·(c)ⓘ·(c)ⓙ 가 전부 **lint 0 · typecheck 0 · gate:src-tree 0 · 덮개 0** 이고,
(b) 를 재는 측정은 어느 레인에도 없었다(표는 `commands.md`). 그 표의 **대조 두 행**이 요점이다:
helper 는 지시를 빼면 typecheck 가 2 이므로 e2e 프로그램 **안**이고, `path` 참조도 빼면 typecheck 가
2 이므로 그 한 줄이 실제로 전역 표면을 **연다** — 붉음이 없던 것은 방어가 아니라 **미측정**이었다.

## 규약과의 관계 — 왜 `eslint-disable` 을 금지하지 않는가

15L r3 이 이 OPEN 을 등재하면서 조건을 달았다: 저장소 규약은 `eslint-disable-next-line` 을 **리뷰로
가는 명시적 탈출구**로 두므로, 「그 규칙의 disable 을 금지」하는 처방을 고르려면 그 규약과의 관계를
먼저 정해야 한다.

**정한다 — 규약은 그대로 둔다.** 금지의 대상은 eslint 규칙 일반이 아니라 **타입 검사를 끄는 지시
셋**이고, 그 금지는 **eslint 가 아닌 층**에 선다. 다른 규칙을 한 줄 끄는 길은 그대로 남고,
`ban-ts-comment` 를 끄는 것도 **여전히 가능하지만 얻는 것이 없다** — 그 지시가 거기 있으면 덮개
게이트가 exit 1 이고 그 게이트에는 eslint 주석이 닿지 않는다. 억제가 정말 필요한 날의 길은
「disable 한 줄」이 아니라 「게이트의 모집단·술어를 고치는 편집」이고, 그것은 diff 에서 게이트
파일로 보여 리뷰가 그 자리에 선다.

## 설계 검토 — 게이트형 slice

**(0) 위협 모델 경계.** 막는 것은 **저장소 안에서 코드를 쓰는 사람이 타입 검사를 조용히 끄는 것**이다
— 선의의 지름길과 리뷰가 놓치는 한 줄이 대상이고, 저장소 쓰기 권한을 쥔 적대자가 게이트 자신을
고치는 것은 대상이 아니다(그 편집은 diff 에 보이고 리뷰가 받는다). 「조용히」가 경계의 중심이다:
남는 길은 전부 **diff 에서 보이는** 길이어야 한다.

**(1) 열거인가 구성인가.** 셋 다 구성이다.
- (a)·(c)ⓘ 의 모집단은 디렉터리 열거가 아니라 **두 TS 프로그램의 파일 집합**이다 — 파일을 어디로
  옮기든 프로그램이 끌어들이는 한 모집단 안이다.
- (c)ⓙ 의 술어는 참조 이름 열거가 아니라 **경계**(`src` 밖을 가리키는 `referencedFiles` 0)와
  **전칭**(`libReferenceDirectives` 0)이다.
- (b) 만 **열거**다 — 대표 자리 열셋. 거기서는 구성이 불가능하다(「eslint 설정이 미치는 파일 전수」는
  디스크 전수가 아니면 셀 수 없고, 그 비용은 이 측정이 드는 값보다 크다). 그 열거의 비용은 「새
  확장자·새 뿌리가 목록 밖일 수 있다」이고, **그 축의 타입 억제는 (a) 의 구조 층이 그래도 닫는다** —
  (b) 는 lint 층의 **조용한 후퇴**를 재는 둘째 눈이지 유일한 방어가 아니다.

**(2) 이 게이트를 조용히 비우는 길.** 아래가 전부 실측이다(명령·수치는 `commands.md`).

| 길 | 받는 자리 | 판정 |
|---|---|---|
| ⓐ `src` 에 이름 단 disable + `@ts-expect-error` | 덮개 「억제 지시 0」 | **닫힘** — lint 0 · typecheck 0 인데 덮개 exit 1 |
| ⓑ 같은 꼴을 `@ts-nocheck` 로 | 같은 자리 | **닫힘** — 0 / 0 / 덮개 1 |
| ⓒ e2e 코드를 `e2e/` 밖으로 옮기고 거기 지시를 둔다 | 같은 자리(모집단이 **프로그램**이다) | **닫힘** — 0 / 0 / 덮개 1 |
| ⓓ 지시를 블록 주석·JSDoc 꼴로 숨긴다 | 렉서 그물 | **닫힘** — 오늘 컴파일러는 그 꼴을 무시하지만 그물은 센다(자리만 옮기면 먹는다) |
| ⓔ 모집단을 줄인다(차감을 넓히거나 한 프로그램을 뺀다) | 비공허 칸 셋 | **닫힘** — 화면 ≥1 · e2e ≥1 · 15L 이 들던 자리 래칫 |
| ⓕ `typecheck` 명령에서 프로젝트를 빼 모집단을 줄인다 | — | **닫힘** — 모집단은 명령이 아니라 **프로젝트 이름**에서 온다. 그 편집은 「명령의 꼴」 칸이 따로 받는다(한 편집이 두 칸을 함께 끄지 못한다) |
| ⓖ 그물을 더 좁히거나 이름 하나를 뺀다 | 양성 대조 여덟 꼴 | **닫힘** |
| ⓗ eslint 블록을 지우거나 glob 을 좁힌다 | 범위 test | **닫힘** — lint 0 인데 범위 test exit 1(= 15L r2 N-3 의 재현) |
| ⓘ eslint 옵션만 느슨하게(`allow-with-description`) | 범위 test | **닫힘** — lint 0 인데 칸 16 이 붉는다 |
| ⓙ 엄격 상수를 설정과 test 에서 **함께** 느슨하게 | 비공허 거동 칸 | **닫힘** — 설명 단 꼴이 실제로 RED 인지 거동으로 재므로 상수만 고치는 변이로는 초록이 되지 않는다 |
| ⓚ `src` 에 `/// <reference path=…>` 로 전역 표면을 연다 | W-4 ④ | **닫힘** — `gate:src-tree` exit 1 |
| ⓛ `src` 에 `/// <reference lib=…>` | 같은 자리 | **닫힘** — `gate:src-tree` exit 1 |
| ⓜ 게이트 test 가 수집에서 빠진다 | `gate-tests.txt` + W-7 | **닫힘** — 만드는 커밋에서 등재했다 |
| ⓝ 파일을 `node_modules` 안에 둔다(차감을 이용) | ④ 상한 · ⑤ 등식 · 추적 | **닫힘 — 설치본이다**. 추적되지 않으므로 「조용히」가 아니고, 화면 프로그램의 전수 상한이 따로 선다 |
| ⓞ 지시를 템플릿 문자열 안에 숨긴다 | 파서 그물 | **닫힘** — 문자열 리터럴은 주석 토큰이 아니다. 치환이 있는 템플릿은 반대로 **거짓 RED** 다(제한 ②) |

**(2b) 값 획득 축 — 새 public 표면 전수.** **0 이다.** 신설 파일은 vitest 가 수집하는 test 파일이고,
덮개 게이트에 더한 `commentLineBody`·`BANNED_PROJECTS`·`BANNED_POPULATION` 은 모두 모듈 지역이며,
`check-src-tree.mjs` 는 실행 스크립트다. 전수 확인: `in_scope` 의 `.mjs` 셋에 `export` **0 건**.
「경계로 처리」 행도 `object` 커널 주입 자리도 없다(TS/JS 레인이고 Kotlin 커널을 건드리지 않는다).

**(3) 과잉·미달.**
- **과잉 없음**: 금지의 대상은 타입 검사를 끄는 지시 셋뿐이고, 저장소 규약(`eslint-disable` 탈출구)과
  `eslint.config.js` 는 무편집이다. 오늘 트리의 실해는 **0** 이라 출하 코드가 한 줄도 붉지 않는다
  (거짓 RED 측정은 `commands.md`).
- **미달**은 아래 「알려진 제한」으로 등재한다. 핵심은 **(b) 가 열거라는 것**과 **프로그램 밖 파일은
  여전히 사각이라는 것**(15L 제한 ① 상속)이다.

## 알려진 제한

1. **두 TS 프로그램 밖은 이 층의 대상이 아니다**(15L 제한 ① 상속). `vite.config.ts`·`eslint.config.js`
   와 어느 프로그램도 끌어들이지 않는 `scripts/**/*.mjs` 는 모집단 밖이고, `checkJs` 가 꺼져 있어
   거기 지시를 두어도 **오늘은 끌 타입 검사 자체가 없다**.
2. **거짓 RED 가 둘 남는다 — 둘 다 닫힌 쪽 실패라 차단이 아니다.** 치환이 있는 템플릿 문자열 뒤의
   그 문구(15L 제한 ⑨ 그대로 — 문맥 없는 스캐너가 `}` 뒤를 주석으로 읽고, 줄머리 좁힘은 이 꼴을
   고치지 않는다)와 **지시 꼴로 줄머리를 시작하는 산문**. 산문은 그 낱말을 인용 부호 안에 두는 쪽이
   맞고 오늘 트리의 두 언급은 이미 그 꼴이다(치환 없는 템플릿·보통 문자열도 green).
4. **(b) 의 대표 자리는 열거다.** 새 확장자나 새 뿌리 이름이 목록 밖일 수 있다 — 설계 검토 (1) 대로
   타입 억제 축은 (a) 가 구조로 닫고, (b) 가 놓치는 것은 「lint 층의 조용한 후퇴」뿐이다.
5. **`referencedFiles` 는 금지가 아니라 경계다** — `src` 안을 가리키는 참조가 끌어오는 파일은 ④ 의
   전수 술어가 이미 받는다. 오늘 `src` 의 그 지시는 **0 건**이다.
6. **규칙 설정을 바깥에서 덮어쓰는 축은 이 측정 밖이다**(15L 제한 ⑧ 계열) — `eslint --rule` CLI
   덮어쓰기와 `web/.npmrc` 의 환경 주입은 `calculateConfigForFile` 이 보는 층이 아니다.
7. **비용**(15L 제한 ⑦ 연장). 덮개 칸이 아홉 → **스물**이지만 늘어난 열하나 가운데 열은 합성 원문
   하나를 파싱할 뿐 프로그램을 다시 세우지 않는다 — 5.4s → 5.6s. 새 범위 test 는 별도 파일 1.2s.

## 거동 변화

**출하 코드 0.** 바뀌는 것은 게이트 층과 장부뿐이고, `web/src`·`web/e2e` 의 원문과
`eslint.config.js`·두 `tsconfig` 는 무편집이다. 새 의존 0.

## 하네스 레인 변경

없음.
