# M6/6G-rc — 명령과 종료 코드

정본은 `scope.md` 의 acceptance 절이다. **마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니라
verifier 와 완료 보고**다 — evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다(커밋이
하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다).

## 2026-10-28T15:19Z — 구현 전 RED 재실측 (임시 clone, HEAD `d7446df0`)

- cmd: `uv run python -m pytest tests/evaluation/test_backtest_strategies.py
  tests/evaluation/test_backtest_exclusions.py tests/evaluation/test_backtest_policy.py
  tests/evaluation/test_evaluation_no_stray_numeric_literals.py -q`
- exit: 1
- 핵심 결과: 12 failed · 109 passed. 계획 §3.1 의 열넷 중 **열둘이 RED**이고 둘은 구현 전에 이미
  참이라 **잠금**이다 — 비대칭 범위의 기대 사정률(`expected_assessment_ratio` 는 앞 판에서도
  옳았다)과 대칭 범위·A=0 에서 옛 S0 밴드가 유지된다는 단언(수정식이 그 판에서 접힌다).

## 2026-10-28T15:11Z~15:19Z — acceptance: `ci.yml` 의 `ml-engine` job (HEAD `f8990b90`)

- cmd: 그 job 의 run step 전부 — S-1 `uv sync --frozen --all-extras` · S-1b serving extras 분리
  확인 · S-2 `ruff check .` + `ruff format --check .` · S-3 `mypy --strict src/ml_engine` ·
  S-4 `lint-imports` · S-5 `pytest tests -q` · S-6 `tools/design_ratchet.py --check` ·
  S-7 재활용 출처 대조 + 양성 대조 · S-9 Python 버전 두 자리 대조 · S-11 `uv build --wheel` +
  설치본 재수출 test. CI 전용 step(체크아웃·툴체인 설치)은 뺀다.
- exit: **열두 호출 전부 0**
- 핵심 결과: pytest **1625 passed** · import 계약 8 kept 0 broken · S-7 양성 대조가 기대대로
  어긋남을 내고 step 은 0.

Kotlin `check` job 은 `reports/evidence` 를 읽는 게이트를 담아 **이 evidence 를 담은 HEAD 에서**
돌려야 의미가 있다 — 그 결과는 위 문단의 사유로 완료 보고가 정본이다.

## 2026-10-28T15:21Z — clean-tree 게이트와 양성 대조 (HEAD `f8990b90`)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 빈 출력
- 핵심 결과: 양성 대조로 출하 정책 파일에 한 줄을 더하니 ` M` 이 잡혔고, **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌린 뒤 다시 빈 출력이다. `checkout --` 은 쓰지 않았다.

## 2026-10-28T15:21Z — 비밀값 스캔 (참조형)

- cmd: `command grep -rniE -f config/quality/leak-patterns.txt $(git diff --name-only
  0768bda1..HEAD) reports/evidence/m6/6grc/`
- exit: 0 — 매치 **한 줄**
- 핵심 결과: 그 한 줄은 `test_backtest_sample_scope.py` 의 **이 slice 가 건드리지 않은** 기존
  줄이다(이 slice 의 변경은 같은 파일의 출하 정책 경로 한 줄뿐이다). 어휘 자체는 검사 대상
  디렉터리(`reports/evidence`)에 없다 — `leakPatternGate` 의 scanRoot 가 그 디렉터리이고
  `scope.md` 는 제외 목록에 있다. 식별자·연락처류는 산출물에 없음을 육안으로 확인했다(합성
  fixture 와 제도 상수만).

## 2026-10-28, acceptance 직전 — 변이 여섯 (바꿔치기, 전부 RED)

각 변이 전에 `git status --porcelain -- <그 파일>` 이 빈 출력임을 확인하고, 적용 스크립트는
바꿀 자리가 정확히 한 곳이 아니면 **0 이 아닌 코드로 멈춘다**. 되돌림은 그 파일 하나의
`git checkout --`(산출물이 이미 커밋돼 있어 미커밋 편집이 없다 — 확인 뒤 실행).

| 변이 | 바꿔치운 것 | RED 가 난 자리 |
| --- | --- | --- |
| M1-s0 | S0 밴드의 앵커를 옛 식(`하한율 x (1 ± 예가 반폭)`)으로 | `test_s0_band_endpoints_are_the_corrected_anchor` |
| M1-s4 | S4 격자 하단을 같은 옛 식으로 | `test_s4_grid_runs_from_the_corrected_anchor_lower_edge` |
| M2 | 예정가격 상한 비교를 `<` 에서 `<=` 로 | 경계 test 둘 + S4 표본별 test (3 failed) |
| M3-s4-off | S4 에서 술어를 떼어 옛 `maximum` 으로(채점만 적용) | S4 표본별 test · S4 자리 probe · 쓰임 명단 등식 · 파생 등식 |
| M3-scoring-off | 채점에서 술어를 떼고 옛 업무 축 비교로(S4 만 적용) | 경계 test · 채점 자리 probe · 쓰임 명단 등식 |
| M4 | 상한을 정책 대신 코드 리터럴(`10_000_000_000.0`)에서 | 리터럴 게이트 둘 + 자리 probe 셋 + 다중 자리 처분 등식 |

핵심 결과: **M3 의 두 방향이 서로 다른 test 를 붉힌다** — 채점 쪽을 떼면 원가 결측 test 는
초록으로 남고(그 자리는 안 건드렸다) 채점 자리 probe 만 붉는다. 자리별 probe 가 실제로 자리를
가른다는 실측이다.

## 2026-10-28T18:38Z — 자리별 변이 셋 (리뷰가 GREEN 으로 통과시킨 자리)

표적은 `test_backtest_strategies.py` + `test_backtest_exclusions.py`(**80 passed** 통제군,
변이 없음). 각 변이 전 그 파일의 `git status --porcelain` 이 빈 출력임을 확인했고, 적용
스크립트는 바꿀 자리가 한 곳이 아니면 0 이 아닌 코드로 멈춘다.

| 변이 | 바꿔치운 것 | 결과 |
| --- | --- | --- |
| V9 | S4 자리(`_simulated_floors`)의 업무 축을 `True` 로 고정 | RED 1 — 비공사 S4 test **하나만** |
| V14 | 채점 자리(`rules.pure_cost_floor`)의 업무 축을 `True` 로 고정 | RED 1 — 비공사 채점 test **하나만** |
| V6b | `np.maximum(floors, pure)` → `np.maximum(pure, pure)`(호출 모양 유지) | RED 1 — max 의미 test **하나만** |

핵심 결과: 셋 다 **의도한 자리 하나씩만** 붉힌다(겹치지 않는다). 이 셋은 리뷰 라운드에서
전체 1625 가 초록이던 변이들이다 — 자리별 test 가 그 구멍을 메웠다는 실측이다.

## 2026-10-28T18:38Z~18:45Z — acceptance 재실행: `ci.yml` 의 `ml-engine` job

- cmd: 위 acceptance 절과 같은 열두 호출
- exit: **전부 0**
- 핵심 결과: pytest **1628 passed**(앞 라운드 1625 + 자리별 test 셋) · import 계약 8 kept 0 broken.
