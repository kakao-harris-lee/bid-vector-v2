# M15 / 15J — commands

## 착수

- base: `65ea475e0af3965b85706746c101724763c75d94`(main, PR #197 머지) · 브랜치 `fix/m15j-scan-floor-distinct`

## 로컬 실측

| 명령 | 결과 |
|---|---|
| `cd web && npx eslint e2e/journey/settings.spec.ts --max-warnings 0` | exit 0 |
| `cd web && npm run typecheck` | exit 0 (include 가 `src` 라 e2e 는 대상 밖) |
| 판정식 사본 대조(변이 넷 — scope.md 「측정」) | 「네 벌」 구판 GREEN → 신판 RED, 나머지 셋 불변 |
| `command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15j/` | 0 건 |

## acceptance(정본 = PR head 의 CI)

CI `web` job 전건(scope.md 의 acceptance_commands — `ci.yml` 그대로)과 나머지 네 job. 결과는
verifier 판정 코멘트와 PR 조치 코멘트가 싣는다(evidence 에 넣으면 커밋을 또 낳는다).
