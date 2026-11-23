# M15 / 15K — NameSource 의 `shown` 누락

```yaml
milestone: M15
slice: 15k
base_sha: ebcd3e493a197d13a1014fca65e668529fd7bcb9  # main, PR #198 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/e2e/journey/touchMeasure.ts
  - reports/evidence/m15/15k/
out_of_scope:
  - e2e 를 typecheck 에 넣는 구조 변경(tsconfig·CI) — 별도 결정. 지금 strict tsc 로 e2e 를 재면
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
빠뜨렸다. 값을 `false` 로 채운다 — 그 항목에는 그려지는 라벨이 없고, `boxOf` 가 쓰는 「모른다를
보인다로 접지 않는다」와 같은 쪽이다.

## 거동 0 근거

소비자는 `settings.spec.ts` 의 시평액 라벨 test 하나다(`command grep -rn shown web/e2e` 로 전수).
- 숨김 필터 `!one.shown` — 구판 `undefined`, 신판 `false` 둘 다 참. 게다가 그 항목은 `width 0` 이라
  `width < 8` 이 이미 참이다.
- 실패 문면 `one.shown ? '' : ' hidden'` — 둘 다 `' hidden'`.

## 하네스 레인 변경

없음.
