# M15 / 15K — NameSource 의 `shown` 누락

```yaml
milestone: M15
slice: 15k
base_sha: ebcd3e493a197d13a1014fca65e668529fd7bcb9  # main, PR #198 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/e2e/journey/touchMeasure.ts
  - docs/discovery/capability-map.md          # 수정 라운드 1 (cr M-1 — OPEN 등재)
  - milestone-15.md                           # 수정 라운드 1 (cr M-1)
  - reports/evidence/m15/15k/
out_of_scope:
  - e2e 를 typecheck 에 넣는 구조 변경(tsconfig·CI) — `OPEN-M15-E2E-TYPECHECK` 로 등재. 지금 strict tsc 로 e2e 를 재면
    `@types/node` 부재로 `node:` 모듈 셋이 더 붉어 게이트를 바로 걸 수 없다
acceptance_commands:
  # CI 의 `web` job 전건 — `.github/workflows/ci.yml` 그대로(15J scope 와 같은 열)
  - cd web && npm ci && npm run lint && npm run typecheck && npm run gate:src-tree
  - cd web && npm run api:types:check && npm run test && npm run gate:tests
  - cd web && npm run build && npm run gate:boundary-canary
  - cd web && npx playwright install chromium && npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar && cd web && npm run test:e2e:journey
rollback: reports/evidence/m15/15k/rollback.md
```

## 무엇을 닫는가

PR #198 code-reviewer L-4 (사용자 지시 2026-11-23 「touchMeasure.ts 타입 누락도 고쳐줘」).
`nameSourcesInPage` 가 `aria`·`none` 항목을 밀어 넣을 때 `NameSource` 의 필수 칸 `shown` 을
빠뜨렸다. 값을 `false` 로 채운다 — 이 helper 는 그 가지에서 라벨 상자를 **재지 않고**(`aria-labelledby` 의
대상은 그려져 있을 수 있다), `boxOf` 가 쓰는 「모른다를 보인다로 접지 않는다」와 같은 쪽이다.

## 거동 0 근거

소비자는 `settings.spec.ts` 의 시평액 라벨 test 하나다(`command grep -rn shown web/e2e` 로 전수).
- 숨김 필터 `!one.shown` — 구판 `undefined`, 신판 `false` 둘 다 참. 게다가 그 항목은 `width 0` 이라
  `width < 8` 이 이미 참이다.
- 실패 문면 `one.shown ? '' : ' hidden'` — 둘 다 `' hidden'`.

## 하네스 레인 변경

없음.

## 수정 라운드 1

PR #199 code-reviewer 지적 처리(verifier 는 판정 SHA `69d1dad30` 에서 ready-for-review).
- L-1·L-2·L-3: 두 가지의 자리표시자 넷을 함수 안 상수 `NO_LABEL_BOX` 로 묶고, 근거 문장을 「라벨이
  없다」(aria-labelledby 에 거짓)에서 「이 helper 는 그 상자를 재지 않는다」로 고쳤다. 상수는
  `page.evaluate` 로 직렬화되는 함수 **안**에 둔다(밖이면 브라우저에서 이름이 풀리지 않는다).
- L-4: rollback 유효성 술어에서 괄호 속 열거를 걷었다(전칭식만).
- M-1: `OPEN-M15-E2E-TYPECHECK` 등재(capability-map 행 · milestone-15 OPEN 요약).
- verifier low 둘: commands.md 의 「환경」을 「저장소가 `@types/node` 를 선언하지 않는다」로 고쳤다 ·
  `head_sha` 자리표시자는 15I·15J 관례로 보류.
