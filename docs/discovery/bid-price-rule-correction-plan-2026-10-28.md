# 투찰가 제도 규칙 수정·구현 계획 (2026-10-28)

- **지위**: 구현 전 계획. 실험 결과나 S4 채택 결정이 아니다.
- **선행 조사**: `bid-price-predictability-2026-09-27.md`, M6/6G 계약과 현재 코드 대조.
- **후속 실험**: `ml-efficacy-validation-plan-2026-10-28.md`.
- **범위**: Python `ml-engine`의 전략 백테스트. 실 KONEPS 수집, 운영 추천가 배선, S4 채택은 범위 밖.

## 1. 현재 판정

S4를 바로 채택해서는 안 된다. 비대칭 예가 범위의 가격 앵커와 순공사원가 규칙 적용 범위를 먼저 고치고, 수정된 S0·S4를 새 정책 버전으로 재평가해야 한다.

기존 Python 1,605개 테스트와 Kotlin 전체 `check`는 통과했다. 그러나 기존 테스트는 대칭 예가와 100억원 미만 공사 중심이라 아래 경계를 증명하지 않는다.

## 2. 문제와 수정 계획

### P1. 비대칭 예가에서 S0·S4 후보 중심이 어긋남

**현재**

- `StrategyInput.expected_assessment_ratio`는 제도 평균 `1 + (begin + end) / 2`를 올바르게 계산한다: `ml-engine/src/ml_engine/evaluation/backtest/strategies.py`.
- S0는 그 값을 쓰지 않고 `floor_rate * (1 ± half_width)`를 쓴다.
- S4도 같은 값을 격자 하단으로 쓴다.
- A값 공사의 평균 하한율에 필요한 A 보정이 없다.

**실측**

예가 범위 `[-2%, +4%]`의 이론 평균 사정률은 `1.01`이고, 20만 회 직접 시뮬레이션 평균은 `1.01000898`이었다. 하한율 `0.87745`, A=0이면 현재 전략 중심은 `0.87745`인데 제도상 평균 하한율은 `0.88622450`이다. 표본 생성기가 아니라 전략 가격 앵커가 문제다.

**수정식**

```text
expected_floor_rate
  = floor_rate * E[R] + (1 - floor_rate) * A / base_amount

half_width_of_floor_rate = floor_rate * reserve_half_width
S0 범위 = expected_floor_rate ± half_width_of_floor_rate
S4 격자 하단 = expected_floor_rate - half_width_of_floor_rate
```

S4의 격자 수·폭·반복 수와 동률 처리는 바꾸지 않는다. S1은 사전 등록된 `floor_rate + offset`을 유지한다. 예가 표본 생성기 `institution.py`도 바꾸지 않는다.

### P2. 순공사원가 98% 실격선을 모든 공사에 적용함

**현재**

- `rules.py`의 채점과 `strategies.py`의 S4는 공사이고 원가가 있으면 예정가격과 무관하게 추가 실격선을 적용한다.
- `rules.py`는 원가가 없는 공사를 실제 예정가격이 100억원 이상이어도 제외할 수 있다.
- 정책 설명은 이미 100억원 미만 공사로 제한하고 있어 코드와 문서가 어긋난다.

**제도 근거**

조달청 시설공사 적격심사세부기준 제5조의 적용 대상은 **예정가격 100억원 미만 공사**다. 기준은 기초금액이나 낙찰방법명 문자열이 아니라 예정가격이며, 정확히 100억원이면 미적용이다.

- https://www.law.go.kr/admRulLsInfoP.do?admRulSeq=2100000209091

**수정 규칙**

```text
적용 조건 = CONSTRUCTION && planned_price < 10_000_000_000
```

- 실제 채점은 실현 예정가격으로 판정한다.
- S4는 시뮬레이션마다 `planned_price = base_amount * sampled_ratio`로 판정한다.
- 100억원을 가로지르는 시뮬레이션에서는 표본별 적용 여부가 달라질 수 있다.
- 규칙 미적용 공사는 원가 결측으로 제외하지 않는다.
- 기존 원가 환산식과 그 근사 한계는 유지하고 적용 여부만 고친다.

### P3. 수정 결과를 v1 실험과 섞을 위험

비대칭 S0 범위와 S4 격자 변경은 전략 결과를 바꾼다. 기존 `strategy-backtest-v1.yaml`은 보존하고 `strategy-backtest-v2.yaml`을 출하 정책으로 추가한다. 판정 JSON의 기존 `policy_version`·`policy_checksum`로 결과를 구분하고, 결과를 보고 상수나 격자를 조정하지 않는다.

## 3. 구현 순서

### 3.1 경계 회귀 테스트를 먼저 추가

**`tests/evaluation/test_backtest_strategies.py`**

1. `[-2%, +4%]`, `[-4%, +2%]`에서 기대 사정률이 각각 `1.01`, `0.99`인지 확인한다.
2. A=0과 A>0에서 평균 하한율 공식을 검증한다.
3. 결정적 가짜 RNG로 S0의 `low/high`를 포획해 수정 공식의 끝점인지 확인한다.
4. S4 첫 격자점이 수정된 하단이고 마지막 점은 `하단 + 정책 폭`인지 확인한다.
5. S4에서 예정가격이 100억원 미만·정확히·초과인 표본의 순공사원가선을 각각 확인한다.
6. 대칭 범위·A=0에서는 기존 S0 기준이 유지되는지 확인한다.

**`tests/evaluation/test_backtest_exclusions.py`**

1. 예정가격 `100억 - 1원`, `100억`, `100억 + 1원` 경계를 검증한다.
2. 100억원 미만의 원가 결측은 제외하고, 100억원 이상의 결측은 이유 없이 제외하지 않는지 확인한다.
3. 일반 하한가와 순공사원가선 사이 금액을 넣어 실제 `admit_rows`에서 적격 판정이 갈리는지 확인한다.
4. 100억원 이상에서는 `pure_cost_floor`와 `eligibility_floors`에 순공사원가선이 없는지 확인한다.

새 테스트는 구현 전 RED, 구현 후 GREEN을 확인한다.

### 3.2 비대칭 가격 앵커를 한 곳에서 계산

`strategies.py`의 `StrategyInput`에 평균 하한율과 반폭을 계산하는 읽기 전용 속성을 둔다. S0와 S4가 그 값을 공유하게 한다. S1·S2 계약, 원 단위 올림, 난수 스트림은 유지한다.

### 3.3 순공사원가 적용 판정을 채점과 S4가 공유

- `policy_values.py`: 예정가격 상한(배타)을 `FloorRateBand`에 추가하고 양의 유한수만 허용한다.
- `floor.py`·`rules.py`: 실제 예정가격이 적용 범위일 때만 순공사원가선을 만든다. 미적용이면 `None`으로 기존 하한가-only 경로를 쓴다.
- `_is_pure_cost_absent`: 규칙이 실제로 적용되는 공사에서만 원가 결측을 제외한다.
- `strategies.py`: 시뮬레이션 예정가격별로 같은 술어를 적용한다. 채점과 S4의 `<` 경계가 달라지지 않게 공통 도우미를 사용한다.

### 3.4 v2 정책과 정책 게이트 갱신

- `strategy-backtest-v1.yaml`을 복제해 `strategy-backtest-v2.yaml`을 만든다. 기존 판정 기준·seed·창·S1·S4 값은 유지한다.
- 새 키 `floor.pure_construction_cost_max_planned_price_exclusive: 10000000000.0`을 추가한다.
- `backtest/policy.py`의 출하 버전과 키 목록·조립을 v2로 갱신한다.
- `test_backtest_policy.py`, `_backtest_support.py`의 출하 경로를 v2로 바꾼다.
- 상한 누락·0·음수·NaN 거부, version·checksum 구분을 테스트한다.
- `test_evaluation_no_stray_numeric_literals.py`에 `1e10`을 출하 임계로 등록해 코드 리터럴을 막는다.
- `test_backtest_policy_sensitivity.py`에 새 상수의 **실제 채점과 S4 사용**을 각각 증명하는 probe를 추가한다.
- `docs/runbook/m6-6g-real-collection.md`의 백테스트 정책 경로를 v2로 바꾼다.
- 과거 v1 evidence는 소급 수정하지 않는다. 낡은 코드 주석과 docstring은 새 동작에 맞춘다.

## 4. 검증과 완료 조건

### 집중 검증

```bash
cd ml-engine
uv run python -m pytest \
  tests/evaluation/test_backtest_strategies.py \
  tests/evaluation/test_backtest_exclusions.py \
  tests/evaluation/test_backtest_policy.py \
  tests/evaluation/test_evaluation_no_stray_numeric_literals.py -q
uv run python -m pytest tests/app/test_backtest_policy_sensitivity.py -q
```

### 표준 전체 검증

```bash
cd ml-engine
uv run ruff check .
uv run ruff format --check .
uv run mypy --strict src/ml_engine
uv run lint-imports
uv run python -m pytest tests -q
uv run python tools/design_ratchet.py --check
uv run python tools/reuse_provenance_check.py

cd ..
./gradlew check --console=plain
```

완료 조건은 다음과 같다.

- 두 방향 비대칭 범위와 A>0의 S0·S4 계산이 공식과 일치한다.
- 100억원 정확히는 순공사원가 규칙이 적용되지 않는다.
- 100억원을 가로지르는 S4 표본과 실제 채점이 같은 규칙을 쓴다.
- 새 상수가 정책에서만 오고 정책 감도·리터럴 게이트를 통과한다.
- Python 품질 게이트와 Kotlin `check`가 실패 0으로 끝난다.
- v2 결과가 version·checksum으로 v1과 구분된다.

실데이터 백테스트는 이 구현 완료 후 별도 작업이다. 그 전에는 S4의 우위나 운영 채택을 주장하지 않는다.
