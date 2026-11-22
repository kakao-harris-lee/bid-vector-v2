# M15 / 15f — 실행 명령과 종료 코드

> **마지막 HEAD 의 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의 post-state 를
> 담을 수 없다(그 줄을 적으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다).
> 마지막 HEAD 의 게이트 결과는 **verifier 와 PR 조치 코멘트**가 든다.

**전건 acceptance 의 정본은 PR CI 다**(팀장 지시) — `check` · `web` · `container` · `ml-engine` ·
`mutation` 다섯. 로컬은 표적 test · 변이 · lint 만 돌린다.

## RED 가 먼저였다 — A-1

| 단계 | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| RED | `./gradlew --no-daemon --no-build-cache :app:test --tests '…EvaluationCommitRunnerTest'` | **1** | 새 test 「감싼 인터럽트도 플래그가 서 있으면 첫 건에서 멈춘다」가 **구판에서 실패**. 실패 문면이 결함 그 자체다 — `attempted` 가 `[1L]` 이 아니라 **`[1L, 2L, 3L]`** 이었다(감싼 예외에서 run 이 계속 돌았다) |
| GREEN | 같은 명령 + `EvaluationCommitRotationTest` 포함(`--tests 'bidvector.app.evaluation.*'`) | **0** | suite 6 · test 40(러너 17 · 회전 4 · 나머지 19) · 실패 0 |

## Kotlin 표적

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `./gradlew --no-daemon --no-build-cache :app:test --tests 'bidvector.app.evaluation.*'` | 0 | 위 GREEN 행 |
| `./gradlew --no-daemon --no-build-cache --continue :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck :app:detekt` | 0 | BUILD SUCCESSFUL — `runnerFor` 의 `@Suppress("LongParameterList")` 를 **지우고도** 초록이다(인자가 일곱에서 여섯으로 줄었다) |
| `./gradlew --no-daemon --no-build-cache :app:bootJar` | 0 | 여정 하네스가 쓸 출하 jar |

## web 표적

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `npm run typecheck` | 0 | |
| `npm run lint` | 0 | `--max-warnings 0` |
| `npm run build` | 0 | 여정 하네스의 preview 입력 |
| `npx vitest run` (설정 · 시작하기 · 표시층 셋) | 0 | 3 파일 · **133 test**(새 test 하나 포함) |
| `CI=1 npm run test:e2e:journey` **전건** | 0 | **33 passed** · 건너뜀 0(`check-journey-results.mjs` 가 판정) |

여정 전건을 돌린 이유: 새 `afterEach` teardown 은 **이 파일 밖의 여정**에도 영향을 줄 수 있는
자리다(공유 계정 하나를 차례로 쓴다). 표적 실행만으로는 그 축이 측정되지 않는다.

## 변이 — 전부 **바꿔치우기**다(더하기 아님)

변이마다 `git diff --numstat` 으로 **줄이 실제로 바뀐 것**을 먼저 확인하고, 파일 사본으로 심고
사본으로 되돌렸다(`git checkout --` 을 쓰지 않는다). 되돌린 뒤 `git status --porcelain` 빈 출력.

| # | 변이 | 적용 확인 | 결과 |
| --- | --- | --- | --- |
| **G1** | 여정 누출 술어의 **축어 검사**를 `if (false && …)` 로 | `1 1` | **RED** — 설정 여정 ④ 「축어 축이 예외 밖에서 붉지 않았다」. **앞 판에서는 이 변이가 생존했다**(r1 v F1 의 `2 passed`) |
| **EX** | 누출 술어의 면제를 **노드 전체**로 되돌린다(앞 판) | `2 6` | **RED** — 설정 여정 ⑤ 「예외가 그 칸의 속성까지 열었다」 |
| **TD** | `afterEach` teardown 이 **아무것도 쓰지 않게** 술어를 뒤집는다 | `1 1` | **RED** — `afterEach` 의 끝 상태 단언 「설정 여정이 사업자번호를 남겼다」. 이 쌍이 「test 가 실제로 저장한다」와 「teardown 이 실제로 되돌린다」를 **함께** 말한다 |
| **IM** | `inputMode="tel"` → `"numeric"`(앞 판 복귀) | `7 1` | **RED** — 설정 화면 test 1 failed / 36 passed. **앞 판에서는 생존**(r1 v F2) |
| **AL** | `aria-live="polite"` → `"off"` | `6 0` | **RED** — 같은 test |
| **HT** | 안내 함수가 `trim()` 을 그만둔다 | `5 2` | **RED** — 표시층 test 2 failed / 32 passed(새 단언과 기존 공백 단언이 **함께** 붉는다) |
| **통제** | 위 변이를 전부 되돌린 뒤 | — | GREEN — 설정 화면 37 passed · 여정 2 passed |

**G1·EX·TD 는 여정 하네스(출하 jar · 버릴 Postgres · fake KONEPS)에서 실제로 돌았다** — 각 실행이
서버에 값을 저장하고 다섯 화면을 쓸었다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15f/ reports/evidence/m15/15c/ reports/evidence/m15/15b/`
- exit: 1 (매치 없음 = 통과)
- 육안: **실 사업자의 값 0** · Telegram id · 운영 credential · 접속 문자열을 적지 않았다. 이
  slice 의 evidence 는 사업자번호를 **어느 꼴로도 담지 않는다**(15C 의 꼴 표가 그 자리를 든다 —
  그 slice 의 알려진 제한 10 이 전제로 삼는 사실이다).

## 낡는 좌표

이 slice 가 편집하는 파일에 `file:line` 을 쓰지 않았다 — 가리킬 때는 심볼 이름 · 절 제목 ·
인용문을 쓴다.

- cmd: `grep -rnE '(EvaluationCommitRunner|EvaluationCommitRunnerTest|EvaluationCommitRunnerFixtures|EvaluationCommitRotationTest|privateValues|settings\.spec|onboarding\.spec|profileDisplay|ProfileDetailsCard|SettingsScreen|m6-6e-operations|capability-map)[A-Za-z.]*:[0-9]+' --include='*.md' --include='*.kt' --include='*.ts' --include='*.tsx' --include='*.properties' .`
- 결과: 16 파일에 대한 좌표 **0**. 남은 하나 `docs/discovery/capability-map.md` 에는 **다른 slice 의
  evidence 가 건 좌표가 여럿 있다** — 그래서 두 축을 재서 밀리지 않음을 보였다.
  - **줄 수가 바뀌지 않았다**: `git diff --numstat` 이 `1 1`(한 줄 치환)이고 `git diff -U0` 의
    hunk 가 `@@ -4673 +4673 @@` **하나**다.
  - **위치도 아래다**: 밖에서 가리키는 좌표의 **최대값이 3457** 이라, 설령 줄 수가 바뀌었어도
    그 아래 좌표는 영향권 밖이다.
  - `docs/runbook/m6-6e-operations.md` 는 줄 수가 바뀌었지만(`18 6`) 그 파일을 좌표로 가리키는
    자리가 **0** 이다.

## clean-tree 게이트 (리뷰 요청 조건)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 출력 없음
- 양성 대조: in_scope 파일 하나에 한 줄을 더하면 그 경로가 `M` 으로 잡히고, **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌렸다(`checkout --` 을 쓰지 않는다 — 공유 트리다).

## 되돌리기

`rollback.md` 가 정본이다 — 목록 17(A 0 · M 17 · D 0) · 복원 exit 0 · 원천 대비 diff **0 줄** ·
내 표지 0 · 남의 표지 보존 · ④⑤⑥ 은 **트리 동일성**으로 갈음(되돌린 트리 ≡ `5a2f98ba7` ≡
`e2e61a57`, 그 SHA 의 CI 다섯 job success).
