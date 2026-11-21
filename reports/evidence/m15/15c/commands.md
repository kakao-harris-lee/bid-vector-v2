# M15 / 15c — 실행 명령과 종료 코드

> **마지막 HEAD 의 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의 post-state 를
> 담을 수 없다(그 줄을 적으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다).
> 마지막 HEAD 의 게이트 결과는 **verifier 와 PR 조치 코멘트**가 든다.

## 번호 결번 때문에 전건을 **임시 연속 번호**로 쟀다

`migrationOrderGate` 는 번호 집합의 등식을 요구하고, V28·V29 는 15A·15B 의 브랜치에만 있다
(scope.md 「알려진 제한 6」). 그래서 전건 측정은 **번호만** 연속으로 바꾼 임시 트리에서 돌렸다.
바꾼 자리는 **둘**이고 둘 다 번호다 — 마이그레이션 파일 이름과 그 번호를 드는 래칫 한 줄.
**어느 것도 커밋하지 않았고**, 측정이 끝난 트리는 `git status --porcelain` 빈 출력으로 확인했다.

## 2026-11-21T07:16Z — 착수 전건(결번 그대로)

- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `migrationOrderGate` 가 결번 둘(V28·V29)을 보고 — 이 slice 가 단독으로 초록일 수 없음을
  확인한 측정이다(게이트가 옳다, scope.md 알려진 제한 6).

## 2026-11-21T07:49Z — 전건 1회차(임시 연속 번호)

- cmd: `./tools/one-command-check.sh`
- exit: 1
- 핵심 결과: 실패 셋 — 표별 CHECK 수(5→6 미갱신) · 잠금 상한 관례 lint(새 파일 미등재) ·
  거부 문면 test 의 씨앗이 **거부되지 않는 값**이었다. 셋 다 다음 커밋이 닫았다.

## 2026-11-21T08:07Z — 전건 2회차(임시 연속 번호, HEAD `e5914053`)

- cmd: `./tools/one-command-check.sh`
- exit: **0**
- 핵심 결과: Kotlin `check` BUILD SUCCESSFUL(35m32s) · `qualityBaseline` SUCCESSFUL ·
  Python pytest 1751 passed · web vitest 74 파일 2005 test passed · Playwright 스모크 39 passed ·
  `:app:bootJar` SUCCESSFUL · 여정 e2e 28 passed.

이 명령은 `.github/workflows/ci.yml` 의 세 job(`check`·`ml-engine`·`web`)이 돌리는 명령을 순서까지
그대로 든다(스크립트 머리말) — 그래서 scope.md 의 acceptance 목록을 한 번에 덮는다.

## 2026-11-21T10:05Z — 수정 라운드 1 (팀장 지시: 전건 대신 **건드린 모듈의 표적 test + web job**)

전건 `check` 와 되돌리기 ①~⑥ 은 **병합 라운드에 한 번** 돈다(B-1 처분 · `rollback.md` 머리말).

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `./gradlew --no-daemon --continue :adapters:test(SchemaGuard·JdbcStore·CleanMigrationCheck·MigrationLockTimeout·CleanMigrationColumn) :app:test(ProfileRequests·ProfileResponse·ProfileEndpoint) :workflow:test(BusinessProfileValues·OnboardingChecklist)` | 0 | suite 10 · test 88 · 실패 0 |
| CI `web` job 단계 전수 W-1~W-13 | 0 | vitest 74 파일 2010 test · 스모크 39 · **여정 29**(새 test 하나 포함) · `api:types:check` 바이트 등식 |

### 변이 — verifier W3 를 이 라운드가 잡는다

| 변이 | 결과 |
| --- | --- |
| `ProfileDetailsCard` 읽기 전용 줄에 저장된 번호를 `3-2-5` 로 그린다(W3 재현) | **RED** — 설정 화면 test 「저장된 사업자번호는 입력칸 밖 어디에도 그려지지 않는다」 |
| 같은 변이를 되돌린다 | GREEN(36/36) — 붉음이 심은 줄 때문임을 쌍이 말한다 |

변이는 파일 사본으로 심고 사본으로 되돌렸다(`git checkout --` 을 쓰지 않는다 — 같은 트리에
미커밋 산출물이 있었다). `git diff --numstat` 으로 **줄이 실제로 바뀐 것**을 먼저 확인했다.

### 라운드 중 고친 자기 결함

새 여정이 `내 입찰함` 을 `exact: true` 로 집다 120초 타임아웃으로 멈췄다. 그 자리의 접근성
이름은 안 읽음 수를 품어 **「내 입찰함 3」**이고 그 수는 앞 여정들이 남긴 상태다 — 탐색으로
좁혀 부분 일치로 집는 꼴(여정 ①·결과 여정의 선례)로 고쳤다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15c/`
- exit: 1 (매치 없음 = 통과)
- 육안: 사업자번호·Telegram id·운영 credential 을 evidence 에 적지 않았다. test 에 쓴 번호는 전부
  합성이다(실 사업자의 값 0).

## 낡는 좌표 — 역방향 파급

- cmd: `grep -rnE '(ProfileDetailsCard|SettingsScreen|OnboardingScreen|profileDisplay|ProfileRequests|ProfileResponses|BusinessProfileValues|OnboardingChecklist|bidvector-operator-api|approvedOperations|screen-vocabulary-registry)[a-z.]*:[0-9]+' --include='*.md' --include='*.kt' --include='*.ts' --include='*.tsx' --include='*.properties' --include='*.mjs' .`
- 결과 1건: M7 evidence 가 계약 파일의 **874-878** 줄을 가리킨다.
- cmd: `git diff 99f2fb25..HEAD -U0 -- openapi/bidvector-operator-api.yaml | grep -E '^@@'`
- 핵심 결과: 이 slice 의 편집은 전부 **2093 줄 이후**다 → 그 좌표는 밀리지 않는다.

## clean-tree 게이트 (리뷰 요청 조건)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 출력 없음
- 양성 대조: `web/e2e/journey/privateValues.ts` 에 한 줄을 더하면 그 경로가 `M` 으로 잡히고,
  **비파괴 절삭**(`head -n <원래 줄수>`)으로 되돌렸다(`checkout --` 을 쓰지 않는다).
