# M6/6G 정책 값의 출처 — `ml-engine/policy/strategy-backtest-v1.yaml`

> **지위: Python 레인 산출물.** 이 파일의 값이 판정을 정한다. 값은 **실험 창을 보기 전에** 고정됐고,
> 바꾸면 새 `version` 이며 이전 판정과 구분된다(위협 모델 ②). 갱신 절차는 **승인 문면 → 정책 파일 →
> test** 순이다(5C-2 `policy-values.md` 관례 승계).
>
> 코드 리터럴로 새지 않는 것은 게이트가 지킨다 — `evaluation/**` 의 숫자 리터럴 전수 검사가
> 하위 패키지까지 보고(6G 보강), 출하 임계 값이 소스에 나타나면 붉어진다.

## 1. 판정식 (A-3 운영자 승인, 2026-09-27)

승인 문면 축어(`scope.md` 「운영자 승인 필요」):

> **A-3 판정 정책 값**: Δ(제안: 상대 +20%) · α(0.05 단측, 주 가설 Bonferroni /3) · 비열등 한계(실격률
> +1%p) · 검정력 0.8 · 창 최소 3 · seed 5.

> **2026-09-27 운영자: 「A-1~A-5 추천안대로 승인」.** 아래 다섯 항목이 제안값 그대로 확정됐다.

| 키 | 값 | 근거 |
|---|---|---|
| `verdict.min_relative_improvement` | 0.20 | A-3 「Δ 상대 +20%」 |
| `verdict.alpha` | 0.05 | A-3 「α 0.05 단측」 |
| `verdict.primary_hypothesis_count` | 3 | A-3 「주 가설 Bonferroni /3」 — 주 가설은 S2 세 후보. **분모와 주 가설 수가 갈리면 job 이 시작도 하지 않는다**(우회 ⑥) |
| `verdict.ineligibility_noninferiority_margin` | 0.01 | A-3 「비열등 한계 실격률 +1%p」 |
| `verdict.target_power` | 0.80 | A-3 「검정력 0.8」 |
| `verdict.min_window_count` | 3 | A-3 「창 최소 3」 |
| `verdict.min_window_rows` | 483 | D-6G-5 「창당 n ≥ 483(ML-07 legacy 필요 표본 승계)」 |
| `stability_seeds.*` | 5C-2 `evaluation-v1.yaml` 과 **같은 다섯** | A-3 「seed 5」. 6G 가 새 다섯을 지어내면 그 자체가 seed 쇼핑 표면이다(우회 ③) — test 가 두 파일을 대조한다 |

## 2. S1 offset — legacy 승계 (A-4 · D-6G-15)

D-6G-15 가 정한 산식은 **`하한율 + offset`** 이고 `E[R]` 를 곱하지 않는다. legacy 의 앵커가
`floor + 선언 offset` 이고 `× E[예정가]` 변형은 legacy 자신이 기각했다.

| 항 | 값 | legacy 좌표 |
|---|---|---|
| offset(추천·p50) | **0.0053** → `strategy.s1_offset_bp: 53.0` | `bid-vector/app/core/config.py` 의 `PREDICTION_CONSTRUCTION_SCENARIO_FLOOR_OFFSETS` |
| (참고) 공격·p25 | 0.0016 | 같은 선언 |
| (참고) 안전·p75 | 0.0148 | 같은 선언 |
| 해석기 | `floor + offset` 을 밴드 안으로 clamp | `bid-vector/app/ai/construction_scenario.py` 의 `resolve_scenario_anchor_rates` |
| 골든 회귀 | floor 0.89745 → base 0.90275 | `bid-vector/tests/test_construction_scenario_anchor.py` |

legacy 가 그 값에 붙인 측정 문면(축어): *"(win÷기초금액 − 무변환 era_floor) 분포 p25=+0.0016(공격),
p50=+0.0053(추천), p75=+0.0148(안전)"*, 표본은 *"clean-basis 공사 정착행 n=2,051"*.

> **이 값이 공사 전용인 것이 S1 의 범위를 정한다.** 용역·물품에 옮길 근거가 없으므로 **S1 은 공사에서만
> 채점하고** 그 밖의 업무에서는 금액을 내지 않는다(기권 사유 `NOT_APPLICABLE_CATEGORY`, 계수 공시).
> 관측값으로 새 상수를 만들지 않는다 — 그것이 곧 사후 튜닝이다(우회 ②).

## 3. 제도·규정에서 오는 값

| 키 | 값 | 근거 |
|---|---|---|
| `institution.reserve_price_count` / `draw_count` | 15 / 4 | 복수예비가격 제도. `inference-v1.yaml` 의 `reserve.expected_price_count`·`reserve.draw_count` 와 **같은 값**이고 test 가 두 파일을 대조한다(층 계약상 한쪽이 다른 쪽을 읽을 수 없다) |
| `floor.pure_construction_cost_ratio` | 0.98 | 순공사원가 98% 배제(국가계약법 시행령 제42조 계열, 추정가격 100억 미만 공사) |
| `floor.rate_band_low` / `high` | 0.30 / 0.995 | 게시 하한율의 개연 밴드(legacy 게이트 승계). **밴드 확정은 `OPEN-DEC-10` 소관**이라 이 값은 「값이 있다 = 옳다」를 주장하지 않는다 — 밖의 값을 사유별로 계수할 뿐이다 |
| `effective.construction` / `service` / `goods` | 2026-01-30 / 2026-05-26 / 2026-05-29 | 2026 낙찰하한율 개정 시행일. 적용례가 「시행일 이후 최초 **입찰공고분**」이라 개찰일이 아니라 공고일로 가른다(D-6G-14). 물품은 보도가 05-26/05-29 로 갈려 **늦은 쪽**을 쓴다 — 그 선택이 경계 구간 공고분을 통째로 뺀다 |
| `window.days` / `embargo_days` | 7 / 7 | D-6G-5 「비중첩 주 단위 창, embargo 1주」 |

> **정책 파일 위치**는 D-6G-24 로 `ml-engine/policy/strategy-backtest-v1.yaml` 이 확정됐다(계약 문면의 `policy/…` 정정).

## 4. 계산량·표본 축 (판정식이 아니다)

| 키 | 값 | 성질 |
|---|---|---|
| `strategy.s4_iteration_count` · `s4_grid_size` · `s4_grid_span_bp` · `s4_min_competitor_samples` | 4000 · 41 · 400.0 · 30 | S4 몬테카를로의 **격자와 반복 수**. 결과를 보고 고르는 길을 닫으려고 파일에 고정한다 |
| `fit.alpha` · `min_sample_count` · `max_bin_ratio_deviation` | 0.05 · 200 · 0.02 | P-4 제도 분포 적합도. 실험 **앞에** 돌고, 어긋나면 판정 대신 멈춤 결과를 낸다 |
| `sampling.*` | 600 · 3 · 80000 · 1449 | 표본 크기 결정식(D-6G-20). 수집 강제는 Kotlin 레인이 하고 Python 은 입력과 결과를 판정 JSON 에 **공시**한다. 상한 80,000 은 A-1 승인 범위 안의 수치 |
| `sensitivity.wide_reserve_half_width` | 0.025 | 지자체 민감도 (a) 판의 경계(D-6G-21) |

> **재현 test 의 파생 정책**: 출하 창당 하한이 483 이라 재현 test 에 수천 공고가 필요하고 S4 가 CI 를
> 몇 분씩 잡는다. 그래서 그 test 는 출하 파일에서 **창 크기·몬테카를로 반복 수·적합도 표본 하한만**
> 낮춘 파생 정책을 임시 경로에 만들어 쓴다 — **판정식 축(Δ·유의수준·Bonferroni 분모·비열등 한계·
> 검정력)은 출하 값 그대로**이고, 그 목록을 test 가 스스로 단언한다(완화 통로가 되지 않게).

## 5. 값이 없어 **정하지 않은** 것

| 자리 | 상태 |
|---|---|
| 기관 코드 → 지자체 대응 | **없다.** 제외 ⑪ 규칙이 한 번도 발화하지 않고, 「가르지 못한 표본 수」를 판정 JSON 이 따로 공시한다. 추정은 보조 민감도 판에서만, 그 판의 `estimate_available: false` 와 함께 |
| 예가 범위율·순공사원가·A값 공고 여부 | **온다**(`OPEN-6G-BASE-AMOUNT-OPERATION` 폐쇄, 수집 구현 `aaf77bde`). 다만 **채움률은 실측 전**이라 판정 JSON 이 여섯 칸의 채움률을 함께 싣는다(D-6G-22) — 채움률이 낮으면 제외가 실제로는 「판정 못 함」이라는 뜻이다 |
| 선박 제조 물품의 조달 분류 코드 | **없다.** ⑧ 은 낙찰방법명 표지로만 발화하고, 코드 공간이 미확정이라는 사실을 판정 불가 축이 공시한다 |
| 하한가 경계 1원의 절상·절하 규칙 | **없다.** 스키마 §1 이 적은 그대로 — 투찰금액이 하한가와 1원 차이인 공고의 적격 판정은 미정이고 판정문의 한계로 나간다 |
| `smkpAmt`(표준시장단가금액)의 A 합산 근거 | **없다.** 스냅숏 계약이 A 합산에서 제외하기로 했다. 이 배제는 하한가를 **낮춰** 적격을 쉽게 만드는 방향이라 보수적이지 **않다**. 영향 범위(술어 참 공고 수)를 셀 칸은 **D-6G-23 으로 신설 결정**됐고 Kotlin 레인이 채운다 — 그 칸은 `schema_version` 을 올리며 오므로 오늘 판독기는 그 스냅숏을 거부한다(두 레인이 같이 움직인다) |
| 지자체 판정 | **확정적으로 없다** — 선행 조사 P-5 가 authoritative 필드 부재를 확인했다(`OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`). 기관 코드 **원값**이 스냅숏에 오고, 민감도 (b) 판이 그 칸을 쓴다 |
