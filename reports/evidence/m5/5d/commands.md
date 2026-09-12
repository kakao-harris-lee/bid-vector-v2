# M5/5D — commands.md

base `f5020aa982e6c5ade01f1660c0568dcd75c2fa7e`(PR #10 머지 = origin/main, 5A·5B 실물 포함)
· 계약 `2bf3bd0`(착수 계약 고정) · 코드 커밋 `2a1bae7`. 로컬: `uv` (pyenv `3.12.2`, `.python
-version` 3.12 과 `requires-python` 두 자리 일치 — S-9). 전건 재실행(부분 게이트 없음).

## S-1 ~ S-9 (scope.md acceptance_commands 순서)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 부재 확인, 복구 후 all-extras 정상 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · `69 files already formatted` |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found in 26 source files` |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 5 kept, 0 broken` |
| S-5 | `uv run python -m pytest tests -q` | 0 | **322 passed, 1 skipped**(golden, OPEN-5D-GOLDEN) — 5A 156 + 5B 77 + 5D 89(inference 79 + registry 9 + golden skip 1) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음(allowlist 편집 없음 — 5D in_scope 는 래칫 한도 무편집이 계약이라, 약한 경계 회피는 `JsonScalar`(닫힌 스칼라 유니온) 타입과 함수 분해로 해결) |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 / 1 | 정상: 위반 0 · 양성(값 어긋남): exit 1(5D 신규 5모듈 포함 10건 불일치 확인 — 정상 evidence 유무에 따라 갈림, 회귀 없음) |
| S-9 | python 버전 두 자리 대조 | 0 | assert 통과(출력 없음) |

## 비고

- leak 스캔: `grep -rniE -f config/quality/leak-patterns.txt ml-engine/src/ml_engine/inference
  ml-engine/src/ml_engine/registry ml-engine/policy ml-engine/tests/inference
  ml-engine/tests/registry reports/evidence/m5/5d` → exit 1(매치 0).
- 하네스 레인 변경: `git log --oneline f5020aa982e6c5ade01f1660c0568dcd75c2fa7e..HEAD --
  CLAUDE.md .claude/` → 빈 목록(착수 이후 하네스 레인 변경 없음).
- 설계 래칫 — `assessment.py::resolve_assessment_posterior`(51→분리)·`policy.py::
  load_inference_policy`(117→`_coerce_values`/`_validate_bands`/`_validate_thresholds`/
  `_validate_invariants`로 분해)·`predict.py::predict_bid_rates`(66→`_predict_center`/
  `_direct_diagnostics`로 분리)·`registry/artifact.py::_parse_manifest`(JSON 페이로드
  검증을 `_parse_feature_fields`/`_parse_release`/`_parse_reproducibility`/
  `_parse_scalars`로 분해, 매개변수 타입은 `dict[str, JsonScalar]` — `Any`/`object`/
  `dict[str, Any]` 없이 함수 경계를 좁혔다)로 전건 위반 0 달성. allowlist 편집 없음(계약
  준수).
- `pyproject.toml` 편집 범위 — `[tool.ruff.lint] select`에 `"BLE"` 추가(D-5D-6),
  `[tool.pytest.ini_options] markers`에 `legacy_parity` 등록. 게이트 계약·래칫 한도(
  `[tool.design-ratchet]`·`[tool.importlinter]`)는 무편집(scope.md 계약대로).
- S-8(Kotlin `./gradlew check`)은 이 slice 의 정본에 없다 — `ml-engine/**`만 닿고
  워크플로가 `ml-engine` job 과 Kotlin `check` job 의 소스 독립을 선언한다(evidence-pack
  2026-09-12 규율 계승).
