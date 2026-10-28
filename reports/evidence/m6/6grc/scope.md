# M6/6G-rc — 투찰가 제도 규칙 수정 · 백테스트 정책 v2 (착수 계약, 2026-10-28, 팀장)

- **근거**: 운영자 문서 `docs/discovery/bid-price-rule-correction-plan-2026-10-28.md`(이하 「계획」) 전문과
  `docs/discovery/ml-efficacy-validation-plan-2026-10-28.md` 의 T0 · T1. 운영자 지시 2026-10-28 「main 의 분석 문서를 확인하고
  방향 재설정이 필요하면 진행해」 — 팀장 판단: 검증 계획의 선행 단계(T1)이고 M7 레인과 경로 교집합이 0 이라 지금 병렬로 연다.
- **base**: main `0768bda1` · worktree `bid-vector-v2-m6-6grc` · 브랜치 `m6-6grc/2026-10-28`.
- **정본**: 이 파일. 결정 ID `D-6GRC-N`. 규칙의 정본은 「계획」이고 이 계약은 그 범위·순서·acceptance 를 고정한다.
- **레인**: P 하나(`ml-implementer`). 판정은 `verifier`(opus) + `code-reviewer`(sonnet) 병렬. Codex 는 대상 경로가 아니다(되돌리기
  어려운 경로 아님 — Python 백테스트 · 정책 파일).
- **성격**: 정책 값 · 규칙 술어 slice. 값 한 줄도 「그 값이 실제 채점과 S4 에 닿는가」를 test 로 잠근다(계획 §3.4 의 감도 probe).

## 범위 — 계획의 P1 · P2 · P3 그대로

- **P1** 비대칭 예가 범위의 가격 앵커: `expected_floor_rate = floor_rate × E[R] + (1 − floor_rate) × A / base_amount`,
  반폭 `floor_rate × reserve_half_width`. S0 범위와 S4 격자 하단이 이 값을 **한 곳**(`StrategyInput` 의 읽기 전용 속성)에서 공유한다.
  S1 · S2 계약, 원 단위 올림, 난수 스트림, S4 의 격자 수 · 폭 · 반복 수 · 동률 처리, 예가 표본 생성기(`institution.py`)는 바꾸지 않는다.
- **P2** 순공사원가 98% 실격선의 적용 범위: `CONSTRUCTION && planned_price < 10_000_000_000`(정확히 100억은 미적용, 기준은 예정가격).
  실제 채점은 실현 예정가격으로, S4 는 시뮬레이션 표본마다 `base_amount × sampled_ratio` 로 판정하고 **두 자리가 같은 술어(공통 도우미)를 쓴다**.
  규칙 미적용 공사는 원가 결측으로 제외하지 않는다. 원가 환산식과 그 근사 한계는 유지한다.
- **P3** 정책 v2: `strategy-backtest-v1.yaml` 은 보존하고 `strategy-backtest-v2.yaml` 을 출하 정책으로 추가(새 키
  `floor.pure_construction_cost_max_planned_price_exclusive: 10000000000.0`, 나머지 판정 기준 · seed · 창 · S1 · S4 값은 v1 과 같음).
  판정 JSON 의 `policy_version` · `policy_checksum` 으로 v1 과 구분한다. **결과를 보고 상수나 격자를 조정하지 않는다.**

## in_scope

- `ml-engine/src/ml_engine/evaluation/backtest/{strategies,rules,floor,policy_values,policy}.py` 와 그 test(`ml-engine/tests/evaluation/` ·
  `ml-engine/tests/app/test_backtest_policy_sensitivity.py` · `_backtest_support.py`).
- `ml-engine/policy/strategy-backtest-v2.yaml`(신규).
- `ml-engine/tests/evaluation/test_evaluation_no_stray_numeric_literals.py` 의 출하 임계 등재(`1e10`).
- `docs/runbook/m6-6g-real-collection.md` 의 백테스트 정책 경로 한 줄.
- 계획이 지목한 낡은 코드 주석 · docstring.
- `reports/evidence/m6/6grc/**`.

## out_scope

- 실 KONEPS 수집 · 실데이터 백테스트 실행(검증 계획 T2 이후 — 별도 승인) · 운영 추천가 배선 · S4 채택 판정.
- v1 evidence 소급 수정 · Kotlin 코드(Kotlin 쪽은 순공사원가를 데이터로만 나르고 이 규칙을 적용하지 않는다 — 2026-10-28 팀장 실측).
- `institution.py`(예가 표본 생성기) — 계획이 「표본 생성기가 아니라 전략 가격 앵커가 문제」라고 실측했다.

## 순서 (계획 §3)

1. **경계 회귀 test 먼저**(RED 선행): 계획 §3.1 의 strategies 여섯 · exclusions 넷. 구현 전 RED 를 실측해 evidence 에.
2. P1 → P2 → P3 구현, 각각 GREEN.
3. 정책 게이트(누락 · 0 · 음수 · NaN 거부, version · checksum 구분) · 리터럴 게이트 · 감도 probe(새 상수가 **실제 채점과 S4 둘 다**에 닿음).
4. 변이(바꿔치기): 앵커를 옛 식으로 · 100억 경계를 `<=` 로 · S4 와 채점 중 한쪽만 술어 적용 · v2 상수를 정책 대신 리터럴로 — 각각 RED.

## acceptance

`ci.yml` 의 `ml-engine` job 명령 그대로(`uv sync --frozen --all-extras` 뒤 ruff check · ruff format --check · mypy --strict · lint-imports ·
pytest 전체 · `tools/design_ratchet.py --check` · `tools/reuse_provenance_check.py` 등 그 job 의 run step 전부)와 Kotlin `check` job
(`leakPatternGate` 가 `reports/evidence` 를 읽는다). `container` 는 ml-serving 이미지 입력이 바뀌면 그 job 도.

## rollback

`git restore --source=0768bda1 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외) + 새 파일 삭제. 목록은
`git diff --name-status 0768bda1..HEAD` 기계 산출, 마지막 산출물 커밋에서 ①~⑥ 실측.

## 하네스 레인 변경

없음.

## 계약 갱신 이력

- 2026-10-28 착수(팀장).
