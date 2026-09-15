# M5/5D-2 — commands.md

base `cefb19c9269ac3b29d0246175fb04d71c04aa3d4`(5D 종결) · 계약 `9466347`(착수 계약 고정)
→ `623baa1`(계약 갱신 — `OPEN-5D2-SAMPLE-SEGMENT`, 구현 레인 발견) · 코드 커밋 `b9cfe77`
(observations·availability·distribution·engine 신설 + policy.py/results.py 확장 +
cascading test 갱신) → `fe836db`(evidence — reuse.md). 로컬: `uv`(pyenv `3.12.2`).
전건 재실행(부분 게이트 없음).

## S-1 ~ S-9 (scope.md acceptance_commands 순서)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 부재 확인, 복구 후 all-extras 정상 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · `81 files already formatted` |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found in 31 source files`(5D 27 + 신규 4: observations·availability·distribution·engine) |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 5 kept, 0 broken`(49 files, 161 dependencies) |
| S-5 | `uv run python -m pytest tests -q -m "not legacy_parity"` | 0 | **390 passed, 4 deselected**(5D 347+1 base − 011 skip 해제 +1 − 알려진-제한 중복 없음 + 신규 5D-2 test 다수, golden 14/14 skip 0) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음(함수 50줄 초과 2건을 헬퍼 분리로 해소 — `policy._coerce_values`→`_scenario_tuples`, `distribution.predict_distribution`→`_prepare_estimation_inputs`/`_assemble_success`) |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 | 정상: 위반 0(observations·availability·distribution 세 모듈 reuse.md 대조) · 양성: exit 1(회귀 없음) |
| S-8 | `uv run python -m pytest tests -q -m legacy_parity` | 0 | **4 passed**(5D 그대로, 5D-2 는 legacy_parity 마커 test 를 신설하지 않았다 — 관측 축 변경 없음) |
| S-9 | python 버전 두 자리 대조 | 0 | assert 통과(출력 없음) |

## golden 011 해소 (scope.md ⑥)

`test_ml_kernel_011_shrinkage_weight_carried_in_response` — `resolve_assessment_posterior`
(K5, 정책 synthetic: `agencyPriorStrength 15`·`categoryPriorStrength 30`·
`minPredictiveStd 0.004`·`minSamplesForVariance 2`·`agencySampleThreshold 10`) +
`distribution._resolve_diagnostics`(5D-2 조립기의 production 함수, golden 전용 표면
신설 없음)를 직접 호출. `verified_paths` 11 개 전부 단언: `diagnostics.segmentSupport`
(`DIRECT`)·`agencySampleCount`(5)·`agencySampleBelowThreshold`(`true`)·
`shrinkageWeight.fraction`(`"0.25"`)·`shrinkageWeightCarriedInResponse`(golden 표지,
production 대응 필드 없음 — 상수 확인)·`levelWeights.{agency,category,global}.fraction`
(`0.25`/`0.1875`/`0.5625`)·`levelWeightSum.fraction`(`"1"`)·`posteriorMean.fraction`
(`"1.01375"`)·`effectiveSampleCount`(`"284.375"`). golden 14/14, skip 0.

## OPEN-5D2-SAMPLE-SEGMENT 반영 확인 (계약 갱신 `623baa1`)

`test_distribution.py::TestDistributionRequestFromProto::test_every_sample_segment_is_missing`
— `DistributionRequest.from_proto`가 표본 수와 무관하게 `SegmentMissing()`을 낸다.
`test_engine.py::test_engine_module_does_not_import_gbm_predict_module`(AST 정적 검사)·
`test_engine_module_does_not_have_predict_in_sys_modules_dependency`(런타임) — 둘 다
`ml_engine.inference.predict`·`ml_engine.registry` 미참조 확인(D-5D2-1 (b), 위협 모델
우회 후보 (4)).

## 누출 검사

```
$ grep -rniE -f config/quality/leak-patterns.txt ml-engine/src/ml_engine/inference \
    ml-engine/tests/inference reports/evidence/m5/5d2 --exclude=scope.md
```

0 매치.
