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
- `ml-engine/src/ml_engine/evaluation/backtest/metrics.py` 의 모듈 docstring(D-6GRC-4).
- `ml-engine/src/ml_engine/evaluation/backtest/reasons.py` 의 `PURE_CONSTRUCTION_COST_ABSENT` 설명(D-6GRC-5).
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
- 2026-10-28 **D-6GRC-1(팀장, 레인 1단계 보고 수령)**: 새 키를 필수(fail-closed — 기존 `test_the_loader_refuses_a_policy_with_a_key_removed`
  가 모든 키에 요구한다)로 두면 `strategy-backtest-v1.yaml` 은 더 이상 적재되지 않는다. 그 귀결로 출하 정책을 적재하는 test 의 경로를 v2 로
  옮기는 것을 in_scope 에 더한다: `ml-engine/tests/app/test_seed_count_pinned.py` · `ml-engine/tests/app/test_backtest_golden.py` ·
  `ml-engine/tests/app/test_backtest_job.py`(그 안의 `policy_version == "strategy-backtest-v1"` 단언 한 줄 포함) ·
  `ml-engine/tests/evaluation/test_backtest_sample_scope.py`. 기계적 귀결이며 새 결정이 아니다.
- 2026-10-28 **D-6GRC-2(팀장)**: v1 파일은 **바이트 동일하게 보존하되 적재 불가**로 둔다(계획은 「보존」만 요구한다). v1·v2 구분 test 는 v1 값으로
  조립한 정책(v1 문면 + 새 키)으로 version · checksum 차이를 잰다. 그 사실을 checklist 알려진 제한에 둔다.
- 2026-10-28 **D-6GRC-3(팀장)**: M6/G1-DEC10 레인이 `app/build.gradle.kts` 의 system property 로 `ml-engine/policy/strategy-backtest-v1.yaml` 을
  가리킨다(하한율 밴드 값 일치 test). v1 이 보존되므로 그 test 는 깨지지 않지만 출하 정책은 v2 다 — **두 브랜치 중 나중에 머지되는 쪽이 그 경로를
  v2 로 옮긴다**(밴드 값은 v1 = v2 라 등식은 그대로). 이 slice 는 Kotlin 을 건드리지 않는다.
- 2026-10-28 **D-6GRC-4(팀장, 레인 동결 보고 수령)**: `metrics.py` 의 「적격」 정의 docstring 이 「공사는 순공사원가선까지」라
  새 적용 범위(P2)와 어긋나 레인이 고쳤다 — 「계획이 지목한 낡은 docstring」에 해당하나 경로 목록에 없었다. 그 파일(docstring 한정, 거동 변경 0)을
  in_scope 에 더한다.
- 2026-10-28 **D-6GRC-5(팀장, r2 동결 보고 수령)**: CR r1 LOW 가 지목한 `reasons.py` 의 `PURE_CONSTRUCTION_COST_ABSENT`
  설명(이 slice 가 그 사유의 외연을 「모든 공사」에서 「규칙이 적용되는 공사」로 좁혔다)을 레인이 더했다. D-6GRC-4 와 같은 부류 — docstring
  한정, 거동 변경 0 — 이므로 그 파일을 in_scope 에 더한다.
