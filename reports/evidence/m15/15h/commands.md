# M15/15H — 명령과 실측

acceptance 는 CI Kotlin `check` job 의 명령 그대로이고 `--continue` 를 붙여 돈다(알려진 빨강을 첫
하나에서 멈추지 않고 전수를 보기 위해서다). 모든 Gradle 실행은 `bv-build.sh` 자리 + `--no-daemon
--no-build-cache` 다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니라 verifier 리포트와 PR 조치 코멘트다** —
evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다.

## R — 착수 전 실측(RED)

| # | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| R-1 | `:app:test --tests 'bidvector.app.evaluation.EvaluationCommitRotationTest'`(보폭 `n-1` · 신설 test 포함) | **1** | 신설 `평일만 도는 timer 에서도 전원이 돈다` **만** FAILED — `Some elements were missing: [8, 9, 10, 11, 12, 13]`(나머지 넷 통과). `OPEN` 행이 센 「계정 여섯」과 같은 수·같은 집합이다 |
| R-2 | 회전식 모사(순수 산술, 빌드 밖) | 0 | 보폭 `n-1`: 평일 400 run 뒤에도 **시작 요일 일곱 전부**에서 같은 여섯이 미도달 · 보폭 1: 일곱 전부에서 **최대 15 평일 run** 안에 전원. 매 달력일 cron 의 한 바퀴는 `K=22·n=4` 7→19 · `K=10·n=4` 3→7 · `K=200·n=50` 5→151 달력일 |

## G — 구현 뒤 실측

| # | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| G-1 | `:app:test --tests 'bidvector.app.evaluation.*'`(보폭 1) | 0 | 클래스 여섯 · test 42 · 실패 0 |
| G-2 | 같은 명령(분할 뒤) | 0 | 클래스 **일곱** · test 44 · 실패 0(러너 5 · 회전 11 · 멈춤 9) |
| G-3 | `check --continue` | **1** | 실패 **하나** — `:app:unusedImportGate` 가 분할이 남긴 미사용 import 를 잡았다. 나머지 432 task 통과 |
| G-4 | `check --continue`(import 제거 뒤) | **0** | 424 task · BUILD SUCCESSFUL. acceptance 통과 |
| G-5 | `check --continue`(**main 병합 뒤** — PR #196/M15/15I 를 들였다) | **0** | 433 task · BUILD SUCCESSFUL (35m14s) |
| G-6 | `check --continue`(**수정 라운드 1** — 경계 문면 정정 뒤) | — | **정본은 PR 조치 코멘트다**(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다). 이 라운드가 바꾼 것은 주석뿐이고 그 사실을 C-1·C-2 가 바이트코드로 잰다 |

## M — 변이 실측

변이는 **더하기가 아니라 바꿔치우기**다(기존 식을 지우고 다른 식을 넣는다). 되돌림은 변이 전 파일
사본에서 복사하고 `git checkout --` 을 쓰지 않는다(미커밋 산출물을 함께 지운다). 변이 전
`git status --porcelain -- app config/quality` 가 빈 출력임을 확인하고 시작했다.

| # | 변이 | 판정 | 붉은 자리 |
|---|---|---|---|
| M-A | 멈춤 줄의 연속 수 자리에 **누적 `failed`** 를 싣는다 | **RED** | **신설 둘만** — `멈춤 줄의 consecutive 는 연속 수다` · `인터럽트로 멈춘 줄의 consecutive 도 연속 수다` |
| M-B | 보폭을 `n-1` 로 되돌린다 | **RED** | 평일 timer · 연속한 KST 달력일 · 회전 창 표 |
| M-C | 법을 `span-1` 로 | **RED** | 평일 timer · 회전 창 표 |
| M-D | 신설 test 클래스를 게이트 등록부에서 뺀다 | **RED** | `:app:gateRegistrationGate` 가 그 클래스 이름을 들어 거부(「등재하지 않으면 지워져도 `check` 가 초록이다」). 표적 실행은 이 게이트를 돌리지 않으므로 전건으로 쟀다 |

**M-A 의 통제군은 같은 run 안에 있다** — 멈춤 줄을 내는 **기존 일곱이 그 run 에서 초록**이다.
그래서 「방어가 없었다」와 「재지 않았다」가 갈리고, 그 초록이 `OPEN-M15-TEST-RESIDUE` ⓐ 가 말한
잔여의 실측이다(착수 시점 다섯 + 분할로 함께 옮긴 둘).

## C — 수정 라운드 1 의 거동 0 대조

경계 문면 정정(`K-n+1` → `K-n+g`)은 **주석만** 바꾼다. 「거동 0」은 해시가 아니라 **바이트코드**로
잰다(주석은 상수 풀에 들어가지 않으므로 해시 비교는 축이 아니다).

| # | 명령 | 결과 |
|---|---|---|
| C-1 | `javap -p -c` — `EvaluationCommitRunner` · `…Kt` · `evaluateOne$1` · `EvaluationProperties` · `EvaluationWiring` 다섯 클래스, 편집 전/후 | **다섯 전부 동일** — 명령 흐름이 한 바이트도 안 바뀌었다 |
| C-2 | **통제군** `javap -p -l`(같은 다섯) | **넷이 다르다**(줄 번호 이동) · `EvaluationWiring` 만 동일 — 그 클래스는 편집 지점(`EvaluationProperties` 의 속성 KDoc)보다 **앞에** 선언돼 줄이 밀리지 않는다. 통제군이 비지 않았으므로 C-1 의 동일성이 공허하지 않다 |
| C-3 | `K-n+g` 경계의 수치 재확인(순수 산술, 빌드 밖) | 간격 `g` 의 일정에서 최악 달력일이 `K-n+g` 이하임을 `(K,n,g)` 여덟 조합에서 확인 · **평일 일정**은 시작 요일 일곱 전수로 `K=22·n=4` **21 일**(화요일 시작) · `K=200·n=50` **153 일**(수요일 시작) — 둘 다 경계와 **같다**(딱 맞는 경계) |

## L — 비밀값 스캔

| # | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| L-1 | `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m15/15h/` | 0 | **evidence 디렉터리 매치 0.** 산출물 쪽 매치는 전부 **착수 전부터 있던 줄**이다(심은 가짜 메시지와 그 부재를 단언하는 두 줄 · 컨테이너 test 의 공개 test 자격). 개수 불변 실측: 평가 test 디렉터리의 해당 어휘 줄이 base 3 · head 3 이고 **분할로 자리만 옮겼다** |
| L-2 | 육안 — Telegram id·사업자 정보 | — | 이 slice 의 변경에 그 축의 값이 **없다**(계정 id 는 test 의 `1L`~`22L` 합성값) |
| L-3 | 같은 명령 — **main 병합 뒤의 확장된 패턴 파일로** 다시(15I 가 사업자등록번호 꼴을 더했다) | 0 | evidence 디렉터리 **매치 0** 그대로. 산출물 쪽도 새 꼴 매치 0 이고, 앞서 센 기존 매치만 남는다 |

루트 `leakPatternGate` 의 scanRoot 는 `reports/evidence` 이고, 그 아래 이 slice 의 신규 파일은
매치가 0 이라 baseline 등재가 필요 없다 — G-4 와 rollback ⑥ 이 그 게이트를 초록으로 지났다.

## 크기

`EvaluationCommitRunnerTest` 499 → **128**. 새 자리는 회전 314 · 멈춤 333 · 고정물 291 이고 전부
게이트 한도(500) 아래다.
