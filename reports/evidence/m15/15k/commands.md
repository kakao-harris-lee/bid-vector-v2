# M15 / 15K — commands

base `ebcd3e493a197d13a1014fca65e668529fd7bcb9` · 브랜치 `fix/m15k-namesource-shown`

## 로컬 실측

| 명령 | 결과 |
|---|---|
| `cd web && npx tsc --noEmit --strict --target es2022 --module esnext --moduleResolution bundler --skipLibCheck --lib es2022,dom,dom.iterable e2e/journey/settings.spec.ts` — base | 오류 5: `touchMeasure.ts` TS2345 둘(`shown` 누락) + `shared.ts` 의 `node:` 모듈 TS2307 셋 |
| 같은 명령 — 고친 뒤 | 오류 3: `node:` 모듈 셋만(`@types/node` 가 web 에 없다 — 환경, 범위 밖) · `touchMeasure.ts` 0 |
| `cd web && npx eslint e2e/journey/touchMeasure.ts --max-warnings 0` | exit 0 |

## acceptance(정본 = PR head 의 CI)

CI `web` job 전건(실물 여정 포함)과 나머지 네 job — verifier 판정과 PR 조치 코멘트가 싣는다.
