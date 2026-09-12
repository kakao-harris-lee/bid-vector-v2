# M5/5D — commands.md

base `f5020aa982e6c5ade01f1660c0568dcd75c2fa7e`(PR #10 머지 = origin/main, 5A·5B 실물 포함)
· 계약 `2bf3bd0`(착수 계약 고정) · 계약 갱신 `619cc5a`(verifier r1 반영 지시) · 코드 커밋
`2a1bae7`(최초) → `145847c`(verifier r1 M-1/M-2/M-4·L-1/L-2/L-3 수정). 로컬: `uv`(pyenv
`3.12.2`, `.python-version` 3.12 과 `requires-python` 두 자리 일치 — S-9). 전건 재실행
(부분 게이트 없음), M-1~M-4·L-1~L-3 는 게이트 술어 변경이라 표 전체를 재실행했다.

## S-1 ~ S-9 (scope.md acceptance_commands 순서)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 부재 확인, 복구 후 all-extras 정상 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · `69 files already formatted` |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found in 26 source files`(`warn_unreachable=true` 켠 뒤 재확인 — M-4 반영, 5A/5B 코드에 새 unreachable 없음) |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 5 kept, 0 broken` |
| S-5 | `uv run python -m pytest tests -q` | 0 | **333 passed, 1 skipped**(golden, OPEN-5D-GOLDEN) — base 322(5A 156+5B 77+5D 89) + 이번 라운드 신규 11(M-1 1·M-2 5·L-1 1·L-3 3·L-2 기존 detail 값 갱신 1) |
| S-5(마커 분리, verifier r1 L-4) | `uv run python -m pytest tests -q -m "not legacy_parity"` | 0 | **329 passed, 1 skipped, 4 deselected** — 판정은 이 표를 기준으로 한다 |
| S-8(관측 전용, verifier r1 L-4 신설 — Kotlin `./gradlew check` 아님) | `uv run python -m pytest tests -q -m legacy_parity` | 0 | **4 passed**(legacy 산식 재현 관측 — 판정 근거 아님, docstring 에 명시) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음(allowlist 편집 없음 — 약한 경계 회피는 재귀 `JsonValue`(스칼라+`list[JsonValue]`+`dict[str,JsonValue]`) 타입과 함수 분해로 해결, M-4 이후에도 유지) |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 / 1 | 정상: 위반 0 · 양성(값 어긋남): exit 1(회귀 없음) |
| S-9 | python 버전 두 자리 대조 | 0 | assert 통과(출력 없음) |

## verifier r1 재검증 세부

- **M-1** `LoadedArtifact(some_manifest)` — 1 인자만 주면 런타임 `TypeError`(실측,
  `test_loaded_artifact_requires_verified_bytes_argument`). mypy strict 도 같은 호출을
  **정적으로** 거부함을 임시 프로브 함수로 직접 확인(즉시 원복, 커밋에 없음) —
  `error: Missing positional argument "verified" in call to "LoadedArtifact"  [call-arg]`.
- **M-2** `scenario.clamp_min = -1.0` 로드 → `PolicyRejected("scenario.clamp_min 은
  0보다 커야 한다: -1.0")`(이전 판은 `InferencePolicy`까지 통과해 `build_scenario_
  candidates(center=-0.5, std=0.0, ...)`가 `Candidate.__post_init__`의 `ValueError`를
  던졌다 — 지금은 정책 로드 단계에서 막힌다). 음수 weight(`-0.10`, 합 1 유지)·
  `min_predictive_std=0.0` 도 각각 `PolicyRejected` 확인.
- **M-4** `warn_unreachable=true` 적용 뒤 `mypy --strict src/ml_engine` 재실행 —
  `registry/artifact.py`의 `JsonValue` 교체 후 unreachable 0. 반증 실측: 이전 판
  `JsonScalar`(중첩 컨테이너 배제)로 임시 되돌려 재실행하면 `feature_names` 목록 검사
  (`_parse_feature_fields`, verify_feature_names 호출 직전 분기)에서 unreachable **1건**
  (`Found 1 error in 1 file`)이 재현됨을 확인했다(임시 diff, 즉시 원복 — 커밋에 남지
  않음). verifier r1 보고의 "죽는 구간이 `feature_names` 목록 검사와 `verify_feature_
  names` 호출"은 이 실측과 일치한다.
- **L-1** `Decimal("0.87465").quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)` →
  `0.8747`, `ROUND_HALF_EVEN` → `0.8746`(두 값 다 test 로 직접 대조).
- **L-2** K5 `global_level=None` → `Unmeasurable(INSUFFICIENT_SAMPLES,
  NO_GLOBAL_SAMPLES)`(이전 `TOO_FEW_DRAWS`에서 분리).
- **L-3** `residual_std=float("nan")`(JSON 왕복 후에도 `NaN` 리터럴로 보존됨, Python
  `json.loads` 기본이 허용) → `ArtifactRejected("residual_std 는 유한해야 한다: nan")`.
  `inf`·`-inf` 도 동일 확인.

## 비고

- leak 스캔: `grep -rniE -f config/quality/leak-patterns.txt ml-engine/src/ml_engine/inference
  ml-engine/src/ml_engine/registry ml-engine/policy ml-engine/tests/inference
  ml-engine/tests/registry reports/evidence/m5/5d` → exit 1(매치 0).
- 하네스 레인 변경: `git log --oneline f5020aa982e6c5ade01f1660c0568dcd75c2fa7e..HEAD --
  CLAUDE.md .claude/` → 빈 목록(이번 라운드까지 하네스 레인 변경 없음).
- 설계 래칫 — 최초 라운드에서 이미 4개 함수(assessment/policy/predict/artifact)를 분해해
  위반 0 을 달성했고(2A~2E 아님, 이 slice 최초 커밋 `2a1bae7` 근거), 이번 라운드는
  `JsonScalar`→`JsonValue` 교체만으로 래칫 재확인(위반 0 유지) — allowlist 편집 없음.
- `pyproject.toml` 편집 범위(이번 라운드 추가분) — `[tool.mypy] warn_unreachable = true`
  (verifier r1 M-4 지시, 계약 갱신 `619cc5a`가 명시 승인한 5A 소유 파일 hunk). 기존
  `[tool.ruff.lint] select`의 `"BLE"`·`[tool.pytest.ini_options] markers`의
  `legacy_parity`는 최초 라운드에서 이미 반영(무변경). 게이트 계약·래칫 한도(
  `[tool.design-ratchet]`·`[tool.importlinter]`)는 계속 무편집.
- M-3(golden 통합)은 팀장 지시로 이번 라운드 제외 — `tests/inference/golden/
  test_kernel_golden.py`는 무편집, checklist.md 알려진 제한 11 에 등재.
