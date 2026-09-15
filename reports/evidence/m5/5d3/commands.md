# M5/5D-3 — commands.md

base `d09666348c6489c1a8fd32144b91b82937620ef4`(PR #16 병합 커밋, 2F 표본 축 — scope.md
문면의 `base_sha`는 자릿수가 하나 많은 오기, `git log --oneline -1 d096663`으로 실측한
값이 이 문서의 정본이다, 팀장에게 scope.md 정정 보고) · 계약 `5306958`(착수 계약 고정)
→ test 커밋 `104697a`(RED) → 구현 커밋 `b72af4e`(GREEN). 로컬: `uv`(pyenv `3.12.2`).

## S-1 ~ S-9 (scope.md acceptance — 5D-2 acceptance_commands 목록과 동일, 전건 재실행)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + `uv sync --frozen --all-extras` 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 부재 확인, 복구 후 all-extras 정상 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · 전건 formatted |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found` |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 6 kept, 0 broken` |
| S-5 | `uv run python -m pytest tests -q -m 'not legacy_parity'` | 0 | 전건 통과(base `d096663`+2F 대비 스택된 baseline 661 passed → 이 slice로 678 passed, golden 14/14 skip 0 — 기준선은 `git stash` 임시 대조로 실측, 아래 「baseline 대조」) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | `설계 래칫 위반 없음` |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 / 1 | 정상 실행 exit 0(위반 0) · 양성 대조(`--evidence tests/gates/fixtures/reuse-mismatch.md`) exit 1(회귀 없음, 의도된 실패) |
| S-8 | `uv run python -m pytest tests -q -m legacy_parity` | 0 | 7 passed(5D-2 그대로 — 이 slice는 legacy_parity 마커 test를 신설하지 않았다) |
| S-9 | python 버전 두 자리 대조 | 0 | assert 통과(출력 없음) |

## baseline 대조 (S-5)

`git stash push -u` → base(`d096663`)+2F 스택 위에서 `pytest tests -q -m 'not legacy_parity'`
재실행 → `git stash pop`으로 이 slice 변경 복원. 이 slice가 늘린 test는 이 대조의 차이분
(golden 011 이 D-5D3-7로 wire 경로에서 재검증된 1개 test 포함, test_facts.py 신규
resolve_text_fact 단위 test 6개, test_distribution.py 신규 segment 규칙표 test, test_engine.py
신규 D-5D3-7 test 1개, test_observations.py 사유 개수 test 갱신 — 정확한 델타는 위 명령의
재실행 결과 자체가 정본이다).

## D-5D3-7 golden 011 wire 재현 확인

`tests/inference/test_engine.py::test_serve_bid_rates_wire_driven_direct_segment_support_
matches_golden_011` — golden `fixtures/input/ml-kernel-011.json`의 `levels.{agency,
category,global}.sampleCount`(5/10/500)·`policy.assessment.*`를 읽어 합성 `CompetitionSample`
500개를 조립하고 `serve_bid_rates` 결과의 `diagnostics`를 `fixtures/expected/ml-kernel-
011.json`의 `diagnostics`(segmentSupport DIRECT·agencySampleCount 5·
agencySampleBelowThreshold true·shrinkageWeight.fraction "0.25")와 대조. golden corpus
파일은 이 slice에서 미편집(읽기 전용 소비).

## 착수 grep 실측

`checklist.md`「착수 grep 실측」절 참고 — `SegmentMissing`·`SampleSegment` 사용처가 착수
시점 src에서 `distribution.py`(정의)·`inference/__init__.py`(재수출)뿐임을 확인.

## 누출 검사

```
$ grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m5/5d3/
```

0 매치(exit 1). in_scope 경로: `ml-engine/src/ml_engine/features/facts.py`·`features/
__init__.py`·`ml-engine/src/ml_engine/inference/{distribution,observations,__init__}.py`·
`ml-engine/tests/features/test_facts.py`·`ml-engine/tests/inference/{_sample_support,
test_distribution,test_engine,test_observations}.py`·`ml-engine/tests/inference/golden/
test_kernel_golden.py`·`reports/evidence/m5/5d3/`(scope.md 제외 — 자기 인용 예외는
2026-09-02 규약과 동일).

## evidence 편집 커밋 뒤 Kotlin `check` 재실행 (2026-09-16 규칙)

루트에서 `./gradlew --no-build-cache --no-daemon :leakPatternGate`(evidence 커밋 `ae4417d`
HEAD에서) — exit 0(`BUILD SUCCESSFUL`).
