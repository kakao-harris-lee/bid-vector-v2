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
