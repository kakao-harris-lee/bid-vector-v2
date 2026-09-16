# M5/5F-1 — commands.md

정본 = scope.md acceptance_commands 전건(S-0·S-1b·S-2~S-7·S-9·S-11·S-12) + S-10(Kotlin
`check`, evidence 커밋마다 그 HEAD 에서 재실측). 명령·exit·핵심 결과 한 줄만.

## RED 확인(값 갱신 전, 2026-09-16, 커밋 `39d8080`)

- cmd: `(cd ml-engine && uv run python -m pytest tests/inference/test_policy.py -q)`
- exit: 1
- 핵심 결과: `test_shipped_policy_file_loads_with_lowered_clamp_and_agency_sample_threshold`
  1건 실패(`PolicyRejected(reason="정책 키 누락: ['assessment.agency_sample_threshold']")`),
  28 passed — D-5F1-3 반전이 실제로 값 부재에 반응함을 확인.

## 값 갱신 뒤 1차 확인(커밋 `b687bbc`)

- cmd: `(cd ml-engine && uv run python -m pytest tests/inference/test_policy.py -q)`
- exit: 0
- 핵심 결과: 29 passed(위 test 통과 + `test_shipped_values_with_agency_sample_threshold_
  declared_load_successfully`의 `scenario_clamp_max == Decimal("1.4")` 단언도 갱신 전엔
  실패했었다 — 값 갱신으로 `Decimal("1.0")`과 일치)

## 전체 스위트 1차 확인(YAML 값만 반영, test 파일들 갱신 전 — 파급 범위 확인용)

- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 1
- 핵심 결과: 4 failed(`test_server_prediction.py::test_shipped_policy_without_agency_
  sample_threshold_stays_not_ready` · `test_scenario.py::test_candidates_have_fixed_
  order_and_labels` · `test_scenario.py::test_matches_legacy_scenario_bid_rates_
  formula` · `test_kotlin_rules_parity.py::test_candidate_rate_above_one_fails_
  closed_end_to_end`), 948 passed — 앞 둘은 scope.md ④⑤ 가 반전을 지시한 test, 셋째는
  scope 밖(팀장 확인 대기 중, checklist.md 판단 4)

## in_scope test 반영 뒤 재확인(커밋 `3ca7bc7`·`b301d2b`)

- cmd: `(cd ml-engine && uv run python -m pytest tests/app/test_server_prediction.py tests/serving/test_kotlin_rules_parity.py -q)`
- exit: 0
- 핵심 결과: 16 passed(app 5 · serving 11) — `test_shipped_policy_serves_success_
  matching_promoted`(병합), `test_shipped_clamp_max_keeps_extreme_observation_within_
  contract_rate`·`test_candidate_rate_above_one_still_fails_closed_with_reverted_
  clamp_max`(반전+변이 쌍) 포함

## S-0

- cmd: `(cd ml-engine && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: Audited 34 packages(변경 없음)

## S-1b

- cmd: `(cd ml-engine && set -euo pipefail && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do if uv run --no-sync python -c "import $m" >/dev/null 2>&1; then echo "금지 패키지 $m 이 serving extras 에 설치됐다" >&2; exit 1; fi; done && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: 금지 패키지 다섯 전부 serving extras 미설치 확인, 전체 extras 복원 완료

## S-2

- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 178 files already formatted

## S-3

- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 72 source files

## S-4

- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: 8 kept, 0 broken(ignored import 2)

## S-5(전체 pytest — **미완, scope 확장 대기**)

- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 1
- 핵심 결과: 2 failed(`tests/inference/test_scenario.py::test_candidates_have_fixed_
  order_and_labels`·`test_matches_legacy_scenario_bid_rates_formula`, scope 밖), 950
  passed. 이 파일을 제외한 나머지 전부(952건 중 950 + 이 slice 가 새로 늘리거나
  반전한 test 전부)는 초록 — checklist.md 판단 4·알려진 제한 2 참고. 팀장이
  scope 확장을 승인하면 이 항목을 재실측한다.

## S-5(golden 서브셋, scope.md out_of_scope 「영향 0」 실측)

- cmd: `(cd ml-engine && uv run python -m pytest tests/inference/golden -q)`
- exit: 0
- 핵심 결과: 14 passed — golden 은 자체 cfg(clampMax·agencySampleThreshold)를 실어
  이 slice 의 출하 정책 값 변경과 무관함을 확인

## S-6

- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0)

## S-7

- cmd: `(cd ml-engine && set -euo pipefail && uv run python tools/reuse_provenance_check.py && if uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md; then echo "양성 대조(어긋난 evidence)가 실패해야 하는데 통과했다" >&2; exit 1; fi)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0. 양성 대조(어긋난 evidence 주입)는 29건
  불일치로 정상 실패(스크립트 자체가 올바르게 판정함을 확인) — 이 slice 는 신규 재활용
  파일이 없어(reuse.md) 이 게이트에 직접 관여하지 않는다

## S-9

- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python'], (v, p['project']['requires-python'])")`
- exit: 0

## S-10(Kotlin `check`, evidence 커밋마다 그 HEAD 에서 재실측)

- cmd: `./gradlew --no-daemon check`(HEAD `e4ba6a4`, evidence 커밋 `b5ebd3d` 직후)
- exit: 0 — BUILD SUCCESSFUL, 337 tasks(74 executed, 20 from cache, 243 up-to-date)

## S-11

- cmd: `(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: 휠 빌드 성공, 1 passed

## S-12

- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(29 tasks) / "교차 언어 socket 스모크 통과"

## 경계 확인

- cmd: `git log --oneline 845e29b..HEAD -- CLAUDE.md .claude/`
- exit: 0(무출력 = 하네스 레인 변경 없음)
- cmd: `git diff --name-status 845e29b..HEAD -- ml-engine/src`
- exit: 0(무출력 = D-5F1-1 준수, `src/**` 무편집)
- cmd: `git diff --name-status 845e29b..HEAD -- reports/evidence/m5/5e2`
- exit: 0(무출력 = D-5F1-4 준수, 5E-2 evidence 무편집)

## clean-tree 게이트(in_scope 경로)

- cmd: `git status --porcelain -- ml-engine/policy/inference-v1.yaml reports/evidence/m5/5d/policy-values.md reports/evidence/m5/5d2/policy-values.md ml-engine/tests/inference/test_policy.py ml-engine/tests/inference/_policy_support.py ml-engine/tests/app/test_server_prediction.py ml-engine/tests/serving/test_kotlin_rules_parity.py ml-engine/tests/serving/test_wire.py reports/evidence/m5/5f1`
- exit: 0(무출력 = 깨끗함, 커밋 `b5ebd3d` 기준)

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt ml-engine/policy/inference-v1.yaml reports/evidence/m5/5d/policy-values.md reports/evidence/m5/5d2/policy-values.md ml-engine/tests/inference/test_policy.py ml-engine/tests/inference/_policy_support.py ml-engine/tests/app/test_server_prediction.py ml-engine/tests/serving/test_kotlin_rules_parity.py reports/evidence/m5/5f1/`
- exit: 1(매치 없음 = 통과)
