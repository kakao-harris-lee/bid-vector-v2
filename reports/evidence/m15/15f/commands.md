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
| `CI=1 npm run test:e2e:journey` **전건** | 0 | **33 passed** · 건너뜀 0(`check-journey-results.mjs` 가 판정). teardown 순서를 고친 뒤 **다시 돌려 같은 33 passed** 다 |

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

**TO(teardown 순서)는 변이가 아니라 구조로 닫았다 — 측정이 아니라 배치다.** 첫 판의 `afterEach` 는
「되돌림 A → 단언 A → 되돌림 B → 단언 B」였고, `expect` 가 그 자리에서 던지므로 **단언 A 가 깨지면
되돌림 B 가 통째로 건너뛴다** — 본문 ④ 를 teardown 으로 옮긴 바로 그 사유(CR r1 M-6)가 teardown
안에서 되살아나는 배치였다. 되돌림 둘과 읽기 둘을 **먼저 다 돌리고** 단언 넷을 모아 하는 것으로
그 의존을 **없앴다**. 「단언이 던진다」는 언어 사실이라 변이로 재는 대신 의존 자체를 지운 쪽이고,
그 뒤 여정 전건이 다시 33 passed 다.

**G1·EX·TD 는 여정 하네스(출하 jar · 버릴 Postgres · fake KONEPS)에서 실제로 돌았다** — 각 실행이
서버에 값을 저장하고 다섯 화면을 쓸었다.

## 더하지 않은 방어 하나 — 실측이 공허하다고 말했다

A-1 이 멈춤 판정을 **스레드 전역 상태**로 옮겼으므로 「앞 test 가 플래그를 남기면 아무 실패도
없는 run 이 첫 계정에서 멈추지 않는가」를 물었다. `@BeforeEach` 로 「플래그가 꺼진 채
들어온다」를 단언하는 가드를 **써 보고 실측한 뒤 지웠다**.

| 단계 | 결과 |
| --- | --- |
| 가드를 넣고 표적 실행 | 17 passed(가드가 test 수를 늘리지 않는다) |
| **누출을 심는다** — 어느 test 끝에 `Thread.currentThread().interrupt()` 한 줄(`numstat` 으로 적용 확인) | 그 test 는 실행 순서 **12/17** 이라 뒤에 다섯이 남는다 |
| 심은 채 표적 실행(`--rerun-tasks` 로 UP-TO-DATE 배제, XML mtime 으로 실행 확인) | **17 passed — 가드가 붉지 않았다** |

**누출이 다음 test 로 전파되지 않는다**(기작은 재지 않았다 — test 실행기가 test 사이에 플래그를
소비하는 것으로 보인다). 그러면 그 가드는 **붉을 수 있는 입력이 없는 단언**이고, 그런 줄은
방어가 아니라 방어의 외양이다. 그래서 넣지 않았다 — 이 표가 그 결정의 근거다.

한 test **안**의 누출은 다른 축이다: 인터럽트를 쓰는 test 둘 다 `Thread.interrupted()` 를
**첫 단언**으로 두어 읽으면서 지운다(run 이 던지지 않는 한 그 줄에 반드시 닿는다).

**이 표가 잰 것은 「단언하는」 가드다 — 「지우는」 가드는 다른 도구다**(M15/15G r1 cr L-7 등재).
`@BeforeEach { Thread.interrupted() }` 는 단언이 아니라 **공허할 수 없고**, 위 결론이 기대고 있는
「실행기가 플래그를 소비한다」는 미측정 거동에 대한 의존도 없앤다. 그것도 넣지 않은 이유는 위와
다르다 — **술어의 범위를 run 으로 좁히면 run 시작 직전에 온 진짜 종료 신호를 삼킨다**(15G 의
`stopCauseFor` KDoc 결정 · 15g scope 알려진 제한 1). 다음 레인이 이 물음을 다시 열 때 재는 것은
그 교환이지 가드의 공허함이 아니다.

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

## 크기 계수 — 무엇을 산출물로 세는가

이 slice 의 산출물 **대부분이 다른 slice 의 evidence 문면**이라 계수 기준을 적지 않으면 두 수가
어느 쪽으로든 읽힌다(M15/15G v F-4 등재). **기준: `reports/evidence/m15/15f/` 밖의 모든 변경을
산출물로 센다** — 15b·15c evidence 수정도 포함한다(그것이 이 slice 가 하기로 한 일이고, 코드
변경과 같은 리뷰 비용을 치른다). 머지 시점(판정 SHA `d71db061`)의 수로 그 기준은 산출물 455 ≥
evidence 403 이다. 「evidence 를 뺀 코드·문서만」으로 좁히면 329 < 403 이라 게이트가 거꾸로
선다 — 그 수를 숨기지 않고 함께 적는다. **이 절 아래의 뒷날 편집은 그 편집을 한 slice 의
산출물이고**(이 절 자체가 15G 의 산출물이다) 15F 의 비율을 다시 열지 않는다.

## clean-tree 게이트 (리뷰 요청 조건)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 출력 없음
- 양성 대조: in_scope 파일 하나에 한 줄을 더하면 그 경로가 `M` 으로 잡히고, **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌렸다(`checkout --` 을 쓰지 않는다 — 공유 트리다).

## 되돌리기

`rollback.md` 가 정본이다 — 목록 17(A 0 · M 17 · D 0) · 복원 exit 0 · 원천 대비 diff **0 줄** ·
내 표지 0 · 남의 표지 보존 · ④⑤⑥ 은 **트리 동일성**으로 갈음(되돌린 트리 ≡ `5a2f98ba7` ≡
`e2e61a57`, 그 SHA 의 CI 다섯 job success).
