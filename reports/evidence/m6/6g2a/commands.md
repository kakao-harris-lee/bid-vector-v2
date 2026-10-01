# M6/6G-2a — 실행 명령과 실측

base `30c6659e` · 레인 `ml-implementer` 하나 · 브랜치 `m6-6g2a/2026-10-01` · Kotlin 변경 0.

> **마지막 HEAD 의 게이트 결과는 이 문서가 정본이 아니다**(evidence-pack 규격) — evidence 를 고치는
> 커밋은 언제나 마지막 산출물 커밋 뒤에 오므로 자기 post-state 를 담을 수 없다. 마지막 HEAD 의
> acceptance 는 verifier 와 PR 조치 코멘트가 정본이고, 아래 표는 그 직전까지를 담는다.

## 착수 실측 (D-6G2a-7 「먼저 기준선」)

**순서를 지켰다** — test 를 한 줄도 고치기 전에 base 트리에서 쟀다. 측정은 저장소 밖 scratchpad 의
probe 로 돌렸고(산출물에 남기지 않는다), 같은 수를 뒤에 test 가 재현한다.

투영은 **셋**이다:

- **판정문 투영** — 판정 JSON 에서 `policy_checksum` · `policy_version` 을 빼고, 흔든 값의 메아리
  잎을 지운 나머지(D-6G2a-1 이 바꿀 그 투영).
- **판정 투영** — 전략별 `(이름, 결말, 통과 여부)`. 멈춤은 `("STOPPED", 사유)`.
- **메아리 포함 전체 바이트** — 6G 가 윗층에서 쓰던 투영(비교 대상).

| 항목 | 값 |
|---|---|
| base SHA | `30c6659e` |
| 정책 파일 키 수 | **39** |
| 로더 구조의 스칼라 필드 수 | **39**(둘의 등식은 기존 test 가 이미 잠근다) |
| 판정 경로에서 읽는 자리가 **0** 인 키 | **0**(AST 전수 — 아래 「읽는 자리 전수」) |
| checksum·version 을 뺀 투영에서 판정문을 움직이는 값 | **19** |
| 같은 투영에서 판정문이 **그대로인** 값 | **20** |
| 작은 흔들기로 **판정 투영**(결말)을 움직이는 값 | **8** |
| 흔든 정책이 로더에 거부된 값 | 0 |
| 흔들기가 값을 바꾸지 못한 값 | 0 |

19 과 20 은 6G verifier r5 H-2 의 수와 **일치한다** — 그 결함이 base 에 그대로 있음을 이 레인이
독립으로 재현했다.

판정문이 그대로인 20:
`effective.construction` · `effective.goods` · `effective.service` · `fit.alpha` ·
`fit.max_bin_ratio_deviation` · `fit.min_sample_count` · `floor.pure_construction_cost_ratio` ·
`floor.rate_band_low` · `sampling.calls_per_notice_construction` ·
`sampling.calls_per_notice_goods` · `sampling.max_total_calls` ·
`sensitivity.wide_reserve_half_width` · `stability_seeds.1` · `stability_seeds.2` ·
`stability_seeds.4` · `strategy.s1_offset_bp` · `strategy.s4_min_competitor_samples` ·
`verdict.ineligibility_noninferiority_margin` · `version` · `window.embargo_days`.

작은 흔들기로 결말까지 움직이는 8:
`exclusion.first_notice_ordinal` · `floor.rate_band_high` · `institution.draw_count` ·
`institution.reserve_price_count` · `strategy.s4_grid_size` · `verdict.min_window_count` ·
`verdict.min_window_rows` · `window.days`.

극단 흔들기(6G 의 `_DECISION_DRIVERS` 여섯)에서 판정 투영을 움직인 것은 셋
(`verdict.alpha` · `verdict.min_window_count` · `verdict.min_window_rows`)이고, 셋
(`verdict.primary_hypothesis_count` · `verdict.min_relative_improvement` ·
`verdict.ineligibility_noninferiority_margin`)은 얼어 있었다 — 6G 의 `_DECISION_FROZEN` 등재와
같은 상태다. 기준선 판정은 **전략 다섯 전부 `StrategyNotEvaluable`** 이어서 통과 갈래를 한 번도
지나지 않는다.

## 읽는 자리 전수 (D-6G2a-6 「착수 실측으로 전수한다」)

정책 값마다 **판정 경로에서 그 값을 읽는 자리**를 AST 로 셌다. 대상은
`ml_engine.evaluation.backtest` 의 모듈 전부와 `ml_engine.app.backtest_*` 다. 같은 잎 이름을 쓰는
두 값(`verdict.alpha` · `fit.alpha`)은 속성 체인의 그룹 이름과 둘러싼 클래스로 갈랐다.
불변식 검사(`__post_init__`)의 읽기는 따로 세고 아래 수에서 뺐다.

| 읽는 자리 수 | 값 수 | 값 |
|---|---|---|
| 3 | 3 | `institution.reserve_price_count` · `institution.draw_count` · `sampling.max_total_calls` |
| 2 | 5 | `verdict.primary_hypothesis_count` · `verdict.min_window_count` · `verdict.min_window_rows` · `floor.pure_construction_cost_ratio` · `sampling.list_call_count` |
| 1 | 31 | 나머지 |
| 0 | **0** | — |

「읽는 자리 0 인 값이 없다」가 부류 ⓒ(판독 밖)를 가르는 근거다 — 정책 값 전부가 이 Python 판정이
**읽는** 값이다.

두 자리 이상인 여덟 중 판정 입력 셋은 자리마다 따로 단언한다(D-6G2a-6):

| 값 | 자리 ① | 자리 ② |
|---|---|---|
| `verdict.min_window_count` | 창 계획 뒤의 멈춤 판정(`run`) | 「못 쟀다」의 창 부족 갈래(`verdict`) |
| `verdict.min_window_rows` | 최소 필요 표본 결정식(`run`) | 창 제외의 표본 하한(`windows`) |
| `verdict.primary_hypothesis_count` | Bonferroni 분모(`policy_values.VerdictThresholds.primary_alpha`) | job 조립의 주 가설 수 대조(`app.backtest_job`) |

나머지 다섯의 둘째·셋째 자리는 **판정문 공시**(`report` 의 표본 기록 재출력)이거나 **업무 축이
공사일 때만 도는 자리**다 — 앞은 메아리라 D-6G2a-1 의 투영이 지우고, 뒤는 공사 행이 있는 판으로
덮는다.

## 6G evidence 정정 선언 (D-6G2a-8)

6G 의 `commands.md` 는 고치지 않는다(닫힌 slice 의 evidence 를 되쓰지 않는다). 그 문서의 두 자리는
아래 전제 위의 측정으로 읽는다.

- **「N1′~N7′ 일곱 다 RED」** — 그 RED 는 **메아리를 포함한 전체 바이트 비교** 위에서 났다. 그
  비교에는 `policy_checksum`(정책 파일 전체의 sha256)이 들어 있어 **어느 값을 흔들어도 바뀐다**.
  따라서 그 행은 「변이가 판정을 바꿨다」가 아니라 「변이가 있는 트리에서 test 가 붉었다」까지만
  말한다. 변이가 **판정 경로를** 끊었는지는 checksum 을 뺀 투영에서 다시 재야 하고, 이 slice 가
  그렇게 다시 쟀다(아래 변이 열).
- **「39개 전부 민감」** — 같은 전제다. checksum·version 을 빼면 39 중 **20** 이 판정문을 움직이지
  않는다(위 착수 실측). 그 문장은 「39 개 값이 판정문의 어떤 바이트든 움직였다」로 읽어야 하고,
  「39 개 값이 판정에 쓰인다」로 읽을 수 없다.

두 정정은 **출하 코드의 결함이 아니다** — 출하 코드는 정책 값을 읽는다(위 「읽는 자리 전수」, 읽는
자리 0 인 값 0). 정정 대상은 게이트가 잰다고 주장한 **범위**다.

## 부류 분할 (D-6G2a-2·3)

세 부류의 합집합이 정책 키 집합과 **등식**이고 교집합은 공집합이다 — 이 등식을
`test_every_policy_value_is_classified_exactly_once` 가 잠근다.

| 부류 | 수 | 단언 |
|---|---|---|
| ⓐ 판정 입력 | **29** | 전략별 결말(**그 사유 포함**)이 바뀐다 |
| ⓑ 산출 입력 | **9** | 결말 불변 **그리고** 메아리를 뺀 판정문의 칸이 바뀐다 |
| ⓒ 판독 밖 | **1** | 아무것도 바뀌지 않는다(사유 등재) |
| 합 | **39** | 정책 파일 키 수와 등식 |

ⓐ 의 흔들기는 **36 개**다(판정식의 일곱은 양쪽 방향으로 둘씩 — 우회 ④ 「한쪽으로만
따른다」를 잡는다).

ⓒ 는 `version` 하나다. 「읽는 자리 0 인 값이 없다」(착수 AST 전수)가 그 근거다 —
`version` 은 읽히지만 판정식·산출식이 쓰지 않고 판정문의 공시 칸에만 실리며, 그 칸은
D-6G2a-1 이 비교 투영에서 뺀 메아리다. 그 칸이 산출물에 **남아 있다**는 것은 전용
test 가 따로 잠근다.

ⓑ 로 **옮긴** 값 셋은 처음에 ⓐ 로 등재했다가 실측이 내린 것이다: 공사 순공사원가 배제
비율 · S1 의 공사 전용 offset · 판별 표본 반폭. 셋 다 판정문은 움직이지만 그 판에서
결말까지는 바꾸지 못한다. 반대로 S4 의 몬테카를로 반복 수는 ⓑ 로 등재했다가 **ⓐ 로
올렸다** — 판정 투영이 「못 쟀다」의 **사유**를 담게 되자 seed 불안정에서 검정력 미달로
옮겨가는 것이 보였다.

## 판 (D-6G2a-4)

**열다섯**이다. 승패를 전략이 아니라 **판**이 정한다: 공고마다 적격 최저 투찰금액을
기초금액 대비 고정 비율로 두면 두 투찰률 중 하나는 이기고 하나는 지므로, 창마다
(후보만 승 · 기준선만 승 · 둘 다 승 · 둘 다 패 · 후보 실격) 분할표를 지정할 수 있다.

| 판 | 기준선 결말 | 무엇을 겨누는가 |
|---|---|---|
| `shipped` | 전략 다섯 전부 `NotEvaluable` | 출하 전략 판(용역) — 전략 모듈에서만 읽히는 값 |
| `mixed` | 결말 섞임 | 업무 셋 — 공사·물품에서만 도는 자리 |
| `pass` | **`StrategyPassed`**(보조) | 통과 갈래 · 하한·유의수준·검정력·창 최소 수를 올리는 방향 |
| `pass-primary` | **`StrategyPassed`**(주) | Bonferroni 분모를 올리는 방향 |
| `edge` | `NotEvaluable(UNDERPOWERED)` | 유의수준·검정력을 **내리는** 방향 |
| `edge-primary` | `NotEvaluable(UNDERPOWERED)` | Bonferroni 분모를 내리는 방향 |
| `gain-short` | `StrategyFailed` | 상대 개선 하한을 내리는 방향 |
| `margin-breach` · `margin-inside` | `Failed` · `Passed` | 비열등 한계 양쪽 |
| `two-windows` | `STOPPED(INSUFFICIENT_WINDOWS)` | 창 최소 수를 내리는 방향 |
| `window-rows` · `sample-floor` | `Passed` · `Passed` | 창당 하한의 **두 자리**를 가른다 |
| `seeded` | `Passed` | seed 다섯이 **전부** 전략에 닿는가 |
| `construction-pad` | `Passed` | 배제 비율의 **제외 단계 읽기 하나** |
| `wide-reserve` | `Passed` | 판별 표본 걸러내기의 반폭 |

**통과 갈래가 실제로 돈다**: `pass` 와 `pass-primary` 에서 후보의 승률이 기준선보다 높고
합동 판정이 통과한다(전용 test 가 단언한다). 6G 의 합성 판은 어떤 후보도 기준선을 이기지
못해 통과 조건의 다른 레그에서 먼저 떨어졌고, 그래서 판정 입력 셋이 「못 움직인다」로
등재됐다 — **게이트 술어의 문제가 아니라 판의 문제**였다. `_DECISION_FROZEN` 등재는
없앴다(D-6G2a-5). 끝내 판을 지을 수 없는 판정 입력은 **없다**.

### 판을 지을 수 없었던 방향 하나 (측정된 구조적 사실)

`verdict.alpha` 를 **올려** `Failed` -> `Passed` 로 뒤집는 판은 없다. 판정식이 상대 개선
하한을 만족하고 검정력이 서 있으면 p 값은 유의수준보다 **훨씬** 아래로 내려간다(검정력
0.8 로 Δ 를 검출하도록 설계된 판에서 실현 효과가 Δ 면 p 는 유의수준의 1/8 수준이다) —
그래서 「개선은 충분한데 p 만 모자란」 상태가 성립하지 않는다. 대신 유의수준은 **필요
표본 수**를 통해 반대 방향으로 닿는다: 올리면 필요 표본이 줄어 `NotEvaluable` 이
`Passed` 로 뒤집힌다(판 `edge`). 그 경로로 양쪽을 다 쟀다.

## 쓰이는 자리마다 (D-6G2a-6)

읽는 자리가 둘 이상인 값은 **여덟**이고, 그중 **여섯**을 자리마다 따로 단언한다. 둘은
둘째 자리가 판정문 공시(메아리)라 투영이 지운다.

| 값 | 자리 | 가르는 방법 |
|---|---|---|
| `verdict.min_window_count` | 2 | 전체 실행(멈춤) + **판정 함수 직접 호출**(창 부족 갈래) |
| `verdict.min_window_rows` | 2 | **한쪽만 묶이는 판 둘** — 창 밖 행 수로 가른다 |
| `verdict.primary_hypothesis_count` | 2 | 전체 실행(결말) + **job 조립 직접 호출**(주 가설 수 대조) |
| `institution.reserve_price_count` | 4 | 전체 실행(제외 ⑮) + 적합도 직접 호출(스칼라 **둘 다**) + S4 투찰금액 직접 비교 |
| `institution.draw_count` | 3 | 같은 셋 |
| `floor.pure_construction_cost_ratio` | 2 | **계획 전략 판**(제외 단계) + S4 투찰금액 직접 비교 |
| `sampling.list_call_count` | 2 | 둘째 자리가 메아리 — 등재 |
| `sampling.max_total_calls` | 3 | 둘째·셋째가 표본 기록과 메아리 — 등재 |

**자리를 가르는 일은 변이 실측이 강제했다.** 처음 구현은 배제 비율과 제도 상수 둘을
「두 자리가 같은 판에서 함께 돈다」는 사유로 자리별 단언에서 빼 놓았다. 그 사유가
**틀렸다**: 배제 비율을 제외 단계에서만 상수로 바꾼 변이(P4)가 초록이었다 — S4 가 같은
값을 따로 읽어 판정문이 움직였다. 같은 계열로 적합도 자리만 상수로 바꾼 변이(P9)도
초록이었다. 둘 다 자리를 가른 뒤 RED 다.

**가를 수 없는 자리 하나**: 적합도의 **구간 수**(변이 P9b 가 초록으로 남는다). 적합도가
공시하는 스칼라 둘이 기준 표본에도 구간 수에도 **동시에** 의존하고, 정책 값 하나가 둘을
함께 정해서 기준 표본을 고정한 채 구간 수만 흔드는 입력이 없다. 사유와 함께 등재하고
`OPEN-6G2A-FIT-BIN-COUNT-SITE` 로 올린다.

## 변이 열 (D-6G2a-7)

**절차는 계약 순서 그대로**다: ① 변이 없는 기준선이 초록임을 먼저 확인(87 passed, 자리별
단언을 더한 뒤 90 passed) → ② 변이 적용 + `git diff --numstat` 로 적용 확인(비면 측정하지
않는다) → ③ **checksum·version 을 뺀 투영에서** RED 측정. 문법·이름 오류를 변이와 가르는
선검사(판정 경로 모듈 import)를 ② 와 ③ 사이에 둔다 — 6G r5 가 가짜 RED 를 실측한 자리다.
변이 뒤에는 원문 바이트를 되쓰고 `git status --porcelain -- ml-engine/src` 가 비었음을
매번 확인한다(저장소에 변이가 남지 않는다).

| 변이 | 무엇을 박는가 | 결과 |
|---|---|---|
| **N1** | 판정 유의수준을 허용 목록 산술 상수로 | **RED** |
| **N4** | 판정 유의수준을 f-string 으로 | **RED** |
| **N2** | 판정 seed 를 `len` 으로 | **RED** |
| **N3** | 판정 seed 를 `int.from_bytes` 로 | **RED** |
| **N5** | 판정 seed 를 `math` 로 | **RED** |
| **N6** | 판정 seed 를 `enum` 멤버 값으로 | **RED** |
| **N7** | 판정 seed 를 생성식 합으로 | **RED** |
| **E1** | Bonferroni 분모를 코드 상수로 | **RED** |
| **E2** | 상대 개선 하한을 코드 상수로 | **RED** |
| **E4** | 검정력 목표를 코드 상수로 | **RED** |
| **P1** | 창 최소 수를 코드 상수로(자리 ① 만) | **RED** |
| **P2** | 창당 하한을 **한쪽으로만** 따른다(`min(정책, 상수)`) | **RED** |
| **P3** | 비열등 한계를 코드 상수로 | **RED** |
| **P4** | 배제 비율을 코드 상수로(제외 단계 자리만) | **RED**(처음엔 GREEN — 아래) |
| **P5** | seed 다섯 중 **첫째만** 정책에서 | **RED** |
| **P6** | 판별 표본 반폭을 코드 상수로 | **RED** |
| **P7** | job 조립의 주 가설 수 대조를 없앤다(자리 ② 만) | **RED** |
| **P8** | 배제 비율을 상수로 — **S4 자리만** | **RED** |
| **P9** | 예비가격 수를 상수로 — **적합도 기준 표본 자리만** | **RED**(처음엔 GREEN) |
| **P9b** | 예비가격 수를 상수로 — **적합도 구간 수 자리만** | **GREEN** — 등재(위 「가를 수 없는 자리」) |
| **P10** | 추첨 수를 상수로 — 적합도 자리만 | **RED** |
| **P11** | 예비가격 수를 상수로 — S4 자리만 | **RED** |
| **P12** | 추첨 수를 상수로 — S4 자리만 | **RED** |

누계 **스물셋**(새로 고안한 우회 **열셋** — 계약이 요구한 ≥3 을 넘는다). `P9b` 를 더한
스물넷 중 **스물셋 RED**.

**P4·P9 가 처음에 GREEN 이었다 — 측정이 설계의 구멍을 잡았다.** 둘 다 「한 자리에만 상수를
박았는데 다른 자리가 정책을 따라 판정문이 움직였다」는 같은 계열이고, 자리를 가르는 단언을
더한 뒤 RED 다. 이것이 D-6G2a-6 이 요구한 측정의 실제 값어치다 — 사유로 넘긴 세 값 중
둘이 실제로 열려 있었다.

**동치 변이와 그 처분.** 거동 층에서 `N2·N3·N5·N6·N7` 은 **한 부류**다 — 다섯 다 같은
자리(로더의 seed 조립)에서 같은 결과(정책 seed 를 무시)를 만들고, 다른 것은 **수를 짓는
형태**뿐이다. 그 형태 차이는 리터럴 게이트에만 의미가 있다(그 게이트는 다섯 중 셋을 못
잡는다). 같은 이유로 `N1·N4` 도 한 부류다. **그래도 다섯과 둘을 다 쟀다** — 6G 계약이
이름으로 든 일곱이라 전수 재측정이 D-6G2a-7 의 문면이고, 비용이 변이당 20초 안쪽이다.
제외한 변이는 없다.

## acceptance

**CI `ml-engine` job 명령 그대로**(`.github/workflows/ci.yml`) — 부분 게이트는 안 돌린 것과
같으므로 job 전부를 돌렸다. 측정 HEAD **`a3cde08d`**(이 slice 의 마지막 산출물 커밋).
Kotlin 축은 돌리지 않았다 — 이 slice 의 변경은 `ml-engine/tests/**` 와 문서뿐이고 Kotlin
소스에 닿지 않는다(Gradle 0, 계약 문면).

| 단계 | 명령이 재는 것 | exit |
|---|---|---|
| S-1 | lock 된 의존성 설치 | 0 |
| S-1b | serving extras 에 금지 패키지 다섯이 없음 | 0 |
| S-2 | ruff check · ruff format --check | 0 · 0 |
| S-3 | mypy --strict(96 파일) | 0 |
| S-4 | import-linter(계약 8 kept · 0 broken) | 0 |
| S-5 | `pytest tests -q` — **1,296 passed** | 0 |
| S-6 | 설계 래칫(위반 0) | 0 |
| S-7 | 재활용 출처 두 자리 대조 + 양성 대조 | 0 |
| S-9 | Python 버전 두 자리 대조 | 0 |
| S-11 | wheel 빌드 + 설치본 재수출 | 0 |

test 수는 base 의 **1,209** 에서 **1,296** 으로 늘었다(+87) — 민감도 test 하나가 셋에서
**아흔**으로 늘어난 몫이다(부류·판·자리별 단언·로더 전수가 전부 parametrize 된다).
그 파일 하나의 실행 시간은 **약 82 초**다.

### 새 public 표면 (2b)

**0 이다.** `ml-engine/src/**` 의 diff 가 0 이므로(아래) 새 public 이름이 생길 자리가
없다. test 지원 헬퍼는 전부 `tests/**` 안에 있고, 그중 모듈 밖에서 쓰이는 이름 넷
(`BoardSpec` · `BoardWindow` · `build_board_rows` · `build_mixed_category_rows` ·
`PlannedBidStrategy` 와 투찰률 상수 셋)은 test 패키지 내부 이름이다 — 출하 wheel 에
들어가지 않는다(S-11 이 설치본을 따로 확인한다).

### 출하 코드 무변경 (D-6G2a-9)

```
git diff --stat 30c6659e..HEAD -- ml-engine/src
```
**빈 출력**이다. D-6G2a-9 ⓐ 의 조건(판정 경로에 **실제로 박힌 수**)은 **발생하지 않았다** —
판정 경로의 정책 값 읽기 자리는 전수 살아 있고(착수 AST 전수 · 변이 열 23/24 RED), 멈추고
보고할 데이터 정확성 결함은 없다. ⓑ(test 지원 코드를 `src` 에 두는 것)도 하지 않았다.

### clean-tree 게이트

`git status --porcelain -- <in_scope 세 경로 개별 인자>` 가 **빈 출력**이고, **양성 대조**를
한 번 돌렸다 — 비파괴 절삭(파일 끝에 빈 줄 하나 덧붙이기)으로 ` M` 한 줄이 나오는 것을
확인하고 사본에서 되썼다(`checkout --` 를 쓰지 않는다).

### 누출 스캔

참조형으로 돌린다: `grep -rniE -f config/quality/leak-patterns.txt` 를 evidence 디렉터리와
in_scope 세 경로에 대해. **일치 0 건**. 처음 측정에서 harness 의 캐시 키 변수 이름이 패턴
하나와 글자로 겹쳐 다섯 줄이 잡혔고, 거동을 바꾸지 않는 이름 변경으로 닫았다.

## 알려진 제한

1. **적합도의 구간 수 자리를 가를 수 없다**(변이 P9b 초록). 적합도가 공시하는 스칼라 둘이
   기준 표본과 구간 수에 동시에 의존하고 정책 값 하나가 둘을 함께 정한다 — 기준 표본을
   고정한 채 구간 수만 흔드는 입력이 없다. 나머지 세 자리(제외 ⑮ · 기준 표본 · S4 분포)는
   가른다. 사유를 test 에 등재했고 그 등재가 비면 RED 다.
   `OPEN-6G2A-FIT-BIN-COUNT-SITE`.
2. **평탄 인덱스 목록의 길이가 스키마에 고정돼 있지 않다**. 마지막 seed 키를 지운 정책이
   거부되지 않고 **seed 넷으로** 선다(중간 인덱스를 지우면 구멍이 생겨 거부된다). A-3 승인
   문면은 「seed 5」이고, 넷으로 돈 판정은 거부가 아니라 판정문의 seed 목록으로만 드러난다.
   로더는 이 slice 의 out_scope 라(D-6G2a-9 ⓑ) 등재 + 양성 대조 test 로 잠갔다.
   `OPEN-6G2A-SEED-COUNT-NOT-PINNED`.
3. **`verdict.alpha` 를 올려 `Failed` 를 `Passed` 로 뒤집는 판은 없다** — 구조적 사실이고
   (위 「판을 지을 수 없었던 방향 하나」) 필요 표본 수 경로로 양쪽을 다 쟀다.
4. **부류 ⓑ 셋은 결말까지 가지 못한다**(배제 비율 · S1 offset · 판별 표본 반폭). 판을 더
   지으면 ⓐ 로 올릴 수 있는 값이 있을 수 있으나, 셋 다 **읽는 자리가 ⓑ 단언으로 잠겨
   있다** — 그 자리에 상수를 박으면 판정문이 멈추고 RED 다(변이 P4·P6·P8 실측).
5. **민감도 test 하나가 약 82 초**다. 판 열다섯을 모듈 범위에서 캐시해 같은 (판, 정책) 쌍을
   다시 돌리지 않는다. 더 줄이려면 판 수를 줄여야 하고 그것은 측정 범위를 줄이는 일이다.

## OPEN 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6G-SENSITIVITY-HARDENING` | **이 slice 가 닫는다** — 비교 투영·부류 등식·경계 판·자리별 측정·변이 열 전부 이행 |
| `OPEN-6G2A-FIT-BIN-COUNT-SITE` | **신설** — 적합도 구간 수 자리를 이 층에서 가를 수 없다(위 제한 1) |
| `OPEN-6G2A-SEED-COUNT-NOT-PINNED` | **신설** — 평탄 인덱스 목록의 길이 미고정(위 제한 2) |
| `OPEN-6G2A-INFERENCE-POLICY-SENSITIVITY` | **신설(등재만)** — 분포 엔진(`inference-v1.yaml`) 값의 민감도는 경계 밖(`OPEN-5C2-SERVING-PATH-PARITY` 소관) |

## 비활성화 방법

이 slice 의 산출물은 test 뿐이라 운영 경로를 끄는 스위치가 필요하지 않다. 게이트만 끄려면
`rollback.md` ① 의 경로 한정 복원으로 세 파일을 base 로 되돌린다 — 출하 거동은 처음부터
바뀌지 않았으므로(`src` diff 0) 되돌림이 제품에 닿지 않는다.
