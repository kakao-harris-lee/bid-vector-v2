# M6 종결 판정 — 실행 기록 (2026-11-02)

출력 전문은 싣지 않는다(핵심 결과 한 줄). 판정은 `checklist.md` 가 정본이다.

## A0. 조건 1 — acceptance 정본은 판정 base 의 CI run 이다

하네스가 「acceptance 는 CI 워크플로 job 이 돌리는 명령 그대로」로 정하므로, 조건 1 의 자격 있는 기록은
**판정 base `4d5d3d73` 의 main CI run** 이다 — 필수 `check` job 의 step 「one-command-check.sh — 완료 조건 1
(S-20)」이 **같은 스크립트 전건**(W-13 포함)을 돌고 **success** 다. 그 run 은 `check`·`web`·`container`·
`ml-engine` 네 job 이 모두 success 다.

아래 A 의 레인 실행은 **보강 실측**이다 — 단계 계수·test 계수·flake 관찰을 얻으려고 돌렸고 acceptance 의
자리를 대신하지 않는다. 「관련 게이트만 돌려 본 것은 돌려 보지 않은 것과 같다」는 규율이 B 의 표적 재실행에도
걸리므로, B 는 **판정 근거가 아니라 현상 관찰**이다.

## A. 보강 실측 — one-command 전건 (버릴 clone)

- 자리: `docs/m6-close` 를 **clone 한 버릴 트리**. 공유 worktree 에서 돌리지 않았다.
- cmd: 빌드 자리 래퍼(`bv-build.sh`)를 통해 `./tools/one-command-check.sh`
- 호스트 점검(별도 호출로 먼저, 결과를 보고 시작): 다른 Gradle 빌드 0 · available 14.5GB · swap free 7.0GB ·
  RSS 상위에 다른 세션의 큰 프로세스 없음.
- 단계: 선언된 작업 단계 25 가 전부 **순서대로 돌았다**(Kotlin 2 · Python 10 · web 13). 스크립트의 26 번째
  `_step` 은 작업이 아니라 **완료 표지**이고, 아래 W-13 에서 멈춰 찍히지 않았다.

| 단계 | 결과 |
| --- | --- |
| Kotlin — `check` | **BUILD SUCCESSFUL in 29m 39s** |
| Kotlin — `qualityBaseline` | BUILD SUCCESSFUL |
| Kotlin test 계수(**결과 XML 집계**, 콘솔 grep 아님) | `test` 태스크만: **tests 4,156 · 실패 0 · 오류 0 · 건너뜀 4**(XML 510). `app` 의 `compatibilitySmokeTest`(XML 1 · tests 9 · 실패 0)를 더하면 **XML 511 · tests 4,165**. 아래 숫자를 인용할 때는 **어느 범위인지** 함께 적는다 |
| 건너뜀 4 의 자리 | `bidvector.adapters.contract.RealServerIntegrationTest` 하나 — 실 서버 통합이라 태그로 기본 실행에서 분리돼 있다(6C 계약의 「경계로 처리」) |
| Python — 열 단계(라벨은 `S-1`~`S-11` **중 열** — `S-1b` 가 있고 `S-8`·`S-10` 이 없다) | 전부 통과 |
| web — W-1~W-12(열두 단계) | 전부 통과(Playwright 스모크 포함) |
| **web — W-13 여정 e2e** | **1회차 실패 — 1 failed · 10 passed.** 전건은 이 단계에서 멈췄고(완료 표지 없음) **비-0** 으로 끝났다 |

**1회차 실패의 정체 — 재실행으로 가렸다(아래 B).** production 거동이 아니라 **e2e 하네스의 응답 포착
경합**이다. 실패 단언은 지우기 뒤 체크리스트 `pending` 에 그 항목이 있는지인데, 받은 배열이 다른 항목
하나뿐이었다. **그 spec 자신의 주석이 같은 증상을 이미 실측으로 적고 있다** — 홈을 거쳐 들어오면 배너와
화면이 같은 질의를 각각 걸어 응답이 둘이고 늦게 닿는 쪽이 기다림에 잡힌다는 것. 앞 라운드가 그 자리에
화면 상태 단언을 넣어 창을 좁혔으나 **창이 닫히지는 않았다** — 응답 기다림을 **거는 순간**에도 앞선 재진입
응답이 아직 날아오고 있을 수 있다. 포착이 질의 URL·메서드로만 거르기 때문이다.

**이것은 로컬 고부하에서만 나는 것이 아니다**(아래 B2). 초판이 「부하 높은 1회차에서만」이라고 적은 것은
표본 하나로 말한 것이고 **틀렸다**.

**종료 코드를 받을 자리를 두지 않았다(레인 실수)** — 전건을 `nohup` 으로 띄우며 래퍼의 종료 코드를 잡지
않아 **정확한 값이 없다**. 비-0 이라는 것은 `set -euo pipefail` · 마지막 단계에서 멈춤 · 완료 표지 부재로
안다. B 의 재실행은 종료 코드를 잡았다.

## B. W-13 표적 재실행 — 2회, 둘 다 통과 (현상 관찰, 판정 근거 아님)

- cmd: 같은 clone 의 `web` 에서 여정 e2e 만
- 1회: **11 passed**(38.4s) · 2회: **exit 0 · 11 passed**(39.7s)
- 뜻: **그 레인 실행에서는 매번 나지 않는다**. 「재현되지 않는다」로 읽으면 안 된다 — 아래 B2 가 CI 에서
  재현을 보인다.

## B2. 같은 자리의 CI 발화 — main push run 7 중 2

경합 수정 커밋 뒤 `main` push run 일곱 가운데 **둘이 같은 자리에서 같은 서명으로 붉었다.** 둘 다 실패 job 은
**필수 `check`** 이고 실패 step 은 **「one-command-check.sh — 완료 조건 1 (S-20)」 하나**다.

| head | 날짜 | 붉은 것 |
| --- | --- | --- |
| `957587c4` | 2026-10-31 | `onboarding.spec.ts` 여정 ① ✘ · **`journey.spec.ts` 여정 ③ 도 함께** ✘ → 2 failed · 9 passed |
| `783817a5` | 2026-11-02 | `onboarding.spec.ts` 여정 ① ✘ → 1 failed · 10 passed. **M7 종결 머지 커밋 자신**이고, 다음 커밋(판정 base)에서 다시 초록 |

- 받은 배열은 레인 1회차와 **같다**(그 항목이 빠지고 다른 항목 하나만).
- 같은 두 run 의 `web`·`container`·`ml-engine` job 은 success — 붉은 것은 `check` 안의 e2e 자리뿐이다.
- 재현: `gh run list --branch main` 으로 run 을 고르고
  `gh api repos/<owner>/<repo>/actions/jobs/<job id>/logs | command grep -aE '✘|Received array'`.
- 판정에 미치는 것: 조건 1 은 **base 의 CI 초록**(A0)으로 선다. 이 두 run 은 그 판정을 뒤집지 않고
  **§3 ⑤ 의 근거**가 된다 — 필수 job 을 간헐적으로 막는 현행 결함이다.

## C. 조건 2 — legacy 무의존 재실측

- 저장소에 legacy symlink 0(그 이름은 `.gitignore` 항목이라 작업 트리 밖 참조물이다).
- Kotlin·Gradle 소스의 그 문자열은 셋뿐이고 전부 **출처 주석·provenance 문자열**이다 — import 0.
- **Python 축도 쟀다**: `git grep -nE '^\s*(from|import) (app|bid_vector)\b' -- '*.py'` → **0**.
  `ml-engine` 모듈에는 legacy 출처를 적은 provenance docstring 이 있으나 그것은 문자열이고 import 가 아니다.

## D. clean-tree (양성 대조 포함)

- cmd: `git status --porcelain -- reports/evidence/m6/closure milestone-6.md` → 커밋 뒤 **빈 출력**
- 양성 대조: in_scope 파일 하나에 줄을 심어 `M` 으로 **잡히는 것**을 확인하고 **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌렸다 — `checkout --` 는 쓰지 않는다(미커밋 편집을 함께 지운다).

## E. 비밀값 스캔

- cmd: 참조형(`-f config/quality/leak-patterns.txt`)으로 `reports/evidence/m6/closure` 와 `milestone-6.md` 를
  훑었다.
- 핵심 결과: **closure 디렉터리의 파일 넷 전부 0 건**이고, `milestone-6.md` 는 **5 건**이다. 그 5 는 **판정
  base `4d5d3d73` 에도 있던 줄**이고(base 에서도 5) 이 slice 가 더한 종결 문단은 **0** 이다 — 즉
  **새로 더한 줄 0 · 기존 5 불변**이다. 상시 게이트(`leakPatternGate`)의 스캔 대상은 `reports/evidence/`
  뿐이라 `milestone-6.md` 는 애초에 그 게이트에 들어가지 않는다.
- 작성 중 한 건이 잡혀(완료 조건 8 의 조건 이름을 축어로 적은 자리) **문면을 바꿔 없앴다** — 조건 이름
  자체가 스캔 어휘와 겹친다.
